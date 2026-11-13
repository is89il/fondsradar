# Fondsradar

Android-app die financieringsregelingen voor film bijhoudt: Filmfonds, omroepregelingen (zoals Telefilm), NPO Fonds, CoBO, regionale en private fondsen, ontwikkelings- en talentregelingen. Het gaat om regelingen voor korte films, speelfilms, telefilms, documentaires en animatie.

## Wat de app doet

- **Elke 14 dagen zoeken.** De app laat Claude (met web search en web fetch) het internet afspeuren, in 10 rondes per brongroep (zie `ClaudeScanner.STARTBRONNEN`). Per regeling worden naam, fonds, deadlines, belangrijkste voorwaarden, bedrag en de officiële link opgeslagen.
- **Meldingen bij nieuws.** Na elke zoektocht krijg je één melding met de nieuwe regelingen en de nieuw bekendgemaakte indieningsdata.
- **Interesse en notities.** Met de ster of de schakelaar markeer je regelingen waar je interesse in hebt. Per regeling kun je een werktitel en vrije notities voor je eigen project bijhouden.
- **Herinneringen vanaf 3 maanden.** Voor regelingen met interesse krijg je 90, 75, 60, 45, 30, 21, 14, 7, 3 en 1 dag(en) voor de deadline een melding om je plan af te ronden.
- Je kunt zelf deadlines toevoegen en regelingen verbergen.

## Installeren

1. Zet `Fondsradar.apk` op je telefoon en open het bestand. Sta "installeren uit onbekende bronnen" toe voor je bestandsbeheerder of browser.
2. Open de app en sta meldingen toe.
3. Ga naar Instellingen, vul een Claude API-sleutel in (aanmaken op console.anthropic.com) en druk op **Opslaan**. De eerste zoektocht start dan vanzelf.

## Kosten

Elke zoektocht doet per ronde tot 15 webzoekopdrachten en 20 paginabezoeken. Bij Claude Opus 5.5 komt dat neer op ruwweg een paar euro per zoektocht, dus een paar euro per maand. Claude Sonnet 5.5 (kies je in Instellingen) is goedkoper. Houd je verbruik bij in de Anthropic Console.

## Bouwen

```bash
./gradlew :app:assembleRelease      # APK in app/build/outputs/apk/release/
./gradlew :core:test                # tests van samenvoegen, herinneringen en parsen
```

Je hebt de Android SDK nodig (platform 36), via Android Studio of `local.properties` met `sdk.dir=…`.

## Opbouw

- `core/`: Kotlin/JVM, zonder Android-afhankelijkheden
  - `ClaudeScanner.kt`: prompt, web search/fetch via de Anthropic Java SDK, verwerking van het JSON-antwoord
  - `Merge.kt`: voegt scanresultaten samen, laat je interesse en notities ongemoeid en herkent nieuwe regelingen en deadlines
  - `Herinneringen.kt`: mijlpalen vanaf 90 dagen voor de deadline
- `app/`: Jetpack Compose-UI, WorkManager-taken (`work/`) en opslag als JSON-bestand (`data/`)

Data staan alleen op de telefoon. Controleer deadlines altijd op de officiële pagina, want de zoektocht kan zich vergissen.
