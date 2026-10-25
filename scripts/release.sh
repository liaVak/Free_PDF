#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
# The directory is ignored by Git; back it up separately before distributing APKs.
mkdir -p .secrets
chmod 700 .secrets
if ! test -f .secrets/release.env; then
  umask 077
  python3 - <<'PY'
from pathlib import Path
import secrets
password=secrets.token_urlsafe(32)
p=Path('.secrets/release.env')
p.write_text('export PDF_READER_KEYSTORE="'+str(Path('.secrets/pdf-reader.jks').resolve())+'"\nexport PDF_READER_STORE_PASSWORD="'+password+'"\nexport PDF_READER_KEY_PASSWORD="'+password+'"\n')
PY
fi
source .secrets/release.env
if ! test -f "$PDF_READER_KEYSTORE"; then
  keytool -genkeypair -keystore "$PDF_READER_KEYSTORE" -storepass:env PDF_READER_STORE_PASSWORD -keypass:env PDF_READER_KEY_PASSWORD -alias pdf-reader -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=PDF Reader Local Release' -storetype PKCS12
fi
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --console=plain
mkdir -p output/apk
cp app/build/outputs/apk/release/app-release.apk output/apk/pdf-reader-0.1.0.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose output/apk/pdf-reader-0.1.0.apk
sha256sum output/apk/pdf-reader-0.1.0.apk > output/apk/pdf-reader-0.1.0.apk.sha256
