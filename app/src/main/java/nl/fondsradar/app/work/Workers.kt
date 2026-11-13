package nl.fondsradar.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.fondsradar.app.data.Instellingen
import nl.fondsradar.app.data.Repository
import nl.fondsradar.core.ClaudeScanner
import nl.fondsradar.core.HerinneringPlanner
import nl.fondsradar.core.Wijziging
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Periodieke taak (elke 14 dagen) die de zoekketen start. */
class ScanStarter(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Zonder API-sleutel valt er niets te zoeken; dan ook geen foutmelding sturen.
        if (Instellingen(applicationContext).apiKey.isNotBlank()) Planning.startScan(applicationContext)
        return Result.success()
    }
}

/** Doorzoekt één brongroep met Claude en voegt de resultaten samen. */
class ScanStapWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val STAP = "stap"
        const val VOORTGANG = "voortgang"
    }

    override suspend fun doWork(): Result {
        val stap = inputData.getInt(STAP, 0)
        val instellingen = Instellingen(applicationContext)
        if (stap == 0 && runAttemptCount == 0) {
            instellingen.openstaandeMeldingen = emptyList()
            instellingen.scanFouten = emptyList()
        }
        setProgress(workDataOf(VOORTGANG to stap))
        val sleutel = instellingen.apiKey
        if (sleutel.isBlank()) {
            if (stap == 0) instellingen.scanFouten = listOf("Geen API-sleutel ingesteld (zie Instellingen).")
            return Result.success()
        }
        val repo = Repository.get(applicationContext)
        val focus = ClaudeScanner.STARTBRONNEN[stap]
        return try {
            val gevonden = withContext(Dispatchers.IO) {
                ClaudeScanner(sleutel, instellingen.model).scan(repo.regelingen.value, focus, LocalDate.now())
            }
            val wijzigingen = repo.voegScanToe(gevonden)
            instellingen.openstaandeMeldingen = instellingen.openstaandeMeldingen + wijzigingen.map(::beschrijf)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                // Niet laten falen: dan zou de rest van de keten geannuleerd worden.
                instellingen.scanFouten = instellingen.scanFouten +
                    "Ronde ${stap + 1}: ${e.message?.take(160) ?: e.javaClass.simpleName}"
                Result.success()
            }
        }
    }

    private fun beschrijf(w: Wijziging): String = when (w) {
        is Wijziging.NieuweRegeling -> {
            val d = w.regeling.deadlines.firstOrNull { it.datum >= LocalDate.now().toString() }?.datum
            "NIEUW|${w.regeling.id}|${w.regeling.fonds}: ${w.regeling.naam}" + (d?.let { " (deadline $it)" } ?: "")
        }
        is Wijziging.NieuweDeadlines ->
            "DEADLINE|${w.regeling.id}|${w.regeling.naam}: ${w.deadlines.joinToString(", ") { it.datum }}"
    }
}

/** Verstuurt na een volledige zoektocht één samenvattende melding. */
class ScanAfrondenWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val instellingen = Instellingen(applicationContext)
        val meldingen = instellingen.openstaandeMeldingen.map { it.split('|', limit = 3) }.filter { it.size == 3 }
        val fouten = instellingen.scanFouten
        val nieuw = meldingen.filter { it[0] == "NIEUW" }
        val deadlines = meldingen.filter { it[0] == "DEADLINE" }

        instellingen.laatsteScan = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d-M-yyyy HH:mm"))
        instellingen.laatsteScanStatus = buildString {
            append("${nieuw.size} nieuwe regeling(en), ${deadlines.size} nieuwe deadline(s)")
            if (fouten.isNotEmpty()) append(". Problemen: ").append(fouten.joinToString("; "))
        }

        if (meldingen.isNotEmpty()) {
            val titel = listOfNotNull(
                nieuw.size.takeIf { it > 0 }?.let { "$it nieuwe regeling" + if (it > 1) "en" else "" },
                deadlines.size.takeIf { it > 0 }?.let { "$it nieuwe deadline" + if (it > 1) "s" else "" },
            ).joinToString(" en ")
            Notificaties.toon(
                applicationContext, Notificaties.KANAAL_NIEUW, 1000,
                titel = titel.replaceFirstChar { it.uppercase() },
                tekst = "Fondsradar heeft het internet afgespeurd.",
                regels = (nieuw + deadlines).map { it[2] },
                regelingId = meldingen.singleOrNull()?.get(1),
            )
        } else if (fouten.isNotEmpty()) {
            Notificaties.toon(
                applicationContext, Notificaties.KANAAL_NIEUW, 1001,
                titel = "Zoektocht niet helemaal gelukt",
                tekst = fouten.joinToString("\n"),
            )
        }
        instellingen.openstaandeMeldingen = emptyList()
        Planning.controleerHerinneringenNu(applicationContext)
        return Result.success()
    }
}

/** Dagelijkse check: herinneringen vanaf 3 maanden voor een deadline. */
class HerinneringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        val teVersturen = HerinneringPlanner.teVersturen(repo.regelingen.value, LocalDate.now())
        for (h in teVersturen) {
            val titel = h.regeling.projectTitel.takeIf { it.isNotBlank() }
                ?.let { "$it → ${h.regeling.naam}" } ?: h.regeling.naam
            Notificaties.toon(
                applicationContext, Notificaties.KANAAL_HERINNERING,
                id = 2000 + (h.regeling.id.hashCode() and 0xFFFF),
                titel = titel,
                tekst = HerinneringPlanner.boodschap(h),
                regelingId = h.regeling.id,
            )
            repo.bijwerken(h.regeling.id) { it.copy(verstuurdeHerinneringen = it.verstuurdeHerinneringen + h.sleutel) }
        }
        return Result.success()
    }
}
