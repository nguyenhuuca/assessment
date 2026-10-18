# Session File (`session.txt`)

`session.txt` at the repo root holds the **one** task currently in flight, so a new session (or `/task-resume`) can pick it up. It is **committed and pushed**, so a session on another machine knows where work stopped after `git pull`. Durable status still goes to `TASKS.md` and `docs/tracking.md`. Whenever the file is non-empty, the SessionStart hook shows the user a status report from it at every startup. Unfinished tasks also get a "/task-resume" hint, and `done` tasks are shown as the last task, so keep the fields short and current: what it shows is exactly what is written here.

## Format

Plain `key: value` lines, one per key, no nesting:

```
task: Continue Watching row (Explore)
status: in_progress
progress: 40
done: FE-1 selector; FE-4 summary invalidation
doing: FE-2 ContinueWatchingRow component
next: FE-2 ContinueWatchingRow component + styles
plan: docs/plans/plan-continue-watching.md
updated: 2026-10-18 21:05
```

- `status`: `in_progress` | `blocked` | `done`
- `progress`: integer 0–100, your honest estimate of the whole task (not the current step)
- `done`: one `;`-item per **finished subtask**, written as `<ID> <what> <commit>`, e.g. `BE-1 position column 6a0a5849`. The startup report shows each item as a `✅` line, at most 8 items and 70 characters each, so keep every item short
- `doing`: subtasks started but not finished (matching `[~]` in `TASKS.md`), same item format as `done`, shown as `🔄` lines. Optional: leave it out or set it to `-` when nothing is half-done. When a subtask finishes, move it from `doing` to `done`
- `next`: short, `;`-separated, concrete enough to act on without the conversation. Only the first item shows in the startup report
- `plan`: artifact path, or `-` when there is none
- `updated`: local date and time as `YYYY-MM-DD HH:MM` (24h, e.g. `2026-10-18 21:05`), never the date alone; get it from `date "+%Y-%m-%d %H:%M"`
- `blocked` adds a `blocker:` line saying what is needed and from whom

## When to Write It

- **Start** of any task with 3+ steps or one that will likely outlive the context window: create or overwrite the file
- **After each step** (task in a plan, commit, verified checkpoint): update `progress`, `done`, `next`, `updated`
- **Blocked or waiting on the user**: `status: blocked` + `blocker:`
- **Finished** (pushed, verified): `status: done`, `progress: 100`

Skip it for one-shot questions and trivial edits. Write it with a single Write call (overwrite), never append. Never put secrets, tokens, hostnames/IPs, key paths or customer data in it: it is pushed to the remote.

## Syncing Across Machines

- Commit `session.txt` together with the work it describes. A status-only update gets its own `chore(session): …` commit. Pushing still needs the user's go-ahead, like every push.
- Before stopping work on a machine, make sure the latest `session.txt` is committed, so the next `git pull` elsewhere brings it along.
- On a new machine, `git pull` first; the SessionStart hook only sees the local copy.
