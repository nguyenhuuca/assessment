# Performance Review Report

**Date:** 2026-02-21
**Project:** Funny Movies (Java 24 / Spring Boot 3.x)
**Scope:** Full codebase — service layer, streaming, cache & concurrency
**Result:** ❌ FAIL — 14 High | 14 Medium | 4 Low

---

## Executive Summary

Three major risk areas were identified:

1. **Cache & Concurrency bugs** — race conditions in the rate limiter and email counter mean security controls are bypassable; `synchronized` on virtual threads eliminates Loom performance gains.
2. **Missing HTTP timeouts** — `RestTemplate` calls to ChatGPT and YouTube have no timeout, risking indefinite thread stalls that can cascade to full service outages.
3. **Streaming inefficiencies** — the video cache only stores the first chunk of each file; all seeks and resumes always hit disk. Combined with `readAllBytes()` heap allocation per cache miss, GC pressure grows rapidly under concurrent viewers.

---

## 🔴 High Severity

> Must fix before production. These findings represent correctness bugs, security control bypasses, or reliability risks under production load.

---

### H1 — `synchronized` Pins Virtual Threads (Loom Anti-Pattern) ✅ Fixed

| Field | Value |
|-------|-------|
| **File** | `api/src/main/java/com/canhlabs/funnyapp/aop/SlidingWindowRateLimiter.java` |
| **Lines** | 53–66 |
| **Type** | Virtual thread pinning |

**Description:**
The `allowRequest` method wraps its core logic in `synchronized (timestamps)`. In JDK 24 with virtual threads enabled, `synchronized` pins the virtual thread to its carrier OS thread for the lock duration. Under high request rates — the exact scenario a rate limiter handles — every contending virtual thread pins a carrier, causing platform thread exhaustion and eliminating the benefit of Project Loom.

**Fix:**
Replace `synchronized (timestamps)` with a `ReentrantLock` (or `StampedLock`) per key, stored alongside the deque. `ReentrantLock` does not pin virtual threads.

---

### H2 — Rate Limiter TOCTOU Race (Limit Bypassable)

| Field | Value |
|-------|-------|
| **File** | `api/src/main/java/com/canhlabs/funnyapp/aop/SlidingWindowRateLimiter.java` |
| **Lines** | 47–51 |
| **Type** | Race condition / TOCTOU |

**Description:**
`getIfPresent` and `put` are two separate non-atomic operations. Two concurrent virtual threads for the same key can both observe `null`, both create a new `LinkedList`, and both call `put`. The second `put` discards the first thread's deque, resetting the sliding window and allowing more requests than configured.

```java
// Broken:
Deque<Long> timestamps = requestCache.getIfPresent(key);  // both threads see null
if (timestamps == null) {
    timestamps = new LinkedList<>();
    requestCache.put(key, timestamps);                     // second put silently wins
}
```

**Fix:**
Use Guava's atomic load-or-compute:
```java
Deque<Long> timestamps = requestCache.get(key, LinkedList::new);
```

---

### H3 — LockManager Race Condition + O(n) Scan on Hot Path

| Field | Value |
|-------|-------|
| **File** | `api/src/main/java/com/canhlabs/funnyapp/cache/LockManagerImpl.java` |
| **Lines** | 23–37 |
| **Type** | Non-atomic check-then-act + performance |

**Description:**
The overlap scan (lines 23–34) and `putIfAbsent` (line 37) are two separate non-atomic operations. A second thread can pass the same overlap scan before the first thread's `putIfAbsent` completes, breaking lock semantics. Additionally, iterating `lockCache.asMap().keySet()` on every `tryLock` is O(n) in the number of active chunks.

**Fix:**
Replace with a `ConcurrentHashMap<String, RangeSet>` per `fileId`, protected by a per-file `ReentrantLock`, and perform check+insert atomically inside `compute`.

---

### H4 — No HTTP Timeout on ChatGPT `RestTemplate` ✅ Fixed

| Field | Value |
|-------|-------|
| **File** | `api/src/main/java/com/canhlabs/funnyapp/service/impl/ChatGptServiceImpl.java` |
| **Lines** | 39–44, 76–81 |
| **Type** | Missing timeout / blocking I/O |

**Description:**
`RestTemplate` makes blocking HTTP calls to ChatGPT with no read or connect timeout configured. ChatGPT API latency can reach 30–60+ seconds under load. A stalled call holds a virtual thread indefinitely. `makePoem()` is called from inside `StructuredTaskScope` forks, so a single hung ChatGPT call blocks the entire download batch.

