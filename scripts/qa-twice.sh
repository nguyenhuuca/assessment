#!/usr/bin/env bash
# Runs the given tests twice and labels each one pass / flaky / fail (used by /autopilot Local QA).
#   pass  = passed both runs
#   flaky = failed one run only  -> reported, does not block
#   fail  = failed both runs     -> blocks
#
# Usage: scripts/qa-twice.sh [--be Class1,Class2] [--fe path/a.test.jsx path/b.test.js ...]
#   --be  backend test classes (mvn -Dtest syntax, comma-separated), run in api/
#   --fe  webapp test files (relative to webapp/), run with vitest
# Exit: 0 = no fail (flaky allowed), 1 = at least one fail, 2 = bad usage or a run that produced no results.
# Local only: no network calls besides what the tests themselves do, no prod access.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/scratchpad/qa-twice"
be=""
fe=()

while [ $# -gt 0 ]; do
  case "$1" in
    --be) be="${2:-}"; shift 2 ;;
    --fe) shift; while [ $# -gt 0 ] && [ "${1#--}" = "$1" ]; do fe+=("$1"); shift; done ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
if [ -z "$be" ] && [ ${#fe[@]} -eq 0 ]; then
  echo "usage: $0 [--be Class1,Class2] [--fe file1 file2 ...]" >&2
  exit 2
fi

rm -rf "$out" && mkdir -p "$out"

for run in 1 2; do
  if [ -n "$be" ]; then
    echo "== backend run $run: $be"
    rm -rf "$root/api/target/surefire-reports"
    (cd "$root/api" && mvn -q test -Dtest="$be" -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true) \
      > "$out/be-$run.log" 2>&1 || true
    if [ ! -d "$root/api/target/surefire-reports" ]; then
      echo "backend run $run produced no surefire reports (compile error?), see $out/be-$run.log" >&2
      exit 2
    fi
    cp -r "$root/api/target/surefire-reports" "$out/be-$run"
  fi
  if [ ${#fe[@]} -gt 0 ]; then
    echo "== webapp run $run: ${fe[*]}"
    (cd "$root/webapp" && npx vitest run "${fe[@]}" --reporter=json --outputFile="$out/fe-$run.json") \
      > "$out/fe-$run.log" 2>&1 || true
    if [ ! -s "$out/fe-$run.json" ]; then
      echo "webapp run $run produced no JSON report, see $out/fe-$run.log" >&2
      exit 2
    fi
  fi
done

node "$root/scripts/qa-twice-report.mjs" "$out"
