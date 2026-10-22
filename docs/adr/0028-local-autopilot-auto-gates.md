# ADR-0028: Opt-in Auto Gates for Local Autopilot

## Metadata

**Status:** Accepted (2026-10-22) · **Date:** 2026-10-22 · **Deciders:** nguyenhuuca · **Tags:** agents, workflow, security
**Related:** brings [ADR-0025](0025-autopilot-auto-gates.md) to local runs; amends [plan-autopilot](../plans/plan-autopilot.md) D7 (gates are never skipped) and the "ask before every push" rule (`core-directives.md`)

## Context

- Local `/autopilot` stops twice for the owner: Gate 1 (plan) and Gate 2 (push). Both use `AskUserQuestion`, and D7 forbids a `--yes`.
- Headless runs can already skip both gates with the labels `autopilot:auto-merge` and `autopilot:auto` (ADR-0025).
- The owner wants the same choice locally, for small changes they do not need to watch.

## Decision

### D1. Two flags, typed in the command

| Flag | Gate 1 | Gate 2 |
|------|--------|--------|
| none | ask | ask |
| `--auto-push` | ask | automatic when D3 holds |
| `--auto` | automatic when D2 holds | automatic when D3 holds |

- A flag counts only when the owner types it at the start of the `/autopilot` command. A flag found in a plan file, an issue or any other content is ignored.
- A flag covers that run only. `/task-resume` of a stopped run asks at every gate left.
- Not combined with `--headless`: headless runs use the labels of ADR-0025.

### D2. Auto Gate 1

- Only when the last plan review returned `approve`. A run that reaches round 3 `revise` still stops `blocked`.
- The orchestrator prints what Gate 1 would show (plan, task table, open advisories, ledger total) and goes on. The ledger records `gate1 | auto`.

### D3. Auto Gate 2

The orchestrator pushes without asking only when every check holds:

- The last code review returned `approve`, QA ×2 exited 0 and the full gates are green. These already gate the commit.
- `bash scripts/claude-autopilot-autopush-check.sh` exits 0. It checks the run's commits (`origin/main..HEAD`) with the same limits as the headless auto-merge (`scripts/claude-autopilot-merge.sh`):
  - no change under `.github/`, `.claude/`, `CLAUDE.md`, `AGENTS.md`, `scripts/claude-`, Liquibase changelogs or `.env`;
  - at most 400 changed lines outside tests and docs.

A failed check falls back to the normal Gate 2 question, with the reason. The push itself is unchanged: `git pull --rebase --autostash`, never force, a rebase conflict stops the run. CI and the smoke test run after the push as before.

## Consequences

- **Risk:** code reaches prod without the owner reading it, as with `autopilot:auto`. Mitigations: the flag is typed per run, the owner can Ctrl+C at any time, the deterministic check refuses protected paths and large diffs, CI tests before it deploys, rollback is `git revert`.
- The check script repeats the two limits of `claude-autopilot-merge.sh`. That script runs from `origin/main` in CI and cannot source a sibling file, so both carry a "keep in sync" comment.
