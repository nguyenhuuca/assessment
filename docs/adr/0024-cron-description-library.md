# ADR-0024: cronstrue for Human-Readable Cron Text

## Metadata

**Status:** Accepted (2026-10-19) · **Date:** 2026-10-19 · **Deciders:** nguyenhuuca · **Tags:** frontend, dependency, scheduling
**Related:** [ADR-0023](0023-dynamic-cron-jobs.md)

## Context

The Admin → Scheduled jobs tab (ADR-0023) needs two things:
- a tooltip on each cron;
- a crontab.guru-style editor that says in words what a cron means ("Every 15 minutes", "Mỗi 15 phút").

Writing a cron-to-text describer by hand means:
- covering every Spring cron form (lists, ranges, steps, `MON-FRI`, `L`…);
- translating every phrase into en and vi.

## Decision

- **Library:** use [`cronstrue`](https://www.npmjs.com/package/cronstrue) in `webapp/` (MIT, zero dependencies).
  - It understands 6-field crons with seconds.
  - It ships `vi` and `en` locales.
  - Only `cronstrue` + `cronstrue/locales/vi` are imported, not the all-locales `cronstrue/i18n` bundle.
- **Version:** pinned exactly (`3.29.0`), the newest release that was at least two weeks old when it was added. 3.33.0 had been published the same day.
- **Text only:** `cronstrue` gives the description, nothing else.
- **Validation and next runs stay on the server:**
  - `GET /admin/jobs/preview` uses Spring `CronExpression` and the same rules as PATCH, so there is one source of truth for "is this cron valid and when does it fire".
  - The FE only pre-checks the shape (6 fields, seconds = one number) to avoid useless requests.

## Options Considered

| Option | Verdict |
|--------|---------|
| **cronstrue (text) + server preview (validation, next runs)** | **Chosen** |
| Hand-written describer (BE or FE) | Rejected: weaker text for complex crons, two translations to maintain |
| `cron-parser` in the FE for next runs | Rejected: a second library whose semantics may differ from Spring (e.g. day-of-week 0/7) |

## Consequences

- `webapp` gains one small runtime dependency (~25 KB min). It appears in `.claude/rules/tech-strategy.md`.
- If `cronstrue` ever mis-describes a cron, the impact is cosmetic only. The next-run times come from the server.
