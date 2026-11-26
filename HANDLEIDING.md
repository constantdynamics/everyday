# Handleiding Everyday

Zo zet je de app op je telefoon, werk je hem bij en maak je een reservekopie van je foto's.
Je hoeft niet te kunnen programmeren. Alles wat je moet doen staat hier stap voor stap.

De app heet **Everyday**, en zo heet ook de map waarin hij je foto's bewaart.

Er zijn twee manieren om de app op je telefoon te krijgen:

- **Gewoon downloaden op je telefoon** (aanbevolen). Je hebt geen computer nodig. Zie hieronder.
- **Zelf bouwen met je Windows-computer** en een usb-kabel. Dat kan nog steeds, maar is
  omslachtiger; zie deel A en B verderop.

---

## Installeren op je telefoon, zonder computer

Elke keer dat er een nieuwe, geteste versie is, maakt GitHub daar zelf een
installatiebestand van. Dat bestand staat op de pagina **Releases**.

1. Open op je telefoon de pagina
   <https://github.com/constantdynamics/everyday/releases/latest>.
2. Tik onder **Assets** op het bestand **Everyday-1.….apk** om het te downloaden.
3. Tik op het gedownloade bestand. Zegt Android dat installeren van deze bron niet is
   toegestaan? Tik op **Instellingen**, zet **Toestaan van deze bron** aan en ga terug.
4. Tik op **Installeren** (of **Updaten** als de app er al op staat).
5. Waarschuwt Google Play Protect dat de app onbekend is? Dat komt doordat hij niet uit de
   Play Store komt. Kies **Toch installeren**. Vraagt hij of de app gecontroleerd mag worden,
   kies dan **Niet verzenden**.

Een nieuwe versie installeer je op precies dezelfde manier. Hij komt over de oude heen; je
foto's, projecten en instellingen blijven staan. Dat lukt omdat elke versie met dezelfde
vaste sleutel is ondertekend (zie hieronder).

De pagina Releases is openbaar, net als de code van de app. Er staat niets persoonlijks in:
geen foto's, geen instellingen en geen sleutel.

De eerste keer dat je de app opent, kies je waar je foto's staan: zie stap 5 verderop.

### Je sleutel bij GitHub (eenmalig)

GitHub kan het installatiebestand alleen maken als het jouw vaste ondertekensleutel heeft.
Die bewaar je daar als **geheim**: een waarde die alleen de automatische bouw kan gebruiken
en die niemand kan inzien, jij ook niet meer.

Je hebt van Claude een tekstbestand **EVERYDAY_SLEUTEL.txt** gekregen. Dat ene bestand is
je sleutel.

1. Bewaar dat bestand eerst ergens veilig, bijvoorbeeld op een usb-stick of in een
   wachtwoordmanager. Het is ook je reservekopie. Deel het met niemand.
2. Open (liefst op een computer) <https://github.com/constantdynamics/everyday/settings/secrets/actions>
   en log zo nodig in.
3. Klik op **New repository secret**.
4. Vul bij **Name** precies in: `EVERYDAY_SLEUTEL`
5. Open EVERYDAY_SLEUTEL.txt, selecteer alle tekst (Ctrl+A), kopieer die (Ctrl+C) en plak
   hem bij **Secret** (Ctrl+V).
6. Klik op **Add secret**.

Vanaf dan verschijnt na elke nieuwe versie die alle tests doorstaat vanzelf een
installatiebestand onder Releases. Ontbreekt de sleutel of is hij verkeerd, dan maakt GitHub
niets: er verschijnt dan nooit een bestand dat niet over je app heen past.

---

## Liever zelf bouwen met je computer

**Wat je nodig hebt**

- een computer met Windows
- je Android-telefoon (Android 8 of nieuwer)
- een usb-kabel die ook gegevens doorgeeft (niet elke oplaadkabel kan dat)
- ongeveer een uur voor de eerste keer, waarvan het grootste deel wachten is
- je sleutel: pak EVERYDAY_SLEUTEL.txt uit naar de map `C:\Users\<jouw naam>\Everyday-sleutel`
  (Claude helpt je daarbij). Zonder die map maakt het bouwen een nieuwe sleutel, en dan moet
  de app die nu op je telefoon staat er eerst af.

## Deel A – Eenmalig klaarzetten

### Stap 1. Android Studio installeren

