# Feature Specification: Spring Boot 4.1 Upgrade

<!--
Feature Specification
Filename: docs/specs/spec-spring-boot-4-upgrade.md
Owner: nguyenhuuca
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
-->

## Metadata

**Status:** Done
**Author:** nguyenhuuca
**Date:** 2026-10-28
**Related PRD:** [PRD-spring-boot-4-upgrade](../prd/PRD-spring-boot-4-upgrade.md)
**Related ADR:** [ADR-0033 Spring Boot 4.1 with a direct Jackson 3 migration](../adr/0033-spring-boot-4-upgrade.md)
**Sequence diagrams:** [sequence-spring-boot-4-upgrade](../sequence/sequence-spring-boot-4-upgrade.md)

---

## Overview

The backend (`api/`) moves from Spring Boot 3.5.0 to the latest 4.1.x on Java 24. All Jackson API usage moves to Jackson 3 (`tools.jackson`), and jjwt moves to the `jjwt-orgjson` JSON backend. Every HTTP endpoint, JWT, database table and scheduled job behaves the same for clients, apart from the two accepted differences in Rule 2 (property order) and Rule 4 (trailing tokens).

---

## Business Rules

### Rule 1 — Supported baseline

`spring-boot-starter-parent` is the latest 4.1.x GA at implementation time.

These must not be pinned to a version the Boot 4.1 BOM already manages:
- Spring Security
- the Spring Boot Maven plugin
- Micrometer (including `micrometer-java21`)
- Liquibase
- Jackson

Java stays on `24` with `--enable-preview`.

### Rule 2 — JSON responses are semantically unchanged

For every response body (success wrappers `ResultObjectInfo<T>` / `ResultListInfo<T>`, `ResultErrorInfo`, the `UnauthorizedException` body, SSE payloads), the 4.1 output must equal the 3.5 output as a JSON value:
- the same property names
- the same values and value types
- `null` present or absent in the same places
- the same `Instant` / date string format

**Property order may change.** Jackson 3 sorts properties alphabetically by default, and that is accepted.

### Rule 3 — Lenient request reading is kept where clients may rely on it

Request and external-payload deserialization must keep these 3.5 behaviors, for both the Spring MVC mapper and every mapper the code builds itself:
- a JSON `null` for a primitive field (`int`, `long`, `boolean` …) maps to the type's default (`0`, `false`) and does **not** fail (`FAIL_ON_NULL_FOR_PRIMITIVES = false`);
- unknown properties are ignored and do **not** fail (`FAIL_ON_UNKNOWN_PROPERTIES = false`).

### Rule 4 — Trailing tokens follow the Jackson 3 default

A request body with extra content after the root JSON value (e.g. `{"a":1} x`) is rejected the same way as any other malformed JSON body (400). This is the one accepted request-side behavior change.

### Rule 5 — Existing JWTs stay valid

A token issued by the 3.5 build, still within its `exp` and signed with the same `JWT_SECRET`, must be accepted by the 4.1 build.
- It must produce the same `UserDetailDto`: `id`, `email`, `role`, `permissions`, `credentialsVersion`.
- A token without a `cv` claim still maps to `credentialsVersion = 0`.

### Rule 6 — No schema change

The upgrade adds no Liquibase changeset. On startup against a database migrated by the 3.5 build, Liquibase must report zero pending changesets.

---

## Functional Requirements

### FR-1: Dependency baseline

- `api/pom.xml` parent must be `spring-boot-starter-parent` 4.1.x.
- The `spring-security.version` property must be removed.
- The explicit `<version>` on `spring-boot-maven-plugin`, `micrometer-java21` and `liquibase-core` must be removed, so the BOM manages them.
- `springdoc-openapi-starter-webmvc-ui` must use the springdoc 3.x line that supports Boot 4.
- `hypersistence-utils-hibernate-63` must be removed (no usage in `api/src/main/java`).
- `spring-boot-properties-migrator` may be present while migrating and must be removed before the final commit.

### FR-2: Boot 4 modular starters

