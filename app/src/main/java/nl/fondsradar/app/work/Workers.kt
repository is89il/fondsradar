package nl.fondsradar.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.fondsradar.app.data.Instellingen
import nl.fondsradar.app.data.Repository
import nl.fondsradar.core.FeedParser
import nl.fondsradar.core.HerinneringPlanner
import nl.fondsradar.core.Wijziging
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Haalt regelingen.json op, voegt samen en meldt nieuwe regelingen en deadlines. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val instellingen = Instellingen(applicationContext)
        val nu = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d-M-yyyy HH:mm"))
        return try {
            val tekst = withContext(Dispatchers.IO) { download(instellingen.bronUrl) }
            val feed = FeedParser.parse(tekst)
            instellingen.laatsteSync = nu
            if (feed.bijgewerkt.isNotBlank() && feed.bijgewerkt == instellingen.verwerkteVersie) {
                instellingen.laatsteSyncStatus = "Geen nieuwe zoektocht sinds ${feed.bijgewerkt}."
                return Result.success()
            }
            val wijzigingen = Repository.get(applicationContext).voegScanToe(feed.regelingen)
            instellingen.verwerkteVersie = feed.bijgewerkt
            meld(wijzigingen)
            val nieuw = wijzigingen.count { it is Wijziging.NieuweRegeling }
            instellingen.laatsteSyncStatus =
                "Zoektocht van ${feed.bijgewerkt.ifBlank { "?" }}: $nieuw nieuwe regeling(en), " +
                "${wijzigingen.size - nieuw} met nieuwe deadline(s)."
            Planning.controleerHerinneringenNu(applicationContext)
            Result.success()
        } catch (e: Exception) {
            instellingen.laatsteSync = nu
            instellingen.laatsteSyncStatus = "Ophalen mislukt: ${e.message?.take(160) ?: e.javaClass.simpleName}"
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    private fun download(adres: String): String {
        val verbinding = URL(adres).openConnection() as HttpURLConnection
        verbinding.connectTimeout = 20_000
        verbinding.readTimeout = 30_000
        verbinding.useCaches = false
        try {
            val code = verbinding.responseCode
            if (code == 404) throw IllegalStateException("nog geen regelingen.json gepubliceerd (404)")
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return verbinding.inputStream.bufferedReader().use { it.readText() }
        } finally {
            verbinding.disconnect()
        }
    }

    private fun meld(wijzigingen: List<Wijziging>) {
        if (wijzigingen.isEmpty()) return
        val vandaag = LocalDate.now().toString()
        val nieuw = wijzigingen.filterIsInstance<Wijziging.NieuweRegeling>()
        val deadlines = wijzigingen.filterIsInstance<Wijziging.NieuweDeadlines>()
        val regels = nieuw.map { w ->
            val d = w.regeling.deadlines.firstOrNull { it.datum >= vandaag }?.datum
            "${w.regeling.fonds}: ${w.regeling.naam}" + (d?.let { " (deadline $it)" } ?: "")
        } + deadlines.map { w -> "${w.regeling.naam}: ${w.deadlines.joinToString(", ") { it.datum }}" }
        val titel = listOfNotNull(
            nieuw.size.takeIf { it > 0 }?.let { "$it nieuwe regeling" + if (it > 1) "en" else "" },
            deadlines.size.takeIf { it > 0 }?.let { "nieuwe deadlines bij $it regeling" + if (it > 1) "en" else "" },
        ).joinToString(" en ").replaceFirstChar { it.uppercase() }
        Notificaties.toon(
            applicationContext, Notificaties.KANAAL_NIEUW, 1000,
            titel = titel,
            tekst = "De tweewekelijkse zoektocht heeft nieuws.",
            regels = regels,
            regelingId = wijzigingen.singleOrNull()?.regeling?.id,
        )
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
