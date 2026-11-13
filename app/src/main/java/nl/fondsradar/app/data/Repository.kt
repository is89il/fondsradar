package nl.fondsradar.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.fondsradar.core.GevondenRegeling
import nl.fondsradar.core.Merger
import nl.fondsradar.core.Regeling
import nl.fondsradar.core.Wijziging
import java.io.File
import java.time.LocalDate

@Serializable
private data class Opslag(val regelingen: List<Regeling> = emptyList())

/**
 * Bewaart alle regelingen als JSON-bestand in de app-opslag. Eén instantie per
 * proces, gedeeld door de UI en de achtergrondtaken.
 */
class Repository private constructor(context: Context) {
    private val bestand = File(context.filesDir, "regelingen.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }
    private val mutex = Mutex()
    private val _regelingen = MutableStateFlow(laad())
    val regelingen: StateFlow<List<Regeling>> = _regelingen.asStateFlow()

    private fun laad(): List<Regeling> = runCatching {
        if (bestand.exists()) json.decodeFromString(Opslag.serializer(), bestand.readText()).regelingen else emptyList()
    }.getOrDefault(emptyList())

    private fun schrijf(lijst: List<Regeling>) {
        val tmp = File(bestand.parentFile, "regelingen.json.tmp")
        tmp.writeText(json.encodeToString(Opslag.serializer(), Opslag(lijst)))
        tmp.renameTo(bestand)
        _regelingen.value = lijst
    }

    suspend fun bijwerken(id: String, f: (Regeling) -> Regeling) = withContext(Dispatchers.IO) {
        mutex.withLock { schrijf(_regelingen.value.map { if (it.id == id) f(it) else it }) }
    }

    suspend fun bijwerkenAlle(f: (List<Regeling>) -> List<Regeling>) = withContext(Dispatchers.IO) {
        mutex.withLock { schrijf(f(_regelingen.value)) }
    }

    suspend fun voegScanToe(gevonden: List<GevondenRegeling>): List<Wijziging> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val resultaat = Merger.merge(_regelingen.value, gevonden, LocalDate.now())
            schrijf(resultaat.regelingen)
            resultaat.wijzigingen
        }
    }

    suspend fun verwijder(id: String) = bijwerkenAlle { lijst -> lijst.filterNot { it.id == id } }

    companion object {
        @Volatile private var instantie: Repository? = null
        fun get(context: Context): Repository =
            instantie ?: synchronized(this) {
                instantie ?: Repository(context.applicationContext).also { instantie = it }
            }
    }
}
