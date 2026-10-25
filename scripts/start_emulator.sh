#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
sdkmanager 'emulator' 'system-images;android-31;default;x86_64'
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-/tmp/pdf-reader-avd}"
mkdir -p "$ANDROID_AVD_HOME"
if ! test -f "$ANDROID_AVD_HOME/pdf-reader-api31.ini"; then
  echo no | avdmanager create avd --name pdf-reader-api31 --package 'system-images;android-31;default;x86_64' --device pixel_2
fi
printf '\ndisk.dataPartition.size=512M\nhw.lcd.width=600\nhw.lcd.height=800\nhw.lcd.density=160\n' >> "$ANDROID_AVD_HOME/pdf-reader-api31.avd/config.ini"
if test -r /dev/kvm && test -w /dev/kvm; then
  exec emulator -avd pdf-reader-api31 -no-snapshot -gpu swiftshader -memory 1536 -no-audio "$@"
else
  exec emulator -avd pdf-reader-api31 -accel off -no-snapshot -gpu swiftshader -memory 1536 -cores 2 -no-audio "$@"
fi
