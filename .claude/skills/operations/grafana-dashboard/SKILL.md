---
name: grafana-dashboard
description: Inspect, debug and edit the Funny App Grafana Cloud dashboards (canhlabs.grafana.net) through the API — find why a panel shows no data, change a panel type or query, and keep the dashboards in sync with app log lines and metrics. Use when a Grafana dashboard or panel "doesn't work", shows "No data" or zeros, needs a new or changed panel, or when changing log levels/messages or Micrometer metrics that a dashboard may read.
metadata:
  category: encoded-preference
---

# Grafana Dashboards (Funny App)

## Facts

| What | Value |
|------|-------|
| Instance | `https://canhlabs.grafana.net` (Grafana Cloud; Alloy on the prod VM ships metrics and logs) |
| Token | `GRAFANA_SA_TOKEN` in `api/.env` — read it inside a command, never echo or print it |
| Prometheus datasource | uid `grafanacloud-prom`, app label `application="funny-app-springboot"` |
| Loki datasource | uid `grafanacloud-logs`, app selector `{service_name="funny-app"}` |
| Business Stats | uid `acxn74` — almost all panels are **Loki log-string matches** on app logs |
| Observability | uid `dLsDQIUnzb` — Prometheus `http_server_requests_*` panels + logs |

From outside, `https://canhlabs.com/api/actuator/prometheus` and `/metrics` return 403 (nginx), and SSH to prod is blocked in auto mode. So query the data through Grafana, not the app.

Helper (stdlib Python, run from repo root): `.claude/skills/operations/grafana-dashboard/resources/grafana.py` — `panels <uid>`, `get <uid> <out>`, `save <file> "<msg>"`, `query prom|loki "<expr>" [range]`, `labels prom|loki <label>`.

## Workflow: "the dashboard doesn't work"

1. **Ask which dashboard** (link/uid) if it isn't given. Other dashboards may work fine, so don't assume it is a pipeline outage.
2. `grafana.py panels <uid>`: list every panel's datasource and query.
3. Run each query with `grafana.py query`. Sort them into: error, empty/0, or has data. A 0 can be real (no events in the range). Compare with an older window (`offset 7d` in PromQL, or a Loki `query_range` per day) before you call it broken.
4. **Loki panels at 0:** grep the matched string in `api/src/main/java`. Then use `git log -S '<string>'` / `git log -G` to find when its text or **log level** changed. Prod logs at INFO, so a line moved to DEBUG disappears from Loki. Confirm the cut-off date with a daily Loki count.
5. **Prometheus panels empty:** check the label values (`labels prom application`). After a Spring Boot or Micrometer upgrade, check metric and label names too.
6. Report the cause with evidence (commit, date, counts) and offer the options before you change code or the dashboard.

## Editing a dashboard

1. **Backup first:** `grafana.py get <uid> <scratchpad>/<slug>-v<N>.backup.json`. Grafana also keeps versions (Settings → Versions).
2. Edit the JSON (panel by `id`), then `grafana.py save <file> "<what changed>"`. It saves with `overwrite: false`, so a concurrent edit fails instead of being lost.
3. Verify the new query with `grafana.py query`. Check the frame shape too, not just the numbers.
4. Tell the user the new version number and where the backup is.

Panel recipes:

- **Prometheus table per label** (e.g. requests per endpoint): use an instant query with `format: "table"`, e.g. `sort_desc(round(sum by (method, uri) (increase(http_server_requests_seconds_count{application="$app_name", uri!~"/actuator.*"}[$__range]))))`. Grafana returns **one frame per series**, with the labels on the value field. Add the transformations `labelsToFields` (mode columns) → `merge` → `organize` (hide Time, rename Value). Without them the table shows one series at a time.
- Use `increase(...[$__range])` for "count in the range". The raw counter is cumulative since app start and resets on every deploy.
- Exclude `/actuator.*` from request panels. Alloy's scrape of `/actuator/prometheus` (~1.4k/day) dwarfs real traffic.

## Log lines are a contract

Business Stats counts views, cache hits and job runs from log text. Before you change the **level or text** of a log line, search the dashboards for that string (`grafana.py panels acxn74`). Known dependencies (marked with a comment in code and pinned by tests in `VideoStreamControllerTest` / `VideoCacheImplTest`):

- `Incoming stream request for fileId:` (INFO), `Range header: …` (INFO) — `VideoStreamController`
- `[CACHE HIT]` (INFO), `[MISS]` (WARN) — `VideoCacheImpl`
- `SePay reconcile run: undelivered=…, settled=…, processed=…` (INFO, every run), `SePay check for order`, `SePay reconcile: …` / `skipped` / `could not process` (WARN) — `SepayReconcileServiceImpl`, pinned in `SepayReconcileServiceImplTest` (row "SePay Reconcile", added v6)
- Others on that board: `User join system`, `Successfully synced from Google Drive` / `Failed to sync from Google Drive`, `Total hits: …, Total misses: …`, `AUDIT | IP=… | User=… | Method=…`, `Send email to … success` / `Send email err`, `Account deactivated and anonymized`, `Rate limit exceeded for client`

History: PM-7 (`0caa8cd7`, 2026-10-24) moved the stream and cache logs to DEBUG, and the Business Stats view and cache panels were empty from 2026-10-26 until the fix `26d282db` (2026-11-06).
