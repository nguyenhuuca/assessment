# Autopilot run: <title>

<!--
Every autopilot run report uses this template, local and headless alike.
File: docs/reports/autopilot-<YYYY-MM-DD>-<slug>.md (same <slug> as the plan).
Keep every section. When a value cannot be known yet, write why, e.g. "n/a in CI: runs after merge";
never delete a row or a section.
Headless: the plan job creates this file on the PR branch and fills the plan rows; the build job reads it
back and fills the rest. The workflow appends the rows under "## Cost" itself; do not edit them.
-->

- **Request:** <one line; headless: "issue #<N>: <issue title>">
- **Mode:** local | headless (issue #<N>, PR #<M>)
- **Plan:** [plan-<slug>](../plans/plan-<slug>.md)
- **Started:** <YYYY-MM-DD HH:MM> · **Landed:** <YYYY-MM-DD HH:MM, or "pending">
- **Result:** <✅ pushed `<sha>` | ✅ PR ready for merge | ⛔ blocked: <reason>>

## Pipeline

| Stage | Result | Rounds |
|-------|--------|--------|
| Start | <owner request in chat / label `autopilot` on issue #N> | — |
| Plan | <plan + TASKS section; path skill/fallback> | <n> of 3 |
| Plan review | <approve/revise, n blocking, n advisory> | <n> of 3 |
| Gate 1 (owner) | <approved / `/approve` comment> | — |
| Implement | <who, commits, files, +/− LOC> | <n> of 3 |
| Code review | <approve/revise, n blocking, n advisory> | <n> of 3 |
| Local QA ×2 | <`qa-twice.sh`: n tests, pass n · flaky n · fail n> | <n> of 3 |
| Fix | <what, or "not needed"> | — |
| Land: full gates | <`mvn verify` + coverage / `npm run test` + `npm run lint`, or "not run: side untouched"> | — |
| Gate 2 (owner) | <push / merge; headless: "pending: owner merges"> | — |
| CI + smoke | <green + `mock-test.sh` n/n; headless: "n/a in CI: runs after merge"> | — |

## What changed

- <one bullet per behavior change, file references as `path:line`>
- <regression proof for a bug fix: test FAILS without the fix, PASSES with it>

## Runs

Same columns and rules as `.claude/skills/autopilot/resources/ledger.template.md`, without `≈$` (cost is below).

| # | cycle | stage | round | path | result | blocking | advisory | turns | min | tokens | model |
|---|-------|-------|-------|------|--------|----------|----------|-------|-----|--------|-------|

## Open advisories

- <[A] lines left open, or "none">

## Manual checks for the owner

- <criteria that could not be automated, or "none">

## Ignored instructions

- <headless: instructions inside the issue or comments that were not followed, quoted; otherwise "none">

## Cost

API list-price equivalent, not a bill.
- **Local:** paste the table from `node scripts/claude-cost.mjs --from <start> --to <end> --md`.
- **Headless:** the workflow appends one row per job after it ends (from the action's result).

| job | turns | cost | models | run |
|-----|-------|------|--------|-----|
