# PRD: Spring Boot 4.1 Upgrade

<!--
Product Requirements Document
Filename: docs/prd/PRD-spring-boot-4-upgrade.md
Owner: nguyenhuuca
Handoff to: Architect (/architect), Builder (/builder)
Related Skills: scope, planning-artifacts
-->

## Overview

**Status:** Done
**Author:** nguyenhuuca
**Date:** 2026-10-28
**Version:** 1.0
**Tracking:** `docs/tracking.md` row to be added on approval
**PR-FAQ:** N/A — platform maintenance, not a new initiative
**Stakeholders:** nguyenhuuca (owner, dev, ops)

Upgrade the backend (`api/`) from Spring Boot **3.5.0** to the latest **4.1.x**, including the dependencies Boot 4 forces along (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3, springdoc 3), with no user-visible behavior change. Java stays on **24**.

## Problem Statement

The backend runs on Spring Boot 3.5.0. The 3.5.x line reached end of open-source support on **2026-06-30**; 3.5.16 was its last OSS release and Spring points users to 4.0.x / 4.1.x. The app is four months past OSS EOL and also 16 patch releases behind the final 3.5.x, so it misses security fixes already published for its own line. The repository is public and the app serves production traffic, so known, unpatched CVEs in the framework are a direct exposure.

Who has the problem: the maintainer (no more upstream patches on the current line) and end users (exposed to framework vulnerabilities in prod).

### Evidence

**Quantitative Evidence:**
- Spring Boot 3.5.x OSS EOL: 2026-06-30 — 120 days ago as of this PRD.
- Current version 3.5.0 vs last OSS 3.5.16: 16 patch releases missed.
- Public CVEs affecting the 3.5 line in 2026 include CVE-2026-22729 and CVE-2026-22730 (HeroDevs write-up).
- Jackson 2 → 3 migration surface: 13 main + 12 test files import `com.fasterxml.jackson.databind|core|dataformat`; 7 more main files use annotations only.

**Qualitative Evidence:**
- Spring's 3.5.16 release post: last OSS release of 3.5.x; recommends moving to 4.0.x or 4.1.x.
- Several pins in `api/pom.xml` already drift from the Boot BOM (`spring-security.version` 6.5.9, `spring-boot-maven-plugin` 3.5.3, `micrometer-java21` 1.15.1), a sign the dependency set is being hand-patched instead of following a supported baseline.

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Supported framework baseline | Spring Boot version in `api/pom.xml` | Latest 4.1.x at implementation time |
| Quality gates hold | `mvn verify` in CI | Green; LINE coverage ≥ `api/.coverage-threshold` (90%) |
| App actually starts | Local boot against Postgres + prod `/actuator/health` | `UP`, Liquibase changelog applied with no new changesets |
| Zero user-visible regression | Prod smoke test after deploy | Magic-link login, MFA, video stream (Range), YouTube import, admin pages all pass |
| Stable in prod | Incidents attributed to the upgrade | 0 within 7 days of deploy |

## User Stories

### Developer / maintainer

- As the maintainer, I want the backend on a Spring Boot line that still receives OSS patches so that security fixes reach prod by a normal version bump.
  - Acceptance: `spring-boot-starter-parent` is 4.1.x; no Spring-managed dependency is pinned to a 3.x-era version.
- As the maintainer, I want Jackson usage on Jackson 3 (`tools.jackson`) so that the codebase does not carry a compatibility shim that will need a second migration later.
  - Acceptance: no `com.fasterxml.jackson.databind|core|dataformat` imports remain in `api/src`; annotations (`com.fasterxml.jackson.annotation`) stay as Jackson 3 keeps that package.
- As the maintainer, I want the dependency list trimmed to what the code uses so that the upgrade surface is smaller.
  - Acceptance: unused dependencies found during the upgrade are removed (e.g. `hypersistence-utils-hibernate-63` has no usage in `api/src/main/java`).

### End user

- As a viewer, I want everything to behave exactly as before so that the upgrade is invisible to me.
  - Acceptance: JSON response shapes (`ResultObjectInfo<T>`, `ResultListInfo<T>`, `ResultErrorInfo`) are byte-compatible for existing endpoints — field names, null handling, date format, enum casing.
