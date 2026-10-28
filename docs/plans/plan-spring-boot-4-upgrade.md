# Plan: Spring Boot 4.1 Upgrade

<!--
Implementation Plan
Filename: docs/plans/plan-spring-boot-4-upgrade.md
Owner: Builder (/builder)
-->

## Overview

**Status:** Approved
**Author:** nguyenhuuca
**Date:** 2026-10-28
**Tracking:** `docs/tracking.md` row "Spring Boot 4.1 Upgrade"; `TASKS.md` section "Spring Boot 4.1 upgrade"
**Related PRD:** [PRD-spring-boot-4-upgrade](../prd/PRD-spring-boot-4-upgrade.md)
**Related ADR:** [ADR-0033](../adr/0033-spring-boot-4-upgrade.md)
**Spec:** [spec-spring-boot-4-upgrade](../specs/spec-spring-boot-4-upgrade.md) (cited as Rule n / FR-n / EC-n)
**Sequence:** [sequence-spring-boot-4-upgrade](../sequence/sequence-spring-boot-4-upgrade.md) (flows 1–6)
**Size:** Medium. 5 tasks, about 1,500 LOC including tests. SB4-4 is larger than the usual 400-LOC unit, because a half-done framework bump does not compile (see Key Decisions).

## Objective

Move `api/` from Spring Boot 3.5.0 to **4.1.2**, the latest GA (2026-10-22). With it come:

| Component | Version |
|-----------|---------|
| Spring Framework | 7.0.10 |
| Spring Security | 7.1.2 |
| Hibernate ORM | 7.4.13.Final |
| Jackson | 3.1.7 |
| Liquibase | 5.0.4 |
| Micrometer | 1.17.2 |
| Lombok | 1.18.48 |

Java stays on 24. Every change that can land safely on 3.5 is shipped first and verified in prod, so the final bump commit holds only what cannot exist without Boot 4.

## Scope

### In Scope

- Contract baseline tests captured on 3.5 (spec FR-7).
- jjwt → `jjwt-orgjson` and removal of unused dependencies, on 3.5 (FR-1, FR-5).
- Internal (non-MVC) Jackson code → Jackson 3, on 3.5 if the classpath allows it (FR-3, FR-4).
- Boot 4.1.2 bump with the remaining Jackson 3, Security 7, Hibernate 7 and test-slice fixes (FR-1 … FR-6, FR-8).
- Local boot verification, prod deploy, smoke test, 7-day watch.

### Out of Scope

Same as the PRD:
- Java 25
- refactors that the upgrade does not require
- new Boot 4 features
- `webapp/`
- schema changes

Also out of scope: replacing `RestTemplate`, which is deprecated in Spring 7 but still present.

## Technical Approach

### Architecture Changes

```
Before (3.5.0)                          After (4.1.2)
──────────────                          ─────────────
Spring MVC ── Jackson 2 ObjectMapper    Spring MVC ── Jackson 3 JsonMapper (FR-4 customizer)
our code   ── Jackson 2 (injected       our code   ── same JsonMapper bean / JsonMappers factory
              + 4× new ObjectMapper)
jjwt       ── jjwt-jackson (Jackson 2)  jjwt       ── jjwt-orgjson (org.json, already a dep)
liquibase-core 4.32 (auto-config core)  spring-boot-starter-liquibase (Liquibase 5.0.4)
```

The flows themselves do not change; see the sequence diagrams.

### Key Decisions

| Decision | Rationale |
|----------|-----------|
| Ship the 3.5-compatible parts first (SB4-1 … SB4-3), each merged and deployed alone | Each lands on `main` on its own and lowers the risk of the final bump. A JWT problem shows up in SB4-2 on a known-good framework, not mixed into the bump |
| SB4-4 is one atomic commit (~600 LOC) | Parent POM, Jackson annotations used by MVC, test-slice packages and Security 7 APIs must all change together, or the build breaks. A trunk-based unit cannot be split there |
| Contract fixtures are committed in SB4-1 and never edited later | They are the proof for Rule 2. Editing them in the bump commit would hide a wire-format change |
| SB4-3 has a fallback | Jackson 3.1.7 next to Boot 3.5's Jackson 2.19 needs a newer `jackson-annotations` (shared by both). If `mvn verify` on 3.5 fails because of that, SB4-3's code moves into SB4-4 instead, with no other plan change |
| Target 4.1.2, not 4.1.x "latest" at merge time | Pin an exact version for a reproducible build. A later 4.1.x patch is a separate one-line bump |
| Liquibase 5 | The Boot BOM moves Liquibase 4.32 → 5.0.4. Check its license and changelog compatibility in SB4-4 step 1. If either is a problem, pin `liquibase.version` to the last 4.x that Boot 4.1's auto-config supports and record that in ADR-0033 |

