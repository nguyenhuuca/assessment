#!/usr/bin/env bash
# Provider-agnostic alert dispatcher. Sourced by nginx-alert-notify.sh,
# nginx-traffic-alert.sh, and fail2ban/fail2ban-notify.sh — none of which
# know or care which provider is actually configured.
#
# To switch notification provider: drop providers/<name>.sh (must define a
# send_alert_message() function taking the message text as $1) and set
# ALERT_PROVIDER=<name> in /etc/nginx-alert/notify.env. No caller script
# needs to change.

NOTIFY_ENV="/etc/nginx-alert/notify.env"
# shellcheck disable=SC1090
[[ -f "$NOTIFY_ENV" ]] && source "$NOTIFY_ENV"
ALERT_PROVIDER="${ALERT_PROVIDER:-telegram}"

NOTIFY_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROVIDER_FILE="${NOTIFY_LIB_DIR}/providers/${ALERT_PROVIDER}.sh"

if [[ ! -f "$PROVIDER_FILE" ]]; then
  echo "Unknown ALERT_PROVIDER '${ALERT_PROVIDER}': ${PROVIDER_FILE} not found" >&2
  exit 1
fi
# shellcheck disable=SC1090
source "$PROVIDER_FILE"
