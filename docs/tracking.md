# Feature Tracking

Status tracker for all PRDs, ADRs, Specs, and Plans in the project.

## Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Done — implemented and merged |
| 🔄 | In Progress — currently being worked on |
| 🔍 | In Review — plan/design ready, pending implementation start |
| 📋 | Ready — plan approved, ready to implement |
| 📝 | Draft — still being scoped or planned |
| 💡 | Proposed — decision made, not yet started |
| ⛔ | Superseded — replaced by a newer document |

---

## Feature Tracker

| Feature | PRD | ADR | Spec | Plan | Presentation | Implemented | Notes |
|---------|-----|-----|------|------|--------------|-------------|-------|
| **Use RDBMS (PostgreSQL)** | — | [0001 ✅](adr/0001-use-relational-database.md) | — | — | — | ✅ | Foundation |
| **Backend Architecture** | — | [0002 ✅](adr/0002-backend-architecture.md) | — | — | — | ✅ | Spring Boot + Virtual Threads |
| **Cache Strategy** | — | [0003 ✅](adr/0003-use-cache.md) | — | — | — | ✅ | Guava LRU |
| **Frontend Structure** | — | [0004 ✅](adr/0004-frontend-structure.md) | — | — | — | ✅ | Vanilla JS → React |
| **LRU Video Cache** | — | [0005 ✅](adr/0005-use_lru_cache_video_streaming.md) | — | — | — | ✅ | Video chunk caching |
| **HLS Video Streaming** | — | [0006 💡](adr/0006-hls-video-streaming.md) | — | [Plan 📋](plans/plan-hls-migration.md) | — | 🔍 | |
| **Admin Dashboard** | — | [0007 ✅](adr/0007-admin-dashboard.md) | — | [Plan 🔄](plans/plan-admin-dashboard.md) | — | 🔄 | |
| **React Migration** | [PRD ✅](prd/PRD-react-migration.md) | [0008 ✅](adr/0008-react-migration.md) | — | [Plan](plans/plan-react-migration.md) | — | ✅ | |
| **Unified Video List API** | — | [0009 💡](adr/0009-unified-video-list-api.md) | — | — | — | 🔍 | |
| **Trivy Container Scanning** | — | [0010 ✅](adr/0010-trivy-container-scanning.md) | — | [Plan](plans/plan-trivy-scan.md) | — | ✅ | CI scanning |
| **Hot Video Priority** | [PRD ✅](prd/PRD-hot-video-priority.md) | [0011 ✅](adr/0011-hot-video-priority.md) | — | [Plan 📋](plans/plan-hot-video-priority.md) | [PRD deck 🎤](prd/PRD-hot-video-priority.html) | 🔍 | |
| **Bitwise Permissions** | — | [0015 ✅](adr/0015-bitwise-permission-system.md) | [Spec ✅](specs/spec-bitwise-permissions.md) | [Plan](plans/plan-permissions-bitwise.md) | — | ✅ | AS-BUILT docs |
| **Hot Score Migration** | — | — | — | [Plan 📋](plans/plan-vid2-hot-score-migration.md) | — | 🔍 | |
| **File-based Hooks** | — | — | — | [Plan A 📋](plans/plan-a-file-based-hooks.md) · [Plan B 📋](plans/plan-b-direct-cli-spawn.md) | — | 🔍 | |
| **Bookmark Feature** | [PRD 📝](prd/PRD-bookmark-feature.md) | [0012 ✅](adr/0012-bookmark-feature-design.md) | — | [Plan 📋](plans/plan-bookmark-feature.md) · [Library Collections ✅](plans/plan-library-collections.md) | — | 🔍 | ADR amended 2026-10-13: collections-first, no bookmarks table; admin public collections |
| **Watch History (v2)** | [PRD ✅](prd/PRD-watch-history-v2.md) | [0021 ✅](adr/0021-watch-history.md) | — | [Plan ✅](plans/plan-watch-history-v2.md) | — | ✅ | `0a2a365` BE (table, API, recording on the view call) + `4c46791` FE (History tab, progress/resume, badges); QA-1 verified on prod 2026-10-18. Supersedes ADR-0013/0014 |
| **User Settings** | [PRD ✅](prd/PRD-user-settings.md) | [0016 ✅](adr/0016-user-settings-design.md) | [Spec ✅](specs/spec-user-settings.md) | [Plan ✅](plans/plan-user-settings.md) | — | ✅ | Pushed to main `dd45fc0`; coverage 95.91% |
| **Video Reactions (like / unlike / dislike)** | — | — | — | [Plan ✅](plans/plan-video-reactions.md) | — | ✅ | `28cc167`; fixes 404 on `/video/like`; per-user reactions, optional-auth GET |
| **Admin Comment Moderation** | — | — | — | [Plan ✅](plans/plan-admin-comment-moderation.md) | — | ✅ | `acfc953` soft remove/restore with policy reasons; `6f66b15` bulk (≤100) |
| **Comment Replies** | — | — | — | [Plan ✅](plans/plan-comment-replies.md) | — | ✅ | `a23950f` 2-level threads, author delete keeps others' replies; `368bf0c` reply-to-reply notifies the replied author |
| **i18n (Vietnamese + English)** | [PRD ✅](prd/PRD-i18n.md) | [0020 ✅](adr/0020-i18n-vietnamese-english.md) | — | [Plan ✅](plans/plan-i18n.md) | — | ✅ | react-i18next, browser default; UI + API errors + emails + Admin. FE-1..5 + BE-1..3 done; QA-1 verified on prod by owner (recorded 2026-10-18) |
| **Collection Reorder (drag & drop)** | — | [0012 ✅](adr/0012-bookmark-feature-design.md) | — | [Plan ✅](plans/plan-collection-reorder.md) | — | ✅ | `position` column + `PATCH /collections/{id}/videos/{videoId}` (afterVideoId); FE "Sắp xếp" mode with @dnd-kit; new videos at the top. QA-1 verified on prod by owner (2026-10-18) |
| **Video Suggestions (+ button)** | [PRD ✅](prd/PRD-video-suggestions.md) | — | — | [Plan ✅](plans/plan-video-suggestions.md) | — | ✅ | Users suggest YouTube/FB links → admin approves → import pipeline; replaces legacy share; History → My suggestions; ~6.5d |
| **User Notifications** | [PRD ✅](prd/PRD-user-notifications.md) | [0017 ✅](adr/0017-user-notifications.md) | — | [Plan ✅](plans/plan-user-notifications.md) | — | ✅ | `a11d31e` in-app + SSE (fetch + header auth) + 15-min digest; verified on prod 2026-10-03; nginx `worker_connections` 768→8192 |
| **Password Login (optional)** | [PRD ✅](prd/PRD-password-login.md) | [0018 ✅](adr/0018-password-login-option.md) | — | [Plan ✅](plans/plan-password-login.md) | — | ✅ | `fd319cf` email + password next to magic link, lockout 5/15 min, `cv` session revocation; `e6d6a3f`/`63e21f0` modal + 🔑 icon; `3de7a4a` role in login response; verified on prod 2026-10-09 |
| **Admin Video Import (YouTube / Facebook → Drive)** | [PRD ✅](prd/PRD-admin-video-import.md) | [0019 ✅](adr/0019-admin-video-import.md) | — | [Plan ✅](plans/plan-admin-video-import.md) | — | ✅ | `795ec5e` yt-dlp → Drive (OAuth uploader) → existing cron ingests; bulk ≤20, run now / 5-min schedule slots, polling progress; `d9bddd9` FB reels; [setup guide](guides/video-import-setup-vi.md); verified on prod 2026-10-11 |
| **Drive Sync Time Filter** | — | — | — | [Plan ✅](plans/plan-drive-sync-time-filter.md) | — | ✅ | `cc7f8f2b` BE + `ad0adc82`/`31f01e86` FE. DRIVE_SYNC tab: server-side filter on Drive created time (`GET /files?from&to`), default last 12 h, presets 1h/12h/24h/7d/30d, All, custom from/to (VN time); orphans only under All. QA-1 verified on prod by owner (2026-10-18) |
| **Admin Drive Force Sync** | — | — | — | [Plan 🔄](plans/plan-admin-drive-force-sync.md) | — | 🔄 | `1e961cc` tab DRIVE_SYNC: Drive ↔ local ↔ DB states; ADMIN views, SUPER_ADMIN force-syncs (re-download + upsert, ≤50/run); unique `(source_type, source_id)`; prod E2E pending |
| **/autopilot skill (dev tooling)** | — | — | — | [Plan ✅](plans/plan-autopilot.md) | — | 🔄 | Built 2026-10-19 (`f8fa31ce`, `d52f6b2e`); QA-1 dry run pending. One skill chains plan → plan review → [you approve] → implement ⇄ review → QA tests ×2 → commit → [you approve push]; round caps 3, run ledger |

