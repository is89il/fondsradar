package nl.fondsradar.core

import kotlinx.serialization.Serializable

/** Soort project waarvoor een regeling geld geeft. */
enum class Categorie(val label: String) {
    KORTE_FILM("Korte film"),
    SPEELFILM("Speelfilm"),
    TELEFILM("Telefilm / drama"),
    DOCUMENTAIRE("Documentaire"),
    ANIMATIE("Animatie"),
    ONTWIKKELING("Ontwikkeling"),
    TALENT("Talent / makers"),
    OVERIG("Overig");

    companion object {
        fun parse(s: String): Categorie {
            val n = s.trim().lowercase()
            return when {
                "kort" in n || "short" in n -> KORTE_FILM
                "tele" in n || "drama" in n || "serie" in n || "omroep" in n -> TELEFILM
                "docu" in n -> DOCUMENTAIRE
                "anima" in n -> ANIMATIE
                "ontwikkel" in n || "develop" in n || "scenario" in n -> ONTWIKKELING
                "talent" in n || "maker" in n || "debuut" in n -> TALENT
                "speel" in n || "feature" in n || "lange" in n || "bioscoop" in n -> SPEELFILM
                else -> runCatching { valueOf(s.trim().uppercase()) }.getOrDefault(OVERIG)
            }
        }
    }
}

@Serializable
data class Deadline(
    /** ISO-datum, yyyy-MM-dd. */
    val datum: String,
    val omschrijving: String = "",
)

@Serializable
data class Regeling(
    val id: String,
    val naam: String,
    val fonds: String,
    val categorieen: List<Categorie> = emptyList(),
    val omschrijving: String = "",
    val voorwaarden: List<String> = emptyList(),
    val bedrag: String? = null,
    val deadlines: List<Deadline> = emptyList(),
    /** Doorlopend indienen mogelijk (geen vaste deadline). */
    val doorlopend: Boolean = false,
    val url: String = "",
    val status: String = "",
    /** ISO-datum waarop de regeling voor het eerst werd gevonden. */
    val eersteKeerGezien: String = "",
    val laatstGezien: String = "",

    // ---- Velden van de gebruiker; worden nooit door een scan overschreven ----
    val interesse: Boolean = false,
    val projectTitel: String = "",
    val notitie: String = "",
    /** Herinneringen die al verstuurd zijn, als "yyyy-MM-dd@dagenVooraf". */
    val verstuurdeHerinneringen: Set<String> = emptySet(),
    val gearchiveerd: Boolean = false,
)

/** Zoals Claude een regeling teruggeeft. */
@Serializable
data class GevondenRegeling(
    val naam: String,
    val fonds: String,
    val categorieen: List<String> = emptyList(),
    val omschrijving: String = "",
    val voorwaarden: List<String> = emptyList(),
    val bedrag: String? = null,
    val deadlines: List<Deadline> = emptyList(),
    val doorlopend: Boolean = false,
    val url: String = "",
    val status: String = "",
)

@Serializable
data class ScanPayload(val regelingen: List<GevondenRegeling> = emptyList())