Android Studio is het gratis programma van Google waarmee Android-apps worden gebouwd.
Je hoeft er niets in te doen; het moet alleen op je computer staan, omdat het de
bouwstenen meebrengt.

1. Ga naar <https://developer.android.com/studio> en klik op **Download Android Studio**.
2. Ga akkoord met de voorwaarden en download het bestand.
3. Dubbelklik op het gedownloade bestand en klik steeds op **Next** en daarna op **Install**.
   Laat alle instellingen staan zoals ze zijn.
4. Start Android Studio. Bij de eerste start verschijnt een installatiewizard:
   - Kies **Do not import settings** als daarom wordt gevraagd.
   - Kies **Standard** en klik op **Next**.
   - Bij de licenties: klik links op elke licentie en kies telkens **Accept**. Klik dan op **Finish**.
5. Wacht tot alles is gedownload ("Downloading Components"). Dat kan een kwartier duren.
6. Sluit Android Studio daarna weer af.

### Stap 2. De app-code op je computer zetten

1. Open <https://github.com/constantdynamics/everyday> in je browser.
2. Klik op de groene knop **Code** en kies **Download ZIP**.
3. Open je map **Downloads**, klik met de rechtermuisknop op het zip-bestand en kies **Alles uitpakken…**.
4. Kies als doelmap `C:\Everyday` en klik op **Uitpakken**.
5. Open `C:\Everyday` en ga de map in die erin staat. Daar hoort onder meer het bestand
   **installeer-op-telefoon** te staan.

Gebruik een korte map zoals `C:\Everyday`. Zet hem niet in OneDrive of op je bureaublad:
daar gaat het bouwen vaak mis.

### Stap 3. Je telefoon voorbereiden

Je telefoon moet toestaan dat je computer er een app op zet. Dat heet *USB-foutopsporing*.

1. **Ontwikkelaarsopties aanzetten**
   - Open **Instellingen** en ga naar **Over de telefoon** (Samsung: **Info telefoon → Softwaregegevens**).
   - Tik zeven keer snel achter elkaar op **Buildnummer** (Xiaomi: op **OS-versie** of **MIUI-versie**).
   - Er verschijnt de melding dat je nu ontwikkelaar bent.
2. **USB-foutopsporing aanzetten**
   - Ga naar **Instellingen → Systeem → Ontwikkelaarsopties** (Samsung: **Instellingen → Ontwikkelaarsopties**, helemaal onderaan).
   - Zet **USB-foutopsporing** aan en bevestig met **OK**.
   - Heb je een Xiaomi, Redmi of Poco? Zet daar ook **Installeren via USB** aan.
3. **Aansluiten**
   - Ontgrendel je telefoon en sluit hem met de usb-kabel aan op je computer.
   - Vraagt je telefoon waarvoor je de usb-verbinding wilt gebruiken? Kies **Bestandsoverdracht**.
   - De vraag **USB-foutopsporing toestaan?** komt pas bij stap 4, zodra je computer de
     telefoon voor het eerst aanspreekt.

### Stap 4. De app bouwen en installeren

1. Zorg dat je telefoon is aangesloten en ontgrendeld.
2. Open de map `C:\Everyday` in de Verkenner en ga de map in die erin staat.
3. Dubbelklik op **installeer-op-telefoon** (het volledige bestand heet `installeer-op-telefoon.bat`).
4. Windows waarschuwt bij elk programmaatje dat van internet komt. Zegt Windows
   "Windows heeft uw pc beschermd"? Klik dan op **Meer informatie** en op **Toch uitvoeren**.
   Vraagt Windows of je het bestand wilt openen? Klik dan op **Uitvoeren**.
5. Er opent een zwart venster. De eerste keer verschijnt nu op je telefoon de vraag
   **USB-foutopsporing toestaan?** Vink **Altijd toestaan vanaf deze computer** aan en tik op
   **Toestaan**. Het venster wacht daarop en gaat daarna vanzelf verder.
6. Laat het venster open en wacht. De allereerste keer duurt dit tien minuten of langer,
   omdat er eenmalig veel wordt gedownload.
7. Vraagt je telefoon of de app naar Google mag worden gestuurd voor een veiligheidscontrole
   (Play Protect)? Kies **Niet verzenden**. Vraagt hij of je de app toch wilt installeren? Kies **Toch installeren**.
8. Staat er in het venster **Klaar! Everyday staat op je telefoon.**, dan is het gelukt.
   De app opent vanzelf op je telefoon.