**Fix:**
Configure `RestTemplate` (or migrate to `RestClient`/`WebClient`) with explicit timeouts:
```java
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(5_000);
factory.setReadTimeout(45_000);
RestTemplate restTemplate = new RestTemplate(factory);
```

---

### H5 — No HTTP Timeout on YouTube API + Unchecked NPE

| Field | Value |
|-------|-------|
| **File** | `api/src/main/java/com/canhlabs/funnyapp/client/YouTubeApiClientImpl.java` |
| **Lines** | 41–42 |
| **Type** | Missing timeout + NPE risk |

**Description:**
Same `RestTemplate` timeout issue as H4. Additionally, `response.getBody().get("items")` is not null-checked. A non-2xx response or empty body from the YouTube API throws `NullPointerException`, crashing the batch job.

**Fix:**
Add connect/read timeouts (same as H4). Add null checks:
```java
JsonNode body = response.getBody();
if (body == null || !body.has("items")) return List.of();
JsonNode items = body.get("items");
```

---

### H6 — Video Cache Only Stores First Chunk (`start == 0`)

| Field | Value |
|-------|-------|
| **File** | `api/src/main/java/com/canhlabs/funnyapp/cache/VideoCacheImpl.java` |
| **Lines** | 73–79 |
| **Type** | Missed cache opportunity + memory leak |

**Description:**
The cache condition requires `start == 0`. Every chunk after the first 256 KB is never cached regardless of request frequency. Viewers who seek or resume always hit disk. Additionally, `accessCounter` entries for non-zero-start chunks accumulate without eviction (removal is only in the cache-hit branch), creating a slow memory leak.

**Fix:**
Remove the `start == 0` guard to cache any popular chunk. Use `Caffeine` with `expireAfterWrite` for `accessCounter` to bound its size automatically.

---

### H7 — `getVideoIds()` Hot Endpoint Has No Cache

| Field | Value |
|-------|-------|
| **File** | `api/src/main/java/com/canhlabs/funnyapp/service/impl/YouTubeVideoServiceImpl.java` |
| **Lines** | 40–53 |
| **Type** | Missing cache on read-heavy hot path |

**Description:**
`getVideoIds()` backs `GET /top-videos` (the main listing page). It issues a full `SELECT * FROM youtube_video WHERE source = 'youtube'` on every request while bypassing the existing Guava cache infrastructure entirely. Data only changes when the scheduler runs.

**Fix:**
Cache the result under a fixed key (e.g., `"top-youtube-videos"`) and invalidate after `updateVideoDetails()` completes a successful `saveAll()`.

---

### H8 — `FetchType.EAGER` on `ShareLink.user` Loads Secrets on Every Query

| Field | Value |
|-------|-------|
| **File** | `api/src/main/java/com/canhlabs/funnyapp/entity/ShareLink.java` |
| **Lines** | 42 |
| **Type** | Over-fetching / security |

**Description:**
Every `ShareLink` query JOINs and fully hydrates the `User` entity including `mfaSecret` and `password`. This loads security-sensitive fields into memory unnecessarily on every list request.

**Fix:**
Change to `FetchType.LAZY`. Where only `userName` is needed, use a JPQL projection DTO to avoid the join entirely.

---

### H9 — Redundant Full `User` Load in `ShareServiceImpl`

| Field | Value |
|-------|-------|
| **File** | `api/src/main/java/com/canhlabs/funnyapp/service/impl/ShareServiceImpl.java` |
| **Lines** | 83–88, 175–186 |
| **Type** | Repeated DB calls |

**Description:**
The JWT context already contains the user ID and email. `getALLShare()` and `shareLink()` call `AppUtils.getCurrentUser()` then immediately issue a second DB query to load the full `User` entity, which is only needed as an FK reference.

**Fix:**
Use `userRepo.getReferenceById(id)` (returns a proxy without a SELECT) to set the FK on `ShareLink`. For queries, add `findAllByUserId(Long userId)` to avoid materializing the entity.

---

### H10 — `new ObjectMapper()` Allocated 3× Per Audit Call

| Field | Value |
|-------|-------|
| **File** | `api/src/main/java/com/canhlabs/funnyapp/aop/AuditLogAspect.java:72`, `MaskingUtils.java:51,60` |
| **Lines** | 72, 51, 60 |
| **Type** | Excessive allocation on hot path |

