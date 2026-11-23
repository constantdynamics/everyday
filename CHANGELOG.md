# Changelog

## Everyday 1.0 – opnieuw opgebouwd

De app is vanaf de basis opnieuw gemaakt volgens een nieuwe opdracht. De eerdere versie in
deze repository ("everyday": series, een Room-database, foto's zichtbaar in de galerij,
importeren, bewerken en muziek) is daarmee vervangen. Die code staat nog in de
geschiedenis van de repository.

**Wat er anders is dan in de eerdere versie**

- De fotomap is de enige bron van waarheid. Er is geen database meer; `project.json` in
  elke projectmap bewaart de instellingen.
- Foto's staan in een map die je zelf kiest, met `.nomedia`, en dus niet meer in de galerij.
- Eén foto per dag per project, met een vraag vóór het vervangen.
- Een project heeft een vaste beeldverhouding en een vaste camera-kant.
- Werkt vanaf Android 8 (minSdk 26) in plaats van Android 14.
- Een eigen app-id (`nl.constantdynamics.dagelijks`), zodat de app nooit botst met een
  eerder geïnstalleerde versie.
- De app heet Everyday. De map met je foto's heet Dagelijks, zoals in de opdracht.
- Retro vormgeving met gebundelde fonts.
- Geen deelknop, geen importeren, geen bewerken, geen muziek.

**Opgeleverd, in de volgorde van de opdracht**

1. *Projectskelet.* Alleen de `CAMERA`-permissie, back-ups volledig uitgesloten, een
   privacycontrole bij elke build, en een vaste ondertekensleutel die bij de eerste build
   buiten de repository wordt aangemaakt.
2. *Opslagmap en projecten.* Map kiezen via het Storage Access Framework, beperkt tot de
   opslag van de telefoon zelf. De app gebruikt altijd een eigen map `Dagelijks`.
3. *Camera en opslaan.* CameraX met één gedeelde ViewPort voor preview en foto. De foto
   wordt bijgesneden, rechtop gezet, nooit gespiegeld en zonder metadata opgeslagen als
   `JJJJ-MM-DD.jpg` (JPEG-kwaliteit 95).
4. *Overlay.* De meest recente eerdere foto over het live beeld, gespiegeld bij de
   voorcamera, met een schuifregelaar waarvan de stand per project wordt onthouden.
5. *Galerij.* Raster per maand, foto bekijken met vegen en zoomen, verwijderen en vervangen
   na bevestiging.
6. *Timelapse.* H.264 in MP4 met `MediaCodec` en `MediaMuxer`, 1 tot 30 foto's per
   seconde, optionele periode en datum in beeld, afspelen in de app.
7. *Vormgeving.* Mint, koraal, mosterd, marineblauw en crème; titels met extrusie en
   contour, lint-labels, korreltextuur, een rustig camerascherm en een retro icoon.

**Keuzes die afwijken van het voorstel in de opdracht**

- Geen Media3 Transformer, WorkManager of DataStore: die brengen netwerkpermissies of
  libraries van derden mee. Zie de tabel "Keuzes" in de README.
- De schuifregelaar heet in de app "zichtbaarheid van de vorige foto": 0% is weg, 100% is
  volledig dekkend. In `project.json` heet de waarde `overlayZichtbaarheid`.
- Een 16:9-project fotografeer je met de telefoon op zijn kant.
- Bij het opnieuw maken van de foto van vandaag blijft de overlay de foto van gisteren,
  niet de foto die je aan het vervangen bent.
- De voorcamera heeft meestal geen flitser; daar laat de flitserknop het scherm even wit
  oplichten.
- Extra's die klein zijn en bij het doel passen: ingedrukt houden verbergt de overlay,
  de datum kan in de timelapse worden gezet, en een project kan worden hernoemd of
  verwijderd.
