# Autopilot run: Report issue state by GitHub webhook

- **Request:** GitHub webhook stores report issue state (ADR-0026 D7, spec v1.3 BR-9, BR-10, FR-8, EC-14..EC-17)
- **Mode:** local
- **Plan:** [plan-report-webhook-state](../plans/plan-report-webhook-state.md)
- **Started:** 2026-10-21 09:15 · **Landed:** pending (Gate 2)
- **Result:** committed `5582b2fc` (BE) + `90dd0a78` (FE), waiting for push approval

## Pipeline

| Stage | Result | Rounds |
|-------|--------|--------|
| Start | owner request in chat (`/autopilot`), after ADR-0026 D7 + spec v1.3 were written (`92b4dd36`) | — |
| Plan | plan + TASKS section, written inline by the orchestrator (swarm-plan method) | 2 of 3 |
| Plan review | round 1 revise (1 blocking: `@Modifying` without a transaction; 4 advisory, all folded in); round 2 approve (1 advisory, check order pinned) | 2 of 3 |
| Gate 1 (owner) | approved | — |
| Implement | orchestrator inline; 2 commits; 15 non-test files +300/−4, 6 test files +434/−5 | 1 of 3 |
| Code review | approve, 0 blocking, 4 advisory (2 fixed, 2 accepted in the spec) | 1 of 3 |
| Local QA ×2 | `qa-twice.sh`: 115 tests, pass 115 · flaky 0 · fail 0 | 1 of 3 |
| Fix | review advisories: `issue.number < 1` → 400; fixed-text WARN on 401 | — |
| Land: full gates | `mvn verify`: 1,548 tests, 0 fail, LINE coverage 96.65 % (≥ 90 %); `npm run test`: 529 pass; `npm run lint`: 0 errors (3 old warnings outside `report/`) | — |
| Gate 2 (owner) | pending | — |
| CI + smoke | pending: runs after push | — |

## What changed

- New migration `api/src/main/resources/db/changelog/sql/202610210001-app-report-github-state.sql`: nullable `github_state`, `github_state_reason`, `github_state_at` columns + partial index on `issue_number`.
- `POST /v1/funny-app/webhooks/github` (`web/GitHubWebhookController.java`), JWT-free via `WHITE_LIST_PATH` (`utils/AppConstant.java`). Check order: 503 (no secret) → 413 (> 1 MiB) → 401 (HMAC) → 400 (payload) → 204.
- `service/impl/GitHubWebhookServiceImpl.java`: HMAC-SHA256 over the raw body, constant-time compare; only `issues` `closed`/`reopened` for this repo; `state_reason` whitelist; logs delivery, event, action, issue and outcome as sanitised tokens only.
- `repo/AppReportRepository.java`: `@Transactional @Modifying` conditional update that only applies newer `updated_at` (EC-14, EC-16).
- `service/impl/ReportServiceImpl.java` `resolveStates`: a stored state wins, with no GitHub call; other rows keep the read-through. `ReportDto.githubStateReason` added.
- `webapp/src/components/report/ReportPage.jsx` `StateBadge`: "Closed · Not planned"; new `stateReason.*` keys in en/vi `report.json`.

## Runs

| # | cycle | stage | round | path | result | blocking | advisory | turns | min | tokens | model |
|---|-------|-------|-------|------|--------|----------|----------|-------|-----|--------|-------|
| 1 | 1 | plan | 1 | inline (orchestrator) | plan written | — | — | — | 8 | — | opus |
| 2 | 1 | plan review | 1 | fallback (worker-reviewer) | revise | 1 | 4 | 26 | 1 | 59,626 | sonnet |
| 3 | 1 | plan | 2 | inline | findings folded in; spec 413 + reason whitelist | — | — | — | 2 | — | opus |
| 4 | 1 | plan review | 2 | fallback (worker-reviewer) | approve | 0 | 1 | 30 | 2 | 63,906 | sonnet |
| 5 | 1 | gate 1 | — | — | approved | — | — | — | — | — | — |
| 6 | 1 | implement WH-1..WH-4 | 1 | inline (orchestrator) | tests green | — | — | — | 12 | — | opus |
| 7 | 1 | code review | 1 | fallback (worker-reviewer, quality+security) | approve | 0 | 4 | 14 | 1 | 51,566 | sonnet |
| 8 | 1 | QA ×2 | 1 | `qa-twice.sh` | pass 115 | — | — | — | 2 | — | — |
| 9 | 1 | land: full gates | — | orchestrator | green | — | — | — | 3 | — | — |

The plan and implement stages ran inline instead of through `swarm-plan` / `swarm-execute` workers, because the orchestrator already held the code facts from writing ADR-0026 D7 and the spec.

## Open advisories

- [A] Same-second close + reopen: GitHub's `updated_at` has second resolution, so the second event is `stale`. Accepted and noted in the spec (EC-16).
- [A] No per-IP rate limit on the public endpoint; the 1 MiB cap and HMAC keep per-request cost small. Accepted and noted in the spec.

## Manual checks for the owner

- WH-5: repo Settings → Webhooks → Add: URL `https://canh-labs.com/api/v1/funny-app/webhooks/github`, content type `application/json`, a random secret, "Let me select individual events" → Issues only.
- Add `GITHUB_WEBHOOK_SECRET=<same secret>` to `/opt/funnyapp/.env` on the VM (`chmod 600`), then `./stopServer.sh && ./startServer.sh`.
- In "Recent Deliveries": `ping` → 204. Close an issue that was created from the app → 204, and the Report list shows "Closed · Completed" right away. Redeliver the same event → 204 with `outcome=STALE` in the log.
- The conditional UPDATE runs against PostgreSQL only on prod (no DB test infra), so this close/redeliver check is its real test.

## Ignored instructions

- none

## Cost

API list-price equivalent, not a bill.
- **Local:** `node scripts/claude-cost.mjs --from 2026-10-21T02:15:07Z --to 2026-10-21T02:32:58Z --md`

| who | model | requests | input | cache write | cache read | output | cost |
|---|---|---|---|---|---|---|---|
| main session (orchestrator) | claude-opus-5-5 | 57 | 120 | 89,437 | 11,120,855 | 48,360 | $3.91 |
| worker-reviewer: Code review webhook diff | claude-sonnet-5-5 | 8 | 16 | 50,031 | 285,829 | 454 | $0.16 |
| worker-reviewer: Plan review webhook state | claude-sonnet-5-5 | 9 | 20 | 63,221 | 368,224 | 4,952 | $0.24 |
| **TOTAL** |  | **74** | **156** | **202,689** | **11,774,908** | **53,766** | **$4.31** |
