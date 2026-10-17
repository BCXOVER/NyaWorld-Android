#!/usr/bin/env python3
"""Inspect only public NyaWorld updater manifest structure; never print download URLs."""
import json
import os
import sys
import urllib.request
from urllib.error import URLError, HTTPError

URL = "https://serve.nyaworld.site/client/manifest.json"

def main():
    try:
        req = urllib.request.Request(URL, headers={"User-Agent": "NyaWorld-Android-Probe/1.0"})
        with urllib.request.urlopen(req, timeout=20) as response:
            data = response.read(8*1024*1024 + 1)
        if len(data) > 8*1024*1024:
            raise ValueError("Manifest exceeds 8 MiB")
        root = json.loads(data)
    except (URLError, HTTPError, OSError, ValueError) as e:
        print("Unable to read PC manifest:", type(e).__name__, str(e), file=sys.stderr)
        return 1
    result = {"type": type(root).__name__, "bytes": len(data)}
    files = root if isinstance(root,list) else None
    if isinstance(root,dict):
        result["root_fields"] = sorted(root.keys())
        for key in ("version","revision","build","minecraftVersion","neoforgeVersion"):
            if key in root and isinstance(root[key],(str,int,float)):
                result[key] = root[key]
        for key in ("files","Files","entries"):
            if isinstance(root.get(key),list):
                files = root[key]
                result["files_field"] = key
                break
    if isinstance(files,list):
        result["files_count"] = len(files)
        for entry in files:
            if isinstance(entry,dict):
                result["sample_file_fields"] = sorted(entry.keys())
                break
    summary=json.dumps(result,ensure_ascii=False,indent=2)
    print(summary)
    path=os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path,"a",encoding="utf-8") as f:
            f.write("### PC update manifest structure\n\n```json\n"+summary+"\n```\n")
    return 0

if __name__=="__main__":
    sys.exit(main())
