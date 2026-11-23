@echo off
rem Builds Dagelijks and puts it on the phone that is connected with a USB cable.
rem Double-click this file in Windows Explorer. See HANDLEIDING.md for the steps before it.
setlocal
title Dagelijks op je telefoon zetten
cd /d "%~dp0"

echo.
echo   Dagelijks op je telefoon zetten
echo   ===============================
echo.

rem --- 1. Java: Android Studio brings its own copy -------------------------------------
if not defined JAVA_HOME goto zoek_java
if exist "%JAVA_HOME%\bin\java.exe" goto java_gevonden

:zoek_java
set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
if exist "%JAVA_HOME%\bin\java.exe" goto java_gevonden
set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android Studio\jbr"
if exist "%JAVA_HOME%\bin\java.exe" goto java_gevonden
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
"%ADB%" start-server >nul 2>nul
"%ADB%" devices | findstr /R /C:"device$" >nul
if not errorlevel 1 goto bouwen

"%ADB%" devices | findstr /C:"unauthorized" >nul
if errorlevel 1 goto geen_telefoon
echo   Je telefoon is gevonden, maar heeft deze computer nog niet toegelaten.
echo   Kijk op het scherm van je telefoon: daar staat de vraag
echo   "USB-foutopsporing toestaan?". Vink "Altijd toestaan" aan en tik op Toestaan.
echo   Start dit bestand daarna opnieuw.
goto mislukt

:geen_telefoon
echo   Er is geen telefoon gevonden.
echo.
echo   Controleer dit:
echo    1. De telefoon zit met een usb-kabel aan deze computer en is ontgrendeld.
echo    2. USB-foutopsporing staat aan (HANDLEIDING.md, stap 3).
echo    3. De kabel kan ook gegevens doorgeven (niet elke oplaadkabel kan dat).
echo   Start dit bestand daarna opnieuw.
goto mislukt

:bouwen
echo   De app wordt gebouwd en op je telefoon gezet.
echo   De allereerste keer duurt dat lang (tien minuten of meer), daarna veel korter.
echo   Laat dit venster open en je telefoon aangesloten.
echo.
call gradlew.bat installRelease
if errorlevel 1 goto bouwen_mislukt

if defined ADB "%ADB%" shell am start -n nl.constantdynamics.dagelijks/.MainActivity >nul 2>nul
echo.
echo   Klaar! Dagelijks staat op je telefoon.
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
