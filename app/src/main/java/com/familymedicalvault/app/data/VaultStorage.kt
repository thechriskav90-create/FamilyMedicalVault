package com.familymedicalvault.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.familymedicalvault.app.model.MedicalCategory
import java.io.OutputStream
import java.util.UUID

class VaultStorage(private val context: Context) {

    fun root(uri: Uri): DocumentFile? = DocumentFile.fromTreeUri(context, uri)

    fun listPeople(rootUri: Uri): List<DocumentFile> =
        root(rootUri)?.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()

    fun createPerson(rootUri: Uri, displayName: String): DocumentFile? {
        val root = root(rootUri) ?: return null
        val safeName = sanitize(displayName)
        val existing = root.findFile(safeName)
        val person = existing ?: root.createDirectory(safeName) ?: return null
        MedicalCategory.entries.forEach { category ->
            if (person.findFile(category.folderName) == null) person.createDirectory(category.folderName)
        }
        return person
    }

    fun categoryDirectory(rootUri: Uri, personFolder: String, category: MedicalCategory): DocumentFile? {
        val person = root(rootUri)?.findFile(sanitize(personFolder)) ?: return null
        return person.findFile(category.folderName) ?: person.createDirectory(category.folderName)
    }

    fun importDocument(
        rootUri: Uri,
        personFolder: String,
        category: MedicalCategory,
        sourceUri: Uri,
        preferredName: String? = null
    ): Result<String> = runCatching {
        val source = DocumentFile.fromSingleUri(context, sourceUri)
        val sourceName = preferredName?.takeIf { it.isNotBlank() }
            ?: source?.name
            ?: ("αρχείο-" + UUID.randomUUID())
        val mime = context.contentResolver.getType(sourceUri) ?: "application/octet-stream"
        importWithWriter(rootUri, personFolder, category, sourceName, mime) { output ->
            context.contentResolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "Αδυναμία ανάγνωσης αρχείου" }
                input.copyTo(output)
            }
        }.getOrThrow()
    }

    fun importText(
        rootUri: Uri,
        personFolder: String,
        category: MedicalCategory,
        text: String,
        preferredName: String = "κείμενο.txt"
    ): Result<String> = importWithWriter(rootUri, personFolder, category, preferredName, "text/plain") { output ->
        output.writer(Charsets.UTF_8).use { it.write(text) }
    }

    fun importWithWriter(
        rootUri: Uri,
        personFolder: String,
        category: MedicalCategory,
        preferredName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): Result<String> = runCatching {
        createPerson(rootUri, personFolder) ?: error("Δεν ήταν δυνατή η δημιουργία του προσώπου")
        val targetDir = categoryDirectory(rootUri, personFolder, category)
            ?: error("Δεν βρέθηκε ο φάκελος προορισμού")
        val safeName = uniqueName(targetDir, sanitizeFileName(preferredName))
        val target = targetDir.createFile(mimeType, safeName) ?: error("Αποτυχία δημιουργίας αρχείου")
        try {
            context.contentResolver.openOutputStream(target.uri, "w").use { output ->
                requireNotNull(output) { "Αδυναμία εγγραφής αρχείου" }
                writer(output)
            }
            target.uri.toString()
        } catch (t: Throwable) {
            runCatching { target.delete() }
            throw t
        }
    }

    fun listDocuments(rootUri: Uri, personFolder: String, category: MedicalCategory): List<DocumentFile> =
        categoryDirectory(rootUri, personFolder, category)?.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    private fun uniqueName(dir: DocumentFile, name: String): String {
        if (dir.findFile(name) == null) return name
        val existing = dir.listFiles().mapNotNull { it.name }.toSet()
        return uniqueNameForExisting(existing, name)
    }

    private fun sanitize(value: String): String =
        value.trim().replace(Regex("[\\/:*?\"<>|]"), "-").ifBlank { "Πρόσωπο" }

    private fun sanitizeFileName(value: String): String =
        value.trim().replace(Regex("[\\/:*?\"<>|]"), "-").ifBlank { "αρχείο" }

    companion object {
        fun uniqueNameForExisting(existing: Set<String>, name: String): String {
            if (name !in existing) return name
            val dot = name.lastIndexOf('.')
            val base = if (dot > 0) name.substring(0, dot) else name
            val ext = if (dot > 0) name.substring(dot) else ""
            var i = 2
            while ("$base ($i)$ext" in existing) i++
            return "$base ($i)$ext"
        }
    }
}
