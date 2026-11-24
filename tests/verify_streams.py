#!/usr/bin/env python3
"""Independent stream decoder + differential comparison against upstream SpliX."""
import re, struct, sys, zlib
from pathlib import Path
root=Path(sys.argv[1])

def decompress11(data):
    prefix=struct.unpack_from('<I',data)[0]
    offsets=struct.unpack_from('<64H',data,4)
    assert 0<prefix<=128 and min(offsets)>0
    out=bytearray(data[132:132+prefix]);p=132+prefix
    while p<len(data):
        control=data[p];p+=1
        if control&128:
            pointer=data[p];p+=1
            count=(control&127)|((pointer&192)<<1);count+=3
            distance=offsets[pointer&63]
            assert distance<=len(out)
            for _ in range(count):out.append(out[-distance])
        else:
            count=control+1;out.extend(data[p:p+count]);p+=count
    assert p==len(data)
    return bytes(out)

def unpackbits(data,terminator=False):
    out=bytearray();p=0
    while p<len(data):
        n=data[p];p+=1
        if n==128:
            if terminator:assert p==len(data);break
            continue
        if n<128:out.extend(data[p:p+n+1]);p+=n+1
        else:out.extend(data[p:p+1]*(257-n));p+=1
    assert p==len(data)
    return bytes(out)

def qpdl(path):
    data=path.read_bytes();p=data.index(b'@PJL ENTER LANGUAGE = QPDL\n')+len(b'@PJL ENTER LANGUAGE = QPDL\n')
    header=data[p:p+17];p+=17;bands={}
    while data[p]==12:
        _,number,w,h,compression,size=struct.unpack_from('>BBHHBI',data,p);p+=11
        assert compression==17 and data[p:p+4]==bytes.fromhex('efcdab09')
        block=data[p:p+size];assert sum(block[:-4])==struct.unpack('>I',block[-4:])[0]
        decoded=decompress11(block[4:-4]);assert len(decoded)==w*h//8
        bands[number]=(w,h,decoded);p+=size
    assert data[p:p+3]==b'\x01\x00\x01'
    assert data[p+3:]==b'\t\x1b%-12345X'
    return header,bands

for raw in root.glob('band-*.raw'):
    assert decompress11(raw.with_suffix('.compressed').read_bytes())==raw.read_bytes(),raw
for raw in root.glob('row-*.raw'):
    assert unpackbits(raw.with_suffix('.packed').read_bytes())==raw.read_bytes(),raw
reference=qpdl(root/'reference.qpdl');candidate=qpdl(root/'java.qpdl')
assert candidate==reference,'Java QPDL differs from upstream page headers or decoded raster bands'
for model in ['ml1510','ml1630','ml1915']:
    assert qpdl(root/f'java-{model}.qpdl')==qpdl(root/f'reference-{model}.qpdl'),model

raw=(root/'page.bin').read_bytes()
ps=(root/'java.ps').read_text('ascii');payload=ps.split('>> image\n',1)[1].split('>',1)[0]
decoded=unpackbits(bytes.fromhex(payload),terminator=True)
assert decoded==bytes(b^255 for b in raw),'PostScript image raster differs'
pcl=(root/'java.pcl').read_bytes();start=pcl.index(b'\x1b*b2M')+len(b'\x1b*b2M');rows=[]
while pcl[start:start+3]==b'\x1b*b':
    m=re.match(rb'\x1b\*b(\d+)W',pcl[start:]);assert m
    size=int(m[1]);start+=m.end();rows.append(unpackbits(pcl[start:start+size]));start+=size
assert b''.join(rows)==raw and pcl[start:]==b'\x1b*rB\x0c\x1bE','PCL raster differs'
pdf=(root/'java.pdf').read_bytes();assert b'/Count 1' in pdf
image=re.search(rb'/Subtype /Image .*?/Length (\d+) >>\nstream\n',pdf);assert image
assert zlib.decompress(pdf[image.end():image.end()+int(image[1])])==bytes(b^255 for b in raw)
xref=int(re.search(rb'startxref\n(\d+)',pdf)[1]);assert pdf[xref:xref+4]==b'xref'
lines=pdf[xref:].splitlines();count=int(lines[1].split()[1])
for i in range(1,count):
    pos=int(lines[2+i].split()[0]);assert pdf[pos:].startswith(f'{i} 0 obj\n'.encode())
ipp=(root/'ipp.bin').read_bytes();assert ipp[:8]==b'\x01\x01\x00\x02\x00\x00\x00\x01'
assert b'printer-uri' in ipp and b'application/pdf' in ipp and ipp[-1]==3
print(f'PASS: QPDL bands and headers equal upstream SpliX ({len(candidate[1])} non-empty bands)')
print('PASS: QPDL 1/2/3 and model-specific hardware margins match upstream SpliX')
print('PASS: random/repeated SPL compression; PCL, PostScript and PDF raster round-trips; PDF xrefs; IPP envelope')
