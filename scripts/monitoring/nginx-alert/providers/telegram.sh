#!/usr/bin/env bash
# Telegram provider for lib-notify.sh. Defines send_alert_message(), the
# only contract lib-notify.sh callers rely on.
# Expects TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID, loaded here from
# /etc/nginx-alert/telegram.env (kept separate from notify.env so
# credentials for a provider survive switching ALERT_PROVIDER away and
# back).

TELEGRAM_ENV="/etc/nginx-alert/telegram.env"
# shellcheck disable=SC1090
[[ -f "$TELEGRAM_ENV" ]] && source "$TELEGRAM_ENV"

send_alert_message() {
  local text="$1"
  : "${TELEGRAM_BOT_TOKEN:?TELEGRAM_BOT_TOKEN not set (see /etc/nginx-alert/telegram.env)}"
  : "${TELEGRAM_CHAT_ID:?TELEGRAM_CHAT_ID not set (see /etc/nginx-alert/telegram.env)}"

  # -f makes curl exit non-zero on HTTP 4xx/5xx (e.g. wrong token/chat id),
  # so callers running under `set -e` actually notice a failed send.
  curl -sS --max-time 10 -f \
    -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
    --data-urlencode "chat_id=${TELEGRAM_CHAT_ID}" \
    --data-urlencode "text=${text}" \
    >/dev/null
}
