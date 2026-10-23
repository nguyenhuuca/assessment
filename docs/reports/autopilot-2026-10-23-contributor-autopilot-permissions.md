# Autopilot run: Contributor role and autopilot permissions for reports

- **Request:** `/autopilot docs/plans/plan-contributor-autopilot-permissions.md` (plan mode, CA-1..CA-6)
- **Mode:** local
- **Plan:** [plan-contributor-autopilot-permissions](../plans/plan-contributor-autopilot-permissions.md)
- **Started:** 2026-10-23 21:14 · **Landed:** pending (Gate 2)
- **Result:** pending Gate 2 (push)

## Pipeline

| Stage | Result | Rounds |
|-------|--------|--------|
| Start | owner request in chat (`/autopilot <plan>`) | — |
| Plan | reused: plan mode, 6 open tasks (written by `/swarm-plan` from the spec) | — |
| Plan review | approve, 0 blocking, 5 advisory (all folded into plan/spec before Gate 1) | 1 of 3 |
| Gate 1 (owner) | approved | — |
| Implement | 2 worker-builders in worktrees, in parallel: BE CA-1..CA-3 and FE CA-4..CA-6; cherry-picked onto `main` as 8 commits (`e1eeb892`..`102fced0`); 37 files +943/−96 (non-test +214/−73) | 1 of 3 |
| Code review | BE (security + quality): approve, 0 blocking, 3 advisory. FE (quality): approve, 0 blocking, 2 advisory | 1 of 3 |
| Local QA ×2 | `qa-twice.sh`: 290 tests, pass 290 · flaky 0 · fail 0 | 1 of 3 |
| Fix | the 5 code-review advisories were applied as tests and a small refactor (`9f82a9c5`, `102fced0`) | — |
| Land: full gates | `mvn verify`: 1640 tests, 0 failures, 2 skipped, line coverage 96.70% (threshold 90%). `npm run test`: 583/583. `npm run lint`: 0 errors, 3 old warnings | — |
| Gate 2 (owner) | pending | — |
| CI + smoke | pending: runs after the push | — |

## What changed

- **Role:** `UserRole.CONTRIBUTOR` sits between USER and ADMIN. Its default permission mask is 0 (`enums/UserRole.java`).
- **Permission bits:** `Permission` adds `AUTOPILOT_AUTO_MERGE` (32) and `AUTOPILOT_AUTO` (64). `ALL_BITS` stays 31, `AUTOPILOT_BITS` is 96, and `allowsAutoLabel(mask, label)` implements BR-8 (`enums/Permission.java`).
- **Security:**
  - `/v1/funny-app/admin/reports/**` is open to CONTRIBUTOR and above, and is registered before `/admin/**` (`filter/WebSecurityConfig.java:55-56`).
  - `AdminReportController` uses `hasRole('CONTRIBUTOR')` and drops `@HasPermission(ADMIN)`.
  - The real-chain test checks that a CONTRIBUTOR gets 403 on the 7 other admin paths and on path tricks.
- **Accounts:**
  - Only the SUPER_ADMIN can change the autopilot bits.
  - The bits are refused on a USER.
  - A role change keeps them for CONTRIBUTOR and ADMIN, and clears them for USER (`service/impl/AdminAccountServiceImpl.java`).
- **Reports:**
  - The label check reads the DB user, not the JWT or the Harness flag. A refused label gets 403 and no row is saved.
  - A retry downgrades the label.
  - The cap counts ADMIN and CONTRIBUTOR together.
  - The issue body says "a CONTRIBUTOR".
  - These rules are in `service/impl/ReportServiceImpl.java` and `ReportIssueContent.java`. The changeset is `202610230002-app-report-cap-contributor.sql`.
- **Frontend:**
  - `userCanReport` and `allowedAutoLabels` are new.
  - AuthContext keeps `permissions`.
  - CONTRIBUTOR gets the Report menu and page and the Admin notification tab, but not the Admin menu.
  - The report form offers only the allowed labels.
  - The account table gets a CONTRIBUTOR rank and badge, a role select for the SUPER_ADMIN (USER ⇄ CONTRIBUTOR for an ADMIN), and SUPER_ADMIN-only autopilot toggles.

## Runs

