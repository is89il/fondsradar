package nl.fondsradar.app

import android.app.Application
import nl.fondsradar.app.work.Notificaties
import nl.fondsradar.app.work.Planning

class FondsradarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notificaties.maakKanalen(this)
        Planning.planAlles(this)
    }
}
