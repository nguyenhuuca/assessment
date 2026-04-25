# Use Layered Spring Boot Architecture with Virtual Threads Support

## Status
Accepted

## Date
2025-07-03

## Context
The backend handles multiple concerns:
- REST APIs (video, comment, like, view)
- Background jobs (cron: download from Google Drive, generate thumbnail, cleanup)
- Caching (Redis)
- File I/O (video streaming, stats logging)

We expect high concurrency, especially when serving video streams or updating view stats, which requires efficient threading without consuming too many OS threads.

## Decision
We use:
- **Spring Boot 3.x** with **virtual thread support** (Project Loom, JEP 444)
- Layered architecture: `Controller → Service → Repository`
- Enable virtual threads by:
    - Configuring Tomcat to use `Executors.newVirtualThreadPerTaskExecutor()`
    - Ensuring all blocking operations (DB, file, network) are thread-friendly

Virtual threads allow handling many concurrent requests with minimal memory/CPU cost compared to platform threads.

## Consequences
- Backend can handle more concurrent I/O-bound requests (video, file I/O) with fewer resources.
- Reduces the need for complex async/reactive code in most scenarios.
- Some libraries that use native threads may still block (must monitor).

## Related
- Task: Stream video using `RadomAccessFile` with virtual threads
- Task: Schedule background jobs using `@Scheduled`
- JEP 444: https://openjdk.org/jeps/444
