# Claude Code — User Guide

> This guide explains how to use **Claude Code** with the **Funny Movies** project. After cloning the repo, read this to get the most out of the AI assistant.

---

## Table of Contents

1. [Introduction](#introduction)
2. [Pre-configured Setup](#pre-configured-setup)
3. [Workflow Skills — 15 Commands](#workflow-skills-15-commands)
4. [Library Skills — Loaded Automatically](#library-skills-loaded-automatically)
5. [Swarm — Multi-Agent Parallel Execution](#swarm-multi-agent-parallel-execution)
6. [Session File (session.txt)](#session-file-sessiontxt)
7. [Hooks & Permissions](#hooks-permissions)
8. [Real-World Workflows](#real-world-workflows)
9. [Available Templates](#available-templates)
10. [Tips & Tricks](#tips-tricks)

---

## Introduction

This project uses the **[Claude Agentic Framework](https://github.com/dralgorhythm/claude-agentic-framework) v5**, adapted for Funny Movies (see [Claude Workspace](../claude/index.md)):

- ✅ **15 workflow skills** you invoke as `/name` (scope, spec, architect, builder, swarm…)
- ✅ **10 library skills** Claude loads on its own when a task matches (testing, ADRs, threat modeling…)
- ✅ **5 worker agents** for swarms, each with a pinned model, `maxTurns` and tool allowlist
- ✅ **Rules** loaded in every session: core directives, code quality, security, tech strategy, debugging protocol
- ✅ **4 safety hooks** plus permission deny rules for secrets
- ✅ **Templates** for PRD, ADR, spec, plan, and more

Two conventions specific to this repo:

1. **Planning docs live in `docs/`**: `docs/adr/`, `docs/prd/`, `docs/specs/`, `docs/plans/`, with status in `docs/tracking.md`.
2. **Work is committed on `main`, and Claude always asks before every `git push`**, because CI deploys from `main`.

---

## Pre-configured Setup

### Folder Structure

```
.claude/
├── skills/
│   ├── <name>/SKILL.md          # 15 workflow skills: invoke as /name
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
AGENTS.md                        # "Landing the Plane": how a work session finishes
templates/
├── artifacts/                   # PRD, ADR, spec, plan, design spec, postmortem…
└── claude_mechanisms/           # Agent, command, skill, rule templates
```

> **There is no `.claude/commands/` any more.** The old commands became skills; the slash names are unchanged.

---

## Workflow Skills — 15 Commands

Workflow skills run only when you type `/name`; Claude never invokes them on its own.

| Command | Role | Use when | Output |
|---------|------|----------|--------|
| `/scope <idea>` | PRD Generator | An idea is still vague: asks clarifying questions, then writes the PRD | `docs/prd/PRD-{slug}.md` |
| `/spec <PRD> [ADR]` | Spec Generator | Turns an approved PRD (+ ADR when there is one) into a behavior contract | `docs/specs/spec-{slug}.md` |
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
| `tech-strategy.md` | This project's golden paths (Java 24, Spring Boot 3, React 19…) |
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

# Step 3: Spec — precise behavior contract
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
- **Framework**: Spring Boot 3.x
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
