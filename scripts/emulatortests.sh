#!/usr/bin/env bash
# Runs the tests that need a real Android system (camera, storage, video encoder) on
# the emulator that is already running, then collects what they left behind:
# screenshots of the app in use and the numbers they measured.
set -uo pipefail

out=app/build/emulator
mkdir -p "$out"

adb logcat -c || true
./gradlew --no-daemon connectedDebugAndroidTest
status=$?

adb pull /sdcard/Download/dagelijks-test "$out/schermafbeeldingen" >/dev/null 2>&1 || echo "Geen schermafbeeldingen op de emulator gevonden."
adb logcat -d -s DagelijksTest:I > "$out/metingen.txt" 2>/dev/null || true
adb logcat -d > "$out/logcat.txt" 2>/dev/null || true

echo "---- Wat de tests hebben gemeten ----"
cat "$out/metingen.txt" 2>/dev/null || true
echo "---- Schermafbeeldingen ----"
ls -la "$out/schermafbeeldingen" 2>/dev/null || true

exit $status
