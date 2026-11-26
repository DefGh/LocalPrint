#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
JDK_DIR="${LOCALPRINT_JDK:-/opt/homebrew/opt/openjdk@17}"
BT="$PWD/.toolchain/android-16"
AJ="$PWD/.toolchain/android-36/android.jar"
mkdir -p build/instrumentation/classes build/instrumentation/dex
"$BT/aapt2" link -o build/instrumentation/resources.apk --manifest tests/AndroidManifest.xml -I "$AJ"
"$JDK_DIR/bin/javac" --release 8 -encoding UTF-8 -cp "$AJ:build/classes" -d build/instrumentation/classes tests/AndroidProbe.java tests/UiSnapshotProbe.java tests/DiscoveryProbe.java tests/SessionProbe.java
"$JDK_DIR/bin/jar" cf build/instrumentation/classes.jar -C build/instrumentation/classes .
"$JDK_DIR/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --min-api 26 --lib "$AJ" --classpath build/classes.jar --output build/instrumentation/dex build/instrumentation/classes.jar
cp build/instrumentation/resources.apk build/instrumentation/test-unsigned.apk
python3 - <<'PY'
from zipfile import ZipFile
with ZipFile('build/instrumentation/test-unsigned.apk','a') as z:z.write('build/instrumentation/dex/classes.dex','classes.dex')
PY
"$BT/zipalign" -f 4 build/instrumentation/test-unsigned.apk build/instrumentation/test-aligned.apk
"$JDK_DIR/bin/java" -jar "$BT/lib/apksigner.jar" sign --ks .signing/localprint-dev.jks --ks-pass pass:localprint-dev --out build/instrumentation/LocalPrint-tests.apk build/instrumentation/test-aligned.apk
