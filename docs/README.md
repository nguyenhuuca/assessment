# Funny Movies — Personal Project

> Built for fun. Learning by doing.

A personal side project for streaming funny videos — built to explore modern backend architecture, AI-assisted development, and real-world engineering practices. No business pressure, just curiosity and a love for building things.

---

## Why This Project Exists

| Goal | What I'm exploring |
|------|--------------------|
| Build something real | End-to-end video streaming system — not just a tutorial |
| Learn modern tech | Java 24 Virtual Threads, Spring Boot 3.x, React 19, HLS streaming, Kubernetes |
| Learn AI-assisted dev | Using Claude Code to scope features, write ADRs, generate plans, and implement code |
| Practice good engineering | ADRs, PRDs, architecture diagrams, coverage gates, CI/CD pipelines |
| Take security seriously | Bitwise permissions, feature flags, Trivy scanning, magic link auth |

---

## Tech Stack

**Backend:** Java 24 · Spring Boot 3.x · Virtual Threads (Project Loom) · PostgreSQL · Liquibase · Guava Cache · JWT · OpenTelemetry

**Frontend:** React 19 · Vite · TanStack Query · React Router · Bootstrap 5

**Infrastructure:** Docker · Kubernetes · Helm · GitHub Actions · Docker Hub

**AI Tooling:** Claude Code — for scoping, architecture decisions, implementation, and documentation

---

## How Features Are Built

Every feature follows a structured flow powered by Claude:

```
/scope → PRD → ADR → Plan → /builder → /qa-engineer → /code-review → merge
```

See the full guide: [From Scope to Implementation](flow-scope-to-implement.md)

---

## Architecture Documents

- [System Architecture Overview](Video%20Streaming%20System%20Architecture.md)
- [Sequence Diagram](Sequence-Diagram.md)
- [Performance Review](performance-review.md)
- [CI/CD Pipeline](diagrams/ci-pipeline.md)

---

## Architecture Decisions (ADRs)

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
