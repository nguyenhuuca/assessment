# Feature Tracking

Bảng theo dõi trạng thái của tất cả PRD, ADR, và Plan trong dự án.

## Legend

| Ký hiệu | Ý nghĩa |
|---------|---------|
| ✅ | Done — đã hoàn thành |
| 🔄 | In Progress — đang thực hiện |
| 📋 | Ready — đã có plan, sẵn sàng implement |
| 📝 | Draft — đang scoping/planning |
| 💡 | Proposed — quyết định đã có, chưa bắt đầu |
| — | Không áp dụng |

---

## Feature Tracker

| Feature | PRD | ADR | Plan | Implement | Ghi chú |
|---------|-----|-----|------|-----------|---------|
| **Use RDBMS (PostgreSQL)** | — | [0001 ✅](adr/0001-use-relational-database.md) | — | ✅ | Nền tảng ban đầu |
| **Backend Architecture** | — | [0002 ✅](adr/0002-backend-architecture.md) | — | ✅ | Spring Boot + Virtual Threads |
| **Cache Strategy** | — | [0003 ✅](adr/0003-use-cache.md) | — | ✅ | Guava LRU |
| **Frontend Structure** | — | [0004 ✅](adr/0004-frontend-structure.md) | — | ✅ | Vanilla JS → React |
| **LRU Video Cache** | — | [0005 ✅](adr/0005-use_lru_cache_video_streaming.md) | — | ✅ | Video chunk caching |
| **HLS Video Streaming** | — | [0006 💡](adr/0006-hls-video-streaming.md) | [Plan 📋](plans/plan-hls-migration.md) | — | Chờ implement |
| **Admin Dashboard** | — | [0007 ✅](adr/0007-admin-dashboard.md) | [Plan](plans/plan-admin-dashboard.md) | ✅ | |
| **React Migration** | [PRD 🔄](prd/PRD-react-migration.md) | [0008 💡](adr/0008-react-migration.md) | [Plan 📋](plans/plan-react-migration.md) | 🔄 | React đã dùng, đang hoàn thiện |
| **Unified Video List API** | — | [0009 💡](adr/0009-unified-video-list-api.md) | — | — | |
| **Trivy Container Scanning** | — | [0010 ✅](adr/0010-trivy-container-scanning.md) | [Plan](plans/plan-trivy-scan.md) | ✅ | CI scanning |
| **Hot Video Priority** | [PRD ✅](prd/PRD-hot-video-priority.md) | [0011 ✅](adr/0011-hot-video-priority.md) | [Plan](plans/plan-hot-video-priority.md) | ✅ | Hot score algorithm |
| **Bitwise Permissions** | — | — | [Plan](plans/plan-permissions-bitwise.md) | ✅ | |
| **Hot Score Migration** | — | — | [Plan](plans/plan-vid2-hot-score-migration.md) | ✅ | |
| **File-based Hooks** | — | — | [Plan A](plans/plan-a-file-based-hooks.md) · [Plan B](plans/plan-b-direct-cli-spawn.md) | ✅ | |
| **Bookmark Feature** | [PRD 📝](prd/PRD-bookmark-feature.md) | [0012 💡](adr/0012-bookmark-feature-design.md) | [Plan 📋](plans/plan-bookmark-feature.md) | — | Sẵn sàng implement |

---

## Tóm Tắt Theo Trạng Thái

### ✅ Done
- Use RDBMS, Backend Architecture, Cache Strategy, Frontend Structure, LRU Video Cache
- Admin Dashboard, Trivy Container Scanning, Hot Video Priority
- Bitwise Permissions, Hot Score Migration, File-based Hooks

### 🔄 In Progress
- React Migration

### 📋 Ready to Implement
- HLS Video Streaming
- Bookmark Feature

### 📝 Draft / Proposed
- Unified Video List API
- Bookmark Feature PRD (Draft — plan đã Approved)

---

## ADR Status Summary

| # | ADR | Trạng thái |
|---|-----|-----------|
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

*Cập nhật lần cuối: 2026-05-31*
