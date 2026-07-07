#!/usr/bin/env bash
# PostToolUse hook: append a concise entry to dev-log/YYYY-MM-DD.md
# Reads tool input from stdin (JSON), extracts file_path, captures real
# diff via `git diff --no-color`, appends one entry.
#
# Designed to be small + fast: no LLM call, no network, no extra deps.
# Token cost on the main conversation: zero (runs in a subshell).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOG_DIR="$PROJECT_ROOT/dev-log"
mkdir -p "$LOG_DIR"

INPUT="$(cat)"

TOOL_NAME="$(printf '%s' "$INPUT" | python -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_name',''))" 2>/dev/null || true)"
[ -z "$TOOL_NAME" ] && exit 0

# file_path can live at top level or under tool_input
FILE_PATH="$(printf '%s' "$INPUT" | python -c "import sys,json; d=json.load(sys.stdin); v=d.get('file_path') or d.get('tool_input',{}).get('file_path') or d.get('tool_input',{}).get('path',''); print(v)" 2>/dev/null || true)"
[ -z "$FILE_PATH" ] && exit 0

# Normalize: forward slashes only (keep original case so file paths stay
# correct on case-sensitive filesystems). On Windows, the file path may
# arrive as "d:/foo" while PROJECT_ROOT arrives from $(pwd) as "/d/foo".
# Convert drive-letter prefixes to a leading slash so both forms compare.
norm() {
  printf '%s' "$1" | tr '\\' '/' \
    | sed -E 's#^([A-Za-z]):#/\1#'
}
FILE_PATH_NORM="$(norm "$FILE_PATH")"
PROJECT_ROOT_NORM="$(norm "$PROJECT_ROOT")"
case "$FILE_PATH_NORM" in
  "$PROJECT_ROOT_NORM"/*) ;;
  *) exit 0 ;;
esac
REL_PATH="${FILE_PATH_NORM#${PROJECT_ROOT_NORM}/}"

# Skip noise: build outputs, the log itself, and crash logs
case "$FILE_PATH_NORM" in
  */node_modules/*|*/dist/*|*/target/*|*/.git/*|*/dev-log/*|*/.m2/*|*/hs_err_pid*|*/replay_pid*) exit 0 ;;
esac

TS="$(date '+%H:%M:%S')"
DATE="$(date '+%Y-%m-%d')"
LOG_FILE="$LOG_DIR/$DATE.md"

DIFF="$(cd "$PROJECT_ROOT" && git diff --no-color -- "$REL_PATH" 2>/dev/null || true)"
if [ -z "$DIFF" ] && [ -f "$FILE_PATH" ]; then
  if ! (cd "$PROJECT_ROOT" && git ls-files --error-unmatch "$REL_PATH" >/dev/null 2>&1); then
    NEW_CONTENT="$(head -c 2000 "$FILE_PATH")"
    DIFF="$(printf '(new file)\n````\n%s\n````\n' "$NEW_CONTENT")"
  fi
fi

if [ "${#DIFF}" -gt 1500 ]; then
  DIFF="$(printf '%s\n... (truncated; full diff in `git diff -- %s`) ...' "${DIFF:0:1500}" "$REL_PATH")"
fi

if [ ! -f "$LOG_FILE" ]; then
  {
    echo "# $DATE 开发日志"
    echo
    echo "> 自动记录 · 由 .claude/hooks/log-change.sh 生成"
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
    echo "_no diff captured (file may be unmodified or outside git tracking)_"
  fi
  echo
} >> "$LOG_FILE"

exit 0