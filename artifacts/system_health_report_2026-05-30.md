# System Health Report: Funny Movies Application
**Date:** 2026-05-30
**Architect:** Principal Architect (Claude Sonnet 4.6)
**Status:** ✅ OPERATIONAL
**Previous Report:** 2026-02-06

---

## Executive Summary

Since the February 2026 report, the **Funny Movies** application has made significant quality improvements: test coverage jumped from **58% to 95%+** (exceeding industry standard), and several new features have been added — bitwise permission system, comments, video access stats, and a complete MFA flow. The system is in strong shape with 16 successful releases since the last review.

The JWT library has been upgraded to 0.12.6 (resolved this session). The remaining open item is the HPA config, which is blocked on Redis cache.

**Overall Health Score: 8.5/10** _(up from 8.0 — driven by coverage gains and new features)_

---

## Delta Since Report 2026-02-06

| Area | Feb 2026 | May 2026 | Change |
|------|----------|----------|--------|
| Test Coverage | 58% | **95%+** | ✅ +37% |
| App Version | prod-12 | prod-28 | ✅ 16 releases |
| Test Files | 30 | 52 | ✅ +22 files |
| Source Files | 100+ | 147 | ✅ +47 files |
| Permission System | ❌ | ✅ Bitwise | ✅ New |
| Comment Feature | ❌ | ✅ | ✅ New |
| Video Access Stats | ❌ | ✅ | ✅ New |
| MFA (TOTP) | Partial | ✅ Complete | ✅ Improved |
| JWT 0.11.5 | 🔴 | ✅ 0.12.6 | ✅ Fixed |
| HPA maxReplicas=1 | ⚠️ | ⚠️ | ❌ Not fixed |
| Guava 32.1.2 | ⚠️ | ⚠️ | ❌ Not fixed |

---

## System Overview

| Component | Status | Version | Notes |
|-----------|--------|---------|-------|
| Java Runtime | ✅ | 24 (preview) | Virtual Threads enabled |
| Spring Boot | ✅ | 3.5.0 | Latest stable |
| PostgreSQL | ✅ | Latest | Schema via Liquibase |
| Maven | ✅ | 3.x | Build automation |
| Docker | ✅ | Latest | prod-28 |
| Kubernetes | ✅ | Helm 0.1.0 | Deployed |
| CI/CD | ✅ | GitHub Actions | Fully automated |
| Test Coverage | ✅ | **95%+** | Exceeds industry standard |
| Version | ✅ | prod-28 | Auto-incremented |

---

## Architecture Assessment

### ✅ Strengths

#### 1. Outstanding Test Coverage Growth
Coverage grew from 58% to **95%+** across four consecutive sprints:

| Sprint | Before | After | Tests Added |
|--------|--------|-------|-------------|
| 1 | 62.7% | 79.5% | +96 |
| 2 | 79.5% | 86.7% | +97 |
| 3 | 86.7% | 95%+  | +51 |
| 4 | 95%+  | 95%+  | +32 (gap-fill) |

The CI coverage gate (+1%/commit) ensures coverage never regresses.

#### 2. Bitwise Permission System
A fully functional, fine-grained permission model was introduced:

```java
public enum Permission {
    READ(1 << 0), WRITE(1 << 1), EXEC(1 << 2),
    DELETE(1 << 3), ADMIN(1 << 4);
}
```

- `@HasPermission(perm = Permission.ADMIN)` annotation on endpoints
- Backed by `@PreAuthorize` + Spring Security
- DB bootstrapped via `202605010001-init_permissions.sql`

#### 3. New Features Since Feb 2026
- **Comment system** — `CommentController` + `create-comment-table` migration
- **Video Access Stats** — view tracking (`video_access_stats` table)
- **Google Drive streaming** — `VideoStorageServiceImpl` downloads and streams from Drive
- **Logout endpoint** — added to `UserController`
- **Complete MFA flow** — magic link + TOTP end-to-end

#### 4. Concurrency Model
- `StructuredTaskScope` used in `VideoStorageServiceImpl.downloadFileFromFolder()` for parallel file downloads
- All I/O operations benefit from Virtual Threads via Spring Boot configuration

#### 5. Robust CI/CD
```
Build → Test + Coverage → SonarCloud + Codecov
     → Docker Build → Push Registry → Update Helm → Deploy VM
```
16 releases shipped without any recorded rollback.

