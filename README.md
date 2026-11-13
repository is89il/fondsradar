# Fondsradar

Android-app die financieringsregelingen voor film bijhoudt: Filmfonds, omroepregelingen (zoals Telefilm), NPO Fonds, CoBO, regionale en private fondsen, ontwikkelings- en talentregelingen. Het gaat om regelingen voor korte films, speelfilms, telefilms, documentaires en animatie.

## Hoe het werkt

1. Elke twee weken draait een **Claude Code-routine** binnen je Claude-abonnement. Die speurt het internet af volgens [`ROUTINE.md`](ROUTINE.md) en publiceert het resultaat in [`data/regelingen.json`](data/regelingen.json).
2. De **app** haalt dat bestand elke dag op. Dat is gratis en gaat om een paar kB. Is het bijgewerkt, dan krijg je een melding met nieuwe regelingen en nieuw bekendgemaakte deadlines.
3. In de app markeer je regelingen met een **ster** en houd je per regeling een **werktitel en notities** voor je eigen project bij. Die staan alleen op je telefoon.
4. Voor regelingen met een ster krijg je **herinneringen vanaf 3 maanden voor de deadline**: 90, 75, 60, 45, 30, 21, 14, 7, 3 en 1 dag(en) vooraf.

Je kunt ook zelf deadlines toevoegen en regelingen verbergen. Controleer data altijd op de officiële pagina, want de zoektocht kan zich vergissen.

## Installeren

1. Download [`apk/Fondsradar.apk`](https://github.com/is89il/fondsradar/raw/main/apk/Fondsradar.apk) of bouw hem zelf (zie hieronder). Open het bestand op je telefoon en sta installeren uit onbekende bronnen toe.
2. Open de app en sta meldingen toe. De app haalt de regelingen meteen op.

De zoekopdracht bijsturen? Pas [`ROUTINE.md`](ROUTINE.md) aan. De volgende routine gebruikt de nieuwe versie.

## Bouwen

```bash
./gradlew :app:assembleRelease      # APK in app/build/outputs/apk/release/
./gradlew :core:test                # tests van samenvoegen, herinneringen en het bestandsformaat
```

Je hebt de Android SDK nodig (platform 36), via Android Studio of `local.properties` met `sdk.dir=…`.

## Opbouw

- `ROUTINE.md`: instructies voor de tweewekelijkse Claude-routine
- `data/regelingen.json`: de gepubliceerde regelingen, bijgehouden door de routine
- `scripts/valideer.py`: controle van het JSON-bestand vóór publicatie
- `core/`: Kotlin/JVM, zonder Android-afhankelijkheden
  - `Feed.kt` / `Model.kt`: het bestandsformaat
  - `Merge.kt`: voegt scanresultaten samen, laat je interesse en notities ongemoeid en herkent nieuwe regelingen en deadlines
  - `Herinneringen.kt`: mijlpalen vanaf 90 dagen voor de deadline
- `app/`: Jetpack Compose-UI, WorkManager-taken (`work/`) en opslag als JSON-bestand (`data/`)

