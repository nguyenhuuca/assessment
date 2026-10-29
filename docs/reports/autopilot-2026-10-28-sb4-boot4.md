# Autopilot run: Spring Boot 4.1 upgrade, SB4-2..SB4-4

- **Request:** `docs/plans/plan-spring-boot-4-upgrade.md` SB4-2..SB4-4, one commit per task; `json-contract/` fixtures unchanged; SB4-3 falls back into SB4-4 on a jackson-annotations conflict
- **Mode:** local (plan mode)
- **Plan:** [plan-spring-boot-4-upgrade](../plans/plan-spring-boot-4-upgrade.md)
- **Started:** 2026-10-28 22:06 · **Landed:** 2026-10-28 22:50 (pushed `1552b8de`)
- **Result:** committed locally: SB4-2 `02fe8707`, SB4-4 `6f42137d`; pushed after Gate 2

## Pipeline

| Stage | Result | Rounds |
|-------|--------|--------|
| Start | Owner request in chat | — |
| Plan | Reused (plan mode); edited in place after review (`53435777`) | 1 of 3 |
| Plan review | Round 1 revise (4 blocking, 9 advisory), round 2 approve (0 blocking, 2 advisory, applied) | 2 of 3 |
| Gate 1 (owner) | Approved | — |
| Implement | worker-builder ×3 (SB4-2, SB4-3, SB4-4); SB4-3 fallback fired and was folded into SB4-4; SB4-4: 53 files, main +80/−67, test ≈ +105/−59 | 1 of 3 |
| Code review | SB4-2 approve (0 blocking, 3 advisory); SB4-4 approve (0 blocking, 4 advisory) | 1 of 3 |
| Local QA ×2 | SB4-2: 69 tests, pass 69 · flaky 0 · fail 0. SB4-4: 361 tests (29 changed classes), pass 361 · flaky 0 · fail 0 | 1 of 3 |
| Fix | SB4-2: orphan pom comments removed. SB4-4: static mapper in `UnauthorizedException`, comment on the enum setting | — |
| Land: full gates | `mvn verify` green after each task; LINE coverage 96.38% (SB4-2), 96.24% (SB4-4, `clean verify`) | — |
| Gate 2 (owner) | Push (owner accepted that the first Boot 4 start is on prod: no local Postgres) | — |
| CI + smoke | CI green (build, tests, Docker, deploy); `mock-test.sh` 22/22; health 200; `/actuator/prometheus` 403 from nginx/Cloudflare in front of the app (external block, not the app) | — |

## What changed

- **SB4-2:** jjwt 0.12.6 → 0.13.0, and the JSON backend `jjwt-jackson` → `jjwt-orgjson` (`api/pom.xml`).
  - The legacy 3.5 tokens still verify.
  - The unused `hypersistence-utils-hibernate-63` and the direct `jackson-dataformat-yaml` are removed.
- **SB4-3:** not committed; the fallback fired.
  - Trigger 2: `NoClassDefFoundError: com/fasterxml/jackson/annotation/JsonSerializeAs`.
  - Trigger 3: Jackson 3.1.7 needs `jackson-annotations` 2.21, while Boot 3.5.0 pins 2.19.0.
  - Steps 2.3–2.4 were carried into SB4-4.
- **SB4-4, dependencies:**
  - Spring Boot 4.1.2 (Framework 7.0.10, Security 7.1.2, Hibernate 7.4.13, Jackson 3.1.7, Liquibase 5.0.4).
  - New starters: webmvc, jackson, restclient, liquibase, cache.
  - New test starters: webmvc-test, security-test, cache-test.
  - springdoc 3.2.0.
  - BOM-overriding pins removed.
- **SB4-4, JSON:**
  - One settings source, `config/JsonMappers.java`, used both by the MVC mapper (through a `JsonMapperBuilderCustomizer` in `ConfigBean`) and by code-built mappers.
  - All Jackson usage moved to `tools.jackson`; `JsonProcessingException`/`IOException` catches became `JacksonException` with the same fallbacks.
  - `UpdateUserSettingsRequest` disables its all-args constructor as a Jackson creator. Jackson 3 picked that constructor and lost the null-vs-absent `locale` signal; `UserSettingsServiceImplTest.request_jsonNullVersusAbsentLocale` failed before the fix and passes after.
- **SB4-4, framework fixes:**
  - `Error` reads the constraint leaf name through the public `jakarta.validation.Path` API instead of Hibernate Validator's internal `PathImpl`.
  - `WebSecurityConfig` takes the Security 7 `Supplier<? extends Authentication>` override signature; no matcher or rule changed.
- **Contract:**
  - The 31 SB4-1 fixtures are byte-identical (`git diff --exit-code 02fe8707 -- api/src/test/resources/json-contract`).
  - `trailingTokens_throw` is enabled with its assertion unchanged.
- **Docs:** `tech-strategy.md` and `CLAUDE.md` now say Spring Boot 4.x. The ADR-0033 changelog records the jjwt backend, the SB4-3 fold and the Liquibase decision.

## Runs

