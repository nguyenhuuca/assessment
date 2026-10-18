# Session File (`session.txt`)

`session.txt` at the repo root holds the **one** task currently in flight, so a new session (or `/task-resume`) can pick it up. It is gitignored and machine-local; durable status still goes to `TASKS.md` and `docs/tracking.md`. While `status` is not `done`, the SessionStart hook shows the user a status report from this file plus a "/task-resume" hint at every startup, so keep the fields short and current: what it shows is exactly what is written here.

## Format

Plain `key: value` lines, one per key, no nesting:

```
task: Continue Watching row (Explore)
status: in_progress
progress: 40
done: FE-1 selector; FE-4 summary invalidation
next: FE-2 ContinueWatchingRow component + styles
plan: docs/plans/plan-continue-watching.md
updated: 2026-10-18 21:05
```

- `status`: `in_progress` | `blocked` | `done`
- `progress`: integer 0–100, your honest estimate of the whole task (not the current step)
- `done` / `next`: short, `;`-separated; `next` must be concrete enough to act on without the conversation
- `plan`: artifact path, or `-` when there is none
- `blocked` adds a `blocker:` line saying what is needed and from whom

## When to Write It

- **Start** of any task with 3+ steps or one that will likely outlive the context window: create or overwrite the file
- **After each step** (task in a plan, commit, verified checkpoint): update `progress`, `done`, `next`, `updated`
- **Blocked or waiting on the user**: `status: blocked` + `blocker:`
- **Finished** (pushed, verified): `status: done`, `progress: 100`

Skip it for one-shot questions and trivial edits. Write it with a single Write call (overwrite), never append. Never put secrets, tokens or customer data in it.
