#!/usr/bin/env bash
# Deploys and verifies the full nginx-alert toolkit (crash-alert,
# traffic-alert, fail2ban auto-block) on a remote server over SSH.
#
# Usage:
#   cp server.config.example server.config   # fill in your server details
#   ./run-all.sh                             # or: ./run-all.sh /path/to/other.config
#
# Idempotent: safe to re-run. Existing credential files (telegram.env,
# traffic.env, notify.env) are never overwritten once created — only
# scripts, systemd units, and fail2ban config get redeployed every run.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${1:-${SCRIPT_DIR}/server.config}"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "Config file not found: $CONFIG_FILE" >&2
  echo "Copy server.config.example to server.config and fill in your server details." >&2
  exit 1
fi
# shellcheck disable=SC1090
source "$CONFIG_FILE"

: "${SSH_HOST:?SSH_HOST not set in $CONFIG_FILE}"
: "${SSH_USER:?SSH_USER not set in $CONFIG_FILE}"
: "${SSH_KEY_PATH:?SSH_KEY_PATH not set in $CONFIG_FILE}"
SSH_PORT="${SSH_PORT:-22}"

# Convert a Windows-style path (D:\foo\bar) to Git-Bash form (/d/foo/bar).
if [[ "$SSH_KEY_PATH" =~ ^([A-Za-z]):\\(.*)$ ]]; then
  drive="${BASH_REMATCH[1],,}"
  rest="${BASH_REMATCH[2]//\\//}"
  SSH_KEY_PATH="/${drive}/${rest}"
fi

SSH_OPTS=(-i "$SSH_KEY_PATH" -p "$SSH_PORT" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10)
REMOTE="${SSH_USER}@${SSH_HOST}"

remote() {
  ssh "${SSH_OPTS[@]}" "$REMOTE" "$@"
}

push() {
  # push <local-file> [<local-file>...] <remote-dir>
  local args=("$@")
  local dest="${args[-1]}"
  unset 'args[-1]'
  scp -i "$SSH_KEY_PATH" -P "$SSH_PORT" -o StrictHostKeyChecking=accept-new \
    "${args[@]}" "${REMOTE}:${dest}"
}

PASS=0
FAIL=0

check() {
  local desc="$1"
  local cmd="$2"
  if remote "$cmd"; then
    echo "  OK   $desc"
    PASS=$((PASS + 1))
  else
    echo "  FAIL $desc"
    FAIL=$((FAIL + 1))
  fi
}

echo "==> Deploying to ${REMOTE}:${SSH_PORT}"

echo "--- 1. Remote directories ---"
remote 'mkdir -p /usr/local/bin/providers /etc/nginx-alert /etc/systemd/system/nginx.service.d /etc/fail2ban/filter.d /etc/fail2ban/action.d /etc/fail2ban/jail.d /var/lib/nginx-alert'

echo "--- 2. Notify dispatcher + telegram provider + alert scripts ---"
push "${SCRIPT_DIR}/lib-notify.sh" "${SCRIPT_DIR}/nginx-alert-notify.sh" "${SCRIPT_DIR}/nginx-traffic-alert.sh" /usr/local/bin/
push "${SCRIPT_DIR}/providers/telegram.sh" /usr/local/bin/providers/
push "${SCRIPT_DIR}/fail2ban/fail2ban-notify.sh" /usr/local/bin/
remote 'chmod 755 /usr/local/bin/lib-notify.sh /usr/local/bin/nginx-alert-notify.sh /usr/local/bin/nginx-traffic-alert.sh /usr/local/bin/fail2ban-notify.sh /usr/local/bin/providers/telegram.sh'

echo "--- 3. Config env files (created only if missing — existing creds untouched) ---"
remote '[[ -f /etc/nginx-alert/notify.env ]] || { echo "ALERT_PROVIDER=telegram" > /etc/nginx-alert/notify.env; echo "  created notify.env"; }'
remote '[[ -f /etc/nginx-alert/telegram.env ]] || { printf "TELEGRAM_BOT_TOKEN=REPLACE_ME\nTELEGRAM_CHAT_ID=REPLACE_ME\n" > /etc/nginx-alert/telegram.env; chmod 600 /etc/nginx-alert/telegram.env; echo "  created telegram.env — EDIT IT with real bot token/chat id"; }'
remote '[[ -f /etc/nginx-alert/traffic.env ]] || { printf "ACCESS_LOG=/var/log/nginx/access.log\nTHRESHOLD_TOTAL=600\nTHRESHOLD_IP=200\nALERT_COOLDOWN_SEC=600\n" > /etc/nginx-alert/traffic.env; chmod 600 /etc/nginx-alert/traffic.env; echo "  created traffic.env with default thresholds"; }'

echo "--- 4. systemd units ---"
push "${SCRIPT_DIR}/nginx-alert.service" "${SCRIPT_DIR}/nginx-traffic-alert.service" "${SCRIPT_DIR}/nginx-traffic-alert.timer" /etc/systemd/system/
push "${SCRIPT_DIR}/nginx-override.conf" /etc/systemd/system/nginx.service.d/override.conf

