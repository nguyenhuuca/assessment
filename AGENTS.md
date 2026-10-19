# Agent Instructions

Funny Movies: a Java 24 / Spring Boot 3 API (`api/`) and a React 19 + Vite webapp (`webapp/`). These instructions apply to any coding agent working in this repo, not just Claude Code. Project detail lives in `CLAUDE.md` and `webapp/CLAUDE.md`.

## Where Things Live

- `.claude/` — skills (user-invoked workflows + knowledge library), rules, hooks, and worker agent definitions (adapted from claude-agentic-framework v5)
- `docs/` — durable planning documents, committed: `docs/adr/`, `docs/prd/`, `docs/specs/`, `docs/plans/`, plus `docs/tracking.md` (feature status)
- `./scratchpad/` — ephemeral working notes and draft content; gitignored, disposable
- `./artifacts/` — design images (Stitch exports, previews), not planning docs

## Task Tracking

1. **Durable record**: `TASKS.md` and `docs/tracking.md` (features with an ADR/PRD/plan); GitHub Issues for follow-ups.
2. **In-flight work**: your tool's native task/todo list, owned by whichever agent is orchestrating — sub-agents receive focused prompts and return results rather than sharing mutable state.
3. **Handoffs**: reference concrete artifacts under `docs/` by file path.

## Landing the Plane (Session Completion)

This protocol has two modes. Check which one applies — it depends on whether the current agent's frontmatter declares `isolation: worktree`.

### Mode A — Isolated workers (`isolation: worktree` in agent frontmatter)

An isolated worker runs in its own throwaway git worktree, not the shared checkout, so parallel workers never race on one git index. The worker does not push; the orchestrator does.

1. **Run quality gates** (if code changed) — see Quality Gates below
2. **Commit** on the assigned worktree branch — atomic, complete, working changes only
3. **Report back to the orchestrator**: the commit SHA and a summary of files changed, tests added/modified, and any follow-up work discovered
4. **Stop there** — do NOT `git push`, do NOT merge, do NOT switch branches. The orchestrator merges the worktree branch, re-runs gates, and cleans up the worktree.

#### Recovery

1. **Worker stops at its `maxTurns` ceiling without a completion report.** Inspect the worktree (`git -C <worktree-path> status`, `git -C <worktree-path> log`), then resume the SAME worker with a focused continuation message rather than respawning one.
2. **Orchestrator session is lost after a worker committed but before merge.** List worktree branches (`git branch --list 'worktree-agent-*'`) and diff each against `main` (`git log <branch> --not main`) to find unlanded commits. Merge or discard deliberately.
3. **`git merge --ff-only` is rejected because `main` advanced.** Rebase the worker branch onto the current `main`, re-run gates, then retry the fast-forward merge.

### Mode B — Non-isolated agents and sessions

`/autopilot` runs this mode as its Land stage; its Gate 2 is the "ask before push" step below.

1. **File follow-ups** — anything left over goes to `TASKS.md` or a GitHub Issue
2. **Run quality gates** (if code changed)
3. **Update status** — `TASKS.md`; `docs/tracking.md` when a feature/plan/ADR status changed
4. **Commit** with explicit pathspecs (never `git add -A`); never commit `.claude/settings.local.json` or `*.stackdump`
5. **Ask the user before pushing.** Every push to `main` deploys through CI, and approval for one push does not carry over to the next. Once approved:
   ```bash
   git pull --rebase
   git push
   git status  # MUST show "up to date with origin"
   ```
6. **Verify** — watch the CI runs for the pushed SHA ("Funny App Build and Deploy", "Deploy Frontend (React Build)", "Publish Docs") and smoke-test prod: once the backend deploy is green, run `./api/mock-test.sh` (read-only API smoke, `api/Mock-Test.http`; exits non-zero on a failed check) and add a request there for any new API
7. **Hand off** — leave clear context and artifact references for the next session

**Rules:**
- Never force-push; never push without the user's go-ahead
- If a push is rejected, `git pull --rebase` and retry once; still rejected → stop and report
- Work goes straight to `main` here; open a PR only when the user asks for one

## Quality Gates

- **API**: `cd api && mvn verify` (tests + LINE coverage ≥ `api/.coverage-threshold`)
- **Webapp**: `cd webapp && npm run lint && npx vitest run --root . && npm run build`
