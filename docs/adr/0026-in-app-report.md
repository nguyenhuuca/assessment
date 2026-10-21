# ADR-0026: In-App Report Creates GitHub Issues With the Owner's Token

## Metadata

**Status:** Accepted (2026-10-20), amended 2026-10-21 (D7 webhook) · **Date:** 2026-10-20 · **Deciders:** nguyenhuuca · **Tags:** api, integration, security, agents
**Related:** [PRD-in-app-report](../prd/PRD-in-app-report.md) · amends the trust model of [ADR-0022](0022-headless-autopilot.md) D2 · works with [ADR-0025](0025-autopilot-auto-gates.md)

## Context

- **What the PRD asks for:** a **Report** menu where an admin sends a bug, an enhancement or an issue. The server turns it into a GitHub issue labelled `autopilot`, so headless autopilot picks it up.
- **The workflow's owner-only check:** `.github/workflows/autopilot.yml` starts only when the label sender is the repo owner (ADR-0022 D2). A server that creates issues with **any** identity other than the owner's does not start autopilot.
- **The repo is public,** so issue text is public.
- **Existing building blocks:**
  - **External HTTP:** `java.net.http.HttpClient`, for example `client/HttpRedirectProbe.java:31-34`. There is no Spring `RestClient` or `WebClient` in `api/`.
  - **Rate limiting:**
    - `@RateLimited` keys the limit by user name plus request URI (`aop/RateLimitAspect.java:33-36, 52-55`).
    - The limiter is in memory, which fits one replica (ADR-0023 D5).
  - **Roles:**
    - `UserRole` declaration order is the rank (`enums/UserRole.java`).
    - `WebSecurityConfig.roleHierarchy()` builds `hasRole` implications from that order (`filter/WebSecurityConfig.java:81-88`).
    - The role is stored as a string (`entity/User.java:56-57`, `EnumType.STRING`).
  - **Permission bits:** generic (`READ`, `WRITE`, `EXEC`, `DELETE`, `ADMIN`; `enums/Permission.java`). Admins get every bit.
  - **Cache:** Guava with `expireAfterWrite` (`cache/CredentialsVersionCache.java:24-25`).

## Decision Drivers

