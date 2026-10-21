# CLAUDE.md

This file provides guidance to Claude Code when working with this repository.

## Cost Optimization - READ FIRST

**Always use the cheapest model for each task:**

- **Haiku** (cheapest): File searches, reading code, finding patterns, listing files
- **Sonnet** (default): Writing code, refactoring, debugging, tests
- **Opus** (expensive): Architecture decisions only, use sparingly

**Examples:**
```
✅ "Use Haiku to find all service files"
✅ "Use Sonnet to refactor UserService"
❌ "Find and refactor all services" (uses expensive model for search)
```

**Cost target: $0.10-0.15/hour** with proper model selection.


## Claude Code Rules for Cost Optimization

### Prompt Caching
- Always enable prompt caching for CLAUDE.md
- Cache system instructions and project context
- Minimize cache breaks by keeping file structure stable

### Model Selection Priority
1. Use Haiku for: grep, find, ls, cat, reading files, pattern matching
2. Use Sonnet for: write, edit, refactor, test, debug
3. Use Opus ONLY when explicitly requested for architecture

### Token Optimization
- Batch similar operations in one request
- Use glob patterns to filter files before reading
- Request specific files instead of "analyze everything"
- Keep responses focused and concise

### Cost Targets
- Search/explore tasks: <$0.01
- Implementation tasks: $0.05-0.20
- Daily budget: <$0.50 for normal development

### Anti-patterns to Avoid
❌ Reading entire codebase without filtering
❌ Using Sonnet/Opus for simple searches
❌ Repeating same analysis multiple times
❌ Loading unnecessary context

## Efficient Workflows
✅ "Use Haiku to find X, then read specific files"
✅ "Use glob to filter, then analyze matches with Sonnet"
✅ Batch read multiple files in one request
✅ Use grep before opening files

## Task Tracking
- Track all tasks in TASKS.md
- Before starting any work, read TASKS.md to check current status
- After completing each task, update TASKS.md with status and notes
- Format: [ ] pending, [x] done, [~] in progress

## Core Principles

Distilled from `.claude/rules/` (auto-loaded); the rules and skills refer to these by number.

1. **Understand First** — read before writing; grep before creating; verify APIs via docs (Context7) before trusting training data.
2. **Prove It Works** — tests first, run quality gates before every commit, a regression test for every bug fix.
3. **Keep It Safe** — no secrets in code, validate input, parameterized queries, least privilege, flag vulnerabilities immediately.
4. **Keep It Simple** — single responsibility, no premature abstraction, delete dead code, fix warnings before committing.
5. **Don't Repeat Yourself** — check `.claude/skills/` before ad-hoc solutions; one source of truth for business logic.
6. **Ship It** — commit on `main`, **ask before every `git push`** (CI deploys from `main`), then watch CI and smoke-test prod. Protocol: AGENTS.md "Landing the Plane".
7. **Leave a Trail** — artifacts in `docs/` (table below), status in `docs/tracking.md`, decisions in ADRs.

## Workflow & Artifacts

**Planning flow**: PR-FAQ → PRD → ADR → Spec (+ Design spec for UI) → Sequence diagrams → Plan → implementation; ceremony scales with scope (`core-directives.md` Rule 6): PRD by feature size, ADR by decision type at any size, and **every feature with a PRD gets Mermaid sequence diagrams** before its plan. Skills and templates (`templates/artifacts/`) use these names; if one disagrees, this table wins:

| Type | Location | Example |
|------|----------|---------|
| ADR | `docs/adr/NNNN-<topic>.md` | `docs/adr/0021-watch-history.md` |
| PR-FAQ | `docs/prd/PR-FAQ-<feature>.md` | — |
| PRD | `docs/prd/PRD-<feature>.md` | `docs/prd/PRD-watch-history-v2.md` |
| Spec | `docs/specs/spec-<feature>.md` | `docs/specs/spec-user-settings.md` |
| Design spec (UI) | `docs/specs/design-spec-<component>.md` | — |
| System design | `docs/architecture/system-design-<component>.md` | — |
| Sequence diagrams (Mermaid, required with a PRD) | `docs/sequence/sequence-<feature>.md` (slug = PRD slug) | `docs/sequence/sequence-watch-history-v2.md` |
| Plan / roadmap | `docs/plans/plan-<feature>.md`, `docs/plans/roadmap-<project>.md` | `docs/plans/plan-watch-history-v2.md` |
| Security audit / postmortem | `docs/reports/security-audit-<YYYY-MM-DD>.md`, `docs/reports/postmortem-<YYYY-MM-DD>-<incident>.md` | `docs/reports/system-health-2026-05-30.md` |
| Feature status | `docs/tracking.md` | — |

Ephemeral notes go to `./scratchpad/` (gitignored). `./artifacts/` holds design images (Stitch exports), not planning docs.

