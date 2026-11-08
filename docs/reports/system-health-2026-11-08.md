# System Health Report: Funny Movies Backend
**Date:** 2026-11-08
**Method:** `/swarm-research` — 6 parallel read-only reviewers (security, concurrency/perf, data layer, architecture/quality, integrations, tests/ops), cross-checked and spot-verified by the orchestrator against code
**Previous Report:** 2026-05-30 (`system-health-2026-05-30.md`)
**Scope:** `api/` (357 main / 149 test Java files), `helm/`, `.github/workflows/`
**Per-track notes:** `scratchpad/research-be-review-{security,concurrency,data,arch,integrations,ops}.md` (ephemeral)

---

## Executive Summary

The backend is structurally sound. Layering is clean (no `repo`/`entity` in `web/`, no raw SQL in services). There is no `synchronized` and there are no static-init I/O traps. Range streaming is bounded, external process execution (yt-dlp, HLS ffmpeg) is hardened, and counters use atomic SQL with `SKIP LOCKED` and `ON CONFLICT`. The test suite maps broadly onto the code.

The risk is concentrated in four places:

1. **Authorization holes in user-facing flows.** The MFA endpoints trust a `username` from the request, and share-link delete has no owner check.
2. **Secrets and defaults.** There is a JWT secret fallback in a public repo, the YouTube API key is sent in the URL, and the Helm chart ships the `dev` profile with a plaintext DB password.
3. **Silent failure in background work.** SMTP has no timeouts, and jobs record SUCCESS on `Error` or on swallowed subtask failures.
4. **Unverifiable data layer.** The Liquibase changelog cannot build a fresh database, and no test boots the context against a real database.

**Overall Health Score: 7.0/10** (down from 8.5). Most findings are not regressions. They come from a deeper review method (security and data audits the earlier report did not run).

| Severity | Count (after dedupe) |
|----------|----------------------|
| Critical | 1 |
| High | 9 |
| Medium | ~18 |
| Low/Info | ~25 |

---

## Priority Fix List (do in this order)

