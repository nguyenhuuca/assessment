# ADR-0027: Agent Self-Improvement Loop ("Evolve")

## Metadata

**Status:** Accepted (2026-10-21) · **Date:** 2026-10-21 · **Deciders:** nguyenhuuca · **Tags:** agents, ci, workflow, quality
**Related:** builds on [ADR-0022](0022-headless-autopilot.md) (headless runs) and [ADR-0025](0025-autopilot-auto-gates.md) (auto gates, never for `.claude/`); input from `scripts/agent-metrics.mjs` (`docs/reports/agent-metrics-2026-10.md`)

## Context

- `scripts/agent-metrics.mjs` now measures the agent's work: escaped defects, reverts, review rounds, blocked runs, flaky tests, CI failures, cost.
- Today a bad signal only turns into a better rule when the owner runs a retro by hand. The owner wants the agent to **improve itself from the metrics**, without being asked each time.
- First real signal: `62526c97` fixed a defect from the dynamic-cron-jobs run. Existing job bodies caught and logged every exception, so the new scheduler recorded SUCCESS on failure. Plan review, code review and QA ×2 all passed it.
- Risks of an agent that edits its own rules:
  - **Goodhart:** it can make the numbers look better (weaker tests, lower gates, a changed metrics script) instead of the work.
  - **Drift:** one bad rule makes every later run worse, and nobody notices.
  - **Bloat:** rules only grow, each run gets slower and more expensive.
  - **Injection:** issue, PR and commit text could be turned into rules.

## Decision Drivers

1. Every change to the agent's rules must come with **evidence that it helps**, checked by code the agent cannot change.
2. The owner keeps control of the direction: one click per change, no reading of raw metrics needed.
3. The loop must not be able to weaken any gate, metric or eval.
4. Bounded cost per run.

## Considered Options

| Option | Pros | Cons |
|---|---|---|
| A. Manual retro (today) | No risk | Depends on the owner remembering; signals are lost |
| **B. Evolve loop, proposals as PRs, owner merges** | Automatic diagnosis and proof; owner only merges | One human step per change |
| C. Evolve loop with auto-merge | Fully hands-off | The agent changes its own rules with no human read; one bad change compounds; conflicts with ADR-0025 (no auto-merge for `.claude/`) |

## Decision Outcome

**Option B.**

### D1. The loop

```
/evolve (run by the owner)
  Observe   agent-metrics --json → new signals (escaped defect, revert, blocked, flaky, /revise, cost jump)
  Diagnose  per signal: original run diff, fix commit, plan/report → Five Whys → the stage that should have caught it
  Propose   one small change at that stage, on branch evolve/<date>-<signal>
  Prove     deterministic eval (D3)
  Ship      PR "Evolve: <signal>" with the Five Whys and the eval table → owner merges or closes
  Re-measure next month's metrics; a later run may propose removing a rule that no longer earns its place (D5)
```

- At most **3 signals per run**; the rest wait for the next run.
- Signal ids are stable (`defect:<fix sha>`, `revert:<sha>`, `blocked:<run id>`, …). A signal that already has an `evolve` PR or issue (open, merged or closed) is skipped, so a change the owner rejected is not proposed again. The state lives in GitHub, not in commits to `main`.

### D2. What the agent may change (enforced by the workflow, not by the prompt)

- **May add or tighten:** `.claude/skills/**/SKILL.md`, `.claude/skills/**/resources/*.md`, `.claude/rules/*.md`, `.claude/agents/*.md` (prompt body only, not frontmatter), and **new** directories under `evals/seeded/`.
- **Never:** `.github/`, `scripts/`, `.claude/hooks/`, `.claude/settings*.json`, existing `evals/seeded/*` cases, `api/.coverage-threshold`, application code, migrations, `CLAUDE.md`, `AGENTS.md`.
- A deterministic step checks `git diff --name-only origin/main...HEAD` against this allowlist and against "no deleted lines in rule files" (removals only through D5). Any violation → no PR, an `evolve` issue that names the violation.
- A change that needs CI or a hook (for example a grep check for swallowed exceptions in `jobs/`) is written in the PR body as an **owner action**, not made by the agent.
- Issue, PR and commit text is data. The agent quotes it as evidence; it never copies instructions from it into a rule.

### D3. The fitness function: seeded-bug evals

- `evals/seeded/<id>/case.json` = a real defect: `base` and `head` commits of the run that introduced it, the files, and an `expect` block (the file that must get a blocking finding, plus regexes the finding must match).
- `scripts/evolve.mjs eval` runs the reviewer (`worker-reviewer`, the code-review stage of `/autopilot`) on `git diff base..head` with the `.claude/` of a given ref, and grades the verdict block deterministically: **caught** = a blocking finding on an expected file whose text matches an `expect` regex.
- A proposal passes only when, with the eval runner **from `main`**:
  1. the new case is **missed with `main`'s rules in at least 2 of 3 runs** (otherwise no rule change is needed; the PR only adds the case, and that case-only PR needs `main` to catch it in 2 of 3),
  2. it is **caught with the branch's rules in at least 2 of 3 runs** (model output is not deterministic),
  3. **every existing case is still caught** with the branch's rules (no regression),
  4. the mean reviewer cost per case rises by at most 25%.