**Description:**
`ObjectMapper` is constructed fresh in `logAudit`, `cloneObject`, and `toJsonSafe` — three instances per intercepted method call. `@AuditLog` is applied class-wide on controllers and service classes. `ObjectMapper` construction involves reflection scanning and is expensive.

**Fix:**
Declare a `private static final ObjectMapper MAPPER = new ObjectMapper()` or inject the Spring-managed `ObjectMapper` bean. `ObjectMapper` is thread-safe after configuration.

---

### H11 — Full Reflection Clone on Every Audit Argument

| Field | Value |
|-------|-------|
| **File** | `api/src/main/java/com/canhlabs/funnyapp/aop/MaskingUtils.java` |
| **Lines** | 27–46 |
| **Type** | Unnecessary reflection overhead |

**Description:**
`maskSensitiveFields` clones every method argument via JSON serialize/deserialize and iterates all declared fields via `clazz.getDeclaredFields()` + `field.setAccessible(true)` on every call, with no caching of field metadata.

**Fix:**
Pre-compute per-class sensitive field lists in a `ConcurrentHashMap<Class<?>, List<Field>>` populated on first access. Skip the JSON clone for classes with no sensitive fields.

---

### H12 — Email Daily Counter Is Non-Atomic (Limit Not Enforced)

| Field | Value |
|-------|-------|
| **File** | `api/src/main/java/com/canhlabs/funnyapp/cache/EmailCacheLimiterImpl.java` |
| **Lines** | 26–29 |
| **Type** | Race condition / lost update |

**Description:**
`get` then `put` is a non-atomic read-modify-write. Two concurrent threads both read `0`, both compute `1`, and both write `1`. The daily email limit is silently under-counted and never correctly enforced.

**Fix:**
Use `AtomicInteger` as the cache value and call `incrementAndGet()`, or use:
```java
cache.asMap().merge(key, 1, Integer::sum);
```

---

### H13 — Missing Pagination on List Queries

| Field | Value |
|-------|-------|
| **Files** | `repo/YoutubeVideoRepo.java:12`, `repo/ShareLinkRepo.java:14,16` |
| **Type** | Missing pagination / full table scan |

**Description:**
`findAllBySource`, `findByOrderByCreatedAtDesc`, and `findAllByUser` return unbounded `List<T>` with no limit. Full table scans that load all rows into heap on every call.

**Fix:**
Add `Pageable` parameters to each method and return `Page<T>` or `Slice<T>`. Update service and controller layers to accept page/size parameters.

---

## 🟡 Medium Severity

> Should fix before production or in the next sprint. These findings impact performance under moderate load.

| # | File | Lines | Issue | Fix |
|---|------|-------|-------|-----|
| M1 | `StreamVideoServiceImpl.java` | 55–56 | `readAllBytes()` on cache miss allocates 512 KB per concurrent viewer — GC pressure | Stream directly to response on miss; only materialize to `byte[]` for cache write |
| M2 | `StreamVideoServiceImpl.java` | 99–105 | Serial N+1 ChatGPT calls + individual `save()` per video | Parallelize with `StructuredTaskScope`; batch with `saveAll()` |
| M3 | `VideoStorageServiceImpl.java` | 125–141, 204 | Blocking `makePoem()` inside `StructuredTaskScope` fork serializes download pipeline | Separate download + thumbnail pipeline from description enrichment into two phases |
| M4 | `VideoStreamController.java` / `VideoStorageServiceImpl.java` | 52 / 112 | `file.length()` stat syscall on every stream request; file size never changes | Cache in `StatsCache` or `ConcurrentHashMap<String, Long>` keyed by `fileId` |
| M5 | `VideoStorageServiceImpl.java` | 99–102 | Dual `RandomAccessFile` + `FileInputStream` sharing one FD — fragile close ordering | Use `FileChannel.transferTo()` for zero-copy OS-level transfer |
| M6 | `VideoStreamController.java` | 66 | 8 KB buffer for 512 KB chunks = 64 loop iterations/chunk | Increase to 64 KB (`64 * 1024`) |
| M7 | `FfmpegServiceImpl.java` | 12–13 | `@Async` called from `StructuredTaskScope` fork — thumbnail exceptions silently swallowed | Remove `@Async`; the `scope.fork()` already handles concurrency |
| M8 | `YouTubeVideoServiceImpl.java` | 108–118 | ChatGPT + YouTube API calls are serial; no caching of results between scheduler runs | Cache `getTopYoutubeVideoIds()` result with 6–24 h TTL |
| M9 | `CacheBean.java` | 22–25 | `videoCache` evicts by count (2000 × 512 KB ≈ 1 GB); ignores byte size | Switch to `createCacheByWeight` with a byte cap (e.g., 150 MB) |
| M10 | `AppScheduler.java` | 66–70 | `calculateRatio()` called inside stats loop = O(n²) | Compute once before the loop; capture in local variable |
| M11 | `AppScheduler.java` | 74–87 | Cron every 15 min but lookback window is 60 min → 4× file re-download overlap | Align window with schedule period; track last sync timestamp durably |
| M12 | `AppScheduler.java` | 90–96 | Cron `0 0 3 30 2 *` = February 30th — **job never fires**; deletion logic also commented out | Fix cron to `0 0 3 L * *` (last day of month); uncomment deletion logic |
| M13 | `ChunkIndexCacheImpl.java` | 29, 36 | `HashSet<Range>` shared across virtual threads — no synchronization | Use `ConcurrentHashMap.newKeySet()` or synchronize all accesses |
| M14 | `TaskUtils.java` | 34, 61 | `scope.join()` with no deadline — hung subtask hangs caller forever | Use `scope.joinUntil(Instant.now().plus(timeout))` with `TimeoutException` handling |