## Implementation Steps

### Phase 1: Baseline on 3.5 (SB4-1)

- [ ] **Step 1.1:** `JsonContractTest`: serialize fixed-value DTOs with the Spring MVC mapper and compare them to the fixtures (lenient order, strict values)
  - Files: `api/src/test/java/com/canhlabs/funnyapp/contract/JsonContractTest.java`, `api/src/test/resources/json-contract/*.json`
  - DTOs (one per controller that returns JSON):
    - `VideoDto`, `WatchHistoryItemDto` (in a `Page`), `AdminVideoDto`, `SuggestionDto`
    - `CommentNode` (with nested replies), `CreateCommentResponse`
    - `UserSettingsDto` (+ `AccountStatusDto`)
    - `NotificationDto`, `UnreadCountDto`
    - `VideoImportJobDto`, `ReportDto`
    - `CollectionDto` (`isDefault` via `@JsonProperty`)
    - `DriveSyncRunDto`, `ReactionSummaryDto`, `ScheduledJobDto`
    - `UserInfoDto` (`@JsonInclude` ALWAYS / NON_NULL; fake jwt), `UserDetailDto` (`credentialsVersion` absent)
  - Wrappers and error bodies:
    - `ResultObjectInfo`, `ResultListInfo`, `ResultErrorInfo` (timestamp fixed or ignored)
    - `Error` through `RestExceptionHandler` (`{"error":{…}}`; timestamp matched by the regex `\d{2}-\d{2}-\d{4} \d{2}:\d{2}:\d{2}`)
    - `UnauthorizedException.toJson()`
  - Other fixtures:
    - The notification `payload` JSON written to the DB (`Map<String,String>`), because it is persisted and must stay readable.
    - One `Page<T>` fixture, to pin the `PageImpl` shape (Spring Data 4 may change it).
  - Assertion: `JSONAssert.assertEquals(expected, actual, JSONCompareMode.NON_EXTENSIBLE)` (already on the classpath through `spring-boot-starter-test`). Never use `LENIENT`, which would accept extra fields.
- [ ] **Step 1.2:** Legacy JWT fixture: a token signed with the test secret, `exp` = 2099, claims `id`, `email`, `role`, `permissions`, `cv` (and a second one without `cv`), generated once on 3.5 and stored as a fixture string
  - Files: `filter/JwtProviderTest.java` (new cases), `src/test/resources/json-contract/legacy-jwt.txt`
  - Covers EC-1 and EC-2.
- [ ] **Step 1.3:** Request-leniency tests on the MVC mapper: null → primitive is OK, unknown property is ignored (Rule 3, EC-4, EC-5). Add the trailing-token case with the expectation `@Disabled("enabled in SB4-4: Jackson 3 rejects, Rule 4")`.

### Phase 2: Pre-bump changes on 3.5 (SB4-2, SB4-3)

- [ ] **Step 2.1 (SB4-2):** jjwt `0.12.6` → `0.13.0`, `jjwt-jackson` → `jjwt-orgjson`
  - Files: `api/pom.xml`, `filter/JwtProvider.java` only if needed (ServiceLoader picks the backend up). `JwtProviderTest`: warm-up still signs and parses once; legacy tokens from step 1.2 verify; numeric claims from org.json (`Integer`/`Long`/`BigDecimal`) convert (EC-1).