| # | Sev | Finding | Where | Fix | Tracks |
|---|-----|---------|-------|-----|--------|
| P1 | **Critical** | **MFA IDOR.** `/user/mfa/enable` takes `username` and verifies the OTP against an *attacker-supplied* secret, then writes that secret onto any user. Any registered user can bind their own authenticator to an admin account, and the victim is locked out of every later login. `disable` and `setup` take `username` the same way. CWE-639/862 | `web/UserController.java:135-178`, `service/impl/UserServiceImpl.java` `enableMfa`/`disableMfa`/`setupMfa` | Derive the user from `AppUtils.getCurrentUser()`. Reject when MFA is already enabled. Require the current OTP or a fresh re-auth to replace a secret. Add regression tests | Sec ✔verified |
| P2 | High | **MFA OTP brute force.** `/mfa/verify` is whitelisted with no `@RateLimited`, and failed attempts don't invalidate the MFA session token. CWE-307 | `UserServiceImpl.verifyMfa`, `AppConstant.java:51` | `@RateLimited` on all MFA endpoints. Invalidate the session after about 5 failures. Remove the token on success | Sec |
| P3 | High | **JWT secret fallback** `${JWT_SECRET:1234…32}` in a public repo. If the env var is missing, anyone can forge a SUPER_ADMIN token. Same pattern for `DB_PASS:123456`. CWE-798 | `application.yaml:117`, `:73` | Remove the defaults so a missing value fails boot. Assert key length ≥ 32 random bytes | Sec ✔, Ops |
| P4 | High | **Share-link delete IDOR.** `deleteById` with no owner check. CWE-639 | `ShareServiceImpl.deleteShareLink:103-106` | Load the link, compare the owner, return 403/404 | Sec ✔, Data |
| P5 | High | **YouTube API key in the query string.** `RestTemplate` I/O exceptions include the full URL, which then gets logged. CWE-532/598 | `ShareServiceImpl.java:137`, `YouTubeApiClientImpl.java:36` | Send the key in the `x-goog-api-key` header. Log only the exception class and status | Integ ✔ |
| P6 | High | **No SMTP timeouts.** The notification digest calls the *synchronous* `sendSimpleMail`, so one hung Gmail socket keeps the job's running flag set forever and digests stop until a restart. `@Async` invites leak virtual threads the same way | `application.yaml:62-69`, `NotificationDigestService.java:104`, `DynamicJobScheduler.java:113-129` | `mail.smtp.connectiontimeout`/`timeout`/`writetimeout` (5–15 s) plus `starttls.required` | Conc ✔, Integ ✔ (corroborated) |
| P7 | High | **The changelog cannot build a fresh database.** There is no `CREATE TABLE users`/`share_links`, yet later changesets `ALTER`/`REFERENCES users`. This breaks DR, new environments and any Testcontainers test. `users.user_name` uniqueness and the index can't be audited | `db/changelog/sql/` | Baseline changeset with a `tableExists` precondition / `MARK_RAN` | Data ✔ (worker rated Critical; downgraded because prod is unaffected) |
| P8 | High | **No boot or DB integration test.** Liquibase, JPA mappings and `@Value` placeholders are never validated in CI. P7 is the proof: such a test would fail today | `api/src/test` (none), `AppReportMigrationTest.java:38` | One Testcontainers Postgres `@SpringBootTest` in the CI test job | Ops, Data |
| P9 | High | **Lost updates.** Full-row `save()` on `VideoSource` and `User` (no `@DynamicUpdate`/`@Version`) overwrites concurrent native updates. An admin edit reverts `hls_status`, and a profile save can clear `failed_login_count`/`locked_until`, which is a lockout bypass | `AdminVideoServiceImpl` (6 sites), `UserServiceImpl`, `AdminAccountServiceImpl` | `@DynamicUpdate` on both entities. `@Version` where conflicting edits matter | Data ✔ |
| P10 | High | **Supply chain and CVE gate.** The Trivy job is `if: false`, there is no Dependabot, and `trivy-action`, `scp-action` and `ssh-action` are pinned to `@master` (the latter two handle the deploy SSH key) | `.github/workflows/funnyapp-ci.yml` | Pin actions to SHAs, re-enable Trivy, add `dependabot.yml` (maven + github-actions) | Ops |

---

## Domain Findings (Medium, deduplicated)

### Security & exposure
- **Actuator is public.** `/actuator/**` is in `ALLOW_ALL_METHOD` (`AppConstant.java:81`), so `prometheus` and `metrics` are anonymous. Restrict them by port or role. *(Sec ✔, Ops — corroborated)*
- **Default-allow.** `anyRequest().permitAll()` (`WebSecurityConfig:59`) relies on the custom filter. Switch to `authenticated()` with an explicit public list. *(Sec)*
- **Token lifetime is ~30 days** (`1000L * 84600 * 30`, typo for 86400; the Javadoc says 24 h). The role comes from claims with no revocation on demote. *(Sec ✔)*
- **Rate limiting** keys on `getRemoteAddr` with no forward-headers config, so all users may share one bucket behind a proxy. `/join` is limited per IP only, so a mailbox can be flooded. `/verify-magic` has no limit. *(Sec)*
- **Error detail leaks.** The `IllegalArgumentException` message is returned verbatim, the constraint cause goes to the client, and an upstream `HttpClientErrorException` status and body are relayed. There is also no catch-all `Exception` handler, so `HlsException`/`ImportException` fall to Spring's default shape. *(Sec, Arch, Integ — 3 tracks)*
- **Helm ships `SPRING_PROFILES_ACTIVE=dev`** (Swagger plus `show-sql`) and a plaintext `DB_PASS`. *(Ops)*
- `FfmpegServiceImpl` bypasses the hardened `ProcessFactory`, so ffmpeg inherits `DB_PASS`/`JWT_SECRET`. CWE-526. *(Integ)*
- The Drive URL check `contains("drive.google.com")` accepts any URL as `embedLink`. *(Integ)*

