# Handleiding Dagelijks

Zo zet je de app op je telefoon, werk je hem bij en maak je een reservekopie van je foto's.
Je hoeft niet te kunnen programmeren. Alles wat je moet doen staat hier stap voor stap.

**Wat je nodig hebt**

- een computer met Windows
- je Android-telefoon (Android 8 of nieuwer)
- een usb-kabel die ook gegevens doorgeeft (niet elke oplaadkabel kan dat)
- ongeveer een uur voor de eerste keer, waarvan het grootste deel wachten is

---

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
4. Kies als doelmap `C:\Dagelijks` en klik op **Uitpakken**.

Gebruik een korte map zoals `C:\Dagelijks`. Zet hem niet in OneDrive of op je bureaublad:
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
   - Op je telefoon verschijnt de vraag **USB-foutopsporing toestaan?**
     Vink **Altijd toestaan vanaf deze computer** aan en tik op **Toestaan**.

### Stap 4. De app bouwen en installeren

1. Open de map `C:\Dagelijks` in de Verkenner en ga de map in die erin staat.
2. Dubbelklik op **installeer-op-telefoon** (het volledige bestand heet `installeer-op-telefoon.bat`).
3. Zegt Windows "Windows heeft uw pc beschermd"? Klik op **Meer informatie** en dan op **Toch uitvoeren**.
   Windows waarschuwt bij elk programmaatje dat van internet komt.
4. Er opent een zwart venster. Laat het open en wacht.
   De allereerste keer duurt dit tien minuten of langer, omdat er eenmalig veel wordt gedownload.
5. Vraagt je telefoon of de app naar Google mag worden gestuurd voor een veiligheidscontrole
   (Play Protect)? Kies **Niet verzenden**. Vraagt hij of je de app toch wilt installeren? Kies **Toch installeren**.
6. Staat er in het venster **Klaar! Dagelijks staat op je telefoon.**, dan is het gelukt.
   De app opent vanzelf op je telefoon.

> **Je ondertekensleutel.** Bij de eerste keer bouwen is er een map
> `C:\Users\<jouw naam>\Dagelijks-sleutel` aangemaakt met twee bestanden (in de Verkenner
> heet `Users` "Gebruikers"). Dat is de
> vaste sleutel waarmee jouw app wordt ondertekend. Dankzij die sleutel kan een nieuwe
> versie straks over de oude heen worden geïnstalleerd. **Maak een reservekopie van deze
> map**, bijvoorbeeld op een usb-stick. Deel hem met niemand.

### Stap 5. De eerste keer de app openen

1. De app vraagt waar je foto's mogen staan. Tik op **Kies een map**.
2. Er opent een scherm van Android zelf, in de map **Documenten**. Je hebt twee mogelijkheden:
   - **Aanbevolen:** tik rechtsboven op de drie puntjes of op het mapje met een plusje, kies
     **Nieuwe map**, noem hem `Dagelijks` en tik op **OK**. Je staat nu in die nieuwe map.
   - **Makkelijker:** doe niets en blijf in Documenten. De app maakt de map `Dagelijks` dan zelf aan.
3. Tik onderaan op **Deze map gebruiken** en daarna op **Toestaan**.
4. Je bent nu op het startscherm. Tik op **Nieuw project** om te beginnen.

De eerste keer dat je de camera opent, vraagt Android of Dagelijks de camera mag gebruiken.
Kies **Tijdens gebruik van de app**. Dat is de enige toestemming die de app vraagt.

---

## Deel B – De app bijwerken

Als er een nieuwe versie van de app is:

1. Download de zip opnieuw (stap 2) en pak hem uit in `C:\Dagelijks`.
   Kies **Bestanden in de doelmap vervangen** als Windows daarom vraagt.
2. Sluit je telefoon aan en dubbelklik weer op **installeer-op-telefoon**.

De nieuwe versie komt over de oude heen. Je foto's, projecten en instellingen blijven staan.
Dit gaat veel sneller dan de eerste keer.

---

