#!/bin/bash
# Session start hook for swarm context loading
# Loads project context and swarm state

INPUT=$(cat)

# jq is required to parse tool input past this point. Rather than failing
# silently when it's missing (hooks are guardrails, not a security boundary
# — but a silent gap is worse than a visible one), surface exactly what
# degrades so the current session knows what it can't rely on.
if ! command -v jq >/dev/null 2>&1; then
    cat << 'EOF'

[HOOK DEGRADATION]
jq is not installed — the following guardrails are degraded for this session:
- Secret detection & file-lock coordination (pre-tool-use-validator.sh): skipped entirely
- Dangerous-command warnings (dangerous-command-guard.sh): skipped entirely
- Commit quality gate (pre-commit-verification.sh): skipped entirely — commits are not gate-blocked
- Task-completion quality gate (task-quality-gate.sh): skipped entirely
Unaffected: pre-push-main-blocker.sh's branch-block does not depend on jq and
keeps working either way; permissions.deny (.claude/settings.json) is
enforced at the permission layer regardless of jq or any hook.
Install jq to restore full hook coverage.
EOF
    exit 0
fi

SOURCE=$(echo "$INPUT" | jq -r '.source // "startup"' 2>/dev/null || echo "startup")
SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // empty' 2>/dev/null || true)

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
STATE_DIR="$PROJECT_DIR/.claude/hooks/.state"
LOCK_DIR="$PROJECT_DIR/.claude/hooks/.locks"
mkdir -p "$STATE_DIR" "$LOCK_DIR" 2>/dev/null || true

# Clean up session files older than 24 hours
find "$STATE_DIR" -name "session_*.json" -type f -mtime +1 -delete 2>/dev/null || true

# Clean stale locks (>5 min old)
if [ -d "$LOCK_DIR" ]; then
    find "$LOCK_DIR" -name "*.lock" -mmin +5 -delete 2>/dev/null || true
fi

# Initialize session tracking
SESSION_SHORT=$(echo "$SESSION_ID" | cut -c1-8)
echo "{\"session_id\": \"$SESSION_ID\", \"started\": \"$(date -Iseconds)\", \"source\": \"$SOURCE\"}" > "$STATE_DIR/session_$SESSION_SHORT.json"

# Build context message
CONTEXT=""

# Post-compaction / resume re-orientation: on "compact", prior context was
# just summarized away; on "resume", this is picking up a session from
# scratch. Either way, don't trust what's already "known" — re-check state
# before continuing (see debugging-protocol.md's Stale Context Check).
if [ "$SOURCE" = "compact" ] || [ "$SOURCE" = "resume" ]; then
    CONTEXT="$CONTEXT

[POST-COMPACTION RE-ORIENTATION]
- Check the native task list for in-flight work before starting anything new
- If a plan artifact is active (docs/plans/plan-*.md), re-read it before continuing
- Re-read any file before editing it — do not trust memory of its contents (Stale Context Check, .claude/rules/debugging-protocol.md)"
fi

# Check for active swarm agents
ACTIVE_AGENTS=$(find "$STATE_DIR" -maxdepth 1 -name 'session_*.json' -type f 2>/dev/null | wc -l | tr -d ' ')
if [ "$ACTIVE_AGENTS" -gt 1 ]; then
    CONTEXT="$CONTEXT

[SWARM STATUS]
- Active agents in project: $ACTIVE_AGENTS
- Coordinate via the task tracker to avoid conflicts
- Check file locks before major edits"
fi

# Check for pending work from previous sessions
if [ -f "$STATE_DIR/handoff.json" ]; then
    HANDOFF=$(cat "$STATE_DIR/handoff.json" 2>/dev/null || true)
    HANDOFF_MSG=$(echo "$HANDOFF" | jq -r '.message // empty' 2>/dev/null || true)
    if [ -n "$HANDOFF_MSG" ]; then
        CONTEXT="$CONTEXT

[HANDOFF FROM PREVIOUS SESSION]
$HANDOFF_MSG"
        # Clear handoff after reading
        rm -f "$STATE_DIR/handoff.json"
    fi
fi

