#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
JDK_DIR="${LOCALPRINT_JDK:-/opt/homebrew/opt/openjdk@17}"
mkdir -p build/test/classes build/test/fixtures
"$JDK_DIR/bin/javac" --release 8 -d build/test/classes app/src/main/java/org/localprint/core/*.java tests/CoreProbe.java
python3 - <<'PY'
from pathlib import Path
data=bytearray(620*7017)
for y in range(7017):
    for x in range(620):
        if 24<=x<590 and 300<=y<1400 and (y%73<3 or x%41==0):data[y*620+x]=0xAA if y%2 else 0xFF
for y in range(2800,3100):
    for x in range(110,300):data[y*620+x]=(x*31+y*17)&255
Path('build/test/fixtures/page.bin').write_bytes(data)
PY
"$JDK_DIR/bin/java" -cp build/test/classes CoreProbe build/test/fixtures
clang -Wno-deprecated-declarations tests/make_raster.c -lcups -o build/test/make_raster
build/test/make_raster build/test/fixtures/page.bin build/test/fixtures/input.raster
python3 tests/build_reference.py
PPD="$PWD/third_party/splix/ppd/scx3200.ppd" build/test/rastertoqpdl 1 test test 1 'PageSize=A4' build/test/fixtures/input.raster > build/test/fixtures/reference.qpdl 2> build/test/fixtures/splix.log
for model in ml1510 ml1630 ml1915; do
    PPD="$PWD/third_party/splix/ppd/$model.ppd" build/test/rastertoqpdl 1 test test 1 'PageSize=A4' build/test/fixtures/input.raster > "build/test/fixtures/reference-$model.qpdl" 2> "build/test/fixtures/$model.log"
done
python3 tests/verify_streams.py build/test/fixtures
