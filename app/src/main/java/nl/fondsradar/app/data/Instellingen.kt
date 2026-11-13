package nl.fondsradar.app.data

import android.content.Context
import androidx.core.content.edit
import nl.fondsradar.core.ClaudeScanner

/** Eenvoudige instellingen in app-privé SharedPreferences. */
class Instellingen(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("instellingen", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit { putString("api_key", v.trim()) }

    var model: String
        get() = prefs.getString("model", ClaudeScanner.DEFAULT_MODEL) ?: ClaudeScanner.DEFAULT_MODEL
        set(v) = prefs.edit { putString("model", v) }

    var laatsteScan: String
        get() = prefs.getString("laatste_scan", "") ?: ""
        set(v) = prefs.edit { putString("laatste_scan", v) }

    var laatsteScanStatus: String
        get() = prefs.getString("laatste_scan_status", "") ?: ""
        set(v) = prefs.edit { putString("laatste_scan_status", v) }

    /** Wijzigingen die tijdens de lopende scan verzameld worden (één per regel). */
    var openstaandeMeldingen: List<String>
        get() = prefs.getString("openstaande_meldingen", "")!!.split('\n').filter { it.isNotBlank() }
        set(v) = prefs.edit { putString("openstaande_meldingen", v.joinToString("\n")) }

    var scanFouten: List<String>
        get() = prefs.getString("scan_fouten", "")!!.split('\n').filter { it.isNotBlank() }
        set(v) = prefs.edit { putString("scan_fouten", v.joinToString("\n")) }
}