# Current task from session.txt (.claude/rules/session-file.md); /task-resume continues it.
# The model gets the raw file as context; the user gets a status report on screen
# (systemMessage) right at startup, before typing anything.
REPORT=""
SESSION_FILE="$PROJECT_DIR/session.txt"
if [ -s "$SESSION_FILE" ]; then
    field() { sed -n "s/^$1:[[:space:]]*//p" "$SESSION_FILE" | head -1 | tr -d '\r'; }
    # First ";"-item of a field, cut to 80 characters (UTF-8 aware) so the report stays one line per field
    short() {
        local LC_ALL=C.UTF-8 v
        v=$(field "$1"); v=${v%%;*}
        [ ${#v} -gt 80 ] && v="${v:0:79}…"
        printf '%s' "$v"
    }
    TASK_STATUS=$(field status)
    if [ "$TASK_STATUS" = "done" ]; then
        ICON="✅"
        HINT="Đã xong · gõ yêu cầu mới để bắt đầu task khác"
        NOTE="- Last task is done. The user already saw it in a status report from this hook; do not resume it."
    else
        ICON="📌"
        HINT="▶ /task-resume để làm tiếp"
        NOTE="- Unfinished task from a previous session. The user already saw a status report from this hook; in your first reply, if the user did not run /task-resume, add one line reminding them that /task-resume continues it. Continue only if they run /task-resume or ask to."
    fi
    CONTEXT="$CONTEXT

[CURRENT TASK — session.txt]
$(head -c 2000 "$SESSION_FILE" | tr -d '\r')
$NOTE
- Keep session.txt current while working (.claude/rules/session-file.md)."

    if [ "$SOURCE" != "compact" ]; then
        PROGRESS=$(field progress | tr -cd '0-9')
        PROGRESS=${PROGRESS:-0}
        [ "$PROGRESS" -gt 100 ] && PROGRESS=100
        FILLED=$((PROGRESS / 10))
        BAR="$(printf '%*s' "$FILLED" '' | tr ' ' '#')$(printf '%*s' $((10 - FILLED)) '' | tr ' ' '-')"
        REPORT="$ICON $(short task)
   ${TASK_STATUS:-?} · [$BAR] $PROGRESS% · $(field updated)"
        # Subtasks: one "<icon>" line per ";"-item of a field (at most 8, each cut to 70 characters)
        items() {
            field "$1" | LC_ALL=C.UTF-8 awk -v RS=';' -v icon="$2" '
                { gsub(/^[ \t\n]+|[ \t\n]+$/, "") }
                $0 == "" || $0 == "-" { next }
                { n++; if (n <= 8) printf "   %s %s\n", icon, (length($0) > 70 ? substr($0, 1, 69) "…" : $0) }
                END { if (n > 8) printf "   … +%d more\n", n - 8 }'
        }
        for spec in "done ✅" "doing 🔄"; do
            LINES=$(items ${spec% *} "${spec#* }")
            [ -n "$LINES" ] && REPORT="$REPORT
${LINES%$'\n'}"
        done
        BLOCKER=$(short blocker)
        [ -n "$BLOCKER" ] && REPORT="$REPORT
   ⛔ $BLOCKER"
        NEXT=$(short next)
        [ "$TASK_STATUS" != "done" ] && [ -n "$NEXT" ] && [ "$NEXT" != "-" ] && REPORT="$REPORT
   → $NEXT"
        REPORT="$REPORT
   $HINT"
        # Frame: left border only (emoji are 2 columns wide, so a right border would not line up)
        REPORT=$(printf '%s\n' "$REPORT" | awk '
            { line[NR] = $0 }
            END {
                for (i = 1; i <= NR; i++) {
                    s = line[i]; sub(/^   /, "", s)
                    printf "%s%s%s", (i == 1 ? "╭─ " : (i == NR ? "╰─ " : "│  ")), s, (i < NR ? "\n" : "")
                }
            }')
    fi
fi

# Output: JSON when there is a report for the user, plain text (context only) otherwise
if [ -n "$REPORT" ]; then
    jq -n --arg msg "$REPORT" --arg ctx "$CONTEXT" \
        '{systemMessage: $msg, hookSpecificOutput: {hookEventName: "SessionStart", additionalContext: $ctx}}'
elif [ -n "$CONTEXT" ]; then
    echo "$CONTEXT"
fi

exit 0