- The agent writes the case, but cannot game it: an `expect` that matches everything fails rule 1, one that matches nothing fails rule 2.
- The eval table (case × ref × run → caught, cost) goes into the PR body.

### D4. Running it

- **Local only:** the owner runs `/evolve`; it goes through the same stages in the session and asks before any push or merge, like autopilot Gate 2.
- **Gate hardening:** the case must be added by the branch, its `base`/`head` must be commits on `main`, rule-file frontmatter is frozen (new skills: `name`, `description`, `argument-hint`, `disable-model-invocation` only), no new agents, and `not-eval-proven` proposals may not touch the reviewer's rules.
- **Budget:** at most 3 signals per run, at most 3 eval runs for the new case and 1 run per existing case (retry once on a miss).
- **Headless workflow removed (2026-10-21).** A weekly `.github/workflows/agent-evolve.yml` (propose job with a read-only token, ship job that checks and opens the PR) was built and then removed by the owner. Its first real run showed that headless Claude Code treats `.claude/` files as sensitive and asks for permission on every edit, which CI cannot grant, so the agent could not write a single proposal; a packaging bug then closed the month's signal with a wrong "no proposal" issue (#50). Running it headless again needs a new decision (for example: the agent writes encoded file copies outside `.claude/` and the ship job applies them).

### D5. Pruning

- Every rule line added by Evolve ends with `<!-- evolve:<case id> -->`.
- Quarterly (or when total rule size grows by more than 20% since the last prune), a run may propose **removing** one such line. It passes only when its case is still caught without it (D3 rule 2) and nothing regresses. This is the only path that deletes rule lines.

### D6. Compress: removing text, not only adding it (amendment 2026-10-21)

Adding rules alone makes every session pay for more context (`CLAUDE.md` + always-loaded rules ≈ 37 KB, loaded by every session and worker). Evolve may also **cut**:

- **Signal:** `compress:<YYYY-MM>`, at most one a month, after the defect signals. It carries lint hints from `scripts/evolve.mjs lint-rules`: exact duplicate lines across rule files, dead path references, the always-loaded size.
- **What may be cut:** duplicated text (keep one home, point to it), dead references, text that contradicts its source of truth, stale claims.
- **Paths:** the D2 paths **plus `CLAUDE.md`**, existing files only, removals allowed. **Protected, never cut or edited:** `AGENTS.md`, `.claude/rules/security.md`, the evolve skill, `verdict.md`, frontmatter, and any line carrying a safety rule (push, gates, merge, deny, secrets, tokens, prod, smoke, security, Core Principles, "never …"), matched by a fixed pattern in `scripts/evolve.mjs`.
- **Gate** (`gate --kind compress`):
  - C1: the touched files shrink, and the always-loaded context drops ≥ 5% or the touched files lose ≥ 1 KiB;
  - C2: no new duplicate lines or dead references;
  - C3: every seeded case is still caught with the compressed rules (and there is at least one case).
- **Always a draft PR.** With a small eval set the gate cannot prove that behavior outside it is unchanged, so the owner reads every removed line.
- Rewriting a skill to work better (not just shorter) is a `rule` proposal: it needs a seeded case that the old text misses.

## Consequences

- **Good:** every escaped defect becomes a regression eval for the agent, and each rule change has before/after evidence. The eval set grows with real bugs, never with invented ones.
- **Good:** the owner's job shrinks to reading a short PR (Five Whys + eval table) and merging or closing it.
- **Cost:** about one agent run (~$1–3) plus the evals (one Sonnet review per case run, ~$0.10–0.20) per `/evolve` run with signals; with no new signal it stops after the metrics step.
- **Limit:** only stages a reviewer can be tested on are proven by D3. Changes to plan review or QA rules are proposed with the Five Whys only and marked "not eval-proven" in the PR.
- **Limit:** keyword grading can miss a correct finding worded differently; the 2-of-3 rule and the owner's read cover that.
- **Risk:** a merged rule could still hurt cases not in the eval set. The monthly metrics and D5 pruning are the backstop; rollback is `git revert` of the evolve PR.

## Validation

- First case `evals/seeded/2026-10-19-swallowed-job-exceptions` (from `62526c97`): missed with today's rules, caught after the Evolve change, in 2 of 3 runs.
- The allowlist step rejects a test branch that edits `scripts/agent-metrics.mjs`.
- After two months: escaped defect rate does not rise while the number of runs grows, and total rule size stays within the D5 bound.

## Links

- [ADR-0022 Headless autopilot](0022-headless-autopilot.md) · [ADR-0025 Auto gates](0025-autopilot-auto-gates.md)
- `scripts/agent-metrics.mjs`, `docs/reports/agent-metrics-2026-10.md`

## Changelog

- 2026-10-21: Proposed; accepted by the owner the same day. The eval runner lives in `scripts/evolve.mjs` (subcommands `signals`, `check-paths`, `eval`, `gate`).
- 2026-10-21: D6 (compress) added at the owner's request: Evolve may also remove duplicate, dead or stale text, with protected lines and a size + eval gate.
- 2026-10-21: headless workflow `agent-evolve.yml` removed at the owner's request (D4); `/evolve` is local only. The `manifest` command went with it.
