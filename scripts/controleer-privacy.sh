#!/usr/bin/env bash
# Checks a built APK against the app's privacy promises, independently of the build:
# it looks at what is really inside the APK with Android's own aapt2 tool.
#
#   scripts/controleer-privacy.sh app/build/outputs/apk/release/app-release.apk
set -euo pipefail

apk="${1:?Geef het pad naar een APK op}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
aapt2="$(ls -1 "$sdk"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1 || true)"
if [ -z "$aapt2" ]; then
  echo "aapt2 niet gevonden; is ANDROID_HOME ingesteld?" >&2
  exit 2
fi

permissions="$("$aapt2" dump permissions "$apk")"
manifest="$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk")"
package="$(echo "$permissions" | sed -n 's/^package: //p')"

echo "APK:      $apk"
echo "Pakket:   $package"
echo "Permissies volgens 'aapt2 dump permissions':"
echo "$permissions" | sed 's/^/    /'

fouten=0
fout() { echo "FOUT: $1"; fouten=$((fouten + 1)); }

# 1. No network access of any kind.
for verboden in INTERNET ACCESS_NETWORK_STATE ACCESS_WIFI_STATE CHANGE_NETWORK_STATE CHANGE_WIFI_STATE; do
  if echo "$permissions" | grep -q "android.permission.$verboden'"; then fout "netwerkpermissie aanwezig: $verboden"; fi
done

# 2. The camera is the only permission requested; the one extra entry is AndroidX's
#    own private, signature-level permission that nobody else can hold.
toegestaan="android.permission.CAMERA
$package.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
aangevraagd="$(echo "$permissions" | sed -n "s/^uses-permission[^:]*: name='\([^']*\)'.*/\1/p" | sort -u)"
while IFS= read -r permissie; do
  [ -z "$permissie" ] && continue
  if ! echo "$toegestaan" | grep -qxF "$permissie"; then fout "onverwachte permissie: $permissie"; fi
done <<< "$aangevraagd"
echo "$aangevraagd" | grep -qxF "android.permission.CAMERA" || fout "de CAMERA-permissie ontbreekt"

# 3. Nothing goes into backups or along to a new phone.
echo "$manifest" | grep -q 'allowBackup(0x01010280)=false' || fout "allowBackup staat niet op false"
echo "$manifest" | grep -q 'hasFragileUserData(0x0101059a)=true' || fout "hasFragileUserData staat niet op true"
echo "$manifest" | grep -q 'dataExtractionRules(0x0101063e)=@' || fout "dataExtractionRules ontbreekt"
echo "$manifest" | grep -q 'fullBackupContent(0x010104eb)=@' || fout "fullBackupContent ontbreekt"
echo "$manifest" | grep -q 'usesCleartextTraffic(0x010104ec)=false' || fout "usesCleartextTraffic staat niet op false"

# 4. No browser component and no automatic emoji font download.
if echo "$manifest" | grep -qi 'webview'; then fout "er zit een WebView-verwijzing in het manifest"; fi
if echo "$manifest" | grep -q 'EmojiCompatInitializer'; then fout "EmojiCompatInitializer is niet verwijderd"; fi

if [ "$fouten" -gt 0 ]; then
  echo "Privacycontrole MISLUKT ($fouten probleem/problemen)."
  exit 1
fi
echo "Privacycontrole geslaagd: geen netwerkpermissies, enige toestelpermissie is CAMERA,"
echo "allowBackup=false en de back-upregels sluiten alles uit."
