#!/usr/bin/env bash
# Headless autopilot (ADR-0022): after a job, post its cost and append it to the run report.
# Usage: bash scripts/claude-autopilot-cost.sh <execution_file>
# Env: EVENT (issues|issue_comment), NUM (issue or PR number), JOB (plan|build), RUN_URL, GH_TOKEN.
# The cost comes from the action's result message (total_cost_usd, modelUsage): API list-price equivalent.
# A failure here only warns; it never fails the job after Claude's work is pushed.
set -uo pipefail
exec_file=$1

last='[.[] | select(.type == "result")][-1]'
cost=$(jq -r "$last"' | .total_cost_usd // empty' "$exec_file")
[ -n "$cost" ] || { echo "no result message in $exec_file"; exit 0; }
turns=$(jq -r "$last"' | .num_turns // "?"' "$exec_file")
models=$(jq -r "$last"' | .modelUsage // {} | to_entries
  | map("\(.key) $\((.value.costUSD // 0) * 100 | round / 100)") | join(", ")' "$exec_file")
cost=$(printf '%.2f' "$cost")

if [ "$EVENT" = "issues" ]; then
  target=$(gh pr list --head "autopilot/issue-$NUM" --state all --json number --jq '.[0].number // empty')
else
  target=$NUM
fi
msg="Autopilot $JOB job cost: \$$cost ($turns turns; ${models:-models n/a}), API list-price equivalent. Run: $RUN_URL"
if [ -n "$target" ]; then gh pr comment "$target" --body "$msg"; else gh issue comment "$NUM" --body "$msg"; fi
[ -n "$target" ] || exit 0

# Append the row to the report's "## Cost" table (the last section of report.template.md) on the PR branch.
branch=$(gh pr view "$target" --json headRefName,state --jq 'select(.state == "OPEN") | .headRefName')
case "$branch" in autopilot/*) ;; *) echo "PR #$target is not an open autopilot PR; report not updated"; exit 0 ;; esac
wt="$(mktemp -d)/report"
git fetch -q origin "$branch" main || { echo "::warning::fetch failed; report not updated"; exit 0; }
git worktree add -q --detach "$wt" "origin/$branch" || { echo "::warning::worktree failed; report not updated"; exit 0; }
report=$(git -C "$wt" diff --name-only origin/main...HEAD -- 'docs/reports/autopilot-*.md' | head -1)
if [ -z "$report" ]; then
  echo "::warning::no docs/reports/autopilot-*.md on $branch; cost is only in the PR comment"
  exit 0
fi
[ -z "$(tail -c1 "$wt/$report")" ] || echo >> "$wt/$report"
printf '| %s | %s | $%s | %s | [run](%s) |\n' "$JOB" "$turns" "$cost" "${models:-n/a}" "$RUN_URL" >> "$wt/$report"
git -C "$wt" add "$report"
git -C "$wt" commit -q -m "docs(autopilot): $JOB job cost \$$cost"
git -C "$wt" push -q origin "HEAD:$branch" || echo "::warning::push failed; cost is only in the PR comment"