---

## Open Issues

### ✅ Issue 1: JWT Library — RESOLVED (2026-05-30)
**Was:** 0.11.5 (2022) — HIGH severity
**Now:** 0.12.6 — security patches for signature validation and algorithm hardening applied.

API migration completed in `JwtProvider.java` and `JwtProviderTest.java` (commit `bfac68e`). All tests pass.

---

### ⚠️ Issue 2: HPA maxReplicas=1
**Severity:** MEDIUM — **Open since 2026-02-06**
**File:** `helm/funny-app/values.yaml:19`

```yaml
autoscaling:
  minReplicas: 1
  maxReplicas: 1    # HPA defined but will never scale
```

**Note:** Scaling beyond 1 pod requires a distributed cache first (Redis per ADR-0003), since Guava is in-memory only.

---

### ⚠️ Issue 3: Outdated Dependencies
**Severity:** LOW–MEDIUM

| Dependency | Current | Latest | Notes |
|------------|---------|--------|-------|
| Guava | 32.1.2-jre | 33.3.1-jre | Potential CVEs |
| Commons Validator | 1.7 | 1.9 | Minor risk |

Run `mvn versions:display-dependency-updates` to audit the full list.

---

### ⚠️ Issue 4: Platform Threads in StructuredTaskScope
**Severity:** LOW
**File:** `VideoStorageServiceImpl.java:123`

```java
var scope = new StructuredTaskScope<>("download", Thread.ofPlatform().factory());
```

Using a platform thread factory undermines the Virtual Threads model. Switch to virtual threads for consistency and efficiency:

```java
var scope = new StructuredTaskScope<>("download", Thread.ofVirtual().factory());
```

---

### ⚠️ Issue 5: `SPRING_PROFILES_ACTIVE=dev` in Helm
**Severity:** LOW
**File:** `helm/funny-app/values.yaml:35`

```yaml
- name: SPRING_PROFILES_ACTIVE
  value: "dev"   # Swagger UI enabled
```

If this chart is used for a production deployment, Swagger should not be exposed. Consider a separate `values-prod.yaml` with `"prod"`.

---

## Test Coverage

### ✅ Current State: 95%+ Instruction Coverage

**Test approach:**
- Plain Mockito (`@ExtendWith(MockitoExtension.class)`) — avoids `@WebMvcTest` CME issues with `StreamingResponseBody`
- `MockedStatic` for `AppUtils` and `Converter` static methods
- `@TempDir` for file system tests
- Reflection for private method testing (`getQueryParam`, `toEntity`)

**52 test files** covering all layers: controllers, services, repositories, AOP, cache, utils.

---

## Project Structure

```
api/src/main/java/com/canhlabs/funnyapp/
├── aop/      ✅ AuditLog, RateLimited, HasPermission (NEW), Sensitive
├── cache/    ✅ VideoCacheImpl, ChunkIndexCacheImpl, StatsCacheImpl
├── client/   ✅ YouTubeApiClient
├── config/   ✅ Spring Security, AppProperties
├── dto/      ✅ Full DTO coverage
├── entity/   ✅ User, ShareLink, VideoSource (NEW), VideoAccessStats (NEW)
├── enums/    ✅ Permission (NEW — bitwise)
├── exception/✅ CustomException, ControllerAdvice
├── filter/   ✅ JWT filter
├── jobs/     ✅ AppScheduler
├── repo/     ✅ JPA repositories
├── service/  ✅ Interfaces + implementations
├── utils/    ✅ Contract, ContractDSL, AppUtils
└── web/      ✅ 7 controllers (AdminController, CommentController NEW, ...)
```

**Liquibase Migrations:**
```
202504300001  add-user-role
202504300002  add-video-status
20250601001   add-mfa
202506040001  create-email-request
202506220101  create-source-stream
202507210001  create_video_access_stats_table  (NEW)
202508240001  create-comment-table             (NEW)
202605010001  init_permissions                 (NEW)
```

---

## Kubernetes Deployment

