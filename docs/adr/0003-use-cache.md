# Use Guava Cache Initially, with Planned Migration to Redis

## Status
Accepted

## Date
2025-07-03

## Context
The system needs caching for:
- Video metadata (title, duration, availability)
- Chunk data in memory for video streaming
- Hit stats (view count, like count) used for cleanup logic

During early stages, an in-memory cache is sufficient and avoids external dependencies. However, for horizontal scaling and persistence across app restarts, Redis will be required.

## Decision
- **Currently**, we use **Guava Cache** for in-memory caching within the application.
- Guava is suitable for:
    - Fast local memory access
    - Lightweight TTL support
    - No need for network layer or external services

- **Future Plan**:
    - Migrate to **Redis** when:
        - We deploy multiple instances of the backend
        - Cache needs to persist across restarts
        - We centralize metrics or chunk-level cache
    - Redis will be deployed using Docker Compose locally and via managed services (e.g., Redis Cloud, AWS ElastiCache)

## Consequences
- Current setup is fast, zero-config, ideal for local testing
- Redis will introduce infra/ops overhead but bring cross-instance and persistent caching
- Code should be abstracted to allow switching from Guava to Redis with minimal impact (e.g., via `AppCache` interface)

## Related
- Task: Setup Redis with user/pass and Spring Boot integration
- Task: Cache video chunks and hit stats
