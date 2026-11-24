#!/usr/bin/env python3
"""Download the exact official Google build packages; no global SDK changes."""
from pathlib import Path
import concurrent.futures, hashlib, urllib.request, zipfile

ROOT=Path(__file__).resolve().parents[1]/'.toolchain'
PACKAGES=[
    ('platform-36_r02.zip','2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576'),
    ('build-tools_r36_macosx.zip','199ae0047ee61e842f8ee0c6d3918e44fb9a1f83'),
]
def install(item):
    name,expected=item;archive=ROOT/name
    if not archive.exists():urllib.request.urlretrieve('https://dl.google.com/android/repository/'+name,archive)
    h=hashlib.sha1()
    with archive.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
    if h.hexdigest()!=expected:raise RuntimeError('Google archive checksum mismatch: '+name)
    with zipfile.ZipFile(archive) as z:
        for info in z.infolist():
            z.extract(info,ROOT)
            mode=(info.external_attr>>16)&0o777
            if mode:(ROOT/info.filename).chmod(mode)
    print(name,'verified')
ROOT.mkdir(exist_ok=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:list(pool.map(install,PACKAGES))
