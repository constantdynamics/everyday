@echo off
rem Builds Everyday and puts it on the phone that is connected with a USB cable.
rem Double-click this file in Windows Explorer. See HANDLEIDING.md for the steps before it.
setlocal
title Everyday op je telefoon zetten
cd /d "%~dp0"

echo.
echo   Everyday op je telefoon zetten
echo   ==============================
echo.

rem --- 0. A new version unpacked over an old one leaves old files between the new ones,
rem and then the build fails with errors nobody can make sense of. These files only
rem existed in the first version.
if exist "app\src\main\java\nl\constantdynamics\dagelijks\ui\components\RetroControls.kt" goto oude_bestanden
if exist "app\src\main\java\nl\constantdynamics\dagelijks\ui\components\RetroText.kt" goto oude_bestanden

rem --- 1. Java: Android Studio brings its own copy, and that is the one that fits ------
rem A Java that happens to be installed for something else may be too old, so it is
rem only used when Android Studio is not in one of its usual places.
set "EIGEN_JAVA=%JAVA_HOME%"
set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
if exist "%JAVA_HOME%\bin\java.exe" goto java_gevonden
set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android Studio\jbr"
if exist "%JAVA_HOME%\bin\java.exe" goto java_gevonden
set "JAVA_HOME=%EIGEN_JAVA%"
if not defined JAVA_HOME goto geen_java
if exist "%JAVA_HOME%\bin\java.exe" goto java_gevonden

:geen_java
echo   Android Studio is niet gevonden op deze computer.
echo   Installeer eerst Android Studio (HANDLEIDING.md, stap 1) en probeer het daarna opnieuw.
goto mislukt

:java_gevonden
rem --- 2. The Android SDK that Android Studio installed ------------------------------
rem ANDROID_HOME and ANDROID_SDK_ROOT only count when they really point at an SDK;
rem otherwise the place where Android Studio puts it by default is used.
set "SDK=%ANDROID_HOME%"
if defined SDK if exist "%SDK%\platform-tools\adb.exe" goto sdk_gevonden
set "SDK=%ANDROID_SDK_ROOT%"
if defined SDK if exist "%SDK%\platform-tools\adb.exe" goto sdk_gevonden
set "SDK=%LOCALAPPDATA%\Android\Sdk"
if exist "%SDK%\platform-tools\adb.exe" goto sdk_gevonden
rem Opening the project in Android Studio writes down where the SDK is; that is enough to build.
if exist "local.properties" goto bouwen
echo   De Android SDK is niet gevonden op de gebruikelijke plek.
echo.
echo   Heb je Android Studio nog nooit gestart? Start het dan een keer en doorloop de
echo   installatiewizard tot het einde (HANDLEIDING.md, stap 1).
echo.
echo   Heb je dat al gedaan? Dan staat de SDK op een andere plek. Start Android Studio,
echo   kies Open, wijs deze map aan en wacht tot het laden onderin klaar is. Sluit
echo   Android Studio daarna af en start dit bestand opnieuw.
goto mislukt

:sdk_gevonden
set "ANDROID_HOME=%SDK%"
set "ANDROID_SDK_ROOT=%SDK%"
set "ADB=%SDK%\platform-tools\adb.exe"

rem --- 3. Is a phone connected, and has it accepted this computer? -------------------
rem "adb get-state" succeeds only when exactly one phone is connected and trusted.
rem A phone that was plugged in a moment ago needs a few seconds to show up.
echo   Ik zoek je telefoon...
"%ADB%" start-server >nul 2>nul
set /a POGING=0

:zoek_telefoon
"%ADB%" get-state >nul 2>nul
if not errorlevel 1 goto bouwen
"%ADB%" devices | findstr /C:"unauthorized" >nul
if not errorlevel 1 goto vraag_toestemming
set /a POGING+=1
if %POGING% geq 6 goto geen_telefoon
ping -n 3 127.0.0.1 >nul
goto zoek_telefoon

:vraag_toestemming
echo.
echo   Je telefoon is gevonden. Kijk nu op het scherm van je telefoon. Daar staat de vraag
echo   "USB-foutopsporing toestaan?". Vink "Altijd toestaan vanaf deze computer" aan
echo   en tik op Toestaan. Dit venster wacht tot je dat hebt gedaan.
set /a POGING=0

