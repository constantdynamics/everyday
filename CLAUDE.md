# Afspraken voor dit project

Everyday is een privé Android-app voor een dagelijkse foto-timelapse. De eigenaar programmeert
niet: leg in gewone taal uit wat hij zelf moet doen, en doe de rest zelf.

- **Pull requests meteen samenvoegen.** De eigenaar heeft op 23 november 2026 gevraagd om pull
  requests voortaan meteen samen te voegen, zonder eerst te vragen. Invulling van Claude (niet
  zo gezegd door de eigenaar): wacht tot de workflow "Bouwen en testen" groen is, zodat de
  hoofdversie altijd een werkende app blijft.
- **Privacy is niet onderhandelbaar.** Geen netwerkpermissies, geen analytics, geen back-up
  naar de cloud, alleen de runtime-permissie `CAMERA`. De build controleert dit zelf
  (`controleerPrivacy…` en `scripts/controleer-privacy.sh`); zie het hoofdstuk Privacy in
  `README.md`.
- **Namen.** De app heet Everyday. De map met foto's op de telefoon heet ook `Everyday` en de
  map met de ondertekensleutel op de computer heet `Everyday-sleutel`. Het app-id en de
  pakketnaam blijven `nl.constantdynamics.dagelijks`.
- **Vormgeving.** De eigenaar koos het ontwerp "Nachtfilm": donker inktblauw, warm wit, amber,
  en koraalrood alleen voor de sluiter en de ene actie die telt. Kleuren en letters staan in
  `ui/theme/Theme.kt`. De eigenaar vond dikke letters, dikke randen, harde schaduwen, drukke
  achtergronden en een kinderlijke uitstraling lelijk; houd het rustig.
- **Taal.** Alles wat de gebruiker ziet is Nederlands; code en commentaar zijn Engels.
- **Testen.** `./gradlew testDebugUnitTest lintDebug` voor de unittests en lint; de tests op
  een emulator draaien in de workflow (`.github/workflows/bouw.yml`).
