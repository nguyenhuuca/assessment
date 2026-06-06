# Claude Code — User Guide

> This guide explains how to use **Claude Code** with the **Funny Movies** project. After cloning the repo, read this to get the most out of the AI assistant.

---

## Table of Contents

1. [Introduction](#introduction)
2. [Pre-configured Setup](#pre-configured-setup)
3. [Personas — 12 Commands](#personas--12-commands)
4. [Skills — 90+ Automated Workflows](#skills--90-automated-workflows)
5. [Swarm — Multi-Agent Parallel Execution](#swarm--multi-agent-parallel-execution)
6. [Real-World Workflows](#real-world-workflows)
7. [Available Templates](#available-templates)
8. [Tips & Tricks](#tips--tricks)

---

## Introduction

This project is pre-configured with the **Claude Code Framework** — a powerful AI assistant system that provides:

- ✅ **Auto-loaded skills** matching the current context
- ✅ **12 specialist commands** (scope, spec, architect, builder, security, QA, presentation…)
- ✅ **90+ workflows** covering every development task
- ✅ **Multi-agent swarm** for parallel task execution
- ✅ **Templates** for ADR, PRD, design docs, and more

---

## Pre-configured Setup

### Folder Structure

```
.claude/
├── commands/          # 9 personas (architect, builder, QA…)
├── agents/            # 5 worker agents for swarm execution
├── skills/            # 90+ skill workflows (auto-triggered)
│   ├── architecture/  # System design, API design, DDD…
│   ├── core-engineering/  # Coding, testing, debugging…
│   ├── languages/     # TypeScript, Python, Go, Rust, Java…
│   ├── operations/    # Infrastructure, observability, incident…
│   ├── product/       # PRD, roadmap, requirements…
│   ├── security/      # Security audit, threat modeling…
│   └── design/        # UI/UX, accessibility, design systems…
├── rules/             # Code quality, security, tech strategy
└── settings.json      # Permissions (200+ commands allowed)

templates/
├── artifacts/         # ADR, PRD, design spec, postmortem…
└── claude_mechanisms/ # Agent, command, skill, rule templates
```

---

## Personas — 12 Commands

### 1. `/scope` — PRD Generator

**Use when:** Turning a rough feature idea into a structured PRD

**Capabilities:**
- Asks clarifying questions one at a time (problem, users, success metrics, scope)
- Reads existing codebase context before writing
- Produces a complete PRD using the official template

**Example:**
```
/scope add watch history feature
/scope add a comment section to video pages
```

**Output:** `docs/prd/PRD-{slug}.md`

---

### 2. `/spec` — Feature Specification Generator

**Use when:** Translating an approved PRD + ADR into a precise, implementable specification

**Capabilities:**
- Reads PRD and ADR to extract what's already known
- Asks clarifying questions one at a time (business rules, error cases, edge cases, performance targets)
- Verifies entity/API names against actual codebase
- Produces a Spec with exact API contract, DB schema, edge cases, and acceptance criteria

**Example:**
```
/spec docs/prd/PRD-watch-history.md docs/adr/0013-watch-history-design.md
```

**Output:** `docs/specs/spec-{slug}.md`

---

### 3. `/architect` — Principal Architect

**Use when:** Designing systems, APIs, or writing ADRs

**Capabilities:**
- System design with trade-off analysis
- API design with OpenAPI spec
- Architecture Decision Records (ADR)
- Sequential Thinking (structured reasoning)
- Does not write implementation code

**Examples:**
```
/architect design api to get list video titles
/architect design microservices architecture for video streaming
/architect should we use Redis or Guava cache?
```

**Output:** ADR document, system design doc, API specification

---

### 4. `/builder` — Builder

**Use when:** Implementing features, fixing bugs, refactoring

**Capabilities:**
- Write production code (Java, TypeScript, Python…)
- Implement features from spec
- Refactor existing code
- Fix bugs systematically
- Write SOLID, DRY, clean code

**Examples:**
```
/builder implement JWT authentication
/builder fix login bug in UserService
/builder refactor UserController to use DTO pattern
```

**Output:** Working code + tests

---

### 5. `/qa-engineer` — QA Engineer

**Use when:** Writing tests, test strategy, increasing coverage

**Capabilities:**
- Write unit tests (JUnit, Mockito, Vitest)
- Write integration tests (TestContainers)
- Define test strategy for the project
- Increase code coverage
- Performance testing

**Examples:**
```
/qa-engineer write tests for UserService
/qa-engineer create test strategy for authentication module
/qa-engineer increase coverage for service layer to 80%
```

**Output:** Test files, test plan, coverage report

---

### 6. `/security-auditor` — Security Auditor

**Use when:** Security review, vulnerability scan, compliance

**Capabilities:**
- OWASP Top 10 audit
- Security code review
- Vulnerability detection (SQL injection, XSS…)
- Threat modeling (STRIDE)
- Compliance check (GDPR, SOC2…)

**Examples:**
```
/security-auditor review authentication flow
/security-auditor scan for SQL injection vulnerabilities
/security-auditor check OWASP compliance
```

**Output:** Security audit report with severity levels (Critical / High / Medium / Low)

---

### 7. `/ui-ux-designer` — UI/UX Designer

**Use when:** Designing UI, improving UX, accessibility

**Capabilities:**
- UI/UX design
- Interface design
- Accessibility (WCAG compliance)
- Design systems
- Visual assets

**Examples:**
```
/ui-ux-designer design video list page
/ui-ux-designer improve accessibility for login form
/ui-ux-designer create design system for buttons
```

**Output:** Design mockups, component specs, accessibility report

---

### 8. `/code-check` — Code Quality Reviewer

**Use when:** Code review, checking code quality

**Capabilities:**
- SOLID principles check
- DRY violations detection
- Code smell detection
- Naming consistency
- Best practices verification

**Examples:**
```
/code-check review UserService
/code-check check SOLID violations in entire codebase
```

**Output:** Code quality report with recommendations

---

### 9. `/swarm-plan` — Swarm Planner

**Use when:** Planning complex tasks with many subtasks

**Capabilities:**
- Decompose tasks into subtasks
- Create execution plan
- Identify dependencies
- Estimate effort

**Example:**
```
/swarm-plan implement user authentication system
```

**Output:** Task breakdown plan for swarm execution

---

### 10. `/swarm-execute` — Swarm Executor

**Use when:** Executing tasks with multiple agents in parallel

**Capabilities:**
- Launch 4–8 worker agents in parallel
- Coordinate work via Beads
- Aggregate results
- Handle failures

**Example:**
```
/swarm-execute implement video recommendation feature
```

**Output:** Completed feature with multiple agents working together

---

### 11. `/swarm-review` — Swarm Reviewer

**Use when:** Multi-perspective code review

**Capabilities:**
- Launch multiple reviewer agents
- Quality review
- Security review
- Performance review
- Aggregate findings

**Example:**
```
/swarm-review security audit entire codebase
```

**Output:** Comprehensive review report from multiple perspectives

---

### 12. `/presentation` — Presentation Renderer

**Use when:** Turning a doc into a stakeholder-ready slide deck

**Capabilities:**
- Renders ONE markdown doc (PRD / Spec / ADR / Plan) into ONE self-contained `.html` deck
- Auto-detects document type and maps sections to tabs
- Render-only — keeps wording, tables, code, and diagrams faithful (no new content)
- Opens directly in any browser, no build step

**Examples:**
```
/presentation docs/prd/PRD-hot-video-priority.md
/presentation docs/adr/0006-hls-video-streaming.md
```

**Output:** `<doc-name>.html` (next to the source) — see the [Presentation command](../claude/commands/presentation.md)

---

## Skills — 90+ Automated Workflows

### Auto-triggered Skills

Skills load automatically when:
1. You use **keywords** in your prompt
2. You open a **file with a matching extension**
3. You match an **intent pattern**

### Core Engineering Skills

| Skill | Auto-trigger | Example |
|-------|-------------|---------|
| **implementing-code** | "implement", "code", "feature" | `implement user login` |
| **debugging** | "debug", "fix", "bug", "error" | `fix NPE in UserService` |
| **testing** | "test", "unit test", "coverage" | `write tests for UserService` |
| **refactoring** | "refactor", "cleanup", "improve" | `refactor UserController` |
| **optimizing** | "optimize", "performance", "slow" | `optimize database queries` |
| **data-management** | "database", "schema", "migration" | `design database schema` |

### Architecture Skills

| Skill | Auto-trigger | Example |
|-------|-------------|---------|
| **designing-systems** | "architecture", "design", "diagram" | `design video streaming system` |
| **designing-apis** | "api", "endpoint", "rest" | `design REST API for users` |
| **domain-driven-design** | "ddd", "domain", "aggregate" | `apply DDD to user module` |
| **cloud-native-patterns** | "cloud", "microservice", "k8s" | `design microservices architecture` |
| **writing-adrs** | "adr", "decision record" | `document Redis migration decision` |

### Security Skills

| Skill | Auto-trigger | Example |
|-------|-------------|---------|
| **application-security** | "security", "vulnerability", "owasp" | `security review for login` |
| **threat-modeling** | "threat", "attack", "risk" | `threat model for payment flow` |
| **security-review** | "security review", "audit" | `audit authentication code` |
| **identity-access** | "auth", "oauth", "jwt" | `implement OAuth2 authentication` |
| **compliance** | "compliance", "gdpr", "hipaa" | `check GDPR compliance` |

### Language Skills

| Language | Auto-trigger | File patterns |
|----------|-------------|---------------|
| **java** | "java", "spring boot", "maven" | `*.java`, `pom.xml` |
| **typescript** | "typescript", "ts" | `*.ts`, `*.tsx` |
| **python** | "python", "py" | `*.py`, `requirements.txt` |
| **go** | "golang", "go" | `*.go`, `go.mod` |
| **rust** | "rust", "cargo" | `*.rs`, `Cargo.toml` |
| **kotlin** | "kotlin", "android" | `*.kt`, `build.gradle.kts` |

### Operations Skills

| Skill | Auto-trigger | Example |
|-------|-------------|---------|
| **infrastructure** | "infrastructure", "terraform", "iac" | `setup AWS infrastructure` |
| **observability** | "logging", "metrics", "tracing" | `add Prometheus metrics` |
| **incident-management** | "incident", "outage", "postmortem" | `investigate production outage` |
| **chaos-engineering** | "chaos", "resilience" | `test system resilience` |

### Product Skills

| Skill | Auto-trigger | Example |
|-------|-------------|---------|
| **writing-prds** | "prd", "requirements" | `write PRD for video search` |
| **writing-pr-faqs** | "pr-faq", "vision" | `write PR-FAQ for new feature` |
| **decomposing-tasks** | "break down", "decompose" | `break down authentication task` |
| **execution-roadmaps** | "roadmap", "milestone" | `create Q1 roadmap` |
| **estimating-work** | "estimate", "story points" | `estimate video recommendation effort` |

---

## Swarm — Multi-Agent Parallel Execution

### 5 Worker Agents

| Worker | Model | Tools | Use case |
|--------|-------|-------|----------|
| **worker-explorer** | Haiku | Read, Glob, Grep | Fast codebase search |
| **worker-builder** | Sonnet | All | Implementation, testing, refactoring |
| **worker-reviewer** | Sonnet | Read, Grep | Code review, security audit |
| **worker-researcher** | Sonnet | Read, WebFetch | External research, docs |
| **worker-architect** | Opus | All | Complex design decisions |

### Swarm Workflow

```
1. /swarm-plan "implement user authentication"
   → Decompose into subtasks
   → Create execution plan

2. /swarm-execute
   → Launch 4–8 workers in parallel
   → Each worker handles 1 subtask
   → Workers coordinate via Beads

3. /swarm-review
   → Multi-perspective review
   → Aggregate findings
   → Report completion
```

### Swarm Patterns

#### Pattern 1: Parallel Implementation
```
Task: Implement video recommendation feature

Swarm breakdown:
├── Worker 1: YouTubeApiClient (fetch metadata)
├── Worker 2: ChatGptService (get recommendations)
├── Worker 3: RecommendationController (REST API)
├── Worker 4: RecommendationService (business logic)
└── Worker 5: Tests (unit + integration)

All workers execute in parallel → 5x faster
```

#### Pattern 2: Security Sweep
```
Task: Security audit entire codebase

Swarm breakdown:
├── Worker 1: Scan authentication module
├── Worker 2: Scan API endpoints
├── Worker 3: Scan database layer
├── Worker 4: Scan file operations
├── Worker 5: Scan external integrations
├── Worker 6: Check OWASP Top 10
├── Worker 7: Dependency vulnerability scan
└── Worker 8: Configuration security check

Aggregate findings → Comprehensive security report
```

#### Pattern 3: Parallel Exploration
```
Task: Find all usages of deprecated API

Swarm breakdown:
├── Worker 1: Search controllers/
├── Worker 2: Search services/
├── Worker 3: Search repositories/
├── Worker 4: Search utils/
└── Worker 5: Search tests/

Fast parallel search → Complete results
```

---

## Real-World Workflows

### Workflow 1: Implement a New Feature (Spec-Driven)

```bash
# Step 1: Scope — clarify requirements, produce PRD
/scope add video recommendation feature

# Step 2: Architect — key technical decisions, produce ADR
/architect docs/prd/PRD-video-recommendation.md

# Step 3: Spec — exact behavior contract, produce Spec
/spec docs/prd/PRD-video-recommendation.md docs/adr/NNNN-video-recommendation.md

# Step 4: Plan — task breakdown from Spec
/swarm-plan docs/specs/spec-video-recommendation.md

# Step 5: Execute (Swarm)
/swarm-execute docs/plans/plan-video-recommendation.md

# Step 6: Test (QA)
/qa-engineer write tests for RecommendationService

# Step 7: Security (Security Auditor)
/security-auditor review recommendation feature

# Step 8: Review (Code Check)
/code-check review recommendation module
```

---

### Workflow 2: Debug a Production Issue

```bash
# Step 1: Investigate
"debug login timeout issue"
→ Auto-load: debugging skill

# Step 2: Analyze
/architect analyze authentication flow

# Step 3: Fix
/builder fix timeout in UserService

# Step 4: Test
/qa-engineer write regression tests

# Step 5: Postmortem
"write postmortem for login timeout"
→ Auto-load: incident-management skill
```

---

### Workflow 3: Security Audit

```bash
# Step 1: Full audit
/swarm-review security audit entire codebase

# Step 2: Fix critical issues
/builder fix SQL injection in UserRepo
/builder fix hardcoded credentials in Helm values

# Step 3: Verify
/security-auditor verify security fixes

# Step 4: Document
"document security improvements"
→ Save to security_audit_YYYY-MM-DD.md
```

---

### Workflow 4: Performance Optimization

```bash
# Step 1: Profile
"analyze slow API endpoints"
→ Auto-load: optimizing-code skill

# Step 2: Design solution
/architect design Redis caching strategy

# Step 3: Implement
/swarm-execute implement Redis cache

# Step 4: Test performance
/qa-engineer write performance tests

# Step 5: Verify
"benchmark before/after caching"
```

---

## Available Templates

### Artifact Templates

Located in `templates/artifacts/`:

| Template | Use case | Triggered by |
|----------|----------|--------------|
| **prd.template.md** | Product Requirements Document | `/scope …` |
| **adr.template.md** | Architecture Decision Record | `/architect …` |
| **spec.template.md** | Feature Specification | `/spec …` |
| **plan.template.md** | Implementation plan | `/swarm-plan …` |
| **design_spec.template.md** | Design specification | `/architect design …` |
| **pr_faq.template.md** | Press Release FAQ | `Write PR-FAQ for …` |
| **roadmap.template.md** | Execution roadmap | `Create roadmap for …` |
| **postmortem.template.md** | Incident postmortem | `Write postmortem for …` |
| **security_audit.template.md** | Security audit report | `/security-auditor review …` |
| **system_design.template.md** | System design doc | `/architect design system …` |

### Claude Mechanism Templates

Located in `templates/claude_mechanisms/`:

| Template | Use case |
|----------|----------|
| **agent.template.md** | Create a custom worker agent |
| **command.template.md** | Create a custom persona command |
| **skill.template.md** | Create a custom skill workflow |
| **rule.template.md** | Create a custom rule |

---

## Tips & Tricks

### Best Practices

#### 1. Always design before coding
```bash
# Bad
"implement video recommendation"

# Good
/architect design video recommendation API
# Review design → Approve
/swarm-execute implement video recommendation
```

#### 2. Use swarm for complex tasks
```bash
# Bad: Single agent for a large task
/builder implement entire authentication system

# Good: Swarm for parallel execution
/swarm-plan implement authentication system
/swarm-execute
```

#### 3. Security-first mindset
```bash
# After implementing any feature:
/security-auditor review [feature-name]
```

#### 4. Keep test coverage above 80%
```bash
# After implementing:
/qa-engineer write tests for [service-name]
# Check coverage:
mvn verify
```

#### 5. Document important decisions
```bash
# Any major decision → Write ADR
/architect document decision to use Redis cache
```

---

### Shortcuts

#### Auto-triggered skills (no manual call needed)

```bash
"implement user login"   → auto-load: implementing-code
"fix bug in UserService" → auto-load: debugging
"write tests"            → auto-load: testing
"design API"             → auto-load: designing-apis
"security review"        → auto-load: application-security
"optimize queries"       → auto-load: optimizing-code
```

#### Quick commands

```bash
# Git
"create commit"       → Auto git add + commit with message
"create pull request" → Auto git push + gh pr create

# Code quality
"check code quality"  → SOLID, DRY analysis
"increase coverage"   → Generate missing tests

# Documentation
"explain this code"   → Code explanation
"document this API"   → OpenAPI/Swagger docs
```

---

### Learning Path

#### Beginner
1. Scope a feature idea: `/scope add a feature`
2. Use core personas: `/builder`, `/qa-engineer`
3. Create commits and PRs with Claude

#### Intermediate
4. Full planning flow: `/scope` → `/architect` → `/spec` → `/swarm-plan`
5. Execute with swarm: `/swarm-execute`
6. Security audit with `/security-auditor`
7. System design and ADRs with `/architect`

#### Advanced
8. Create custom skills in `.claude/skills/`
9. Configure permissions in `.claude/settings.json`
10. Build multi-agent workflows with swarm

---

## Quick Reference

### Common Commands

| Command | Purpose | Output |
|---------|---------|--------|
| `/scope …` | Scope idea into PRD | `docs/prd/PRD-{slug}.md` |
| `/architect …` | Architecture decisions | `docs/adr/NNNN-{slug}.md` |
| `/spec …` | Feature specification | `docs/specs/spec-{slug}.md` |
| `/swarm-plan …` | Task breakdown | `docs/plans/plan-{slug}.md` |
| `/builder implement …` | Code implementation | Working code |
| `/qa-engineer test …` | Write tests | Test files |
| `/security-auditor review …` | Security audit | Audit report |
| `/code-check review …` | Code quality | Quality report |
| `/swarm-execute …` | Parallel execution | Feature complete |
| `/presentation …` | Render doc into HTML deck | `<doc-name>.html` |

### Common Skills

| Keywords | Skill loaded | Use case |
|----------|--------------|----------|
| implement, code | implementing-code | Write feature |
| debug, fix, bug | debugging | Fix issues |
| test, coverage | testing | Write tests |
| design, api | designing-apis | API design |
| security, audit | application-security | Security check |
| optimize, performance | optimizing-code | Speed up code |

---

## Project-Specific Notes

### Funny Movies Architecture

- **Language**: Java 24 with Virtual Threads
- **Framework**: Spring Boot 3.x
- **Database**: PostgreSQL + Liquibase
- **Cache**: Guava LRU (Redis migration planned)
- **CI/CD**: GitHub Actions
- **Deployment**: Docker + Kubernetes (Helm)

### Project-Specific Commands

```bash
# Build & Run
cd api && ./startLocal.sh

# Run tests
cd api && ./unittest.sh

# Tests + coverage report
cd api && mvn verify

# Docker build
cd api && docker build -t funny-app .
```

---

## Related Documentation

- **CLAUDE.md** — Project config and development guide
- **README.md** — Project setup and installation
- **docs/adr/** — Architecture Decision Records
- **.claude/rules/** — Code quality, security, tech strategy rules

---

**Happy coding with Claude!**

*Last updated: 2026-05-31*
