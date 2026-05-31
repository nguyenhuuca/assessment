# ADR-0003: Use Guava Cache Initially, with Planned Migration to Redis

## Metadata
**Status:** Accepted
**Date:** 2025-07-03
**Deciders:** nguyenhuuca
**Related PRD:** N/A
**Tech Strategy Alignment:**
- [x] Decision follows Golden Path in `.claude/rules/tech-strategy.md`

**Domain Tags:** data, infrastructure
**Supersedes:** N/A
**Superseded By:** N/A

---

## Context
The system requires caching to reduce database load and improve response times for frequently accessed data including video metadata, video chunk data for streaming, and hit statistics. In the early single-instance deployment phase, an external cache service introduces unnecessary operational complexity. As the system scales to multiple instances, a shared cache becomes necessary for correctness.

---

## Decision Drivers
- Avoid external service dependencies during early development and single-instance deployment
- Video metadata and chunk data must be served with low latency
- Cache implementation must be swappable without changing business logic
- Horizontal scaling in the future will require a shared, cross-instance cache

---

## Considered Options

### Option 1: Guava Cache (current choice)
Google Guava's in-process LRU cache with TTL support, running inside the JVM.

| Pros | Cons |
|------|------|
| Zero external dependencies or infrastructure | Cache is not shared across multiple instances |
| Fast local memory access with no network overhead | Data lost on application restart |
| Lightweight TTL and size-based eviction built-in | Memory-bound; large caches compete with JVM heap |
| Easy to configure and test locally | |

### Option 2: Redis (immediate adoption)
An external in-memory data store supporting distributed caching, persistence, and pub/sub.

| Pros | Cons |
|------|------|
| Shared across all instances — correct for horizontal scale | Adds infrastructure dependency from day one |
| Supports persistence across restarts | Network latency adds overhead vs. in-process cache |
| Rich data structures (sorted sets, streams) | Requires ops setup: deployment, auth, monitoring |
| Industry standard for distributed caching | Overkill for a single-instance early-stage deployment |

### Option 3: Caffeine
A high-performance in-process cache for Java, successor to Guava Cache.

| Pros | Cons |
|------|------|
| Higher throughput than Guava in benchmarks | Same single-instance limitation as Guava |
| Spring Boot's preferred local cache implementation | Adds a dependency with minimal benefit over Guava at this scale |
| Compatible with Spring Cache abstraction | |

### Option 4: No Cache
Serve all requests directly from the database and filesystem without caching.

| Pros | Cons |
|------|------|
| Simplest implementation | High database load under concurrent video streaming |
| No cache invalidation complexity | Unacceptable latency for repeated video metadata reads |
| | View stat queries would hit the DB on every request |

---

## Decision Outcome
**Chosen Option:** Option 1 — Guava Cache (with planned migration to Redis)
**Rationale:** Guava Cache satisfies the current caching requirements with no operational overhead, which is appropriate for a single-instance deployment. The cache logic is abstracted behind an `AppCache` interface so the implementation can be swapped to Redis when horizontal scaling is required. Adopting Redis immediately would add infrastructure complexity before it is needed.

### Quantified Impact *(where applicable)*
| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| Video metadata DB calls | Per request | Near-zero for cached entries | TTL-based eviction controls staleness |
| Operational dependencies | None | None (Guava phase) | Redis deferred until multi-instance |

---

## Consequences
**Positive:**
- Fast, zero-configuration caching in the early development and single-instance phase
- No external service to provision, monitor, or secure during initial deployment
- Abstracted cache interface allows future migration to Redis with minimal code changes

**Negative:**
- Cache is not shared across instances; horizontal scaling before migration will cause cache misses and inconsistencies
- Cached data is lost on application restart, causing a cold-start period

**Risks:**
- Deferring Redis adoption could require urgent migration under time pressure when scaling is needed
- Cache abstraction must be maintained carefully to avoid leaking Guava-specific behavior into callers

---

## Validation
- [ ] Tech Strategy alignment confirmed
- [ ] Related plan document created: N/A

---

## Links
- [Guava Cache docs](https://github.com/google/guava/wiki/CachesExplained)
- [Redis official docs](https://redis.io/docs/)

---

## Changelog
| Date | Author | Change |
|------|--------|--------|
| 2025-07-03 | nguyenhuuca | Initial draft |
| 2026-05-31 | nguyenhuuca | Restructured to new ADR template |
