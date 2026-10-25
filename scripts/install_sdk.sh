#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
# SDK licenses are required for the requested local Android development environment.
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses > .tools/sdk-licenses.log 2>&1 || test "${PIPESTATUS[1]}" = 0
sdkmanager --sdk_root="$ANDROID_HOME" 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
java -version
adb version
