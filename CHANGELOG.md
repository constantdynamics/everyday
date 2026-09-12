# Changelog

Per mijlpaal: wat er is opgeleverd en welke keuzes er onderweg zijn gemaakt.

## M3 — backupmap, kopieerwachtrij, metadata en herstellen

**Opgeleverd**

- **Backupmap** kiezen via de systeemkiezer, met blijvende toestemming. De map mag
  op interne opslag of op een SD-kaart staan.
- Na elke opname wordt het bestand automatisch naar de backupmap gekopieerd, in
  dezelfde mapstructuur: `<backupmap>/<serie>/<bestandsnaam>`.
- **Kopieerwachtrij** in de database: een mislukte kopie kost nooit een foto en
  houdt de app nergens op. De wachtrij wordt afgewerkt bij het starten van de app,
  telkens als de app weer op de voorgrond komt, en zodra een losgekoppeld volume
  opnieuw wordt aangekoppeld.
- Lukt er in een hele ronde niets, dan stopt het inlopen en wordt het bij de
  volgende gelegenheid opnieuw geprobeerd — geen eindeloos doorpogen.
- **`everyday-metadata.json`** naast de foto's, met series, foto's en dagkeuzes.
  Alles verwijst naar mapnamen en bestandsnamen, nooit naar database-ids of uri's,
  zodat de backupmap in zijn eentje genoeg is.
- **Herstellen uit de backupmap**: leest de metadata terug, zet ontbrekende series
  en foto's weer in de app en kopieert de bestanden terug. Dit is het vangnet na
  een de-installatie of een toestelwissel.
- **Instellingenscherm** met de backupstatus (laatste geslaagde kopie, aantal in de
  wachtrij, oudste wachtende), knoppen "Alles opnieuw kopiëren" en "Herstellen uit
  backupmap", de standaarddekking van de ghost overlay, de dagstart, de themakeuze
  en de nuchtere regel over de galerij en Google Foto's.
- Op het startscherm verschijnt pas een melding als de achterstand ouder is dan
  drie dagen. Een SD-kaart die er even uit is, valt je dus niet lastig.
- Definitief opgeruimde foto's worden in de backupmap naar `_verwijderd/`
  verplaatst in plaats van gewist.
- Databaseversie 2 met migratie voor de kopieerwachtrij.

## M2 — ghost overlay, dagdetail, foto-van-de-dag, verwijderen

**Opgeleverd**

- **Ghost overlay**: de allerlaatst gemaakte foto van de serie ligt halftransparant
  over het live camerabeeld. Het camerabeeld krijgt precies de beeldverhouding van
  de sensor, zodat een foto met een afwijkende verhouding gecentreerd op hetzelfde
  rechthoekje wordt ingepast en nooit wordt uitgerekt.
- Schuifregelaar voor de dekking (standaard 40%, wordt onthouden), en het beeld
  ingedrukt houden haalt de overlay even weg.
- Bij de voorcamera wordt de overlay gespiegeld getoond, omdat het live beeld
  gespiegeld is en de opgeslagen foto niet. Zo liggen ze op elkaar.
- Ghost-cache op schijf: na elke opname wordt meteen een verkleinde versie
  weggeschreven, zodat het opnamescherm niet hoeft te wachten op het decoderen van
  een foto op volle resolutie.
- **Dagdetail**: alle foto's van één dag naast elkaar, met tijdstip en teller.
- **Foto-van-de-dag** kiezen met de ster; de automatisch gekozen foto draagt het
  label "standaard", een handmatige keuze "gekozen als foto van de dag", en de ster
  zet de keuze weer terug naar standaard.
- **Verwijderen** met een prullenbak van 30 dagen: de foto verdwijnt uit de app maar
  het bestand blijft staan, met "Ongedaan maken" in de melding. Bij het starten van
  de app wordt alles opgeruimd dat de termijn voorbij is; pas dan verdwijnt het
  bestand echt.
- **Vervangen** opent het opnamescherm, en **delen** gaat via de Android-sharesheet.
- Tikken op een dag in de galerij opent het dagdetail.