- [ ] **Step 2.2 (SB4-2):** Remove the unused `hypersistence-utils-hibernate-63` and `jackson-dataformat-yaml` (no imports in `src/`).
- [ ] **Step 2.3 (SB4-3):** Add `tools.jackson.core:jackson-databind:3.1.7` and a `JsonMappers` holder. It provides one `JsonMapper` with the FR-4 settings:
  - `FAIL_ON_NULL_FOR_PRIMITIVES=false`
  - `FAIL_ON_UNKNOWN_PROPERTIES=false`
  - `WRITE_DATES_AS_TIMESTAMPS=false`

  The holder is exposed as a bean for injected sites. Static sites (`MaskingUtils`, `YtDlpClientImpl`, `ShareServiceImpl`) call a package-visible factory method `JsonMappers.create()`, so their tests can build the same mapper without Spring.
  - On Boot 3.5 a Jackson 3 `JsonMapper` bean is a different type from Jackson 2's `ObjectMapper`. Boot's `JacksonAutoConfiguration` (which only backs off for an `ObjectMapper` bean) and Spring MVC ignore it, so SB4-3 cannot change the HTTP wire format.
  - Files: `config/JsonMappers.java` (new), `config/ConfigBean.java`
- [ ] **Step 2.4 (SB4-3):** Move internal Jackson usage to `tools.jackson`. Catch blocks keep the same fallback (the inventory is in the explorer notes below).
  - **Static or per-call mappers:**
    - `aop/MaskingUtils` (drop `JavaTimeModule`; keep the `"<unserializable …>"` fallback)
    - `client/YtDlpClientImpl` (`catch IOException` → `catch JacksonException`, still `ImportException(EXTRACTOR_ERROR)`)
    - `service/impl/ShareServiceImpl` (still `error.video.infoUnavailable`)
  - **Injected mappers:**
    - `client/JdkGitHubIssueClient` (still `GitHubException` / `IssueState.UNKNOWN`)
    - `service/impl/GitHubWebhookServiceImpl` (still `IllegalArgumentException("body is not JSON")`)
    - `service/impl/ChatGptServiceImpl`
    - `service/notification/NotificationEventListener`, `ReportNotificationListener`, `SuggestionNotificationListener` (the `payload` fixture from step 1.1 must still pass)
  - **Stay on Jackson 2 until SB4-4:**
    - `exception/Error`, `exception/LowerCaseClassNameResolver`, `exception/UnauthorizedException` (MVC / filter path)
    - `client/YouTubeApiClientImpl` (`JsonNode` through the `RestTemplate` Jackson 2 converter)
  - **Tests:** the matching tests (`JdkGitHubIssueClientTest`, `ChatGptServiceImplTest`, `GitHubWebhookServiceImplTest`, the three listener tests, `UserSettingsServiceImplTest`).
  - **Fallback trigger** (any one of these, checked before committing SB4-3):
    1. `mvn -f api/pom.xml dependency:tree -Dincludes=com.fasterxml.jackson.core:jackson-annotations` shows more than one version, or a version different from Boot 3.5.0's managed one;
    2. any test fails with a Jackson linkage error (`NoSuchMethodError`, `NoClassDefFoundError`, `ClassNotFoundException` naming a `com.fasterxml.jackson` or `tools.jackson` class);
    3. making it work needs a `jackson-bom` / `jackson-annotations` version override that changes Boot 3.5's managed Jackson 2 versions.
  - **On trigger:** discard all SB4-3 changes (`git restore`/`git clean` on the SB4-3 files; nothing is committed), mark the SB4-3 row "folded into SB4-4" in this table and in `TASKS.md` (with the trigger that fired), and carry steps 2.3–2.4 into SB4-4. No half state lands on `main`.

### Phase 3: The bump (SB4-4)

- [ ] **Step 3.1:** Check Liquibase 5.0.4: license and changelog compatibility with `db/changelog/db.changelog-master.yaml`. If either is a problem, decide on a 4.x pin (see Key Decisions). Record the decision in the ADR-0033 changelog **before** editing the pom, because a pin changes step 3.2.
- [ ] **Step 3.2:** Update `api/pom.xml`:
  - **Version changes:**
    - parent → `4.1.2`
    - `springdoc-openapi.version` → `3.2.0`
  - **Dependency swaps:**
    - `liquibase-core` → `spring-boot-starter-liquibase`
    - add `spring-boot-jackson` if `spring-boot-starter-web` no longer brings it (check with `dependency:tree`)
    - drop the explicit Jackson 3 version from SB4-3 (now BOM-managed)
  - **Pins to remove:**
    - `spring-security.version`
    - `<version>` on `spring-boot-maven-plugin`, `micrometer-java21`, `mockito-core`, `byte-buddy`
    - `lombok.version` property (BOM 1.18.48). `pom.xml:379` uses `${lombok.version}` in the compiler plugin's `annotationProcessorPaths`; Boot's parent still defines `lombok.version`, so the reference keeps working once the local override is gone. Confirm with `mvn help:evaluate -Dexpression=lombok.version`
    - `maven-compiler-plugin` 3.11.0 and the surefire pin (BOM 3.15.0 / 3.5.6)
  - **Migration helper:** keep `spring-boot-properties-migrator` for step 3.6.
