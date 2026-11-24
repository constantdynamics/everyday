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
import glob, re, html, sys
total = failed = left_out = 0
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
            left_out += 1 if skipped else 0
            print(f"{'overgeslagen' if skipped else 'geslaagd':<8} {name}")
print(f"TOTAAL {total} tests op de emulator, {failed} mislukt, {left_out} overgeslagen")
# A test that was skipped has proven nothing, so it does not count as passed. The same
# goes for a run in which no test reported at all.
if left_out or total == 0:
    print("NIET GOED: overgeslagen of ontbrekende tests tellen als mislukt.")
    sys.exit(1)
PY
summary=$?

echo "---- Wat de tests hebben gemeten ----"
cat "$out/metingen.txt" 2>/dev/null || true
# When the app itself stopped with an error, this is where it says why.
echo "---- Fouten van de app zelf ----"
grep -E -A 30 "FATAL EXCEPTION|Compose Runtime internal error" "$out/logcat.txt" 2>/dev/null | head -150 || true
echo "---- Schermafbeeldingen ----"
ls -la "$out/schermafbeeldingen" 2>/dev/null || true

if [ "$status" -ne 0 ]; then exit "$status"; fi
exit "$summary"
