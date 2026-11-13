package nl.fondsradar.core

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.WebFetchTool20260209
import com.anthropic.models.messages.WebSearchTool20260209
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.LocalDate

class ScanFout(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Laat Claude met web search / web fetch het internet afspeuren naar
 * financieringsregelingen voor film en documentaire, en geeft een
 * gestructureerde lijst terug.
 */
class ClaudeScanner(
    apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .timeout(Duration.ofMinutes(15))
        .maxRetries(3)
        .build(),
) {
    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
        val MODELLEN = listOf(
            "claude-opus-5-5" to "Claude Opus 5.5 (grondigst)",
            "claude-sonnet-5-5" to "Claude Sonnet 5.5 (goedkoper)",
        )
        private const val MAX_RONDES = 10

        private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

        val STARTBRONNEN = listOf(
            "Nederlands Filmfonds – filmfonds.nl (alle regelingen: ontwikkeling, productie, kort, documentaire, animatie, talent, Netherlands Film Production Incentive)",
            "Telefilm-regeling (NPO, Nederlands Filmfonds en CoBO) en andere drama-/fictie-regelingen van de publieke omroep",
            "NPO Fonds (voorheen Mediafonds) – npofonds.nl",
            "CoBO Fonds – cobofonds.nl",
            "Omroepen met open calls of pitchrondes voor korte films, documentaires en drama (NTR, VPRO, BNNVARA, KRO-NCRV, AVROTROS, EO, HUMAN, MAX, Omroep Zwart, PowNed, regionale omroepen)",
            "Talentprogramma's en kortefilmregelingen zoals Kort!, De Oversteek, One Night Stand, Telefilm, Teledoc, 2Doc en vergelijkbare",
            "Regionale filmfondsen: Amsterdams Fonds voor de Kunst, Film Fund Rotterdam / Rotterdam Film Fund, Mediafonds Utrecht, Limburg Film Fund, Gelders Filmfonds, Brabantse en Noordelijke fondsen, Frysk Filmfûns, Haags Fonds",
            "Stimuleringsfonds Creatieve Industrie, Fonds Podiumkunsten/Mondriaan Fonds (indien relevant voor film), Prins Bernhard Cultuurfonds, VSBfonds, Fentener van Vlissingen Fonds en andere private fondsen die film of documentaire steunen",
            "Festivalfondsen en internationale regelingen toegankelijk voor Nederlandse makers: IDFA Bertha Fund, Hubert Bals Fund (IFFR), Creative Europe MEDIA, Eurimages, Nederlands-Vlaamse coproductieregelingen (VAF)",
            "Breed zoeken: nieuwe of minder bekende regelingen, open calls, pitchwedstrijden en ontwikkelingsbeurzen voor film en documentaire in Nederland die niet onder de andere bronnen vallen",
        )
    }

    /**
     * Doorzoekt één groep bronnen ([focus], bijvoorbeeld een item uit [STARTBRONNEN]).
     * Door per groep te zoeken blijft elke aanroep binnen de tijdslimiet van
     * Android-achtergrondtaken.
     */
    fun scan(bekend: List<Regeling>, focus: String, vandaag: LocalDate = LocalDate.now()): List<GevondenRegeling> {
        var params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(32000L)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.HIGH).build())
            .system(systeemPrompt())
            .addTool(WebSearchTool20260209.builder().maxUses(15L).build())
            .addTool(WebFetchTool20260209.builder().maxUses(20L).build())
            .addUserMessage(gebruikersPrompt(bekend, focus, vandaag))
            // Server-side fallback bij een (onterechte) weigering door veiligheidsfilters.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()

        repeat(MAX_RONDES) {
            val antwoord: Message = try {
                client.messages().create(params)
            } catch (e: Exception) {
                throw ScanFout("Claude API-aanroep mislukt: ${e.message}", e)
            }
            when (antwoord.stopReason().orElse(null)) {
                StopReason.PAUSE_TURN -> {
                    // Lange zoektocht: laat Claude verder gaan waar het was.
                    params = params.toBuilder().addMessage(antwoord).build()
                }
                StopReason.REFUSAL -> throw ScanFout("Claude weigerde het verzoek.")
                StopReason.MAX_TOKENS -> throw ScanFout("Antwoord te lang (max_tokens bereikt).")
                else -> return parse(tekstVan(antwoord))
            }
        }
        throw ScanFout("Zoektocht niet afgerond na $MAX_RONDES rondes.")
    }

    private fun tekstVan(m: Message): String =
        m.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }.joinToString("\n")

    internal fun parse(tekst: String): List<GevondenRegeling> = parseAntwoord(tekst)

    private fun systeemPrompt(): String = """
        Je bent een onderzoeksassistent voor een Nederlandse filmmaker. Je zoekt actuele
        financieringsregelingen waarmee geld kan worden aangevraagd voor korte films,
        speelfilms, telefilms/drama, documentaires en animatie, inclusief ontwikkelings-,
        scenario- en talentregelingen. Focus op regelingen toegankelijk voor makers en
        producenten in Nederland.

        Werkwijze:
        - Gebruik web search en web fetch. Controleer deadlines altijd op de officiële
          pagina van het fonds of de omroep zelf, niet alleen in zoekresultaten.
        - Neem alleen deadlines op die je daadwerkelijk op een bron hebt gezien. Verzin
          nooit een datum. Is de exacte datum nog niet bekend, laat hem weg en vermeld
          in "status" dat de ronde verwacht wordt.
        - Neem regelingen met doorlopende indiening op met "doorlopend": true.
        - Gebruik precies de officiële naam van de regeling en van het fonds, zodat
          dezelfde regeling bij een volgende zoektocht herkend wordt.
        - Vat de belangrijkste voorwaarden kort samen (wie mag aanvragen, vereiste
          producent/omroep, maximaal bedrag, eigen bijdrage, verplichte stukken).

        Antwoord als laatste met uitsluitend één JSON-codeblok (```json ... ```) met dit formaat:
        {"regelingen": [{
          "naam": "string",
          "fonds": "string",
          "categorieen": ["korte film" | "speelfilm" | "telefilm" | "documentaire" | "animatie" | "ontwikkeling" | "talent" | "overig"],
          "omschrijving": "1-3 zinnen",
          "voorwaarden": ["korte bullet", "..."],
          "bedrag": "bijv. 'max. € 50.000' of null",
          "deadlines": [{"datum": "YYYY-MM-DD", "omschrijving": "bijv. 'indiening ronde 1'"}],
          "doorlopend": false,
          "url": "officiële pagina",
          "status": "open | verwacht | gesloten | doorlopend"
        }]}
    """.trimIndent()

    private fun gebruikersPrompt(bekend: List<Regeling>, focus: String, vandaag: LocalDate): String {
        val bekendeLijst = if (bekend.isEmpty()) "(nog geen)" else bekend
            .filter { !it.gearchiveerd }
            .joinToString("\n") { r ->
                val d = r.deadlines.joinToString(", ") { it.datum }.ifBlank { if (r.doorlopend) "doorlopend" else "geen" }
                "- ${r.fonds} — ${r.naam} (deadlines: $d)"
            }
        return """
            Vandaag is het $vandaag. Zoek alle actuele en aangekondigde regelingen met
            indieningsdeadlines vanaf vandaag tot en met ${vandaag.plusMonths(18)}, plus
            doorlopende regelingen.

            Deze zoekronde richt zich op: $focus
            Andere bronnen worden in aparte rondes doorzocht; blijf bij dit onderwerp.

            Deze regelingen ken ik al. Controleer de exemplaren die bij dit onderwerp horen op
            nieuwe of gewijzigde deadlines en neem ze opnieuw op in je antwoord (met precies
            dezelfde naam en hetzelfde fonds) als ze nog bestaan:
            $bekendeLijst
        """.trimIndent()
    }
}

/** Haalt het JSON-blok uit Claude's antwoord en zet het om. */
fun parseAntwoord(tekst: String): List<GevondenRegeling> {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    val blok = Regex("```json\\s*(.*?)```", RegexOption.DOT_MATCHES_ALL).findAll(tekst).lastOrNull()?.groupValues?.get(1)
        ?: tekst.substring(
            tekst.indexOf('{').takeIf { it >= 0 } ?: throw ScanFout("Geen JSON in het antwoord."),
            tekst.lastIndexOf('}') + 1,
        )
    return try {
        json.decodeFromString(ScanPayload.serializer(), blok.trim()).regelingen
    } catch (e: Exception) {
        throw ScanFout("Kon het antwoord niet lezen: ${e.message}", e)
    }
}