- [ ] **Step 3.3:** Finish Jackson 3 (Error and MVC side):
  - `exception/Error`: `@JsonSerialize`/`@JsonDeserialize` with the jsr310 `LocalDateTime` (de)serializer. Use Jackson 3's built-in java.time and keep `@JsonFormat("dd-MM-yyyy hh:mm:ss")` (EC-8).
  - `@JsonTypeIdResolver` → `tools.jackson.databind.annotation`.
  - `exception/LowerCaseClassNameResolver` → Jackson 3 `TypeIdResolverBase` (signatures take `DatabindContext`).
  - `exception/UnauthorizedException`: use `JsonMappers`; `toJson()` stops declaring the checked exception, and `JWTAuthenticationFilter:88` is updated to match.
  - `client/YouTubeApiClientImpl`: `tools.jackson.databind.JsonNode`.
  - `JsonMapperBuilderCustomizer` bean applying the same FR-4 settings to the MVC mapper. `JsonMappers` and MVC share one source of settings.
  - Tests: `RestExceptionHandlerTest`, `YouTubeApiClientImplTest`, `CommentControllerTest` and `UserSettingsControllerTest` (`@Autowired JsonMapper`).
  - **Contract test code (not fixtures):** `contract/JsonContractSupport.java:3-5,10,35`, `contract/JsonReadContractTest.java:3`, `contract/JsonContractTest.java:43` and `exception/ErrorJsonContractTest`, `service/notification/NotificationPayloadContractTest` move to `tools.jackson` / `JsonMapper` and Boot 4's `JacksonAutoConfiguration` package. They may change only so that the same assertions run against the Boot 4 MVC mapper.
  - **`src/test/resources/json-contract/**` is read-only.** If a fixture comparison fails, fix the mapper configuration or the annotations, never the fixture. Check: `git diff --exit-code <SB4-3 commit> -- api/src/test/resources/json-contract`.
  - **3.5 quirks the fixtures pin; SB4-4 must keep them.** The customizer preserves Boot 3.5's visibility and null-inclusion behavior (null fields are written; field visibility as in Jackson 2 defaults):
    - `Error.timestamp` written with the 12-hour pattern `dd-MM-yyyy hh:mm:ss` (`Error.java:29`), e.g. 15:04 → `03:04` (`error-code.json`);
    - `ApiSubError` sub-errors serialize only `field` and `rejectedValue` (it has no getters for `object` / `message`) (`error-field-sub-errors.json`, `error-constraint-violation.json`);
    - `ResultErrorInfo` serializes `"error": null` (`error-result-error-info.json`).
- [ ] **Step 3.4:** Compile fixes, limited to what the build needs (FR-6):
  - **Test slices (15 `@WebMvcTest` files):** the `WebMvcTest` import moves to the Boot 4 test module package (add the `spring-boot-webmvc-test` dependency if needed); the `TaskExecutionAutoConfiguration` import moves in `config/AsyncExecutorVirtualThreadTest`.
  - **`config/ConfigBean`:** `RestTemplateBuilder` and `MeterRegistryCustomizer` package moves.
  - **`filter/WebSecurityConfig`:** the `DefaultMethodSecurityExpressionHandler.createEvaluationContext` override (`:92-105`) on Security 7; string `requestMatchers` (`:55-58`) keep the same effect under Security 7's path-pattern matching, so check `AppConstant.WebIgnoringConfig` paths for pattern-syntax changes; CORS unchanged. `WebSecurityConfigAdminMatcherTest` and the 4 `*PermissionTest` classes must pass with unchanged assertions.
  - **`exception/Error:110`:** Hibernate Validator internal `PathImpl`. Replace it with the public `jakarta.validation.Path` API, with the same field name in the output.
  - **Entities:** for the 5 entities with `@GeneratedValue` and no strategy (`UserEmailRequest`, `AppReport`, `VideoCollection`, `VideoComment`, `VideoAccessStats`), confirm that Hibernate 7 resolves the same sequence / identity as Liquibase created. Set it explicitly only if it does not.
  - **`exception/RestExceptionHandler`:** `ResponseEntityExceptionHandler` override signatures.
