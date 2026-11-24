#!/usr/bin/env python3
"""Temporary LAN delivery of the build artifacts only; unrelated to printing."""
import argparse,http.server,threading,urllib.parse
from pathlib import Path

p=argparse.ArgumentParser();p.add_argument('--bind',required=True);p.add_argument('--port',type=int,default=8765);p.add_argument('--seconds',type=int,default=7200);args=p.parse_args()
dist=Path(__file__).resolve().parents[1]/'dist'
allowed={'LocalPrint-0.1.0.apk','LocalPrint-0.1.0-source.zip','INSTALL-RU.txt','COPYING.txt','LocalPrint-0.1.0.apk.sha256','LocalPrint-0.1.0-source.zip.sha256'}
class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self,*a,**kw):super().__init__(*a,directory=str(dist),**kw)
    def send_head(self):
        name=urllib.parse.unquote(urllib.parse.urlsplit(self.path).path).lstrip('/')
        if name not in allowed:self.send_error(404);return None
        return super().send_head()
    def guess_type(self,path):return 'application/vnd.android.package-archive' if str(path).endswith('.apk') else super().guess_type(path)
    def end_headers(self):self.send_header('Cache-Control','no-store');super().end_headers()
server=http.server.ThreadingHTTPServer((args.bind,args.port),Handler)
timer=threading.Timer(args.seconds,server.shutdown);timer.daemon=True;timer.start()
print(f'Download: http://{args.bind}:{args.port}/LocalPrint-0.1.0.apk (temporary LAN server)',flush=True)
try:server.serve_forever()
finally:timer.cancel();server.server_close()
