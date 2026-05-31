# Video Streaming System

This project delivers on-demand video streaming using Spring Boot, Guava cache, and Virtual Threads.  
Monitoring via OpenTelemetry and Grafana Cloud.

## 📄 Architecture Documents

- [Video Streaming System Architecture.md](Video%20Streaming%20System%20Architecture.md)
- [architecture_quality_report.md](architecture_quality_report_2025-07-05.md)
- [Sequence-Diagram.puml](Sequence-Diagram.md)


## 📝 Design Decisions

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0001](adr/0001-use-relational-database.md) | Use Relational Database (PostgreSQL) for Core Data | Accepted |
| [ADR-0002](adr/0002-backend-architecture.md) | Use Layered Spring Boot Architecture with Virtual Threads | Accepted |
| [ADR-0003](adr/0003-use-cache.md) | Use Guava Cache Initially, with Planned Migration to Redis | Accepted |
| [ADR-0004](adr/0004-frontend-structure.md) | Use Vanilla JS + Bootstrap with Planned Migration to React | Accepted |
| [ADR-0005](adr/0005-use_lru_cache_video_streaming.md) | Use LRU Cache for Video Streaming | Accepted |
| [ADR-0006](adr/0006-hls-video-streaming.md) | Switch to HLS for Local Video Streaming | Proposed |
| [ADR-0007](adr/0007-admin-dashboard.md) | Admin Dashboard — Content & Account Management | Accepted |
| [ADR-0008](adr/0008-react-migration.md) | Migrate Frontend to React with Vite | Proposed |
| [ADR-0009](adr/0009-unified-video-list-api.md) | Unified Video List API for UI Display | Proposed |
| [ADR-0010](adr/0010-trivy-container-scanning.md) | Add Trivy Dependency & Filesystem Scanning to CI | Accepted |
| [ADR-0011](adr/0011-hot-video-priority.md) | Hot Video Priority Scoring | Accepted |

