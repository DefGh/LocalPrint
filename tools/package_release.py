#!/usr/bin/env python3
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
import shutil,hashlib

root=Path(__file__).resolve().parents[1];dist=root/'dist';dist.mkdir(exist_ok=True)
apk=dist/'LocalPrint-0.1.1.apk';shutil.copy2(root/'build'/apk.name,apk)
archive=dist/'LocalPrint-0.1.1-source.zip'
excluded={'.toolchain','.signing','build','dist','.git','__pycache__'}
with ZipFile(archive,'w',ZIP_DEFLATED) as out:
    for p in sorted(root.rglob('*')):
        rel=p.relative_to(root)
        if p.is_file() and not any(x in excluded for x in rel.parts) and p.suffix!='.pyc' and p.name!='.DS_Store':
            out.write(p,'localprint/'+str(rel))
for p in [apk,archive]:
    digest=hashlib.sha256(p.read_bytes()).hexdigest();p.with_suffix(p.suffix+'.sha256').write_text(digest+'  '+p.name+'\n')
    print(p.name,p.stat().st_size,'bytes',digest)
shutil.copy2(root/'COPYING',dist/'COPYING.txt')
instructions='''LocalPrint 0.1.1 — установка

1. Телефон и принтер должны быть в одной доступной друг другу локальной сети.
2. Установи LocalPrint-0.1.1.apk и открой приложение.
3. Нажми «Открыть настройки печати» и включи службу LocalPrint.
4. Нажми «Найти автоматически», выбери устройство из списка и проверь драйвер.
   Если устройство не объявляется в сети, нажми «Найти вручную» и укажи адрес.
5. Нажми «Сохранить». Поиск, выбор и сохранение ничего не печатают.
6. Открой PDF или другое приложение → «Печать» → выбери сохранённый принтер.
7. Если принтер не виден: открой системный список принтеров, затем LocalPrint
   → «Журнал» → «Обновить». «Скопировать диагностику» копирует события службы.

После установки компьютер для печати не нужен. Это тестовая версия:
системная служба и драйвер проверены на Android-эмуляторе и сравнены с SpliX;
на настоящих телефонах и принтерах ещё нужны испытания.
Первая версия печатает чёрно-белым, с одной стороны.

Архив LocalPrint-0.1.1-source.zip содержит полные исходники под GPL-2.0.
Принтеры и адреса в приложении не заданы заранее: можно добавлять разные модели.
Другие модели: вкладка «Драйверы». Скачиваются совместимые модельные
профили OpenPrinting, а новые механизмы печати добавляются обновлением APK.
'''
(dist/'INSTALL-RU.txt').write_text(instructions)