- [ ] **Step 3.5:** Remove `@Disabled` from `JsonReadContractTest.trailingTokens_throw` (`:41`) **without changing its assertion**: reading a body with trailing tokens through the Boot 4 MVC mapper must throw (Jackson 3 default `FAIL_ON_TRAILING_TOKENS=true`; the customizer must not turn it off) (spec Rule 4, EC-6). Contract fixtures from SB4-1 must pass **unchanged**.
- [ ] **Step 3.6:** Boot locally against Postgres (`./startLocal.sh`):
  - Liquibase: 0 pending changesets.
  - Local DB only: create one row each for `UserEmailRequest`, `AppReport`, `VideoCollection`, `VideoComment`, `VideoAccessStats` through the app (implicit `@GeneratedValue`); ids must come from the Liquibase-created sequences/identity with no Hibernate error.
  - `/actuator/health` UP, `/actuator/prometheus` serves metrics, Swagger UI loads (dev profile).
  - The properties-migrator log shows no renamed keys; fix any it reports.
  - Then remove `spring-boot-properties-migrator`.
- [ ] **Step 3.7:** Docs in the same commit (FR-8):
  - `.claude/rules/tech-strategy.md`: Spring Boot 4.x, Jackson 3.
  - `CLAUDE.md`: project overview says Spring Boot 4.x.
  - ADR-0033 changelog: final jjwt backend and the Liquibase outcome.

### Phase 4: Ship and watch (SB4-5)

- [ ] **Step 4.1:** After push approval, watch CI ("Funny App Build and Deploy"). Confirm the prod VM starts with the existing `startServer.sh` flags (sequence flow 1).
- [ ] **Step 4.2:** Prod smoke test:
  - magic-link login, password login + MFA
  - a session from before the deploy is still logged in
  - video list and Range streaming
  - YouTube import, admin pages
  - notifications SSE
  - CORS PATCH/PUT from the webapp
- [ ] **Step 4.3:** 7-day watch. Then set the tracking row to ✅ and the PRD/ADR/plan statuses to Done.

## Task Breakdown

Every unit merges to `main` alone, in this order; each needs its own push approval.

| ID | Task | Depends on | ~LOC | Acceptance |
|----|------|-----------|------|-----------|
| SB4-1 | Contract baseline tests on 3.5 (JSON fixtures + legacy JWT + leniency) | — | 450 (test only) | `mvn verify` green on 3.5; fixtures cover every JSON-returning controller, wrappers, `Error`, `UnauthorizedException`, notification payload, `Page`; uses `NON_EXTENSIBLE` |
| SB4-2 | jjwt 0.13.0 + `jjwt-orgjson`; drop unused deps (on 3.5) | SB4-1 | 120 | Both legacy JWT fixtures verify (`with-cv` → `credentialsVersion=3`, `without-cv` → `0`); warm-up test passes; expired/tampered still 602/601; `mvn dependency:tree` shows no `jjwt-jackson` / hypersistence / dataformat-yaml; prod: users stay logged in after deploy |
| SB4-3 | Internal Jackson → Jackson 3 + `JsonMappers` (on 3.5, with fallback) | SB4-2 | 400 | `grep -rlE "com\.fasterxml\.jackson\.(databind\|core)" api/src/main` lists only `exception/Error.java`, `exception/LowerCaseClassNameResolver.java`, `exception/UnauthorizedException.java`, `client/YouTubeApiClientImpl.java`; same catch fallbacks (tests); contract + payload fixtures pass; none of the step 2.4 fallback triggers fired (else: folded into SB4-4) |
| SB4-4 | Boot 4.1.2 bump + remaining Jackson 3 + compile fixes + docs | SB4-3 | 600 | `grep -rE "com\.fasterxml\.jackson\.(databind\|core\|dataformat)" api/src` is empty (main and test); `git diff --exit-code <SB4-3 commit> -- api/src/test/resources/json-contract` is clean and every contract test is green, including the three pinned quirks (step 3.3); `trailingTokens_throw` enabled with its assertion unchanged and passing; security matcher/permission tests unchanged and green; coverage ≥ 90%; `BeanConstructorTest` green; local boot checklist (step 3.6) done; tech-strategy and CLAUDE.md updated |
| SB4-5 | Deploy, prod smoke test, 7-day watch | SB4-4 | 0 | Smoke list in step 4.2 passes; no incident in 7 days; tracking ✅ |

