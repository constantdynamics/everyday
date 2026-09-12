# everyday

Een privé dagelijkse-fotoapp voor één toestel. Je maakt elke dag een foto van een
vast onderwerp, met een halftransparante versie van de vorige foto over het
camerabeeld zodat de compositie gelijk blijft. Later maak je er een timelapse van.

De app praat met niets en niemand: geen netwerk, geen account, geen cloud, geen
meldingen. Alles staat lokaal op je toestel.

## Stand van zaken

| Mijlpaal | Inhoud | Status |
|---|---|---|
| M1 | Projectskelet, series, opnamescherm, opslaan via MediaStore, galerij | ✅ |
| M2 | Ghost overlay, lens per serie, dagdetail, foto-van-de-dag, verwijderen, vervangen | volgt |
| M3 | Backupmap via SAF, automatische kopie, wachtrij, `everyday-metadata.json`, herstellen | volgt |
| M4 | Bewerken: roteren, rechtzetten en bijsnijden met behoud van het origineel | volgt |
| M5 | Importeren uit de galerij | volgt |
| M6 | Timelapse zonder muziek | volgt |
| M7 | Muziek, resterende instellingen, opslaggebruik, polish | volgt |

Zie `CHANGELOG.md` voor wat er per mijlpaal is opgeleverd.

## Bouwen en installeren

### Vereisten

- Android Studio (huidige stabiele versie) of alleen een JDK 17
- Een toestel met Android 14 of nieuwer (`minSdk 34`)

### Debug-build op je eigen machine

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

In Android Studio: map openen, toestel koppelen, op ▶ drukken.

### APK uit GitHub Actions

Bij elke push bouwt de workflow **Bouw APK** een debug-APK. Open de run op GitHub
en download het artefact `everyday-debug-apk`. Die APK is ondertekend met een
wegwerp-debugsleutel van de runner — prima om te kijken of iets werkt, maar niet
om dagelijks mee te werken. Lees hieronder waarom.

### Ondertekende release-APK (dit is de versie die je installeert)

**Belangrijk.** Android koppelt de foto's in `Pictures/Everyday/` en de toestemming
op je backupmap aan de app-installatie. Zolang je een nieuwe versie er *overheen*
installeert blijft alles staan. Maar zodra je de app moet de-installeren — en dat
moet zodra de handtekening verandert — verliest de app het eigendom van zijn eigen
bestanden en vervalt de toestemming op de backupmap. Je foto's zelf blijven staan;
de administratie niet.

Gebruik daarom vanaf het begin **één keystore** en installeer altijd de release-APK.

Keystore aanmaken (eenmalig, bewaar hem buiten deze repo en maak er een reservekopie van):

```bash
keytool -genkeypair -v \
  -keystore ~/everyday.jks \
  -alias everyday \
  -keyalg RSA -keysize 4096 -validity 10000
```

Lokaal bouwen met die keystore:

```bash
EVERYDAY_KEYSTORE_FILE=$HOME/everyday.jks \
EVERYDAY_KEYSTORE_PASSWORD='...' \
EVERYDAY_KEY_ALIAS=everyday \
EVERYDAY_KEY_PASSWORD='...' \
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Of laten bouwen door GitHub Actions. Zet dan vier repository-secrets:

| Secret | Waarde |
|---|---|
| `EVERYDAY_KEYSTORE_BASE64` | `base64 -w0 ~/everyday.jks` |
| `EVERYDAY_KEYSTORE_PASSWORD` | wachtwoord van de keystore |
| `EVERYDAY_KEY_ALIAS` | `everyday` |
| `EVERYDAY_KEY_PASSWORD` | wachtwoord van de sleutel |

De job `release` levert dan het artefact `everyday-release-apk`. Zonder die
secrets slaat die job zichzelf netjes over.

De keystore en de wachtwoorden staan nergens in deze repository, en horen daar ook
niet. Raak je de keystore kwijt, dan kun je geen update meer over de bestaande
installatie heen zetten.

## Waar je bestanden terechtkomen

```
Pictures/Everyday/<serie>/
    <serie>_2026-09-11_074512.jpg          originelen, volledige cameraresolutie
    bewerkt/
        <serie>_2026-09-11_074512.jpg      afgeleide na bijsnijden of roteren (M4)

