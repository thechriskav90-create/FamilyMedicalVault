package com.familymedicalvault.app

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familymedicalvault.app.data.VaultPreferences
import com.familymedicalvault.app.data.VaultStorage
import com.familymedicalvault.app.model.MedicalCategory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialPerson = intent.getStringExtra(EXTRA_PERSON)
        val initialCategory = intent.getStringExtra(EXTRA_CATEGORY)?.let(MedicalCategory::fromFolderName)
        setContent { MedicalVaultApp(initialPerson, initialCategory) }
    }

    companion object {
        const val EXTRA_PERSON = "fmv_person"
        const val EXTRA_CATEGORY = "fmv_category"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalVaultApp(initialPerson: String? = null, initialCategory: MedicalCategory? = null) {
    val context = LocalContext.current
    val prefs = remember { VaultPreferences(context) }
    val storage = remember { VaultStorage(context) }
    val scope = rememberCoroutineScope()
    val rootUriString by prefs.rootUri.collectAsStateWithLifecycle(initialValue = null)
    val rootUri = rootUriString?.let(Uri::parse)

    var selectedPerson by remember { mutableStateOf(initialPerson) }
    var selectedCategory by remember { mutableStateOf(initialCategory ?: MedicalCategory.EXAMS) }
    var newPersonName by remember { mutableStateOf("") }
    var refreshTick by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            scope.launch { prefs.saveRootUri(uri.toString()) }
        }
    }

    val fmvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.startActivity(Intent(context, PackageImportActivity::class.java).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val person = selectedPerson
        val root = rootUri
        if (uri != null && person != null && root != null) {
            val result = storage.importDocument(root, person, selectedCategory, uri)
            message = if (result.isSuccess) {
                "Το αρχείο αποθηκεύτηκε."
            } else {
                "Αποτυχία: " + (result.exceptionOrNull()?.message ?: "άγνωστο σφάλμα")
            }
            refreshTick++
        }
    }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Ιατρικός Φάκελος") }) }) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (rootUri == null) {
                    Text("Διάλεξε έναν φάκελο που θα χρησιμοποιείται αποκλειστικά ως οικογενειακό ιατρικό αρχείο.")
                    Button(onClick = { treePicker.launch(null) }) { Text("Σύνδεση φακέλου") }
                    Text("Η εφαρμογή δεν ζητά γενική πρόσβαση στα αρχεία και δεν χρησιμοποιεί Internet.")
                    return@Column
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newPersonName,
                        onValueChange = { newPersonName = it },
                        label = { Text("Νέο πρόσωπο") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        if (newPersonName.isNotBlank()) {
                            storage.createPerson(rootUri, newPersonName)
                            newPersonName = ""
                            refreshTick++
                        }
                    }) { Text("Προσθήκη") }
                }

                Button(
                    onClick = { fmvPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Εισαγωγή .fmv από ChatGPT")
                }
                Text(
                    "Διάλεξε το αρχείο .fmv από τις Λήψεις. Η εφαρμογή θα το ελέγξει πριν σου δείξει την επιβεβαίωση.",
                    style = MaterialTheme.typography.bodySmall
                )

                message?.let { Text(it) }

                val people = remember(rootUriString, refreshTick) { storage.listPeople(rootUri) }
                if (selectedPerson == null || people.none { it.name == selectedPerson }) {
                    if (selectedPerson != null) selectedPerson = null
                    Text("Πρόσωπα", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(people, key = { it.uri.toString() }) { person ->
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth().clickable { selectedPerson = person.name }
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(person.name ?: "Χωρίς όνομα", style = MaterialTheme.typography.titleMedium)
                                    Text("Άνοιγμα ιατρικού φακέλου")
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selectedPerson = null }) { Text("← Πρόσωπα") }
                        Spacer(Modifier.width(8.dp))
                        Text(selectedPerson!!, style = MaterialTheme.typography.titleLarge)
                    }

                    CategoryChooser(selectedCategory) { selectedCategory = it }

                    Button(onClick = {
                        filePicker.launch(arrayOf("application/pdf", "image/*", "text/plain"))
                    }) {
                        Text("Προσθήκη αρχείου στην κατηγορία " + selectedCategory.folderName)
                    }

                    val docs = remember(selectedPerson, selectedCategory, refreshTick) {
                        storage.listDocuments(rootUri, selectedPerson!!, selectedCategory)
                    }
                    if (docs.isEmpty()) {
                        Text("Δεν υπάρχουν αρχεία σε αυτή την κατηγορία.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(docs, key = { it.uri.toString() }) { doc -> DocumentRow(doc) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChooser(current: MedicalCategory, onSelected: (MedicalCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MedicalCategory.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { category ->
                    FilterChip(
                        selected = current == category,
                        onClick = { onSelected(category) },
                        label = { Text(category.folderName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentRow(doc: DocumentFile) {
    val context = LocalContext.current
    val mime = doc.type ?: "application/octet-stream"

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(doc.name ?: "Αρχείο", style = MaterialTheme.typography.bodyLarge)
            Text(mime, style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(doc.uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newUri(context.contentResolver, doc.name ?: "Αρχείο", doc.uri)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(viewIntent, "Άνοιγμα με"))
                    }
                }) { Text("Άνοιγμα") }

                TextButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, doc.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newUri(context.contentResolver, doc.name ?: "Αρχείο", doc.uri)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(shareIntent, "Κοινή χρήση"))
                    }
                }) { Text("Κοινή χρήση") }
            }
        }
    }
}
