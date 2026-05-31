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

| Feature | PRD | ADR | Spec | Plan | Implemented | Notes |
|---------|-----|-----|------|------|-------------|-------|
| **Use RDBMS (PostgreSQL)** | — | [0001 ✅](adr/0001-use-relational-database.md) | — | — | ✅ | Foundation |
| **Backend Architecture** | — | [0002 ✅](adr/0002-backend-architecture.md) | — | — | ✅ | Spring Boot + Virtual Threads |
| **Cache Strategy** | — | [0003 ✅](adr/0003-use-cache.md) | — | — | ✅ | Guava LRU |
| **Frontend Structure** | — | [0004 ✅](adr/0004-frontend-structure.md) | — | — | ✅ | Vanilla JS → React |
| **LRU Video Cache** | — | [0005 ✅](adr/0005-use_lru_cache_video_streaming.md) | — | — | ✅ | Video chunk caching |
| **HLS Video Streaming** | — | [0006 💡](adr/0006-hls-video-streaming.md) | — | [Plan 📋](plans/plan-hls-migration.md) | 🔍 | |
| **Admin Dashboard** | — | [0007 ✅](adr/0007-admin-dashboard.md) | — | [Plan 🔄](plans/plan-admin-dashboard.md) | 🔄 | |
| **React Migration** | [PRD ✅](prd/PRD-react-migration.md) | [0008 ✅](adr/0008-react-migration.md) | — | [Plan](plans/plan-react-migration.md) | ✅ | |
| **Unified Video List API** | — | [0009 💡](adr/0009-unified-video-list-api.md) | — | — | 🔍 | |
| **Trivy Container Scanning** | — | [0010 ✅](adr/0010-trivy-container-scanning.md) | — | [Plan](plans/plan-trivy-scan.md) | ✅ | CI scanning |
| **Hot Video Priority** | [PRD ✅](prd/PRD-hot-video-priority.md) | [0011 ✅](adr/0011-hot-video-priority.md) | — | [Plan 📋](plans/plan-hot-video-priority.md) | 🔍 | |
| **Bitwise Permissions** | — | — | — | [Plan](plans/plan-permissions-bitwise.md) | ✅ | |
| **Hot Score Migration** | — | — | — | [Plan 📋](plans/plan-vid2-hot-score-migration.md) | 🔍 | |
| **File-based Hooks** | — | — | — | [Plan A 📋](plans/plan-a-file-based-hooks.md) · [Plan B 📋](plans/plan-b-direct-cli-spawn.md) | 🔍 | |
| **Bookmark Feature** | [PRD 📝](prd/PRD-bookmark-feature.md) | [0012 💡](adr/0012-bookmark-feature-design.md) | — | [Plan 📋](plans/plan-bookmark-feature.md) | 🔍 | |
| **Watch History** | [PRD 📝](prd/PRD-watch-history.md) | [0013 💡](adr/0013-watch-history-design.md) | [Spec 📋](specs/spec-watch-history.md) | [Plan 📋](plans/plan-watch-history.md) | 🔍 | |

---

## Status Summary

### ✅ Done (8)
- [Use RDBMS](adr/0001-use-relational-database.md)
- [Backend Architecture](adr/0002-backend-architecture.md)
- [Cache Strategy](adr/0003-use-cache.md)
- [Frontend Structure](adr/0004-frontend-structure.md)
- [LRU Video Cache](adr/0005-use_lru_cache_video_streaming.md)
- [Trivy Container Scanning](adr/0010-trivy-container-scanning.md)
- [Bitwise Permissions](plans/plan-permissions-bitwise.md)
- [React Migration](prd/PRD-react-migration.md)

### 🔄 In Progress (1)
- [Admin Dashboard](plans/plan-admin-dashboard.md)

### 🔍 In Review (7)
- [HLS Video Streaming](plans/plan-hls-migration.md)
- [Unified Video List API](adr/0009-unified-video-list-api.md)
- [Hot Video Priority](plans/plan-hot-video-priority.md)
- [Hot Score Migration](plans/plan-vid2-hot-score-migration.md)
- [File-based Hooks](plans/plan-a-file-based-hooks.md)
- [Bookmark Feature](plans/plan-bookmark-feature.md)
- [Watch History](specs/spec-watch-history.md)

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

---

*Last updated: 2026-05-31*
