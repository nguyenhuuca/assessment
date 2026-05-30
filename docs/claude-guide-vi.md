# 🤖 Hướng dẫn sử dụng Claude Code

> **Tài liệu này** hướng dẫn cách sử dụng Claude Code với project **Funny Movies**. Sau khi clone repo về, đọc file này để biết cách tận dụng AI assistant hiệu quả nhất.

---

## 📚 Mục lục

1. [Giới thiệu](#giới-thiệu)
2. [Cấu hình có sẵn](#cấu-hình-có-sẵn)
3. [Personas - 9 vai trò chuyên môn](#personas---9-vai-trò-chuyên-môn)
4. [Skills - 90+ workflows tự động](#skills---90-workflows-tự-động)
5. [Swarm - Multi-agent parallel execution](#swarm---multi-agent-parallel-execution)
6. [Workflows thực tế](#workflows-thực-tế)
7. [Templates có sẵn](#templates-có-sẵn)
8. [Tips & Tricks](#tips--tricks)

---

## Giới thiệu

Project này đã được config sẵn với **Claude Code Framework** - một hệ thống AI assistant mạnh mẽ giúp:

- ✅ **Tự động load skills** phù hợp với context
- ✅ **9 personas chuyên môn** (architect, builder, security, QA...)
- ✅ **90+ workflows** cho mọi task development
- ✅ **Multi-agent swarm** để execute task song song
- ✅ **Templates** cho ADR, PRD, design docs...

---

## Cấu hình có sẵn

### 📁 Cấu trúc thư mục

```
.claude/
├── commands/          # 9 personas (architect, builder, QA...)
├── agents/            # 5 worker agents cho swarm execution
├── skills/            # 90+ skill workflows (auto-triggered)
│   ├── architecture/  # System design, API design, DDD...
│   ├── core-engineering/  # Coding, testing, debugging...
│   ├── languages/     # TypeScript, Python, Go, Rust, Java...
│   ├── operations/    # Infrastructure, observability, incident...
│   ├── product/       # PRD, roadmap, requirements...
│   ├── security/      # Security audit, threat modeling...
│   └── design/        # UI/UX, accessibility, design systems...
├── rules/             # Code quality, security, tech strategy
└── settings.json      # Permissions (200+ commands allowed)

templates/
├── artifacts/         # ADR, PRD, design spec, postmortem...
└── claude_mechanisms/ # Agent, command, skill, rule templates
```

---

## Personas - 9 vai trò chuyên môn

### 1. 🏛️ `/architect` - Principal Architect

**Dùng khi:** Design system, API, viết ADR

**Capabilities:**
- System design với trade-off analysis
- API design với OpenAPI spec
- Architecture Decision Records (ADR)
- Sequential Thinking (structured reasoning)
- Không viết implementation code

**Example:**
```
/architect design api to get list video titles
/architect design microservices architecture for video streaming
/architect should we use Redis or Guava cache?
```

**Output:** ADR document, system design doc, API specification

---

### 2. 🔨 `/builder` - Builder

**Dùng khi:** Implement feature, fix bug, refactor code

**Capabilities:**
- Write production code (Java, TypeScript, Python...)
- Implement features theo spec
- Refactor existing code
- Fix bugs systematically
- Viết code SOLID, DRY, clean

**Example:**
```
/builder implement JWT authentication
/builder fix login bug in UserService
/builder refactor UserController to use DTO pattern
```

**Output:** Working code + tests

---

### 3. 🧪 `/qa-engineer` - QA Engineer

**Dùng khi:** Write tests, test strategy, increase coverage

**Capabilities:**
- Write unit tests (JUnit, Mockito, Vitest)
- Write integration tests (TestContainers)
- Test strategy cho project
- Increase code coverage
- Performance testing

**Example:**
```
/qa-engineer write tests for UserService
/qa-engineer create test strategy for authentication module
/qa-engineer increase coverage for service layer to 80%
```

**Output:** Test files, test plan, coverage report

---

### 4. 🔒 `/security-auditor` - Security Auditor

**Dùng khi:** Security review, vulnerability scan, compliance

**Capabilities:**
- OWASP Top 10 audit
- Security code review
- Vulnerability detection (SQL injection, XSS...)
- Threat modeling (STRIDE)
- Compliance check (GDPR, SOC2...)

**Example:**
```
/security-auditor review authentication flow
/security-auditor scan for SQL injection vulnerabilities
/security-auditor check OWASP compliance
```

**Output:** Security audit report with severity levels (Critical/High/Medium/Low)

---

### 5. 🎨 `/ui-ux-designer` - UI/UX Designer

**Dùng khi:** Design UI, improve UX, accessibility

**Capabilities:**
- UI/UX design
- Interface design
- Accessibility (WCAG compliance)
- Design systems
- Visual assets

**Example:**
```
/ui-ux-designer design video list page
/ui-ux-designer improve accessibility for login form
/ui-ux-designer create design system for buttons
```

**Output:** Design mockups, component specs, accessibility report

---

### 6. 🔍 `/code-check` - Code Quality Reviewer

**Dùng khi:** Code review, check code quality

**Capabilities:**
- SOLID principles check
- DRY violations detection
- Code smell detection
- Naming consistency
- Best practices verification

**Example:**
```
/code-check review UserService
/code-check check SOLID violations in entire codebase
```

**Output:** Code quality report với recommendations

---

### 7. 🧩 `/swarm-plan` - Swarm Planner

**Dùng khi:** Plan complex task với nhiều subtasks

**Capabilities:**
- Decompose task thành subtasks
- Create execution plan
- Identify dependencies
- Estimate effort

**Example:**
```
/swarm-plan implement user authentication system
```

**Output:** Task breakdown plan cho swarm execution

---

### 8. ⚡ `/swarm-execute` - Swarm Executor

**Dùng khi:** Execute task với multiple agents song song

**Capabilities:**
- Launch 4-8 worker agents in parallel
- Coordinate work via Beads
- Aggregate results
- Handle failures

**Example:**
```
/swarm-execute implement video recommendation feature
```

**Output:** Completed feature với multiple agents working together

---

### 9. 🔬 `/swarm-review` - Swarm Reviewer

**Dùng khi:** Multi-perspective code review

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

**Output:** Comprehensive review report từ multiple perspectives

---

## Skills - 90+ workflows tự động

### 🎯 Auto-triggered skills

Skills tự động load khi:
1. Bạn dùng **keywords** trong câu hỏi
2. Bạn mở **file có extension** phù hợp
3. Bạn dùng **intent patterns** phù hợp

### 📦 Core Engineering Skills

| Skill | Auto-trigger | Example |
|-------|-------------|---------|
| **implementing-code** | "implement", "code", "feature" | `implement user login` |
| **debugging** | "debug", "fix", "bug", "error" | `fix NPE in UserService` |
| **testing** | "test", "unit test", "coverage" | `write tests for UserService` |
| **refactoring** | "refactor", "cleanup", "improve" | `refactor UserController` |
| **optimizing** | "optimize", "performance", "slow" | `optimize database queries` |
| **data-management** | "database", "schema", "migration" | `design database schema` |

### 🏗️ Architecture Skills

| Skill | Auto-trigger | Example |
|-------|-------------|---------|
| **designing-systems** | "architecture", "design", "diagram" | `design video streaming system` |
| **designing-apis** | "api", "endpoint", "rest" | `design REST API for users` |
| **domain-driven-design** | "ddd", "domain", "aggregate" | `apply DDD to user module` |
| **cloud-native-patterns** | "cloud", "microservice", "k8s" | `design microservices architecture` |
| **writing-adrs** | "adr", "decision record" | `document Redis migration decision` |

### 🔒 Security Skills

| Skill | Auto-trigger | Example |
|-------|-------------|---------|
| **application-security** | "security", "vulnerability", "owasp" | `security review for login` |
| **threat-modeling** | "threat", "attack", "risk" | `threat model for payment flow` |
| **security-review** | "security review", "audit" | `audit authentication code` |
| **identity-access** | "auth", "oauth", "jwt" | `implement OAuth2 authentication` |
| **compliance** | "compliance", "gdpr", "hipaa" | `check GDPR compliance` |

### 💻 Language Skills

| Language | Auto-trigger | File patterns |
|----------|-------------|---------------|
| **java** | "java", "spring boot", "maven" | `*.java`, `pom.xml` |
| **typescript** | "typescript", "ts" | `*.ts`, `*.tsx` |
| **python** | "python", "py" | `*.py`, `requirements.txt` |
| **go** | "golang", "go" | `*.go`, `go.mod` |
| **rust** | "rust", "cargo" | `*.rs`, `Cargo.toml` |
| **swift** | "swift", "ios" | `*.swift`, `*.xcodeproj` |
| **kotlin** | "kotlin", "android" | `*.kt`, `build.gradle.kts` |

### 🚀 Operations Skills

| Skill | Auto-trigger | Example |
|-------|-------------|---------|
| **infrastructure** | "infrastructure", "terraform", "iac" | `setup AWS infrastructure` |
| **observability** | "logging", "metrics", "tracing" | `add Prometheus metrics` |
| **incident-management** | "incident", "outage", "postmortem" | `investigate production outage` |
| **deploy-railway** | "railway", "deploy" | `deploy to Railway` |
| **chaos-engineering** | "chaos", "resilience" | `test system resilience` |

### 📋 Product Skills

| Skill | Auto-trigger | Example |
|-------|-------------|---------|
| **writing-prds** | "prd", "requirements" | `write PRD for video search` |
| **writing-pr-faqs** | "pr-faq", "vision" | `write PR-FAQ for new feature` |
| **decomposing-tasks** | "break down", "decompose" | `break down authentication task` |
| **execution-roadmaps** | "roadmap", "milestone" | `create Q1 roadmap` |
| **estimating-work** | "estimate", "story points" | `estimate video recommendation effort` |

---

## Swarm - Multi-agent parallel execution

### 🐝 5 Worker Agents

| Worker | Model | Tools | Use case |
|--------|-------|-------|----------|
| **worker-explorer** | Haiku | Read, Glob, Grep | Fast codebase search |
| **worker-builder** | Sonnet | All | Implementation, testing, refactoring |
| **worker-reviewer** | Sonnet | Read, Grep | Code review, security audit |
| **worker-researcher** | Sonnet | Read, WebFetch | External research, docs |
| **worker-architect** | Opus | All | Complex design decisions |

### 🎯 Swarm Workflow

```
1. /swarm-plan "implement user authentication"
   → Decompose task thành subtasks
   → Create execution plan

2. /swarm-execute
   → Launch 4-8 workers in parallel
   → Each worker handles 1 subtask
   → Workers coordinate via Beads

3. /swarm-review
   → Multi-perspective review
   → Aggregate findings
   → Report completion
```

### 📊 Swarm Patterns

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

## Workflows thực tế

### 🎯 Workflow 1: Implement New Feature

```bash
# Step 1: Design (Architect)
/architect design video recommendation API

# Step 2: Plan (Swarm)
/swarm-plan implement video recommendation

# Step 3: Execute (Swarm)
/swarm-execute implement video recommendation

# Step 4: Test (QA)
/qa-engineer write tests for RecommendationService

# Step 5: Security (Security Auditor)
/security-auditor review recommendation feature

# Step 6: Review (Code Check)
/code-check review recommendation module
```

---

### 🐛 Workflow 2: Debug Production Issue

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

### 🔒 Workflow 3: Security Audit

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

### ⚡ Workflow 4: Performance Optimization

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

## Templates có sẵn

### 📄 Artifacts Templates

Located in `templates/artifacts/`:

| Template | Use case | Command |
|----------|----------|---------|
| **adr.template.md** | Architecture Decision Record | `/architect design ...` |
| **design_spec.template.md** | Design specification | `/architect design ...` |
| **prd.template.md** | Product Requirements | Write PRD for ... |
| **pr_faq.template.md** | Press Release FAQ | Write PR-FAQ for ... |
| **roadmap.template.md** | Execution roadmap | Create roadmap for ... |
| **postmortem.template.md** | Incident postmortem | Write postmortem for ... |
| **security_audit.template.md** | Security audit report | `/security-auditor review ...` |
| **system_design.template.md** | System design doc | `/architect design system ...` |
| **plan.template.md** | Implementation plan | `/swarm-plan ...` |

### 🔧 Claude Mechanisms Templates

Located in `templates/claude_mechanisms/`:

| Template | Use case |
|----------|----------|
| **agent.template.md** | Create custom worker agent |
| **command.template.md** | Create custom persona command |
| **skill.template.md** | Create custom skill workflow |
| **rule.template.md** | Create custom rule |

---

## Tips & Tricks

### 💡 Best Practices

#### 1. Luôn design trước khi code
```bash
# ❌ Bad
"implement video recommendation"

# ✅ Good
/architect design video recommendation API
# Review design → Approve
/swarm-execute implement video recommendation
```

#### 2. Dùng swarm cho complex tasks
```bash
# ❌ Bad: Single agent for large task
/builder implement entire authentication system

# ✅ Good: Swarm for parallel execution
/swarm-plan implement authentication system
/swarm-execute
```

#### 3. Security-first mindset
```bash
# After implementing any feature:
/security-auditor review [feature-name]
```

#### 4. Test coverage luôn > 80%
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

### 🚀 Shortcuts

#### Auto-triggered skills

```bash
# No need to call skill manually, just use keywords:

"implement user login" → auto-load implementing-code skill
"fix bug in UserService" → auto-load debugging skill
"write tests" → auto-load testing skill
"design API" → auto-load designing-apis skill
"security review" → auto-load application-security skill
"optimize queries" → auto-load optimizing-code skill
```

#### Quick commands

```bash
# Git operations
"create commit"          → Auto git add, commit with message
"create pull request"    → Auto git push, gh pr create

# Code quality
"check code quality"     → SOLID, DRY analysis
"increase coverage"      → Generate missing tests

# Documentation
"explain this code"      → Code explanation
"document this API"      → OpenAPI/Swagger docs
```

---

### 🎓 Learning Path

#### Beginner
1. Sử dụng personas: `/architect`, `/builder`, `/qa-engineer`
2. Thử auto-triggered skills: implement, test, debug
3. Tạo commit và PR với Claude

#### Intermediate
4. Sử dụng `/swarm-plan` và `/swarm-execute`
5. Security audit với `/security-auditor`
6. Design systems với `/architect`

#### Advanced
7. Create custom skills trong `.claude/skills/`
8. Configure permissions trong `.claude/settings.json`
9. Build multi-agent workflows với swarm

---

## 📞 Support & Contribution

### Getting Help

```bash
# Claude Code help
/help

# Feedback/Issues
https://github.com/anthropics/claude-code/issues
```

### Customize cho team

1. **Add custom skills**: `.claude/skills/custom/`
2. **Add custom commands**: `.claude/commands/`
3. **Update tech strategy**: `.claude/rules/tech-strategy.md`
4. **Configure permissions**: `.claude/settings.json`

---

## 🎯 Quick Reference

### Common Commands

| Command | Purpose | Output |
|---------|---------|--------|
| `/architect design ...` | System/API design | ADR, design doc |
| `/builder implement ...` | Code implementation | Working code |
| `/qa-engineer test ...` | Write tests | Test files |
| `/security-auditor review ...` | Security audit | Audit report |
| `/code-check review ...` | Code quality | Quality report |
| `/swarm-execute ...` | Parallel execution | Feature complete |

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

## 📊 Project-Specific Notes

### Funny Movies Architecture

- **Language**: Java 24 with Virtual Threads
- **Framework**: Spring Boot 3.5.0
- **Database**: PostgreSQL + Liquibase
- **Cache**: Guava (migrate to Redis planned)
- **CI/CD**: GitHub Actions
- **Deployment**: Docker + Kubernetes (Helm)

### Key Technologies

- Virtual Threads for concurrency
- StructuredTaskScope for coordinated tasks
- JWT authentication
- Magic link passwordless auth
- YouTube API integration
- ChatGPT API for recommendations

### Project-Specific Commands

```bash
# Build & Run
cd api && ./startLocal.sh

# Run tests
cd api && ./unittest.sh

# Check coverage (current: 35%)
cd api && mvn verify

# Docker build
cd api && docker build -t funny-app .
```

---

## 🔗 Related Documentation

- **CLAUDE.md**: Project documentation and development guide
- **README.md**: Project setup and installation
- **doc/adr/**: Architecture Decision Records
- **.claude/rules/**: Code quality, security, tech strategy rules

---

**Happy coding with Claude! 🚀**

*Last updated: 2026-02-07*
