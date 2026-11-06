#!/usr/bin/env python3
"""Grafana Cloud helper for the Funny App dashboards (stdlib only).

The token is read from GRAFANA_SA_TOKEN in api/.env and is never printed.

  python grafana.py panels  <dash_uid>                  list panels + queries
  python grafana.py get     <dash_uid> <out.json>       save full dashboard JSON (backup)
  python grafana.py save    <in.json> "<message>"       save edited JSON (overwrite=false)
  python grafana.py query   prom|loki "<expr>" [range]  run an instant query, default range 24h
  python grafana.py labels  prom|loki <label>           list values of a label

Run from the repo root.
"""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://canhlabs.grafana.net"
DS = {"prom": {"type": "prometheus", "uid": "grafanacloud-prom"},
      "loki": {"type": "loki", "uid": "grafanacloud-logs"}}


def token():
    with open(os.path.join("api", ".env"), encoding="utf-8") as f:
        for line in f:
            if line.startswith("GRAFANA_SA_TOKEN="):
                return line.split("=", 1)[1].strip().strip('"').strip("'")
    sys.exit("GRAFANA_SA_TOKEN missing in api/.env")


def call(path, body=None):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Authorization": "Bearer " + token(), "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=60) as f:
            return json.load(f)
    except urllib.error.HTTPError as e:
        sys.exit(f"HTTP {e.code}: {e.read().decode()[:500]}")


def walk(panels):
    for p in panels:
        yield p
        yield from walk(p.get("panels", []))


def panels(uid):
    d = call(f"/api/dashboards/uid/{uid}")
    db = d["dashboard"]
    print(db["title"], "version", db["version"], "folder", d["meta"].get("folderUid"))
    for v in db.get("templating", {}).get("list", []):
        print("var", v["name"], v.get("query"))
    for p in walk(db["panels"]):
        print(f"## {p.get('id')} {p.get('type')} {p.get('title')!r} {p.get('gridPos')}")
        for t in p.get("targets", []):
            print("   ", t.get("refId"), t.get("expr") or t.get("rawSql") or "")


def get(uid, out):
    with open(out, "w", encoding="utf-8") as f:
        json.dump(call(f"/api/dashboards/uid/{uid}"), f, indent=1, ensure_ascii=False)
    print("saved", out)


def save(path, message):
    with open(path, encoding="utf-8") as f:
        d = json.load(f)
    body = {"dashboard": d["dashboard"], "folderUid": d["meta"].get("folderUid"),
            "overwrite": False, "message": message}
    r = call("/api/dashboards/db", body)
    print(r["status"], "version", r["version"], BASE + r["url"])


def query(kind, expr, rng="24h"):
    expr = expr.replace("$app_name", "funny-app-springboot").replace("$__range", rng).replace("$__interval", "15m")
    q = {"refId": "A", "datasource": DS[kind], "expr": expr, "instant": True, "queryType": "instant", "maxLines": 20}
    res = call("/api/ds/query", {"from": f"now-{rng}", "to": "now", "queries": [q]})["results"]["A"]
    if res.get("error"):
        sys.exit("ERROR " + res["error"])
    frames = res.get("frames", [])
    print(len(frames), "frame(s)")
    for fr in frames[:30]:
        fields = fr["schema"]["fields"]
        labels = next((f.get("labels") for f in fields if f.get("labels")), {})
        last = fr["data"]["values"][-1] if fr["data"]["values"] else []
        print(" ", labels, last[:5])


def labels(kind, label):
    path = ("/api/datasources/proxy/uid/grafanacloud-prom/api/v1/label/{}/values" if kind == "prom"
            else "/api/datasources/proxy/uid/grafanacloud-logs/loki/api/v1/label/{}/values")
    print(call(path.format(urllib.parse.quote(label))).get("data"))


if __name__ == "__main__":
    cmd, *args = sys.argv[1:] or ["help"]
    fn = {"panels": panels, "get": get, "save": save, "query": query, "labels": labels}.get(cmd)
    if not fn:
        sys.exit(__doc__)
    fn(*args)
