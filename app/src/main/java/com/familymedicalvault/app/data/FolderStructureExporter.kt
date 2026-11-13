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

        val examsRoots = locateAllExamsRoots(grantedRoot)
        require(examsRoots.isNotEmpty()) {
            "Δεν βρέθηκε κανένας φάκελος εξετάσεων με όνομα Εξετάσεις σε κανένα υποφάκελο."
        }

        var totalEntries = 0

        fun walk(node: DocumentFile, relativePath: String, depth: Int): JSONArray {
            require(depth <= MAX_DEPTH) { "Η δομή φακέλων είναι υπερβολικά βαθιά." }
            val entries = JSONArray()

            val children = node.listFiles().sortedWith(
                compareBy<DocumentFile> { !it.isDirectory }
                    .thenBy { it.name?.lowercase() ?: "" }
            )

            for (child in children) {
                require(totalEntries < MAX_ENTRIES) {
                    "Η δομή ξεπερνά το όριο των $MAX_ENTRIES αντικειμένων."
                }

                val name = child.name ?: "(χωρίς όνομα)"
                val path = if (relativePath.isBlank()) name else "$relativePath/$name"

                val item = JSONObject()
                    .put("type", if (child.isDirectory) "directory" else "file")
                    .put("name", name)
                    .put("path", path)

                if (child.isFile) {
                    item.put("mimeType", child.type ?: JSONObject.NULL)
                    item.put("sizeBytes", child.length())
                    item.put("lastModified", child.lastModified())
                }

                entries.put(item)
                totalEntries++

                if (child.isDirectory) {
                    item.put("children", walk(child, path, depth + 1))
                }
            }

            return entries
        }

        val rootsJson = JSONArray()
        for ((rootPath, examsRoot) in examsRoots) {
            rootsJson.put(
                JSONObject()
                    .put("relativePath", rootPath)
                    .put("rootName", examsRoot.name ?: "Εξετάσεις")
                    .put("entries", walk(examsRoot, "", 0))
            )
        }

        val selectedRootName = grantedRoot.name ?: "root"
        val json = JSONObject()
            .put("format", "familymedicalvault-structure")
            .put("version", 2)
            .put("scope", "all-exams-folders")
            .put("selectedRootName", selectedRootName)
            .put("generatedAtEpochMs", System.currentTimeMillis())
            .put("containsFileContents", false)
            .put("examFolderCount", examsRoots.size)
            .put("entryCount", totalEntries)
            .put("examRoots", rootsJson)

        val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val out = File(shareDir, "FamilyMedicalVault-structure-all-exams.json")
        out.writeText(json.toString(2), Charsets.UTF_8)
        out
    }

    private fun locateAllExamsRoots(root: DocumentFile): List<Pair<String, DocumentFile>> {
        val found = mutableListOf<Pair<String, DocumentFile>>()
        var scanned = 0

        fun search(node: DocumentFile, relativePath: String, depth: Int) {
            require(depth <= MAX_SEARCH_DEPTH) { "Η αναζήτηση φακέλων είναι υπερβολικά βαθιά." }
            require(scanned < MAX_SCAN_ENTRIES) {
                "Η αναζήτηση ξεπερνά το όριο των $MAX_SCAN_ENTRIES φακέλων/αρχείων."
            }

            val nodeName = node.name.orEmpty()
            if (node.isDirectory && isExamsFolderName(nodeName)) {
                found += relativePath to node
                return
            }

            if (!node.isDirectory) return

            for (child in node.listFiles()) {
                scanned++
                if (!child.isDirectory) continue
                val childName = child.name ?: "(χωρίς όνομα)"
                val childPath = if (relativePath.isBlank()) childName else "$relativePath/$childName"
                search(child, childPath, depth + 1)
            }
        }

        search(root, "", 0)
        return found.sortedBy { it.first.lowercase() }
    }

    private fun isExamsFolderName(name: String): Boolean {
        val normalized = name
            .trim()
            .lowercase()
            .replace('ά', 'α')
            .replace('έ', 'ε')
            .replace('ή', 'η')
            .replace('ί', 'ι')
            .replace('ό', 'ο')
            .replace('ύ', 'υ')
            .replace('ώ', 'ω')
        return normalized == "εξετασεις"
    }

    companion object {
        const val MAX_ENTRIES = 20_000
        const val MAX_DEPTH = 30
        const val MAX_SEARCH_DEPTH = 20
        const val MAX_SCAN_ENTRIES = 20_000
    }
}
