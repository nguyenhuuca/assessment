# Video Streaming Architecture with Chunk Caching and Virtual Threads

## 🔢 System Overview

This architecture outlines a backend service (Java Spring Boot 3) that streams video using HTTP Range requests, caches video in chunks, and supports concurrency-safe operations using virtual threads.

---

## 🌀 Component Diagram

```
Client (HTML5 <video>)
   |
   |---> [VideoController]
                |
                |---> [VideoService]
                            |
                            |---> [VideoCacheService]
                            |            |---> [ChunkLockManager]
                            |            |---> [DiskStorage]
                            |
                            |---> [GoogleDriveService]
```

---

## 🌐 Request Flow (Range-based)

1. **Client** sends `GET /stream/{fileId}` with `Range: bytes=start-end`
2. **VideoController** parses range, calculates chunk boundaries
3. Delegates to **VideoService.getPartialFileByChunk()**
4. **VideoCacheService** checks cache:

    * If **chunk hit**: return immediately
    * If **chunk miss**:

        * Fetch from Google Drive
        * Use `ChunkLockManager` to avoid concurrent writes
        * Save chunk to disk
        * Return chunk stream
5. **Response** is streamed using Spring's `StreamingResponseBody` with headers: `Content-Range`, `Content-Length`, `206 Partial Content`

---

## 📊 Chunk Storage Design

* Cache path: `CACHE_DIR/{fileId}/{start}-{end}.cache`
* Chunk size: 512KB
* Example:

    * `0-524287.cache`
    * `524288-1048575.cache`

---

## 🔗 Lock Strategy

* **Purpose:** Prevent multiple threads saving same chunk simultaneously
* **Solution:**

    * `ChunkLockManager` uses `AppCache<String, Boolean>` (Guava-based abstraction)
    * `tryLock()` uses `putIfAbsent(key, true)`
    * `release()` invalidates the key

---

## 🔄 Overlap Handling (Manual Seek)

* If a requested range overlaps with existing chunks, fallback to closest existing chunk if delta < 100KB
* Stream from that chunk to client
* Log warning: "Serving overlapping chunk from cache"

---

## ✨ Optimizations

* ✅ Uses **virtual threads** for controller to handle concurrent streams efficiently
* ✅ Uses **Guava cache abstraction** to allow future swap to Redis
* ✅ OTEL-compatible tracing enabled via agent `.jar` and `WithSpan` on service methods
* ✅ Metrics collected via `CacheStatsService` for total hits, misses, and per-file stats

---

## 🌎 Metrics Tracked

* Total hit/miss
* Hit/miss by `fileId`
* Exposed periodically every 5 minutes (scheduled task)

---

## 🌟 Future Improvements

* Switch to **HLS** or **DASH** for adaptive bitrate
* Upload background jobs to **warm cache** by pre-fetching popular videos
* Support **Redis-based distributed cache** with same `AppCache` abstraction
* Use **async Netty-based streaming** for ultimate IO scalability

---

## 📚 Notes

* Fallback logic ensures user experience is maintained even with overlap or cache miss
* Manual seek does not break flow due to closest chunk fallback
* Logs include thread name + `isVirtual()` for tracing concurrency behavior

---

**Owner:** Canh Labs / \[nguyenhuuca]
**Last Updated:** 2025-06-28
