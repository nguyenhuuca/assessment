#!/usr/bin/env bash
# Runs the post-deploy API smoke test (Mock-Test.http) with httpyac.
# Credentials come from api/env.local (USER_CLAUDE_TEST, USER_CLAUDE_PASS); they are never printed.
# Target env (host/prefix in http-client.env.json): ENV=prod (default) or ENV=local.
set -euo pipefail
cd "$(dirname "$0")"

env_value() { grep -m1 "^$1=" env.local | cut -d= -f2- | tr -d '\r'; }

user=$(env_value USER_CLAUDE_TEST)
pass=$(env_value USER_CLAUDE_PASS)
if [ -z "$user" ] || [ -z "$pass" ]; then
  echo "USER_CLAUDE_TEST / USER_CLAUDE_PASS missing in api/env.local" >&2
  exit 2
fi

args=(--var "USER_CLAUDE_TEST=$user" --var "USER_CLAUDE_PASS=$pass")
npx -y httpyac@6.16.7 send Mock-Test.http --all --output short --env "${ENV:-prod}" "${args[@]}"
