# 🤖 Hướng dẫn sử dụng Claude Code

> **Tài liệu này** hướng dẫn cách sử dụng Claude Code với project **Funny Movies**. Sau khi clone repo về, đọc file này để biết cách tận dụng AI assistant hiệu quả nhất.

---

## 📚 Mục lục

1. [Giới thiệu](#gioi-thieu)
2. [Các cấp độ sử dụng Claude](#cac-cap-o-su-dung-claude)
3. [Cấu hình có sẵn](#cau-hinh-co-san)
4. [Workflow skills - 17 lệnh](#workflow-skills-17-lenh)
5. [Library skills - tự động load](#library-skills-tu-ong-load)
6. [Swarm - Multi-agent parallel execution](#swarm-multi-agent-parallel-execution)
7. [Autopilot - chạy trọn một task](#autopilot-chay-tron-mot-task)
8. [Đo chất lượng & Evolve - agent tự cải thiện](#o-chat-luong-evolve-agent-tu-cai-thien)
9. [Session file (session.txt)](#session-file-sessiontxt)
10. [Hooks & permissions](#hooks-permissions)
11. [Workflows thực tế](#workflows-thuc-te)
12. [Templates có sẵn](#templates-co-san)
13. [Tips & Tricks](#tips-tricks)

---

## Giới thiệu

Project này dùng **[Claude Agentic Framework](https://github.com/dralgorhythm/claude-agentic-framework) v5**, đã chỉnh cho Funny Movies (xem [Claude Workspace](../claude/index.md)):

- ✅ **17 workflow skills** gọi bằng `/tên` (scope, spec, architect, builder, swarm, autopilot, evolve…)
- ✅ **10 library skills** Claude tự load khi task phù hợp (testing, ADR, threat modeling…)
- ✅ **5 worker agents** cho swarm, mỗi agent có model, `maxTurns` và tools cố định
- ✅ **Rules** nạp vào mọi session: core directives, code quality, security, tech strategy, debugging protocol
- ✅ **4 safety hooks** + permission deny cho secrets
- ✅ **Templates** cho PRD, ADR, spec, plan…

Hai quy ước riêng của repo này cần nhớ:

1. **Tài liệu planning nằm trong `docs/`**: `docs/adr/`, `docs/prd/`, `docs/specs/`, `docs/sequence/` (sequence diagram Mermaid, bắt buộc với mọi feature có PRD), `docs/plans/`, trạng thái ở `docs/tracking.md`.
2. **Commit trên `main`, và Claude luôn hỏi trước mỗi lần `git push`**, vì CI deploy từ `main`.

---

## Các cấp độ sử dụng Claude

Dự án dùng Claude Code theo 6 cấp. Cấp càng cao, Claude tự làm càng nhiều và bạn chạm vào càng ít. Rules, hooks và quality gates ở cấp thấp vẫn áp dụng ở mọi cấp trên.

| Cấp | Cách dùng | Bạn tham gia | Ví dụ thật trong repo |
|-----|-----------|--------------|------------------------|
| 1 | **Hỏi & khám phá:** hỏi về code, Claude đọc và grep trước khi trả lời, không đổi code | Mỗi câu hỏi | "Quản lý cron job dynamic thế nào?" → 3 mức, đề xuất Mức 2 (DB + TaskScheduler + admin UI) |
| 2 | **Làm trực tiếp có guardrails:** CLAUDE.md + `.claude/rules/` luôn load, library skills tự load, hooks chặn secret, `session.txt`, hỏi trước mỗi push | Từng bước, duyệt push | Cron editor modal + tooltip (`df8bdba1`, `9b1d5419`): làm trực tiếp, không qua autopilot |
| 3 | **Workflow skills + swarm:** `/scope` → `/architect` → `/spec` → `/swarm-plan` → `/swarm-execute` → `/swarm-review` → `/land-the-plane`; worker agents chạy song song, model pinned theo agent | Duyệt từng artifact (PRD, ADR, Spec, Plan) | Feature "premium user": autopilot từ chối vì cần PRD → chuyển sang `/scope` |
| 4 | **`/autopilot` local:** plan ⇄ review → Gate 1 → implement ⇄ review → QA ×2 → commit → Gate 2 → CI + smoke; tối đa 3 round mỗi vòng | 3 lần: Start, Gate 1, Gate 2 | Dynamic cron jobs (ADR-0023): BE + FE, CI xanh, smoke 22/22, $3.71 |
| 5 | **Headless trên GitHub** ([ADR-0022](../adr/0022-headless-autopilot.md)): label `autopilot` → draft PR → `/approve` → build → bạn merge | Gắn label, `/approve`, merge | Issue #34 (màu nút quay lại) → PR #35 |
| 6 | **Auto gates** ([ADR-0025](../adr/0025-autopilot-auto-gates.md)): thêm label `autopilot:auto` hoặc `autopilot:auto-merge`, workflow tự duyệt và tự merge sau khi tự kiểm tra | Chỉ gắn label | Issue #36 → PR #37 merged + deployed trong ~5 phút, build job $0.39 |

**Chọn cấp nào:**
- Chưa rõ nên làm gì, hoặc chỉ cần hiểu code → **cấp 1**.
- Sửa nhỏ, cần bạn nhìn từng bước → **cấp 2**.
- Feature vừa/lớn (cần PRD, nhiều ngày) → **cấp 3**.
- Feature nhỏ, fix, refactor có acceptance criteria rõ → **cấp 4**, hoặc **cấp 5** nếu không mở terminal.
- Việc nhỏ, rủi ro thấp, không đụng migration hay config agent → **cấp 6**.

---

## Cấu hình có sẵn

### 📁 Cấu trúc thư mục

```
.claude/
├── skills/
│   ├── <name>/SKILL.md          # 17 workflow skills: gọi bằng /name
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
evals/seeded/                    # Seeded-bug evals cho reviewer (Evolve, ADR-0027)
scripts/agent-metrics.mjs        # Metric chất lượng agent; scripts/evolve.mjs = signals + eval gate của Evolve
AGENTS.md                        # "Landing the Plane": cách kết thúc một phiên làm việc
templates/
├── artifacts/                   # PRD, ADR, spec, plan, design spec, postmortem...
└── claude_mechanisms/           # Agent, command, skill, rule templates
```

> **Từ v5 không còn `.claude/commands/`.** Các lệnh cũ đã thành skill, tên lệnh giữ nguyên.

---

## Workflow skills - 17 lệnh

Phần lớn workflow skill chỉ chạy khi bạn gõ `/tên`. Ngoại lệ là 5 skill mà `/autopilot` nối với nhau (`swarm-plan`, `swarm-review`, `swarm-execute`, `qa-engineer`, `land-the-plane`): chúng đặt `disable-model-invocation: false`, nên Claude cũng có thể tự gọi khi yêu cầu khớp. Xem [Autopilot](#autopilot-chay-tron-mot-task).

| Lệnh | Vai trò | Dùng khi | Output |
|------|---------|----------|--------|
| `/scope <ý tưởng>` | PRD Generator | Ý tưởng còn mơ hồ: hỏi lại cho rõ rồi viết PRD | `docs/prd/PRD-{slug}.md` |
| `/spec <PRD> [ADR]` | Spec Generator | Biến PRD đã duyệt (+ ADR nếu có) thành behavior contract, kèm sequence diagram Mermaid | `docs/specs/spec-{slug}.md` + `docs/sequence/sequence-{slug}.md` |
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
| `/autopilot <yêu cầu>` | Autopilot | Chạy trọn một feature nhỏ, fix hoặc refactor: plan → review → bạn duyệt → code → review → QA ×2 → bạn duyệt push | Code đã deploy + `docs/reports/autopilot-*.md` |
| `/evolve [--since <ngày>]` | Self-Improver | Biến signal từ agent-metrics thành thay đổi rule có chứng minh (local, hoặc chạy hàng tuần trên CI) | Branch `evolve/*`, seeded case trong `evals/seeded/` |
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

### 🤝 Các agent làm việc với nhau thế nào

- **Orchestrator là session chính (Opus).** Chỉ nó được tạo worker (worker không có tool `Agent`), giữ task list, gộp kết quả của worker, chạy gates và hỏi bạn ở các gate.
- **Mỗi worker nhận một prompt tập trung và trả về bản tóm tắt** (file đã sửa, test đã thêm, khối verdict), không trả nguyên file. Các worker không chia sẻ state với nhau.
- **`worker-builder` làm trong git worktree riêng.** Orchestrator merge hoặc cherry-pick commit của nó vào `main`, rồi tự chạy lại gates.
- **Reviewer là agent khác với builder.** Mỗi reviewer kết thúc bằng khối verdict (`approve` / `revise`). Chỉ lỗi blocking mới bắt làm lại, tối đa 3 round.

```
                    Bạn  (Start · Gate 1 · Gate 2)
                              │
               Orchestrator  (session chính, Opus)
     ┌──────────────┬─────────┴──────────┬──────────────────┐
worker-explorer ×3  worker-reviewer     worker-builder ×2    worker-reviewer ×2
(Haiku: tìm         (review plan)       (BE + FE, mỗi cái    (quality + security)
 pattern có sẵn)                         một worktree)
```

**Ví dụ: run dynamic cron jobs** ([report](../reports/autopilot-2026-10-19-dynamic-cron-jobs.md)):

| Bước | Agent | Model | Kết quả | Chi phí |
|------|-------|-------|---------|---------|
| Explore | 3 × `worker-explorer`: pattern Liquibase + entity, pattern admin BE, pattern admin UI webapp | Haiku | Pattern cho plan; orchestrator viết plan + ADR-0023 | $0.03 |
| Plan review | `worker-reviewer` | Sonnet | `approve`, 0 blocking, 7 advisory | $0.16 |
| Gate 1 | bạn | — | duyệt | — |
| Implement | 2 × `worker-builder` song song: BE (DJ-1..3), FE (DJ-4..5) | Sonnet | Commit trong worktree, cherry-pick lên `main` | $0.42 + $0.25 |
| Code review | 2 × `worker-reviewer`: quality, security | Sonnet | `approve`, 0 blocking, 6 advisory | $0.17 + $0.15 |
| QA ×2, gates, commit, CI + smoke | orchestrator | Opus | 84 test pass 2 lần, CI xanh, smoke 22/22 | $2.53 (cả run) |

Tổng **$3.71**. Orchestrator chiếm 68%; 8 worker cộng lại $1.18.

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

## Autopilot - chạy trọn một task

`/autopilot <yêu cầu>` nối các workflow skill thành một chuỗi chạy tự động, từ yêu cầu đến commit đã deploy. Bạn chỉ cần có mặt ở **3 chỗ**: lúc gõ lệnh (Start), lúc duyệt plan (**Gate 1**) và lúc duyệt push (**Gate 2**). Mỗi vòng lặp chạy tối đa 3 round rồi dừng, không bao giờ lặp vô hạn.

```
Plan ⇄ Plan review → [GATE 1: bạn duyệt plan] → Implement ⇄ Code review
     → Local QA (test ×2) ⇄ Fix → Commit → [GATE 2: bạn duyệt push] → CI + smoke
```

| Bước | Skill được gọi | Fallback (khi skill bị đặt lại `disable-model-invocation: true`) |
|------|----------------|------------------------------------------------------------------|
| Plan | `swarm-plan` | `worker-architect` / `worker-builder` đọc `SKILL.md` rồi làm theo |
| Plan review, Code review | `swarm-review` | `worker-reviewer` |
| Implement, Fix | `swarm-execute` | `worker-builder` (chạy trong worktree riêng) |
| Local QA | `qa-engineer` + `scripts/qa-twice.sh` | `worker-builder` (focus testing) |
| Land | `land-the-plane` | Orchestrator làm theo AGENTS.md "Landing the Plane" |

- Mỗi reviewer kết thúc bằng một **khối verdict** (`approve` / `revise` + số lỗi blocking / advisory) theo `.claude/skills/autopilot/resources/verdict.md`. Chỉ lỗi **blocking** mới bắt làm lại; advisory được ghi vào report.
- `scripts/qa-twice.sh` chạy các test liên quan **2 lần**. Pass cả 2 lần là `pass`, fail 1 lần là `flaky` (chỉ ghi lại, không chặn), fail cả 2 lần là `fail` (chuyển sang Fix).

### ✅ Trường hợp dùng được

| Loại việc | Ví dụ thật trong repo |
|-----------|----------------------|
| **Feature nhỏ** (khoảng ≤ 1–3 ngày, mức "Small Feature" của `swarm-plan`), yêu cầu đủ rõ để viết acceptance criteria mà không cần hỏi lại | Suggestion re-review: admin Reopen + user yêu cầu review lại ([report](../reports/autopilot-2026-10-19-suggestion-rereview.md)) |
| **Refactor nhỏ, không đổi behavior** (Two Hats): test cũ phải pass mà không sửa | Gom luật chuyển trạng thái vào `SuggestionTransition` ([report](../reports/autopilot-2026-10-19-suggestion-transitions.md)) |
| **Fix bug đã biết nguyên nhân**: biết sửa gì, cần thêm regression test | — |
| Thay đổi chạm backend và/hoặc webapp, **kiểm tra được bằng test tự động** (JUnit, Vitest) | cả hai run ở trên |
| Có quyết định kỹ thuật nhỏ cần ADR | Vẫn chạy được: bước Plan viết luôn ADR |
| **Đã có plan** (từ `/swarm-plan` hoặc viết tay) với **≤ 6 task chưa làm** | `/autopilot docs/plans/plan-x.md`: bỏ qua bước Plan, vẫn chạy Plan review và Gate 1, chỉ làm các task còn `[ ]` trong `TASKS.md` |

### ⚠️ Trường hợp không nên dùng (dùng flow khác)

| Tình huống | Vì sao | Dùng thay |
|-----------|--------|-----------|
| Ý tưởng còn mơ hồ, chưa rõ phải làm gì | Autopilot không hỏi lại để làm rõ yêu cầu | `/scope` |
| Feature vừa hoặc lớn (≥ 1 tuần, cần PRD hoặc PR-FAQ) | Vượt mức một plan nhỏ; bị **từ chối** ở scope guard | `/scope` → `/spec` → `/swarm-plan` → `/swarm-execute` |
| Quyết định kiến trúc lớn, one-way door | Cần so sánh nhiều phương án, cần người duyệt kỹ | `/architect` |
| Sự cố prod chưa rõ root cause | Autopilot cần biết sửa gì; debug cần ≥ 3 giả thuyết và đọc log | Workflow 2 (Debug Production Issue) |
| Câu hỏi, giải thích, sửa typo hoặc 1–2 dòng | Mỗi run tốn plan + 2 review + QA ×2; làm trực tiếp rẻ hơn nhiều | Nhờ Claude làm trực tiếp |
| Ghi dữ liệu prod, thao tác hạ tầng (VM, secret, chạy SQL tay) | Autopilot **không bao giờ** ghi prod; smoke test chỉ đọc | Làm tay, hoặc nhờ Claude soạn lệnh để bạn chạy |
| UI mới cần duyệt thiết kế hoặc visual | Không có gate Lighthouse/visual tự động (chỉ được ghi là manual check) | `/ui-ux-designer` trước, sau đó mới autopilot |
| Tối ưu hiệu năng | Không có bước benchmark trước/sau | Workflow 4 (Performance) |
| Nhiều việc không liên quan gộp trong một yêu cầu | Một run = một plan = một mục tiêu; gộp lại sẽ làm review loãng | Tách thành nhiều run |

### 🛑 Khi autopilot từ chối hoặc dừng giữa chừng

| Tình huống | Autopilot làm gì | Bạn làm gì |
|-----------|------------------|-----------|
| **Scope guard**: yêu cầu cần PRD/PR-FAQ | **Từ chối** ngay, trước khi plan, báo một dòng và chỉ sang `/scope` | Chạy `/scope`, hoặc thu nhỏ yêu cầu rồi chạy lại |
| **Plan mode**: plan có > 6 task chưa làm, có task đang `[~]`, hoặc plan đã Done | **Từ chối**, chỉ sang `/swarm-execute` | Dùng `/swarm-execute`, hoặc tách plan nhỏ hơn |
| **Plan mode**: plan không còn task nào chưa làm | Báo và dừng | — |
| Plan review vẫn `revise` sau 3 round | Dừng, `session.txt` chuyển `status: blocked`, liệt kê các lỗi blocking còn mở | Sửa yêu cầu hoặc tự quyết các điểm đang vướng |
| Code review vẫn `revise` sau 3 round | Dừng `blocked`; code nằm trên worktree branch, chưa vào `main` | Xem các finding, quyết định sửa tay hay bỏ |
| QA fail cả 2 lần, sau 3 round Fix vẫn fail | Dừng `blocked`, kèm danh sách `FAIL` và đường dẫn log | Debug theo Workflow 2 |
| `qa-twice.sh` exit 2 (compile error, không có report) | Tính là fail, đưa đường dẫn log vào bước Fix | — |
| Test **flaky** (fail 1 trong 2 lần) | **Không chặn**, ghi vào report | Sửa test flaky ở một task sau (`qa-engineer`: no flaky tests) |
| Reviewer không trả khối verdict, hỏi lại vẫn thiếu | Dừng `blocked` ("review returned no verdict") | Chạy lại run |
| Full gate đỏ (`mvn verify`, coverage < `api/.coverage-threshold`, vitest, lint) | Không commit | Như QA fail |
| Bạn chọn **Revise with notes** ở Gate 1 | Plan lại theo ghi chú của bạn (cycle mới, đếm round lại từ đầu) | — |
| Bạn chọn **Stop** ở Gate 1 | Dừng `blocked` | — |
| Bạn chọn **Don't push yet** ở Gate 2 | Commit nằm ở local, không push | Push sau bằng `/land-the-plane` hoặc nhờ Claude |
| Rebase conflict khi push | Dừng và báo, không bao giờ force-push | Xử lý conflict |

Run bị dừng có thể làm tiếp bằng `/task-resume`, dựa vào `session.txt` và ledger trong `scratchpad/autopilot/`.

### 🔒 Những điều autopilot không bao giờ làm

- Tự bỏ qua Gate 1 hoặc Gate 2. Không có `--yes`; duyệt gate này không thay cho gate kia hay cho run sau. Chỉ flag bạn gõ trong lệnh mới bỏ qua được gate (`--auto`, `--auto-push`, xem Cách gọi).
- Push trước Gate 2, force-push, hoặc mở PR trong lúc đang chạy.
- Ghi dữ liệu prod. `api/Mock-Test.http` chỉ chạy GET và login.
- Sửa test cũ khi đang refactor để test pass.

### 📄 Output của một run

| File | Nội dung |
|------|----------|
| `docs/plans/plan-<slug>.md` | Plan đã duyệt ở Gate 1. Report **không** được ghi vào plan |
| `TASKS.md` | Một section cho feature, task đánh `[x]` kèm commit hash |
| `docs/reports/autopilot-<YYYY-MM-DD>-<slug>.md` | Report, cùng các section khi chạy local hay headless (`.claude/skills/autopilot/resources/report.template.md`): kết quả từng bước, bảng ledger, chi phí theo agent (local) hoặc theo job (headless, workflow tự thêm), CI + smoke (headless: sau khi merge), manual check, advisory còn mở |
| `docs/metrics/agent-runs/<run id>.json` | Metric của run cho `agent-metrics.mjs` (round, verdict, LOC, kết quả QA, chi phí); mọi commit của run có trailer `Autopilot-Run: <run id>` |
| `scratchpad/autopilot/<timestamp>-<slug>.md` | Ledger trong lúc chạy (gitignored) |
| `session.txt` | Bước đang chạy, progress, và `blocker` khi dừng |

**Lite mode** (task dưới 100 dòng code, không tính test, và không cần ADR): **không** tạo `docs/plans/plan-*.md` và `docs/reports/autopilot-*.md`. Plan và report in ra console (local) hoặc ghi vào mô tả PR (headless). `TASKS.md` và `session.txt` vẫn cập nhật. Nếu trong lúc làm diff vượt khoảng 100 dòng hoặc cần ADR, autopilot chuyển sang chế độ đầy đủ và viết file plan.

### 💰 Chi phí thực tế

| Run | Thời gian | Orchestrator (Opus) | Worker (Sonnet/Haiku) | Tổng |
|-----|-----------|---------------------|-----------------------|------|
| Suggestion re-review (BE + FE, 1 lần replan) | ~20 phút | $2.51 | $1.25 | **$3.76** |
| Transitions refactor (chỉ BE) | ~11 phút | $1.78 | $0.47 | **$2.24** |

Số liệu chính xác, tính bằng `node scripts/claude-cost.mjs --from <bắt đầu> --to <kết thúc>`. Script đọc transcript của session và tính theo giá API list price (với gói subscription thì đây là chi phí quy đổi, không phải hóa đơn). Report của mỗi run có bảng chi phí chi tiết theo từng agent.

Orchestrator (Opus) chiếm 67–79% chi phí mỗi run, chủ yếu do mỗi request đọc lại context ngày càng lớn của session. Muốn rẻ hơn thì: viết yêu cầu rõ (tránh replan), giữ mỗi run một việc, và chỉ bật song song FE/BE khi cả hai cùng đổi.

### ☁️ Chạy headless trên GitHub (không cần terminal)

Ngoài cách chạy local, autopilot còn chạy được trên GitHub Actions ([ADR-0022](../adr/0022-headless-autopilot.md), workflow `.github/workflows/autopilot.yml`). Hai cổng duyệt chuyển lên GitHub:

| Bước | Bạn làm | Autopilot làm |
|------|---------|---------------|
| Start | Gắn label **`autopilot`** cho một issue (issue do chính bạn tạo) | Viết plan + review trên branch `autopilot/issue-<N>`, mở **draft PR**, comment link PR lên issue |
| Gate 1 | Comment **`/approve`** trên PR, hoặc **`/revise <ghi chú>`** để plan lại | Implement ⇄ review → QA ×2 → full gates → push lên branch của PR, ghi report vào PR, chuyển PR sang ready |
| Gate 2 | Xem diff rồi **merge** PR. Merge chính là push lên `main`, CI sẽ deploy | Không bao giờ tự push `main` hay tự merge |
| Sau deploy | Chạy `./api/mock-test.sh` | — |

**Bỏ qua gate bằng label** ([ADR-0025](../adr/0025-autopilot-auto-gates.md)). Gắn thêm một label cùng với `autopilot` (thứ tự không quan trọng):

| Label thêm | Gate 1 | Gate 2 |
|------------|--------|--------|
| (không có) | bạn `/approve` | bạn merge |
| `autopilot:auto-merge` | bạn `/approve` | tự merge |
| `autopilot:auto` | tự duyệt | tự merge |

- **Gate 1 tự duyệt:** plan job chỉ ghi dấu `<!-- autopilot:plan-review=approve -->` vào mô tả PR khi plan review ra `approve`. Workflow thấy dấu đó thì tự chạy build job (`workflow_dispatch`). `/revise` luôn chờ bạn.
- **Gate 2 tự merge:** `scripts/claude-autopilot-merge.sh` (luôn chạy bản trên `main`) chỉ merge khi đủ hết các điều kiện:
  - PR đã ready, tức là code review ra `approve`;
  - không đụng `.github/`, `.claude/`, `CLAUDE.md`, `AGENTS.md`, `scripts/claude-*`, migration Liquibase hay `.env`;
  - ≤ 400 dòng thay đổi ngoài test và docs;
  - script **tự chạy lại** `mvn verify` / test + lint + build webapp.
  - Sau đó merge squash bằng secret `AUTOPILOT_MERGE_TOKEN`, để CI deploy chạy. Thiếu điều kiện nào thì script comment lý do và để PR cho bạn.
- **Ví dụ thật:** issue #36 (dịch nhãn ON/OFF) → PR #37 merged + frontend deployed trong khoảng 5 phút, build job $0.39, không ai bấm gì.
- **Rủi ro:** code lên prod mà không ai đọc. Smoke test sau deploy vẫn chạy tay. Rollback = `git revert` commit squash.

**Cài đặt một lần (bạn tự làm, Claude không đụng vào token):**

```bash
claude setup-token                                  # tạo token từ gói subscription
gh secret set CLAUDE_CODE_OAUTH_TOKEN               # dán token vào khi được hỏi
gh label create autopilot --color 5319e7 --description "Run /autopilot headless"
# Chỉ cần nếu dùng auto gate: fine-grained PAT chỉ cho repo này, Contents + Pull requests read/write
gh secret set AUTOPILOT_MERGE_TOKEN
```

**Giới hạn và an toàn:**
- Chỉ **owner của repo** kích hoạt được, kể cả label lẫn comment. Repo đang public, nên comment của người khác bị bỏ qua.
- Nội dung issue chỉ là dữ liệu. Chỉ dẫn nằm trong issue mà định thay đổi workflow, quyền hay gate sẽ bị bỏ qua và được ghi lại trong PR.
- Headless **từ chối** sửa `.github/`, `.claude/`, `CLAUDE.md`, `AGENTS.md`, file `.env` và migration xóa dữ liệu. Những việc đó phải làm ở local.
- Mỗi job tối đa 60 phút, mỗi lúc chỉ 1 run. Các run xếp hàng theo thứ tự đến trước chạy trước, nên gắn label cho nhiều issue cùng lúc không mất run nào (chờ tối đa khoảng 5,5 giờ). Bước Claude chỉ thấy secret `CLAUDE_CODE_OAUTH_TOKEN`; `AUTOPILOT_MERGE_TOKEN` chỉ bước auto-merge thấy; các secret deploy (SSH, Docker) không bao giờ lộ ra job.

### ▶️ Cách gọi

```bash
/autopilot ben tab Đề xuất cho admin move về chờ duyệt rồi review lại
/autopilot refactor: gom luật chuyển trạng thái suggestion vào một chỗ, không đổi behavior
/autopilot fix: nút Reopen chưa disable khi đang gửi request
/autopilot docs/plans/plan-<feature>.md               # chạy trên plan có sẵn (plan mode)
/autopilot --auto-push fix: <...>                     # Gate 2 tự push khi check pass
/autopilot --auto fix: <...>                          # tự qua cả 2 gate (Gate 1 cần plan review approve)
```

- `autopilot` đặt `disable-model-invocation: true`: Claude chỉ chạy khi bạn **gõ `/autopilot`**. Nếu bạn yêu cầu bằng lời trong chat ("chạy autopilot cho việc X"), Claude đọc `SKILL.md` và làm theo từng bước, vẫn đủ hai gate.
- 5 skill được nối trong chuỗi (`swarm-plan`, `swarm-review`, `swarm-execute`, `qa-engineer`, `land-the-plane`) đặt `disable-model-invocation: false` để orchestrator gọi được. Đổi lại `true` lúc nào cũng được; khi đó bước đó tự chuyển sang fallback và report ghi rõ bước nào đi đường nào.
- **Auto gates ([ADR-0028](../adr/0028-local-autopilot-auto-gates.md)):** `--auto-push` chỉ tự push khi `scripts/claude-autopilot-autopush-check.sh` pass: không đụng `.github/`, `.claude/`, `CLAUDE.md`, `AGENTS.md`, `scripts/claude-*`, migration hay `.env`, và ≤ 400 dòng thay đổi ngoài test và docs. Thiếu điều kiện thì vẫn hỏi bạn như thường. Flag chỉ có hiệu lực cho run đó; `/task-resume` sẽ hỏi lại ở các gate còn lại.
- Chi tiết thiết kế: [plan-autopilot](../plans/plan-autopilot.md).

---

## Đo chất lượng & Evolve - agent tự cải thiện

Hai lớp nằm trên autopilot: **đo** chất lượng việc agent làm theo thời gian, rồi để agent **tự sửa rule của chính nó** dựa trên số liệu ([ADR-0027](../adr/0027-agent-self-improvement-loop.md)).

### 📊 Đo: `scripts/agent-metrics.mjs`

```bash
node scripts/agent-metrics.mjs --since 2026-10-01 --until 2026-10-31 --out docs/reports/agent-metrics-2026-10.md
node scripts/agent-metrics.mjs --json            # signals cho /evolve
```

| Metric | Cách đo |
|--------|---------|
| **Escaped defect rate** (quan trọng nhất) | Một commit `fix:` sau đó, không thuộc run, trong vòng 7 ngày, sửa file code mà run đã đụng |
| Revert | `This reverts commit <sha>` trỏ vào commit của agent |
| Số round review, run blocked, flaky test, review bị bỏ qua, chi phí | `docs/metrics/agent-runs/<run id>.json`, `/autopilot` ghi ở cuối mỗi run |
| Số `/revise`, PR đóng không merge, thời gian mở → merge | GitHub (PR headless) |
| Tỉ lệ CI fail (mọi push và commit của agent) | GitHub Actions runs |

- **Cách nhận ra run:** mọi commit của autopilot có trailer `Autopilot-Run: <YYYY-MM-DD>-<slug>`; run cũ hơn được nhận qua hash trong report hoặc tên PR headless.
- **Baseline tháng 10** ([report](../reports/agent-metrics-2026-10.md)): 16 run, escaped defect rate **6%** (1/16: run dynamic-cron-jobs, sửa bởi `62526c97`), 0 revert, PR headless mở → merge trung vị 7 phút.

### 🧬 Tiến hoá: `/evolve`

```
Observe (signals) → Diagnose (Five Whys → stage lẽ ra phải bắt) → Propose (thêm một rule + một seeded case)
  → Prove (eval gate) → Ship (bạn chọn: merge, mở PR, giữ branch hoặc bỏ)
```

- **Signals:** `defect:<sha>`, `bug:#<N>` (bug report từ app mà bản sửa phải đổi các dòng do một run của agent viết, tìm bằng `git blame`), `revert:<sha>`, `blocked:<run>`, `flaky:<run>`, `review-skipped:<run>`, `revise:pr-<N>`. Signal nào đã có PR hoặc issue `evolve` (kể cả cái bạn đã đóng) hoặc đã có seeded case thì không đề xuất lại. Tối đa 3 signal mỗi run.
- **Seeded-bug evals** (`evals/seeded/<id>/case.json`): mỗi bug lọt thật thành một regression test **cho agent**: chạy reviewer trên đúng diff có bug và chấm cố định (có finding blocking ở đúng file, đúng cách mô tả bug).
- **Eval gate** (`scripts/evolve.mjs gate`), phải đủ cả 4:
  1. rule hiện tại trên `main` **bỏ sót** case (ít nhất 2/3 lần);
  2. rule mới **bắt được** ít nhất 2/3 lần;
  3. mọi case cũ vẫn bắt được;
  4. chi phí reviewer tăng tối đa 25%.
- **Agent được sửa gì:** chỉ được thêm dòng vào `.claude/skills/**`, `.claude/rules/*.md`, phần prompt của `.claude/agents/*.md`, và thêm case mới. Không bao giờ đụng `scripts/`, `.github/`, hooks, settings, case cũ, threshold hay code app. Frontmatter bị khoá. Do code kiểm tra (`check-paths`), không phải do prompt.
- **Cách chạy:** gõ `/evolve`. Nó dừng ở bước Ship và hỏi bạn: merge vào `main`, push và mở PR, giữ branch, hay bỏ.
- **Chạy hàng tuần trên CI:** `.github/workflows/agent-evolve.yml` chạy 9:00 thứ Hai (hoặc Actions → Agent Evolve → Run workflow). Agent ghi bản sao file vào `evolve-out/`, không sửa `.claude/`. Ship job dựng branch `evolve/*` bằng `scripts/evolve.mjs apply`, chạy cùng gate rồi mở PR có label `evolve`; bạn là người merge ([ADR-0029](../adr/0029-headless-evolve-staging.md)).
- **Dọn rule (prune):** mỗi rule do Evolve thêm kết thúc bằng `<!-- evolve:<case id> -->`; run sau có thể đề xuất gỡ một rule, chỉ khi case của nó vẫn bắt được khi không có rule đó.
- **Compress** (ADR-0027 D6): mỗi tháng một lần (`compress:<YYYY-MM>`), Evolve được **cắt bớt** nội dung: trùng lặp, tham chiếu chết, nội dung mâu thuẫn với source of truth, số liệu lỗi thời, trong `.claude/` và `CLAUDE.md`.
  - `node scripts/evolve.mjs lint-rules` đưa gợi ý (dòng trùng, path chết, kích thước context luôn load, hiện khoảng 37 KB).
  - Không bao giờ cắt: `AGENTS.md`, `security.md`, skill evolve, frontmatter, và mọi dòng mang luật an toàn (push, gate, merge, deny, secret, prod, smoke, Core Principles, "never …").
  - Gate: nhỏ hơn (context luôn load -5% hoặc file bị sửa -1 KiB), không thêm dòng trùng hay ref chết, mọi seeded case vẫn bắt được. Bạn đọc từng dòng bị xoá trước khi merge.
  - Compress đầu tiên (`evolve/compress-2026-10`): phần cost trong `CLAUDE.md`, ba mục trùng nhau → một, -1.1 KB, gate PASS.
  - **Cách dùng:**

    ```bash
    # 1. Xem có gì cắt được (chỉ là gợi ý; vài "dead ref" thật ra là ví dụ trong text)
    node scripts/evolve.mjs lint-rules

    # 2. Chạy: /evolve   (mỗi tháng có signal compress:<YYYY-MM> trong danh sách; dừng trước khi push)

    # 3. Tự cắt bằng tay trên branch? Kiểm tra bằng đúng gate mà workflow dùng
    git switch -c evolve/compress-<YYYY-MM>
    #    ... sửa CLAUDE.md / .claude/ rồi commit ...
    node scripts/evolve.mjs check-paths --base main --head evolve/compress-<YYYY-MM> --kind compress
    node scripts/evolve.mjs gate --base main --head evolve/compress-<YYYY-MM> --kind compress   # ~$0.15 mỗi seeded case
    ```

    - **Review:** đọc từng dòng bị xoá (`git diff main...<branch>`). Merge nếu mỗi chỗ cắt đúng là trùng lặp, ref chết hoặc lỗi thời; ngược lại thì bỏ.
    - **Bỏ qua một tháng:** tạo issue label `evolve` có body chứa `<!-- evolve:signal=compress:<YYYY-MM> -->`.

**Lần tiến hoá đầu tiên** (`fe36685d`): từ bug `62526c97` (job nuốt exception nên cron job lỗi vẫn hiện SUCCESS), reviewer có thêm rule *"kiểm tra code mới giả định gì về code cũ không đổi; giả định sai là blocking"*. Case `2026-10-19-swallowed-job-exceptions`: rule cũ bắt **0/3**, rule mới **3/3**, khoảng $0.15 mỗi lần chạy eval.

| Chi phí | |
|---------|---|
| `/evolve` không có signal mới | gần như 0 (dừng sau bước metrics) |
| `/evolve` có signal | ~$1–3 cho agent + ~$0.15 mỗi lần eval (6 lần mỗi đề xuất + 1 lần mỗi case cũ) |

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

# Step 3: Spec — behavior contract chính xác, tạo Spec + sequence diagram Mermaid (docs/sequence/)
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
| **sequence.template.md** | Sequence diagram Mermaid (bắt buộc khi có PRD) | `docs/sequence/sequence-{slug}.md` |
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
| `/autopilot ...` | Chạy trọn một task nhỏ, 2 cổng duyệt | Code đã deploy + `docs/reports/autopilot-*.md` |
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