### Background jobs & concurrency
- **Jobs report false success** (3 mechanisms, corroborated):
  - `DynamicJobScheduler` catches only `Exception`, so an `Error` such as OOM or a class-init failure is recorded as SUCCESS. *(Conc ✔)*
  - `syncDriveVideos` uses a plain `StructuredTaskScope` that drops subtask failures. *(Conc ✔)*
  - `AppScheduler:143` logs only the message and still records SUCCESS. *(Arch)*
- **`syncDriveVideos` is unbounded.** It starts one platform thread and one up-to-1 GB download per file. Add a `Semaphore(1-2)`. *(Conc ✔)*
- **Container JVM flags.** `entrypoint.sh` overwrites `JAVA_OPTS`, so the Helm `-Xmx512m` is dead (and too high for 600Mi anyway). There is no `virtualThreadScheduler.parallelism`, so `cpu: 500m` leaves 1 carrier, the same configuration as the 2026-10-15 deadlock. There is no `ExitOnOutOfMemoryError`. The prod VM flags live only in a server-side script. *(Ops ✔, Conc — corroborated)*
- The `@Async`/`@Scheduled` VT executor is unbounded and Tomcat is uncapped. Only Hikari (10) throttles. *(Conc)*
- `StreamVideoServiceImpl.updateDesc` runs a serial, uncapped OpenAI loop with no per-item catch. *(Integ)*
- One `RestTemplate` (45 s read timeout) is shared by YouTube and OpenAI, with no retry or circuit breaker. The `shareLink` request thread can block for up to 45 s. *(Integ)*
- `MailServiceImpl` swallows send failures, still increments the daily quota and logs success. *(Integ)*

### Data layer
- `prepareThreshold=0` (the rolling-deploy fix from the past incident) is not in the repo. `DROP COLUMN is_hide` shipped in the same release as the code change. `SELECT *` native queries are exposed to cached-plan errors. Use expand/contract. *(Data)*
- `findBySourceId` (on the streaming and HLS hot path) can't use the `(source_type, source_id)` index. Add an index on `source_id`. *(Data)*
- Unpaginated: the comment tree per video, `findAllByOrderByCreatedAtDesc` and `findAllBySourceType`. *(Data, Conc — corroborated)*
- There is no FK from comments or notifications to `video_sources`, so `deleteVideo` leaves orphans. `deleteVideo` also removes the Drive and disk files *before* the DB row. *(Data)*

### Architecture & quality
- `CommentController` and `UserController` inject concrete `*Impl` classes. *(Arch)*
- `AdminVideoController` hand-parses `Map<String,Object>`; use typed records with `@Valid`. *(Arch)*
- Large services: `VideoSuggestionServiceImpl` 423 LOC, `VideoCollectionServiceImpl` 412, `PasswordLoginServiceImpl` with 12 dependencies, `AdminVideoServiceImpl` with 8 setter-injected dependencies. *(Arch)*
- **No correlation id/MDC anywhere.** Email addresses (PII) are logged in `UserServiceImpl`/`MailServiceImpl`. *(Arch, Ops — corroborated)*

### Tests, build & ops
- Jacoco excludes `config/**`, `repo/**` and `WebSecurityConfig*`. `pom.xml:35` has `jacoco.minimum.coverage=0.07`, so the 90% gate exists only in a CI shell step and there is no branch gate. ✔
- `@Disabled`: `AppSchedulerTest:105` (thumbnail happy path) and `TotpImplTest:54` (OTP expiry; rewrite with an injected `Clock`).
- Probes run `exec curl` on the aggregate `/actuator/health` for liveness. Use the liveness/readiness groups, `httpGet` and a `startupProbe`.
- There is no `server.shutdown: graceful`, the container runs as root, and the base image is not digest-pinned.
- `deploy` does not wait for `docker`, and there is no post-deploy health check in CI.

---

