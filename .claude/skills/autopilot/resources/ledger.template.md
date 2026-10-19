# Autopilot run: <slug>

- **Request:** <$ARGUMENTS, one line>
- **Plan:** docs/plans/plan-<slug>.md
- **Started:** <YYYY-MM-DD HH:MM>

## Runs

| # | cycle | stage | round | path | result | blocking | advisory | turns | min | tokens | ≈$ |
|---|-------|-------|-------|------|--------|----------|----------|-------|-----|--------|----|
| 1 | 1 | plan | 1 | skill | plan written | — | — | 0 | 0 | 0 | — |

Column rules:
- `path`: `skill` (Skill tool) or `fallback` (worker read the SKILL.md).
- `result`:
  - review stages: `approve` or `revise` from `verdict.md`;
  - QA: `pass` / `flaky n` / `fail n`;
  - gates: `approved` / `revise` / `stopped` / `push` / `no push`.
- `turns`, `tokens`, `min`: added up from the Agent results of that stage. Write `—` when the stage spawned no worker.
- `≈$`: leave `—` while running. The exact per-agent cost comes at the end from `node scripts/claude-cost.mjs --from <start> --to <end> --md` and goes into the report's `## Cost`. Write `—` when the price is unknown, and never guess one. The figures are API list-price equivalent, not a bill.

## Notes

- Flaky tests: <test — run that failed>
- Manual checks for the owner (not automatable): <criterion>
- Open advisories: <[A] lines>
- Parser notes: <e.g. "revise without blockers" at run 4>

## Total

<n> runs · <min> min · <tokens> tokens · ≈$<x>
