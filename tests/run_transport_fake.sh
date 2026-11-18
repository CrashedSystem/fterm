#!/usr/bin/env bash
# 전송 계층 검증 중 데몬이 필요 없는 것. 가짜 데몬이 소켓 위에서 프로토콜만 흉내 낸다.
#   bash tests/run_transport_fake.sh
#
# run_transport.sh 는 진짜 ftermd 에 붙어야 하므로 Termux 가 필요하지만, "S_ERROR 로 끝난
# 세션은 C_CLOSE 를 보내야 한다" 같은 계약은 데몬 없이도 확인할 수 있고, 확인해야 한다.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${FTERM_TMPDIR:-/data/data/com.termux/files/usr/tmp}/fterm-transport
rm -rf "$OUT"; mkdir -p "$OUT"

javac -nowarn -d "$OUT" \
  "$ROOT/app/src/com.termux/terminal/SessionTransport.java" \
  "$ROOT/app/src/com.termux/terminal/SocketTransport.java" \
  "$ROOT/tests/jvm/com/termux/terminal/TerminalSession.java" \
  "$ROOT/tests/jvm/harness/OrphanCloseTest.java"

java -cp "$OUT" OrphanCloseTest
