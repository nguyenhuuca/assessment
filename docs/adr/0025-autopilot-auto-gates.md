# ADR-0025: Opt-in Auto Gates for Headless Autopilot

## Metadata

**Status:** Accepted (2026-10-20) · **Date:** 2026-10-20 · **Deciders:** nguyenhuuca · **Tags:** ci, agents, security, workflow
**Related:** amends [ADR-0022](0022-headless-autopilot.md) D1 (both gates human) and D4 (only one secret)

## Context

- Headless autopilot (ADR-0022) stops twice for the owner: `/approve` at Gate 1 and a merge at Gate 2.
- The owner wants small issues to go through when they are busy, with no approval.
- Two GitHub limits shape the design:
  - **Comments posted with the job token start no workflows**, so a bot `/approve` cannot start the build job. `workflow_dispatch` is the exception: the job token can start it.
  - **A merge made with the job token does not start the push-to-`main` deploy CI**. The merge needs another token.
- The repo is **public**, so the checks that decide a merge must not depend on what the model says.

## Decision

### D1. Opt-in labels on the issue

| Label (added with `autopilot`) | Gate 1 | Gate 2 |
|------|--------|--------|
| none | `/approve` | owner merges |
| `autopilot:auto-merge` | `/approve` | automatic |
| `autopilot:auto` | automatic | automatic |

- The labels only change gates. `autopilot` is still the only label that starts a run.
- The issue author check from ADR-0022 D2 still applies. In this repo only the owner can add labels.

### D2. Auto Gate 1: the workflow dispatches the build

- **Marker:** the plan job writes `<!-- autopilot:plan-review=approve -->` in the PR body, and only when the plan review returned `approve`.
- **Workflow step:** after the plan job, a workflow step checks two things:
  - the issue has `autopilot:auto`;
  - the open `autopilot/issue-<N>` PR has the marker.
- **If both hold:** the step runs `gh workflow run autopilot.yml -f pr=<N>` and comments on the PR.
- **If the marker is missing:** it comments that Gate 1 stays manual.
- **Scope:** only a first plan (the `issues` event) dispatches. A `/revise` always waits for `/approve`.
- **Who can start the build:** the dispatched build accepts the actor `github-actions[bot]` or the owner. `allowed_bots: "github-actions[bot]"` is set only on the build step.

### D3. Auto Gate 2: a deterministic merge script

`scripts/claude-autopilot-merge.sh` is always run from `origin/main`, never from the PR branch. It merges only when every check passes; otherwise it comments the reason and leaves the PR for the owner. The checks:
1. The head branch is `autopilot/issue-<N>`.
2. The issue or PR has `autopilot:auto-merge` or `autopilot:auto`.
3. The PR is open and **not a draft**. The model marks it ready only after a code-review `approve` and green gates.
4. **No protected paths changed:**
   - `.github/`, `.claude/`, `CLAUDE.md`, `AGENTS.md`, `scripts/claude-*`;
   - Liquibase migrations (`api/src/main/resources/db/changelog/`);
   - `.env*` files.
   - A schema change always gets a human.
5. At most **400 changed lines** outside tests and docs (`AUTOPILOT_MAX_LOC`).
6. **The gates are re-run by the script**, not taken from the model's report:
   - `mvn verify` when `api/` changed;
   - `vitest run`, lint and build when `webapp/` changed.
7. **The merge:** `gh pr merge --squash --match-head-commit <sha>` with the secret `AUTOPILOT_MERGE_TOKEN`. Its push starts the normal deploy CI.

### D4. The merge token

- **Token type:** a fine-grained PAT (or GitHub App token) created and stored by the owner. Scope: this repo only, with `Contents: read/write` and `Pull requests: read/write`.
- **Exposure:** only the auto Gate 2 step sees it, through the step-level `env`. The Claude step never does.
- **If the secret is missing:** the script comments and skips. Everything else keeps working.

## Consequences

- **Code reaches prod without a human read.** The limits on that are:
  - the opt-in label;
  - two model reviews;
  - the re-run gates;
  - no migrations and no workflow or agent-config changes.
- **The smoke check after the deploy stays manual** (`./api/mock-test.sh`). Smoke tests are read-only, so write paths are not covered.
- **ADR-0022 D4's "both gates are human" no longer holds for labelled issues.** A prompt injection in a labelled issue body could now reach prod without anyone seeing it. The owner-only issue check (ADR-0022 D2) is what keeps that out.
- **Rolling back** = `git revert` the squash commit on `main` and push.
