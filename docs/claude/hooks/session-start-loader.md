# session-start-loader.sh

SessionStart — re-orientation context after compact/resume; injects the task from session.txt into context and shows the user a boxed status report at startup.

Source: `.claude/hooks/session-start-loader.sh`, wired in `.claude/settings.json`.

```bash
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
    # Cut to 70 characters (UTF-8 aware) so every table row stays one line
    cut70() { local LC_ALL=C.UTF-8 v=$1; [ ${#v} -gt 70 ] && v="${v:0:69}…"; printf '%s' "$v"; }
    # First ";"-item of a field
    short() { local v; v=$(field "$1"); cut70 "${v%%;*}"; }
    TASK_STATUS=$(field status)
    if [ "$TASK_STATUS" = "done" ]; then
        ICON="✅"
        TITLE="Task gần nhất (session.txt)"
        HINT="Đã xong · gõ yêu cầu mới để bắt đầu task khác"
        NOTE="- Last task is done. The user already saw it in a status report from this hook; do not resume it."
    else
        ICON="📌"
        TITLE="Task đang dở (session.txt)"
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
        BAR="$(printf '%*s' "$FILLED" '' | sed 's/ /█/g')$(printf '%*s' $((10 - FILLED)) '' | sed 's/ /░/g')"
        # Two-column table: label | value. "sep" rows become ├──┼──┤ lines between groups.
        LC_ALL=C.UTF-8
        LABELS=() VALUES=() COMMITS=() SEP=0
        row() {
            [ "$SEP" = 1 ] && [ ${#LABELS[@]} -gt 0 ] && { LABELS+=("sep"); VALUES+=(""); COMMITS+=(""); }
            SEP=0; LABELS+=("$1"); VALUES+=("$2"); COMMITS+=("${3:-}")
        }
        # One row per ";"-item of a field (at most 8); the label shows on the first row only.
        # A trailing commit hash ("... 6a0a5849" or "... (6a0a5849)") moves to its own column.
        rows() {
            local label=$2 n=0 it commit
            while IFS= read -r it; do
                it="${it#"${it%%[![:space:]]*}"}"; it="${it%"${it##*[![:space:]]}"}"
                [ -z "$it" ] || [ "$it" = "-" ] && continue
                commit=""
                if [[ $it =~ ^(.*[^[:space:]])[[:space:]]+\(?([0-9a-f]{7,40})\)?$ ]]; then
                    it=${BASH_REMATCH[1]}; commit=${BASH_REMATCH[2]:0:8}
                fi
                n=$((n + 1)); [ $n -le 8 ] && { row "$label" "$(cut70 "$it")" "$commit"; label=""; }
            done < <(field "$1" | tr ';' '\n')
            [ $n -gt 8 ] && row "" "… +$((n - 8)) more"
        }
        row "$ICON Task" "$(short task)"
        row "Status" "${TASK_STATUS:-?} · [$BAR] $PROGRESS% · $(field updated)"
        SEP=1; rows done "✅ Done"
        SEP=1; rows doing "🔄 Doing"
        SEP=1
        BLOCKER=$(short blocker); [ -n "$BLOCKER" ] && row "⛔ Blocked" "$BLOCKER"
        NEXT=$(short next)
        [ "$TASK_STATUS" != "done" ] && [ -n "$NEXT" ] && [ "$NEXT" != "-" ] && row "→ Next" "$NEXT"

        # Display width: these emoji take 2 terminal columns, everything else 1
        # (counted per emoji: Git Bash sees 📌/🔄 as two UTF-16 units, so ${#s} alone is off)
        dw() {
            local s=$1 e r n=0
            for e in ✅ 🔄 ⛔ 📌; do
                r=${s//"$e"/}; n=$(( n + (${#s} - ${#r}) / ${#e} )); s=$r
            done
            echo $(( ${#s} + 2 * n ))
        }
        pad() { printf '%s%*s' "$1" $(( $2 - $(dw "$1") )) ''; }
        line() { printf '%*s' $(($1 + 2)) '' | sed 's/ /─/g'; }
        # hr <left> <mid> <right>; the commit column is drawn only when some row has a commit
        hr() {
            printf '%s%s%s%s' "$1" "$(line $LW)" "$2" "$(line $VW)"
            [ "$CW" -gt 0 ] && printf '%s%s' "$2" "$(line $CW)"
            printf '%s\n' "$3"
        }
        LW=0 VW=0 CW=0
        for i in "${!LABELS[@]}"; do
            [ "${LABELS[$i]}" = "sep" ] && continue
            w=$(dw "${LABELS[$i]}"); [ "$w" -gt "$LW" ] && LW=$w
            w=$(dw "${VALUES[$i]}"); [ "$w" -gt "$VW" ] && VW=$w
            w=${#COMMITS[$i]}; [ "$w" -gt "$CW" ] && CW=$w
        done
        # The UI prefixes the first line with "SessionStart:startup says: ", so the
        # table starts on line two, under a title line that absorbs the prefix.
        REPORT=$(
            printf '%s\n' "$TITLE"
            hr "┌" "┬" "┐"
            for i in "${!LABELS[@]}"; do
                if [ "${LABELS[$i]}" = "sep" ]; then hr "├" "┼" "┤"; continue; fi
                printf '│ %s │ %s │' "$(pad "${LABELS[$i]}" "$LW")" "$(pad "${VALUES[$i]}" "$VW")"
                [ "$CW" -gt 0 ] && printf ' %s │' "$(pad "${COMMITS[$i]}" "$CW")"
                printf '\n'
            done
            hr "└" "┴" "┘"
            printf '%s' "$HINT"
        )
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
```
