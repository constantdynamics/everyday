# Changelog

## Na de code-review

Negen onafhankelijke reviewers hebben de app doorgelicht, elk op een eigen onderdeel. Wat
zij vonden en wat daaraan is gedaan:

**Je foto's en video's**

- Een foto of video wordt nu altijd eerst volledig weggeschreven onder een tijdelijke naam
  (`.bezig-…`). Pas daarna verdwijnt de oude en krijgt de nieuwe zijn naam. Valt de app of
  de telefoon halverwege uit, dan blijft de oude staan of staat de nieuwe er al, en maakt
  de app het bij de volgende start af. Eerder kon een onderbreking op precies het verkeerde
  moment een foto kosten of een halve foto achterlaten.
- Een lege of halve foto telt niet meer als de foto van die dag.
- Een `project.json` die niet te lezen is, wordt bewaard als `project-beschadigd.json` in
  plaats van overschreven. De beeldverhouding wordt dan afgeleid van de nieuwste foto die
  wel te lezen is.
- Elke projectmap krijgt een eigen `.nomedia`, zodat de foto's ook uit de galerij blijven
  als een projectmap los ergens anders terechtkomt.
- Een timelapse bevat niets meer over je telefoon: Android zet er zelf zijn versienummer in
  en bij sommige telefoons het merk en model. Dat wordt er na het renderen uitgehaald.

**Camera**

- Bleef de app een nacht open op het camerascherm, dan klopten de datum, de overlay en de
  melding "Vandaag al gelukt" de volgende dag niet. Dat is verholpen, en de flitser staat
  weer uit zodra je de app verlaat.
- Geef je de cameratoestemming via de instellingen van je telefoon, dan merkt de app dat
  meteen als je terugkomt.
- Heeft de telefoon te weinig geheugen voor een heel grote foto, dan wordt hij op halve
  grootte opgeslagen in plaats van dat de app stopt.

**Timelapse**

- Het scherm blijft aan tijdens het renderen, ook nadat je tussendoor een video hebt bekeken.
- Geen crash meer bij het kiezen van een periode nadat er foto's zijn verwijderd.
- Wat een afgebroken render in het werkgeheugen van de app achterliet, wordt bij de
  volgende start opgeruimd.

**Installeren en bijwerken**

- Bijwerken gaat voortaan door de oude map eerst te verwijderen (HANDLEIDING.md, deel B).
  Een nieuwe versie over de oude heen uitpakken liet oude bestanden staan, waardoor het
  bouwen mislukte. Het installatiebestand herkent die situatie en zegt wat je moet doen.
- Is je ondertekensleutel kwijt, dan kan het installatiebestand de oude app zelf van je
  telefoon halen. De handleiding waarschuwt voor het vinkje "app-gegevens behouden".
- Het installatiebestand vindt de Android SDK ook als die niet op de standaardplek staat.

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
- De app heet Everyday, en zo heten ook de map met je foto's en (met `-sleutel` erachter) de map
  met de ondertekensleutel.
- Een eigen vormgeving met gebundelde lettertypen.
- Geen deelknop, geen importeren, geen bewerken, geen muziek.

**Opgeleverd, in de volgorde van de opdracht**

1. *Projectskelet.* Alleen de `CAMERA`-permissie, back-ups volledig uitgesloten, een
   privacycontrole bij elke build, en een vaste ondertekensleutel die bij de eerste build
   buiten de repository wordt aangemaakt.
2. *Opslagmap en projecten.* Map kiezen via het Storage Access Framework, beperkt tot de
   opslag van de telefoon zelf. De app gebruikt altijd een eigen map `Everyday`.
3. *Camera en opslaan.* CameraX met één gedeelde ViewPort voor preview en foto. De foto
   wordt bijgesneden, rechtop gezet, nooit gespiegeld en zonder metadata opgeslagen als
   `JJJJ-MM-DD.jpg` (JPEG-kwaliteit 95).
4. *Overlay.* De meest recente eerdere foto over het live beeld, gespiegeld bij de
   voorcamera, met een schuifregelaar waarvan de stand per project wordt onthouden.
5. *Galerij.* Raster per maand, foto bekijken met vegen en zoomen, verwijderen en vervangen
   na bevestiging.
6. *Timelapse.* H.264 in MP4 met `MediaCodec` en `MediaMuxer`, 1 tot 30 foto's per
   seconde, optionele periode en datum in beeld, afspelen in de app.
7. *Vormgeving.* Het ontwerp "Nachtfilm": donker inktblauw, warm wit en amber, met koraalrood
   alleen voor de sluiter en de ene actie die telt. Titels in Unbounded, tekst in Outfit,
   dunne lijnen in plaats van randen en schaduwen, en een bijpassend icoon. De eigenaar koos
   dit uit vijf varianten; het eerste, retro ontwerp is ermee vervangen.

**Keuzes die afwijken van het voorstel in de opdracht**

- Geen Media3 Transformer, WorkManager of DataStore: die brengen netwerkpermissies of
  libraries van derden mee. Zie de tabel "Keuzes" in de README.
- De vormgeving volgt niet de retrostijl uit de opdracht, maar het ontwerp dat de eigenaar
  daarna in Claude Design heeft gekozen. Dat heeft volgens de opdracht voorrang.
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