1. Autopilot must start from an app report with **no extra click** (owner's request: label `autopilot` by default).
2. **Least privilege:** the server holds the smallest possible GitHub credential.
3. **Abuse control:** reports start paid runs, and `autopilot:auto` can reach prod.
4. **Public repo:** no personal data in issues.
5. Follow existing patterns. Add no new library and no new infrastructure.

## Considered Options

### Who creates the issue

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| **A. Fine-grained PAT of the owner, server calls the GitHub REST API** | Issue sender is the owner, so autopilot starts with no workflow change; one repo, Issues only; no new infra | Every ADMIN acts as the owner toward autopilot; PAT on the server | **Chosen** |
| B. GitHub App (bot identity) | Separate, revocable identity; clean audit on GitHub | The workflow must trust a bot (rewrite ADR-0022 D2 and the job `if`); App setup and key storage; more moving parts for one feature | Rejected for v1 |
| C. Create the issue without `autopilot`; the owner adds the label | Keeps the owner as the only gate | Loses the "autopilot by default" requirement | Rejected |
| D. `gh` CLI on the server | Familiar | Needs a CLI and auth state on the VM; shells out with user text | Rejected |

### Access for a future CONTRIBUTOR

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| **New role `CONTRIBUTOR` between `USER` and `ADMIN`** | `roleHierarchy()` already derives `ADMIN > CONTRIBUTOR > USER` from enum order; endpoints just say `hasRole('CONTRIBUTOR')` | Inserting it moves `ordinal()`. That is safe only because roles are stored as strings and ranked by `outranks`. | **Chosen (later)** |
| New permission bit `REPORT` | Fine-grained | Bits are generic verbs today, so this starts a per-feature bit pattern. `ALL_BITS` changes, and stored admin masks need a migration. | Rejected |

## Decision

### D1. GitHub client

- **Interface:** `GitHubIssueClient` in the `client` package, implemented with `java.net.http.HttpClient` and Jackson, like `HttpRedirectProbe`. It has two calls:
  - `POST /repos/{owner}/{repo}/issues` with `title`, `body` and `labels`;
  - `GET /repos/{owner}/{repo}/issues/{number}` for the state.
- **Config:** `AppProperties.github` with `token`, `owner` and `repo`. The token comes from the env var `GITHUB_REPORT_TOKEN`.
- **Token rules:**
  - Fine-grained PAT, this repo only, Issues read/write.
  - Never logged, never returned to the browser, masked by `@AuditLog`.
- **Timeouts:** connect 5 s, request 10 s.

### D2. Labels and trust (amends ADR-0022 D2)

- **Labels on every issue:** `autopilot` plus one type label: `bug`, `enhancement` or `report`.
- **Auto labels:** `autopilot:auto` and `autopilot:auto-merge` are accepted **only from SUPER_ADMIN**. The server checks this and returns 403 to an ADMIN; hiding the option in the UI is not the check.
- **Trust change:**
  - Because the PAT is the owner's, an ADMIN report starts autopilot.
  - The owner-only check in ADR-0022 D2 now means "the owner, or an admin through the app". Admin accounts are granted only by the SUPER_ADMIN.
- **Report text is data,** exactly like issue text today (ADR-0022 D3). The headless denylist and the ADR-0025 merge checks still apply.

### D3. Abuse limits

- **Per user:** `@RateLimited(permit = 5, windowInSeconds = 3600)` on create and on retry (NFR-4). It is in memory, so a restart resets it; that is acceptable.
- **Global daily cap** on reports that carry `autopilot`: **10 per rolling 24 h**, counted from the stored reports, so it survives a restart.
  - **Over the cap:** the issue is still created, but **without** `autopilot`.
  - The user is told that the owner can add the label.
- **Audit:** `@AuditLog` on create and on retry. Each stored report keeps its reporter.

### D4. Storage and status

- **Table:** `app_report`, added by a Liquibase migration in `db/changelog/sql/`. Columns:
  - `id`, `reporter_id` (FK to users);
  - `type`, `title`, `description`;
  - `context` (path, app version, user agent, screen width);
  - `labels`, `status` (`CREATED` / `FAILED`), `issue_number`, `issue_url`, `error`;
  - `created_at`, `updated_at`.
- **Write path:**
  1. Save the report as `FAILED`.
  2. Call GitHub.
  3. On success, set `CREATED` with the issue number and URL.
  4. On failure, keep `FAILED` with a short error (no token, no response body). Retry repeats steps 2 and 3.
- **Duplicates:** the issue body ends with `<!-- app-report:{id} -->`. A response lost after GitHub created the issue can lead to a duplicate on retry; the marker lets the owner find and close it. The risk is accepted.
- **Status:** read through from GitHub when the list loads:
  - one `GET` per issue on the page, in parallel on virtual threads;
  - cached in Guava for 5 min;
  - a failure shows `unknown`.
  - Superseded for issues with a stored state by D7: the list reads the state from the table and calls GitHub only for rows that have none.
- **List scope:** every admin sees all reports, newest first, paged, with the reporter's display name. The issues are public anyway.

### D5. Issue body (public repo)

- **Content, in order:**
  1. the description as typed;
  2. a "Context" section: page path **without the query string**, app version, user agent, screen width;
  3. the footer "Reported in-app by `<display name>` · report #`<id>`".
- **Never in the body:** the email, IDs other than the report id, or tokens.
- **Form warning:** the form says that the text will be public.

### D6. Future CONTRIBUTOR

- **v1:** the endpoints use `hasRole('ADMIN')`.
- **When CONTRIBUTOR is added:**
  - insert it into `UserRole` between `USER` and `ADMIN`;
  - change the endpoints to `hasRole('CONTRIBUTOR')`;
  - add a migration if the `role` column has a check constraint;
  - leave the auto-label rule (SUPER_ADMIN only) unchanged.

### D7. Issue state by webhook (amendment, 2026-10-21)

The owner asked for the closed state to be pushed to the app and stored, and to tell "done" from "closed, not planned".

- **Endpoint:** `POST /v1/funny-app/webhooks/github`, added to `WHITE_LIST_PATH` (`utils/AppConstant.java:45`), so no JWT. GitHub is the only intended caller.
- **Authentication:** the HMAC-SHA256 signature in `X-Hub-Signature-256` over the **raw body**, with the shared secret from the env var `GITHUB_WEBHOOK_SECRET`.
  - Compared in constant time (`MessageDigest.isEqual`) before the body is parsed.
  - Missing or wrong signature → 401. Secret not configured → 503, so the endpoint is off until the owner sets it.
  - The secret is never logged; the delivery id (`X-GitHub-Delivery`) is logged at INFO.
- **Events:** only `X-GitHub-Event: issues` with action `closed` or `reopened`. `ping` and every other event or action → 204 and nothing stored.
- **Storage:** new columns on `app_report`: `github_state` (`OPEN`/`CLOSED`), `github_state_reason` (`completed`, `not_planned`, `duplicate`, `reopened`, or null), `github_state_at` (the issue's `updated_at` from the payload), plus an index on `issue_number`.
  - The row is found by `issue_number` among `CREATED` reports. An issue that was not created by the app → 204, nothing stored.
  - **Out-of-order deliveries:** an event is applied only when its `updated_at` is newer than the stored `github_state_at`.
- **List:** a row with a stored state uses it, with no GitHub call. A row without one (created before D7, or no event yet) keeps the D4 read-through.
- **Missed deliveries:** GitHub does not retry a failed delivery on its own. The stored state can then stay stale until the next event or a manual redeliver from the repo's webhook settings. Accepted.
- **Owner setup:** repo Settings → Webhooks → payload URL above, content type `application/json`, secret, "Issues" events only; `GITHUB_WEBHOOK_SECRET` in the VM's env file, then restart.

#### Options considered for D7

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| **A. Repo webhook, HMAC-verified, state stored on `app_report`** | Push, no polling; `state_reason` comes with the event; one public endpoint | A new unauthenticated endpoint (guarded by HMAC); a second secret; GitHub does not retry | **Chosen** |
| B. Scheduled job polls GitHub for open reports | No public endpoint | Uses API quota; state is late by up to the poll interval; another job | Rejected |
| C. Keep read-through only, add `state_reason` to the GET | Smallest change | Nothing stored; still one call per row on a cold cache | Rejected: the owner wants the state stored |

## Consequences

### Positive

- **One click** from seeing a problem to an autopilot run, and the issue carries its context.
- **Small footprint:**
  - no new library, no new infrastructure, no workflow change;
  - one new client, one table, one controller.
- **Least-privilege credential:** easy to rotate by replacing the env var.

### Negative

- **The owner-only gate of ADR-0022 is wider.** Any ADMIN can start paid runs, capped at 10 a day. An admin account compromise now reaches autopilot; prod is still reachable only through `autopilot:auto`, which is SUPER_ADMIN only.
- **The PAT lives on the server** alongside other secrets. A server compromise leaks it, limited to creating and reading issues in one repo.
- **Status calls add latency** to the list: up to one GitHub call per row on a cold cache.
- **D7 adds a public endpoint.** Its only guard is the HMAC secret; a leaked secret lets anyone set report states (display only, no other effect). Rotate it in both GitHub and the env file.
- **The per-user limit is in memory.** It resets on deploy, which is acceptable at one replica. Moving to Redis follows ADR-0003 if the app scales out.

## Validation

- An integration test against a stubbed GitHub (local `HttpServer`, like `HttpRedirectProbeTest`) checks:
  - the labels for ADMIN, for SUPER_ADMIN with auto labels, and over the daily cap;
  - FAILED and retry;
  - the marker in the body.
- Permission tests:
  - USER gets 403;
  - ADMIN sending an auto label gets 403;
  - SUPER_ADMIN gets 201.
- A prod check by the owner: a report from the app creates issue N with `autopilot`, and the plan job starts.

## Links

- PRD: [PRD-in-app-report](../prd/PRD-in-app-report.md)
- GitHub REST: create an issue, `POST /repos/{owner}/{repo}/issues`
- Patterns: `client/HttpRedirectProbe.java`, `aop/RateLimitAspect.java`, `filter/WebSecurityConfig.java:81-88`

## Changelog

| Date | Change |
|------|--------|
| 2026-10-20 | Proposed |
| 2026-10-20 | Accepted by the owner |
| 2026-10-21 | D7: issue state by HMAC-verified webhook, stored on `app_report` (owner request) |
