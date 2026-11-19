#!/usr/bin/env bash
# Wire-level suites against one daemon binary.
#
#   bash tests/run_daemon.sh termux/ftermd-rs/target/release/ftermd
#
# The daemon is given as a command (not a path) so a future implementation with a different
# launcher fits the same harness. Everything inside is identical for every daemon: the same
# protocol client and the same mode-tracker claims judge it, which is the only way the claim
# "this daemon speaks the same protocol" means anything.
#
# The daemon runs on its own FTERM_RUNDIR and port so this never touches the phone's tabs.
set -uo pipefail

rc=0

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

echo "=== protocol spec (two tables: daemon and app)"
# Static, and independent of which daemon this run is for: it reads the constant tables out of
# the Rust daemon and the app's Protocol class and fails on any difference.
# It lives here because this script is what CI calls.
python3 "$ROOT/tests/test_protocol.py" || rc=1
echo
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

echo
echo "=== protocol (termux/test_client.py)"
python3 "$ROOT/termux/test_client.py" || rc=1

echo
echo "=== terminal modes over the wire (tests/test_modes.py)"
python3 "$ROOT/tests/test_modes.py" || rc=1

echo
# The three defects this daemon exists to remove: a stale pidfile that blocks startup, a
# blocked tab stalling the others, and an unbounded output buffer. They are properties of the
# implementation, not of the protocol, so they are not expected of every daemon that speaks
# the wire format - but the one we ship must have them.
if "${DAEMON[@]}" version 2>/dev/null | grep -q rust; then
  echo "=== daemon hardening (stale pidfile, slow client, blocked tab)"
  python3 "$ROOT/tests/test_daemon_hardening.py" "${DAEMON[@]}" || rc=1
else
  echo "=== daemon hardening: skipped for this daemon"
  echo "    tests/test_daemon_hardening.py pins the Rust daemon's hardening guarantees."
fi

echo
if [ "$rc" -eq 0 ]; then
  echo "DAEMON SUITES OK (${DAEMON[*]})"
else
  echo "DAEMON SUITES FAILED (${DAEMON[*]})" >&2
  echo "--- daemon log (last 40 lines)" >&2
  tail -40 "$LOG" >&2
fi
exit "$rc"