- As a user, I want sign-in and video playback to keep working through the deploy so that I am not logged out or interrupted.
  - Acceptance: JWTs issued before the deploy are still accepted after it; video Range requests return the same status codes and headers.

## Requirements

### Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | Bump `spring-boot-starter-parent` to the latest 4.1.x | Must Have | Remove the `spring-security.version` override; let the BOM manage Security 7 |
| FR-2 | Align non-BOM dependencies with Boot 4.1 | Must Have | springdoc-openapi 3.x, `spring-boot-maven-plugin` from parent (drop the 3.5.3 pin), micrometer from BOM, Liquibase from BOM, jjwt version compatible with Jackson 3 |
| FR-3 | Migrate Jackson 2 → Jackson 3 in main and test code | Must Have | `ObjectMapper` → `JsonMapper`/`tools.jackson.*`; `jjwt-jackson` replaced or reconfigured if it only supports Jackson 2 |
| FR-4 | Replace Boot 4 modular auto-config gaps | Must Have | Add the dedicated starters Boot 4 now needs (e.g. Liquibase, mail, actuator) so nothing silently stops auto-configuring |
| FR-5 | Fix compile/test breakages from Spring Framework 7, Security 7, Hibernate 7 | Must Have | Only the changes needed to compile and keep behavior; no refactor beyond that |
| FR-6 | Keep JSON wire format identical | Must Have | Jackson 3 changed some defaults (e.g. date handling, `FAIL_ON_*`); configure explicitly to match 3.5 output |
| FR-7 | Remove unused dependencies uncovered by the upgrade | Should Have | `hypersistence-utils-hibernate-63` confirmed unused; `spring-boot-properties-migrator` kept only during migration, then removed |
| FR-8 | Update `.claude/rules/tech-strategy.md` "Spring Boot 3.x" → "Spring Boot 4.x" | Must Have | Single source of truth for stack |
| FR-9 | `BeanConstructorTest` and a local boot pass | Must Have | Green CI does not prove the app starts (no `@SpringBootTest`) |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Performance | Startup time and p95 latency of key endpoints not worse than 3.5 baseline by > 10% |
| NFR-2 | Resource limits | Fits current limits: 500m CPU, 600Mi memory; runs on the 1 vCPU prod VM with existing `startServer.sh` JVM flags |
| NFR-3 | Availability | Rollback to the previous 3.5 image/JAR possible within 10 minutes |
| NFR-4 | Security | No new HIGH/CRITICAL CVEs vs 3.5.0 in a dependency scan; security config (CORS methods, JWT filter, public endpoints) unchanged in effect |
| NFR-5 | Compatibility | Java 24 with `--enable-preview` keeps working; Postgres schema unchanged (no new Liquibase changesets) |

## Scope

### In Scope

- Spring Boot 3.5.0 → 4.1.x and everything it transitively requires (Framework 7, Security 7, Hibernate 7 / JPA 3.2, Jakarta EE 11 APIs).
- Jackson 2 → Jackson 3 migration across `api/src/main` and `api/src/test`.
- Version alignment of springdoc, jjwt, micrometer, Liquibase, Maven plugins.
- Removing dependencies that are unused or obsolete after the bump.
- `tech-strategy.md` update.
- Local boot verification, CI, prod deploy, prod smoke test.

### Out of Scope

- Java 24 → 25 LTS (separate follow-up; Java 24 itself is past EOL).
- Refactoring not required to compile or keep behavior.
- Adopting new Boot 4 features (API versioning, JSpecify null-safety, HTTP service clients, etc.).
- Frontend (`webapp/`) changes.
- Database schema changes.

## Dependencies

| Dependency | Owner | Status | Risk |
|------------|-------|--------|------|
| Spring Boot 4.1.x GA | Spring team | Released | Low |
| springdoc-openapi 3.x (Boot 4 support) | springdoc | Released | Low |
| jjwt Jackson 3 support (`jjwt-jackson` or alternative serializer) | jjwt | To verify | Med |
| Hibernate 7 compatibility of entities / queries | Owner | To verify | Med |
| Liquibase version managed by Boot 4.1 vs current 4.32.0 changelogs | Owner | To verify | Low |
| `ff-java-server-sdk`, Google API clients — Jackson 2 transitive deps | Third parties | To verify | Med |
| Prod VM deploy (`startServer.sh`, JVM flags) + Helm chart | Owner | Existing | Med |

