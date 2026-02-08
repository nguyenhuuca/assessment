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
 - Reading entire codebase without filtering
 - Using Sonnet/Opus for simple searches
 - Repeating same analysis multiple times
 - Loading unnecessary context

## Efficient Workflows
✅ "Use Haiku to find X, then read specific files"
✅ "Use glob to filter, then analyze matches with Sonnet"
✅ Batch read multiple files in one request
✅ Use grep before opening files

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
webapp/         # Frontend (vanilla JS)
helm/           # Kubernetes charts
doc/adr/        # Architecture decisions
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

**Coverage:** Jacoco enforces +1% increase per commit, threshold in `api/.coverage-threshold`

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

**Architecture docs:** See `doc/adr/` for decisions, `doc/` for diagrams