Movies/Everyday/
    <serie>_timelapse_2026-09-11.mp4       (M6)
```

Bestandsnamen zijn zelfbeschrijvend, sorteren chronologisch en veranderen na
aanmaak nooit meer. De mapnaam wordt afgeleid van de serienaam op het moment dat
je de serie aanmaakt; hernoem je de serie later, dan verandert alleen de naam die
je in de app ziet en blijft de map staan.

De administratie (series, foto's, dagkeuzes) staat in een Room-database in de
privéopslag van de app.

## Backupmap instellen

Komt in M3. Je kiest dan één map via de systeemkiezer — interne opslag of
SD-kaart — en de app kopieert daar elke nieuwe of gewijzigde foto naartoe, plus
een `everyday-metadata.json` waarmee de hele administratie uit alleen die map
terug te halen is.

## Back-up van Google Foto's uitzetten voor deze map

De foto's staan bewust in een gewone, zichtbare map, zodat je galerij ze ziet.
Dat betekent ook dat een cloud-backup-app ze kan uploaden. Zo zet je dat uit:

1. Open **Google Foto's**.
2. Tik op je profielfoto rechtsboven → **Instellingen voor Foto's**.
3. Ga naar **Back-up** → **Back-up maken van apparaatmappen**.
4. Zet de schakelaar voor **Everyday** uit.

Staat de map er nog niet bij, dan verschijnt hij zodra je de eerste foto hebt
gemaakt.

## Keuzes die in de code vastliggen

| Keuze | Waarom |
|---|---|
| Native Kotlin, Jetpack Compose, één module, MVVM met repositories | Alles draait lokaal en leunt op camera, MediaStore en video-encoding: alle drie het prettigst native. |
| Geen DI-framework, maar een handgeschreven `AppContainer` | Eén module en een handvol objecten. Scheelt een annotatieverwerker en dus buildtijd. |
| Geen Coil of Glide, maar een eigen `FotoLader` | Beide brengen de INTERNET-permissie mee in hun manifest. We laden uitsluitend lokale bestanden, en de lader is toch nodig voor de ghost-cache en het geheugengedrag bij lange series. |
| `tools:node="remove"` op INTERNET, meldingen en locatie | Zo kan geen enkele bibliotheek die permissies alsnog binnensmokkelen. De workflow controleert de samengevoegde manifest bij elke build. |
| `allowBackup="false"` plus uitsluitende `data_regels.xml` | Geen enkele appdata gaat mee in een cloudback-up of toestelovergang. |
| Nederlandse teksten rechtstreeks in de code, alleen `app_naam` als resource | De app is eentalig en wordt nooit vertaald; losse resourceverwijzingen maken de schermen alleen maar minder leesbaar. |
| Debug en release delen dezelfde `applicationId` | Een afwijkende applicationId is voor Android een andere app, waardoor het eigendom van de foto's en de toestemming op de backupmap niet meeverhuizen. |
| Dagsleutel wordt eenmalig bij opslaan berekend | Foto's verschuiven dan niet alsnog van dag als je naar een andere tijdzone reist. |
| Foto's worden niet gespiegeld opgeslagen bij de voorcamera | De opname blijft daarmee gelijk aan wat de camera ziet, zoals elke andere app doet. De ghost overlay wordt straks gespiegeld getoond zodra de voorcamera actief is, zodat overlay en live beeld wel op elkaar liggen. |
| GPS wordt na elke opname actief uit de EXIF gehaald | CameraX schrijft geen locatie weg zolang we die niet meegeven; de controle achteraf is het vangnet. |
