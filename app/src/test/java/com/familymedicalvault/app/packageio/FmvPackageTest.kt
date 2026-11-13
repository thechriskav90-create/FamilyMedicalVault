package com.familymedicalvault.app.packageio

import com.familymedicalvault.app.data.VaultStorage
import com.familymedicalvault.app.model.MedicalCategory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FmvPackageTest {
    private val payload = "medical-data".toByteArray()

    @Test
    fun validPackage() {
        val bytes = packageBytes()
        val result = FmvPackage.validate { ByteArrayInputStream(bytes) }
        assertTrue(result.isSuccess)
        assertEquals("Πατέρας", result.getOrThrow().person)
    }

    @Test
    fun wrongSha256Rejected() {
        val bytes = packageBytes(sha = "0".repeat(64))
        assertTrue(FmvPackage.validate { ByteArrayInputStream(bytes) }.isFailure)
    }

    @Test
    fun unsupportedVersionRejected() {
        val bytes = packageBytes(version = 2)
        assertTrue(FmvPackage.validate { ByteArrayInputStream(bytes) }.isFailure)
    }

    @Test
    fun unknownCategoryRejected() {
        val bytes = packageBytes(category = "Άγνωστο")
        assertTrue(FmvPackage.validate { ByteArrayInputStream(bytes) }.isFailure)
    }

    @Test
    fun missingPayloadRejected() {
        val bytes = packageBytes(includePayload = false)
        assertTrue(FmvPackage.validate { ByteArrayInputStream(bytes) }.isFailure)
    }

    @Test
    fun traversalRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("../evil.txt"))
            zip.write("x".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestJson().toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("payload/test.pdf"))
            zip.write(payload)
            zip.closeEntry()
        }
        assertTrue(FmvPackage.validate { ByteArrayInputStream(out.toByteArray()) }.isFailure)
    }

    @Test
    fun duplicateFilenameGetsSuffix() {
        val existing = setOf("test.pdf", "test (2).pdf")
        assertEquals("test (3).pdf", VaultStorage.uniqueNameForExisting(existing, "test.pdf"))
    }

    @Test
    fun categoryUsedForAutoCreatedPersonIsRecognized() {
        assertEquals(MedicalCategory.EXAMS, MedicalCategory.fromFolderName("Εξετάσεις"))
    }

    private fun packageBytes(
        version: Int = 1,
        category: String = "Εξετάσεις",
        sha: String = sha256(payload),
        includePayload: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestJson(version, category, sha).toByteArray())
            zip.closeEntry()

            if (includePayload) {
                zip.putNextEntry(ZipEntry("payload/test.pdf"))
                zip.write(payload)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun manifestJson(
        version: Int = 1,
        category: String = "Εξετάσεις",
        sha: String = sha256(payload)
    ): String = JSONObject()
        .put("format", "familymedicalvault-import")
        .put("version", version)
        .put("person", "Πατέρας")
        .put("category", category)
        .put("displayName", "test.pdf")
        .put("mimeType", "application/pdf")
        .put("payload", "payload/test.pdf")
        .put("sha256", sha)
        .put("source", "ChatGPT")
        .toString()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
