#!/usr/bin/env bash
# Headless autopilot (ADR-0025): Gate 2 without a human, when the owner opted in with a label.
# Usage: bash scripts/claude-autopilot-merge.sh <pr>
# Env: GH_TOKEN (job token: read + comment), MERGE_TOKEN (secret AUTOPILOT_MERGE_TOKEN; a merge made with it
#      starts the deploy CI, a merge made with the job token would not).
# The workflow runs this from origin/main, never from the PR branch. Every check here is deterministic:
# the model never merges, and nothing it wrote decides the merge except marking the PR ready.
set -uo pipefail
pr=$1
max_loc=${AUTOPILOT_MAX_LOC:-400}
skip() {
  echo "auto-merge skipped: $1"
  gh pr comment "$pr" --body "Gate 2 auto-merge skipped: $1. Review and merge by hand."
  exit 0
}

info=$(gh pr view "$pr" --json headRefName,headRefOid,isDraft,state,labels,files)
branch=$(jq -r .headRefName <<<"$info")
case "$branch" in autopilot/issue-[0-9]*) ;; *) echo "not an autopilot PR: $branch"; exit 0 ;; esac
issue=${branch#autopilot/issue-}
labels=$( { jq -r '.labels[].name' <<<"$info"; gh issue view "$issue" --json labels --jq '.labels[].name'; } | sort -u)
grep -qxE 'autopilot:(auto|auto-merge)' <<<"$labels" || { echo "no auto label: Gate 2 stays manual"; exit 0; }

[ "$(jq -r .state <<<"$info")" = OPEN ] || skip "the PR is not open"
[ "$(jq -r .isDraft <<<"$info")" = false ] || skip "the PR is still a draft (code review or gates did not pass)"

paths=$(jq -r '.files[].path' <<<"$info")
bad=$(grep -E '^(\.github/|\.claude/|CLAUDE\.md$|AGENTS\.md$|scripts/claude-|api/src/main/resources/db/changelog/)|(^|/)\.env' <<<"$paths" | head -5 | tr '\n' ' ')
[ -z "$bad" ] || skip "protected paths changed: $bad"

loc=$(jq -r '.files[] | select(.path | test("(^|/)(test|__tests__)/|\\.test\\.[jt]sx?$|^docs/|^TASKS\\.md$") | not)
  | .additions + .deletions' <<<"$info" | awk '{ s += $1 } END { print s + 0 }')
[ "$loc" -le "$max_loc" ] || skip "$loc changed lines outside tests and docs (limit $max_loc)"

# Re-run the gates here instead of trusting the model's own report.
sha=$(jq -r .headRefOid <<<"$info")
[ "$(git rev-parse HEAD)" = "$sha" ] || skip "the checked-out commit is not the PR head $sha"
if grep -q '^api/' <<<"$paths"; then
  mvn -B -q -f api/pom.xml verify || skip "mvn verify failed"
fi
if grep -q '^webapp/' <<<"$paths"; then
  npm --prefix webapp run test -- --run || skip "webapp tests failed"
  npm --prefix webapp run lint || skip "webapp lint failed"
  npm --prefix webapp run build || skip "webapp build failed"
fi

[ -n "${MERGE_TOKEN:-}" ] || skip "the secret AUTOPILOT_MERGE_TOKEN is not set"
GH_TOKEN=$MERGE_TOKEN gh pr merge "$pr" --squash --match-head-commit "$sha" || skip "gh pr merge failed (see the run log)"
gh pr comment "$pr" --body "Gate 2 auto-approved by label (ADR-0025): merged \`${sha:0:8}\`. The normal CI deploys it; run \`./api/mock-test.sh\` after the deploy."
