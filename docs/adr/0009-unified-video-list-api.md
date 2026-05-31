# ADR-0009: Unified Video List API

## Metadata

**Status:** Proposed · **Date:** 2026-02-06 · **Deciders:** nguyenhuuca · **Tags:** api, data  
**Related PRD:** N/A · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ✅ Follows Golden Path

---

## Context

The application exposes four separate video listing endpoints:
- `/api/top-videos` — YouTube videos suggested by ChatGPT
- `/api/stream/list` — Locally stored streamable videos
- `/api/share-links` — User-shared video links
- `/api/private-videos` — Private user uploads

These endpoints share no common contract and have accumulated compounding limitations:

- No pagination — any response with more than ~100 videos risks memory exhaustion
- No sorting — results are returned in insertion order only
- No filtering — clients cannot narrow results by source, user, or category
- No search — titles and descriptions are not queryable
- Frontend must call all four endpoints and merge results client-side, leading to inconsistent ordering and duplicated logic
- No caching strategy — every request hits the database
- Service-side sorting of merged lists breaks at scale

---

## Decision Drivers

- Eliminate client-side result merging — the frontend should call one endpoint
- Prevent memory exhaustion from unbounded list responses
- Enable search, sorting, and filtering without frontend workarounds
- Align with the existing Guava cache strategy (ADR-0003)
- Maintain backward compatibility during migration — existing consumers must not break

---

## Considered Options

### Option 1: Keep fragmented endpoints (status quo)
Retain all four endpoints; address limitations individually on each.

| Pros | Cons |
|------|------|
| No migration effort | Each endpoint needs independent pagination/sort/filter work |
| | Frontend continues to merge results — UX inconsistency persists |
| | Caching must be implemented four times |
| | Scalability problems multiply with each new video source |

### Option 2: Unified endpoint with service-layer aggregation (chosen)
Single endpoint `GET /v1/funny-app/videos` with `page`, `size`, `sort`, `source`, `userId`, `query`, and `includeStats` parameters. The service layer queries each repository independently and merges results in memory before returning a paginated response.

| Pros | Cons |
|------|------|
| Single contract for all video sources | Service-level aggregation is more complex than direct queries |
| Pagination, sort, filter, and search in one place | In-memory merging has memory overhead at large page sizes (bounded by max page size) |
| Caching implemented once | Cache invalidation must cover all four source types |
| Old endpoints can be thin deprecated wrappers | Frontend migration required (mitigated by deprecation wrappers) |
| Type-safe, flexible — easy to add new sources | |

### Option 3: Unified endpoint with UNION SQL query
Single endpoint backed by a database `UNION` across all source tables.

| Pros | Cons |
|------|------|
| Single DB round-trip | Requires shared schema shape across heterogeneous tables |
| Potentially faster than service aggregation | Complex query harder to maintain and extend |
| | Pushes sorting/filtering logic into raw SQL — harder to type-safely test |
| | Optimization deferred: no current evidence this is a bottleneck |

### Option 4: GraphQL API
Replace REST endpoints with a GraphQL schema.

| Pros | Cons |
|------|------|
| Flexible field selection | Major departure from current REST + response-wrapper conventions |
| Eliminates over-fetching | Requires new tooling on both backend and frontend |
| | High migration cost with no proportional near-term benefit |
| | Not on the Golden Path |

---

## Decision Outcome
**Chosen Option:** Option 2 — Unified endpoint with service-layer aggregation

**Rationale:** Service-layer aggregation delivers all required capabilities (pagination, sort, filter, full-text search, lazy stats loading) with the lowest risk and migration cost. A UNION query would force a shared schema projection across heterogeneous source tables, creating tight coupling that makes adding new sources harder. GraphQL is not on the Golden Path and introduces unwarranted complexity. The status-quo option leaves the core UX problem (client-side merging, no pagination) unresolved.

Offset pagination is chosen over cursor-based because admin and regular users need random page access, and the current dataset does not approach the scale where offset performance degrades meaningfully. Cursor-based pagination can be adopted if that changes.

PostgreSQL full-text search is sufficient for the expected dataset size (< 100k videos); Elasticsearch would be premature optimization.

Guava cache is used for list query results, consistent with ADR-0003. Redis migration is deferred until horizontal scaling is needed.

Stats (view counts, likes) are loaded lazily (`includeStats=true`) because they are expensive to compute and most callers do not need them.

Existing endpoints are retained as thin deprecated wrappers delegating to the new service, with a 6-sprint sunset window.

### Quantified Impact *(where applicable)*

| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| DB calls per list view | 4 (one per endpoint) | 1–3 (per source filter) | Cache hit eliminates DB calls entirely |
| DB load (estimated) | Baseline | ~80–90% reduction | Via Guava cache with 5-min TTL |
| p95 API latency target | Unmeasured | < 500ms | Success metric |
| Cache hit rate target | N/A | > 80% | Success metric |

---

## Consequences
**Positive:**
- Single endpoint eliminates frontend result-merging logic
- Pagination prevents memory exhaustion at any dataset size
- Caching reduces database load by an estimated 80–90%
- Full-text search, sorting, and filtering are available without frontend workarounds
- DRY: one cache, one pagination pattern, one response contract
- New video sources can be added by extending the service, not adding new endpoints

**Negative:**
- Service-layer aggregation increases code complexity vs. direct single-table queries
- Frontend must migrate from four endpoints to one (mitigated by backward-compatible wrappers)
- Cache invalidation must be coordinated across create, update, and delete operations for all source types
- In-memory merge can consume memory for large sort operations (bounded by enforced max page size)

**Risks:**
- Cache invalidation gaps could serve stale lists — mitigate with conservative TTL and explicit invalidation on writes
- Offset pagination degrades at extreme page numbers — monitor and migrate to cursor-based if needed
- 6-sprint deprecation window may be tight for consumers unaware of the change — communicate actively

---

## Validation
- [ ] Tech Strategy alignment confirmed
- [ ] Related plan document created: [docs/plans/plan-unified-video-api.md](../plans/plan-unified-video-api.md) *(to be created)*

---

## Links
- ADR-0001: PostgreSQL database decision
- ADR-0002: Layered Spring Boot architecture with Virtual Threads
- ADR-0003: Guava Cache strategy (future Redis migration)
- ADR-0005: LRU cache for video streaming
- Plan document: `docs/plans/plan-unified-video-api.md` (to be created)

---

## Changelog
| Date | Author | Change |
|------|--------|--------|
| 2026-02-06 | nguyenhuuca | Initial draft |
| 2026-05-31 | nguyenhuuca | Restructured to new ADR template; technical details moved to plan doc |