## Cross-Cutting Themes

1. **Ownership is not enforced in the service layer.** P1, P4 and the `setupMfa` username issue all trust caller-supplied identity. The admin URL gate is solid, which suggests that non-admin, user-owned resources never got the same treatment. A grep sweep of every endpoint that accepts a user or resource id is warranted (see Gaps).
2. **"Success" is reported when work failed.** Mail, three job mechanisms and Drive sync all report success after a failure. On a 1 vCPU box with a history of hangs, silent degradation is the main operational risk.
3. **Prod truth lives outside the repo.** The JVM flags, `prepareThreshold`, the `users` DDL and the prod launch script are all server-side only. This makes the repo unable to recreate prod, and it is why the container path has drifted (one carrier thread, dead `JAVA_OPTS`).
4. **Insecure defaults as fallbacks.** The JWT secret, `DB_PASS`, the Helm `dev` profile, `permitAll` by default and public actuator. Each one is harmless only while something else is configured correctly.

---

## Contradictions & Corrections

- **`startServer.sh` loop** (Ops F15). The worker claimed the "Tomcat started" check can never succeed because stdout goes to `/dev/null`. This is **incorrect**: logback writes `logs/funny-app.log` directly, so a normal boot exits the loop. The real bug is narrower: `i` is never incremented, so a *failed* boot waits forever instead of 5 iterations.
- **Changelog severity.** The data worker rated it Critical. It is rated High here because prod is unaffected; the impact is on DR, new environments and tests.
- **MFA IDOR severity.** The security worker rated it High. It is raised to Critical because the exploit is a single request by any self-registered user against any account, including SUPER_ADMIN.
- **Web Push (ADR-0032)** is not implemented in `api/` (grep). The integrations reviewer could not assess VAPID or the 410 cleanup. Check whether the ADR status should be updated.

## Confidence Summary

| Claim | Evidence |
|-------|----------|
| P1, P3, P4, P5, P6, P7, P9 (no `@DynamicUpdate`/`@Version`), actuator exposure, JVM entrypoint, jacoco 0.07, scheduler catch, platform-thread scope, digest sync mail | Verified by the orchestrator in code |
| SMTP timeouts, JVM flags, actuator, error leakage, false-success jobs, unpaginated comments, missing correlation id | Corroborated by ≥2 independent tracks |
| P2 brute force, P9 race frequency, rate-limit bucket behind proxy, `deploy`/`docker` ordering | Single track, code-cited, not re-verified |
| Large-service ranking, F10 unused cache classes | Grep-based inference; medium confidence |

## Gaps & Follow-Up

- Ownership checks in watch-history, notifications, collections, suggestions and reactions endpoints (not traced end to end).
- Magic-link token entropy, expiry and storage (hashed or plain).
- `HlsStorage.resolveSegment` path traversal (`..`).
- `AdminAccountController` and `AdminCommentController` method-level checks beyond the `/admin/**` URL gate.
- yt-dlp `jobDir` cleanup on failure or timeout.
- Whether prod actually sets `JWT_SECRET` (assumed yes; P3 is about the fallback).
- No consolidated external data-flow inventory in `docs/architecture/` (`security.md` "Data Routing"). Integrations listed: OpenAI (titles only), YouTube (ids + key), Gmail (invitee email + link), yt-dlp (user URL), Drive (file ids), GitHub Issues (report content; check for user identifiers).

## Suggested Next Steps

- **Security batch (P1–P5)** via `/autopilot` or a small plan. Each item is under 100 LOC with regression tests. P1 and P4 first.
- **Resilience batch:** SMTP timeouts, the scheduler catch of `Throwable`, Drive sync bound and failure propagation, and mail failure propagation.
- **Data/infra batch:** baseline changeset plus Testcontainers boot test (P7 + P8 together), `@DynamicUpdate`, and the `source_id` index.
- **CI/ops batch:** pin actions, Trivy, Dependabot, the `entrypoint.sh` JVM flags, and the Helm profile and secret.
