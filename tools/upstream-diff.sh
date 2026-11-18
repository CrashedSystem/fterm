#!/usr/bin/env bash
# 벤더한 com.termux 파일과 업스트림을 파일별로 비교한다.
#
#   bash tools/upstream-diff.sh                 # 고정된 기준점(아래 BASE)과 비교
#   bash tools/upstream-diff.sh <commit|tag>    # 다른 지점과 비교
#
# app/src/com.termux/ 아래 파일은 termux/termux-app 에서 가져와 고친 것이다. "무엇을 고쳤는지"를
# 사람이 기억하는 대신 매번 이 스크립트로 뽑는다. 네트워크가 필요하고, 실패해도 종료 코드는 0이다
# (정보를 보여 주는 도구이지 게이트가 아니다). 기준점과 표는 docs/UPSTREAM.md 에 있다.
set -uo pipefail

BASE=8629e632fcb95da272221be327db653fb24befe9
REF=${1:-$BASE}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TMP=$(mktemp -d "${TMPDIR:-/tmp}/fterm-upstream.XXXXXX")
trap 'rm -rf "$TMP"' EXIT

echo "기준: termux/termux-app @ $REF"
echo

identical=0
modified=0
local_only=0

while IFS= read -r rel; do
  case "$rel" in
    terminal/*) up="terminal-emulator/src/main/java/com/termux/$rel" ;;
    view/*)     up="terminal-view/src/main/java/com/termux/$rel" ;;
    *)          up="" ;;
  esac
  local="$ROOT/app/src/com.termux/$rel"
  [ -n "$up" ] || { echo "  (경로 규칙 없음) $rel"; continue; }

  url="https://raw.githubusercontent.com/termux/termux-app/$REF/$up"
  out="$TMP/$(basename "$rel")"
  code=$(curl -sS -o "$out" -w "%{http_code}" --max-time 60 "$url" 2>/dev/null || echo 000)
  if [ "$code" != "200" ]; then
    printf '  %-56s 이 저장소 (업스트림에 없음)\n' "$rel"
    local_only=$((local_only + 1))
    continue
  fi
  add=$(diff -u "$out" "$local" | grep -c '^+[^+]' || true)
  del=$(diff -u "$out" "$local" | grep -c '^-[^-]' || true)
  if [ "$add" = "0" ] && [ "$del" = "0" ]; then
    printf '  %-56s 동일\n' "$rel"
    identical=$((identical + 1))
  else
    printf '  %-56s +%-5s -%-5s\n' "$rel" "$add" "$del"
    modified=$((modified + 1))
  fi
done < <(cd "$ROOT/app/src" && find com.termux -name '*.java' | sed 's|^com.termux/||' | sort)

echo
echo "동일 $identical · 수정 $modified · 이 저장소 $local_only"
echo "표를 바꿨다면 docs/UPSTREAM.md 도 같이 고치십시오."
