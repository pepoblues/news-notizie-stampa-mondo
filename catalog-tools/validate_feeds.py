#!/usr/bin/env python3
import json,sys,time,urllib.request,xml.etree.ElementTree as ET
p=sys.argv[1] if len(sys.argv)>1 else "app/src/main/assets/feeds.json"
data=json.load(open(p,encoding="utf-8")); report=[]
for f in data:
    try:
        req=urllib.request.Request(f["feedUrl"],headers={"User-Agent":"MondoFeed-validator/0.1"})
        with urllib.request.urlopen(req,timeout=15) as r: raw=r.read(2_000_000); code=r.status; final=r.url
        root=ET.fromstring(raw); ok=root.tag.lower().endswith(("rss","feed","rdf"))
        report.append({"id":f["id"],"ok":ok,"http":code,"finalUrl":final,"error":None})
    except Exception as e: report.append({"id":f["id"],"ok":False,"error":str(e)})
    time.sleep(.25)
json.dump(report,open("feed-health-report.json","w",encoding="utf-8"),ensure_ascii=False,indent=2)
print(sum(x["ok"] for x in report),"validi su",len(report))
