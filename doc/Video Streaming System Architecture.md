# Video Streaming System Architecture

Date: 2025-07-04  
Status: Accepted  
Authors: Canh  
Deciders: canh-labs  
Supersedes: -  
Superseded-by: -

## Context and Problem Statement

We are building a video streaming system where users can browse and watch videos on demand. The system must handle streaming efficiently, support video metadata management, in-memory caching, background download jobs, and telemetry for monitoring.

We adopt the [C4 Model](https://c4model.com) to document our software architecture in 3 levels: System Context, Container, and Component (for Backend).

---

## Decision Drivers

- Efficient video streaming with support for chunked responses
- Clear separation between frontend/backend/database
- Future-proof design with Redis integration and observability
- Developer onboarding and system clarity

---

## Considered Options

- Express + Node.js (not chosen)
- Monolithic Spring Boot with embedded Tomcat and Virtual Threads ✅
- CDN-based architecture (planned in future)

---

## Decision Outcome

Adopt Spring Boot monolith with clearly defined layers and caching mechanism, and monitor it via OpenTelemetry and Grafana Cloud.

---

## Architecture Overview (C4 Model)

### Level 1 - System Context

![img.png](images/C4-L1.png)

---

### Level 2 - Container Diagram

![img_1.png](images/C4-L2.png)

---

### Level 3 - Component Diagram (Backend)

![img_2.png](images/C4-L3.png)

---

### Deployment Diagram

![Deployment.png](images/Deployment.png)

---

## Consequences

- Easy to scale the backend horizontally
- Clear layering improves maintainability
- Video chunk caching can be switched from Guava to Redis later
- Monitoring is decoupled and aligned with observability standards

---

## ✨ Optimizations

* ✅ Uses **virtual threads** for controller to handle concurrent streams efficiently
* ✅ Uses **Guava cache abstraction** to allow future swap to Redis
* ✅ OTEL-compatible tracing enabled via agent `.jar` and `WithSpan` on service methods
* ✅ Metrics collected via `CacheStatsService` for total hits, misses, and per-file stats

---

##  Future Improvements

* Switch to **HLS** or **DASH** for adaptive bitrate
* Upload background jobs to **warm cache** by pre-fetching popular videos
* Support **Redis-based distributed cache** with same `AppCache` abstraction
* Use **async Netty-based streaming** for ultimate IO scalability

---

##  Notes

- Virtual Threads are used in Backend for efficient streaming
- In-memory cache with Guava used until Redis is integrated
- CronJob handles background downloading and database update