| # | cycle | stage | round | path | result | blocking | advisory | turns | min | tokens | model |
|---|-------|-------|-------|------|--------|----------|----------|-------|-----|--------|-------|
| 1 | 1 | plan | — | reused | plan mode, 6 open tasks | — | — | — | — | — | — |
| 2 | 1 | plan review | 1 | fallback (worker-reviewer) | approve | 0 | 5 | 22 | 1 | 67,277 | sonnet |
| 3 | 1 | gate 1 | — | owner | approved | — | — | — | — | — | — |
| 4 | 1 | implement CA-4..CA-6 (FE) | 1 | fallback (worker-builder, worktree) | tests green, lint 0 errors | — | — | 72 | 11 | 98,959 | sonnet |
| 5 | 1 | implement CA-1..CA-3 (BE) | 1 | fallback (worker-builder, worktree) | mvn verify green | — | — | 43 | 14 | 126,126 | sonnet |
| 6 | 1 | code review BE | 1 | fallback (worker-reviewer, security+quality) | approve | 0 | 3 | 6 | 1 | 54,707 | sonnet |
| 7 | 1 | code review FE | 1 | fallback (worker-reviewer, quality) | approve | 0 | 2 | 4 | 1 | 44,588 | sonnet |
| 8 | 1 | QA specs (review advisories) | 1 | fallback (same worker-builders) | BE 6 tests added, FE refactor | — | — | 10 | 3 | — | sonnet |
| 9 | 1 | local QA ×2 | 1 | `qa-twice.sh` | pass 290 | — | — | — | 4 | — | — |
| 10 | 1 | land: full gates | — | orchestrator | green | — | — | — | 6 | — | opus |

The implement and QA-spec stages ran through `worker-builder` workers (the fallback path) in isolated worktrees, one per side. The orchestrator rebased `main` onto `origin/main` (resolving a `TASKS.md` append conflict with #65) and cherry-picked the 8 worker commits.

## Open advisories

None. All 10 were applied:
- plan review (5): line refs, the `includeAll` wording, the rollout rule, CA-6 after CA-2, the LOC estimate;
- code review BE (3): path-trick, mask-128 and contributor cap-boundary tests;
- code review FE (2): a single `userCanReport` entry point, and `labelKey` on the autopilot bits.

## Manual checks for the owner

- After deploy, set a test account to CONTRIBUTOR and grant `AUTOPILOT_AUTO_MERGE`. Send a report with "Auto-merge", and check that the GitHub issue has `autopilot` and `autopilot:auto-merge`.
- Revoke the bit. The report form then shows no autopilot option.
- Check visually that a CONTRIBUTOR sees Report but no Admin menu (desktop and mobile).
- Run `./api/mock-test.sh` after the deploy.
- **Rollback note:** before reverting CA-1, set every CONTRIBUTOR back to USER.

## Ignored instructions

- None (local run).

## Cost

API list-price equivalent, not a bill. Window 2026-10-23T14:14:45Z → 14:38:38Z.

| who | model | requests | input | cache write | cache read | output | cost |
|---|---|---|---|---|---|---|---|
| main session (orchestrator) | claude-opus-5-5 | 35 | 88 | 52,296 | 9,604,022 | 21,545 | $2.77 |
| worker-reviewer: Code review frontend diff | claude-sonnet-5-5 | 5 | 12 | 43,435 | 129,971 | 4,352 | $0.17 |
| worker-builder: Implement backend CA-1..CA-3 | claude-sonnet-5-5 | 42 | 86 | 128,925 | 3,500,070 | 37,530 | $1.05 |
| worker-reviewer: Plan review round 1 | claude-sonnet-5-5 | 5 | 10 | 65,354 | 193,909 | 6,569 | $0.25 |
| worker-reviewer: Code review backend diff | claude-sonnet-5-5 | 6 | 14 | 53,015 | 182,417 | 6,082 | $0.21 |
| worker-builder: Implement frontend CA-4..CA-6 | claude-sonnet-5-5 | 42 | 86 | 101,680 | 2,888,452 | 31,853 | $0.86 |
| **TOTAL** |  | **135** | **296** | **444,705** | **16,498,841** | **107,931** | **$5.31** |

| job | turns | cost | models | run |
|-----|-------|------|--------|-----|
| local | 135 requests | $5.31 | opus 5.5 + sonnet 5.5 | 2026-10-23-contributor-autopilot-permissions |