**User-invoked skills**: `/swarm-plan`, `/swarm-execute`, `/swarm-review`, `/swarm-research`, `/architect`, `/builder`, `/qa-engineer`, `/security-auditor`, `/ui-ux-designer`, `/code-check`, `/land-the-plane`, `/tailor`, `/scope` (idea → PRD), `/spec` (PRD + ADR → spec + sequence diagrams), `/presentation` (doc → HTML deck), `/autopilot` (one small feature or fix: plan ⇄ review → your approval → build ⇄ review → QA tests ×2 → commit → your push approval; `docs/plans/plan-autopilot.md`), `/task-resume` (continue the unfinished task in `session.txt`; format and update rules in `.claude/rules/session-file.md`).

## Project Overview

**Funny Movies** - Java 24 video streaming app with Spring Boot 3.x, PostgreSQL, Virtual Threads (Loom), YouTube API integration, and passwordless auth.

## Essential Commands

**Local development:**
```bash
cd api
./startLocal.sh              # Build & run
./unittest.sh                # Run tests
mvn verify                   # Tests + coverage
mvn test -Dtest=ClassName    # Single test
```

**Frontend (webapp):**
```bash
cd webapp
npm install                  # Install dependencies
npm run dev                  # Dev server (Vite)
npm run build                # Production build → dist/
npm run test                 # Run Vitest tests
npm run lint                 # ESLint check
```

**Environment setup:**
```bash
cp api/.env.example api/.env
# Required: DB_PASS, DB_HOST, GOOGLE_KEY, GPT_KEY, JWT_SECRET, EMAIL_SENDER, EMAIL_PASS
```

**Database:**
```bash
createdb funnyapp
psql -d funnyapp -f db/dump.sql
```

**Docker:**
```bash
cd api
mvn clean package
docker build -t funny-app .
docker run -p 8081:8081 --env-file env.local funny-app
```

**API endpoints:**
- Swagger: http://localhost:8081/swagger-ui/ (dev profile)
- Health: http://localhost:8081/actuator/health

## Project Structure

```
api/
├── src/main/java/com/canhlabs/funnyapp/
│   ├── web/          # REST controllers
│   ├── service/      # Business logic
│   ├── repo/         # JPA repositories
│   ├── entity/       # JPA entities
│   ├── dto/          # Data Transfer Objects
│   ├── client/       # External APIs (YouTube)
│   ├── config/       # Spring configuration
│   ├── cache/        # Guava LRU cache
│   ├── aop/          # Aspects (audit, rate limit, masking)
│   ├── jobs/         # Scheduled tasks
│   └── utils/        # Utilities
├── src/main/resources/
│   ├── application.yaml       # Config
│   └── db/changelog/          # Liquibase migrations
└── pom.xml
webapp/         # Frontend (React 19 + Vite)
helm/           # Kubernetes charts
docs/adr/       # Architecture decisions
```

## Key Architecture

**Virtual Threads:** Java 24 with `spring.threads.virtual.enabled=true`, uses `StructuredTaskScope` for concurrency

**Caching:** Guava LRU (in-memory) for video metadata, chunks, stats. Config: `cache.type=guava`

**Auth:** Passwordless magic links + optional MFA (TOTP)

**Database:** PostgreSQL + JPA (`ddl-auto: none`) + Liquibase

**External APIs:**
- YouTube API via `YouTubeApiClient`
- ChatGPT via `ChatGptService`
- Email via Spring Mail (Gmail SMTP)

**AOP Features:**
- `@AuditLog`: Audit logging with masking
- `@RateLimited`: Rate limiting

## Development Patterns

**Error handling:** `@ControllerAdvice` + custom exceptions extending `Error`

**API responses:**
- `ResultObjectInfo<T>`: Single object
- `ResultListInfo<T>`: List
- `ResultErrorInfo`: Errors

**Services:** Interface in `service/`, impl in `service/impl/`

**Validation:** Use `Contract` and `ContractDSL`:
```java
Contract.require(condition, "Error");
ContractDSL.of(value).notNull().inRange(min, max);
```

## Testing & CI

**Coverage:** CI requires LINE coverage >= the fixed minimum in `api/.coverage-threshold` (currently 90%)

**CI Pipeline:** Build → Test → Coverage → Docker → Deploy
- Version in `api/.funny-app.version` (auto-incremented)
- Docker image pushed to Docker Hub
- Deployed via SSH/SCP or Kubernetes Helm

**Java 24 Preview:** Requires `--enable-preview` flag in compiler and runtime

## Configuration Notes

**Profiles:**
- `dev`: Swagger enabled
- `prod`: Swagger disabled (default)

**Spring config:** All in `application.yaml`, override via environment variables

**Critical env vars:** See `.env.example` for required keys

## Quick Reference

**Find entity/DTO:** Check `api/src/main/java/com/canhlabs/funnyapp/entity/` or `/dto/`

**Add endpoint:** Controller in `web/`, service in `service/`, repo in `repo/`

**Add migration:** Create file in `src/main/resources/db/changelog/`

**View logs:** Check AOP audit logs (sensitive fields auto-masked)

**Architecture docs:** See `docs/adr/` for decisions, `docs/architecture/` and `docs/puml/` for diagrams