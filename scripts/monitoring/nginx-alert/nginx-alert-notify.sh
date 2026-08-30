#!/usr/bin/env bash
# Sends an alert when nginx.service enters the failed state.
# Installed at /usr/local/bin/nginx-alert-notify.sh and invoked by
# nginx-alert.service (see nginx-alert.service in this directory).
set -euo pipefail

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${LIB_DIR}/lib-notify.sh"

HOST="$(hostname)"
TIMESTAMP="$(date '+%Y-%m-%d %H:%M:%S %Z')"
STATUS="$(systemctl status nginx --no-pager -l 2>&1 | tail -n 15 || true)"

MESSAGE="$(cat <<EOF
🔴 nginx DOWN on ${HOST}
Time: ${TIMESTAMP}

${STATUS}
EOF
)"

send_alert_message "$MESSAGE"