| Resource | Configuration | Status |
|----------|--------------|--------|
| Namespace | `funny-app` | ✅ |
| Image | `nguyenhuuca/funny-app:prod-28` | ✅ |
| Replicas | 1 (HPA: 1–1) | ⚠️ Not autoscaling |
| Service | NodePort 30080 | ✅ |
| Resources | 400m–500m CPU, 600Mi RAM | ✅ |
| Probes | Liveness + Readiness (exec curl) | ✅ |
| Email Secrets | Kubernetes Secret | ✅ |
| GCP Credentials | Secret mount `/secrets/googlekey.json` | ✅ |
| Active Profile | `dev` (Swagger on) | ⚠️ |

---

## CI/CD Pipeline Health

**Status:** ✅ Fully Operational — 16 successful releases

| Job | Status | Notes |
|-----|--------|-------|
| Build (JDK 24) | ✅ | Maven + preview flags |
| Test + Coverage | ✅ | 95%+ enforced |
| SonarCloud | ✅ | Code quality gate |
| Codecov | ✅ | Coverage tracking |
| Docker Build/Push | ✅ | prod-28 |
| Helm Auto-update | ✅ | values.yaml patched per release |
| VM Deploy | ✅ | SSH + SCP |
| Git Tag | ✅ | Per release |

---

## Security Posture

### ✅ Strengths
- Passwordless magic link authentication
- JWT authorization (upgrade pending)
- Complete MFA (TOTP)
- Bitwise permission system with `@HasPermission`
- AOP audit logging with `@Sensitive` field masking
- Sliding-window rate limiting (`SlidingWindowRateLimiter`)
- CORS and Spring Security configured

### Open Gaps
| # | Issue | Severity | Status |
|---|-------|----------|--------|
| 1 | JWT 0.11.5 — outdated security library | HIGH | ✅ Fixed 2026-05-30 |
| 2 | Swagger exposed via `dev` profile in Helm | LOW | ❌ Backlog |
| 3 | No secrets scanning in CI | MEDIUM | ❌ Backlog |
| 4 | No container image scanning (Trivy) | MEDIUM | ❌ Backlog |

---

## Performance Considerations

### ✅ Current Optimizations
- Virtual Threads for all I/O via Spring Boot
- Guava LRU cache for video chunks and metadata
- HTTP Range request support for streaming
- `RandomAccessFile` for efficient video byte-range serving
- `StructuredTaskScope` for parallel Drive file downloads

### Known Bottlenecks
1. **In-memory cache** — cannot share state across pods; blocks horizontal scaling (ADR-0003 tracks Redis migration)
2. **Single replica** — HPA defined but nonfunctional until maxReplicas > 1
3. **Platform threads in StructuredTaskScope** — see Issue 4

---

## Action Items (Prioritized)

### ✅ Completed This Session
1. ~~**Upgrade JWT 0.11.5 → 0.12.6**~~ — done, commit `bfac68e`

### ⚠️ This Sprint
2. **Upgrade Guava 32.1.2 → 33.3.1** (~1 hour)

### ⚠️ Next Sprint
3. **Switch `Thread.ofPlatform()` → `Thread.ofVirtual()`** in `VideoStorageServiceImpl` (30 min)
4. **Fix HPA `maxReplicas: 1` → `5`** after Redis cache is in place
5. **Add Trivy scan** to CI pipeline (~2 hours)

### 🔵 Backlog
6. **Redis cache implementation** per ADR-0003 — prerequisite for horizontal scaling
7. **Switch Helm probes** from `exec: curl` to `httpGet` for efficiency
8. **Enable Dependabot** for automated dependency PRs
9. **Set `SPRING_PROFILES_ACTIVE=prod`** in a dedicated `values-prod.yaml`

---

## Conclusion

The **Funny Movies** application is in good health. The 95%+ test coverage milestone is a standout achievement — well above the 80% industry benchmark — and the 16 clean releases demonstrate a reliable CI/CD pipeline. The new permission system, comment feature, and video access stats meaningfully expand the product surface.

The main outstanding item is the **JWT library upgrade**, which has been deferred since February. This should be the top priority for the current sprint before any further feature work.

---

## Appendix

**References:**
- Previous report: `artifacts/system_health_report_2026-02-06.md`
- ADR-0003: Guava → Redis cache migration plan
- OWASP Top 10 2021

**Report Generated By:** Principal Architect (Claude Sonnet 4.6)
**Timestamp:** 2026-05-30
**Next Review:** 2026-08-30