:wacht_op_toestemming
ping -n 3 127.0.0.1 >nul
"%ADB%" get-state >nul 2>nul
if not errorlevel 1 goto bouwen
set /a POGING+=1
if %POGING% lss 60 goto wacht_op_toestemming
echo.
echo   Er is geen toestemming gegeven. Zie je de vraag niet op je telefoon? Trek de kabel
echo   los, ontgrendel je telefoon, sluit de kabel opnieuw aan en start dit bestand opnieuw.
goto mislukt

:geen_telefoon
echo.
echo   Er is geen telefoon gevonden (of er zijn er meerdere aangesloten).
echo.
echo   Controleer dit:
echo    1. De telefoon zit met een usb-kabel aan deze computer en is ontgrendeld.
echo    2. USB-foutopsporing staat aan (HANDLEIDING.md, stap 3).
echo    3. De kabel kan ook gegevens doorgeven (niet elke oplaadkabel kan dat).
echo   Start dit bestand daarna opnieuw.
goto mislukt

:bouwen
echo.
echo   De app wordt gebouwd en op je telefoon gezet.
echo   De allereerste keer duurt dat lang (tien minuten of meer), daarna veel korter.
echo   Laat dit venster open en je telefoon aangesloten. Kijk af en toe op je telefoon:
echo   vraagt die iets over installeren, kies dan Installeren of Toch installeren.
echo.
rem An app file from an earlier attempt is removed first. If there is one after a failed
rem build, it was made just now, so the building went well and only the installing failed.
set "APK=app\build\outputs\apk\release\app-release.apk"
if exist "%APK%" del "%APK%"
call gradlew.bat --no-daemon installRelease
if errorlevel 1 goto bouwen_mislukt

:gelukt
if defined ADB "%ADB%" shell am start -n nl.constantdynamics.dagelijks/.MainActivity >nul 2>nul
echo.
echo   Klaar! Everyday staat op je telefoon.
echo.
echo   Denk aan een reservekopie van de map met je ondertekensleutel:
echo     %USERPROFILE%\Everyday-sleutel
echo.
pause
exit /b 0

:bouwen_mislukt
echo.
echo   Het is niet gelukt. Hierboven staat de foutmelding.
if not defined ADB goto meer_oplossingen
if not exist "%APK%" goto meer_oplossingen
rem The app was built, so putting it on the phone is what failed. Doing that once more
rem by hand shows the reason: a phone that holds an app signed with another key says so.
"%ADB%" install -r "%APK%" > "%TEMP%\everyday-installatie.txt" 2>&1
findstr /C:"Success" "%TEMP%\everyday-installatie.txt" >nul
if not errorlevel 1 goto gelukt
findstr /C:"INSTALL_FAILED_UPDATE_INCOMPATIBLE" "%TEMP%\everyday-installatie.txt" >nul
if errorlevel 1 goto meer_oplossingen
echo.
echo   De oorzaak is gevonden: op je telefoon staat al een versie van de app die met een
echo   andere sleutel is ondertekend. Die moet er eerst af. Je foto's blijven staan: die
echo   staan in je eigen map en niet in de app.
if defined AL_VERWIJDERD goto zelf_verwijderen
echo.
choice /c JN /n /m "  Zal ik de oude app van je telefoon halen en het opnieuw proberen? Typ J of N: "
if errorlevel 2 goto zelf_verwijderen
if not errorlevel 1 goto zelf_verwijderen
set "AL_VERWIJDERD=1"
echo.
echo   De oude app wordt van je telefoon gehaald...
"%ADB%" uninstall nl.constantdynamics.dagelijks
echo   Kies straks in de app dezelfde map opnieuw. Al je foto's staan er nog.
goto bouwen

:zelf_verwijderen
echo.
echo   Verwijder de app zelf van je telefoon en start dit bestand opnieuw. Vraagt je telefoon
echo   bij het verwijderen of de app-gegevens bewaard moeten blijven? Zet daar GEEN vinkje.

:meer_oplossingen
echo.
echo   Oplossingen voor de meest voorkomende problemen staan onderaan HANDLEIDING.md.
goto mislukt

:oude_bestanden
echo   In deze map staan nog bestanden van een oudere versie tussen de nieuwe.
echo   Dat gebeurt als je een nieuwe versie over de oude heen uitpakt.
echo.
echo   Verwijder deze hele map, pak het zip-bestand opnieuw uit en start dan opnieuw
echo   (HANDLEIDING.md, Deel B). Je sleutel en je foto's staan ergens anders en blijven staan.

:mislukt
echo.
pause
exit /b 1
