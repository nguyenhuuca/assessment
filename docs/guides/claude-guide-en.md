# Claude Code — User Guide

> This guide explains how to use **Claude Code** with the **Funny Movies** project. After cloning the repo, read this to get the most out of the AI assistant.

---

## Table of Contents

1. [Introduction](#introduction)
2. [Levels of Using Claude](#levels-of-using-claude)
3. [Pre-configured Setup](#pre-configured-setup)
4. [Workflow Skills — 17 Commands](#workflow-skills-17-commands)
5. [Library Skills — Loaded Automatically](#library-skills-loaded-automatically)
6. [Swarm — Multi-Agent Parallel Execution](#swarm-multi-agent-parallel-execution)
7. [Autopilot — Run One Task End to End](#autopilot-run-one-task-end-to-end)
8. [Quality Metrics & Evolve — the Agent Improves Itself](#quality-metrics-evolve-the-agent-improves-itself)
9. [Session File (session.txt)](#session-file-sessiontxt)
10. [Hooks & Permissions](#hooks-permissions)
11. [Real-World Workflows](#real-world-workflows)
12. [Available Templates](#available-templates)
13. [Tips & Tricks](#tips-tricks)

---

## Introduction

This project uses the **[Claude Agentic Framework](https://github.com/dralgorhythm/claude-agentic-framework) v5**, adapted for Funny Movies (see [Claude Workspace](../claude/index.md)):

- ✅ **17 workflow skills** you invoke as `/name` (scope, spec, architect, builder, swarm, autopilot, evolve…)
- ✅ **10 library skills** Claude loads on its own when a task matches (testing, ADRs, threat modeling…)
- ✅ **5 worker agents** for swarms, each with a pinned model, `maxTurns` and tool allowlist
- ✅ **Rules** loaded in every session: core directives, code quality, security, tech strategy, debugging protocol
- ✅ **4 safety hooks** plus permission deny rules for secrets
- ✅ **Templates** for PRD, ADR, spec, plan, and more

Two conventions specific to this repo:

1. **Planning docs live in `docs/`**: `docs/adr/`, `docs/prd/`, `docs/specs/`, `docs/sequence/` (Mermaid sequence diagrams, required for every feature with a PRD), `docs/plans/`, with status in `docs/tracking.md`.
2. **Work is committed on `main`, and Claude always asks before every `git push`**, because CI deploys from `main`.

---

## Levels of Using Claude

This project uses Claude Code at 6 levels. The higher the level, the more Claude does on its own and the less you touch. Rules, hooks and quality gates from the lower levels still apply at every level above.

| Level | How | You take part | Real example in this repo |
|-------|-----|---------------|---------------------------|
| 1 | **Ask & explore:** ask about the code; Claude reads and greps before answering, no code changes | Every question | "How do I manage cron jobs dynamically?" → 3 levels, Level 2 recommended (DB + TaskScheduler + admin UI) |
| 2 | **Direct work with guardrails:** CLAUDE.md + `.claude/rules/` always loaded, library skills auto-load, hooks catch secrets, `session.txt`, ask before every push | Each step, approve the push | Cron editor modal + tooltip (`df8bdba1`, `9b1d5419`): done directly, not through autopilot |
| 3 | **Workflow skills + swarm:** `/scope` → `/architect` → `/spec` → `/swarm-plan` → `/swarm-execute` → `/swarm-review` → `/land-the-plane`; worker agents run in parallel, model pinned per agent | Approve each artifact (PRD, ADR, Spec, Plan) | "Premium user" feature: autopilot refused because it needs a PRD → go to `/scope` |
| 4 | **Local `/autopilot`:** plan ⇄ review → Gate 1 → implement ⇄ review → QA ×2 → commit → Gate 2 → CI + smoke; at most 3 rounds per loop | 3 times: Start, Gate 1, Gate 2 | Dynamic cron jobs (ADR-0023): BE + FE, CI green, smoke 22/22, $3.71 |
| 5 | **Headless on GitHub** ([ADR-0022](../adr/0022-headless-autopilot.md)): label `autopilot` → draft PR → `/approve` → build → you merge | Add the label, `/approve`, merge | Issue #34 (back button color) → PR #35 |
| 6 | **Auto gates** ([ADR-0025](../adr/0025-autopilot-auto-gates.md)): add `autopilot:auto` or `autopilot:auto-merge`; the workflow approves and merges after its own checks | Only add the label | Issue #36 → PR #37 merged and deployed in ~5 minutes, build job $0.39 |

**Which level to pick:**
- Not sure what to do yet, or you only need to understand the code → **level 1**.
- A small change you want to watch step by step → **level 2**.
- A medium or large feature (needs a PRD, several days) → **level 3**.
- A small feature, fix or refactor with clear acceptance criteria → **level 4**, or **level 5** without a terminal.
- Small, low-risk work that touches no migration or agent config → **level 6**.

---

## Pre-configured Setup

### Folder Structure

```
.claude/
├── skills/
│   ├── <name>/SKILL.md          # 17 workflow skills: invoke as /name
│   ├── architecture/            # designing-systems, writing-adrs
│   ├── core-engineering/        # testing, dependency-upgrade, review-steering
│   ├── design/                  # presentation
│   ├── operations/              # postmortem, swarm-coordination
│   ├── product/                 # planning-artifacts (PR-FAQ, PRD, roadmap)
│   └── security/                # threat-modeling
├── agents/                      # 5 worker agents for swarms
├── rules/                       # Loaded in every session
├── hooks/                       # 4 safety hooks (bash)
├── templates/                   # Framework templates
├── statusline-command.sh        # Status line: model | ctx | cost | rate | current task
└── settings.json                # Permission allow/deny lists + hook registration

session.txt                      # The one task in flight (committed; see Session File)
evals/seeded/                    # Seeded-bug evals for the reviewer (Evolve, ADR-0027)
scripts/agent-metrics.mjs        # Agent quality metrics; scripts/evolve.mjs = Evolve signals + eval gate
AGENTS.md                        # "Landing the Plane": how a work session finishes
templates/
├── artifacts/                   # PRD, ADR, spec, plan, design spec, postmortem…
└── claude_mechanisms/           # Agent, command, skill, rule templates
```

> **There is no `.claude/commands/` any more.** The old commands became skills; the slash names are unchanged.

---

## Workflow Skills — 17 Commands

Most workflow skills run only when you type `/name`. The exceptions are the 5 skills `/autopilot` chains (`swarm-plan`, `swarm-review`, `swarm-execute`, `qa-engineer`, `land-the-plane`): they have `disable-model-invocation: false`, so Claude may also invoke them when a request matches. See [Autopilot](#autopilot-run-one-task-end-to-end).

| Command | Role | Use when | Output |
|---------|------|----------|--------|
| `/scope <idea>` | PRD Generator | An idea is still vague: asks clarifying questions, then writes the PRD | `docs/prd/PRD-{slug}.md` |
| `/spec <PRD> [ADR]` | Spec Generator | Turns an approved PRD (+ ADR when there is one) into a behavior contract, plus the Mermaid sequence diagrams | `docs/specs/spec-{slug}.md` + `docs/sequence/sequence-{slug}.md` |
| `/architect <topic>` | Principal Architect | System design; records decisions as ADRs | `docs/adr/NNNN-{slug}.md` |
| `/builder <task>` | Builder | Implementation, debugging, refactoring with tests | Code + tests |
| `/qa-engineer <component>` | QA Engineer | Test strategy, E2E, accessibility gate | Test plan / tests |
| `/security-auditor <scope>` | Security Auditor | OWASP/STRIDE audit with data-flow tracing | Audit report |
| `/ui-ux-designer <flow>` | UI/UX Designer | Interface design, WCAG checks via DevTools | Design spec |
| `/code-check <scope>` | Codebase Auditor | SOLID, DRY, consistency and scope-adherence audit | Quality report |
| `/swarm-plan <feature>` | Planning Orchestrator | Breaks a large feature into 200–400 LOC tasks | `docs/plans/plan-{slug}.md` |
| `/swarm-execute <plan>` | Execution Orchestrator | Runs a plan with parallel workers | Committed code |
| `/swarm-review <scope>` | Adversarial Reviewer | Multi-perspective review: security, performance, scope… | Review report |
| `/swarm-research <topic>` | Research Orchestrator | Deep investigation, technology comparison | Research report |
| `/land-the-plane` | Finish-Line Protocol | Gates → commit → (ask) push → check CI | Pushed commit |
| `/tailor` | Configuration Tailor | Detects the stack and proposes config changes (proposal only) | `scratchpad/tailor-proposal.md` |
| `/autopilot <request>` | Autopilot | Run one small feature, fix or refactor end to end: plan → review → you approve → code → review → QA ×2 → you approve the push | Deployed code + `docs/reports/autopilot-*.md` |
| `/evolve [--since <date>]` | Self-Improver | Turn agent-metrics signals into proven rule changes (local, or weekly in CI) | `evolve/*` branch, seeded case in `evals/seeded/` |
| `/task-resume [instruction]` | Task Resumer | Continues the unfinished task in `session.txt` after re-checking the repo | Work continued from `next` |

Examples:

```bash
/scope add a trending videos section to the homepage
/spec docs/prd/PRD-bookmark-feature.md docs/adr/0012-bookmark-feature-design.md
/swarm-plan docs/specs/spec-video-recommendation.md
/swarm-execute docs/plans/plan-watch-history-v2.md
/swarm-review commit 0bf86a71
/presentation docs/adr/0021-watch-history.md
```

> `/presentation` is a library skill but can still be invoked as `/presentation <path>`. It renders one PRD/Spec/ADR/Plan into a single `.html` deck that opens directly in a browser.

---

## Library Skills — Loaded Automatically

Claude reads every skill's name and description at startup and pulls in a skill's body when the task matches its description. There is no keyword list or registry file.

| Skill | Group | Loaded when |
|-------|-------|-------------|
| **testing** | core-engineering | Writing tests, fixing a bug (regression test), raising coverage |
| **dependency-upgrade** | core-engineering | Upgrading a library/action: verified pins, staged rollout |
| **review-steering** | core-engineering | Creating/updating `REVIEW.md` to steer code review |
| **designing-systems** | architecture | Designing a system, comparing architectures |
| **writing-adrs** | architecture | Recording a technical decision |
| **threat-modeling** | security | A feature with security implications (STRIDE, prompt injection…) |
| **planning-artifacts** | product | Writing a PR-FAQ, PRD or roadmap |
| **postmortem** | operations | Writing an incident postmortem |
| **swarm-coordination** | operations | Coordinating agents, handoffs, token budgets |
| **presentation** | design | Turning a document into an HTML deck |

**Why only 24 skills (down from about 65)?** Every skill's name and description is loaded into context in every session. Too many skills exceed the budget, descriptions get truncated and Claude picks the wrong skill. Skills that only restated what the model already knows (java, python, terraform, deploy-*…) were removed; use Context7 for version-specific details.

Disciplines that used to live in skills are now **rules**, loaded in every session:

| Rule | Covers |
|------|--------|
| `core-directives.md` | Planning flow, where docs go, commit/push conventions |
| `debugging-protocol.md` | At least three hypotheses before choosing one; root cause required; re-read files before editing |
| `code-quality.md` | SOLID, DRY, Two Hats, quality gates |
| `security.md` | Checklist (tagged with how each line is really enforced), prompt injection, OWASP |
| `tech-strategy.md` | This project's golden paths (Java 24, Spring Boot 4, React 19…) |
| `agent-constraints.md` | Rules for swarm workers |

---

## Swarm — Multi-Agent Parallel Execution

### 5 Worker Agents

| Worker | Model | Tools | maxTurns | Use case |
|--------|-------|-------|----------|----------|
| **worker-explorer** | Haiku | Read, Grep, Glob, WebFetch, WebSearch | 30 | Fast code search, quick API/doc lookups |
| **worker-builder** | Sonnet | Read, Write, Edit, Bash, Glob, Grep | 90 | Implementation, tests, refactoring (runs in its own git worktree) |
| **worker-reviewer** | Sonnet | Read, Grep, Glob, Bash | 60 | Code, security and performance review |
| **worker-research** | Sonnet | Read, Grep, Glob, WebFetch, WebSearch, Write | 80 | Deep multi-source research |
| **worker-architect** | Opus | Read, Grep, Glob, Write | 40 | Complex design decisions |

> The old `worker-researcher` was split into `worker-explorer` (quick lookups) and `worker-research` (deep research).

### Swarm Workflow

```
1. /swarm-plan "implement user authentication"
   → Workers explore in parallel → plan in docs/plans/
   → Tasks of 200–400 LOC with acceptance criteria and dependencies

2. /swarm-execute docs/plans/plan-<feature>.md
   → Orchestrator builds a task list, runs up to 8 workers
   → worker-builder commits in its own worktree; the orchestrator merges into main
   → Quality gates re-run after the merge; push only after you approve

3. /swarm-review
   → Perspectives: security, performance, architecture, tests, scope adherence
   → Small, low-risk diffs get a lite (single-perspective) review
```

Task tracking uses **Claude Code's native task list** (in-session) plus `TASKS.md` / `docs/tracking.md` / GitHub Issues (durable). Beads (`bd`) is no longer used.

### How the Agents Work Together

- **The orchestrator is the main session (Opus).** It is the only agent that starts workers (workers have no `Agent` tool), owns the task list, merges their work, runs the gates and asks you at the gates.
- **Each worker gets one focused prompt and returns a summary** (files changed, tests added, verdict block), not file dumps. Workers never share state with each other.
- **`worker-builder` works in its own git worktree.** The orchestrator merges or cherry-picks its commits into `main`, then re-runs the gates itself.
- **Reviewers are separate agents from builders.** Each ends with a verdict block (`approve` / `revise`). Only blocking findings send work back, at most 3 rounds.

```
                    You  (Start · Gate 1 · Gate 2)
                              │
               Orchestrator  (main session, Opus)
     ┌──────────────┬─────────┴──────────┬──────────────────┐
worker-explorer ×3  worker-reviewer     worker-builder ×2    worker-reviewer ×2
(Haiku: find        (plan review)       (BE + FE, each in    (quality + security)
 existing patterns)                      its own worktree)
```

**Example: the dynamic cron jobs run** ([report](../reports/autopilot-2026-10-19-dynamic-cron-jobs.md)):

| Step | Agent(s) | Model | Result | Cost |
|------|----------|-------|--------|------|
| Explore | 3 × `worker-explorer`: Liquibase + entity patterns, backend admin patterns, webapp admin UI patterns | Haiku | Patterns for the plan; the orchestrator writes the plan + ADR-0023 | $0.03 |
| Plan review | `worker-reviewer` | Sonnet | `approve`, 0 blocking, 7 advisory | $0.16 |
| Gate 1 | you | — | approved | — |
| Implement | 2 × `worker-builder` in parallel: BE (DJ-1..3), FE (DJ-4..5) | Sonnet | Committed in worktrees, cherry-picked onto `main` | $0.42 + $0.25 |
| Code review | 2 × `worker-reviewer`: quality, security | Sonnet | `approve`, 0 blocking, 6 advisory | $0.17 + $0.15 |
| QA ×2, gates, commit, CI + smoke | orchestrator | Opus | 84 tests pass twice, CI green, smoke 22/22 | $2.53 (whole run) |

Total **$3.71**. The orchestrator is 68% of it; the 8 workers together cost $1.18.

### Swarm Patterns

#### Pattern 1: Parallel Implementation
```
/swarm-plan docs/specs/spec-<feature>.md
→ Independent BE, FE and test tasks
/swarm-execute docs/plans/plan-<feature>.md
→ BE and FE workers run in parallel; the orchestrator merges and runs gates
```

#### Pattern 2: Security Sweep
```
/swarm-review security audit api/
→ worker-reviewer (focus: security) scans each module in parallel
→ Findings ranked Critical/High/Medium/Low with CWE IDs
/builder fix the Critical/High findings
```

#### Pattern 3: Parallel Exploration
```
/swarm-research compare HLS vs chunked MP4 streaming for 1 vCPU
→ worker-explorer / worker-research investigate each angle
→ Synthesized into a cited report
```

---

## Autopilot — Run One Task End to End

`/autopilot <request>` chains the workflow skills into one automatic run, from the request to a deployed commit. You are needed at **3 points only**: when you type the command (Start), when the plan is ready (**Gate 1**) and before the push (**Gate 2**). Every loop is capped at 3 rounds, so a run never loops forever.

```
Plan ⇄ Plan review → [GATE 1: you approve the plan] → Implement ⇄ Code review
     → Local QA (tests ×2) ⇄ Fix → Commit → [GATE 2: you approve the push] → CI + smoke
```

| Stage | Skill called | Fallback (when a skill is set back to `disable-model-invocation: true`) |
|-------|--------------|------------------------------------------------------------------------|
| Plan | `swarm-plan` | `worker-architect` / `worker-builder` reads the `SKILL.md` and follows it |
| Plan review, Code review | `swarm-review` | `worker-reviewer` |
| Implement, Fix | `swarm-execute` | `worker-builder` (in its own worktree) |
| Local QA | `qa-engineer` + `scripts/qa-twice.sh` | `worker-builder` (testing focus) |
| Land | `land-the-plane` | Orchestrator follows AGENTS.md "Landing the Plane" |

- **Verdict block.** Every reviewer ends with one (`approve` / `revise` + blocking / advisory counts), as defined in `.claude/skills/autopilot/resources/verdict.md`. Only **blocking** findings trigger another round; advisories go into the report.
- **Tests twice.** `scripts/qa-twice.sh` runs the affected tests **twice**:
  - pass both runs → `pass`;
  - fail once → `flaky` (logged, does not block);
  - fail both runs → `fail` (goes to Fix).

### ✅ When It Fits

| Kind of work | Real example in this repo |
|--------------|---------------------------|
| **Small feature** (about ≤ 1–3 days, `swarm-plan`'s "Small Feature"), clear enough to write acceptance criteria without asking back | Suggestion re-review: admin Reopen + user re-review request ([report](../reports/autopilot-2026-10-19-suggestion-rereview.md)) |
| **Small refactor with no behavior change** (Two Hats): existing tests must pass unedited | One `SuggestionTransition` table for status rules ([report](../reports/autopilot-2026-10-19-suggestion-transitions.md)) |
| **Bug fix with a known cause**: you know what to change and want a regression test | — |
| Changes to backend and/or webapp that **automated tests can verify** (JUnit, Vitest) | both runs above |
| A small technical decision that needs an ADR | Still fine: the Plan stage writes the ADR too |
| **A plan already exists** (from `/swarm-plan` or written by hand) with **≤ 8 open tasks** | `/autopilot docs/plans/plan-x.md` skips the Plan stage but still runs Plan review and Gate 1. It works only on the tasks still `[ ]` in `TASKS.md` |

### ⚠️ When Not to Use It (use another flow)

| Situation | Why | Use instead |
|-----------|-----|-------------|
| The idea is still vague | Autopilot does not ask back to clarify the request | `/scope` |
| Medium or large feature (≥ 1 week, needs a PRD or PR-FAQ) | Bigger than one small plan; **rejected** by the scope guard | `/scope` → `/spec` → `/swarm-plan` → `/swarm-execute` |
| Big architecture decision, one-way door | Needs options compared and a careful human review | `/architect` |
| Production incident with an unknown root cause | Autopilot needs to know what to fix; debugging needs ≥ 3 hypotheses and log reading | Workflow 2 (Debug Production Issue) |
| A question, an explanation, a typo or a 1–2 line change | Each run costs a plan + 2 reviews + QA ×2; doing it directly is far cheaper | Ask Claude directly |
| Writing prod data, infra work (VM, secrets, manual SQL) | Autopilot **never** writes to prod; the smoke test is read-only | Do it by hand, or have Claude draft the commands for you |
| New UI that needs design or visual approval | No automated Lighthouse/visual gate (only listed as a manual check) | `/ui-ux-designer` first, then autopilot |
| Performance optimization | No before/after benchmark stage | Workflow 4 (Performance) |
| Several unrelated changes in one request | One run = one plan = one goal; mixing them dilutes the reviews | Split into several runs |

### 🛑 When Autopilot Refuses or Stops

| Situation | What autopilot does | What you do |
|-----------|---------------------|-------------|
| **Scope guard**: the request needs a PRD/PR-FAQ | **Refuses** at once, before planning, with one line pointing to `/scope` | Run `/scope`, or shrink the request and run again |
| **Plan mode**: the plan has > 8 open tasks, a task is `[~]`, or the plan is Done | **Refuses** and points to `/swarm-execute` | Use `/swarm-execute`, or split the plan |
| **Plan mode**: the plan has no open tasks left | Says so and stops | — |
| Plan review still `revise` after 3 rounds | Stops, `session.txt` gets `status: blocked`, the open blocking findings are listed | Clarify the request or decide the stuck points yourself |
| Code review still `revise` after 3 rounds | Stops `blocked`; the code stays on the worktree branch, not on `main` | Read the findings, fix by hand or drop |
| QA fails both runs and still fails after 3 Fix rounds | Stops `blocked` with the `FAIL` list and log paths | Debug with Workflow 2 |
| `qa-twice.sh` exits 2 (compile error, no report) | Treated as a fail; the log path goes to Fix | — |
| A **flaky** test (fails 1 of 2 runs) | **Does not block**; recorded in the report | Fix the flaky test in a later task (`qa-engineer`: no flaky tests) |
| A reviewer returns no verdict block, even after one re-ask | Stops `blocked` ("review returned no verdict") | Run again |
| A full gate is red (`mvn verify`, coverage < `api/.coverage-threshold`, vitest, lint) | Does not commit | Same as a QA fail |
| You pick **Revise with notes** at Gate 1 | Replans with your notes (new cycle, round counter reset) | — |
| You pick **Stop** at Gate 1 | Stops `blocked` | — |
| You pick **Don't push yet** at Gate 2 | Commits stay local, no push | Push later with `/land-the-plane` or ask Claude |
| Rebase conflict on push | Stops and reports; never force-pushes | Resolve the conflict |

A stopped run can be continued with `/task-resume`, which reads `session.txt` and the ledger in `scratchpad/autopilot/`.

### 🔒 What Autopilot Never Does

- **Skip Gate 1 or Gate 2 on its own.** There is no `--yes`, and approving one gate never covers the other or a later run. Only a flag you type in the command skips a gate (`--auto`, `--auto-push`, see How to Call It).
- **Push early.** It never pushes before Gate 2, never force-pushes and never opens a PR mid-run.
- **Write prod data.** `api/Mock-Test.http` runs only GET and login.
- **Edit existing tests to make a refactor pass.**

### 📄 What a Run Produces

| File | Content |
|------|---------|
| `docs/plans/plan-<slug>.md` | The plan approved at Gate 1. The report is **not** written into the plan |
| `TASKS.md` | One section per feature; tasks flipped to `[x]` with the commit hash |
| `docs/reports/autopilot-<YYYY-MM-DD>-<slug>.md` | The report, same sections local or headless (`.claude/skills/autopilot/resources/report.template.md`):<br>• result per stage<br>• ledger table<br>• cost per agent (local) or per job (headless, added by the workflow)<br>• CI + smoke result (headless: after merge)<br>• manual checks and open advisories |
| `docs/metrics/agent-runs/<run id>.json` | Run metrics for `agent-metrics.mjs` (rounds, verdicts, LOC, QA counts, cost); every run commit also carries `Autopilot-Run: <run id>` |
| `scratchpad/autopilot/<timestamp>-<slug>.md` | The live ledger (gitignored) |
| `session.txt` | Current stage, progress, and a `blocker` when stopped |

**Lite mode** (under 100 changed lines not counting tests, and no ADR): **no** `docs/plans/plan-*.md` and **no** `docs/reports/autopilot-*.md`. The plan and the report go to the console (local) or the PR body (headless). `TASKS.md` and `session.txt` are still updated. If the diff grows past ~100 lines or an ADR turns up, the run switches to full mode and writes the plan file.

### 💰 Real Cost So Far

| Run | Wall time | Orchestrator (Opus) | Workers (Sonnet/Haiku) | Total |
|-----|-----------|---------------------|------------------------|-------|
| Suggestion re-review (BE + FE, 1 replan) | ~20 min | $2.51 | $1.25 | **$3.76** |
| Transitions refactor (BE only) | ~11 min | $1.78 | $0.47 | **$2.24** |

These are exact figures from `node scripts/claude-cost.mjs --from <start> --to <end>`, which reads the session transcripts and prices them at API list price. On a subscription they are the equivalent cost, not a bill. Each run report has the per-agent breakdown.

- **Where the money goes:** the orchestrator (Opus) is 67–79% of each run, mostly cache reads of the growing session context.
- **To spend less:**
  - write a clear request, so no replan is needed;
  - keep one goal per run;
  - run FE and BE in parallel only when both change.

### ☁️ Running Headless on GitHub (no terminal)

Besides local runs, autopilot can run on GitHub Actions ([ADR-0022](../adr/0022-headless-autopilot.md), workflow `.github/workflows/autopilot.yml`). Both gates move to GitHub:

| Step | You do | Autopilot does |
|------|--------|----------------|
| Start | Add the label **`autopilot`** to an issue you opened | Plans and reviews on branch `autopilot/issue-<N>`, opens a **draft PR**, comments the PR link on the issue |
| Gate 1 | Comment **`/approve`** on the PR, or **`/revise <notes>`** to replan | Implement ⇄ review → QA ×2 → full gates → pushes to the PR branch, adds the report to the PR, marks it ready |
| Gate 2 | Review the diff and **merge** the PR. The merge is the push to `main`, and CI deploys it | Never pushes to `main`, never merges |
| After deploy | Run `./api/mock-test.sh` | — |

**Skipping gates with a label** ([ADR-0025](../adr/0025-autopilot-auto-gates.md)). Add one more label together with `autopilot` (order does not matter):

| Extra label | Gate 1 | Gate 2 |
|-------------|--------|--------|
| (none) | you `/approve` | you merge |
| `autopilot:auto-merge` | you `/approve` | automatic merge |
| `autopilot:auto` | automatic | automatic merge |

- **Auto Gate 1:** the plan job writes `<!-- autopilot:plan-review=approve -->` in the PR body only when plan review returned `approve`. The workflow sees it and dispatches the build job (`workflow_dispatch`). A `/revise` always waits for you.
- **Auto Gate 2:** `scripts/claude-autopilot-merge.sh` (always the copy on `main`) merges only when every check passes:
  - the PR is ready, meaning code review returned `approve`;
  - no changes to `.github/`, `.claude/`, `CLAUDE.md`, `AGENTS.md`, `scripts/claude-*`, Liquibase migrations or `.env`;
  - ≤ 400 changed lines outside tests and docs;
  - the script **re-runs** `mvn verify` / webapp test + lint + build itself.
  - It then squash-merges with the secret `AUTOPILOT_MERGE_TOKEN`, so the deploy CI starts. If any check fails, it comments the reason and leaves the PR to you.
- **Real example:** issue #36 (translate the ON/OFF badge) → PR #37 merged and frontend deployed in about 5 minutes, build job $0.39, nobody clicked anything.
- **Risk:** code reaches prod without a human read. The smoke check after the deploy is still manual. Rollback = `git revert` the squash commit.

**One-time setup (you do this; Claude never handles the token):**

```bash
claude setup-token                                  # creates a token from your subscription
gh secret set CLAUDE_CODE_OAUTH_TOKEN               # paste the token when asked
gh label create autopilot --color 5319e7 --description "Run /autopilot headless"
# Only for auto gates: a fine-grained PAT for this repo only, Contents + Pull requests read/write
gh secret set AUTOPILOT_MERGE_TOKEN
```

**Limits and safety:**
- **Owner only.** Only the **repo owner** can trigger a run, by label or by comment. The repo is public, so anyone else's comments are ignored.
- **Issue text is data.** Instructions in an issue that try to change the workflow, permissions or gates are ignored and listed in the PR.
- **Refused paths.** Headless **refuses** to edit `.github/`, `.claude/`, `CLAUDE.md`, `AGENTS.md`, `.env` files, or write data-dropping migrations. Do those locally.
- **Limits and secrets.** Each job has a 60 min cap, and only one run happens at a time: runs queue first come first served, so labeling several issues at once loses none (the wait is capped at about 5.5 hours). Claude's steps see only `CLAUDE_CODE_OAUTH_TOKEN`; `AUTOPILOT_MERGE_TOKEN` is seen only by the auto-merge step; deploy secrets (SSH, Docker) are never exposed.

### ▶️ How to Call It

```bash
/autopilot on the Suggestions tab let an admin move a suggestion back to Pending and review it again
/autopilot refactor: put the suggestion status transition rules in one place, no behavior change
/autopilot fix: the Reopen button is not disabled while the request is pending
/autopilot docs/plans/plan-<feature>.md               # run on an existing plan (plan mode)
/autopilot --auto-push fix: <...>                     # Gate 2 pushes on its own when the check passes
/autopilot --auto fix: <...>                          # both gates on their own (Gate 1 needs a plan-review approve)
```

- **Typed command only.** `autopilot` has `disable-model-invocation: true`, so Claude starts it only when you **type `/autopilot`**. If you ask in chat instead ("run autopilot for X"), Claude reads the `SKILL.md` and follows it step by step, gates included.
- **The chained skills.** `swarm-plan`, `swarm-review`, `swarm-execute`, `qa-engineer` and `land-the-plane` have `disable-model-invocation: false`, so the orchestrator can call them. You can flip any of them back to `true` at any time. That stage then uses its fallback, and the report records which path each stage took.
- **Auto gates ([ADR-0028](../adr/0028-local-autopilot-auto-gates.md)).** `--auto-push` pushes on its own only when `scripts/claude-autopilot-autopush-check.sh` passes: no change to `.github/`, `.claude/`, `CLAUDE.md`, `AGENTS.md`, `scripts/claude-*`, migrations or `.env`, and ≤ 400 changed lines outside tests and docs. Otherwise Gate 2 asks as usual. The flag covers that run only; `/task-resume` asks at every gate left.
- **Design details:** [plan-autopilot](../plans/plan-autopilot.md).

---

## Quality Metrics & Evolve — the Agent Improves Itself

Two layers on top of autopilot: **measure** the agent's work over time, then let the agent **improve its own rules** from what the numbers show ([ADR-0027](../adr/0027-agent-self-improvement-loop.md)).

### 📊 Measuring: `scripts/agent-metrics.mjs`

```bash
node scripts/agent-metrics.mjs --since 2026-10-01 --until 2026-10-31 --out docs/reports/agent-metrics-2026-10.md
node scripts/agent-metrics.mjs --json            # signals for /evolve
```

| Metric | How it is measured |
|--------|--------------------|
| **Escaped defect rate** (the one that matters most) | A later `fix:` commit, not part of the run, within 7 days, touching a code file the run changed |
| Reverts | `This reverts commit <sha>` pointing at an agent commit |
| Review rounds, blocked runs, flaky tests, skipped reviews, cost | `docs/metrics/agent-runs/<run id>.json`, written by `/autopilot` at the end of every run |
| `/revise` count, PRs closed unmerged, open → merge time | GitHub (headless PRs) |
| CI failure rate (all pushes and agent head commits) | GitHub Actions runs |

- **How runs are found:** every autopilot commit carries the trailer `Autopilot-Run: <YYYY-MM-DD>-<slug>`; older runs are found through their report hashes or the headless PR title.
- **October baseline** ([report](../reports/agent-metrics-2026-10.md)): 16 runs, escaped defect rate **6%** (1 of 16: the dynamic-cron-jobs run, fixed by `62526c97`), 0 reverts, headless PR open → merge median 7 minutes.

### 🧬 Evolving: `/evolve`

```
Observe (signals) → Diagnose (Five Whys → the stage that should have caught it) → Propose (one added rule + a seeded case)
  → Prove (eval gate) → Ship (you choose: merge, PR, keep or drop)
```

- **Signals:** `defect:<sha>`, `bug:#<N>` (an in-app bug report whose fix changed lines an agent wrote, in an autopilot run or a Claude-co-authored session commit, found with `git blame`), `revert:<sha>`, `blocked:<run>`, `flaky:<run>`, `review-skipped:<run>`, `revise:pr-<N>`. A signal that already has an `evolve` PR or issue (even one you closed) or a seeded case is never proposed again. At most 3 per run.
- **Seeded-bug evals** (`evals/seeded/<id>/case.json`): each real escaped defect becomes a regression test **for the agent**: the reviewer is run on the original buggy diff and graded deterministically (a blocking finding on the expected file matching the expected wording).
- **Eval gate** (`scripts/evolve.mjs gate`), all four must hold:
  1. today's rules on `main` **miss** the case (at least 2 of 3 runs);
  2. the new rule **catches** it in at least 2 of 3 runs;
  3. every existing case is still caught;
  4. reviewer cost rises by at most 25%.
- **What the agent may change:** only add lines to `.claude/skills/**`, `.claude/rules/*.md`, the prompt body of `.claude/agents/*.md`, and add a new case. Never `scripts/`, `.github/`, hooks, settings, existing cases, thresholds or app code. Frontmatter is frozen. Checked by code (`check-paths`), not by the prompt.
- **How to run:** type `/evolve`. It stops at Ship and asks you: merge into `main`, push a PR, keep the branch, or drop.
- **Weekly in CI:** `.github/workflows/agent-evolve.yml` runs every Monday 09:00 (or Actions → Agent Evolve → Run workflow). The agent writes staged file copies under `evolve-out/`, never `.claude/`. The ship job builds the `evolve/*` branch with `scripts/evolve.mjs apply`, runs the same gate and opens a PR labelled `evolve`; you merge it ([ADR-0029](../adr/0029-headless-evolve-staging.md)).
- **Pruning:** every Evolve rule ends with `<!-- evolve:<case id> -->`; a later run may propose removing one, only when its case is still caught without it.
- **Compress** (ADR-0027 D6): once a month (`compress:<YYYY-MM>`) Evolve may also **cut** text: duplicates, dead references, text that contradicts its source of truth, stale claims, in `.claude/` and `CLAUDE.md`.
  - `node scripts/evolve.mjs lint-rules` gives the hints (duplicate lines, dead path refs, always-loaded size, about 37 KB today).
  - Never cut: `AGENTS.md`, `security.md`, the evolve skill, frontmatter, and any line carrying a safety rule (push, gates, merge, deny, secrets, prod, smoke, Core Principles, "never …").
  - Gate: smaller (always-loaded -5% or the touched files -1 KiB), no new duplicates or dead refs, every seeded case still caught. You read every removed line before it is merged.
  - First compress (`evolve/compress-2026-10`): the cost section of `CLAUDE.md`, three overlapping sections → one, -1.1 KB, gate PASS.
  - **How to use:**

    ```bash
    # 1. See what could be cut (hints, not orders; some "dead refs" are only examples in the text)
    node scripts/evolve.mjs lint-rules

    # 2. Run it: /evolve   (the compress:<YYYY-MM> signal is in its list once a month; it stops before any push)

    # 3. Cutting by hand on a branch? Check it with the same gate the workflow uses
    git switch -c evolve/compress-<YYYY-MM>
    #    ... edit CLAUDE.md / .claude/ and commit ...
    node scripts/evolve.mjs check-paths --base main --head evolve/compress-<YYYY-MM> --kind compress
    node scripts/evolve.mjs gate --base main --head evolve/compress-<YYYY-MM> --kind compress   # ~$0.15 per seeded case
    ```

    - **Reviewing:** read every removed line (`git diff main...<branch>`). Merge if each cut is a duplicate, a dead reference or stale; drop it otherwise.
    - **Skip a month:** open an issue labelled `evolve` whose body has `<!-- evolve:signal=compress:<YYYY-MM> -->`.

**First evolution** (`fe36685d`): from defect `62526c97` (job bodies swallowed exceptions, so failed cron jobs showed SUCCESS), the reviewer got the rule *"check what new code assumes about code it did not change; a false assumption is blocking"*. Case `2026-10-19-swallowed-job-exceptions`: caught **0/3** with the old rules, **3/3** with the new one, about $0.15 per eval run.

| Cost | |
|------|---|
| `/evolve` with no new signal | almost nothing (stops after the metrics step) |
| `/evolve` with signals | ~$1–3 agent + ~$0.15 per eval run (6 runs per proposal + 1 per existing case) |

---

## Session File (session.txt)

`session.txt` at the repo root records the **one** task in flight, so the next session (on this machine or another) can pick it up. Full rule: `.claude/rules/session-file.md`. Durable status still lives in `TASKS.md` and `docs/tracking.md`.

```
task: Collection Reorder (drag & drop videos in a collection)
status: in_progress
progress: 60
done: Plan 5ea4fec2; BE-1 position migration 6a0a5849
doing: FE-2 @dnd-kit reorder mode
next: FE-2 reorder mode; QA-1 prod smoke
plan: docs/plans/plan-collection-reorder.md
updated: 2026-10-18 21:05
```

| Field | Content |
|-------|---------|
| `task` | Short task name |
| `status` | `in_progress` \| `blocked` \| `done`; `blocked` adds a `blocker:` line (what is needed, from whom) |
| `progress` | 0–100, estimate for the whole task, not the current step |
| `done` | `;`-separated finished subtasks, each `<ID> <what> <commit>` |
| `doing` | Half-done subtasks (matching `[~]` in `TASKS.md`); `-` when none |
| `next` | `;`-separated next steps, concrete enough to act on without the conversation |
| `plan` | Plan artifact path, or `-` |
| `updated` | Local date and time, `YYYY-MM-DD HH:MM` |

**When Claude writes it**: at the start of any task with 3+ steps (or one likely to outlive the context window), after each step or commit, `blocked` while waiting on you, and `done` + `progress: 100` once pushed and verified. It is always a full overwrite; trivial edits and one-shot questions skip it.

**Sync across machines**: the file is committed and pushed together with the work it describes (a status-only update gets its own `chore(session): …` commit). Run `git pull` first on another machine. Never put secrets, hostnames/IPs or key paths in it.

**What you see**
- **At startup**, `session-start-loader.sh` shows a boxed table: 📌 Task, Status (progress bar + `updated`), ✅ Done and 🔄 Doing (commit hashes in their own column), ⛔ Blocked, → Next. An unfinished task gets the hint `▶ /task-resume`; a `done` task is shown as the last task and is not resumed. The table is skipped after a compact.
- **Status line** (`.claude/statusline-command.sh`) shows `task: <name> <progress>% (<status>)` while the task is not done.
- **`/task-resume [instruction]`** re-reads the plan, `git status`/`git log` and `TASKS.md`, corrects the file when the repo disagrees, reports in 3–5 lines, then continues from `next`. Claude never resumes on its own, and pushes still need your go-ahead.

---

## Hooks & Permissions

Hooks are bash scripts in `.claude/hooks/`, registered in `.claude/settings.json`. They are **fail-open**: without `jq` they skip. On Windows they run through Git Bash.

| Hook | When | What it does |
|------|------|--------------|
| `pre-tool-use-validator.sh` | Before Write/Edit/Bash | Asks when content looks like a secret (including Bash heredocs); denies edits to `.env`, `.git/`, `.mcp.json`; asks before editing `.claude/settings.json`, `.claude/rules/*`, `CLAUDE.md`; locks a file for 120 s while another session edits it |
| `dangerous-command-guard.sh` | Before Bash | Warns on destructive commands (`terraform destroy`, `kubectl delete`…) |
| `session-start-loader.sh` | Session start / after compact | Shows the `session.txt` status table at startup and injects the task into context; reminds to re-read the task list and plan after compaction |
| `stop-validator.sh` | End of a turn | Releases the session's locks; reminds about uncommitted files and unpushed commits |

**Permission deny rules** (no allow rule can override them): reading `.env*`, `*.pem`, `~/.ssh`, `~/.aws`, credentials; `rm -rf`, `sudo`, `git push --force`, `git reset --hard`; `curl … | bash`.

> This repo's `pre-tool-use-validator.sh` is **patched for Windows paths**. The framework's original denies every Write/Edit on Windows.

---

## Real-World Workflows

### Workflow 1: Implement a New Feature (Spec-Driven)

```bash
# Step 1: Scope — clarify requirements, create the PRD
/scope add video recommendation feature

# Step 2: Architect — technical decisions, create the ADR
/architect docs/prd/PRD-video-recommendation.md

# Step 3: Spec — precise behavior contract + Mermaid sequence diagrams (docs/sequence/)
/spec docs/prd/PRD-video-recommendation.md docs/adr/NNNN-video-recommendation.md

# Step 4: Plan — break the spec into tasks
/swarm-plan docs/specs/spec-video-recommendation.md

# Step 5: Execute (swarm)
/swarm-execute docs/plans/plan-video-recommendation.md

# Step 6: Review
/swarm-review the recommendation feature

# Step 7: Land — gates, commit, ask to push, check CI + smoke prod
/land-the-plane
```

Ceremony scales with scope: a change you can describe in one sentence needs no PRD/ADR — at most a plan, often nothing.

---

### Workflow 2: Debug a Production Issue

```bash
# Step 1: Investigate — the debugging-protocol rule always applies:
#   ≥ 3 hypotheses, find the root cause, read logs/code before editing
"prod shows CORS errors, investigate"

# Step 2: Fix + regression test (the testing skill loads itself)
/builder fix the hang in VideoImportServiceImpl

# Step 3: Postmortem (the postmortem skill loads itself)
"write a postmortem for the virtual-thread deadlock"
```

---

### Workflow 3: Security Audit

```bash
# Step 1: Full audit
/swarm-review security audit entire codebase

# Step 2: Fix critical issues
/builder fix SQL injection in UserRepo

# Step 3: Verify
/security-auditor verify security fixes

# Step 4: Document
→ docs/reports/security-audit-YYYY-MM-DD.md
```

---

### Workflow 4: Performance Optimization

```bash
# Step 1: Profile — measure before guessing
"analyze the slow /videos endpoint with EXPLAIN and timing logs"

# Step 2: Design the solution
/architect design caching strategy for the video list

# Step 3: Implement (Two Hats: refactor and optimize in separate commits)
/builder implement the cache from docs/adr/NNNN-video-list-cache.md

# Step 4: Verify — benchmark before/after
```

---

## Available Templates

### Artifact Templates

In `templates/artifacts/` (used by `/scope`, `/spec`, `/swarm-plan`):

| Template | Use case | Saved to |
|----------|----------|----------|
| **prd.template.md** | Product Requirements Document | `docs/prd/PRD-{slug}.md` |
| **adr.template.md** | Architecture Decision Record | `docs/adr/NNNN-{slug}.md` |
| **spec.template.md** | Feature Specification | `docs/specs/spec-{slug}.md` |
| **sequence.template.md** | Mermaid sequence diagrams (required with a PRD) | `docs/sequence/sequence-{slug}.md` |
| **plan.template.md** | Implementation plan | `docs/plans/plan-{slug}.md` |
| **design_spec.template.md** | Design specification | `docs/` |
| **pr_faq.template.md** | Press Release FAQ | `docs/` |
| **roadmap.template.md** | Execution roadmap | `docs/` |
| **postmortem.template.md** | Incident postmortem | `docs/reports/` |
| **security_audit.template.md** | Security audit report | `docs/reports/` |
| **system_design.template.md** | System design doc | `docs/architecture/` |

Skills also bundle their own templates in `resources/` (e.g. `designing-systems`, `planning-artifacts`, `postmortem`). Their file names follow the Artifacts table in `CLAUDE.md` (e.g. `docs/adr/NNNN-<topic>.md`); if one ever disagrees, **the table wins**.

### Claude Mechanism Templates

In `templates/claude_mechanisms/` and `.claude/templates/`:

| Template | Use case |
|----------|----------|
| **skill.template.md** | Create a new skill (workflow or library) |
| **agent.template.md** | Create a worker agent (`maxTurns` is required) |
| **rule.template.md** | Create a rule |
| **hook.template.sh** | Create a hook (`.claude/templates/`) |

---

## Tips & Tricks

### Best Practices

#### 1. Design before coding
```bash
# ❌ Bad
"implement video recommendation"

# ✅ Good
/architect design video recommendation API
# Review design → Approve
/swarm-plan docs/adr/NNNN-video-recommendation.md
```

#### 2. Use a swarm for large tasks
```bash
# ❌ Bad: one agent for a whole system
/builder implement entire authentication system

# ✅ Good: break it down, then run in parallel
/swarm-plan implement authentication system
/swarm-execute docs/plans/plan-authentication.md
```

#### 3. Security-first mindset
```bash
/security-auditor review [feature-name]
```

#### 4. Coverage ≥ 90% (CI gate)
```bash
cd api && mvn verify    # LINE coverage >= api/.coverage-threshold
```

#### 5. Record important decisions
```bash
/architect document decision to use Redis cache
# Then update docs/tracking.md
```

---

### Shortcuts

```bash
# Finish work the right way
/land-the-plane

# Audit the whole repo
/code-check all

# Render a document for a presentation
/presentation docs/prd/PRD-watch-history-v2.md
```

---

### Learning Path

#### Beginner
1. Scope an idea: `/scope add a feature`
2. Code with `/builder`, finish with `/land-the-plane`

#### Intermediate
3. Full planning flow: `/scope` → `/architect` → `/spec` → `/swarm-plan`
4. Execute with `/swarm-execute`, review with `/swarm-review`
5. Security audit with `/security-auditor`

#### Advanced
6. Create skills in `.claude/skills/` (from `skill.template.md`)
7. Write hooks (see the `hooks-conventions.md` rule)
8. `/tailor` to propose configuration changes for the stack

---

## Support & Contribution

### Getting Help

```bash
/help                       # Claude Code help
/doctor                     # Check skills/settings for problems
/context                    # See how much context the skill listing uses
```

### Customizing for Your Team

1. **Add a skill**: `.claude/skills/<name>/SKILL.md` (workflow skills: add `disable-model-invocation: true`)
2. **Update tech strategy**: `.claude/rules/tech-strategy.md`
3. **Permissions & hooks**: `.claude/settings.json` (machine-local: `.claude/settings.local.json`, not committed)
4. **Conventions for every agent**: `AGENTS.md`

Editing `CLAUDE.md`, `.claude/settings.json` or `.claude/rules/*` triggers a confirmation prompt from a hook; that is intended.

---

## Quick Reference

### Common Commands

| Command | Purpose | Output |
|---------|---------|--------|
| `/scope ...` | Scope an idea into a PRD | `docs/prd/PRD-{slug}.md` |
| `/architect ...` | Architecture decision | `docs/adr/NNNN-{slug}.md` |
| `/spec ...` | Feature specification | `docs/specs/spec-{slug}.md` |
| `/swarm-plan ...` | Task breakdown | `docs/plans/plan-{slug}.md` |
| `/swarm-execute ...` | Parallel execution | Feature complete |
| `/swarm-review ...` | Multi-perspective review | Review report |
| `/builder ...` | Implement code | Working code |
| `/qa-engineer ...` | Write tests | Test files |
| `/security-auditor ...` | Security audit | Audit report |
| `/code-check ...` | Code quality | Quality report |
| `/autopilot ...` | Small task end to end, 2 approval gates | Deployed code + `docs/reports/autopilot-*.md` |
| `/land-the-plane` | Gates → commit → push (asks first) | Pushed commit |
| `/presentation ...` | Render a doc as an HTML deck | `<doc-name>.html` |

### Frequently Used Library Skills

| When you… | Skill loaded |
|-----------|--------------|
| write tests, fix a bug | testing |
| record an architecture decision | writing-adrs, designing-systems |
| build a security-sensitive feature | threat-modeling |
| upgrade a dependency | dependency-upgrade |
| write a PRD / PR-FAQ / roadmap | planning-artifacts |
| write a postmortem | postmortem |

---

## Project-Specific Notes

### Funny Movies Architecture

- **Language**: Java 24 with Virtual Threads
- **Framework**: Spring Boot 4.x
- **Database**: PostgreSQL + Liquibase
- **Cache**: Guava LRU (Redis once horizontal scaling is needed)
- **Frontend**: React 19 + Vite (`webapp/`)
- **CI/CD**: GitHub Actions, deploying from `main` (VM over SSH + Helm chart)

### Project-Specific Commands

```bash
# Build & run
cd api && ./startLocal.sh

# Tests + coverage (gate: api/.coverage-threshold, currently 90%)
cd api && mvn verify

# Webapp
cd webapp && npm run lint && npx vitest run --root . && npm run build

# Docker build
cd api && docker build -t funny-app .
```

---

## Related Documentation

- **CLAUDE.md** — Project guide, Core Principles, Artifacts table
- **AGENTS.md** — Landing the Plane, quality gates
- **[Claude Workspace](../claude/index.md)** — All skills, agents, rules and hooks
- **docs/adr/** — Architecture Decision Records
- **docs/tracking.md** — Feature status

---

**Happy coding with Claude!**

*Last updated: 2026-10-18 (framework v5)*
