# ADR-0002: Use Layered Spring Boot Architecture with Virtual Threads Support

## Metadata
**Status:** Accepted
**Date:** 2025-07-03
**Deciders:** nguyenhuuca
**Related PRD:** N/A
**Tech Strategy Alignment:**
- [x] Decision follows Golden Path in `.claude/rules/tech-strategy.md`

**Domain Tags:** infrastructure, api
**Supersedes:** N/A
**Superseded By:** N/A

---

## Context
The backend handles REST APIs, background jobs, file I/O for video streaming, and view stat updates. High concurrency is expected, particularly when serving video streams to multiple simultaneous users. The architecture must handle many parallel I/O-bound requests efficiently without consuming excessive OS thread resources or requiring complex asynchronous programming models.

---

## Decision Drivers
- High concurrency requirement for video streaming and stat updates
- I/O-bound workloads dominate (database, file, network calls)
- Team familiarity with imperative, synchronous code is preferred over reactive programming
- Reducing infrastructure cost by maximizing throughput per instance
- Maintainability and onboarding simplicity must be preserved

---

## Considered Options

### Option 1: Spring Boot + Virtual Threads (Project Loom)
Spring Boot 3.x with Java 24 virtual threads enabled via Project Loom (JEP 444). Layered architecture: Controller → Service → Repository.

| Pros | Cons |
|------|------|
| Handles high concurrency with minimal memory per thread | Some third-party libraries may pin platform threads (must monitor) |
| Synchronous, imperative code style — easy to read and debug | Virtual threads are relatively new; edge cases may surface |
| No reactive programming model required | Requires JDK 21+ (Java 24 used here) |
| Natural fit with Spring Boot's existing servlet model | |
| Layered architecture is well-understood and easy to onboard | |

### Option 2: Spring WebFlux (Reactive)
Spring's reactive stack using Project Reactor and non-blocking I/O throughout the stack.

| Pros | Cons |
|------|------|
| Excellent scalability for high-concurrency I/O workloads | Steep learning curve; reactive programming is complex |
| Non-blocking end-to-end eliminates thread starvation | All layers must be reactive — difficult to adopt incrementally |
| Battle-tested in production at scale | Debugging and stack traces are harder to follow |
| | Requires reactive-compatible libraries throughout |

### Option 3: Node.js / Express
JavaScript runtime with event-loop-based non-blocking I/O, commonly used for API backends.

| Pros | Cons |
|------|------|
| Single-threaded event loop handles concurrency naturally | Not idiomatic for a Java team |
| Lightweight and fast to prototype | Limited type safety without TypeScript overhead |
| Large ecosystem of packages | CPU-bound tasks block the event loop |
| | Weaker ecosystem for enterprise patterns (JPA, Spring Security) |

---

## Decision Outcome
**Chosen Option:** Option 1 — Spring Boot + Virtual Threads (Project Loom)
**Rationale:** Virtual threads deliver reactive-level concurrency with synchronous, imperative code. This keeps the codebase simple and maintainable while meeting the high-concurrency requirement for video streaming. Spring WebFlux would require a full reactive rewrite across all layers and significantly increase complexity. Node.js is not suitable for a Java-first team with JPA and Spring Security requirements.

### Quantified Impact *(where applicable)*
| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| Max concurrent requests (per instance) | Limited by OS thread count | Scales to thousands | Virtual threads are lightweight |
| Code complexity | N/A | Low (synchronous style) | No reactive operators or callbacks |

---

## Consequences
**Positive:**
- Backend handles many concurrent I/O-bound requests with far fewer OS resources
- Synchronous code style reduces cognitive load for developers
- Background jobs and REST handlers share the same simple threading model
- Straightforward layered architecture is easy to extend and test

**Negative:**
- Libraries that pin platform threads (e.g., certain JDBC drivers, synchronized blocks) may reduce virtual thread benefits
- Requires JDK 21 or later; Java 24 with preview features adds compiler configuration overhead

**Risks:**
- Undetected thread pinning by third-party libraries could silently degrade throughput
- Preview features in Java 24 may introduce breaking changes in future JDK releases

---

## Validation
- [ ] Tech Strategy alignment confirmed
- [ ] Related plan document created: N/A

---

## Links
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Spring Boot Virtual Threads docs](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.thread-virtual)

---

## Changelog
| Date | Author | Change |
|------|--------|--------|
| 2025-07-03 | nguyenhuuca | Initial draft |
| 2026-05-31 | nguyenhuuca | Restructured to new ADR template |
