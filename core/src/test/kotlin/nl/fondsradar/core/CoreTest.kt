package nl.fondsradar.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreTest {
    private val vandaag = LocalDate.of(2026, 11, 13)

    private fun gevonden(naam: String, vararg data: String) = GevondenRegeling(
        naam = naam, fonds = "Nederlands Filmfonds", categorieen = listOf("documentaire"),
        deadlines = data.map { Deadline(it, "ronde") },
    )

    @Test
    fun nieuweRegelingEnNieuweDeadlineWordenGemeld() {
        val eerste = Merger.merge(emptyList(), listOf(gevonden("Documentaire productie", "2027-02-01")), vandaag)
        assertEquals(1, eerste.wijzigingen.size)
        assertTrue(eerste.wijzigingen.single() is Wijziging.NieuweRegeling)

        val metInteresse = eerste.regelingen.map { it.copy(interesse = true, notitie = "Mijn docu") }
        val tweede = Merger.merge(metInteresse, listOf(gevonden("Documentaire productie", "2027-02-01", "2027-06-01")), vandaag)
        val w = tweede.wijzigingen.single() as Wijziging.NieuweDeadlines
        assertEquals(listOf("2027-06-01"), w.deadlines.map { it.datum })
        val r = tweede.regelingen.single()
        assertTrue(r.interesse)
        assertEquals("Mijn docu", r.notitie)
        assertEquals(Categorie.DOCUMENTAIRE, r.categorieen.single())
    }

    @Test
    fun geenWijzigingBijZelfdeData() {
        val eerste = Merger.merge(emptyList(), listOf(gevonden("Kort", "2027-01-10")), vandaag)
        val tweede = Merger.merge(eerste.regelingen, listOf(gevonden("Kort", "2027-01-10")), vandaag)
        assertTrue(tweede.wijzigingen.isEmpty())
    }

    @Test
    fun herinneringenVanafDrieMaanden() {
        val r = Regeling(
            id = "x", naam = "Telefilm", fonds = "NPO", interesse = true,
            deadlines = listOf(Deadline("2027-02-11")),
        )
        // 90 dagen ervoor
        assertEquals(90, HerinneringPlanner.teVersturen(listOf(r), LocalDate.of(2026, 11, 13)).single().mijlpaal)
        // 100 dagen ervoor: nog niets
        assertTrue(HerinneringPlanner.teVersturen(listOf(r), LocalDate.of(2026, 11, 3)).isEmpty())
        // 20 dagen ervoor: mijlpaal 21
        val h = HerinneringPlanner.teVersturen(listOf(r), LocalDate.of(2027, 1, 22)).single()
        assertEquals(21, h.mijlpaal)
        // al verstuurd -> niet opnieuw
        val verstuurd = r.copy(verstuurdeHerinneringen = setOf(h.sleutel))
        assertTrue(HerinneringPlanner.teVersturen(listOf(verstuurd), LocalDate.of(2027, 1, 22)).isEmpty())
        // zonder interesse geen herinnering
        assertTrue(HerinneringPlanner.teVersturen(listOf(r.copy(interesse = false)), LocalDate.of(2027, 1, 22)).isEmpty())
    }

    @Test
    fun parseJsonBlok() {
        val tekst = """
            Hier is het resultaat.
            ```json
            {"regelingen":[{"naam":"Kort!","fonds":"Nederlands Filmfonds","categorieen":["korte film"],
              "deadlines":[{"datum":"2027-03-01","omschrijving":"indiening"}],"doorlopend":false,"bedrag":null,"onbekendVeld":1}]}
            ```
        """.trimIndent()
        val r = parseAntwoord(tekst).single()
        assertEquals("Kort!", r.naam)
        assertEquals("2027-03-01", r.deadlines.single().datum)
    }
}