```
SB4-1 ──► SB4-2 ──► SB4-3 ──► SB4-4 ──► SB4-5
```

## Files to Modify

| File | Action | Task | Description |
|------|--------|------|-------------|
| `api/src/test/java/.../contract/JsonContractTest.java` | Create | SB4-1 | Wire-format baseline |
| `api/src/test/resources/json-contract/*.json`, `legacy-jwt.txt` | Create | SB4-1 | Fixtures (never edited after SB4-1) |
| `api/src/test/java/.../filter/JwtProviderTest.java` | Modify | SB4-1, SB4-2 | Legacy tokens, org.json numeric claims |
| `api/pom.xml` | Modify | SB4-2, SB4-3, SB4-4 | jjwt, dependency removals, Jackson 3, parent bump, pins, starters |
| `api/src/main/java/.../config/JsonMappers.java` | Create | SB4-3 | Shared Jackson 3 mapper (FR-4) |
| `aop/MaskingUtils`, `client/YtDlpClientImpl`, `client/JdkGitHubIssueClient`, `service/impl/{ShareServiceImpl,GitHubWebhookServiceImpl,ChatGptServiceImpl}`, `service/notification/*Listener` | Modify | SB4-3 | `tools.jackson`, catch blocks |
| `exception/{Error,LowerCaseClassNameResolver,UnauthorizedException,RestExceptionHandler}`, `filter/JWTAuthenticationFilter`, `client/YouTubeApiClientImpl` | Modify | SB4-4 | MVC-side Jackson 3, `PathImpl`, handler signatures |
| `config/ConfigBean`, `filter/WebSecurityConfig` | Modify | SB4-4 | Package moves, Security 7 |
| 15 `@WebMvcTest` tests, `config/AsyncExecutorVirtualThreadTest`, `aop/*AspectTest` | Modify | SB4-4 | Test-module imports |
| `contract/*.java`, `exception/ErrorJsonContractTest`, `service/notification/NotificationPayloadContractTest` | Modify | SB4-4 | Jackson 3 imports only; fixtures under `json-contract/` stay untouched |
| `.claude/rules/tech-strategy.md`, `CLAUDE.md`, `docs/adr/0033-…` | Modify | SB4-4 | FR-8, ADR changelog |

## Dependencies

### Code Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| `org.springframework.boot:spring-boot-starter-parent` | 4.1.2 | Target baseline |
| `io.jsonwebtoken:jjwt-api` / `jjwt-impl` / `jjwt-orgjson` | 0.13.0 | JWT without Jackson 2 |
| `tools.jackson.core:jackson-databind` | 3.1.7 (explicit in SB4-3, BOM in SB4-4) | Jackson 3 |
| `org.springframework.boot:spring-boot-starter-liquibase` | BOM (Liquibase 5.0.4) | Boot 4 Liquibase auto-config |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 3.2.0 | Boot 4.1 support |

### Service Dependencies

| Service | Status | Notes |
|---------|--------|-------|
| Local PostgreSQL | Available | Boot check in step 3.6 |
| GitHub Actions CI + prod VM deploy | Available | Each task deploys on push |

## Testing Strategy

### Unit Tests

| Component | Test Cases | Status |
|-----------|------------|--------|
| `JsonContractTest` | Every DTO, wrapper and error fixture; `Page` shape; notification payload | Pending |
| `JwtProviderTest` | Legacy token, missing `cv`, expired/tampered (601/602), org.json numeric claims, warm-up | Pending |
| MVC mapper leniency | null → primitive, unknown property, trailing tokens (enabled in SB4-4) | Pending |
| Jackson call sites | The fallback of every catch block in step 2.4 | Pending |

