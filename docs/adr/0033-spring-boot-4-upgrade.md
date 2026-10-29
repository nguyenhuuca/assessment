# ADR-0033: Upgrade to Spring Boot 4.1 with a Direct Jackson 3 Migration

## Metadata

**Status:** Accepted · **Date:** 2026-10-28 · **Deciders:** nguyenhuuca · **Tags:** api, platform, security, dependencies
**Related PRD:** [PRD-spring-boot-4-upgrade](../prd/PRD-spring-boot-4-upgrade.md) · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ⚠️ Changes a Golden Path row — *Framework: Spring Boot 3.x* becomes *Spring Boot 4.x* (`.claude/rules/tech-strategy.md`). Every other choice (JDK 24 preview, JPA/Hibernate, Liquibase, Spring Security + JWT, Swagger/OpenAPI, Jacoco) stays; only their major versions move with the Boot BOM.

---

## Context

- **Spring Boot 3.5.x is past OSS end of life.**
  - OSS support ended on 2026-06-30. 3.5.16 was the last OSS release.
  - `api/pom.xml:17` is on **3.5.0**, 16 patches behind, and misses fixes such as CVE-2026-22729/22730.
  - The repo is public and the app serves production.
- **Boot 4.x is the only supported OSS line.**
  - 4.0 OSS support is short-lived (end of 2026), so the target is **4.1.x**.
  - Boot 4 brings Spring Framework 7, Spring Security 7, Hibernate 7, Jakarta EE 11, a modular auto-config split, and **Jackson 3** (`tools.jackson`) as the default.
  - Jackson 2 support lives in the deprecated `spring-boot-jackson2` module (`spring.jackson2.*` properties) and is "provided purely to ease the migration … will be removed in a future Spring Boot 4.x release" (Spring Boot reference, JSON chapter).
- **Jackson footprint in this codebase:**
  - 13 main files and 12 test files import `com.fasterxml.jackson.databind|core|dataformat`, e.g. `aop/MaskingUtils.java`, `client/YouTubeApiClientImpl.java`, `client/YtDlpClientImpl.java`, `service/impl/ChatGptServiceImpl.java`, `service/impl/GitHubWebhookServiceImpl.java`, the notification listeners and `exception/LowerCaseClassNameResolver.java`.
  - 7 more main files use only `com.fasterxml.jackson.annotation`. Jackson 3 keeps that package (its BOM explicitly permits `jackson-annotations`), so those files do not change.
  - 4 classes construct their own `new ObjectMapper()`; there is no shared mapper bean.
  - `jackson-dataformat-yaml` is a direct dependency.
- **JWT serialization:**
  - `jjwt` 0.12.6 discovers `jjwt-jackson` at runtime through ServiceLoader. The code never references `JacksonSerializer`/`JacksonDeserializer`.
  - `jjwt-jackson` is built on Jackson 2, and no Jackson 3 variant could be confirmed (latest release seen: 0.13.0).
  - The project already depends on `org.json:json` (`json-lib.version` 20231013), which jjwt supports through `jjwt-orgjson`.
- **Pins that fight the BOM:**
  - `spring-security.version` 6.5.9
  - `spring-boot-maven-plugin` 3.5.3
  - `micrometer-java21` 1.15.1
  - `liquibase-core` 4.32.0
  - `springdoc-openapi` 2.7.0
  - `hypersistence-utils-hibernate-63`, which has no usage in `api/src/main/java`
- **Operational constraints:**
  - Prod is a 1 vCPU VM with `-Xmx256m`.
  - It runs on virtual threads and has had two class-init / loader deadlocks (2026-10-15, 2026-10-24; JVM flags in `startServer.sh`).
  - CI deploys from `main`.
  - There is no `@SpringBootTest`, so green CI does not prove the app boots (`BeanConstructorTest` guards constructors only).
- **Scope decided in the PRD:**
  - Java stays on 24.
  - No refactors beyond what compiling and keeping behavior require.
  - No new Boot 4 features.
  - No frontend change.

---

## Decision Drivers

1. **Get back on a patched line**, and stay there for the longest OSS window available.
2. **Zero user-visible change.** The JSON wire format of `ResultObjectInfo<T>` / `ResultListInfo<T>` / `ResultErrorInfo` and JWT validity across the deploy must hold.
3. **Do not buy a second migration.** Anything deprecated-for-removal in Boot 4 (the Jackson 2 module) would have to be redone inside the 4.x line.
4. **Keep the diff reviewable and reversible.** One person reviews it; rollback must be quick on a single VM.
5. **Do not add startup or class-loading risk on the 1 vCPU VM.** A larger classpath means more class init at boot, and that has already caused hangs here.

