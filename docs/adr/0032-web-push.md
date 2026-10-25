# ADR-0032: Web Push Notifications with VAPID, Sent from the Backend

## Metadata

**Status:** Proposed · **Date:** 2026-10-25 · **Deciders:** nguyenhuuca · **Tags:** api, security, frontend, integrations
**Related PRD:** [PRD-web-push](../prd/PRD-web-push.md) · **Builds on:** [ADR-0017 User Notifications](0017-user-notifications.md), [ADR-0023 Dynamic Cron Jobs](0023-dynamic-cron-jobs.md), [plan-pwa-basic](../plans/plan-pwa-basic.md) · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ✅ No new runtime dependency. It uses JCA in the JDK, `jjwt` (already used for auth) and `java.net.http.HttpClient` (already used by `JdkGitHubIssueClient`). Adds one row to *External Integrations* (Web Push / VAPID).

---

## Context

- **Notifications reach users only while the site is open.**
  - In-app notifications go out over SSE (`service/notification/InMemorySsePublisher.java`, ADR-0017).
  - The email digest (`service/notification/NotificationDigestService.java`) covers personal events only.
  - The `user_settings.notify_new_content` toggle exists (`entity/UserSettings.java:30`, Settings page `SettingsPage.jsx:257`), but nothing sends new-content notifications.
- **The PWA has a service worker** (`webapp/public/sw.js`, plain JS with no imports) and an installable manifest (2026-10-24). That is the client half Web Push needs.
- **The PRD asks for three things** (signed-in users only):
  1. a daily new-video digest at 19:00 Asia/Ho_Chi_Minh (text and app icon only, no image);
  2. admin alerts on import `DONE`/`FAILED` (`VideoImportWorker.java:210,303`) and HLS `ERROR`;
  3. Settings controls and click-through tracking.
- **Constraints:**
  - 1 vCPU / `-Xmx256m` VM.
  - Virtual threads; no `synchronized` (tech strategy). Hangs came from class-init and loader contention (postmortem 2026-10-24).
  - Public repo, so secrets live only in env.
  - `security.md`: no silent external data routing.
- **Patterns already in the code:**
  - after-commit domain events (`@TransactionalEventListener(phase = AFTER_COMMIT)` in the notification listeners);
  - dynamic cron jobs in `scheduled_job` run by `DynamicJobScheduler` (ADR-0023);
  - JDK `HttpClient` with explicit timeouts (`JdkGitHubIssueClient.java:35-38`);
  - `jjwt` for JWT signing (`filter/JwtProvider.java`).

---

## Decision Drivers

1. **No third-party service holding user data.** The owner chose standard Web Push over FCM or OneSignal SDKs.
2. **No heavy dependency on a small VM.** Every library must be maintained, small, and JDK 24 friendly.
3. **Never hurt the core paths.** A push failure cannot block or fail imports, HLS jobs, the feed or in-app notifications.
4. **Opt-in and low noise.** The permission prompt appears only on user action, with at most one digest per day.
5. **Measurable.** Click-through per push type.
6. **Secrets out of the repo.** The VAPID private key lives in env.

---

## Considered Options

### Option 1: Firebase Cloud Messaging / OneSignal SDK

| Pros | Cons |
|------|------|
| Managed delivery, dashboards, retries | User tokens and payloads go through and are stored by a third party (driver 1) |
| Well-known client SDKs | Adds a client SDK and service account or keys; vendor lock-in |
| | The owner rejected it in the PRD |

### Option 2: Standard Web Push with `nl.martijndwars:web-push` (webpush-java)

The most widely used Java Web Push library. It has moved to `web-push-libs/webpush-java`, and the latest Maven artifact seen is 5.1.x.

| Pros | Cons |
|------|------|
| Proven implementation of RFC 8291 and VAPID | Requires BouncyCastle as a JCE provider and Apache HttpAsyncClient: two extra dependency trees on a 256 MB heap |
| Includes a CLI to generate VAPID keys | Releases are slow. Forks exist for newer Java (`dev.blanke.webpush`), which is a sign of maintenance drift |

### Option 3: Standard Web Push with a zero-dependency library (`com.zerodeplibs:zerodep-web-push-java`, or `com.interaso:webpush`)

| Pros | Cons |
|------|------|
| No transitive dependencies (zerodep). Pluggable HTTP client | Small user base; still a new third-party dependency to vet and track |
| `interaso` uses the JDK HttpClient | `interaso` is Kotlin and would pull `kotlin-stdlib` into a Java project |

### Option 4: Standard Web Push implemented in-house on JCA *(chosen)*

- Encrypt payloads per RFC 8291 (`aes128gcm`) with JDK primitives: ECDH P-256 (`KeyAgreement "ECDH"`), HKDF-SHA-256 (`Mac "HmacSHA256"`) and AES-128-GCM (`Cipher "AES/GCM/NoPadding"`).
- Sign the VAPID JWT (ES256, RFC 8292) with the existing `jjwt`.
- Send with `java.net.http.HttpClient`.

