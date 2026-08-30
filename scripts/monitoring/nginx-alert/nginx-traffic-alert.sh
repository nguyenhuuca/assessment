#!/usr/bin/env bash
# Detects abnormal nginx request volume by diffing access.log growth since
# the last run: total requests in the interval, and requests from the
# single busiest IP. Invoked every minute by nginx-traffic-alert.timer.
#
# Assumes the default nginx combined/common log format, where the first
# whitespace-separated field of each line is the client IP.
set -euo pipefail

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${LIB_DIR}/lib-telegram.sh"

TELEGRAM_ENV="/etc/nginx-alert/telegram.env"
TRAFFIC_ENV="/etc/nginx-alert/traffic.env"
# shellcheck disable=SC1090
[[ -f "$TELEGRAM_ENV" ]] && source "$TELEGRAM_ENV"
# shellcheck disable=SC1090
[[ -f "$TRAFFIC_ENV" ]] && source "$TRAFFIC_ENV"

ACCESS_LOG="${ACCESS_LOG:-/var/log/nginx/access.log}"
THRESHOLD_TOTAL="${THRESHOLD_TOTAL:-600}"
THRESHOLD_IP="${THRESHOLD_IP:-200}"
ALERT_COOLDOWN_SEC="${ALERT_COOLDOWN_SEC:-600}"

STATE_DIR="/var/lib/nginx-alert"
OFFSET_FILE="${STATE_DIR}/access.offset"
LAST_TOTAL_ALERT="${STATE_DIR}/last-total-alert"
LAST_IP_ALERT="${STATE_DIR}/last-ip-alert"

mkdir -p "$STATE_DIR"

if [[ ! -f "$ACCESS_LOG" ]]; then
  echo "access log not found: $ACCESS_LOG" >&2
  exit 0
fi

CUR_SIZE=$(stat -c '%s' "$ACCESS_LOG")
PREV_OFFSET=0
[[ -f "$OFFSET_FILE" ]] && PREV_OFFSET=$(cat "$OFFSET_FILE")

# Log rotated/truncated since last run -> re-read from the top of the new file.
if (( CUR_SIZE < PREV_OFFSET )); then
  PREV_OFFSET=0
fi

echo "$CUR_SIZE" > "$OFFSET_FILE"

if (( CUR_SIZE == PREV_OFFSET )); then
  exit 0  # no new lines since last run
fi

NEW_LINES="$(tail -c +$((PREV_OFFSET + 1)) "$ACCESS_LOG")"
TOTAL_COUNT=$(wc -l <<< "$NEW_LINES")

TOP_IP_LINE="$(awk '{print $1}' <<< "$NEW_LINES" | sort | uniq -c | sort -rn | head -1)"
TOP_IP_COUNT="$(awk '{print $1}' <<< "$TOP_IP_LINE")"
TOP_IP="$(awk '{print $2}' <<< "$TOP_IP_LINE")"

NOW=$(date +%s)

cooldown_ok() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  local last
  last=$(cat "$file")
  (( NOW - last >= ALERT_COOLDOWN_SEC ))
}

if (( TOTAL_COUNT > THRESHOLD_TOTAL )) && cooldown_ok "$LAST_TOTAL_ALERT"; then
  send_telegram_message "⚠️ nginx traffic spike on $(hostname)
Total requests in last interval: ${TOTAL_COUNT} (threshold ${THRESHOLD_TOTAL})
Top IP: ${TOP_IP:-n/a} (${TOP_IP_COUNT:-0} reqs)"
  echo "$NOW" > "$LAST_TOTAL_ALERT"
fi

if [[ -n "${TOP_IP:-}" ]] && (( TOP_IP_COUNT > THRESHOLD_IP )) && cooldown_ok "$LAST_IP_ALERT"; then
  send_telegram_message "⚠️ Abnormal request volume from single IP on $(hostname)
IP: ${TOP_IP}
Requests in last interval: ${TOP_IP_COUNT} (threshold ${THRESHOLD_IP})"
  echo "$NOW" > "$LAST_IP_ALERT"
fi
