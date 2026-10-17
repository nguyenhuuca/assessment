# Claude Workspace

This section documents the Claude Code workspace configuration used in this project: skills, agents, rules, hooks, and templates. Every page under it is a verbatim copy of the file in `.claude/` (or `templates/`), so the docs match what Claude actually loads.

## Attribution

The majority of content in this section is derived from the **Claude Agentic Framework** by [@dralgorhythm](https://github.com/dralgorhythm):

> **Source:** [https://github.com/dralgorhythm/claude-agentic-framework](https://github.com/dralgorhythm/claude-agentic-framework)

Adopted at **v5.0.0** (2026-10-18), then adapted to this project (Funny Movies — Java 24 + Spring Boot + React).

## Layout

| Path | What it is | Loaded |
|------|------------|--------|
| `.claude/skills/<name>/SKILL.md` | 14 user-invoked **workflow skills** (`/scope`, `/swarm-plan`, `/land-the-plane`, …) | Only when you type `/name` (`disable-model-invocation: true`) |
| `.claude/skills/<category>/<name>/SKILL.md` | 10 **library skills** (testing, ADRs, threat modeling, presentation, …) | Claude pulls one in when its description matches the task |
| `.claude/agents/` | 5 swarm workers (explorer, builder, reviewer, research, architect) with pinned model, `maxTurns` and least-privilege tools | When an orchestrator dispatches them |
| `.claude/rules/` | Core directives, code quality, security, tech strategy, debugging protocol, agent constraints | Every session (`hooks-conventions` only when editing hooks/scripts) |
| `.claude/hooks/` + `settings.json` | 4 safety hooks and the permission allow/deny lists | On the matching tool events |
| `AGENTS.md` | "Landing the Plane": how a session or worker finishes work | Referenced by rules and skills |
| `templates/` | Project templates for PRD, ADR, spec, plan, … (used by `/scope`, `/spec`) | On demand |

## What was adapted for this project

- **Planning docs live in `docs/`** (`docs/adr/NNNN-*.md`, `docs/prd/PRD-*.md`, `docs/specs/spec-*.md`, `docs/plans/plan-*.md`, status in `docs/tracking.md`) instead of the framework's `./artifacts/`. `./artifacts/` here holds design images.
- **Work is committed on `main`, and every push is confirmed with the user first**, because CI deploys from `main`. The framework's trunk-based PR rule, `pre-push-main-blocker` and `branch-pr-discipline` hooks, and push-to-main deny rules were not adopted.
- **Project-only skills** kept: `/scope` (idea → PRD), `/spec` (PRD + ADR → spec), `/presentation` (doc → HTML deck).
- **`pre-tool-use-validator.sh` is patched for Windows** paths (`D:\…`); the unpatched hook denied every Write/Edit.
- The framework's commit/task **quality-gate hooks are not installed**: they cannot detect the Maven `api/` and npm `webapp/` builds yet. Run the gates listed in `AGENTS.md` instead.
- `rules/security.md` tags each checklist line with how it is really enforced here (the Trivy CI job is currently disabled).
