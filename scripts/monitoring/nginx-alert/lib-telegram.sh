#!/usr/bin/env bash
# Shared Telegram sender for nginx-alert-telegram.sh and nginx-traffic-alert.sh.
# Source this file, then call send_telegram_message "text".
# Expects TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID to already be in the environment
# (both scripts load them from /etc/nginx-alert/telegram.env before sourcing this).

send_telegram_message() {
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