---

## Status Summary

### ✅ Done (18)
- [Use RDBMS](adr/0001-use-relational-database.md)
- [Backend Architecture](adr/0002-backend-architecture.md)
- [Cache Strategy](adr/0003-use-cache.md)
- [Frontend Structure](adr/0004-frontend-structure.md)
- [LRU Video Cache](adr/0005-use_lru_cache_video_streaming.md)
- [Trivy Container Scanning](adr/0010-trivy-container-scanning.md)
- [Bitwise Permissions](adr/0015-bitwise-permission-system.md)
- [React Migration](prd/PRD-react-migration.md)
- [User Settings](adr/0016-user-settings-design.md)
- [Video Reactions](plans/plan-video-reactions.md)
- [Admin Comment Moderation](plans/plan-admin-comment-moderation.md)
- [Comment Replies](plans/plan-comment-replies.md)
- [User Notifications](adr/0017-user-notifications.md)
- [Password Login](adr/0018-password-login-option.md)
- [Admin Video Import](adr/0019-admin-video-import.md)
- [Watch History (v2)](adr/0021-watch-history.md)
- [i18n Vietnamese + English](plans/plan-i18n.md)
- [Drive Sync Time Filter](plans/plan-drive-sync-time-filter.md)

### 🔄 In Progress (3)
- [/autopilot skill](plans/plan-autopilot.md)
- [Admin Dashboard](plans/plan-admin-dashboard.md)
- [Admin Drive Force Sync](plans/plan-admin-drive-force-sync.md)

