#!/data/data/com.termux/files/usr/bin/bash
# Termux 쪽 설치: 데몬을 ~/fterm 에 두고 한 번 띄운다.
# 재실행해도 안전하다(덮어쓰기 + 이미 떠 있으면 그대로).
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
DEST=${FTERM_HOME:-$HOME/fterm}

command -v python3 >/dev/null || { echo "python3 필요: pkg install python" >&2; exit 1; }

mkdir -p "$DEST"
cp "$HERE/ftermd" "$DEST/ftermd"
chmod 700 "$DEST/ftermd"

# The keeper is what lets the app start the daemon by itself: RUN_COMMAND is silently
# ignored by this Termux build, so the app writes a request file and the keeper acts on it.
cp "$HERE/fterm-keeper" "$DEST/fterm-keeper"
chmod 700 "$DEST/fterm-keeper"

echo "설치: $DEST/ftermd"
"$DEST/ftermd" status >/dev/null 2>&1 || true

if "$DEST/ftermd" status >/dev/null 2>&1; then
  echo "데몬이 이미 실행 중"
else
  echo "데몬 시작"
  # setsid: detach from the terminal's process group. A plain nohup daemon still dies
  # with SIGHUP when the Termux shell that launched it closes, which is exactly the
  # "ftermd died while I was away" report.
  setsid nohup "$DEST/ftermd" start > "$DEST/daemon.log" 2>&1 < /dev/null &
  sleep 1
fi

"$DEST/ftermd" status

# Start the keeper so the app can bring the daemon up on its own from now on.
if "$DEST/fterm-keeper" status >/dev/null 2>&1; then
  echo "keeper 이미 실행 중"
else
  echo "keeper 시작"
  setsid nohup "$DEST/fterm-keeper" start > /dev/null 2>&1 < /dev/null &
  sleep 1
fi
"$DEST/fterm-keeper" status

echo
# The daemon is a long-running process: replacing the file on disk does not change the
# process already in memory. Restart it so fixes (and TERM_PROGRAM) actually take effect.
if "$DEST/ftermd" status >/dev/null 2>&1; then
  echo "데몬 재시작 (실행 중인 프로세스는 옛 코드를 들고 있다)"
  "$DEST/ftermd" stop >/dev/null 2>&1 || true
  sleep 1
  setsid nohup "$DEST/ftermd" start > "$DEST/daemon.log" 2>&1 < /dev/null &
  sleep 2
fi

# Re-arm the keeper on every Termux start: it is the app's only handle for starting the
# daemon, so a keeper that died quietly must come back without the user remembering this
# script. Idempotent - the status check skips the spawn when it is already running.
BASHRC="$HOME/.bashrc"
if [ -f "$BASHRC" ] && ! grep -q "fterm-keeper" "$BASHRC"; then
  cat >> "$BASHRC" <<'HOOK'

# fterm: keep the daemon-keeper alive. The fterm app asks the keeper (via a file in
# /sdcard/Download) to start the daemon; without the keeper the app cannot revive it.
if [ -x "$HOME/fterm/fterm-keeper" ] && ! "$HOME/fterm/fterm-keeper" status >/dev/null 2>&1; then
  setsid nohup "$HOME/fterm/fterm-keeper" start >/dev/null 2>&1 </dev/null &
fi
HOOK
  echo "keeper 자동실행 훅을 ~/.bashrc에 추가했습니다"
fi

echo "앱을 열면 자동으로 연결됩니다."
echo "수동 토큰(필요할 때만):"
"$DEST/ftermd" token