| # | cycle | stage | round | path | result | blocking | advisory | turns | min | tokens | model |
|---|-------|-------|-------|------|--------|----------|----------|-------|-----|--------|-------|
| 1 | 1 | plan | — | reused | plan mode: SB4-2, SB4-3, SB4-4 open | — | — | — | — | — | — |
| 2 | 1 | plan review | 1 | fallback (worker-reviewer) | revise | 4 | 9 | 11 | 0.9 | 47470 | sonnet |
| 3 | 1 | plan | 2 | orchestrator | 4B + 8A applied | — | — | — | 4 | — | opus |
| 4 | 1 | plan review | 2 | fallback (worker-reviewer) | approve | 0 | 2 | 2 | 0.2 | 54378 | sonnet |
| 5 | 1 | plan | 3 | orchestrator | 2A applied | — | — | — | 1 | — | opus |
| 6 | 1 | gate1 | — | owner | approved | — | — | — | — | — | — |
| 7 | 1 | implement SB4-2 | 1 | fallback (worker-builder) | pom only | — | — | 6 | 1.7 | 31499 | sonnet |
| 8 | 1 | code review SB4-2 | 1 | fallback (worker-reviewer) | approve | 0 | 3 | 6 | 1.1 | 44171 | sonnet |
| 9 | 1 | qa x2 SB4-2 | 1 | qa-twice.sh | pass 69/69 | — | — | — | 3 | — | — |
| 10 | 1 | land SB4-2 | — | orchestrator | `02fe8707`; verify green | — | — | — | 4 | — | opus |
| 11 | 1 | implement SB4-3 | 1 | fallback (worker-builder) | fallback fired → folded into SB4-4 | — | — | 23 | 3.5 | 40534 | sonnet |
| 12 | 1 | step 3.1 Liquibase | — | orchestrator + owner | FSL-1.1-ALv2; owner accepts 5.0.4 | — | — | — | 2 | — | opus |
| 13 | 1 | implement SB4-4 | 1 | fallback (worker-builder) | verify green | — | — | 68 | 18.2 | 136084 | sonnet |
| 14 | 1 | code review SB4-4 | 1 | fallback (worker-reviewer) | approve | 0 | 4 | 23 | 3.4 | 68871 | sonnet |
| 15 | 1 | fix SB4-4 | — | orchestrator | 2 advisories applied | — | — | — | 2 | — | opus |
| 16 | 1 | qa x2 SB4-4 | 1 | qa-twice.sh | pass 361/361 | — | — | — | 8 | — | — |
| 17 | 1 | land SB4-4 | — | orchestrator | clean verify green, LINE 96.24% | — | — | — | 5 | — | opus |

## Open advisories

- **SB4-2:** `org.json` 20231013 is now on the JWT parse path, which reads untrusted input before the signature is verified. Bump `json-lib.version` in a separate change after checking the latest release and its CVEs.
- **SB4-4:** `MaskingUtils.cloneObject` falls back to the original object, which the caller then masks in place. This hazard existed before the upgrade; fix it in its own task.
- **SB4-4:** `Error` leaf name for container-element paths (e.g. `list[0]`) through `Path.Node.getName()` is not covered by a fixture.
- **Plan step 3.6:** resolved 2026-10-29. The first prod start (2026-10-28 16:09) logged no renamed keys and Liquibase reported "no changesets to execute", so `spring-boot-properties-migrator` was removed. The one new-looking warning, `Serializing PageImpl instances as-is`, was already logged once per start on 3.5.

## Manual checks for the owner

- **Step 3.6, local boot against PostgreSQL:** not run, because this machine has no local Postgres and Docker Desktop is stopped. Check:
  - Liquibase 5.0.4 reports 0 pending changesets.
  - `/actuator/health` is UP, `/actuator/prometheus` serves metrics, and Swagger UI loads (dev profile).
  - The properties-migrator log shows no renamed keys.
  - One row can be inserted for each of `UserEmailRequest`, `AppReport`, `VideoCollection`, `VideoComment`, `VideoAccessStats` (Hibernate 7 `@GeneratedValue`).
- **SB4-5 prod smoke** (plan step 4.2):
  - magic-link login, password login + MFA
  - a session from before the deploy is still logged in
  - video list and Range streaming
  - YouTube import, admin pages
  - notifications SSE
  - CORS PATCH/PUT

## Ignored instructions

- none

## Cost

API list-price equivalent, not a bill. `claude-cost.mjs` from 2026-10-28T15:06:18Z to 15:47:23Z (before Gate 2).

| who | model | requests | cost |
|---|---|---|---|
| main session (orchestrator) | claude-opus-5-5 | 40 | $3.65 |
| worker-reviewer: Plan review SB4-2..SB4-4 | claude-sonnet-5-5 | 6 | $0.23 |
| worker-builder: Implement SB4-2 | claude-sonnet-5-5 | 6 | $0.11 |
| worker-reviewer: Code review SB4-2 | claude-sonnet-5-5 | 6 | $0.16 |
| worker-builder: Implement SB4-3 | claude-sonnet-5-5 | 21 | $0.21 |
| worker-builder: Implement SB4-4 | claude-sonnet-5-5 | 60 | $1.22 |
| worker-reviewer: Code review SB4-4 | claude-sonnet-5-5 | 17 | $0.38 |
| **TOTAL** |  | **156** | **$5.96** |
