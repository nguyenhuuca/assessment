#!/usr/bin/env bash
# Local autopilot (ADR-0028 D3): may Gate 2 push without asking? Checks the run's commits (origin/main..HEAD).
# Usage: bash scripts/claude-autopilot-autopush-check.sh
# Exit 0 = auto-push allowed; exit 1 = ask the owner, the reason is on stdout.
# The two limits below are the same as in scripts/claude-autopilot-merge.sh: keep them in sync.
set -uo pipefail
max_loc=${AUTOPILOT_MAX_LOC:-400}
deny() { echo "auto-push refused: $1"; exit 1; }

git fetch -q origin main || deny "git fetch origin main failed"
[ -n "$(git rev-list origin/main..HEAD)" ] || deny "no commits ahead of origin/main"
[ -z "$(git status --porcelain --untracked-files=no)" ] || deny "uncommitted changes to tracked files"

paths=$(git diff --name-only origin/main...HEAD)
bad=$(grep -E '^(\.github/|\.claude/|CLAUDE\.md$|AGENTS\.md$|scripts/claude-|api/src/main/resources/db/changelog/)|(^|/)\.env' <<<"$paths" | head -5 | tr '\n' ' ')
[ -z "$bad" ] || deny "protected paths changed: $bad"

# numstat prints "-" for binary files; they count as 0 lines
loc=$(git diff --numstat origin/main...HEAD | awk -F'\t' '
  $3 ~ /(^|\/)(test|__tests__)\// || $3 ~ /\.test\.[jt]sx?$/ || $3 ~ /^docs\// || $3 == "TASKS.md" { next }
  { s += ($1 == "-" ? 0 : $1) + ($2 == "-" ? 0 : $2) } END { print s + 0 }')
[ "$loc" -le "$max_loc" ] || deny "$loc changed lines outside tests and docs (limit $max_loc)"

echo "auto-push allowed: $(git rev-list --count origin/main..HEAD) commit(s), $loc changed lines outside tests and docs"
