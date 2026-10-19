# Plan: Headless Autopilot (GitHub Actions)

<!--
Implementation Plan
Filename: docs/plans/plan-autopilot-headless.md
Owner: Builder
Handoff to: Owner (secret + first run)
-->

## Overview

**Status:** Done 2026-10-19 · first run issue #26 → PR #27 merged (`b0a2d7a1`)
**Author:** nguyenhuuca
**Date:** 2026-10-19
**ADR:** [ADR-0022](../adr/0022-headless-autopilot.md) (gates on GitHub, owner-only triggers, least privilege)
**Related plan:** [plan-autopilot](plan-autopilot.md) (the local skill this extends)
**Size:** Small (~half a day)

## Objective

Start `/autopilot` from GitHub and approve it there, without a terminal:
1. **Start:** the owner adds the label `autopilot` to an issue.
2. **Plan:** a draft PR opens with the plan.
3. **Gate 1:** the owner comments `/approve`.
4. **Build:** the PR gets the code, tests and report.
5. **Gate 2:** the owner merges, which deploys through the normal CI.

## Owner Decisions (2026-10-19)

| Question | Choice |
|----------|--------|
| Auth | Subscription token, `CLAUDE_CODE_OAUTH_TOKEN` (`claude setup-token`) |
| Gates | Branch + PR. Gate 1 = `/approve` comment, Gate 2 = merge |
| Limits | 60 min per job, one run at a time |

## Tasks

| ID | Task | Acceptance criteria | Status |
|----|------|---------------------|--------|
| **AH-1** | ADR-0022 | Decision drivers, D1–D5, compliance checks | done |
| **AH-2** | `.github/workflows/autopilot.yml` | Plan job triggers on label `autopilot`, or on `/revise` on an `autopilot/*` PR. Build job triggers on `/approve`. Both require sender == owner. Only numbers and ids reach `prompt` and `run:`. Only `CLAUDE_CODE_OAUTH_TOKEN` is referenced. 60 min timeout, concurrency 1 | done |
| **AH-3** | `SKILL.md` headless mode | The three `--headless` forms. Untrusted-input rules and path denylist. Gates mapped to draft PR / merge. No push to `main` | done |
| **AH-4** | Guide vi/en: headless section | How to set up, how to run, what is refused | done |
| **AH-5** (owner) | Setup | Run `claude setup-token`, then `gh secret set CLAUDE_CODE_OAUTH_TOKEN`; create the `autopilot` label | done |
| **AH-QA-1** (owner) | First run on a tiny issue | Label → draft PR with plan → `/approve` → PR ready with report → merge → CI green | done: #26 → #27. It took 3 plan runs and needed 3 workflow fixes |

## Risks

| Risk | Mitigation |
|------|------------|
| Prompt injection in an issue the owner labels | ADR-0022 D3 path denylist, both human gates, and the diff is reviewed before merge |
| The action rejects the run (auth, permissions) | First run on a tiny issue (AH-QA-1); the action log shows the reason |
| The plan job and the build job share no conversation context | The build job re-reads the plan and `TASKS.md` from the branch |
