#!/usr/bin/env bash
# Wire-level suites against one daemon binary.
#
#   bash tests/run_daemon.sh python3 termux/ftermd
#   bash tests/run_daemon.sh termux/ftermd-rs/target/release/ftermd
#
# The daemon is given as a command (not a path) because the two daemons are started
# differently: the Python one carries a Termux shebang that nothing else has, so on a Linux
# runner it has to be handed to python3 explicitly, while the Rust one is a binary. Everything
# after that is identical - the same protocol client and the same mode-tracker claims judge
# both, which is the only way the claim "the Rust daemon is protocol compatible" means
# anything.
#
# The daemon runs on its own FTERM_RUNDIR and port so this never touches the phone's tabs.
set -uo pipefail

if [ $# -eq 0 ]; then
  echo "usage: $0 <daemon command...>" >&2
  exit 2
fi

ROOT=$(cd "$(dirname "$0")/.." && pwd)
DAEMON=("$@")
RUNDIR=$(mktemp -d "${TMPDIR:-/tmp}/fterm-rund.XXXXXX")
PORT=${FTERM_PORT:-8791}
LOG="$RUNDIR/daemon.log"
DPID=""

export FTERM_RUNDIR="$RUNDIR"
export FTERM_PORT="$PORT"

cleanup() {
  if [ -n "$DPID" ]; then
    "${DAEMON[@]}" stop >/dev/null 2>&1 || true
    # A daemon that is a child of this script becomes a zombie when it exits and is only
    # gone once reaped; `stop` cannot tell a zombie from a live process, so reap here.
    wait "$DPID" 2>/dev/null || true
    if kill -0 "$DPID" 2>/dev/null; then
      kill -TERM "$DPID" 2>/dev/null || true
      sleep 1
      kill -KILL "$DPID" 2>/dev/null || true
    fi
  fi
  rm -rf "$RUNDIR"
}
trap cleanup EXIT

echo "=== daemon: ${DAEMON[*]}"
echo "=== port $PORT, rundir $RUNDIR"
"${DAEMON[@]}" start >"$LOG" 2>&1 &
DPID=$!

ready=""
for _ in $(seq 1 100); do
  # `status` asks the socket, not just the pidfile, so this is a real readiness check.
  if "${DAEMON[@]}" status >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 0.1
done
if [ -z "$ready" ]; then
  echo "daemon did not come up on 127.0.0.1:$PORT" >&2
  echo "--- daemon log" >&2
  cat "$LOG" >&2 2>/dev/null
  exit 1
fi
sed -n '1p' "$LOG" 2>/dev/null

rc=0

echo
echo "=== protocol (termux/test_client.py)"
python3 "$ROOT/termux/test_client.py" || rc=1

echo
echo "=== terminal modes over the wire (tests/test_modes.py --daemon)"
python3 "$ROOT/tests/test_modes.py" --daemon || rc=1

echo
if [ "$rc" -eq 0 ]; then
  echo "DAEMON SUITES OK (${DAEMON[*]})"
else
  echo "DAEMON SUITES FAILED (${DAEMON[*]})" >&2
  echo "--- daemon log (last 40 lines)" >&2
  tail -40 "$LOG" >&2
fi
exit "$rc"
