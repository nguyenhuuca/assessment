# Plan: `/autopilot` Skill (Plan → Review → Build → QA → Land)

<!--
Implementation Plan
Filename: docs/plans/plan-autopilot.md
Owner: Builder (/swarm-execute)
Handoff to: Builder, QA Engineer
-->

## Overview

**Status:** Built 2026-10-19 · AU-1, AU-3 `f8fa31ce`; AU-2, AU-4, AU-5 `d52f6b2e`; QA-1 first run 2026-10-19 → [report](../reports/autopilot-2026-10-19-suggestion-rereview.md)
**Author:** nguyenhuuca
**Date:** 2026-10-19
**ADR:** none. This is dev tooling inside `.claude/`, it changes no app code or stored data, and deleting the skill folder undoes it (`swarm-plan` Artifact Requirements). The existing skills, agents, and the "ask before push" rule stay as they are.
**Size:** Small feature (~1 day), so this plan is the only artifact (no PRD).

## Objective

One user-invoked skill, `/autopilot <feature request>`, runs the whole workflow as a chain. A human is needed at only **three gates**:

```
Start ─▶ Plan ⇄ Plan review ─▶ [GATE 1: you approve plan] ─▶ Implement ⇄ Code review
      ─▶ Local QA (tests ×2) ⇄ Fix ─▶ Commit on main ─▶ [GATE 2: you approve push] ─▶ CI + smoke ─▶ Done
```

- Each loop (`⇄`) has a cap of **3 rounds**. When a loop reaches the cap, the run stops with `status: blocked` and asks the user. It never loops forever.
- Reviews return a verdict with **blocking / advisory** counts. Only a blocking finding sends the work back.
- QA runs the affected tests **twice**:
  - Fails once, passes once → **flaky**. Logged, does not block.
  - Fails both times → **fail**, goes to Fix.
- Every step is recorded in a **run ledger**: turns, duration, tokens. At the end the run prints a summary table like the Autopilot screenshot.
- The **final step is a commit on `main`**, not a draft PR (core-directives: trunk-based, CI deploys from `main`). The push still needs the user's go-ahead.

## Verified Facts (exploration 2026-10-19)

| Area | Fact | Impact |
|------|------|--------|
| Skills | 15 skills had `disable-model-invocation: true`; the 5 autopilot uses were switched to `false` on 2026-10-19 (owner may flip back any time) | Primary path calls them via the Skill tool; fallback (D1) runs a worker that reads the SKILL.md when the flag is `true` again |
| Workers | `worker-builder` sonnet/90 turns, `worker-reviewer` sonnet/60, `worker-explorer` haiku/30, `worker-architect` opus/40. None has `Agent` in its tools | The orchestrator is the main session, so there is no nesting (agent-constraints). Model tiering stays in the agent frontmatter |
| Verdict | `swarm-review` has an Approve / Request Changes framework but no machine-readable output | Add a small verdict contract that the orchestrator can parse (D2) |
| Landing | AGENTS.md "Landing the Plane": quality gates → commit → **ask** → push → CI → smoke (`./api/mock-test.sh`) | Autopilot Gate 2 = this protocol, reused unchanged |
| Tracking | `session.txt` rules (`.claude/rules/session-file.md`), `TASKS.md` per-feature section | Autopilot writes both after each stage, so `/task-resume` can continue a stopped run |
| Cost data | Agent completion results report tool uses, tokens and duration. There is no $ figure | The ledger records turns, minutes and tokens. $ is an **estimate** from tokens × the model price table, labelled "≈" |

## Design Decisions

- **D1 — Call skills directly, fall back to workers.** On 2026-10-19 five skills were set to `disable-model-invocation: false` so the orchestrator can call them with the Skill tool: `swarm-plan`, `swarm-review`, `swarm-execute`, `qa-engineer`, `land-the-plane`.

  | Stage | Primary: Skill tool | Fallback: worker reads the SKILL.md |
  |-------|---------------------|-------------------------------------|
  | Plan | `swarm-plan` | `worker-architect` (opus) if an ADR is needed, otherwise `worker-builder` |
  | Plan review / code review | `swarm-review` | `worker-reviewer` |
  | Implement / Fix | `swarm-execute` | `worker-builder` |
  | Local QA specs | `qa-engineer` | `worker-builder` (+ `testing` skill) |
  | Land | `land-the-plane` | Orchestrator follows AGENTS.md "Landing the Plane" itself |

  **Fallback rule.** The fallback is used for a stage when its skill is unavailable:
  - the owner set the flag back to `true`;
  - the Skill tool refuses the call;
  - the skill is missing.

  The fallback worker gets "Read `.claude/skills/<name>/SKILL.md` and follow its method" plus the stage input, and must end with the same verdict contract (D2). The run ledger records which path each stage used (`skill` / `fallback`), so flipping the flag never breaks a run. Either way, the rules stay in one place (DRY).