---

## 🟢 Low Severity

> Good to fix; won't cause outages but improve correctness and maintainability.

| # | File | Lines | Issue | Fix |
|---|------|-------|-------|-----|
| L1 | `repo/UserRepo.java` | 9–10 | `findAllByUserName` named like a collection query but returns single entity | Rename to `findByUserName` returning `Optional<User>` |
| L2 | `service/impl/VideoAccessServiceImpl.java` | 25–31 | `@Async` stub with empty body spins a virtual thread on every video view | Remove `@Async` from stub until feature is implemented |
| L3 | `cache/GuavaAppCache.java` | 47–58 | `maxSize` parameter in 3-arg constructor silently ignored | Apply `.maximumSize()` or remove the parameter |
| L4 | `web/VideoStreamController.java` | 78 | `Content-Type: video/mp4` hardcoded for all files | Detect MIME type at download time, store in `VideoSource`, serve at stream time |

---

## Also Noted

| File | Lines | Note |
|------|-------|------|
| `ShareServiceImpl.java` | 139 | `new ObjectMapper()` created per `POST /share-links` call — inject Spring bean |
| `ShareServiceImpl.java` | 131–138 | Uses raw `RestTemplate` instead of the existing `YouTubeApiClient` bean |
| `ShareServiceImpl.java` | 159–172 | Manual `split("=")[1]` URL parsing drops base64 values with `=` — use `UriComponentsBuilder` |
| `AppCacheFactory.java` | 27–33 | `createDefaultCache` hard-codes 5 min TTL / 1000 entries, ignores `CacheProperties.defaultSettings` |
| `VideoStorageServiceImpl.java` | 165–172 | First `listFilesInFolder` overload missing `nextPageToken` pagination loop — silently drops files 101+ |

---

## Priority Fix Order

```
1. [H1]  synchronized → ReentrantLock in SlidingWindowRateLimiter     (thread pinning)
2. [H2]  TOCTOU fix in SlidingWindowRateLimiter                        (rate limit bypass)
3. [H12] Atomic increment in EmailCacheLimiterImpl                      (email limit bypass)
4. [H4]  Add timeouts to ChatGPT RestTemplate                          (indefinite hang risk)
5. [H5]  Add timeouts + null check in YouTubeApiClientImpl              (NPE + hang risk)
6. [H7]  Cache getVideoIds() hot endpoint                               (DB hit on every page load)
7. [H6]  Cache non-zero chunks in VideoCacheImpl                        (majority of stream requests miss cache)
8. [H8]  LAZY fetch on ShareLink.user                                   (secrets loaded on every list)
9. [H10] Share ObjectMapper in AuditLogAspect / MaskingUtils            (3× alloc per request on hot path)
10. [H11] Cache field metadata in MaskingUtils                           (reflection on every audit call)
11. [H3]  Fix LockManager race + O(n) scan                              (lock correctness + performance)
12. [H9]  getReferenceById in ShareServiceImpl                           (extra DB round-trip)
13. [H13] Add Pageable to unbounded repo queries                         (full table scan as data grows)
```

---

*Generated by Claude Code performance review — 3 parallel reviewer agents across service layer, streaming/controllers, and cache/concurrency.*
