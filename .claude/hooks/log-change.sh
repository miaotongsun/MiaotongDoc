#!/usr/bin/env bash
# PostToolUse hook: append a concise entry to dev-log/YYYY-MM-DD.md
#
# Reads tool input from stdin (JSON), extracts tool_name + file_path,
# captures real diff via `git diff --no-color`, appends one entry.
#
# Triggered by Claude Code PostToolUse hook event with matcher
# "Write|Edit|MultiEdit". See .claude/settings.json.
#
# Designed to be small + fast: no LLM call, no network, no extra deps.
# Token cost on the main conversation: zero (runs in a subshell).
#
# Exit codes:
#   0  - success (entry written OR intentionally skipped)
#   1  - unexpected error (logged to stderr; hook runner continues)

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOG_DIR="$PROJECT_ROOT/dev-log"
mkdir -p "$LOG_DIR" || { echo "log-change: cannot create $LOG_DIR" >&2; exit 1; }

INPUT="$(cat)"

# --- Extract fields from JSON via python (Git Bash ships Python, jq doesn't) ---
extract() {
  local key="$1"
  printf '%s' "$INPUT" | python -c "
import sys, json
try:
    d = json.load(sys.stdin)
    for path in ('$key', 'tool_input.$key', 'tool_input.path'):
        v = d
        for part in path.split('.'):
            if isinstance(v, dict) and part in v:
                v = v[part]
            else:
                v = ''
                break
        if v:
            print(v); sys.exit(0)
except Exception:
    pass
" 2>/dev/null
}

TOOL_NAME="$(extract tool_name)"
FILE_PATH="$(extract file_path)"

# Nothing useful to log
[ -z "$TOOL_NAME" ] && exit 0
[ -z "$FILE_PATH" ] && exit 0

# --- Path normalization: Windows "d:/foo" → "/d/foo" so it matches
# PROJECT_ROOT which arrives as "/d/foo" from $(pwd). Keep original case
# so file paths stay correct on case-sensitive filesystems.
norm() {
  printf '%s' "$1" | tr '\\' '/' | sed -E 's#^([A-Za-z]):#/\1#'
}
FILE_PATH_NORM="$(norm "$FILE_PATH")"
PROJECT_ROOT_NORM="$(norm "$PROJECT_ROOT")"
case "$FILE_PATH_NORM" in
  "$PROJECT_ROOT_NORM"/*) ;;
  *) exit 0 ;;
esac
REL_PATH="${FILE_PATH_NORM#${PROJECT_ROOT_NORM}/}"

# Skip noise: build outputs, the log itself, crash logs, dependency caches
case "$FILE_PATH_NORM" in
  */node_modules/*|*/dist/*|*/target/*|*/.git/*|*/dev-log/*|*/.m2/*|*/hs_err_pid*|*/replay_pid*) exit 0 ;;
esac

# Verify the file actually exists before logging
[ -f "$FILE_PATH" ] || exit 0

TS="$(date '+%H:%M:%S')"
DATE="$(date '+%Y-%m-%d')"
LOG_FILE="$LOG_DIR/$DATE.md"

# --- Capture real diff ---
# Use `git diff HEAD` so we see what's actually changed relative to the last
# commit (works for both staged and unstaged changes).
DIFF="$(cd "$PROJECT_ROOT" && git diff --no-color HEAD -- "$REL_PATH" 2>/dev/null || true)"

# If nothing in HEAD diff, file might be untracked (new file)
if [ -z "$DIFF" ]; then
  if ! (cd "$PROJECT_ROOT" && git ls-files --error-unmatch "$REL_PATH" >/dev/null 2>&1); then
    # New (untracked) file: show first ~2KB of content. Use python to truncate
    # safely on a UTF-8 character boundary (avoid cutting multi-byte chars).
    NEW_CONTENT="$(python -c "
import sys
try:
    with open(r'''$FILE_PATH''', 'rb') as f:
        data = f.read(2500)
    # Decode safely, ignoring incomplete tail bytes
    text = data.decode('utf-8', errors='ignore')
    print(text[:2000])
except Exception:
    pass
" 2>/dev/null || true)"
    if [ -n "$NEW_CONTENT" ]; then
      DIFF="(new file, first 2KB below)
\`\`\`
$NEW_CONTENT
\`\`\`"
    fi
  fi
fi

# Truncate huge diffs; preserve full text in git history
if [ "${#DIFF}" -gt 2000 ]; then
  DIFF="${DIFF:0:2000}
... (truncated; full diff in \`git diff HEAD -- $REL_PATH\`) ..."
fi

# --- Write entry ---
if [ ! -f "$LOG_FILE" ]; then
  {
    echo "# $DATE 开发日志"
    echo
    echo "> 自动记录 · 由 .claude/hooks/log-change.sh 生成（VSCode 扩展模式下可能不触发）"
    echo
  } > "$LOG_FILE"
fi

{
  echo "## $TS · $TOOL_NAME · $REL_PATH"
  echo
  if [ -n "$DIFF" ]; then
    echo '````diff'
    echo "$DIFF"
    echo '````'
  else
    echo "_no diff (file unchanged from HEAD)_"
  fi
  echo
} >> "$LOG_FILE"

exit 0