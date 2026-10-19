#!/usr/bin/env bash
# Runs the post-deploy API smoke test (Mock-Test.http) with httpyac.
# Credentials come from api/env.local (USER_CLAUDE_TEST, USER_CLAUDE_PASS); they are never printed.
# Target env (host/prefix in http-client.env.json): ENV=prod (default) or ENV=local.
# Waits for /actuator/health to be UP first (HEALTH_WAIT seconds, default 120).
set -euo pipefail
cd "$(dirname "$0")"

env_value() { grep -m1 "^$1=" env.local | cut -d= -f2- | tr -d '\r'; }

user=$(env_value USER_CLAUDE_TEST)
pass=$(env_value USER_CLAUDE_PASS)
if [ -z "$user" ] || [ -z "$pass" ]; then
  echo "USER_CLAUDE_TEST / USER_CLAUDE_PASS missing in api/env.local" >&2
  exit 2
fi

# Right after a deploy the backend is still restarting and every request errors. Wait until
# /actuator/health says UP (HEALTH_WAIT seconds, default 120; 0 skips the wait).
env_name="${ENV:-prod}"
base=$(node -e 'const e=require("./http-client.env.json")[process.argv[1]]; if(!e) process.exit(1); process.stdout.write(e.host+e.prefix)' "$env_name") \
  || { echo "unknown ENV '$env_name' in http-client.env.json" >&2; exit 2; }
wait_s="${HEALTH_WAIT:-120}"
waited=0
until curl -fsS --max-time 5 "$base/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
  if [ "$waited" -ge "$wait_s" ]; then
    echo "health not UP after ${wait_s}s at $base/actuator/health; running the smoke test anyway" >&2
    break
  fi
  [ "$waited" -eq 0 ] && echo "waiting for $base/actuator/health to be UP (max ${wait_s}s)..."
  sleep 5
  waited=$((waited + 5))
done
[ "$waited" -gt 0 ] && [ "$waited" -lt "$wait_s" ] && echo "health UP after ${waited}s"

args=(--var "USER_CLAUDE_TEST=$user" --var "USER_CLAUDE_PASS=$pass")
npx -y httpyac@6.16.7 send Mock-Test.http --all --output short --env "$env_name" "${args[@]}"