Every feature auto-configured on 3.5 must still auto-configure on 4.1. Add the Boot 4 starter or module where auto-config moved out of the core:
- Liquibase (`spring.liquibase.*` in `application.yaml`)
- Mail (`spring-boot-starter-mail`)
- Actuator health and Prometheus
- Validation
- Security
- Data JPA

### FR-3: Jackson 3 migration

- Every import of `com.fasterxml.jackson.databind|core|dataformat` in `api/src/main` and `api/src/test` must move to its `tools.jackson.*` equivalent.
- `com.fasterxml.jackson.annotation.*` imports stay unchanged.
- `jackson-dataformat-yaml` moves to its Jackson 3 artifact if the code uses YAML through Jackson. Otherwise it must be removed.
- Code that caught `JsonProcessingException` (checked) must catch `tools.jackson.core.JacksonException` (unchecked) where the old catch handled a real failure, and keep the same fallback behavior.
- `throws JsonProcessingException` clauses that no longer apply must be removed.
- `MaskingUtils` must drop `JavaTimeModule` registration, because Jackson 3 supports java.time natively, and must produce the same masked output.

### FR-4: One mapper configuration

The JSON mapper used by Spring MVC must be configured once, through `spring.jackson.*` properties or a `JsonMapperBuilderCustomizer`, with:
- `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES = false`
- `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false`
- whatever date/time settings are needed to keep `Instant` output identical to 3.5 (Rule 2)

The four classes that call `new ObjectMapper()` must get a mapper with the same settings, either injected or built through one shared factory:
- `aop/MaskingUtils`
- `client/YtDlpClientImpl`
- `exception/UnauthorizedException`
- `service/impl/ShareServiceImpl`

The other `ObjectMapper` users must do the same:
- `JdkGitHubIssueClient`
- `ChatGptServiceImpl`
- `GitHubWebhookServiceImpl`
- the three notification listeners

`UnauthorizedException.toJson()` must still emit `{"status":"FAILED","error":{…}}`, with `Error.timestamp` in pattern `dd-MM-yyyy hh:mm:ss`.

### FR-5: JWT JSON backend

- `jjwt-jackson` must be replaced by `jjwt-orgjson`, keeping `jjwt-api` and `jjwt-impl` on the same version.
- `JwtProvider` must not change its claim names, signing key derivation, or expiry logic.
- `JwtProvider.warmUp()` must keep warming the JSON reader. After the swap that means the org.json-backed reader/writer, so class init still happens on the main thread before Tomcat accepts requests (prod deadlock fix 2026-10-15 / 2026-10-24).

### FR-6: Framework 7 / Security 7 / Hibernate 7 compile fixes

Code changes in this area are limited to what is needed to compile and to keep behavior:
- removed or renamed APIs
- the new `SecurityFilterChain` DSL requirements
- Hibernate 7 query and type changes

The effective security config must stay the same:
- public endpoints
- JWT filter order
- CORS allowed origins and methods, including PATCH/PUT in `WebSecurityConfig.setAllowedMethods`
- the stateless session policy

### FR-7: Serialization baseline tests

Before the version bump, a test class (e.g. `JsonContractTest`) must capture the JSON produced on 3.5 for:
- one representative response DTO for each of the 20 `@RestController` classes in `web/`;
- `ResultObjectInfo`, `ResultListInfo`, `ResultErrorInfo`, `Error`, and the `UnauthorizedException.toJson()` body.

Each DTO is filled with fixed values (including `Instant` fields and `null`s for `@JsonInclude(NON_NULL)` fields). The expected JSON is stored as test fixtures (`src/test/resources/json-contract/*.json`).

After the bump, the same test must pass when compared semantically, ignoring property order (Rule 2). Fixtures must not be edited during the upgrade commit.

### FR-8: Tech strategy update

In `.claude/rules/tech-strategy.md`, change *Spring Boot 3.x* to *Spring Boot 4.x* (Java Golden Path table) and note Jackson 3 under the backend stack. In `CLAUDE.md`, change the project overview "Spring Boot 3.x" to "4.x".

---

