package nl.fondsradar.core

import java.text.Normalizer
import java.time.LocalDate

sealed interface Wijziging {
    val regeling: Regeling

    data class NieuweRegeling(override val regeling: Regeling) : Wijziging
    data class NieuweDeadlines(override val regeling: Regeling, val deadlines: List<Deadline>) : Wijziging
}

data class MergeResultaat(val regelingen: List<Regeling>, val wijzigingen: List<Wijziging>)

object Merger {
    fun sleutel(fonds: String, naam: String): String = normaliseer("$fonds $naam")

    private fun normaliseer(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    private fun geldigeDatum(s: String): Boolean =
        runCatching { LocalDate.parse(s) }.isSuccess

    /**
     * Voegt scanresultaten samen met de bestaande lijst. Gebruikersvelden (interesse,
     * notities, verstuurde herinneringen) blijven altijd behouden. Regelingen die
     * niet meer gevonden worden blijven staan.
     */
    fun merge(
        bestaand: List<Regeling>,
        gevonden: List<GevondenRegeling>,
        vandaag: LocalDate,
    ): MergeResultaat {
        val perId = LinkedHashMap<String, Regeling>()
        bestaand.forEach { perId[it.id] = it }
        val wijzigingen = mutableListOf<Wijziging>()
        val iso = vandaag.toString()

        for (g in gevonden) {
            if (g.naam.isBlank()) continue
            val id = sleutel(g.fonds, g.naam)
            val deadlines = g.deadlines
                .filter { geldigeDatum(it.datum) }
                .distinctBy { it.datum }
                .sortedBy { it.datum }
            val oud = perId[id]
            if (oud == null) {
                val nieuw = Regeling(
                    id = id,
                    naam = g.naam.trim(),
                    fonds = g.fonds.trim(),
                    categorieen = g.categorieen.map(Categorie::parse).distinct(),
                    omschrijving = g.omschrijving,
                    voorwaarden = g.voorwaarden,
                    bedrag = g.bedrag,
                    deadlines = deadlines,
                    doorlopend = g.doorlopend,
                    url = g.url,
                    status = g.status,
                    eersteKeerGezien = iso,
                    laatstGezien = iso,
                )
                perId[id] = nieuw
                wijzigingen += Wijziging.NieuweRegeling(nieuw)
            } else {
                val oudeData = oud.deadlines.map { it.datum }.toSet()
                val nieuweDeadlines = deadlines.filter { it.datum !in oudeData && it.datum >= iso }
                // Behoud oude deadlines die niet meer genoemd worden (geschiedenis), voeg nieuwe toe.
                val samengevoegd = (oud.deadlines.filter { d -> deadlines.none { it.datum == d.datum } } + deadlines)
                    .sortedBy { it.datum }
                val bijgewerkt = oud.copy(
                    naam = g.naam.trim(),
                    fonds = g.fonds.trim(),
                    categorieen = g.categorieen.map(Categorie::parse).distinct().ifEmpty { oud.categorieen },
                    omschrijving = g.omschrijving.ifBlank { oud.omschrijving },
                    voorwaarden = g.voorwaarden.ifEmpty { oud.voorwaarden },
                    bedrag = g.bedrag ?: oud.bedrag,
                    deadlines = samengevoegd,
                    doorlopend = g.doorlopend,
                    url = g.url.ifBlank { oud.url },
                    status = g.status.ifBlank { oud.status },
                    laatstGezien = iso,
                )
                perId[id] = bijgewerkt
                if (nieuweDeadlines.isNotEmpty() && !oud.gearchiveerd) {
                    wijzigingen += Wijziging.NieuweDeadlines(bijgewerkt, nieuweDeadlines)
                }
            }
        }
        return MergeResultaat(perId.values.toList(), wijzigingen)
    }
}
