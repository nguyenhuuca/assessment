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
| **Bookmark Feature** | [PRD 📝](prd/PRD-bookmark-feature.md) | [0012 💡](adr/0012-bookmark-feature-design.md) | — | [Plan 📋](plans/plan-bookmark-feature.md) | — | 🔍 | |
| **Watch History (Opus)** | [PRD 📝](prd/PRD-watch-history-opus.md) | [0014 💡](adr/0014-watch-history-design-opus.md) | [Spec 📋](specs/spec-watch-history-opus.md) | [Plan 📝](plans/plan-watch-history-opus.md) | — | 🔍 | Active branch · [comparison](comparison-watch-history-models.md) |
| **Watch History (baseline)** | [PRD 📝](prd/PRD-watch-history.md) | [0013 💡](adr/0013-watch-history-design.md) | [Spec 📋](specs/spec-watch-history.md) | [Plan 📋](plans/plan-watch-history.md) | [Spec deck 🎤](specs/spec-watch-history.html) | 🔍 | 🗄️ Archived — model-comparison baseline |
| **User Settings** | [PRD ✅](prd/PRD-user-settings.md) | [0016 ✅](adr/0016-user-settings-design.md) | [Spec ✅](specs/spec-user-settings.md) | [Plan ✅](plans/plan-user-settings.md) | — | ✅ | Pushed to main `dd45fc0`; coverage 95.91% |

---

## Status Summary

### ✅ Done (9)
- [Use RDBMS](adr/0001-use-relational-database.md)
- [Backend Architecture](adr/0002-backend-architecture.md)
- [Cache Strategy](adr/0003-use-cache.md)
- [Frontend Structure](adr/0004-frontend-structure.md)
- [LRU Video Cache](adr/0005-use_lru_cache_video_streaming.md)
- [Trivy Container Scanning](adr/0010-trivy-container-scanning.md)
- [Bitwise Permissions](adr/0015-bitwise-permission-system.md)
- [React Migration](prd/PRD-react-migration.md)
- [User Settings](adr/0016-user-settings-design.md)

### 🔄 In Progress (1)
- [Admin Dashboard](plans/plan-admin-dashboard.md)

### 🔍 In Review (7)
- [HLS Video Streaming](plans/plan-hls-migration.md)
- [Unified Video List API](adr/0009-unified-video-list-api.md)
- [Hot Video Priority](plans/plan-hot-video-priority.md)
- [Hot Score Migration](plans/plan-vid2-hot-score-migration.md)
- [File-based Hooks](plans/plan-a-file-based-hooks.md)
- [Bookmark Feature](plans/plan-bookmark-feature.md)
- [Watch History (Opus)](plans/plan-watch-history-opus.md) · baseline archived

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
| 0008 | React Migration | 💡 Proposed |
| 0009 | Unified Video List API | 💡 Proposed |
| 0010 | Trivy Container Scanning | ✅ Accepted |
| 0011 | Hot Video Priority | ✅ Accepted |
| 0012 | Bookmark Feature Design | 💡 Proposed |
| 0013 | Watch History Design (baseline, archived) | 💡 Proposed |
| 0014 | Watch History Design (Opus) | 💡 Proposed |
| 0015 | Bitwise Permission System | ✅ Accepted |
| 0016 | User Settings Design | ✅ Accepted |

---

*Last updated: 2026-06-20 — User Settings feature implemented and merged to main (dd45fc0)*
