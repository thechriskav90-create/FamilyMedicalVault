package com.familymedicalvault.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familymedicalvault.app.data.VaultPreferences
import com.familymedicalvault.app.data.VaultStorage
import com.familymedicalvault.app.model.MedicalCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedUri = extractSharedUri(intent)
        val sharedText = if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        setContent {
            MaterialTheme {
                ShareImportScreen(
                    sharedUri = sharedUri,
                    sharedText = sharedText,
                    onDone = { finish() },
                    onOpenMain = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }

    private fun extractSharedUri(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_SEND) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareImportScreen(
    sharedUri: Uri?,
    sharedText: String?,
    onDone: () -> Unit,
    onOpenMain: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { VaultPreferences(context) }
    val storage = remember { VaultStorage(context) }
    val rootUriString by prefs.rootUri.collectAsStateWithLifecycle(initialValue = null)
    val rootUri = rootUriString?.let(Uri::parse)

    var selectedPerson by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf(MedicalCategory.EXAMS) }
    var status by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Αποθήκευση στον Ιατρικό Φάκελο") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sharedUri == null && sharedText.isNullOrBlank()) {
                Text("Δεν βρέθηκε αρχείο ή κείμενο για εισαγωγή.")
                Button(onClick = onDone) { Text("Κλείσιμο") }
                return@Column
            }

            if (rootUri == null) {
                Text("Δεν έχει συνδεθεί ακόμη ο οικογενειακός φάκελος.")
                Button(onClick = onOpenMain) { Text("Άνοιγμα εφαρμογής") }
                return@Column
            }

            val people = remember(rootUriString) { storage.listPeople(rootUri) }
            if (people.isEmpty()) {
                Text("Δεν υπάρχουν ακόμη πρόσωπα στον ιατρικό φάκελο.")
                Button(onClick = onOpenMain) { Text("Προσθήκη προσώπου") }
                return@Column
            }

            Text("1. Ποιον αφορά;", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(people, key = { it.uri.toString() }) { person ->
                    PersonChoice(
                        person = person,
                        selected = selectedPerson == person.name,
                        onClick = { selectedPerson = person.name }
                    )
                }
            }

            Text("2. Κατηγορία", style = MaterialTheme.typography.titleMedium)
            MedicalCategory.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category.folderName) }
                        )
                    }
                }
            }

            status?.let { Text(it) }

            Button(
                onClick = {
                    val person = selectedPerson ?: return@Button
                    val result = if (sharedUri != null) {
                        storage.importDocument(rootUri, person, selectedCategory, sharedUri)
                    } else {
                        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                        storage.importText(
                            rootUri,
                            person,
                            selectedCategory,
                            sharedText.orEmpty(),
                            "κείμενο_" + stamp + ".txt"
                        )
                    }

                    status = if (result.isSuccess) {
                        "Το αρχείο αποθηκεύτηκε στο " + person + " / " + selectedCategory.folderName + "."
                    } else {
                        "Αποτυχία αποθήκευσης: " + (result.exceptionOrNull()?.message ?: "άγνωστο σφάλμα")
                    }
                },
                enabled = selectedPerson != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Αποθήκευση")
            }

            if (status?.startsWith("Το αρχείο αποθηκεύτηκε") == true) {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Τέλος")
                }
            }
        }
    }
}

@Composable
private fun PersonChoice(person: DocumentFile, selected: Boolean, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(person.name ?: "Χωρίς όνομα", style = MaterialTheme.typography.titleMedium)
            Text(if (selected) "Επιλεγμένο" else "Πάτησε για επιλογή")
        }
    }
}
