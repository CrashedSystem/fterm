#!/data/data/com.termux/files/usr/bin/bash
# SocketTransport JVM 검증. Android 없이 실제 ftermd에 붙는다.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
T=/data/data/com.termux/files/usr/tmp/fterm-jvm
RUNDIR=${FTERM_RUNDIR:-/data/data/com.termux/files/usr/tmp/ftrund}
PORT=${FTERM_PORT:-8791}

rm -rf "$T"; mkdir -p "$T/out"
javac -d "$T/out" \
  "$ROOT/app/src/com.termux/terminal/SessionTransport.java" \
  "$ROOT/app/src/com.termux/terminal/SocketTransport.java" \
  "$ROOT/tests/jvm/com/termux/terminal/TerminalSession.java" \
  "$ROOT/tests/jvm/harness/TransportTest.java"

TOKEN=$(cat "$RUNDIR/token")
java -cp "$T/out" TransportTest "$TOKEN" "$PORT"