---

## Considered Options

### Option 1: Stay on 3.5 and bump to 3.5.16

Patch to the last OSS release and stop.

| Pros | Cons |
|------|------|
| Trivial diff, near-zero regression risk | Still EOL: no more OSS patches after 3.5.16, the next CVE stays open |
| Fixes known CVEs today | Only delays the Boot 4 migration and makes it larger later |
| | Commercial support is the only way to stay patched |

### Option 2: Boot 4.1 with the Jackson 2 compatibility module

Upgrade to 4.1 but add `spring-boot-jackson2`, keep `com.fasterxml.jackson` code and `spring.jackson2.*` config, and migrate Jackson later.

| Pros | Cons |
|------|------|
| Smallest framework-upgrade diff; Jackson code untouched | Module is deprecated for removal within 4.x, so a second migration is guaranteed |
| JSON output risk deferred | Jackson 2 and 3 both on the classpath (Spring MVC uses 3, the code uses 2): two mappers with different defaults, easy to diverge |
| | More classes to load at startup on the 1 vCPU VM |

### Option 3: Boot 4.1 with a direct Jackson 3 migration *(chosen)*

Upgrade to 4.1, move all Jackson API usage to `tools.jackson` (`JsonMapper`, `JacksonException`, YAML via the Jackson 3 dataformat), keep annotations as they are, and switch jjwt to a JSON backend that does not need Jackson 2.

| Pros | Cons |
|------|------|
| One migration, aligned with Boot's default; nothing deprecated left behind | Larger diff: ~25 files plus config |
| Single JSON stack used by Spring MVC and by our code, so one set of defaults | Jackson 3 changes some defaults; JSON output must be proven unchanged |
| Longest support runway (4.1 OSS window, no removal-pending modules) | jjwt backend change touches the auth path |

### Option 4: Boot 4.0.x as an intermediate step

Go to 4.0 first, then to 4.1.

| Pros | Cons |
|------|------|
| Smaller step if 4.1 had extra breaking changes | 4.0 OSS support ends around the end of 2026, so it is outdated on arrival |
| | Two upgrade cycles for one person; no added safety, since 4.0→4.1 is a minor bump |

---

## Decision Outcome

**Chosen Option:** Option 3 — Boot 4.1 with a direct Jackson 3 migration.

**Rationale:**
- **Drivers 1 and 3** rule out Options 1 and 4: they leave the app on a line that is EOL now or will be within weeks.
- **Driver 3 rules out Option 2.** Option 2 meets Driver 4 better, but the Jackson 2 module is explicitly temporary.
- **Option 2 also creates a correctness hazard** that works against Driver 2. Spring MVC would serialize responses with Jackson 3 while `MaskingUtils`, the clients and the listeners would still use Jackson 2, so two mappers with different defaults would sit in one process.
- **Option 3 keeps one JSON stack.** That makes "the wire format did not change" a single thing to prove, not two.

**Sub-decisions that are part of this choice:**

1. **JWT JSON backend → `jjwt-orgjson`.**
   - `org.json` is already a project dependency.
   - It removes the Jackson 2 dependency from the auth path without writing custom serializer code.
   - Tokens are standard JWS compact strings, so the JSON library does not affect signatures or claim names, and tokens issued by the 3.5 build must still verify.
   - If a Jackson 3 `jjwt-jackson` turns out to exist at implementation time, it is an acceptable equal alternative. The plan picks one and records it here.
2. **Wire format is pinned explicitly, not inherited.**
   - Jackson 3 default changes that would alter output are set back explicitly through `spring.jackson.*` or a `JsonMapperBuilderCustomizer`, so the output matches 3.5.
   - The four ad-hoc `new ObjectMapper()` sites use a mapper with the same settings (built the same way or injected), so they do not drift from MVC.
3. **Let the Boot BOM own versions.**
   - Remove the pins on Security, the Boot Maven plugin, micrometer and Liquibase.
   - Move springdoc to the 3.x line that supports Boot 4.
   - Drop `hypersistence-utils-hibernate-63`, which is unused.
   - Keep `spring-boot-properties-migrator` only while migrating, then remove it.
4. **Delivery: a single atomic upgrade, with rollback by redeploying the previous image/JAR.**
   - No schema change is part of this upgrade, so the 3.5 build can run against the same database after a rollback.

### Quantified Impact

| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| Spring Boot line | 3.5.0 (OSS EOL 2026-06-30) | 4.1.x (supported) | Patches resume |
| Missed patch releases | 16 | 0 | |
| Jackson stacks used by our code | 1 (Jackson 2) | 1 (Jackson 3) | Option 2 would have made it 2 |
| Files touched for Jackson | — | ~25 (13 main, 12 test) | Annotation-only files unchanged |
| Version pins overriding the BOM | 5 | 0 | Plus 1 unused dependency removed |
| Startup time / heap | baseline | ≤ +10% (target) | Measured before/after on the same machine; must still fit `-Xmx256m` |

---

## Consequences

**Positive:**
- Back on an OSS-supported Spring line; future CVE fixes arrive as patch bumps of 4.1.x.
- One JSON library for responses and internal use; no deprecated module waiting to be removed.
- `pom.xml` follows the Boot BOM again, so future upgrades become version bumps instead of hand-tuning.
- The auth path no longer depends on Jackson at all.

**Negative:**
- A large, cross-cutting diff in one change; review takes longer and touches auth, AOP masking, external clients and notifications.
- Hibernate 7, Security 7 and Framework 7 may each need small code fixes that are not known until compile/test time.
- Third-party SDKs that still bring Jackson 2 transitively (Google API client, `ff-java-server-sdk`) keep it on the classpath. That is harmless because the packages differ, but the classpath does not shrink as much as it could.

**Risks:**
- **JSON output drift** (dates, nulls, enum casing, unknown properties). *Mitigation:* capture response snapshots of the key endpoints on 3.5 before the bump, assert them after, and pin settings explicitly.
- **Users logged out** if JWT parsing breaks. *Mitigation:* a test verifies a token issued by the 3.5 build with the 4.1 build; `JWT_SECRET` and the claim set stay the same.
- **A feature silently stops auto-configuring** because of Boot 4's modular starters (Liquibase, mail, actuator/Prometheus). *Mitigation:* boot locally against Postgres and check the Liquibase startup log, `/actuator/health` and `/actuator/prometheus`.
- **A virtual-thread or class-init stall on the 1 vCPU VM.** *Mitigation:* keep the `startServer.sh` JVM flags, watch startup and the first requests after deploy, and keep the previous artifact ready to redeploy.
- **Security 7 changes the effective filter chain or CORS.** *Mitigation:* tests for the public endpoints, the JWT filter and preflight on PATCH/PUT (`setAllowedMethods`).

---

## Validation

- [ ] `mvn verify` green with LINE coverage ≥ `api/.coverage-threshold` (90%)
- [ ] App boots locally against Postgres; Liquibase reports no pending changesets; `/actuator/health` is `UP`
- [ ] JSON snapshot tests of key responses are identical before and after the upgrade
- [ ] A JWT issued by the 3.5 build validates on the 4.1 build
- [ ] Prod smoke test after deploy: magic-link login, MFA, video Range streaming, YouTube import, admin pages
- [ ] No incidents attributed to the upgrade within 7 days
- [ ] Security review of the dependency diff (no new HIGH/CRITICAL CVEs)
- [ ] `.claude/rules/tech-strategy.md` updated to Spring Boot 4.x
- [ ] Related plan created: `docs/plans/plan-spring-boot-4-upgrade.md`

---

## Links

- [PRD: Spring Boot 4.1 Upgrade](../prd/PRD-spring-boot-4-upgrade.md)
- [Implementation Plan](../plans/plan-spring-boot-4-upgrade.md) *(to be written)*
- [Spring Boot 3.5.16 available now — last OSS 3.5.x release](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)
- Spring Boot reference, JSON chapter (Jackson 3 default, Jackson 2 deprecated)
- [jjwt](https://github.com/jwtk/jjwt): JSON backends `jjwt-jackson`, `jjwt-gson`, `jjwt-orgjson`

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-10-28 | nguyenhuuca | Initial draft |
| 2026-10-28 | nguyenhuuca | Accepted |
| 2026-10-28 | nguyenhuuca | SB4-2: jjwt 0.13.0 with `jjwt-orgjson` (no Jackson 3 `jjwt-jackson` exists). SB4-3 folded into SB4-4: Jackson 3.1.7 needs `jackson-annotations` 2.21, Boot 3.5.0 pins 2.19.0. Liquibase: accept Boot 4.1.2's 5.0.4 (Community now FSL-1.1-ALv2, source-available; this app is not a competing use), owner decision |
| 2026-10-29 | nguyenhuuca | Implemented and verified on prod (SB4-1..SB4-5); properties-migrator removed |
