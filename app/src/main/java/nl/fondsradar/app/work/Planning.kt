package nl.fondsradar.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import nl.fondsradar.core.ClaudeScanner
import java.util.concurrent.TimeUnit

object Planning {
    const val SCAN_KETEN = "scan-keten"
    private const val SCAN_PERIODIEK = "scan-periodiek"
    private const val HERINNERING_PERIODIEK = "herinnering-periodiek"

    private val netwerk = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Plant de tweewekelijkse zoektocht en de dagelijkse herinneringscheck. */
    fun planAlles(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            SCAN_PERIODIEK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ScanStarter>(14, TimeUnit.DAYS)
                .setConstraints(netwerk)
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            HERINNERING_PERIODIEK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HerinneringWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    /**
     * Start een volledige zoektocht: per brongroep één taak (elk ruim binnen de
     * tien minuten die Android een achtergrondtaak geeft), daarna een afrondtaak die
     * de meldingen verstuurt.
     */
    fun startScan(context: Context) {
        val stappen = ClaudeScanner.STARTBRONNEN.indices.map { i ->
            OneTimeWorkRequestBuilder<ScanStapWorker>()
                .setConstraints(netwerk)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                .setInputData(workDataOf(ScanStapWorker.STAP to i))
                .addTag(SCAN_KETEN)
                .build()
        }
        val afronden = OneTimeWorkRequestBuilder<ScanAfrondenWorker>().addTag(SCAN_KETEN).build()
        var keten = WorkManager.getInstance(context)
            .beginUniqueWork(SCAN_KETEN, ExistingWorkPolicy.KEEP, stappen.first())
        stappen.drop(1).forEach { keten = keten.then(it) }
        keten.then(afronden).enqueue()
    }

    fun controleerHerinneringenNu(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<HerinneringWorker>().build())
    }
}
