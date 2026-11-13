package nl.fondsradar.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.fondsradar.app.data.Repository
import nl.fondsradar.app.work.Planning
import nl.fondsradar.core.Deadline
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScherm(id: String, terug: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val scope = rememberCoroutineScope()
    val alle by repo.regelingen.collectAsStateWithLifecycle()
    val r = alle.firstOrNull { it.id == id }
    if (r == null) {
        LaunchedEffect(Unit) { terug() }
        return
    }

    // Lokale tekstvelden; worden kort na typen opgeslagen.
    var projectTitel by remember(id) { mutableStateOf(r.projectTitel) }
    var notitie by remember(id) { mutableStateOf(r.notitie) }
    LaunchedEffect(projectTitel, notitie) {
        delay(600)
        if (projectTitel != r.projectTitel || notitie != r.notitie) {
            repo.bijwerken(id) { it.copy(projectTitel = projectTitel, notitie = notitie) }
        }
    }
    var datumKiezen by remember { mutableStateOf(false) }
    var verwijderVragen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(r.fonds, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = terug) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(r.naam, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (r.categorieen.isNotEmpty()) {
                Text(r.categorieen.joinToString(" · ") { it.label }, color = MaterialTheme.colorScheme.secondary)
            }
            if (r.omschrijving.isNotBlank()) Text(r.omschrijving)
            r.bedrag?.takeIf { it.isNotBlank() }?.let { Text("Bedrag: $it", fontWeight = FontWeight.Medium) }
            if (r.status.isNotBlank()) Text("Status: ${r.status}", style = MaterialTheme.typography.bodySmall)

            // ---- Interesse en eigen project ----
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Interesse voor eigen project", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Je krijgt herinneringen vanaf 3 maanden voor de deadline.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = r.interesse,
                            onCheckedChange = { aan ->
                                scope.launch {
                                    repo.bijwerken(id) { it.copy(interesse = aan) }
                                    if (aan) Planning.controleerHerinneringenNu(context)
                                }
                            },
                        )
                    }
                    OutlinedTextField(
                        value = projectTitel,
                        onValueChange = { projectTitel = it },
                        label = { Text("Werktitel project") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = notitie,
                        onValueChange = { notitie = it },
                        label = { Text("Notities: idee, logline, team, wat moet er nog af") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    )
                }
            }

            // ---- Deadlines ----
            Text("Deadlines", style = MaterialTheme.typography.titleMedium)
            if (r.deadlines.isEmpty()) {
                Text(if (r.doorlopend) "Doorlopend indienen mogelijk." else "Nog geen datum bekend.")
            }
            r.deadlines.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.datum, fontWeight = FontWeight.Medium)
                        if (d.omschrijving.isNotBlank()) Text(d.omschrijving, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {
                        scope.launch { repo.bijwerken(id) { it.copy(deadlines = it.deadlines - d) } }
                    }) { Icon(Icons.Default.Close, "Deadline verwijderen") }
                }
            }
            OutlinedButton(onClick = { datumKiezen = true }) {
                Icon(Icons.Default.Add, null)
                Text("Deadline zelf toevoegen")
            }

            // ---- Voorwaarden ----
            if (r.voorwaarden.isNotEmpty()) {
                HorizontalDivider()
                Text("Belangrijkste voorwaarden", style = MaterialTheme.typography.titleMedium)
                r.voorwaarden.forEach { Text("• $it") }
            }

            if (r.url.isNotBlank()) {
                Button(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, r.url.toUri())) }
                }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Text("  Officiële pagina")
                }
            }
            Text(
                "Gevonden op ${r.eersteKeerGezien.ifBlank { "?" }}, laatst gecontroleerd ${r.laatstGezien.ifBlank { "?" }}. " +
                    "Controleer data altijd op de officiële pagina.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { verwijderVragen = true }) { Text("Regeling verbergen") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (datumKiezen) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { datumKiezen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val datum = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        scope.launch {
                            repo.bijwerken(id) {
                                it.copy(deadlines = (it.deadlines + Deadline(datum, "zelf toegevoegd")).sortedBy { d -> d.datum })
                            }
                            Planning.controleerHerinneringenNu(context)
                        }
                    }
                    datumKiezen = false
                }) { Text("Toevoegen") }
            },
            dismissButton = { TextButton(onClick = { datumKiezen = false }) { Text("Annuleren") } },
        ) { DatePicker(state) }
    }

    if (verwijderVragen) {
        AlertDialog(
            onDismissRequest = { verwijderVragen = false },
            title = { Text("Regeling verbergen?") },
            text = { Text("De regeling verdwijnt uit je lijsten en je krijgt er geen herinneringen meer voor. Je notities blijven bewaard.") },
            confirmButton = {
                TextButton(onClick = {
                    verwijderVragen = false
                    scope.launch { repo.bijwerken(id) { it.copy(gearchiveerd = true) } }
                    terug()
                }) { Text("Verbergen") }
            },
            dismissButton = { TextButton(onClick = { verwijderVragen = false }) { Text("Annuleren") } },
        )
    }
}