## M1 — projectskelet, series, opnemen, galerij

**Opgeleverd**

- Android-project met Gradle Kotlin DSL, version catalog, `minSdk 34`, `targetSdk 37`.
- Series aanmaken met automatisch afgeleide, unieke mapnaam.
- Startscherm met per serie een omslagfoto, het aantal foto's, de datum van de
  laatste foto en een directe cameraknop (foto maken in twee tikken).
- Opnamescherm met CameraX: live beeld, sluiterknop, wisselen tussen voor- en
  achtercamera, miniatuur van de laatste foto, en direct terug in opnamestand na
  elke opname.
- Foto's worden als JPG op volle resolutie weggeschreven naar
  `Pictures/Everyday/<serie>/` via MediaStore, dus zichtbaar voor de galerij.
- GPS wordt na elke opname uit de EXIF verwijderd.
- Galerij per serie: de foto-van-de-dag in een raster, nieuwste boven, gegroepeerd
  per maand met een blijvende maandkop. Dagen zonder foto worden overgeslagen.
- Room-database met het volledige datamodel (series, foto's, dagkeuzes) en
  `exportSchema = true`.
- GitHub Actions-workflow die de debug-APK bouwt, de unit tests draait en de
  samengevoegde manifest controleert op verboden permissies.

**Keuzes**

- **Video-encoder (sectie 10.1):** MediaCodec + MediaMuxer met frames die zelf op
  een Canvas worden samengesteld en via EGL naar de encoder-surface gaan. Media3
  Transformer kan wel foto's en overlays aan, maar een crossfade tussen
  opeenvolgende foto's is daar geen eersteklas functie; zodra je toch elk frame
  zelf bepaalt is een eigen encoderlus voorspelbaarder en scheelt het een forse
  set dependencies waarvan de manifest uitgekamd moet worden. Wordt gebouwd in M6.
- **Passing (10.2):** "passend" wordt letterbox met zwarte balken; "vullen" wordt
  center-crop met een instelbaar snijpunt (boven, midden, onder), omdat een
  portret bij exact midden door het hoofd wordt gesneden.
- **Dagdefinitie (10.3):** instelbare dagstart, standaard 00:00 dus de gewone
  kalenderdag. De dagsleutel wordt eenmalig bij opslaan vastgelegd.
- **Serie hernoemen (10.4):** alleen de weergavenaam verandert; mapnaam en
  bestandspaden blijven staan.
- **Verwijderen (10.5):** prullenbak van 30 dagen via `verwijderdOp`; het veld zit
  al in het datamodel. Bij definitief opruimen gaat de backupkopie naar
  `_verwijderd/` in plaats van weg.
- **Ghost overlay:** bron is de allerlaatst gemaakte foto van de serie. Foto's
  worden niet gespiegeld opgeslagen; de overlay wordt straks gespiegeld getoond
  zolang de voorcamera actief is, zodat overlay en live beeld op elkaar liggen.
- **Geen beeldbibliotheek:** een eigen `FotoLader` in plaats van Coil of Glide,
  omdat die de INTERNET-permissie meebrengen.
- **Geen DI-framework:** een handgeschreven `AppContainer`.
- **Maandkalenderweergave:** niet gebouwd. Een kalender toont per definitie de
  lege dagen, terwijl overgeslagen dagen juist onzichtbaar horen te blijven.

**Bouwketen**

Na de eerste groene build is de hele keten op de huidige stabiele versies gezet:
Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.20, KSP 2.3.12, `compileSdk` en `targetSdk`
37, Compose BOM 2026.09.00, Room 2.8.5, CameraX 1.6.2. AGP 9 brengt Kotlin zelf
mee, dus de losse `kotlin-android`-plugin is uit het app-buildbestand verdwenen.
Het geëxporteerde Room-schema (versie 1) staat in `app/schemas/` en wordt door de
build bijgewerkt zodra het verandert.

**Nog niet in M1**

Ghost overlay, dagdetail, foto-van-de-dag kiezen, verwijderen, vervangen,
backupmap, bewerken, importeren, timelapse en het instellingenscherm.
