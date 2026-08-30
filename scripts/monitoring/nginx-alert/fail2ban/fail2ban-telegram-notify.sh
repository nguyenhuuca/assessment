#!/usr/bin/env bash
# Installed at /usr/local/bin/fail2ban-telegram-notify.sh, called by the
# telegram fail2ban action (see telegram.action in this directory) on every
# ban/unban. Reuses lib-telegram.sh + /etc/nginx-alert/telegram.env from the
# crash/traffic alert setup one level up.
set -euo pipefail

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${LIB_DIR}/lib-telegram.sh"

ENV_FILE="/etc/nginx-alert/telegram.env"
# shellcheck disable=SC1090
[[ -f "$ENV_FILE" ]] && source "$ENV_FILE"

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
    TOP_PATHS="$(grep "^${IP} " "$ACCESS_LOG" 2>/dev/null \
      | awk -F'"' '{print $2}' | awk '{print $2}' \
      | sort | uniq -c | sort -rn | head -5)"
  fi

  send_telegram_message "🚫 fail2ban BANNED ${IP} on $(hostname)
Jail: ${JAIL}
Failures: ${FAILURES}
Top endpoints:
${TOP_PATHS:-n/a}"
else
  send_telegram_message "fail2ban unbanned ${IP} on $(hostname)
Jail: ${JAIL}"
fi
