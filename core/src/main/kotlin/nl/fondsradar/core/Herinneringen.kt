package nl.fondsradar.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Herinnering(
    val regeling: Regeling,
    val deadline: Deadline,
    val dagenTeGaan: Long,
    /** Mijlpaal (in dagen vooraf) waarvoor deze herinnering geldt. */
    val mijlpaal: Int,
) {
    val sleutel: String get() = "${deadline.datum}@$mijlpaal"
}

object HerinneringPlanner {
    /** Vanaf 3 maanden vooraf: eerst om de twee weken, daarna steeds vaker. */
    val MIJLPALEN = listOf(90, 75, 60, 45, 30, 21, 14, 7, 3, 1)

    /**
     * Geeft per regeling met interesse hooguit één herinnering terug: voor de
     * eerstvolgende deadline binnen 90 dagen, bij de kleinste mijlpaal die al
     * bereikt is en nog niet verstuurd werd. Zo krijg je na een paar dagen
     * zonder controle (telefoon uit) niet een hele reeks tegelijk.
     */
    fun teVersturen(regelingen: List<Regeling>, vandaag: LocalDate): List<Herinnering> =
        regelingen.filter { it.interesse && !it.gearchiveerd }.mapNotNull { r ->
            val volgende = r.deadlines
                .mapNotNull { d -> runCatching { d to LocalDate.parse(d.datum) }.getOrNull() }
                .filter { (_, datum) -> !datum.isBefore(vandaag) }
                .minByOrNull { (_, datum) -> datum }
                ?: return@mapNotNull null
            val (deadline, datum) = volgende
            val dagen = ChronoUnit.DAYS.between(vandaag, datum)
            if (dagen > MIJLPALEN.first()) return@mapNotNull null
            val mijlpaal = MIJLPALEN.filter { dagen <= it }.minOrNull() ?: return@mapNotNull null
            val h = Herinnering(r, deadline, dagen, mijlpaal)
            if (h.sleutel in r.verstuurdeHerinneringen) null else h
        }

    fun boodschap(h: Herinnering): String {
        val wanneer = when (h.dagenTeGaan) {
            0L -> "vandaag"
            1L -> "morgen"
            in 2..13 -> "over ${h.dagenTeGaan} dagen"
            in 14..59 -> "over ${h.dagenTeGaan / 7} weken"
            else -> "over ongeveer ${h.dagenTeGaan / 30} maanden"
        }
        val project = h.regeling.projectTitel.ifBlank { "je project" }
        val oproep = when {
            h.dagenTeGaan > 60 -> "Tijd om het plan voor $project scherp te krijgen."
            h.dagenTeGaan > 30 -> "Werk aan de aanvraag voor $project: plan, begroting, team."
            h.dagenTeGaan > 7 -> "Finaliseer de aanvraag voor $project."
            else -> "Laatste loodjes: controleer en dien $project in."
        }
        return "Deadline ${h.deadline.datum} ($wanneer). $oproep"
    }
}
