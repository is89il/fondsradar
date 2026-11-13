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
import java.util.concurrent.TimeUnit

object Planning {
    const val SYNC_NU = "sync-nu"
    private const val SYNC_PERIODIEK = "sync-periodiek"
    private const val HERINNERING_PERIODIEK = "herinnering-periodiek"

    private val netwerk = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Haalt elke dag regelingen.json op (een paar kB, gratis). Alleen als de
     * Claude-routine het bestand heeft bijgewerkt volgen er meldingen.
     */
    fun planAlles(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            SYNC_PERIODIEK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(netwerk)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            HERINNERING_PERIODIEK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HerinneringWorker>(1, TimeUnit.DAYS).build(),
        )
        // Opruimen van taken uit de eerste versie (zoeken via de API).
        wm.cancelUniqueWork("scan-periodiek")
        wm.cancelUniqueWork("scan-keten")
    }

    fun syncNu(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_NU,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(netwerk).build(),
        )
    }

    fun controleerHerinneringenNu(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<HerinneringWorker>().build())
    }
}
