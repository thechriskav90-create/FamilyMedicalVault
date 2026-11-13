package com.familymedicalvault.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familymedicalvault.app.data.VaultPreferences
import com.familymedicalvault.app.data.VaultStorage
import com.familymedicalvault.app.model.MedicalCategory
import com.familymedicalvault.app.packageio.FmvManifest
import com.familymedicalvault.app.packageio.FmvPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PackageImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageUri = intent.data

        setContent {
            MaterialTheme {
                PackageImportScreen(
                    packageUri = packageUri,
                    onDone = { finish() },
                    onOpenMain = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onOpenImported = { person, category ->
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            putExtra(MainActivity.EXTRA_PERSON, person)
                            putExtra(MainActivity.EXTRA_CATEGORY, category.folderName)
                        })
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackageImportScreen(
    packageUri: Uri?,
    onDone: () -> Unit,
    onOpenMain: () -> Unit,
    onOpenImported: (String, MedicalCategory) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { VaultPreferences(context) }
    val storage = remember { VaultStorage(context) }
    val rootUriString by prefs.rootUri.collectAsStateWithLifecycle(initialValue = null)
    val rootUri = rootUriString?.let(Uri::parse)
    val scope = rememberCoroutineScope()

    var manifest by remember { mutableStateOf<FmvManifest?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf(false) }

    LaunchedEffect(packageUri) {
        loading = true
        errorText = null
        manifest = null

        if (packageUri == null) {
            errorText = "Δεν βρέθηκε πακέτο .fmv."
            loading = false
            return@LaunchedEffect
        }

        val result = withContext(Dispatchers.IO) {
            FmvPackage.validate {
                context.contentResolver.openInputStream(packageUri)
                    ?: error("Αδυναμία ανάγνωσης πακέτου")
            }
        }

        result
            .onSuccess { manifest = it }
            .onFailure { errorText = it.message ?: "Μη έγκυρο πακέτο .fmv." }

        loading = false
    }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Εισαγωγή από ChatGPT") }) }) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator()
                    Text("Έλεγχος πακέτου…")
                    return@Column
                }

                errorText?.let {
                    Text("Το πακέτο δεν μπορεί να εισαχθεί: " + it)
                    Button(onClick = onDone) { Text("Κλείσιμο") }
                    return@Column
                }

                if (rootUri == null) {
                    Text("Δεν έχει συνδεθεί ακόμη ο οικογενειακός φάκελος.")
                    Text("Άνοιξε πρώτα την εφαρμογή και επίλεξε τον κεντρικό φάκελο.")
                    Button(onClick = onOpenMain) { Text("Άνοιγμα εφαρμογής") }
                    return@Column
                }

                val item = manifest ?: return@Column
                val category = MedicalCategory.fromFolderName(item.category) ?: return@Column

                Text("Έτοιμο για εισαγωγή", style = MaterialTheme.typography.titleLarge)
                InfoRow("Πρόσωπο", item.person)
                InfoRow("Κατηγορία", item.category)
                InfoRow("Όνομα αρχείου", item.displayName)
                InfoRow("Τύπος αρχείου", item.mimeType)
                InfoRow("Προέλευση", item.source)

                if (!imported) {
                    Button(
                        onClick = {
                            val uri = packageUri ?: return@Button
                            importing = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    storage.importWithWriter(
                                        rootUri = rootUri,
                                        personFolder = item.person,
                                        category = category,
                                        preferredName = item.displayName,
                                        mimeType = item.mimeType
                                    ) { output ->
                                        FmvPackage.copyPayload(
                                            openStream = {
                                                context.contentResolver.openInputStream(uri)
                                                    ?: error("Αδυναμία ανάγνωσης πακέτου")
                                            },
                                            manifest = item,
                                            output = output
                                        ).getOrThrow()
                                    }
                                }

                                importing = false
                                if (result.isSuccess) {
                                    imported = true
                                } else {
                                    errorText = result.exceptionOrNull()?.message ?: "Αποτυχία εισαγωγής."
                                }
                            }
                        },
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (importing) "Εισαγωγή…" else "Εισαγωγή")
                    }

                    OutlinedButton(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !importing
                    ) {
                        Text("Ακύρωση")
                    }
                } else {
                    Text("Η εισαγωγή ολοκληρώθηκε.")

                    Button(
                        onClick = { onOpenImported(item.person, category) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Άνοιγμα στον Ιατρικό Φάκελο")
                    }

                    OutlinedButton(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Τέλος")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