- **D2 — Verdict contract.** Every review worker ends its reply with a fenced block, and the orchestrator parses only that block:
  ```
  VERDICT: approve | revise
  BLOCKING: <n>
  ADVISORY: <n>
  - [B] file:line — finding
  - [A] file:line — finding
  ```
  `revise` with `BLOCKING: 0` is treated as `approve`. Advisories go into the ledger and the summary but never trigger a loop.
- **D3 — Round caps.** Plan⇄review ≤ 3, implement⇄review ≤ 3, QA⇄fix ≤ 3. Going over a cap means `blocked`, with a summary of what failed.
- **D4 — Flaky detection script.** `scripts/qa-twice.sh` takes the backend test classes and webapp test files to run.
  - It runs each set twice: `mvn -q test -Dtest=…` and `npx vitest run …`.
  - It prints `pass` / `flaky` / `fail` per test.
  - The full gates (`mvn verify`, `npm run test`, `npm run lint`) still run once before the commit.
- **D5 — Ledger.** The ledger lives at `scratchpad/autopilot/<YYYYMMDD-HHMM>-<slug>.md` (ephemeral) and holds one row per worker run: stage, round, verdict, turns, min, tokens, ≈$. The run report (results + ledger + cost) goes to `docs/reports/autopilot-<YYYY-MM-DD>-<slug>.md`, so a durable record stays in `docs/`. The plan artifact is not edited for it (owner, 2026-10-19).
- **D6 — Scope guard.** Autopilot refuses tasks that need a PR-FAQ or PRD (Medium/Large features per `swarm-plan`) and points to `/scope`. It targets small features and fixes.
- **D7 — Gates are never skipped.** Gate 1 (plan) and Gate 2 (push) always use `AskUserQuestion`. There is no `--yes` flag. This follows the standing "ask before every push" rule.

## Tasks

| ID | Task | Acceptance criteria | Depends on |
|----|------|---------------------|------------|
| **AU-1** | `.claude/skills/autopilot/SKILL.md`: stages, gates, round caps, worker prompt templates, scope guard | `disable-model-invocation: true`; every stage names its worker + referenced skill; caps = 3; both gates use `AskUserQuestion`; no rule text copied from other skills | — |
| **AU-2** | `.claude/skills/autopilot/resources/verdict.md`: verdict contract + parsing rules; review prompts include it | A sample reviewer reply parses to `{verdict, blocking, advisory}`; `revise` + 0 blocking → approve | AU-1 |
| **AU-3** | `scripts/qa-twice.sh` | Runs given BE classes / FE files twice; exit 0 on pass or flaky-only, 1 on any double fail; prints a per-test status table; no secrets or prod calls | — |
| **AU-4** | Ledger + tracking: ledger file format, `session.txt` update after each stage, TASKS.md section, summary appended to the plan | After a stage, `session.txt` shows the stage in `doing`/`done`; final summary table has rows per run + total | AU-1 |
| **AU-5** | Docs: list `/autopilot` in CLAUDE.md user-invoked skills, AGENTS.md pointer that Gate 2 = Landing the Plane | Both files mention the skill; nothing else changes | AU-1 |
| **QA-1** | Dry run on a real small task: code-health finding **#6** (`CommentPanel` `useQuery`) | Run reaches Gate 2 with a ledger + summary; at least one review verdict parsed; owner approves or rejects the push | AU-1..AU-4 |

Order: AU-1 and AU-3 can run in parallel → AU-2, AU-4 → AU-5 → QA-1.

## Risks

| Risk | Mitigation |
|------|------------|
| Reviewer forgets the verdict block | The parser treats a missing block as `revise` with a note and re-asks once. Missing it twice → `blocked` |
| Cost overrun on a long run | The ledger total is shown at each gate. The run stops at the round caps |
| `mvn test` ×2 is slow | Only affected test classes run twice. The full suite runs once |
| Orchestrator context grows over many stages | Workers return only a summary + verdict. The plan file and ledger are re-read after each stage (Stale Context Check) |
