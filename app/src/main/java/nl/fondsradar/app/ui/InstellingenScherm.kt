package nl.fondsradar.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import nl.fondsradar.app.data.Instellingen
import nl.fondsradar.app.data.Repository
import nl.fondsradar.app.work.Notificaties
import nl.fondsradar.app.work.Planning
import nl.fondsradar.app.work.ScanStapWorker
import nl.fondsradar.core.ClaudeScanner

/** Tekst over de lopende zoektocht, of null als er niets loopt. */
@Composable
fun rememberScanBezig(): State<String?> {
    val context = LocalContext.current
    val flow = remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(Planning.SCAN_KETEN) }
    return produceState<String?>(null, flow) {
        flow.collect { infos ->
            val actief = infos.any { !it.state.isFinished && it.state != WorkInfo.State.CANCELLED }
            value = if (!actief) null else {
                val lopend = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                val stap = lopend?.progress?.getInt(ScanStapWorker.VOORTGANG, -1) ?: -1
                val totaal = ClaudeScanner.STARTBRONNEN.size
                if (stap >= 0) "Zoeken… ronde ${stap + 1} van $totaal" else "Zoektocht ingepland (wacht op netwerk)…"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstellingenScherm(terug: () -> Unit) {
    val context = LocalContext.current
    val instellingen = remember { Instellingen(context) }
    var sleutel by remember { mutableStateOf(instellingen.apiKey) }
    var model by remember { mutableStateOf(instellingen.model) }
    val scanBezig by rememberScanBezig()
    val regelingen by Repository.get(context).regelingen.collectAsStateWithLifecycle()
    val aantal = regelingen.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Instellingen") },
                navigationIcon = { IconButton(onClick = terug) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Claude API-sleutel", style = MaterialTheme.typography.titleMedium)
            Text(
                "De app gebruikt Claude (met web search) om elke twee weken het internet af te speuren. " +
                    "Maak een sleutel aan op console.anthropic.com. De sleutel blijft alleen op deze telefoon.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = sleutel,
                onValueChange = { sleutel = it },
                label = { Text("sk-ant-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                val eersteKeer = instellingen.apiKey.isBlank() && sleutel.isNotBlank()
                instellingen.apiKey = sleutel
                if (eersteKeer && aantal == 0) Planning.startScan(context)
            }) { Text("Opslaan") }

            HorizontalDivider()
            Text("Model", style = MaterialTheme.typography.titleMedium)
            ClaudeScanner.MODELLEN.forEach { (id, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = model == id, onClick = { model = id; instellingen.model = id })
                    Text(label)
                }
            }

            HorizontalDivider()
            Text("Zoektocht", style = MaterialTheme.typography.titleMedium)
            Text("Automatisch elke 14 dagen, in ${ClaudeScanner.STARTBRONNEN.size} rondes per brongroep.")
            Text(
                if (instellingen.laatsteScan.isBlank()) "Nog niet gezocht."
                else "Laatste zoektocht: ${instellingen.laatsteScan}\n${instellingen.laatsteScanStatus}",
                style = MaterialTheme.typography.bodySmall,
            )
            scanBezig?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            Button(
                enabled = scanBezig == null && instellingen.apiKey.isNotBlank(),
                onClick = { Planning.startScan(context) },
            ) { Text("Nu zoeken") }
            if (scanBezig != null) {
                OutlinedButton(onClick = {
                    WorkManager.getInstance(context).cancelUniqueWork(Planning.SCAN_KETEN)
                }) { Text("Zoektocht stoppen") }
            }

            HorizontalDivider()
            Text("Meldingen", style = MaterialTheme.typography.titleMedium)
            Text(
                "Je krijgt een melding na elke zoektocht met nieuwe regelingen of deadlines, en voor regelingen " +
                    "met interesse herinneringen 90, 75, 60, 45, 30, 21, 14, 7, 3 en 1 dag(en) voor de deadline.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = {
                Notificaties.toon(
                    context, Notificaties.KANAAL_HERINNERING, 999,
                    "Testmelding", "Meldingen van Fondsradar werken.",
                )
            }) { Text("Testmelding sturen") }
            OutlinedButton(onClick = { Planning.controleerHerinneringenNu(context) }) {
                Text("Herinneringen nu controleren")
            }
        }
    }
}
