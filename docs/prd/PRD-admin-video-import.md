# PRD: Admin Video Import (YouTube / Facebook)

<!--
Product Requirements Document
Filename: docs/prd/PRD-admin-video-import.md
Owner: nguyenhuuca
Handoff to: Architect (ADR-0019), Builder (/builder)
-->

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-10-10
**Related ADR:** [ADR-0019 Admin Video Import](../adr/0019-admin-video-import.md)
**Related Plan:** [plan-admin-video-import](../plans/plan-admin-video-import.md)

Admin pastes one or many YouTube / Facebook video URLs; the server downloads them with yt-dlp, uploads them into the Google Drive folder that the existing sync cron reads, and the cron brings them into the app. Imports run now or at a scheduled time; progress is visible in the admin page.

## Problem Statement

Adding a video today means: download it on a PC, upload it to Google Drive by hand, wait for the cron. For short clips (Reels, Shorts) this is slow and repetitive, and cannot be done from a phone.

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Faster content intake | Admin time per video | < 30 s (paste + submit) |
| Reliable | Imports ending `DONE` (valid public URL) | ≥ 95 % |
| Same ingest path | Imported videos visible in app after upload | ≤ 15 min (next cron) |

## User Stories (Admin)

- **US-1** Paste one URL, optionally a title, press "Tải ngay" → job starts immediately.
- **US-2** Paste many URLs (one per line, optional `URL | Tiêu đề`) → one job per line (bulk, max 20).
- **US-3** Choose "Hẹn giờ" and a date/time (Asia/Ho_Chi_Minh) → jobs start at that time.
- **US-4** See each job's status, phase (đang tải / đang upload) and percent, updating automatically.
- **US-5** Title auto-filled from the source when possible ("Lấy tiêu đề"); I can override it.
- **US-6** Cancel a pending/running job; retry a failed one; run a scheduled job now.
- **US-7** See whether a finished job is already in the app ("Đã lên app") or waiting for sync ("Chờ đồng bộ").

## Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-1 | Admin tab **Import Video** in AdminView (admin only) | Must |
| FR-2 | Submit 1–20 URLs; per-line optional title; invalid lines reported per line, valid ones still created | Must |
| FR-3 | Allowed sources: YouTube (`youtube.com`, `youtu.be`, Shorts) and Facebook (`facebook.com` video/reel/watch, `fb.watch`); https only | Must |
| FR-4 | Run now or schedule (`scheduledAt` in the future, ≤ 30 days) | Must |
| FR-5 | Download max 720p mp4, max 1 GB; upload to Drive folder as `{title}.mp4` | Must |
| FR-6 | Title: admin > source metadata > `{platform}-{id}`; sanitized, ≤ 120 chars | Must |
| FR-7 | Progress polling every 2 s while active jobs exist; status, phase, %, size, error | Must |
| FR-8 | Cancel / retry / run-now actions | Must |
| FR-9 | Duplicate active URL rejected (`DUPLICATE_ACTIVE`) | Should |
| FR-10 | "Đã lên app" indicator (video_sources has the Drive file id) | Should |
| FR-11 | Jobs survive restarts; interrupted jobs retried up to 3 attempts | Must |

## Non-Functional

| ID | Requirement |
|----|-------------|
| NFR-1 | One import at a time (configurable); streaming must stay responsive |
| NFR-2 | No shell execution; URL allowlist; yt-dlp `--ignore-config`, `--use-extractors youtube,facebook`, `--` before URL |
| NFR-3 | Timeouts: metadata 60 s, download 30 min; disk guard ≥ 2 GB free |
| NFR-4 | Errors shown as codes + short Vietnamese message, never raw process output |
| NFR-5 | All endpoints `ADMIN` only, `@AuditLog`; create endpoint rate-limited |

## Scope

**In:** YouTube + Facebook, single/bulk, now/scheduled, polling progress, cancel/retry, title auto/manual.
**Out:** TikTok/Instagram/other sites, recurring schedules, playlists/channels, choosing quality, editing/trimming, direct ingest without the cron (ADR-0019 D1).

## Risks

| Risk | Mitigation |
|------|-----------|
| Service Account cannot upload to personal Drive (`storageQuotaExceeded`) | Phase 0 PoC; OAuth refresh-token uploader fallback (ADR-0019 D2) |
| yt-dlp broken by site changes | Daily `yt-dlp -U` on host; clear `EXTRACTOR_ERROR`; retry |
| Facebook login-only videos | Optional cookies file; otherwise `FAILED (LOGIN_REQUIRED)` |
| Copyright / ToS | Owner imports only content they have rights to |

## Open Questions

| # | Question | Proposed |
|---|----------|----------|
| OQ-1 | Bulk max per submit | 20 |
| OQ-2 | Delete local temp file after upload? | Yes (cron re-downloads; D1) |
| OQ-3 | Keep job history how long? | 90 days, purge in the existing retention job |

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | | Pending |
| Engineering | nguyenhuuca | | Pending (ADR-0019 proposed) |

## Version History

| Version | Date | Change |
|---------|------|--------|
| 0.1 | 2026-10-10 | Initial draft |
