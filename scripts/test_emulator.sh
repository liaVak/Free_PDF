#!/usr/bin/env bash
# Run the built instrumentation APK directly, without downloading Gradle UTP.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

serial="${1:-emulator-5554}"
if [[ "$serial" != emulator-* ]]; then
  echo "Specify a local emulator serial (for example emulator-5554)." >&2
  exit 2
fi
app_apk=app/build/outputs/apk/debug/app-debug.apk
test_apk=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if [[ ! -f "$app_apk" || ! -f "$test_apk" ]]; then
  echo "Build first: ./gradlew assembleDebug assembleDebugAndroidTest" >&2
  exit 2
fi
mkdir -p output/logs output/emulator
adb_cmd=(adb -s "$serial")
echo "Waiting for Android boot on $serial (up to 20 minutes)..."
deadline=$((SECONDS + 1200))
while [[ "$(timeout 15 "${adb_cmd[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" != 1 ]]; do
  if (( SECONDS >= deadline )); then
    echo "Android did not finish booting; no tests were run." >&2
    exit 1
  fi
  sleep 10
done

echo "Installing application and instrumentation APK..."
timeout 300 "${adb_cmd[@]}" install -r "$app_apk"
timeout 300 "${adb_cmd[@]}" install -r "$test_apk"
"${adb_cmd[@]}" shell input keyevent KEYCODE_WAKEUP
"${adb_cmd[@]}" shell wm dismiss-keyguard
echo "Running Android tests..."
timeout 1200 "${adb_cmd[@]}" shell am instrument -w -r -e timeout_msec 180000 \
  ru.ilyavakarin.pdfreader.test/androidx.test.runner.AndroidJUnitRunner \
  | tee output/logs/emulator-instrumentation.log

echo "Launching PDF Reader..."
"${adb_cmd[@]}" shell am start -W -n ru.ilyavakarin.pdfreader/.MainActivity
"${adb_cmd[@]}" logcat -d -b crash > output/logs/emulator-crashes.log
if ! grep -Eq '^OK \([0-9]+ tests?\)' output/logs/emulator-instrumentation.log; then
  echo "Android tests did not pass. See output/logs/emulator-instrumentation.log." >&2
  exit 1
fi
echo "All Android tests passed."