## API Changes

### New Endpoints

None.

### Modified Endpoints

None. Paths under `/v1/funny-app`, HTTP methods, status codes, headers (including `Range` / `Content-Range` / `Accept-Ranges` on `VideoStreamController`) and response shapes are unchanged.

Only these differences are allowed:

| Area | Before (3.5) | After (4.1) | Allowed by |
|------|--------------|-------------|------------|
| JSON property order | declaration order | alphabetical (Jackson 3 default) | Rule 2 |
| Request body with trailing tokens | accepted, extra ignored | 400 malformed body | Rule 4 |

Error responses keep the current format (`ResultErrorInfo` / `Error`, produced by the existing `@ControllerAdvice`). No new error code.

---

## Database Changes

### New Tables

None.

### Modified Tables

None.

### No Changes

No migration file. Liquibase runs the existing `db/changelog/db.changelog-master.yaml` and must find no pending changesets (Rule 6). Hibernate 7 must keep `ddl-auto` off / `validate` as today; `create` and `update` are prohibited by the tech strategy.

---

## Security Requirements

### Authentication

- JWT Bearer, unchanged. Tokens issued before the deploy stay valid (Rule 5).
- Magic-link, password login and TOTP MFA flows are unchanged.

### Authorization

Role and permission checks are unchanged. Spring Security 7 must enforce the same rules on the same paths. Verify this with the existing security tests plus FR-6.

### Data Validation

- Bean Validation (Jakarta Validation from Boot 4.1) and `Contract` / `ContractDSL` behave the same.
- Request JSON reading follows Rules 3 and 4.

### Sensitive Data

`MaskingUtils` must mask the same fields as before (FR-3), and logs must not start printing secrets or tokens. The dependency diff must add no HIGH or CRITICAL CVE compared with 3.5.0 (PRD NFR-4).

---

## Caching Impact

None. The Guava caches (`VideoCacheImpl`, `ChunkIndexCacheImpl`, `StatsCacheImpl`, `MFASessionStoreImpl`) are not serialized with Jackson, and their keys and values do not change.

> **Assumption:** none of the Guava caches store Jackson-serialized strings; confirm with a grep during implementation.

---

## Frontend Changes

### New Routes

None.

### Modified Components

None. `webapp/` reads JSON by property name, so the property-order change in Rule 2 does not affect it.

### State Management

None. React Query keys and cached shapes are unchanged.

---

## Event / Job Changes

### Domain Events

The after-commit notification listeners (`NotificationEventListener`, `ReportNotificationListener`, `SuggestionNotificationListener`) keep:
- the same event types
- the same SSE / email payloads (Rule 2)

Their mapper follows FR-4.

### Scheduled Jobs

`AppScheduler` and the `DynamicJobScheduler` jobs (ADR-0023) keep:
- the same cron expressions
- the same behavior

No job is added or removed.

---

## Non-Functional Requirements

### Performance

| Operation | Target |
|-----------|--------|
| JVM startup to `Started` log, same machine, 3.5 vs 4.1 | ≤ +10% |
| p95 latency of `GET` video list, video stream first chunk, user info | ≤ +10% vs 3.5 baseline |
| Heap at idle after startup | fits `-Xmx256m` with no OOM; K8s limit 600Mi unchanged |

### Availability

- Rollback means redeploying the previous 3.5 artifact (JAR on the VM / image tag in Helm) within 10 minutes. No DB step is needed (Rule 6).
- After deploy, `/actuator/health` must be `UP` within the usual startup time, and the first authenticated requests must not hang (FR-5 warm-up).

### Scalability

Unchanged: one replica (HPA 1–1), virtual threads, HikariCP pool size from `DB_POOL_SIZE`.

---

## Edge Cases

### EC-1: Token issued before the upgrade

A JWT created by 3.5 with claims `id`, `email`, `permissions`, `role`, `cv` → 4.1 `verifyToken` returns the same `UserDetailDto`. Numeric claims parsed by org.json (`Integer` / `Long` / `BigDecimal`) must still convert:
- `id` → `Long`
- `permissions` → `int` via `Integer.parseInt(String.valueOf(…))`
- `cv` → `int` via `Number.intValue()`

