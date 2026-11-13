package nl.fondsradar.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import nl.fondsradar.app.R
import nl.fondsradar.app.ui.MainActivity

object Notificaties {
    const val KANAAL_NIEUW = "nieuw"
    const val KANAAL_HERINNERING = "herinnering"
    const val EXTRA_REGELING_ID = "regeling_id"

    fun maakKanalen(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(KANAAL_NIEUW, "Nieuwe regelingen en deadlines", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Meldingen na de tweewekelijkse zoektocht" },
        )
        nm.createNotificationChannel(
            NotificationChannel(KANAAL_HERINNERING, "Deadline-herinneringen", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Herinneringen voor regelingen waar je interesse in hebt" },
        )
    }

    private fun openIntent(context: Context, regelingId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            regelingId?.let { putExtra(EXTRA_REGELING_ID, it) }
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun mag(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33

    fun toon(
        context: Context,
        kanaal: String,
        id: Int,
        titel: String,
        tekst: String,
        regels: List<String> = emptyList(),
        regelingId: String? = null,
    ) {
        if (!mag(context)) return
        val builder = NotificationCompat.Builder(context, kanaal)
            .setSmallIcon(R.drawable.ic_stat_film)
            .setContentTitle(titel)
            .setContentText(tekst)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, regelingId, id))
        if (regels.isNotEmpty()) {
            val stijl = NotificationCompat.InboxStyle().setSummaryText(tekst)
            regels.take(7).forEach { stijl.addLine(it) }
            if (regels.size > 7) stijl.addLine("… en ${regels.size - 7} meer")
            builder.setStyle(stijl)
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(tekst))
        }
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
        }
    }
}