### Integration Tests

| Scenario | Expected Outcome | Status |
|----------|------------------|--------|
| 15 `@WebMvcTest` slices on Boot 4 | Same assertions pass unchanged | Pending |
| Security matcher tests (`WebSecurityConfigAdminMatcherTest`, permission tests) | Same access decisions | Pending |
| Local boot against Postgres | Liquibase 0 pending, health UP, Prometheus, Swagger | Pending |

### Manual Testing

- [ ] Prod smoke list (step 4.2)
- [ ] Startup time and idle heap vs 3.5 on the same machine (NFR: ≤ +10%, fits `-Xmx256m`)

## Rollback Plan

1. Every task is its own commit on `main`, so rolling back SB4-4 does not undo SB4-1 … SB4-3.
2. For a prod problem after SB4-4: redeploy the previous artifact (VM JAR / Helm image tag from SB4-3). No DB step is needed, because there is no schema change and the Liquibase changelog is untouched.
3. Verify `/actuator/health` UP, a login, and video playback; then `git revert` the SB4-4 commit on `main` so CI does not redeploy it.

## Risks

| Risk | Mitigation |
|------|------------|
| JSON shape drift (`PageImpl`, alphabetical order, java.time, `Error` type wrapper) | SB4-1 fixtures with `NON_EXTENSIBLE`; never edited after SB4-1 |
| Jackson 3 next to Boot 3.5 conflicts on `jackson-annotations` | SB4-3 fallback: fold into SB4-4 |
| Liquibase 5.0.4 license or changelog incompatibility | Step 3.1 decides before any code moves; 4.x pin as the fallback |
| Security 7 custom expression handler API change | Permission tests already exist; keep the same SpEL behavior |
| Hibernate 7 `@GeneratedValue(AUTO)` picks a different generator | Step 3.4 check against Liquibase sequences; explicit strategy if needed |
| Class-init stall on the 1 vCPU VM with the new JSON backend | `JwtProvider.warmUp()` covers the org.json reader (SB4-2 test); SB4-2 is proven in prod before the bump |
| SB4-4 diff too large to review well | Everything that can move earlier does; SB4-4 contains only bump-coupled changes; `/code-review` before commit |

## Checklist

### Before Starting

- [x] PRD/ADR approved (2026-10-28)
- [x] Dependencies available (versions verified on Maven Central 2026-10-28)
- [x] Work on `main` (repo convention; each task is one commit, push after approval)

### Before Each Commit

- [ ] `mvn verify` green, coverage ≥ `api/.coverage-threshold`
- [ ] Contract fixtures untouched (after SB4-1)
- [ ] Self-review / `/code-review`

### Before SB4-4 Push

- [ ] Local boot checklist (step 3.6)
- [ ] Rollback artifact noted (current prod JAR / image tag)

## Notes

- Explorer findings (2026-10-28): 18 main + 12 test files use Jackson. No `@SpringBootTest`. No `@MockBean` left (already `@MockitoBean`). `jjwt-jackson` 0.13.0 depends on Jackson 2 only. The SSE `unread` payload is a hand-built string, not Jackson, so it is unaffected. No Guava cache stores Jackson output.
- Pre-existing, not changed here: `MaskingUtils.maskSensitiveFields` returns the unmasked object when cloning fails (fail-open). It is worth a separate fix.
- Sources: [Boot 4.1.2 release](https://github.com/spring-projects/spring-boot/releases/tag/v4.1.2), [Boot 4.0 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide), [springdoc releases](https://github.com/springdoc/springdoc-openapi/releases), [jjwt-jackson 0.13.0 POM](https://repo1.maven.org/maven2/io/jsonwebtoken/jjwt-jackson/0.13.0/jjwt-jackson-0.13.0.pom).

---

## Progress Log

| Date | Update |
|------|--------|
| 2026-10-28 | Plan written from 4 explorer passes (Jackson inventory, Boot 4 breakage, contract DTOs, versions) |
| 2026-10-28 | Plan approved |