| Pros | Cons |
|------|------|
| Zero new dependencies (drivers 2, 3) | We own about 150–200 lines of protocol code, with a risk of subtle crypto mistakes |
| Same JDK client and timeout pattern as `JdkGitHubIssueClient`; easy to bound and test | Must track protocol changes ourselves (rare: the RFCs date from 2017) |
| RFC 8291 Appendix A publishes a full test vector, so the encryption can be proven byte for byte in a unit test | |

---

## Decision Outcome

**Chosen Option:** Option 4. Standard Web Push with VAPID, implemented in-house on JCA, `jjwt` and the JDK HttpClient.

**Rationale:**
- Option 1 breaks driver 1. Options 2 and 3 add dependencies for about 200 lines of code whose only hard part, the encryption, can be checked against the RFC's official test vector.
- This is not new cryptography. It composes standard JCA primitives exactly as RFC 8291 specifies, and the result is pinned by the RFC 8291 Appendix A vector and a VAPID round-trip test.
- **Fallback:** if the implementation cannot reproduce the Appendix A vector exactly, switch to Option 3 (`zerodep-web-push-java`) behind the same `PushSender` interface. That change is local and needs no new ADR.

### D1. Subscription storage

- New table `push_subscription`:
  - `id` (UUID), `user_id` (FK `users`, on delete cascade);
  - `endpoint` (TEXT, **unique**), `p256dh` and `auth` (base64url);
  - `user_agent` (truncated), `created_at`, `last_success_at`, `failure_count`.
- A device that subscribes again **upserts on `endpoint`**.
- A user may have **at most 10** subscriptions. When an 11th is added, the oldest is deleted.
- Subscribing and unsubscribing affect only the caller's own rows, identified by `user_id` from the JWT. A user can never pass another user's id.

### D2. VAPID keys and configuration

- `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` (P-256, base64url) and `VAPID_SUBJECT` (`mailto:` the owner) come from env only, never the repo.
- The keys are validated at startup:
  - **missing or invalid keys disable push**: a logged warning, the endpoints answer "push unavailable", the jobs no-op;
  - the app still starts.
- The public key is served by an unauthenticated endpoint so the browser can subscribe.
- Key generation is a one-off owner task, documented in the plan.

### D3. Sending

- A `PushSender` builds the request and posts it:
  - an encrypted body;
  - `Authorization: vapid t=<JWT>, k=<public key>`, with JWT `aud` = endpoint origin and `exp` ≤ 12 h;
  - `TTL` (digest 12 h, admin alert 1 h), `Urgency` (digest `normal`, admin `high`) and `Content-Encoding: aes128gcm`.
- The JDK HttpClient uses connect 5 s and request 10 s timeouts and never follows redirects.
- Sends run on virtual threads bounded by a `Semaphore(4)`, never on a request thread.
- Response handling:
  - **201/202**: success, update `last_success_at`;
  - **404/410**: delete the subscription;
  - **413**: payload bug, logged;
  - **429/5xx or timeout**: increment `failure_count`, no retry in v1. A subscription that reaches 5 consecutive failures is deleted.
- The VAPID JWT is cached per push-service origin until shortly before `exp`, so it is not re-signed for every message.

### D4. Payload

- Payload JSON is at most 2 KB: `{ pushId, type, title, body, url }`.
- It holds **no tokens or personal data** beyond what the notification shows, and it is localized server-side with `user_settings.locale` (default vi).
- No image or thumbnail is sent (owner, 2026-10-25). The service worker shows the app icon.
- `url` is always a same-origin app path: `/` for the feed, or admin tabs.

### D5. Daily digest

- A new dynamic job `push-digest` (row in `scheduled_job`, cron `0 0 19 * * *`, zone `Asia/Ho_Chi_Minh`) is editable in Admin → Jobs, like the others (ADR-0023).
- Recipients are users with `notify_new_content = true` and at least one subscription, whose new-video count since their **last digest** is greater than 0. The last digest is tracked as a new `user_settings.push_digest_last_at`; when it is null, the last 24 h are counted.
- One push per user, sent to each of their devices, with "N video mới hôm nay".
- `push_digest_last_at` is updated after the sends. **At most one digest per user per day** is enforced by the job and by that timestamp.

### D6. Admin alerts

- `VideoImportWorker` and `HlsTranscodeWorker` publish small domain events (`ImportFinishedEvent`, `HlsFailedEvent`).
- The events are published only where the state change actually committed. For HLS that means `markHlsError` returned 1, so no alert fires for a status the owner changed.
- An `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)` + async listener sends to ADMIN and SUPER_ADMIN users with `push_admin_alerts = true` (a new `user_settings` column, default true).
- A failure in the listener is logged and never reaches the worker (driver 3).

### D7. Click-through tracking

