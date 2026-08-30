#!/usr/bin/env bash
# Installed at /usr/local/bin/fail2ban-notify.sh, called by the notify
# fail2ban action (see notify.action in this directory) on every ban/unban.
# Reuses lib-notify.sh (provider-agnostic dispatcher) from the crash/traffic
# alert setup one level up.
set -euo pipefail

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${LIB_DIR}/lib-notify.sh"

TRAFFIC_ENV="/etc/nginx-alert/traffic.env"
# shellcheck disable=SC1090
[[ -f "$TRAFFIC_ENV" ]] && source "$TRAFFIC_ENV"
ACCESS_LOG="${ACCESS_LOG:-/var/log/nginx/access.log}"

JAIL="$1"
IP="$2"
FAILURES="$3"
ACTION="$4"  # ban | unban

if [[ "$ACTION" == "ban" ]]; then
  TOP_PATHS=""
  if [[ -f "$ACCESS_LOG" ]]; then
    # `|| true`: grep exits 1 on zero matches (not an error, just "IP not
    # in this log"), which under pipefail would otherwise abort the whole
    # script via set -e before send_alert_message ever runs.
    TOP_PATHS="$(grep "^${IP} " "$ACCESS_LOG" 2>/dev/null \
      | awk -F'"' '{print $2}' | awk '{print $2}' \
      | sort | uniq -c | sort -rn | head -5 || true)"
  fi

  send_alert_message "🚫 fail2ban BANNED ${IP} on $(hostname)
Jail: ${JAIL}
Failures: ${FAILURES}
Top endpoints:
${TOP_PATHS:-n/a}"
else
  send_alert_message "fail2ban unbanned ${IP} on $(hostname)
Jail: ${JAIL}"
fi