### EC-2: Token without `cv`

A pre-PW-4 token with no `cv` claim → `credentialsVersion = 0`, as today.

### EC-3: Expired or tampered token

- Expired → `UnauthorizedException("TOKEN_EXPIRED", 602)`.
- Bad signature or malformed → `UnauthorizedException("TOKEN_INVALID", 601)`.

The response body format is the same as before (FR-4).

### EC-4: `null` sent for a primitive field

e.g. `{"duration": null}` into a `long` field → value `0`, no error (Rule 3).

### EC-5: Unknown property

- In a client request → ignored (Rule 3).
- In an external payload (YouTube API, yt-dlp `--dump-json`, GitHub webhook, ChatGPT response) → ignored as well, so a provider adding fields does not break parsing.

### EC-6: Trailing tokens in a request body

`{"a":1} garbage` → 400 with the same error body as other malformed JSON (Rule 4).

### EC-7: `Instant` fields

The 38 `Instant` fields in DTOs serialize to the same ISO-8601 strings as on 3.5, not numeric timestamps. `null` `Instant` follows the field's `@JsonInclude` as before.

### EC-8: `Error.timestamp`

It keeps the pattern `dd-MM-yyyy hh:mm:ss` (12-hour `hh`, unchanged on purpose: changing it would alter the wire format).

### EC-9: First requests after deploy on the 1 vCPU VM

A burst of authenticated requests right after startup must not deadlock. The org.json JSON reader and the jjwt classes are initialised in `JwtProvider.warmUp()` on the main thread before Tomcat accepts traffic (FR-5).

### EC-10: Third-party Jackson 2 on the classpath

The Google API client and `ff-java-server-sdk` may still pull `com.fasterxml.jackson.databind` transitively. Our code must not import it (FR-3), and Spring MVC must use only the Jackson 3 mapper. A Jackson 2 jar on the classpath is allowed.

---

## Acceptance Criteria

- [ ] `api/pom.xml` parent is Spring Boot 4.1.x and contains none of the removed pins or dependencies (FR-1)
- [ ] `grep -rE "com\.fasterxml\.jackson\.(databind|core|dataformat)" api/src` returns nothing (FR-3)
- [ ] `JsonContractTest` fixtures were committed **before** the version bump and pass unchanged after it, compared semantically (FR-7, Rule 2)
- [ ] Unit tests cover EC-1 … EC-8 (JWT from a 3.5 fixture token, missing `cv`, expired/tampered, null primitive, unknown property, trailing tokens, `Instant`, `Error.timestamp`)
- [ ] `mvn verify` green; LINE coverage ≥ `api/.coverage-threshold` (90%); `BeanConstructorTest` passes
- [ ] Local boot against Postgres: Liquibase reports no pending changesets; `/actuator/health` = `UP`; `/actuator/prometheus` serves metrics; Swagger UI loads on the dev profile
- [ ] CORS preflight for PATCH and PUT from the webapp origin returns the same allow headers as 3.5
- [ ] Prod smoke test after deploy: magic-link login, password login + MFA, a user logged in before the deploy stays logged in, video list and Range streaming, YouTube import, admin pages, notifications SSE
- [ ] `tech-strategy.md` and `CLAUDE.md` say Spring Boot 4.x (FR-8)
- [ ] No incident attributed to the upgrade within 7 days of the prod deploy

---

## Open Questions

None blocking. Resolved in this spec:
- Wire-format strictness → semantic equality, order may change (Rule 2).
- Request leniency → keep null-for-primitive and unknown-property leniency; trailing tokens strict (Rules 3–4).
- Snapshot baseline → DTO serialization tests per controller, run in CI (FR-7).
- jjwt backend → `jjwt-orgjson` (ADR-0033). If a Jackson 3 `jjwt-jackson` is available at implementation time, either is acceptable; record the choice in the ADR changelog.

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-10-28 | nguyenhuuca | Initial draft |
