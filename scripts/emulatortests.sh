#!/usr/bin/env bash
# Runs the tests that need a real Android system (camera, storage, video encoder) on
# the emulator that is already running, then collects what they left behind:
# screenshots of the app in use and the numbers they measured.
set -uo pipefail

out=app/build/emulator
mkdir -p "$out"

# A slow emulator shows "isn't responding" messages that get in the way of the tests.
adb shell settings put global hide_error_dialogs 1 || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
adb logcat -c || true
./gradlew --no-daemon connectedDebugAndroidTest
status=$?

adb pull /sdcard/Download/dagelijks-test "$out/schermafbeeldingen" >/dev/null 2>&1 || echo "Geen schermafbeeldingen op de emulator gevonden."
adb logcat -d -s DagelijksTest:I > "$out/metingen.txt" 2>/dev/null || true
adb logcat -d > "$out/logcat.txt" 2>/dev/null || true

echo "---- Uitslag per test ----"
python3 - <<'PY'
import glob, re, html
total = failed = 0
for path in sorted(glob.glob('app/build/outputs/androidTest-results/connected/**/*.xml', recursive=True)):
    text = open(path, encoding='utf-8', errors='replace').read()
    for case in re.finditer(r'<testcase name="([^"]+)" classname="([^"]+)"[^>]*?(/>|>(.*?)</testcase>)', text, re.S):
        total += 1
        body = case.group(4) or ''
        problem = re.search(r'<(failure|error)[^>]*>(.*?)</(failure|error)>', body, re.S)
        skipped = '<skipped' in body
        name = f"{case.group(2).split('.')[-1]}.{case.group(1)}"
        if problem:
            failed += 1
            print(f"MISLUKT  {name}")
            for line in html.unescape(problem.group(2)).strip().splitlines()[:14]:
                print(f"         {line[:300]}")
        else:
            print(f"{'overgeslagen' if skipped else 'geslaagd':<8} {name}")
print(f"TOTAAL {total} tests op de emulator, {failed} mislukt")
PY

echo "---- Wat de tests hebben gemeten ----"
cat "$out/metingen.txt" 2>/dev/null || true
echo "---- Schermafbeeldingen ----"
ls -la "$out/schermafbeeldingen" 2>/dev/null || true

exit $status
