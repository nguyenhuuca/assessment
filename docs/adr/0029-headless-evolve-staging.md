# ADR-0029: Headless Evolve With Staged Proposals

## Metadata

**Status:** Accepted (2026-10-22) · **Date:** 2026-10-22 · **Deciders:** nguyenhuuca · **Tags:** agents, ci, security
**Related:** reverses the "local only" part of [ADR-0027](0027-agent-self-improvement-loop.md) D4; the allowlist (D2), the eval gate (D3), pruning (D5) and compress (D6) stay as they are

## Context

- The first weekly `agent-evolve.yml` (2026-10-21) wrote nothing. Headless Claude Code treats `.claude/` files as sensitive and asks permission on every edit, and CI cannot answer.
- The same run hit `Refusing to create empty bundle` while packaging, then still filed a "no proposal" issue for `compress:2026-10` (#50, closed by the owner).
- The owner wants the loop back in CI on a weekly schedule, with the same checks as a local run.

## Decision

### D1. The agent stages file copies; it never touches `.claude/` or git

In `--headless` mode the agent writes the **full new content** of every file it would change under `evolve-out/<slug>/tree/`:

| Repo path | Staged path |
|-----------|-------------|
| `.claude/agents/worker-reviewer.md` | `evolve-out/<slug>/tree/dot-claude/agents/worker-reviewer.md.staged` |
| `CLAUDE.md` | `evolve-out/<slug>/tree/CLAUDE.md.staged` |
| `evals/seeded/<id>/case.json` | `evolve-out/<slug>/tree/evals/seeded/<id>/case.json.staged` |

- No staged path has a `.claude` segment or a file name Claude Code treats specially, so `Write` needs no permission.
- A file cannot be deleted this way. Rule files are never deleted (ADR-0027 D2), and compress removes lines inside files.
- The agent holds a read-only token, as before. Its tools: `Read`, `Glob`, `Grep`, read-only `git diff/show/log/rev-parse/ls-files`, and `Write`/`Edit` scoped to `evolve-out/**`. It cannot write `.git/` or `.gitattributes` (a planted git helper would run under the allowed git commands), cannot read `/proc` or `/sys`, has no shell readers (`cat`, `grep`, `head`, `tail`) and no web tools. Git runs with `GIT_CONFIG_NOSYSTEM=1` and `GIT_CONFIG_GLOBAL=/dev/null`.

### D2. A manifest row for every signal

`evolve-out/manifest.json` is an array with one row per signal:
- a proposal: `{signal, slug, case, kind, body, message}`, where `kind` is `rule`, `case-only`, `not-eval-proven` or `compress`;
- no proposal: `{signal, kind: "none", body}`, where `body` explains why.

The ship job files a "no proposal" issue, which closes the signal, **only** for a `none` row. A signal with no row, or a row without a `tree/` (agent crash, turn limit, broken artifact), is left for next week's run. A row whose staged files are invalid becomes a "rejected (staging)" issue and closes the signal.

### D3. The ship job builds the branch; the agent's files are data

`node scripts/evolve.mjs apply --dir <proposal>/evolve-out/<slug> --base <main sha> --branch evolve/<slug> --message <file>`:
- accepts only regular files ≤ 64 KiB whose decoded path matches the Evolve allowlist or a new case path (ADR-0027 D2), and rejects `..`, symlinks and an empty tree;
- writes the commit with git plumbing (temporary index, `commit-tree`, `update-ref`), so the ship job's working tree, and the `evolve.mjs` it runs, stay on `main`.

Then `check-paths` and `gate` run unchanged. A pass becomes a PR labelled `evolve` (draft for `compress` and `not-eval-proven`). A fail becomes an issue. Nothing is merged: the owner merges.

### D4. Schedule and cost

- The workflow runs every Monday at 09:00 Asia/Ho_Chi_Minh, or on `workflow_dispatch` (owner only, optional `since`).
- It handles at most 3 signals per run (ADR-0027 D4 budget). The agent step costs about $1–5 a week (approximate). Weeks with no new signal cost $0, because the agent does not start.

## Consequences

- The local `/evolve` flow is unchanged. Headless is a second way in, with the same gate.
- The ship job still pushes `evolve/*` branches with a write token. The PR is the owner's Gate 2.
- Everything published (issue and PR bodies, gate tables) passes a token-shape redaction (`sk-ant-`, `gh?_`, `github_pat_`, JWT) first.
- PRs opened with `GITHUB_TOKEN` do not trigger other workflows, and the repo setting "Allow GitHub Actions to create and approve pull requests" must be on (it was for the 2026-10-21 version).
- **Not yet proven:** that headless `Write` to `evolve-out/` raises no permission prompt. The first `workflow_dispatch` run checks it; if it fails, disable the workflow again.

## Changelog

- 2026-10-22: accepted.
