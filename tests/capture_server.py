#!/usr/bin/env python3
"""Disposable RAW/IPP test peers; only loopback, never forwards to real hardware."""
import http.server, socketserver, threading, time
from pathlib import Path

OUT=Path(__file__).resolve().parents[1]/'build/android-captures'
OUT.mkdir(parents=True,exist_ok=True)
lock=threading.Lock();counter=0
def save(data,prefix):
    global counter
    with lock:
        counter+=1;p=OUT/f'{counter:02d}-{prefix}.bin';p.write_bytes(data)
        print(p.name,len(data),flush=True)
class Raw(socketserver.BaseRequestHandler):
    def handle(self):
        data=bytearray();self.request.settimeout(180)
        while chunk:=self.request.recv(65536):
            data.extend(chunk)
            if len(data)>256*1024*1024:raise ValueError('Too big')
        save(data,'raw')
class IPP(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        length=int(self.headers['Content-Length']);body=self.rfile.read(length)
        assert body[:8]==b'\x01\x01\x00\x02\x00\x00\x00\x01'
        assert b'application/pdf' in body and b'%PDF-' in body
        save(body,'ipp')
        self.send_response(200);self.send_header('Content-Type','application/ipp');self.end_headers();self.wfile.write(bytes.fromhex('010100000000000103'))
    def log_message(self,*args):pass
raw=socketserver.ThreadingTCPServer(('127.0.0.1',19100),Raw)
ipp=http.server.ThreadingHTTPServer(('127.0.0.1',19631),IPP)
for server in [raw,ipp]:threading.Thread(target=server.serve_forever,daemon=True).start()
print('RAW 19100 / IPP 19631 ready on loopback',flush=True)
try:
    while True:time.sleep(1)
finally:raw.shutdown();ipp.shutdown()
