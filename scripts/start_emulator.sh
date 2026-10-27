#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
if ! test -x "$ANDROID_HOME/emulator/emulator" || ! test -f "$ANDROID_HOME/system-images/android-31/default/x86_64/system.img"; then
  sdkmanager 'emulator' 'system-images;android-31;default;x86_64'
fi
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$PDF_READER_ROOT/.tools/avd}"
mkdir -p "$ANDROID_AVD_HOME"
if ! test -f "$ANDROID_AVD_HOME/pdf-reader-api31.avd/config.ini"; then
  echo no | avdmanager create avd --force --name pdf-reader-api31 --package 'system-images;android-31;default;x86_64' --device pixel_2
fi
python3 - <<'PY'
from pathlib import Path
import os
p=Path(os.environ['ANDROID_AVD_HOME'])/'pdf-reader-api31.avd/config.ini'
config=dict(line.split('=',1) for line in p.read_text().splitlines() if '=' in line)
config.update({'disk.dataPartition.size':'1024M','hw.lcd.width':'480','hw.lcd.height':'800','hw.lcd.density':'200','hw.keyboard':'yes'})
p.write_text('\n'.join(k+'='+v for k,v in config.items())+'\n')
PY
if test -r /dev/kvm && test -w /dev/kvm; then
  exec emulator -avd pdf-reader-api31 -no-snapshot -gpu swiftshader -memory 1536 -no-audio "$@"
else
  exec emulator -avd pdf-reader-api31 -accel off -no-snapshot -gpu swiftshader -memory 1536 -cores 2 -no-audio "$@"
fi