## Deel C – Je foto's naar je computer kopiëren

1. Sluit je telefoon met de usb-kabel aan en ontgrendel hem.
2. Veeg op je telefoon vanaf de bovenrand omlaag en tik op de melding
   **Dit apparaat wordt opgeladen via USB**. Kies **Bestandsoverdracht**.
3. Open op je computer de **Verkenner** en klik links op **Deze pc**. Dubbelklik op je telefoon.
4. Ga naar **Interne gedeelde opslag → Documents → Dagelijks**.
5. Sleep de hele map **Dagelijks** naar een plek op je computer, bijvoorbeeld je map Afbeeldingen.

Zo ziet de map eruit:

```
Dagelijks
├── .nomedia                      zorgt dat je galerij en Google Foto's de map overslaan
├── Mijn gezicht
│   ├── project.json              de instellingen van dit project
│   ├── 2026-11-23.jpg            één foto per dag
│   ├── 2026-11-24.jpg
│   └── timelapses
│       └── Mijn gezicht_2026-11-23_tot_2027-11-23.mp4
└── De boom in de tuin
    └── …
```

Zie je de nieuwste foto's niet in de Verkenner? Trek de kabel los en sluit hem opnieuw aan.

---

## Deel D – Wat je moet bewaren

Twee dingen. Maak van allebei af en toe een kopie op een andere plek.

| Wat | Waar | Waarom |
|---|---|---|
| De map **Dagelijks** | op je telefoon, in Documenten | Dit zijn je foto's, video's en instellingen. Er bestaat geen kopie in de cloud. |
| De map **Dagelijks-sleutel** | op je computer, in `C:\Users\<jouw naam>` | Hiermee kun je de app bijwerken zonder hem eerst te verwijderen. |

**Sleutel kwijt?** Dat kost je geen foto's. Verwijder de app van je telefoon, installeer hem
opnieuw (er wordt dan een nieuwe sleutel gemaakt) en kies in de app dezelfde map. Alles is terug.

**Nieuwe telefoon?** Kopieer de map `Dagelijks` van je computer naar **Documents** op de nieuwe
telefoon, installeer de app en kies die map. Je projecten gaan verder waar je was.

**App verwijderd?** Je foto's blijven staan, want ze staan in je eigen map en niet in de app.
Installeer de app opnieuw en kies de map.

---

## Als het niet lukt

**"Er is geen telefoon gevonden"**
Controleer of de telefoon ontgrendeld is, of USB-foutopsporing aan staat (stap 3) en of je
de vraag op je telefoon hebt beantwoord met Toestaan. Probeer een andere usb-kabel of een
andere usb-poort. Sommige kabels kunnen alleen opladen.

**"Android Studio is niet gevonden"**
Android Studio staat niet op de standaardplek. Installeer het opnieuw en laat de voorgestelde
map staan (stap 1).

**In het zwarte venster staat `INSTALL_FAILED_UPDATE_INCOMPATIBLE`**
Op je telefoon staat een versie van de app die met een andere sleutel is ondertekend.
Verwijder de app van je telefoon en probeer het opnieuw. Je foto's blijven staan; kies
daarna in de app dezelfde map.

**In het zwarte venster staat iets over `SDK` of `licence`**
Start Android Studio één keer, wacht tot het klaar is met downloaden en sluit het weer.
Dubbelklik daarna opnieuw op **installeer-op-telefoon**.

**De app zegt "Kies een map", terwijl ik dat al gedaan had**
De app is zijn toegang tot de map kwijt, bijvoorbeeld na opnieuw installeren of omdat de map
is hernoemd. Kies dezelfde map opnieuw; er gaat niets verloren.

**Liever via Android Studio zelf?**
Start Android Studio, kies **Open** en wijs de map in `C:\Dagelijks` aan. Wacht tot onderin
"Gradle sync" klaar is. Sluit je telefoon aan en klik bovenin op de groene driehoek ▶.
Dat doet hetzelfde als het bestand **installeer-op-telefoon**, met dezelfde sleutel.