> **Je ondertekensleutel.** Bij de eerste keer bouwen is er een map
> `C:\Users\<jouw naam>\Everyday-sleutel` aangemaakt met twee bestanden (in de Verkenner
> heet `Users` "Gebruikers"). Dat is de
> vaste sleutel waarmee jouw app wordt ondertekend. Dankzij die sleutel kan een nieuwe
> versie straks over de oude heen worden geïnstalleerd. **Maak een reservekopie van deze
> map**, bijvoorbeeld op een usb-stick. Deel hem met niemand.

### Stap 5. De eerste keer de app openen

1. De app vraagt waar je foto's mogen staan. Tik op **Kies een map**.
2. Er opent een scherm van Android zelf, in de map **Documenten**. Je hebt twee mogelijkheden:
   - **Aanbevolen:** tik rechtsboven op de drie puntjes of op het mapje met een plusje, kies
     **Nieuwe map**, noem hem `Everyday` en tik op **OK**. Je staat nu in die nieuwe map.
   - **Makkelijker:** doe niets en blijf in Documenten. De app maakt de map `Everyday` dan zelf aan.
3. Tik onderaan op **Deze map gebruiken** en daarna op **Toestaan**.
4. Je bent nu op het startscherm. Tik op **Nieuw project** om te beginnen.

De eerste keer dat je de camera opent, vraagt Android of Everyday de camera mag gebruiken.
Kies **Tijdens gebruik van de app**. Dat is de enige toestemming die de app vraagt.

---

## Deel B – De app bijwerken

Als er een nieuwe versie van de app is:

1. Open `C:\Everyday` in de Verkenner en **verwijder de map die erin staat**. Dat is de oude
   versie van de app-code. Je raakt er niets mee kwijt: je ondertekensleutel staat ergens
   anders (in `C:\Users\<jouw naam>\Everyday-sleutel`) en je foto's staan op je telefoon.
2. Download de zip opnieuw en pak hem uit in `C:\Everyday`, precies zoals bij stap 2.
3. Sluit je telefoon aan, ga de nieuwe map in en dubbelklik weer op **installeer-op-telefoon**.

Pak een nieuwe versie niet over de oude heen uit. Dan blijven er oude bestanden tussen de
nieuwe staan en mislukt het bouwen.

De nieuwe versie komt op je telefoon over de oude heen. Je foto's, projecten en instellingen
blijven staan. Dit gaat sneller dan de eerste keer, omdat het meeste al is gedownload.

---

## Deel C – Je foto's naar je computer kopiëren

1. Sluit je telefoon met de usb-kabel aan en ontgrendel hem.
2. Veeg op je telefoon vanaf de bovenrand omlaag en tik op de melding
   **Dit apparaat wordt opgeladen via USB**. Kies **Bestandsoverdracht**.
3. Open op je computer de **Verkenner** en klik links op **Deze pc**. Dubbelklik op je telefoon.
4. Ga naar **Interne gedeelde opslag → Documents → Everyday**.
5. Sleep de hele map **Everyday** naar een plek op je computer, bijvoorbeeld je map Afbeeldingen.

Zo ziet de map eruit:

```
Everyday
├── .nomedia                      zorgt dat je galerij en Google Foto's de map overslaan
├── Mijn gezicht
│   ├── .nomedia                  hetzelfde, voor als deze map ooit los ergens anders staat
│   ├── project.json              de instellingen van dit project
│   ├── 2026-11-23.jpg            één foto per dag
│   ├── 2026-11-24.jpg
│   └── timelapses
│       └── Mijn gezicht_2026-11-23_tot_2027-11-23.mp4
└── De boom in de tuin
    └── …
```

Zie je de nieuwste foto's niet in de Verkenner? Trek de kabel los en sluit hem opnieuw aan.

Zie je `.nomedia` niet? Windows verbergt bestanden waarvan de naam met een punt begint soms;
dat is in orde. Een bestand waarvan de naam met `.bezig-` begint is een foto of video die de
app op dat moment nog aan het opslaan is. Laat het staan; de app ruimt het zelf op.

---

## Deel D – Wat je moet bewaren

Twee dingen. Maak van allebei af en toe een kopie op een andere plek.

| Wat | Waar | Waarom |
|---|---|---|
| De map **Everyday** | op je telefoon, in Documenten | Dit zijn je foto's, video's en instellingen. Er bestaat geen kopie in de cloud. |
| De map **Everyday-sleutel** | op je computer, in `C:\Users\<jouw naam>` | Hiermee kun je de app bijwerken zonder hem eerst te verwijderen. |