echo "--- 4b. Cloudflare real_ip (so fail2ban bans the real attacker, not Cloudflare's edge) ---"
push "${SCRIPT_DIR}/cloudflare-realip.conf" /etc/nginx/conf.d/cloudflare-realip.conf
remote 'nginx -t && systemctl reload nginx'

echo "--- 5. fail2ban filters / action / jail ---"
push "${SCRIPT_DIR}/fail2ban/nginx-exploit-probe.filter" /etc/fail2ban/filter.d/nginx-exploit-probe.conf
push "${SCRIPT_DIR}/fail2ban/nginx-req-limit.filter" /etc/fail2ban/filter.d/nginx-req-limit.conf
push "${SCRIPT_DIR}/fail2ban/notify.action" /etc/fail2ban/action.d/notify.conf
push "${SCRIPT_DIR}/fail2ban/nginx-custom.jail" /etc/fail2ban/jail.d/nginx-custom.conf

echo "--- 6. Reload systemd + fail2ban, enable timer ---"
remote 'systemctl daemon-reload'
remote 'systemctl enable --now nginx-traffic-alert.timer'
remote 'fail2ban-client -t'
# `restart`, not `reload`: fail2ban does not reliably re-read a jail's
# *action list* (e.g. adding/removing the notify action) on reload for
# already-running jails — only a full restart guarantees it. Bans persist
# across the restart via fail2ban's sqlite ban database (dbfile), though
# IPs banned purely via manual `fail2ban-client ... banip` (no matching
# filter ticket) can be dropped on restart — re-ban those manually after
# if you relied on any.
remote 'systemctl restart fail2ban'

echo ""
echo "==> Verifying"

check "lib-notify.sh installed"           'test -x /usr/local/bin/lib-notify.sh'
check "telegram provider installed"       'test -x /usr/local/bin/providers/telegram.sh'
check "nginx-alert-notify.sh installed"   'test -x /usr/local/bin/nginx-alert-notify.sh'
check "nginx-traffic-alert.sh installed"  'test -x /usr/local/bin/nginx-traffic-alert.sh'
check "fail2ban-notify.sh installed"      'test -x /usr/local/bin/fail2ban-notify.sh'
check "notify.env present"                'test -f /etc/nginx-alert/notify.env'
check "telegram.env present, mode 600"    '[[ "$(stat -c %a /etc/nginx-alert/telegram.env)" == "600" ]]'
check "traffic.env present"               'test -f /etc/nginx-alert/traffic.env'
check "nginx-alert.service installed"     'test -f /etc/systemd/system/nginx-alert.service'
check "nginx.service.d override in place" 'test -f /etc/systemd/system/nginx.service.d/override.conf'
check "nginx-traffic-alert.timer active"  'systemctl is-active --quiet nginx-traffic-alert.timer'
check "nginx.service override applied"    'systemctl show nginx.service -p Restart | grep -q Restart=always'
check "Cloudflare real_ip config applied" 'nginx -T 2>/dev/null | grep -q "real_ip_header CF-Connecting-IP"'
check "fail2ban config valid"             'fail2ban-client -t >/dev/null'
check "nginx-exploit-probe jail active"   'fail2ban-client status nginx-exploit-probe >/dev/null'
check "nginx-req-limit jail active"       'fail2ban-client status nginx-req-limit >/dev/null'
# Catches the reload-vs-restart pitfall directly: if a previous run only
# reloaded fail2ban after changing the action list, the notify action can
# silently be missing from the running jail even though jail.d/*.conf on
# disk is correct.
check "nginx-exploit-probe has notify action" 'fail2ban-client get nginx-exploit-probe actions | grep -q notify'
check "nginx-req-limit has notify action"     'fail2ban-client get nginx-req-limit actions | grep -q notify'
check "nginx itself is healthy"           'systemctl is-active --quiet nginx'

if remote 'grep -q REPLACE_ME /etc/nginx-alert/telegram.env 2>/dev/null'; then
  echo "  WARN telegram.env still has placeholder values — edit it on the server, then re-run this script to also run the live-delivery checks below"
else
  echo "--- live delivery test (telegram.env looks configured) ---"
  check "crash-alert delivers"  'systemctl start nginx-alert.service && systemctl show nginx-alert.service -p Result | grep -q Result=success'
  check "traffic-alert runs ok" 'systemctl start nginx-traffic-alert.service && systemctl show nginx-traffic-alert.service -p Result | grep -q Result=success'
  # Fake IP with zero access-log matches — the exact scenario that once
  # made fail2ban-notify.sh fail silently under pipefail+set -e.
  check "fail2ban-notify.sh handles no-match IP" '/usr/local/bin/fail2ban-notify.sh smoke-test 198.51.100.1 1 ban'
fi

echo ""
echo "==> ${PASS} passed, ${FAIL} failed"
[[ "$FAIL" -eq 0 ]]
