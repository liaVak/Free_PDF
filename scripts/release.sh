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
  if compgen -G "output/apk/pdf-reader-*.apk" > /dev/null; then
    echo "Existing release key is missing; restore its backup before building an update." >&2
    exit 1
  fi
  keytool -genkeypair -keystore "$PDF_READER_KEYSTORE" -storepass:env PDF_READER_STORE_PASSWORD -keypass:env PDF_READER_KEY_PASSWORD -alias pdf-reader -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=PDF Reader Local Release' -storetype PKCS12
fi
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest assembleRelease --console=plain
mkdir -p output/apk
apk=app/build/outputs/apk/release/app-release.apk
version=$(python3 - <<'PYVERSION'
import json
from pathlib import Path
metadata=json.loads(Path('app/build/outputs/apk/release/output-metadata.json').read_text())
print(metadata['elements'][0]['versionName'])
PYVERSION
)
output="output/apk/pdf-reader-$version.apk"
cp "$apk" "$output"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose "$output"
sha256sum "$output" > "$output.sha256"
if test -f app/build/outputs/mapping/release/mapping.txt; then
  gzip -c app/build/outputs/mapping/release/mapping.txt > "$output.mapping.txt.gz"
fi
