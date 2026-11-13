package com.familymedicalvault.app.packageio

import com.familymedicalvault.app.model.MedicalCategory
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class FmvManifest(
    val format: String,
    val version: Int,
    val person: String,
    val category: String,
    val displayName: String,
    val mimeType: String,
    val payload: String,
    val sha256: String,
    val source: String
)

object FmvPackage {
    const val FORMAT = "familymedicalvault-import"
    const val VERSION = 1
    const val MAX_MANIFEST_BYTES = 64 * 1024
    const val MAX_PAYLOAD_BYTES = 250L * 1024L * 1024L

    fun validate(openStream: () -> InputStream): Result<FmvManifest> = runCatching {
        val manifestBytes = readManifest(openStream)
        val json = JSONObject(manifestBytes.toString(Charsets.UTF_8))
        val manifest = FmvManifest(
            format = json.optString("format"),
            version = json.optInt("version", -1),
            person = json.optString("person"),
            category = json.optString("category"),
            displayName = json.optString("displayName"),
            mimeType = json.optString("mimeType"),
            payload = json.optString("payload"),
            sha256 = json.optString("sha256").lowercase(),
            source = json.optString("source", "Άγνωστη")
        )
        validateManifest(manifest)
        verifyPayload(openStream, manifest)
        manifest
    }

    fun copyPayload(openStream: () -> InputStream, manifest: FmvManifest, output: OutputStream): Result<Unit> = runCatching {
        var found = 0
        ZipInputStream(BufferedInputStream(openStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                validateZipPath(entry.name)
                if (!entry.isDirectory && entry.name == manifest.payload) {
                    found++
                    require(found == 1) { "Το payload υπάρχει περισσότερες από μία φορές." }
                    var total = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_PAYLOAD_BYTES) { "Το αρχείο ξεπερνά το όριο των 250 MB." }
                        output.write(buffer, 0, count)
                    }
                }
                zip.closeEntry()
            }
        }
        require(found == 1) { "Δεν βρέθηκε το δηλωμένο payload." }
    }

    private fun readManifest(openStream: () -> InputStream): ByteArray {
        var manifest: ByteArray? = null
        var count = 0
        ZipInputStream(BufferedInputStream(openStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                validateZipPath(entry.name)
                if (!entry.isDirectory && entry.name == "manifest.json") {
                    count++
                    require(count == 1) { "Βρέθηκαν πολλαπλά manifest.json." }
                    manifest = readLimited(zip, MAX_MANIFEST_BYTES)
                }
                zip.closeEntry()
            }
        }
        require(count == 1 && manifest != null) { "Λείπει το manifest.json." }
        return manifest!!
    }

    private fun verifyPayload(openStream: () -> InputStream, manifest: FmvManifest) {
        val digest = MessageDigest.getInstance("SHA-256")
        var found = 0
        var total = 0L
        ZipInputStream(BufferedInputStream(openStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                validateZipPath(entry.name)
                if (!entry.isDirectory && entry.name == manifest.payload) {
                    found++
                    require(found == 1) { "Το payload υπάρχει περισσότερες από μία φορές." }
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_PAYLOAD_BYTES) { "Το αρχείο ξεπερνά το όριο των 250 MB." }
                        digest.update(buffer, 0, count)
                    }
                }
                zip.closeEntry()
            }
        }
        require(found == 1) { "Δεν βρέθηκε το δηλωμένο payload." }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == manifest.sha256) { "Το SHA-256 του αρχείου δεν συμφωνεί με το manifest." }
    }

    private fun validateManifest(m: FmvManifest) {
        require(m.format == FORMAT) { "Μη υποστηριζόμενος τύπος πακέτου." }
        require(m.version == VERSION) { "Μη υποστηριζόμενη έκδοση πακέτου." }
        require(m.person.isNotBlank() && m.person.length <= 100 && !m.person.contains('\u0000')) { "Μη έγκυρο πρόσωπο." }
        require(MedicalCategory.fromFolderName(m.category) != null) { "Άγνωστη κατηγορία." }
        require(isSafeFileName(m.displayName)) { "Μη ασφαλές όνομα αρχείου." }
        require(m.mimeType.isNotBlank() && m.mimeType.length <= 150) { "Μη έγκυρος MIME τύπος." }
        validateZipPath(m.payload)
        require(m.payload.startsWith("payload/") && !m.payload.endsWith("/")) { "Το payload πρέπει να βρίσκεται στον φάκελο payload/." }
        require(m.sha256.matches(Regex("[0-9a-f]{64}"))) { "Μη έγκυρο SHA-256." }
    }

    private fun isSafeFileName(name: String): Boolean {
        if (name.isBlank() || name.length > 240 || name == "." || name == "..") return false
        if (name.any { it == '/' || it == '\\' || it == '\u0000' || it.code < 32 }) return false
        return true
    }

    private fun validateZipPath(name: String) {
        require(name.isNotBlank() && !name.startsWith("/") && !name.startsWith("\\")) { "Μη ασφαλής διαδρομή ZIP." }
        require(!name.contains('\u0000')) { "Μη ασφαλής διαδρομή ZIP." }
        val normalized = name.replace('\\', '/')
        require(normalized.split('/').none { it == ".." }) { "Εντοπίστηκε προσπάθεια path traversal." }
    }

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "Το manifest ξεπερνά τα 64 KB." }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}
