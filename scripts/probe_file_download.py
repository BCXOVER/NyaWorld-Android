#!/usr/bin/env python3
"""Check one small publicly downloadable asset from the PC manifest.

No file content, account data, tokens, or full paths are logged.
"""
import hashlib
import json
import urllib.request
from urllib.parse import quote

BASE = "https://serve.nyaworld.site/client/"
req = urllib.request.Request(BASE + "manifest.json", headers={"User-Agent": "NyaWorld-Android/0.1"})
with urllib.request.urlopen(req, timeout=20) as response:
    manifest = json.load(response)
candidates = [x for x in manifest["files"] if
              isinstance(x, dict) and isinstance(x.get("path"), str)
              and x["path"].startswith(("config/","defaultconfigs/"))
              and 200 <= int(x.get("size",0)) <= 50000
              and len(x.get("sha256","")) == 64]
if not candidates:
    raise SystemExit("No safe small file in public manifest")
target = min(candidates,key=lambda x: int(x["size"]))
url = BASE + "/".join(quote(p,safe="") for p in target["path"].split("/"))
req = urllib.request.Request(url,headers={"User-Agent":"NyaWorld-Android/0.1"})
with urllib.request.urlopen(req,timeout=25) as res:
    payload=res.read(50001)
digest=hashlib.sha256(payload).hexdigest()
ok=digest.lower()==target["sha256"].lower() and len(payload)==int(target["size"])
print("Download of small pack file:", "PASS" if ok else "FAIL")
print("File bytes:",len(payload),"Expected:",target["size"])
print("Pack category:",target["path"].split("/",1)[0])
if not ok:
    raise SystemExit("File URL is accessible but checksum/size mismatch")