### 🔍 In Review (7)
- [Video Suggestions](plans/plan-video-suggestions.md)
- [HLS Video Streaming](plans/plan-hls-migration.md)
- [Unified Video List API](adr/0009-unified-video-list-api.md)
- [Hot Video Priority](plans/plan-hot-video-priority.md)
- [Hot Score Migration](plans/plan-vid2-hot-score-migration.md)
- [File-based Hooks](plans/plan-a-file-based-hooks.md)
- [Bookmark Feature](plans/plan-bookmark-feature.md)

### 💡 Proposed / No Plan Yet (0)
- —

---

## ADR Status Summary

| # | ADR | Status |
|---|-----|--------|
| 0001 | Use Relational Database | ✅ Accepted |
| 0002 | Backend Architecture | ✅ Accepted |
| 0003 | Cache Strategy | ✅ Accepted |
| 0004 | Frontend Structure | ✅ Accepted |
| 0005 | LRU Video Cache | ✅ Accepted |
| 0006 | HLS Video Streaming | 💡 Proposed |
| 0007 | Admin Dashboard | ✅ Accepted |
| 0008 | React Migration | ✅ Accepted |
| 0009 | Unified Video List API | 💡 Proposed |
| 0010 | Trivy Container Scanning | ✅ Accepted |
| 0011 | Hot Video Priority | ✅ Accepted |
| 0012 | Bookmark Feature Design | ✅ Accepted (amended 2026-10-13) |
| 0013 | Watch History Design (baseline) | ⛔ Superseded by 0021 |
| 0014 | Watch History Design (Opus) | ⛔ Superseded by 0021 |
| 0015 | Bitwise Permission System | ✅ Accepted |
| 0016 | User Settings Design | ✅ Accepted |
| 0017 | User Notifications | ✅ Accepted |
| 0018 | Password Login Option | ✅ Accepted |
| 0019 | Admin Video Import | ✅ Accepted |
| 0020 | i18n Vietnamese + English | ✅ Accepted |
| 0021 | Watch History | ✅ Accepted |

---

*Last updated: 2026-10-19 — /autopilot skill built (dry run pending); ADR-0008 and ADR-0012 marked Accepted; Admin Drive force sync implemented (prod E2E pending); password login (ADR-0018) and admin video import (ADR-0019) verified on prod*