> **Assumption:** Third-party SDKs that bundle Jackson 2 (Google API client, ff-java-server-sdk) can keep Jackson 2 on the classpath side by side with Jackson 3 (different packages), so they do not block the migration.

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Jackson 3 default changes alter JSON output (dates, nulls, enum names, unknown-property handling) | H | H | Snapshot tests of key DTO responses on 3.5 before upgrading; compare after; set explicit `JsonMapper` config to match |
| JWT signing/parsing breaks (jjwt + Jackson 3) → all users logged out | M | H | Test that a token issued by the 3.5 build verifies on the 4.1 build; keep `JWT_SECRET` and claim format unchanged |
| Boot 4 modular auto-config silently drops a feature (Liquibase, mail, actuator endpoint) | M | H | Local boot against Postgres; check `/actuator/health`, `/actuator/prometheus`, Liquibase log on startup |
| Virtual-thread deadlock / class-init stall on the 1 vCPU VM (seen 2026-10-15 and 2026-10-24) | M | H | Keep existing `startServer.sh` JVM flags; watch startup and first requests after deploy; rollback ready |
| Hibernate 7 query or mapping behavior change | M | M | Full repository test suite; smoke the main read paths in prod |
| Security 7 changes default filter chain / CORS behavior | M | H | Diff effective security config; test preflight for PATCH/PUT (`setAllowedMethods`) |
| Postgres cached-plan errors during rolling deploy | L | M | No schema change in this upgrade; `prepareThreshold=0` already the known fix |
| Coverage drops below 90% due to deleted/changed code | L | M | Add tests for any new config class; run `mvn verify` before commit |

## Open Questions

- [ ] Does the latest jjwt release support Jackson 3, or do we switch to `jjwt-gson` / a custom serializer?
- [ ] Should the upgrade land on a short-lived branch + PR instead of `main`, given CI deploys from `main` and the diff is large?
- [ ] Is a Trivy dependency scan (currently `if: false` in `funnyapp-ci.yml`) worth re-enabling as part of this to verify NFR-4?
- [ ] Which exact JSON responses form the snapshot baseline (list of endpoints)?

## Appendix

### Mockups/Wireframes

N/A — backend-only change, no UI.

### Research

- [Spring Boot 3.5.16 available now (spring.io)](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/) — last OSS release of 3.5.x
- [Spring Boot 3.5 is officially end of life (HeroDevs)](https://www.herodevs.com/blog-posts/spring-boot-3-5-is-officially-end-of-life-here-is-what-that-means-for-teams-still-running-it)
- [CVE-2026-22729 / CVE-2026-22730 and the Spring Boot 3.5 EOL crunch (HeroDevs)](https://www.herodevs.com/blog-posts/cve-2026-22729-cve-2026-22730-and-the-spring-boot-3-5-eol-crunch-facing-spring-ai-teams)
- Spring Boot 4.0 / 4.1 migration guides (to be read via Context7 during ADR/spec)

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | 2026-10-28 | Approved |
| Engineering | nguyenhuuca | 2026-10-28 | Approved |
| Design | N/A | | N/A |

---

## Next Steps & Handoffs

After PRD approval:

1. [ ] **Architect Review**: Jackson 3 strategy, jjwt serializer, rollout/rollback
   - Trigger: `/architect`
   - Output: ADR (`docs/adr/0033-spring-boot-4-upgrade.md`)

2. [ ] **UI/UX Designer**: N/A — no UI change

3. [x] **Spec + sequence diagrams**: `/spec` → `docs/specs/spec-spring-boot-4-upgrade.md`, `docs/sequence/sequence-spring-boot-4-upgrade.md`

4. [ ] **Engineering Estimate**: Implementation Plan (`docs/plans/plan-spring-boot-4-upgrade.md`)

5. [ ] **Track it**: add the feature row to `docs/tracking.md`

**Related Artifacts**:
- ADR: [ADR-0033 Spring Boot 4.1 upgrade](../adr/0033-spring-boot-4-upgrade.md)
- Design Spec: N/A
- Implementation Plan: TBD

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-10-28 | nguyenhuuca | Initial draft |
