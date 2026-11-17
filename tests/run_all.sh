#!/data/data/com.termux/files/usr/bin/bash
# Java 전송 계층 검증. Android 없이 실제 ftermd에 붙는다.
#   1) python 프로토콜 테스트
#   2) SocketTransport 왕복/한글/resize/replay/세션분리
#   3) DaemonClient SYNC (재실행 시 세션 재사용)
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
T=/data/data/com.termux/files/usr/tmp/fterm-jvm
export FTERM_RUNDIR=${FTERM_RUNDIR:-$HOME/.fterm}
export FTERM_PORT=${FTERM_PORT:-8791}
PORT=$FTERM_PORT

cd "$ROOT/termux"
echo "=== python protocol"
python3 test_client.py

echo
echo "=== java transport"
rm -rf "$T"; mkdir -p "$T/out"
javac -d "$T/out" \
  "$ROOT/app/src/com.termux/terminal/SessionTransport.java" \
  "$ROOT/app/src/com.termux/terminal/SocketTransport.java" \
  "$ROOT/app/src/com.termux/terminal/DaemonClient.java" \
  "$ROOT/tests/jvm/com/termux/terminal/TerminalSession.java" \
  "$ROOT/tests/jvm/harness/TransportTest.java" \
  "$ROOT/tests/jvm/harness/SyncTest.java" \
  "$ROOT/tests/jvm/harness/IdleTest.java" \
  "$ROOT/tests/jvm/harness/IdleDropTest.java" \
  "$ROOT/tests/jvm/harness/ReconnectTest.java"

TOKEN=$(cat "$FTERM_RUNDIR/token")
java -cp "$T/out" TransportTest "$TOKEN" "$PORT"
echo
java -cp "$T/out" SyncTest "$TOKEN" "$PORT"
echo
java -cp "$T/out" IdleTest "$TOKEN" "$PORT"
echo
java -Ddaemon.src="$ROOT/termux/ftermd" -cp "$T/out" IdleDropTest "$TOKEN" "$PORT"
echo
java -cp "$T/out" ReconnectTest
echo
echo "ALL SUITES OK"
