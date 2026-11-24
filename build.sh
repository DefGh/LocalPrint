#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
JDK_DIR="${LOCALPRINT_JDK:-/opt/homebrew/opt/openjdk@17}"
SDK_DIR="${LOCALPRINT_SDK:-$PWD/.toolchain}"
BUILD_TOOLS="$SDK_DIR/android-16"
ANDROID_JAR="$SDK_DIR/android-36/android.jar"
SIGNING_STORE="${LOCALPRINT_KEYSTORE:-$PWD/.signing/localprint-dev.jks}"
rm -rf build/gen build/classes build/dex
mkdir -p build/gen build/classes build/dex build/apk
"$BUILD_TOOLS/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$BUILD_TOOLS/aapt2" link -o build/resources.apk --manifest app/src/main/AndroidManifest.xml -I "$ANDROID_JAR" --java build/gen -A app/src/main/assets --auto-add-overlay build/resources.zip
find app/src/main/java build/gen -name '*.java' -print > build/sources.txt
"$JDK_DIR/bin/javac" -encoding UTF-8 --release 8 -cp "$ANDROID_JAR" -d build/classes @build/sources.txt
"$JDK_DIR/bin/jar" cf build/classes.jar -C build/classes .
"$JDK_DIR/bin/java" -cp "$BUILD_TOOLS/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 26 --lib "$ANDROID_JAR" --output build/dex build/classes.jar
cp build/resources.apk build/localprint-unsigned.apk
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
with ZipFile('build/localprint-unsigned.apk', 'a', compression=ZIP_DEFLATED) as out:
    for dex in Path('build/dex').glob('*.dex'):
        out.write(dex, dex.name)
PY
"$BUILD_TOOLS/zipalign" -f -p 4 build/localprint-unsigned.apk build/localprint-aligned.apk
if [ ! -f "$SIGNING_STORE" ]; then
    mkdir -p "$(dirname "$SIGNING_STORE")"
    "$JDK_DIR/bin/keytool" -genkeypair -keystore "$SIGNING_STORE" -alias localprint -keyalg RSA -keysize 3072 -validity 10000 -storepass localprint-dev -keypass localprint-dev -dname 'CN=LocalPrint Development'
    chmod 600 "$SIGNING_STORE"
fi
"$JDK_DIR/bin/java" -jar "$BUILD_TOOLS/lib/apksigner.jar" sign --ks "$SIGNING_STORE" --ks-key-alias localprint --ks-pass pass:localprint-dev --key-pass pass:localprint-dev --out build/LocalPrint-0.1.0.apk build/localprint-aligned.apk
"$JDK_DIR/bin/java" -jar "$BUILD_TOOLS/lib/apksigner.jar" verify --verbose build/LocalPrint-0.1.0.apk
shasum -a 256 build/LocalPrint-0.1.0.apk > build/LocalPrint-0.1.0.apk.sha256
