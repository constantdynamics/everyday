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
set "SDK=%LOCALAPPDATA%\Android\Sdk"
if defined ANDROID_HOME set "SDK=%ANDROID_HOME%"
if exist "%SDK%\platform-tools\adb.exe" goto sdk_gevonden
if exist "local.properties" goto bouwen
echo   De Android SDK is niet gevonden.
echo   Start Android Studio een keer en doorloop de installatiewizard tot het einde
echo   (HANDLEIDING.md, stap 1). Probeer het daarna opnieuw.
goto mislukt

:sdk_gevonden
set "ANDROID_HOME=%SDK%"
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
call gradlew.bat --no-daemon installRelease
if errorlevel 1 goto bouwen_mislukt

if defined ADB "%ADB%" shell am start -n nl.constantdynamics.dagelijks/.MainActivity >nul 2>nul
echo.
echo   Klaar! Everyday staat op je telefoon.
echo.
echo   Denk aan een reservekopie van de map met je ondertekensleutel:
echo     %USERPROFILE%\Dagelijks-sleutel
echo.
pause
exit /b 0

:bouwen_mislukt
echo.
echo   Het is niet gelukt. Hierboven staat de foutmelding.
echo.
echo   Staat er INSTALL_FAILED_UPDATE_INCOMPATIBLE? Dan staat er al een versie van de app
echo   op je telefoon die met een andere sleutel is ondertekend. Verwijder de app van je
echo   telefoon (je foto's blijven staan) en start dit bestand opnieuw.
echo   Meer oplossingen staan onderaan HANDLEIDING.md.

:mislukt
echo.
pause
exit /b 1
