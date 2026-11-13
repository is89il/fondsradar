package nl.fondsradar.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import nl.fondsradar.app.data.Instellingen
import nl.fondsradar.app.work.Notificaties
import nl.fondsradar.app.work.Planning

/** Tekst zolang er handmatig wordt opgehaald, anders null. */
@Composable
fun rememberSyncBezig(): State<String?> {
    val context = LocalContext.current
    val flow = remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(Planning.SYNC_NU) }
    return produceState<String?>(null, flow) {
        flow.collect { infos ->
            value = when {
                infos.any { it.state == WorkInfo.State.RUNNING } -> "Regelingen ophalen…"
                infos.any { !it.state.isFinished } -> "Wacht op internetverbinding…"
                else -> null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstellingenScherm(terug: () -> Unit) {
    val context = LocalContext.current
    val instellingen = remember { Instellingen(context) }
    var bron by remember { mutableStateOf(instellingen.bronUrl) }
    val bezig by rememberSyncBezig()
    // Status opnieuw lezen zodra een handmatige sync klaar is.
    var status by remember { mutableStateOf(instellingen.laatsteSync to instellingen.laatsteSyncStatus) }
    LaunchedEffect(bezig) { status = instellingen.laatsteSync to instellingen.laatsteSyncStatus }

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
            Text("Hoe het werkt", style = MaterialTheme.typography.titleMedium)
            Text(
                "Elke twee weken speurt een Claude-routine (binnen je Claude-abonnement) het internet af en " +
                    "publiceert de regelingen in je GitHub-repository. De app haalt dat bestand dagelijks op. " +
                    "Is het bijgewerkt, dan krijg je een melding met nieuwe regelingen en deadlines.",
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider()
            Text("Laatste keer opgehaald", style = MaterialTheme.typography.titleMedium)
            Text(
                if (status.first.isBlank()) "Nog niet opgehaald." else "${status.first}\n${status.second}",
                style = MaterialTheme.typography.bodySmall,
            )
            bezig?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            Button(enabled = bezig == null, onClick = { Planning.syncNu(context) }) { Text("Nu ophalen") }

            HorizontalDivider()
            Text("Bron", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = bron,
                onValueChange = { bron = it },
                label = { Text("Adres van regelingen.json") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                instellingen.bronUrl = bron
                instellingen.verwerkteVersie = ""
                Planning.syncNu(context)
            }) { Text("Opslaan en ophalen") }
            TextButton(onClick = { bron = Instellingen.STANDAARD_BRON }) { Text("Standaardadres herstellen") }

            HorizontalDivider()
            Text("Meldingen", style = MaterialTheme.typography.titleMedium)
            Text(
                "Na een nieuwe zoektocht krijg je een melding met nieuwe regelingen en deadlines. Voor regelingen " +
                    "met interesse krijg je herinneringen 90, 75, 60, 45, 30, 21, 14, 7, 3 en 1 dag(en) voor de deadline.",
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
