# Plan: Agent self-improvement loop ("Evolve")

- **ADR:** [ADR-0027](../adr/0027-agent-self-improvement-loop.md) (Accepted 2026-10-21)
- **Status:** Done (EV-5 headless workflow removed 2026-10-21, see ADR-0027 D4)

## Design (facts)

- Reviewer under test: `.claude/agents/worker-reviewer.md` (Sonnet), run with `claude -p --agent worker-reviewer --output-format json` (CLI 2.1.304 has `--agent`, `--max-budget-usd`, `--output-format`). Verdict format: `.claude/skills/autopilot/resources/verdict.md`.
- Rules of a ref are applied by checking out the case `head` in a temporary git worktree, then replacing `.claude/`, `CLAUDE.md`, `AGENTS.md` with the ref's copy (`git checkout <ref> -- …`).
- First case: dynamic-cron-jobs BE commit `985ba5b5` (base `985ba5b5^`); the defect was fixed by `62526c97` (job bodies in `AppScheduler.java` swallowed exceptions, so `DynamicJobScheduler` recorded SUCCESS).
- Headless agent cannot push: checkout with `persist-credentials: false`; the workflow pushes only verified `evolve/*` branches with the job token. Eval and path checks run from a copy of `scripts/evolve.mjs` taken from `main` before the agent step.

## Tasks

| ID | Task | Acceptance criteria |
|----|------|---------------------|
| EV-1 | `agent-metrics.mjs --json`: signals with stable ids (`defect:<sha>`, `revert:<sha>`, `blocked:<run>`, `flaky:<run>`, `review-skipped:<run>`, `revise:pr-<N>`) incl. the run's base/head commits | node tests; real run lists `defect:62526c97…` |
| EV-2 | `scripts/evolve.mjs`: `signals` (minus ones with an `evolve` PR/issue marker, top 3), `check-paths` (ADR D2 allowlist + no deleted lines in rule files), `eval` (one case × ref × runs, deterministic grading), `gate` (ADR D3 rules 1–4) | node tests for grading, allowlist, gate decisions, signal filter |
| EV-3 | First case `evals/seeded/2026-10-19-swallowed-job-exceptions/case.json` + `evals/README.md` | `eval` with `main` rules: missed in ≥2 of 3 |
| EV-4 | `/evolve` skill (`.claude/skills/evolve/SKILL.md`): local + headless, manifest `evolve-out/manifest.json`, PR body format, `<!-- evolve:<case> -->` markers, prune rule; first evolution = the rule that catches EV-3 | `gate` passes for EV-3 (missed on main, caught ≥2/3 on the branch, no regressions) |
| EV-5 | ~~`.github/workflows/agent-evolve.yml`~~ (removed 2026-10-21): weekly cron + dispatch; signals → agent → check-paths → gate → push + PR (label `evolve`) or issue | `actionlint`-clean YAML; dry run via `workflow_dispatch` after push (owner) |
