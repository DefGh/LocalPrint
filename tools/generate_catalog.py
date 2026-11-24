#!/usr/bin/env python3
"""Build a hash-pinned, data-only catalogue from a local OpenPrinting checkout."""
import hashlib, json, re, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'third_party/splix'
COMMIT = (SOURCE / 'REVISION').read_text().strip()

def value(text, key):
    match = re.search(r'^\*' + re.escape(key) + r':\s*"?([^"\r\n]*)', text, re.M)
    return match[1].strip() if match else ''

profiles = [dict(id='generic-pcl5', name='Generic PCL 5 Mono', engine='pcl5'),
            dict(id='generic-ps', name='Generic PostScript Level 2', engine='ps'),
            dict(id='generic-pdf', name='Generic PDF Direct', engine='pdf')]
catalog = []
for path in sorted((SOURCE / 'ppd').glob('*.ppd')):
    data = path.read_bytes()
    text = data.decode('utf-8', errors='replace')
    if (value(text, 'LanguageVersion') != 'English' or value(text, 'ColorDevice') != 'False'
            or '/cupsCompression 17' not in text or '*QPDL SpecialBandWidth' in text
            or value(text, 'QPDL QPDLVersion') not in ('1', '2', '3')
            or value(text, 'QPDL BandSize') != '128'):
        continue
    name = value(text, 'ModelName')
    header = [int(x, 16) for x in re.findall(r'<([0-9a-fA-F]{1,2})>', value(text, 'General DocHeaderValues'))]
    if len(header) != 3 or not re.fullmatch(r'[a-z0-9]+\.ppd', path.name):
        continue
    area = re.search(r'^\*ImageableArea A4(?:/[^:]*)?:\s*"([0-9.]+)\s+([0-9.]+)', text, re.M)
    if not area:
        continue
    profile = dict(id='spl-' + re.sub('[^a-z0-9]+', '-', name.lower()), name=name, engine='spl',
                   qpdl=int(value(text, 'QPDL QPDLVersion')), header1=header[0], header2=header[1], header3=header[2],
                   fixedWidth=value(text, 'QPDL FixedBandWidth') not in ('', '0'), source='OpenPrinting / SpliX',
                   marginX=float(area[1]), marginY=float(area[2]))
    profiles.append(profile)
    catalog.append(dict(name=name, id=profile['id'], sha256=hashlib.sha256(data).hexdigest(),
                        url=f'https://raw.githubusercontent.com/OpenPrinting/splix/{COMMIT}/ppd/{path.name}'))

profiles.sort(key=lambda p: (p['engine'] != 'spl', p['name']))
assets = ROOT / 'app/src/main/assets'
for name, content in [('profiles.json', profiles), ('catalog.json', catalog)]:
    (assets / name).write_text(json.dumps(content, ensure_ascii=False, indent=2) + '\n')
print(f'{len(profiles)} bundled profiles, {len(catalog)} pinned OpenPrinting downloads; {COMMIT}')
