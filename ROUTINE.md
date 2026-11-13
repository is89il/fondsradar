# Instructies voor de Fondsradar-routine

Deze instructies volgt de Claude-routine die elke twee weken draait. Pas dit bestand aan om de zoektocht bij te sturen, bijvoorbeeld door bronnen toe te voegen.

## Doel

Houd `data/regelingen.json` actueel met alle financieringsregelingen waarmee geld kan worden aangevraagd voor **korte films, speelfilms, telefilms/drama, documentaires en animatie**. Daar horen ook ontwikkelings-, scenario- en talentregelingen bij, voor makers en producenten in Nederland.

## Werkwijze

1. Lees het huidige `data/regelingen.json`. Elke regeling die er al in staat, neem je opnieuw op met **exact dezelfde `naam` en `fonds`**. De app herkent regelingen aan die twee velden; je interesse en notities in de app hangen daaraan.
2. Werk de brongroepen hieronder één voor één af. Gebruik zoeken op internet en open de **officiële pagina** van het fonds of de omroep om deadlines te controleren. Ga niet alleen af op zoekresultaten of nieuwsberichten.
3. **Verzin nooit een datum.** Neem alleen deadlines op die je op een bron hebt gezien. Is een ronde aangekondigd zonder datum, laat `deadlines` dan leeg en zet `status` op `"verwacht"`.
4. Neem deadlines op van vandaag tot en met 18 maanden vooruit. Een regeling zonder vaste deadline krijgt `"doorlopend": true`.
5. Is een bestaande regeling officieel beëindigd, zet `status` dan op `"gesloten"`. Verwijder hem niet.
6. Is een site niet bereikbaar, zet dat dan in `opmerkingen` en laat de bestaande gegevens van die regelingen staan.
7. Zet `bijgewerkt` op de datum en tijd van nu (ISO, bijv. `2026-11-15T04:30`).
8. Draai `python3 scripts/valideer.py data/regelingen.json` en herstel eventuele fouten.
9. Commit alleen `data/regelingen.json` op `main` met de melding `Zoektocht <datum>: <aantal> regelingen, <x> nieuw, <y> nieuwe deadlines` en push.
10. Sluit af met een korte samenvatting: wat er nieuw is, welke deadlines zijn bijgekomen en welke bronnen niet bereikbaar waren.

## Brongroepen

1. Nederlands Filmfonds (filmfonds.nl): alle regelingen, zoals ontwikkeling, productie, kort, documentaire, animatie, talent, de Netherlands Film Production Incentive en Telefilm
2. Telefilm en andere drama-/fictieregelingen van de publieke omroep (NPO, CoBO, Filmfonds)
3. NPO Fonds (npofonds.nl)
4. CoBO Fonds (cobofonds.nl)
5. Omroepen met open calls of pitchrondes voor korte film, documentaire en drama: NTR, VPRO, BNNVARA, KRO-NCRV, AVROTROS, EO, HUMAN, MAX, Omroep Zwart, PowNed en regionale omroepen
6. Talentprogramma's en kortefilmregelingen: Kort!, De Oversteek, One Night Stand, 2Doc, Teledoc en vergelijkbare
7. Regionale filmfondsen: Amsterdams Fonds voor de Kunst, Film Fund Rotterdam, Mediafonds Utrecht, Limburg Film Fund, Gelders Filmfonds, Brabantse en Noordelijke fondsen, Frysk Filmfûns, Haags Fonds
8. Private en cultuurfondsen die film of documentaire steunen: Stimuleringsfonds Creatieve Industrie, Prins Bernhard Cultuurfonds, VSBfonds, Fentener van Vlissingen Fonds en dergelijke
9. Internationaal, toegankelijk voor Nederlandse makers: IDFA Bertha Fund, Hubert Bals Fund (IFFR), Creative Europe MEDIA, Eurimages, Nederlands-Vlaamse coproductie (VAF)
10. Breed zoeken: nieuwe of minder bekende regelingen, open calls, pitchwedstrijden en beurzen die nergens anders onder vallen

## Formaat van `data/regelingen.json`

```json
{
  "bijgewerkt": "2026-11-15T04:30",
  "opmerkingen": ["optioneel: bijv. 'cobofonds.nl niet bereikbaar'"],
  "regelingen": [
    {
      "naam": "officiële naam van de regeling",
      "fonds": "officiële naam van het fonds of de omroep",
      "categorieen": ["korte film", "speelfilm", "telefilm", "documentaire", "animatie", "ontwikkeling", "talent", "overig"],
      "omschrijving": "1-3 zinnen",
      "voorwaarden": ["wie mag aanvragen", "vereiste producent/omroep", "eigen bijdrage", "verplichte stukken"],
      "bedrag": "bijv. 'max. € 50.000' of null",
      "deadlines": [{"datum": "JJJJ-MM-DD", "omschrijving": "bijv. 'indiening ronde 1'"}],
      "doorlopend": false,
      "url": "https://… officiële pagina",
      "status": "open | verwacht | gesloten | doorlopend"
    }
  ]
}
```
