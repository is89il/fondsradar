package nl.fondsradar.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import nl.fondsradar.app.work.Notificaties

sealed interface Scherm {
    data object Lijst : Scherm
    data class Detail(val id: String) : Scherm
    data object Instellingen : Scherm
}

class MainActivity : ComponentActivity() {
    private val openId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        openId.value = intent.getStringExtra(Notificaties.EXTRA_REGELING_ID)
        setContent { FondsradarThema { App(openId.value) { openId.value = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openId.value = intent.getStringExtra(Notificaties.EXTRA_REGELING_ID)
    }
}

@Composable
private fun App(teOpenen: String?, geopend: () -> Unit) {
    var scherm: Scherm by rememberSaveableScherm()
    if (teOpenen != null) {
        scherm = Scherm.Detail(teOpenen)
        geopend()
    }
    BackHandler(enabled = scherm != Scherm.Lijst) { scherm = Scherm.Lijst }
    when (val s = scherm) {
        Scherm.Lijst -> LijstScherm(
            openDetail = { scherm = Scherm.Detail(it) },
            openInstellingen = { scherm = Scherm.Instellingen },
        )
        is Scherm.Detail -> DetailScherm(s.id, terug = { scherm = Scherm.Lijst })
        Scherm.Instellingen -> InstellingenScherm(terug = { scherm = Scherm.Lijst })
    }
}

@Composable
private fun rememberSaveableScherm() = rememberSaveable(
    saver = androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.MutableState<Scherm>, String>(
        save = {
            when (val s = it.value) {
                Scherm.Lijst -> "lijst"
                Scherm.Instellingen -> "instellingen"
                is Scherm.Detail -> "detail:${s.id}"
            }
        },
        restore = {
            mutableStateOf(
                when {
                    it == "instellingen" -> Scherm.Instellingen
                    it.startsWith("detail:") -> Scherm.Detail(it.removePrefix("detail:"))
                    else -> Scherm.Lijst
                },
            )
        },
    ),
) { mutableStateOf<Scherm>(Scherm.Lijst) }

@Composable
fun FondsradarThema(content: @Composable () -> Unit) {
    val goud = Color(0xFFF5C24B)
    val nacht = Color(0xFF1F2A44)
    val schema = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = goud, onPrimary = nacht, secondary = Color(0xFF9FB3D9))
    } else {
        lightColorScheme(primary = nacht, onPrimary = Color.White, secondary = Color(0xFF8A6A12), tertiary = goud)
    }
    MaterialTheme(colorScheme = schema, content = content)
}