- Each send creates a row in `push_delivery`: `push_id` (UUID), `type`, `user_id`, `sent_at`, `status` (SENT / FAILED / EXPIRED), `clicked_at`.
- The service worker's `notificationclick` handler opens or focuses `url` and calls `POST /v1/funny-app/push/click {pushId}`.
  - The call is unauthenticated, because the SW may have no token, and idempotent.
  - It only accepts a known, unclicked `pushId` younger than 7 days.
- Rows older than 30 days are purged by the existing retention job pattern.
- Admin stats show sent, delivered and clicked counts, and CTR per type for the last 7 and 30 days.

### D8. Client

- `sw.js` gains `push` and `notificationclick` handlers. Its caching rules stay unchanged.
- Settings adds:
  - a per-device control: subscribe via `PushManager.subscribe({ userVisibleOnly: true, applicationServerKey })`, then `POST` the subscription, and unsubscribe likewise;
  - its states: unsupported, iOS not installed (with a hint), denied, enabled;
  - an "Admin alerts" toggle for admins.
- The permission prompt only ever follows a click.

### D9. External data routing (`security.md`)

- Push messages leave the system to the **browser vendors' push services** chosen by the user's browser: Google FCM for Chrome and Android, Apple Push for Safari and iOS, Mozilla autopush for Firefox. We only ever call the `endpoint` URL that the browser gave us. Endpoints must be `https`, and the hostname must be a known push service.
- What those services see: the endpoint, the payload size, the TTL and urgency headers, and our VAPID public key. The payload is end-to-end encrypted (RFC 8291), so they cannot read titles or URLs.
- This flow is added to the external integrations inventory, in tech-strategy *External Integrations* and in the architecture docs.

### Quantified Impact

| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| New runtime dependencies | — | 0 | JCA + jjwt + JDK HttpClient |
| Digest send cost | — | about 1 ECDH + 1 AES-GCM per device, ~ms each | Bounded by `Semaphore(4)` |
| Heap per send | — | under ~10 KB transient | Payload ≤ 2 KB |
| New tables / columns | — | `push_subscription`, `push_delivery`; `user_settings.push_digest_last_at`, `push_admin_alerts` | Liquibase changesets |

---

## Consequences

**Positive:**
- Viewers can be reached when the site is closed. Admins learn about failed imports and HLS jobs at once.
- No vendor SDK or account, and no new dependency.
- The existing `notify_new_content` toggle finally does something.
- The first retention metric: push CTR.

**Negative:**
- We maintain about 200 lines of protocol code: VAPID, RFC 8291 encryption and response handling.
- iOS users must install the PWA before they can receive pushes.
- Two new tables and a new scheduled job to operate.

**Risks:**
- *Crypto mistake → pushes rejected or undecryptable.* Mitigation: the RFC 8291 Appendix A vector test, a VAPID JWT test, a manual test on Chrome, Android and iOS, and Option 3 as the fallback.
- *VAPID key leaked.* Mitigation: env only. To rotate, generate new keys; every client must subscribe again (documented).
- *Endpoint pointing at an arbitrary host (SSRF via a crafted subscription).* Mitigation: accept only `https` endpoints whose host is on an allowlist of known push services, validated on subscribe.
- *Click endpoint abuse.* Mitigation: unknown, old or already-clicked `pushId` values are ignored, the endpoint is rate-limited, and the IDs are UUIDs.
- *A slow push service stalls the digest.* Mitigation: timeouts, bounded concurrency, no retries in v1.

---

## Validation

- [ ] RFC 8291 Appendix A test vector reproduced byte for byte
- [ ] VAPID JWT verifies with the public key, and `aud`/`exp` are correct
- [ ] Endpoint allowlist rejects non-push hosts (SSRF test)
- [ ] Security review (subscription ownership, click endpoint, key handling)
- [ ] Tech Strategy updated: *External Integrations*, Web Push (VAPID, in-house, ADR-0032)
- [ ] Related plan document created: `docs/plans/plan-web-push.md`

---

## Links

- [PRD-web-push](../prd/PRD-web-push.md)
- [ADR-0017 User Notifications](0017-user-notifications.md), [ADR-0023 Dynamic Cron Jobs](0023-dynamic-cron-jobs.md), [ADR-0019 Admin Video Import](0019-admin-video-import.md), [ADR-0031 HLS](0031-hls-streaming.md)
- [RFC 8030 Web Push](https://datatracker.ietf.org/doc/html/rfc8030), [RFC 8291 Message Encryption](https://datatracker.ietf.org/doc/html/rfc8291), [RFC 8292 VAPID](https://datatracker.ietf.org/doc/html/rfc8292)
- Library survey: [web-push-libs/webpush-java](https://jitpack.io/p/web-push-libs/webpush-java), [zerodep-web-push-java](https://mvnrepository.com/artifact/com.zerodeplibs/zerodep-web-push-java/2.1.1/changes), [interaso/webpush](https://github-redirect.dependabot.com/interaso/webpush)

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-10-25 | nguyenhuuca | Initial draft |
| 2026-10-25 | nguyenhuuca | Dropped the thumbnail image from the payload (owner) |
