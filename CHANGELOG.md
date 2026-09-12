# Changelog

Per mijlpaal: wat er is opgeleverd en welke keuzes er onderweg zijn gemaakt.

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

**Nog niet in M1**

Ghost overlay, dagdetail, foto-van-de-dag kiezen, verwijderen, vervangen,
backupmap, bewerken, importeren, timelapse en het instellingenscherm.
