package nl.fondsradar.app.data

import android.content.Context
import androidx.core.content.edit

/** Eenvoudige instellingen in app-privé SharedPreferences. */
class Instellingen(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("instellingen", Context.MODE_PRIVATE)

    companion object {
        const val STANDAARD_BRON =
            "https://raw.githubusercontent.com/is89il/fondsradar/main/data/regelingen.json"
    }

    /** Adres van regelingen.json, bijgehouden door de Claude-routine. */
    var bronUrl: String
        get() = prefs.getString("bron_url", STANDAARD_BRON)!!.ifBlank { STANDAARD_BRON }
        set(v) = prefs.edit { putString("bron_url", v.trim()) }

    /** Het veld `bijgewerkt` van de laatst verwerkte versie. */
    var verwerkteVersie: String
        get() = prefs.getString("verwerkte_versie", "") ?: ""
        set(v) = prefs.edit { putString("verwerkte_versie", v) }

    var laatsteSync: String
        get() = prefs.getString("laatste_sync", "") ?: ""
        set(v) = prefs.edit { putString("laatste_sync", v) }

    var laatsteSyncStatus: String
        get() = prefs.getString("laatste_sync_status", "") ?: ""
        set(v) = prefs.edit { putString("laatste_sync_status", v) }
}
