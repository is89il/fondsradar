package nl.fondsradar.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.fondsradar.app.data.Instellingen
import nl.fondsradar.app.data.Repository
import nl.fondsradar.app.work.Planning
import nl.fondsradar.core.Categorie
import nl.fondsradar.core.Regeling
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun Regeling.volgendeDeadline(vandaag: LocalDate = LocalDate.now()): LocalDate? =
    deadlines.mapNotNull { runCatching { LocalDate.parse(it.datum) }.getOrNull() }
        .filter { !it.isBefore(vandaag) }
        .minOrNull()

fun deadlineTekst(r: Regeling): String {
    val d = r.volgendeDeadline() ?: return if (r.doorlopend) "Doorlopend" else "Geen bekende deadline"
    val dagen = ChronoUnit.DAYS.between(LocalDate.now(), d)
    val wanneer = when (dagen) {
        0L -> "vandaag"
        1L -> "morgen"
        else -> "over $dagen dagen"
    }
    return "${d.dayOfMonth}-${d.monthValue}-${d.year} · $wanneer"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LijstScherm(openDetail: (String) -> Unit, openInstellingen: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val scope = rememberCoroutineScope()
    val alle by repo.regelingen.collectAsStateWithLifecycle()
    val scanBezig by rememberScanBezig()

    var tab by rememberSaveable { mutableStateOf(0) }
    var zoek by rememberSaveable { mutableStateOf("") }
    var categorie by rememberSaveable { mutableStateOf<String?>(null) }

    val vandaag = LocalDate.now()
    val gefilterd = alle
        .filter { !it.gearchiveerd }
        .filter {
            when (tab) {
                1 -> it.interesse
                2 -> it.volgendeDeadline(vandaag)?.let { d -> ChronoUnit.DAYS.between(vandaag, d) <= 90 } == true
                else -> true
            }
        }
        .filter { categorie == null || it.categorieen.any { c -> c.name == categorie } }
        .filter {
            zoek.isBlank() || listOf(it.naam, it.fonds, it.omschrijving, it.notitie, it.projectTitel)
                .any { t -> t.contains(zoek, ignoreCase = true) }
        }
        .sortedWith(compareBy<Regeling> { it.volgendeDeadline(vandaag) ?: LocalDate.MAX }.thenBy { it.fonds })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fondsradar") },
                actions = {
                    IconButton(onClick = openInstellingen) { Icon(Icons.Default.Settings, "Instellingen") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (scanBezig != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    scanBezig!!,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            TabRow(selectedTabIndex = tab) {
                listOf("Alle", "Mijn interesse", "< 3 maanden").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            OutlinedTextField(
                value = zoek,
                onValueChange = { zoek = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Zoek op naam, fonds of notitie") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                items(Categorie.entries.toList()) { c ->
                    FilterChip(
                        selected = categorie == c.name,
                        onClick = { categorie = if (categorie == c.name) null else c.name },
                        label = { Text(c.label) },
                    )
                }
            }
            if (gefilterd.isEmpty()) {
                LegeLijst(
                    heeftRegelingen = alle.isNotEmpty(),
                    heeftSleutel = Instellingen(context).apiKey.isNotBlank(),
                    openInstellingen = openInstellingen,
                    zoekNu = { Planning.startScan(context) },
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(gefilterd, key = { it.id }) { r ->
                        RegelingKaart(
                            r,
                            onClick = { openDetail(r.id) },
                            onSter = { scope.launch { repo.bijwerken(r.id) { it.copy(interesse = !it.interesse) } } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegeLijst(heeftRegelingen: Boolean, heeftSleutel: Boolean, openInstellingen: () -> Unit, zoekNu: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    heeftRegelingen -> "Geen regelingen voor dit filter."
                    !heeftSleutel -> "Stel eerst je Claude API-sleutel in. Daarmee speurt de app elke twee weken het internet af naar regelingen."
                    else -> "Nog geen regelingen. Start de eerste zoektocht."
                },
                textAlign = TextAlign.Center,
            )
            if (!heeftRegelingen) {
                Spacer(Modifier.width(8.dp))
                if (heeftSleutel) TextButton(onClick = zoekNu) { Text("Nu zoeken") }
                else TextButton(onClick = openInstellingen) { Text("Naar instellingen") }
            }
        }
    }
}

@Composable
private fun RegelingKaart(r: Regeling, onClick: () -> Unit, onSter: () -> Unit) {
    val nieuw = r.eersteKeerGezien.isNotBlank() &&
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(r.eersteKeerGezien), LocalDate.now()) <= 14 }
            .getOrDefault(false)
    val dichtbij = r.volgendeDeadline()?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) <= 90 } == true
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (r.interesse) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.fonds, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    if (nieuw) {
                        Spacer(Modifier.width(6.dp))
                        Badge { Text("nieuw") }
                    }
                }
                Text(r.naam, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    deadlineTekst(r),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (dichtbij) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (r.projectTitel.isNotBlank()) {
                    Text("Project: ${r.projectTitel}", style = MaterialTheme.typography.bodySmall)
                }
                if (r.categorieen.isNotEmpty()) {
                    Text(
                        r.categorieen.joinToString(" · ") { it.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onSter) {
                Icon(
                    if (r.interesse) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (r.interesse) "Interesse uitzetten" else "Interesse aanzetten",
                    tint = if (r.interesse) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
