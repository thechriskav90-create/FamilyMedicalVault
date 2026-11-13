package com.familymedicalvault.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FolderStructureExporter(private val context: Context) {

    fun exportExamsTree(rootUri: Uri): Result<File> = runCatching {
        val grantedRoot = DocumentFile.fromTreeUri(context, rootUri)
            ?: error("Δεν ήταν δυνατή η πρόσβαση στον συνδεδεμένο φάκελο.")

        val examsRoot = locateExamsRoot(grantedRoot)
            ?: error("Δεν βρέθηκε φάκελος εξετάσεων με όνομα Ιατρικός_φάκελος_*.")

        val entries = JSONArray()
        var count = 0

        fun walk(node: DocumentFile, relativePath: String, depth: Int) {
            require(depth <= MAX_DEPTH) { "Η δομή φακέλων είναι υπερβολικά βαθιά." }
            require(count < MAX_ENTRIES) { "Η δομή ξεπερνά το όριο των $MAX_ENTRIES αντικειμένων." }

            val children = node.listFiles().sortedWith(
                compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name?.lowercase() ?: "" }
            )

            for (child in children) {
                require(count < MAX_ENTRIES) { "Η δομή ξεπερνά το όριο των $MAX_ENTRIES αντικειμένων." }
                val name = child.name ?: "(χωρίς όνομα)"
                val path = if (relativePath.isBlank()) name else "$relativePath/$name"

                val item = JSONObject()
                    .put("type", if (child.isDirectory) "directory" else "file")
                    .put("path", path)

                if (child.isFile) {
                    item.put("mimeType", child.type ?: JSONObject.NULL)
                    item.put("sizeBytes", child.length())
                    item.put("lastModified", child.lastModified())
                }

                entries.put(item)
                count++

                if (child.isDirectory) {
                    walk(child, path, depth + 1)
                }
            }
        }

        walk(examsRoot, "", 0)

        val selectedRootName = grantedRoot.name ?: "root"
        val rootName = examsRoot.name ?: "Ιατρικός_φάκελος"
        val json = JSONObject()
            .put("format", "familymedicalvault-structure")
            .put("version", 1)
            .put("scope", "exams")
            .put("selectedRootName", selectedRootName)
            .put("rootName", rootName)
            .put("fullRootPath", selectedRootName + "/" + rootName)
            .put("generatedAtEpochMs", System.currentTimeMillis())
            .put("containsFileContents", false)
            .put("entries", entries)

        val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val out = File(shareDir, "FamilyMedicalVault-structure-exams.json")
        out.writeText(json.toString(2), Charsets.UTF_8)
        out
    }

    private fun locateExamsRoot(root: DocumentFile): DocumentFile? {
        val name = root.name.orEmpty()
        if (isExamsFolderName(name)) return root

        return root.listFiles()
            .firstOrNull { it.isDirectory && isExamsFolderName(it.name.orEmpty()) }
    }

    private fun isExamsFolderName(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized.startsWith("ιατρικός_φάκελος_") ||
            normalized.startsWith("ιατρικος_φακελος_") ||
            normalized == "ιατρικός_φάκελος" ||
            normalized == "ιατρικος_φακελος"
    }

    companion object {
        const val MAX_ENTRIES = 10_000
        const val MAX_DEPTH = 20
    }
}
