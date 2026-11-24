#!/usr/bin/env python3
from pathlib import Path
import subprocess

root=Path(__file__).resolve().parents[1]
source=root/'third_party/splix'
out=root/'build/test/rastertoqpdl'
files=sorted(p for p in (source/'src').glob('*.cpp') if p.name!='pstoqpdl.cpp')
subprocess.run(['clang++','-std=gnu++11','-O2','-DDISABLE_JBIG','-DDISABLE_THREADS',
                '-Wno-deprecated-declarations','-I'+str(source/'include'),
                *map(str,files),'-lcups','-o',str(out)],check=True)
