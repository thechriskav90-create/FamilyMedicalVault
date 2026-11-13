package com.familymedicalvault.app.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object FolderTreeExporter {
    private const val MAX_ITEMS = 10000
    private const val MAX_DEPTH = 20

    data class ExportResult(
        val shareUri: Uri,
        val file: File,
        val itemCount: Int,
        val rootName: String
    )

    fun exportMedicalFolderStructure(
        context: Context,
        selectedRootUri: Uri
    ): Result<ExportResult> = runCatching {
        val selectedRoot = DocumentFile.fromTreeUri(context, selectedRootUri)
            ?: error("Δεν ήταν δυνατή η ανάγνωση του συνδεδεμένου φακέλου.")

        val medicalRoot = selectedRoot.listFiles()
            .firstOrNull { it.isDirectory && it.name?.startsWith("Ιατρικός_φάκελος_") == true }
            ?: selectedRoot

        var itemCount = 0

        fun node(file: DocumentFile, depth: Int): JSONObject {
            require(depth <= MAX_DEPTH) { "Η δομή φακέλων είναι υπερβολικά βαθιά." }
            itemCount++
            require(itemCount <= MAX_ITEMS) { "Η δομή περιέχει πάνω από $MAX_ITEMS αντικείμενα." }

            val obj = JSONObject()
                .put("name", file.name ?: "Χωρίς όνομα")
                .put("type", if (file.isDirectory) "folder" else "file")

            if (!file.isDirectory) {
                obj.put("mimeType", file.type ?: JSONObject.NULL)
                obj.put("sizeBytes", file.length())
                obj.put("lastModified", file.lastModified())
            } else {
                val children = JSONArray()
                file.listFiles()
                    .sortedWith(compareBy<DocumentFile>({ !it.isDirectory }, { it.name?.lowercase() ?: "" }))
                    .forEach { child -> children.put(node(child, depth + 1)) }
                obj.put("children", children)
            }
            return obj
        }

        val payload = JSONObject()
            .put("format", "familymedicalvault-folder-tree")
            .put("version", 1)
            .put("scope", "medical-exams")
            .put("contentIncluded", false)
            .put("description", "Ονόματα φακέλων και αρχείων μόνο. Δεν περιέχεται ιατρικό περιεχόμενο.")
            .put("tree", node(medicalRoot, 0))

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(exportDir, "familymedicalvault-exams-structure.json")
        outFile.writeText(payload.toString(2), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            outFile
        )

        ExportResult(
            shareUri = uri,
            file = outFile,
            itemCount = itemCount,
            rootName = medicalRoot.name ?: "Ιατρικός φάκελος"
        )
    }
}
