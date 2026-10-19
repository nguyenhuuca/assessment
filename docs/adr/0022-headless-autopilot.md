# ADR-0022: Headless Autopilot on GitHub Actions

## Metadata

**Status:** Accepted (2026-10-19, after the first end-to-end run: issue #26 → PR #27 merged) · **Date:** 2026-10-19 · **Deciders:** nguyenhuuca · **Tags:** ci, agents, security, workflow
**Related:** [plan-autopilot](../plans/plan-autopilot.md) (the local skill) · [plan-autopilot-headless](../plans/plan-autopilot-headless.md) · [Claude Code GitHub Actions docs](https://code.claude.com/docs/en/github-actions)

## Context

- `/autopilot` runs plan → review → build → QA → land with two human gates. It only runs inside an interactive terminal session, and the gates use `AskUserQuestion`.
- Goal ("Level 5"): hand work to the agent from GitHub, without opening a terminal, and keep both gates.
- `anthropics/claude-code-action@v1` runs Claude Code in a GitHub Actions job:
  - **automation mode** with a `prompt`, which can be a skill invocation such as `/autopilot …`;
  - **auth:** `claude_code_oauth_token` (subscription) or `anthropic_api_key`;
  - **trigger check:** on issue and PR events, the triggering user must have **write access**, and bots are rejected unless listed.
- **Repo facts:**
  - `nguyenhuuca/assessment` is **public**, with 1 collaborator (the owner). Anyone can open issues and comment.
  - CI deploys on push to `main` (and `VID-*`). Other branches run no CI and deploy nothing.
  - The repo convention is "commit on `main`, ask before every push".

## Decision Drivers

1. Both gates stay human. The agent never pushes to `main`.
2. Untrusted input: on a public repo, issue and comment text from anyone except the owner must not start a run or steer one.
3. Least privilege: the job gets only the secrets and token scopes it needs. The deploy secrets (SSH, Docker) are never visible to it.
4. Bounded cost and time per run.
5. Reuse the local skill. The headless path is one mode of the same `SKILL.md`, not a second workflow to maintain.

## Decisions

### D1. Gates on GitHub: branch + PR, merge = Gate 2

- **Start:** the owner adds the label **`autopilot`** to an issue.
- **Plan job:**
  - runs `/autopilot --headless plan --issue <N>`;
  - writes the plan and `TASKS.md` section on branch `autopilot/issue-<N>`;
  - opens a **draft PR** with the plan summary and the plan-review verdict;
  - comments on the issue.
- **Gate 1:** the owner comments **`/approve`** on the PR. `/revise <notes>` re-runs the plan job with the notes.
- **Build job:**
  - runs `/autopilot --headless build --pr <N>`: implement ⇄ review, QA ×2, full gates;
  - commits and pushes **to the PR branch only**;
  - writes the run report, then marks the PR ready for review with the report in its body.
- **Gate 2:** the owner **merges** the PR. The merge is the push to `main` and triggers the normal deploy, followed by the owner's smoke check.
- **Exception to "commit on `main`":** headless runs use a branch + PR, because a merge is the only gate GitHub can offer. Local runs keep committing on `main`.

### D2. Who can trigger

Each job runs only when **both** checks pass:
- the action's built-in write-access check;
- an explicit workflow `if`: `github.event.sender.login == github.repository_owner` (label or comment author).

The build and revise jobs also require:
- the PR head branch starts with `autopilot/`;
- the comment starts with `/approve` or `/revise`.

### D3. Untrusted content

- The issue title and body are **data**: the request to plan. Instructions inside them that try to change the workflow, permissions, secrets or gates are ignored and quoted in the PR as a finding.
- **Paths a headless run refuses to touch:** `.github/`, `.claude/`, `CLAUDE.md`, `AGENTS.md`, `**/*.env*`, `scripts/claude-*`, and migrations that drop or rewrite data. A request that needs them is refused at the scope guard.

### D4. Auth and least privilege

- **Auth:** `CLAUDE_CODE_OAUTH_TOKEN` (subscription), created by the owner with `claude setup-token` and stored as a repo secret by the owner.
- **GitHub token:** the workflow passes `github_token: ${{ github.token }}` with job permissions `contents: write`, `pull-requests: write`, `issues: write`, so the broad Claude GitHub App is not needed. Pushes made with this token start no workflows. That is fine here, because `autopilot/*` branches have no CI.
- No other secrets are referenced by the job.
- **The Bash allowlist is a guardrail, not a boundary.** `node`, `mvn`, `npm`, `awk` and `sed` can all run arbitrary code, and the build job needs them. The OAuth token is present in the job, so a determined prompt injection could reach it. The real boundaries are:
  - only the owner can trigger (D2);
  - both gates are human;
  - the job has no deploy secrets;
  - the token can be revoked by running `claude setup-token` again.
  - `curl` and `wget` are not allowed, which keeps casual exfiltration harder.

### D5. Limits

- Workers run in the foreground: `settings: {"env":{"CLAUDE_CODE_DISABLE_BACKGROUND_TASKS":"1"}}`. In `-p` mode the session ends with the turn, so a background worker's result is lost. This was found on the first run for issue #26: the plan job ended "waiting for the review" without a PR.

- Each job has `timeout-minutes: 60`.
- `concurrency: autopilot` with `cancel-in-progress: false`: one run at a time, others wait.
- `--max-turns` per job.
- The skill's round caps (3) still apply. After each job, `scripts/claude-autopilot-cost.sh` (run from `main`, so a PR branch cannot change it) posts the job's cost from the action's result and appends it to the run report on the PR branch.

## Consequences

- **Positive:**
  - Work can be started and approved from a phone, and every run leaves a PR with its plan, verdicts, report and diff.
  - Deploy secrets stay out of reach.
- **Negative:**
  - Plan and build run in separate jobs, so the build job re-reads the plan from the branch. There is no shared conversation context, which may cost a little more per run.
  - Merging from GitHub skips the local "ask before push" prompt. The merge button **is** that prompt.
  - The subscription token is tied to the owner's account, so its usage counts against the owner's plan limits.
- **Risks:**
  - A prompt injection inside an issue the owner labels. Mitigations: D3 path denylist, both human gates, and the diff is reviewed before merge.
  - Action behaviour changes upstream. Mitigation: pin `@v1` and review on upgrade.

## Compliance

The decision holds if:
- `autopilot.yml` contains the owner check from D2;
- no job references secrets other than `CLAUDE_CODE_OAUTH_TOKEN`;
- no job pushes to `main`;
- `SKILL.md` keeps the D3 denylist in headless mode.