**Sleutel kwijt?** Dat kost je geen foto's. Er wordt vanzelf een nieuwe sleutel gemaakt, maar
de app op je telefoon is nog met de oude ondertekend en moet er daarom eerst af. Het bestand
**installeer-op-telefoon** merkt dat en biedt aan om dat voor je te doen: antwoord dan met
**J**. Kies daarna in de app dezelfde map. Alles is terug.

Verwijder je de app liever zelf van je telefoon? Android vraagt dan of de app-gegevens bewaard
moeten blijven (een vakje met een tekst als "… aan app-gegevens behouden"). **Zet daar geen
vinkje.** Je foto's staan in je eigen map en blijven hoe dan ook staan; met het vinkje onthoudt
Android de oude sleutel en lukt installeren daarna nog steeds niet.

**Nieuwe telefoon?** Kopieer de map `Everyday` van je computer naar **Documents** op de nieuwe
telefoon, installeer de app en kies die map. Je projecten gaan verder waar je was.

**App verwijderd?** Je foto's blijven staan, want ze staan in je eigen map en niet in de app.
Installeer de app opnieuw en kies de map.

---

## Als het niet lukt

**"Er is geen telefoon gevonden"**
Controleer of de telefoon ontgrendeld is en of USB-foutopsporing aan staat (stap 3).
Probeer een andere usb-kabel of een andere usb-poort. Sommige kabels kunnen alleen opladen.

**De vraag "USB-foutopsporing toestaan?" verschijnt niet op mijn telefoon**
Trek de kabel los, ontgrendel je telefoon en sluit de kabel opnieuw aan. Helpt dat niet, ga
dan op je telefoon naar de ontwikkelaarsopties, tik op **Autorisaties USB-foutopsporing
intrekken**, en sluit de kabel opnieuw aan.

**"Android Studio is niet gevonden"**
Android Studio staat niet op de standaardplek. Installeer het opnieuw en laat de voorgestelde
map staan (stap 1).

**"De Android SDK is niet gevonden op de gebruikelijke plek"**
Heb je Android Studio nog nooit gestart, doe dat dan en doorloop de wizard (stap 1). Heb je dat
al gedaan, dan heeft Android Studio de bouwstenen op een andere plek gezet (dat gebeurt
bijvoorbeeld als er een letter met een accent in je Windows-gebruikersnaam zit). Start Android
Studio, kies **Open**, wijs de map in `C:\Everyday` aan en wacht tot het laden onderin klaar
is. Sluit Android Studio en dubbelklik opnieuw op **installeer-op-telefoon**.

**In het zwarte venster staat `INSTALL_FAILED_UPDATE_INCOMPATIBLE`**
Op je telefoon staat een versie van de app die met een andere sleutel is ondertekend (zie
"Sleutel kwijt?" in deel D). Het venster vraagt of het de oude app van je telefoon mag halen:
antwoord met **J**. Je foto's blijven staan; kies daarna in de app dezelfde map.
Had je de app al zelf verwijderd en krijg je deze melding toch? Dan heb je bij het verwijderen
de app-gegevens laten bewaren. Ook dan lost het antwoord **J** het op.

**In het zwarte venster staan veel regels met `Unresolved reference` of `Redeclaration`**
Er staan bestanden van een oudere versie tussen de nieuwe, omdat de nieuwe versie over de oude
heen is uitgepakt. Verwijder de map in `C:\Everyday`, pak de zip opnieuw uit en probeer het
opnieuw (deel B).

**In het zwarte venster staat iets over `SDK` of `licence`**
Start Android Studio één keer, wacht tot het klaar is met downloaden en sluit het weer.
Dubbelklik daarna opnieuw op **installeer-op-telefoon**.

**De app zegt "Kies een map", terwijl ik dat al gedaan had**
De app is zijn toegang tot de map kwijt, bijvoorbeeld na opnieuw installeren of omdat de map
is hernoemd. Kies dezelfde map opnieuw; er gaat niets verloren.

**Liever via Android Studio zelf?**
Start Android Studio, kies **Open** en wijs de map in `C:\Everyday` aan. Wacht tot onderin
"Gradle sync" klaar is. Sluit je telefoon aan en klik bovenin op de groene driehoek ▶.
Dat doet hetzelfde als het bestand **installeer-op-telefoon**, met dezelfde sleutel.
