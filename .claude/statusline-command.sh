#!/usr/bin/env bash
# Claude Code status line: model | context | cost (from Claude Code directly) | rate limits

input=$(cat)

# ANSI colors
RST=$'\033[0m'; DIM=$'\033[2m'; BOLD=$'\033[1m'
RED=$'\033[31m'; GRN=$'\033[32m'; YEL=$'\033[33m'; MAG=$'\033[35m'; CYN=$'\033[36m'

# Green below 50%, yellow below 80%, red from 80% (for "used" percentages)
pct_color() {
  local p=${1%.*}
  if [ "${p:-0}" -ge 80 ]; then printf '%s' "$RED"
  elif [ "${p:-0}" -ge 50 ]; then printf '%s' "$YEL"
  else printf '%s' "$GRN"; fi
}

model=$(echo "$input" | jq -r '.model.display_name // "Unknown"')
used_pct=$(echo "$input" | jq -r '.context_window.used_percentage // empty')
remaining_pct=$(echo "$input" | jq -r '.context_window.remaining_percentage // empty')

# ✅ Dùng cost.total_cost_usd do Claude Code tính sẵn (chính xác, có tính cache)
cost_usd=$(echo "$input" | jq -r '.cost.total_cost_usd // empty')

five_hour=$(echo "$input" | jq -r '.rate_limits.five_hour.used_percentage // empty')
seven_day=$(echo "$input" | jq -r '.rate_limits.seven_day.used_percentage // empty')

# --- Context segment ---
ctx_segment=""
if [ -n "$used_pct" ] && [ -n "$remaining_pct" ]; then
  ctx_segment=$(printf "ctx: %s%.0f%% used%s / %.0f%% left" "$(pct_color "$used_pct")" "$used_pct" "$RST" "$remaining_pct")
fi

# --- Cost segment (chính xác từ Claude Code) ---
cost_segment=""
if [ -n "$cost_usd" ]; then
  cost_segment=$(printf "cost: %s\$%.4f%s" "$MAG" "$cost_usd" "$RST")
fi

# --- Rate limit segment ---
rate_segment=""
if [ -n "$five_hour" ]; then
  rate_segment=$(printf "5h: %s%.0f%%%s" "$(pct_color "$five_hour")" "$five_hour" "$RST")
fi
if [ -n "$seven_day" ]; then
  rate_segment="${rate_segment:+$rate_segment }$(printf "7d: %s%.0f%%%s" "$(pct_color "$seven_day")" "$seven_day" "$RST")"
fi

# --- Current task segment (session.txt in the project root) ---
task_segment=""
proj_dir=$(echo "$input" | jq -r '.workspace.project_dir // .workspace.current_dir // .cwd // empty')
sess="$proj_dir/session.txt"
if [ -n "$proj_dir" ] && [ -s "$sess" ]; then
  t_name=$(sed -n 's/^task:[[:space:]]*//p' "$sess" | head -1 | tr -d '\r')
  t_pct=$(sed -n 's/^progress:[[:space:]]*//p' "$sess" | head -1 | tr -d '%\r')
  t_status=$(sed -n 's/^status:[[:space:]]*//p' "$sess" | head -1 | tr -d '\r')
  if [ -n "$t_name" ] && [ "$t_status" != "done" ]; then
    [ ${#t_name} -gt 40 ] && t_name="${t_name:0:39}…"
    # Status color: blocked = red, in_progress (or anything else) = yellow
    t_col=$YEL; [ "$t_status" = "blocked" ] && t_col=$RED
    task_segment="task: ${BOLD}${t_name}${RST}${t_pct:+ ${t_col}${t_pct}%${RST}}"
    [ -n "$t_status" ] && task_segment="$task_segment ${t_col}(${t_status})${RST}"
    # Subtask in flight (session.txt `doing:`); "-" or empty means nothing half-done
    t_doing=$(sed -n 's/^doing:[[:space:]]*//p' "$sess" | head -1 | tr -d '\r')
    if [ -n "$t_doing" ] && [ "$t_doing" != "-" ]; then
      [ ${#t_doing} -gt 40 ] && t_doing="${t_doing:0:39}…"
      task_segment="$task_segment ${DIM}→${RST} doing: ${CYN}${t_doing}${RST}"
    fi
  fi
fi

# --- Assemble ---
parts=()
[ -n "$model" ]        && parts+=("${CYN}${model}${RST}")
[ -n "$ctx_segment" ]  && parts+=("$ctx_segment")
[ -n "$cost_segment" ] && parts+=("$cost_segment")
[ -n "$rate_segment" ] && parts+=("rate: $rate_segment")
[ -n "$task_segment" ] && parts+=("$task_segment")

sep="${DIM} | ${RST}"
out=""
for p in "${parts[@]}"; do out="${out:+$out$sep}$p"; done
printf "%s" "$out"