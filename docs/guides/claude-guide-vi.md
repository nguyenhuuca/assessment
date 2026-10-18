# 🤖 Hướng dẫn sử dụng Claude Code

> **Tài liệu này** hướng dẫn cách sử dụng Claude Code với project **Funny Movies**. Sau khi clone repo về, đọc file này để biết cách tận dụng AI assistant hiệu quả nhất.

---

## 📚 Mục lục

1. [Giới thiệu](#gioi-thieu)
2. [Cấu hình có sẵn](#cau-hinh-co-san)
3. [Workflow skills - 15 lệnh](#workflow-skills-15-lenh)
4. [Library skills - tự động load](#library-skills-tu-ong-load)
5. [Swarm - Multi-agent parallel execution](#swarm-multi-agent-parallel-execution)
6. [Session file (session.txt)](#session-file-sessiontxt)
7. [Hooks & permissions](#hooks-permissions)
8. [Workflows thực tế](#workflows-thuc-te)
9. [Templates có sẵn](#templates-co-san)
10. [Tips & Tricks](#tips-tricks)

---

## Giới thiệu

Project này dùng **[Claude Agentic Framework](https://github.com/dralgorhythm/claude-agentic-framework) v5**, đã chỉnh cho Funny Movies (xem [Claude Workspace](../claude/index.md)):

- ✅ **15 workflow skills** gọi bằng `/tên` (scope, spec, architect, builder, swarm…)
- ✅ **10 library skills** Claude tự load khi task phù hợp (testing, ADR, threat modeling…)
- ✅ **5 worker agents** cho swarm, mỗi agent có model, `maxTurns` và tools cố định
- ✅ **Rules** nạp vào mọi session: core directives, code quality, security, tech strategy, debugging protocol
- ✅ **4 safety hooks** + permission deny cho secrets
- ✅ **Templates** cho PRD, ADR, spec, plan…

Hai quy ước riêng của repo này cần nhớ:

1. **Tài liệu planning nằm trong `docs/`**: `docs/adr/`, `docs/prd/`, `docs/specs/`, `docs/plans/`, trạng thái ở `docs/tracking.md`.
2. **Commit trên `main`, và Claude luôn hỏi trước mỗi lần `git push`**, vì CI deploy từ `main`.

---

## Cấu hình có sẵn

### 📁 Cấu trúc thư mục

```
.claude/
├── skills/
│   ├── <name>/SKILL.md          # 15 workflow skills: gọi bằng /name
│   ├── architecture/            # designing-systems, writing-adrs
│   ├── core-engineering/        # testing, dependency-upgrade, review-steering
│   ├── design/                  # presentation
│   ├── operations/              # postmortem, swarm-coordination
│   ├── product/                 # planning-artifacts (PR-FAQ, PRD, roadmap)
│   └── security/                # threat-modeling
├── agents/                      # 5 worker agents cho swarm
├── rules/                       # Nạp vào mọi session
├── hooks/                       # 4 safety hooks (bash)
├── templates/                   # Template của framework
├── statusline-command.sh        # Status line: model | ctx | cost | rate | task hiện tại
└── settings.json                # Permissions allow/deny + đăng ký hooks

session.txt                      # Task đang làm (được commit; xem Session file)
AGENTS.md                        # "Landing the Plane": cách kết thúc một phiên làm việc
templates/
├── artifacts/                   # PRD, ADR, spec, plan, design spec, postmortem...
└── claude_mechanisms/           # Agent, command, skill, rule templates
```

> **Từ v5 không còn `.claude/commands/`.** Các lệnh cũ đã thành skill, tên lệnh giữ nguyên.

---

## Workflow skills - 15 lệnh

Workflow skills chỉ chạy khi bạn gõ `/tên`; Claude không tự gọi chúng.

| Lệnh | Vai trò | Dùng khi | Output |
|------|---------|----------|--------|
| `/scope <ý tưởng>` | PRD Generator | Ý tưởng còn mơ hồ: hỏi lại cho rõ rồi viết PRD | `docs/prd/PRD-{slug}.md` |
| `/spec <PRD> [ADR]` | Spec Generator | Biến PRD đã duyệt (+ ADR nếu có) thành behavior contract | `docs/specs/spec-{slug}.md` |
| `/architect <chủ đề>` | Principal Architect | Thiết kế hệ thống, ghi quyết định thành ADR | `docs/adr/NNNN-{slug}.md` |
| `/builder <task>` | Builder | Implement, debug, refactor có test | Code + tests |
| `/qa-engineer <component>` | QA Engineer | Test strategy, E2E, accessibility gate | Test plan / tests |
| `/security-auditor <scope>` | Security Auditor | OWASP/STRIDE audit, data-flow tracing | Audit report |
| `/ui-ux-designer <flow>` | UI/UX Designer | Thiết kế giao diện, kiểm tra WCAG bằng DevTools | Design spec |
| `/code-check <scope>` | Codebase Auditor | Audit SOLID, DRY, consistency, scope adherence | Quality report |
| `/swarm-plan <feature>` | Planning Orchestrator | Chia feature lớn thành task 200–400 LOC | `docs/plans/plan-{slug}.md` |
| `/swarm-execute <plan>` | Execution Orchestrator | Chạy plan với nhiều worker song song | Code đã commit |
| `/swarm-review <scope>` | Adversarial Reviewer | Review nhiều góc: security, performance, scope… | Review report |
| `/swarm-research <topic>` | Research Orchestrator | Nghiên cứu sâu, so sánh công nghệ | Research report |
| `/land-the-plane` | Finish-Line Protocol | Chạy gates → commit → (hỏi) push → kiểm CI | Commit đã push |
| `/tailor` | Configuration Tailor | Phát hiện stack, đề xuất chỉnh cấu hình (chỉ đề xuất) | `scratchpad/tailor-proposal.md` |
| `/task-resume [chỉ dẫn]` | Task Resumer | Làm tiếp task dở trong `session.txt` sau khi kiểm lại repo | Làm tiếp từ `next` |

Ví dụ:

```bash
/scope add a trending videos section to the homepage
/spec docs/prd/PRD-bookmark-feature.md docs/adr/0012-bookmark-feature-design.md
/swarm-plan docs/specs/spec-video-recommendation.md
/swarm-execute docs/plans/plan-watch-history-v2.md
/swarm-review commit 0bf86a71
/presentation docs/adr/0021-watch-history.md
```

> `/presentation` là library skill nhưng vẫn gọi được bằng `/presentation <đường dẫn>`. Nó render một PRD/Spec/ADR/Plan thành một file `.html` mở thẳng trong browser.

---

## Library skills - tự động load

Claude đọc tên và mô tả của mọi skill khi khởi động, rồi tự nạp nội dung skill khi task khớp với mô tả. Không cần keyword cố định hay file đăng ký.

| Skill | Nhóm | Tự load khi |
|-------|------|-------------|
| **testing** | core-engineering | Viết test, sửa bug (regression test), tăng coverage |
| **dependency-upgrade** | core-engineering | Nâng version thư viện/action: pin đã kiểm chứng, rollout từng bước |
| **review-steering** | core-engineering | Tạo/cập nhật `REVIEW.md` để định hướng code review |
| **designing-systems** | architecture | Thiết kế hệ thống, so sánh kiến trúc |
| **writing-adrs** | architecture | Ghi lại một quyết định kỹ thuật |
| **threat-modeling** | security | Feature có rủi ro bảo mật (STRIDE, prompt injection…) |
| **planning-artifacts** | product | Viết PR-FAQ, PRD, roadmap |
| **postmortem** | operations | Viết postmortem sau sự cố |
| **swarm-coordination** | operations | Điều phối nhiều agent, handoff, budget token |
| **presentation** | design | Biến tài liệu thành slide HTML |

**Vì sao chỉ còn 24 skill (trước khoảng 65)?** Tên và mô tả của mọi skill được nạp vào context ở mỗi session. Quá nhiều skill thì vượt ngân sách, mô tả bị cắt và Claude chọn sai skill. Các skill chỉ lặp lại kiến thức model đã biết (java, python, terraform, deploy-*…) đã bị bỏ. Thông tin version cụ thể thì tra bằng Context7.

Những kỷ luật trước đây nằm trong skill nay nằm trong **rules** (nạp vào mọi session):

| Rule | Nội dung |
|------|----------|
| `core-directives.md` | Quy trình planning, nơi lưu tài liệu, quy ước commit/push |
| `debugging-protocol.md` | Đưa ra ít nhất 3 giả thuyết trước khi chọn một; bắt buộc tìm root cause; đọc lại file trước khi sửa |
| `code-quality.md` | SOLID, DRY, Two Hats, quality gates |
| `security.md` | Checklist (ghi rõ mức enforce thật), prompt injection, OWASP |
| `tech-strategy.md` | Golden path của project (Java 24, Spring Boot 3, React 19…) |
| `agent-constraints.md` | Luật cho swarm workers |

---

## Swarm - Multi-agent parallel execution

### 🐝 5 Worker Agents

| Worker | Model | Tools | maxTurns | Use case |
|--------|-------|-------|----------|----------|
| **worker-explorer** | Haiku | Read, Grep, Glob, WebFetch, WebSearch | 30 | Tìm code nhanh, tra cứu API/docs |
| **worker-builder** | Sonnet | Read, Write, Edit, Bash, Glob, Grep | 90 | Implement, test, refactor (chạy trong git worktree riêng) |
| **worker-reviewer** | Sonnet | Read, Grep, Glob, Bash | 60 | Review code, security, performance |
| **worker-research** | Sonnet | Read, Grep, Glob, WebFetch, WebSearch, Write | 80 | Nghiên cứu sâu nhiều nguồn |
| **worker-architect** | Opus | Read, Grep, Glob, Write | 40 | Quyết định thiết kế phức tạp |

> `worker-researcher` cũ đã tách thành `worker-explorer` (tra nhanh) và `worker-research` (nghiên cứu sâu).

### 🎯 Swarm Workflow

```
1. /swarm-plan "implement user authentication"
   → Workers explore song song → plan trong docs/plans/
   → Task 200–400 LOC, có acceptance criteria và dependency

2. /swarm-execute docs/plans/plan-<feature>.md
   → Orchestrator tạo task list, chạy tối đa 8 workers
   → worker-builder commit trong worktree riêng, orchestrator merge vào main
   → Quality gates chạy lại sau merge; push chỉ khi bạn đồng ý

3. /swarm-review
   → Review nhiều góc: security, performance, architecture, tests, scope adherence
   → Diff nhỏ, rủi ro thấp → lite review (một góc)
```

Task tracking dùng **task list có sẵn của Claude Code** (trong phiên) + `TASKS.md` / `docs/tracking.md` / GitHub Issues (lâu dài). Framework không còn dùng Beads (`bd`).

### 📊 Swarm Patterns

#### Pattern 1: Parallel Implementation
```
/swarm-plan docs/specs/spec-<feature>.md
→ Task BE, FE, test độc lập
/swarm-execute docs/plans/plan-<feature>.md
→ BE và FE workers chạy song song, orchestrator merge và chạy gates
```

#### Pattern 2: Security Sweep
```
/swarm-review security audit api/
→ worker-reviewer (focus: security) quét từng module song song
→ Findings phân loại Critical/High/Medium/Low kèm CWE
/builder fix the Critical/High findings
```

#### Pattern 3: Parallel Exploration
```
/swarm-research compare HLS vs chunked MP4 streaming for 1 vCPU
→ worker-explorer / worker-research tìm từng khía cạnh
→ Tổng hợp thành report có trích dẫn
```

---

## Session file (session.txt)

`session.txt` ở root repo ghi **một** task đang làm, để session sau (trên máy này hay máy khác) làm tiếp được. Rule đầy đủ: `.claude/rules/session-file.md`. Trạng thái lâu dài vẫn nằm ở `TASKS.md` và `docs/tracking.md`.

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

| Field | Nội dung |
|-------|----------|
| `task` | Tên ngắn của task |
| `status` | `in_progress` \| `blocked` \| `done`; `blocked` thêm dòng `blocker:` (cần gì, từ ai) |
| `progress` | 0–100, ước lượng cho cả task, không phải bước hiện tại |
| `done` | Các subtask đã xong, ngăn bằng `;`, mỗi item `<ID> <việc> <commit>` |
| `doing` | Subtask đang dở (ứng với `[~]` trong `TASKS.md`); `-` nếu không có |
| `next` | Các bước tiếp theo, ngăn bằng `;`, đủ cụ thể để làm mà không cần đọc lại hội thoại |
| `plan` | Đường dẫn plan artifact, hoặc `-` |
| `updated` | Ngày giờ local, `YYYY-MM-DD HH:MM` |

**Khi nào Claude ghi file**: lúc bắt đầu task có từ 3 bước trở lên (hoặc task dễ vượt quá context window), sau mỗi bước hay commit, `blocked` khi đang chờ bạn, và `done` + `progress: 100` khi đã push và verify. Luôn ghi đè toàn bộ file; sửa vặt hay câu hỏi một lần thì không ghi.

**Đồng bộ giữa các máy**: file được commit và push cùng phần việc nó mô tả (cập nhật chỉ trạng thái thì commit riêng `chore(session): …`). Sang máy khác thì `git pull` trước. Không ghi secret, hostname/IP hay đường dẫn key vào file.

**Bạn sẽ thấy gì**
- **Lúc mở session**, `session-start-loader.sh` hiện một bảng có khung: 📌 Task, Status (progress bar + `updated`), ✅ Done và 🔄 Doing (commit hash ở cột riêng), ⛔ Blocked, → Next. Task chưa xong có gợi ý `▶ /task-resume`; task `done` hiện như task gần nhất và không được làm tiếp. Sau compact thì không hiện bảng.
- **Status line** (`.claude/statusline-command.sh`) hiện `task: <tên> <progress>% (<status>)` khi task chưa xong.
- **`/task-resume [chỉ dẫn]`** đọc lại plan, `git status`/`git log` và `TASKS.md`, sửa file nếu repo khác với file, báo cáo 3–5 dòng rồi làm tiếp từ `next`. Claude không tự làm tiếp, và push vẫn cần bạn đồng ý.

---

## Hooks & permissions

Hooks là script bash trong `.claude/hooks/`, đăng ký trong `.claude/settings.json`. Chúng **fail-open**: thiếu `jq` thì bỏ qua. Trên Windows chúng chạy qua Git Bash.

| Hook | Khi nào | Làm gì |
|------|---------|--------|
| `pre-tool-use-validator.sh` | Trước Write/Edit/Bash | Hỏi lại khi nội dung trông giống secret (kể cả heredoc trong Bash); chặn sửa `.env`, `.git/`, `.mcp.json`; hỏi lại khi sửa `.claude/settings.json`, `.claude/rules/*`, `CLAUDE.md`; khoá file 120 s nếu một session khác đang sửa |
| `dangerous-command-guard.sh` | Trước Bash | Cảnh báo lệnh phá huỷ (`terraform destroy`, `kubectl delete`…) |
| `session-start-loader.sh` | Bắt đầu / sau compact | Hiện bảng trạng thái từ `session.txt` lúc mở session và đưa task vào context; nhắc đọc lại task list và plan sau khi context bị nén |
| `stop-validator.sh` | Kết thúc lượt | Nhả lock của session, nhắc file chưa commit / commit chưa push |

**Permission deny** (không allow rule nào ghi đè được): đọc `.env*`, `*.pem`, `~/.ssh`, `~/.aws`, credential; `rm -rf`, `sudo`, `git push --force`, `git reset --hard`; `curl … | bash`.

> Bản `pre-tool-use-validator.sh` của repo có **vá cho đường dẫn Windows**. Bản gốc của framework chặn mọi Write/Edit trên Windows.

---

## Workflows thực tế

### 🎯 Workflow 1: Implement New Feature (Spec-Driven)

```bash
# Step 1: Scope — làm rõ yêu cầu, tạo PRD
/scope add video recommendation feature

# Step 2: Architect — quyết định kỹ thuật, tạo ADR
/architect docs/prd/PRD-video-recommendation.md

# Step 3: Spec — behavior contract chính xác, tạo Spec
/spec docs/prd/PRD-video-recommendation.md docs/adr/NNNN-video-recommendation.md

# Step 4: Plan — chia task từ Spec
/swarm-plan docs/specs/spec-video-recommendation.md

# Step 5: Execute (Swarm)
/swarm-execute docs/plans/plan-video-recommendation.md

# Step 6: Review
/swarm-review the recommendation feature

# Step 7: Land — gates, commit, hỏi push, kiểm CI + smoke prod
/land-the-plane
```

Ceremony theo độ lớn: thay đổi mô tả được trong một câu thì không cần PRD/ADR, chỉ cần plan hoặc làm luôn.

---

### 🐛 Workflow 2: Debug Production Issue

```bash
# Step 1: Investigate — debugging-protocol (rule) luôn áp dụng:
#   ≥ 3 giả thuyết, tìm root cause, đọc log/code trước khi sửa
"prod báo CORS error, kiểm tra xem"

# Step 2: Fix + regression test (testing skill tự load)
/builder fix the hang in VideoImportServiceImpl

# Step 3: Postmortem (postmortem skill tự load)
"viết postmortem cho sự cố virtual-thread deadlock"
```

---

### 🔒 Workflow 3: Security Audit

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

### ⚡ Workflow 4: Performance Optimization

```bash
# Step 1: Profile — đo trước, đoán sau
"phân tích endpoint /videos chậm, dùng EXPLAIN và log thời gian"

# Step 2: Design solution
/architect design caching strategy for the video list

# Step 3: Implement (Two Hats: refactor và optimize ở hai commit riêng)
/builder implement the cache from docs/adr/NNNN-video-list-cache.md

# Step 4: Verify — benchmark before/after
```

---

## Templates có sẵn

### 📄 Artifacts Templates

Nằm trong `templates/artifacts/` (dùng bởi `/scope`, `/spec`, `/swarm-plan`):

| Template | Use case | Lưu vào |
|----------|----------|---------|
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

Skill cũng mang template riêng trong `resources/` (ví dụ `designing-systems`, `planning-artifacts`, `postmortem`). Tên file trong template theo bảng Artifacts trong `CLAUDE.md` (ví dụ `docs/adr/NNNN-<topic>.md`); nếu có chỗ lệch, **bảng đó thắng**.

### 🔧 Claude Mechanisms Templates

Nằm trong `templates/claude_mechanisms/` và `.claude/templates/`:

| Template | Use case |
|----------|----------|
| **skill.template.md** | Tạo skill mới (workflow hoặc library) |
| **agent.template.md** | Tạo worker agent (bắt buộc có `maxTurns`) |
| **rule.template.md** | Tạo rule |
| **hook.template.sh** | Tạo hook (`.claude/templates/`) |

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
/swarm-plan docs/adr/NNNN-video-recommendation.md
```

#### 2. Dùng swarm cho task lớn
```bash
# ❌ Bad: một agent cho cả hệ thống
/builder implement entire authentication system

# ✅ Good: chia nhỏ rồi chạy song song
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

#### 5. Ghi lại quyết định quan trọng
```bash
/architect document decision to use Redis cache
# Sau đó cập nhật docs/tracking.md
```

---

### 🚀 Shortcuts

```bash
# Kết thúc công việc đúng quy trình
/land-the-plane

# Kiểm tra chất lượng toàn repo
/code-check all

# Render tài liệu để trình bày
/presentation docs/prd/PRD-watch-history-v2.md
```

---

### 🎓 Learning Path

#### Beginner
1. Scope ý tưởng: `/scope add a feature`
2. Code với `/builder`, kết thúc bằng `/land-the-plane`

#### Intermediate
3. Full planning flow: `/scope` → `/architect` → `/spec` → `/swarm-plan`
4. Execute với `/swarm-execute`, review với `/swarm-review`
5. Security audit với `/security-auditor`

#### Advanced
6. Tạo skill mới trong `.claude/skills/` (từ `skill.template.md`)
7. Viết hook mới (xem rule `hooks-conventions.md`)
8. `/tailor` để đề xuất chỉnh cấu hình theo stack

---

## 📞 Support & Contribution

### Getting Help

```bash
/help                       # Claude Code help
/doctor                     # Kiểm tra skill/settings có lỗi không
/context                    # Xem skill listing chiếm bao nhiêu context
```

### Customize cho team

1. **Thêm skill**: `.claude/skills/<name>/SKILL.md` (workflow: thêm `disable-model-invocation: true`)
2. **Sửa tech strategy**: `.claude/rules/tech-strategy.md`
3. **Permissions & hooks**: `.claude/settings.json` (máy riêng: `.claude/settings.local.json`, không commit)
4. **Quy ước làm việc cho mọi agent**: `AGENTS.md`

Sửa `CLAUDE.md`, `.claude/settings.json` hoặc `.claude/rules/*` sẽ có hook hỏi xác nhận; đó là hành vi mong muốn.

---

## 🎯 Quick Reference

### Common Commands

| Command | Purpose | Output |
|---------|---------|--------|
| `/scope ...` | Scope ý tưởng thành PRD | `docs/prd/PRD-{slug}.md` |
| `/architect ...` | Quyết định kiến trúc | `docs/adr/NNNN-{slug}.md` |
| `/spec ...` | Feature specification | `docs/specs/spec-{slug}.md` |
| `/swarm-plan ...` | Chia task breakdown | `docs/plans/plan-{slug}.md` |
| `/swarm-execute ...` | Parallel execution | Feature complete |
| `/swarm-review ...` | Multi-perspective review | Review report |
| `/builder ...` | Implement code | Working code |
| `/qa-engineer ...` | Viết tests | Test files |
| `/security-auditor ...` | Security audit | Audit report |
| `/code-check ...` | Code quality | Quality report |
| `/land-the-plane` | Gates → commit → push (hỏi trước) | Commit đã push |
| `/presentation ...` | Render doc thành HTML deck | `<doc-name>.html` |

### Library skills hay dùng

| Khi bạn… | Skill tự load |
|----------|---------------|
| viết test, sửa bug | testing |
| ghi quyết định kiến trúc | writing-adrs, designing-systems |
| feature liên quan bảo mật | threat-modeling |
| nâng version dependency | dependency-upgrade |
| viết PRD / PR-FAQ / roadmap | planning-artifacts |
| viết postmortem | postmortem |

---

## 📊 Project-Specific Notes

### Funny Movies Architecture

- **Language**: Java 24 with Virtual Threads
- **Framework**: Spring Boot 3.x
- **Database**: PostgreSQL + Liquibase
- **Cache**: Guava LRU (Redis khi cần scale ngang)
- **Frontend**: React 19 + Vite (`webapp/`)
- **CI/CD**: GitHub Actions, deploy từ `main` (VM qua SSH + Helm chart)

### Project-Specific Commands

```bash
# Build & Run
cd api && ./startLocal.sh

# Run tests + coverage (gate: api/.coverage-threshold, hiện 90%)
cd api && mvn verify

# Webapp
cd webapp && npm run lint && npx vitest run --root . && npm run build

# Docker build
cd api && docker build -t funny-app .
```

---

## 🔗 Related Documentation

- **CLAUDE.md**: Project guide, Core Principles, bảng Artifacts
- **AGENTS.md**: Landing the Plane, quality gates
- **[Claude Workspace](../claude/index.md)**: Toàn bộ skills, agents, rules, hooks
- **docs/adr/**: Architecture Decision Records
- **docs/tracking.md**: Trạng thái feature

---

**Happy coding with Claude! 🚀**

*Last updated: 2026-10-18 (framework v5)*
