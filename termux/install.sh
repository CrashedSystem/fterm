#!/data/data/com.termux/files/usr/bin/bash
# Termux 쪽 설치: 데몬을 ~/fterm 에 두고 한 번 띄운다.
# 재실행해도 안전하다(덮어쓰기 + 이미 떠 있으면 그대로).
#
# 이 스크립트는 두 갈래로 쓴다.
#
#   curl -fsSL https://raw.githubusercontent.com/CrashedSystem/fterm/master/termux/install.sh | bash
#   bash termux/install.sh          (저장소 안에서, 개발용)
#
# 둘 다 같은 일을 한다. 차이는 바이너리를 어디서 얻는가뿐이다. 저장소 안에서 부르면 이미
# 빌드해 둔 것이나 cargo 로 방금 빌드한 것을 쓰고, 그 밖에서는 GitHub Releases 에서 받는다.
# 사용자에게 Rust 툴체인을 요구하지 않는 것이 요점이다 - 데몬은 이식이 끝난 바이너리이고,
# 빌드는 CI 가 한다.
set -e

REPO=${FTERM_REPO:-CrashedSystem/fterm}
DEST=${FTERM_HOME:-$HOME/fterm}

# `curl | bash` 로 부르면 $0 이 bash 이고 저장소가 없다. 그때 HERE 는 비어 있는데,
# 아래에서 "로컬에 crate 가 있는가"를 이 값으로 판단하므로 빈 문자열이 곧 '배포 모드'다.
HERE=""
if [ -n "${BASH_SOURCE[0]:-}" ] && [ -f "${BASH_SOURCE[0]}" ]; then
  HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
fi

say() { printf '%s\n' "$*"; }
die() { printf '%s\n' "$*" >&2; exit 1; }

# --- what this device is ------------------------------------------------------------
#
# The release carries one architecture because every phone Termux runs on today is arm64.
# Saying so plainly beats downloading an ELF the kernel will refuse with "cannot execute".
ARCH=$(uname -m)
case "$ARCH" in
  aarch64|arm64) ;;
  *) die "이 기기는 $ARCH 입니다. 배포하는 데몬은 aarch64(arm64) 전용입니다.
소스에서 빌드하려면 저장소를 받아 termux/install.sh 를 실행하십시오 (cargo 필요)." ;;
esac

# The keeper is the only part that is still a script; it is the app's one handle for
# starting the daemon, so it needs a Python to run in.
command -v python3 >/dev/null || die "python3 필요: pkg install python"

mkdir -p "$DEST"
chmod 700 "$DEST"

# --- obtain the daemon --------------------------------------------------------------

# Where the release assets live. `latest` resolves through the /releases/latest/download
# redirect, so a re-run always picks up the newest build without an API call.
case "${FTERM_VERSION:-latest}" in
  latest) BASE="https://github.com/$REPO/releases/latest/download" ;;
  *)      BASE="https://github.com/$REPO/releases/download/${FTERM_VERSION}" ;;
esac

DOWNLOADED=""
fetch() {
  # fetch <asset> <destination>
  local asset=$1 out=$2
  command -v curl >/dev/null || die "curl 필요: pkg install curl"
  curl -fL --retry 3 --connect-timeout 20 -o "$out" "$BASE/$asset" \
    || die "$BASE/$asset 를 받지 못했습니다.
릴리스가 아직 없거나 네트워크가 막혔습니다. FTERM_VERSION=v0.4.7 처럼 버전을 지정해 보십시오."
}

RUST_BIN=""
if [ -n "${FTERM_RUST_BIN:-}" ] && [ -x "$FTERM_RUST_BIN" ]; then
  # Escape hatch for a hand-built binary: nothing is downloaded and nothing is compiled.
  RUST_BIN=$FTERM_RUST_BIN
  say "데몬: 지정된 바이너리 $RUST_BIN"
elif [ -n "$HERE" ] && [ -x "$HERE/ftermd-rs/target/release/ftermd" ]; then
  RUST_BIN="$HERE/ftermd-rs/target/release/ftermd"
  say "데몬: 저장소의 빌드 결과"
elif [ -n "$HERE" ] && [ -d "$HERE/ftermd-rs" ]; then
  # A checkout with no build yet. This is the one path that compiles, and it is deliberately
  # the developer path only - someone who cloned the repository has cargo or can get it.
  command -v cargo >/dev/null || die "저장소에서 빌드하려면 cargo 가 필요합니다: pkg install rust
(설치만 하려면 FTERM_VERSION 을 지정해 배포 바이너리를 받으십시오)"
  say "저장소에 빌드 결과가 없어 cargo 로 빌드합니다 (기기에서 몇 분 걸립니다)"
  ( cd "$HERE/ftermd-rs" && cargo build --release --locked )
  RUST_BIN="$HERE/ftermd-rs/target/release/ftermd"
else
  say "데몬 내려받기 ($BASE)"
  TMP=$(mktemp -d "${TMPDIR:-/tmp}/fterm-install.XXXXXX")
  trap 'rm -rf "$TMP"' EXIT
  fetch ftermd-aarch64-linux-android "$TMP/ftermd"
  fetch SHA256SUMS.txt "$TMP/SHA256SUMS.txt"
  # The checksum is not ceremony: this binary is fetched over the network and then run with
  # the user's Termux uid, which is every app's data. A truncated or substituted file has to
  # stop here rather than at first launch.
  if [ -s "$TMP/SHA256SUMS.txt" ]; then
    WANT=$(awk '$2 == "ftermd-aarch64-linux-android" { print $1 }' "$TMP/SHA256SUMS.txt")
    GOT=$(sha256sum "$TMP/ftermd" | awk '{ print $1 }')
    if [ -z "$WANT" ]; then
      say "경고: SHA256SUMS.txt 에 데몬 항목이 없어 검증을 건너뜁니다"
    elif [ "$WANT" != "$GOT" ]; then
      die "체크섬이 맞지 않습니다 (기대 $WANT, 실제 $GOT). 받은 파일을 쓰지 않습니다."
    else
      say "체크섬 확인: ${GOT%????????????????????????????????????????????????}"
    fi
  else
    say "경고: SHA256SUMS.txt 가 없어 검증을 건너뜁니다"
  fi
  RUST_BIN="$TMP/ftermd"
  DOWNLOADED=1
fi

# --- the keeper ---------------------------------------------------------------------

# Same split as the daemon: from a checkout, copy the working-tree script; otherwise fetch
# the released one. Keeping it in the release means `curl | bash` needs no repository at all.
KEEPER_SRC=""
if [ -n "$HERE" ] && [ -f "$HERE/fterm-keeper" ]; then
  KEEPER_SRC="$HERE/fterm-keeper"
else
  KEEPER_SRC="${TMP:-$(mktemp -d)}/fterm-keeper"
  fetch fterm-keeper "$KEEPER_SRC"
fi

# --- replace the running daemon -----------------------------------------------------
#
# A running binary cannot be overwritten: the kernel answers ETXTBSY ("Text file busy").
# Stopping first also made the restart below correct in the only order that works, since a
# daemon replaced under a live pidfile would keep serving the old code. The restart still runs
# in its own session (see below) because this script is usually started from a fterm tab whose
# shell is a child of the daemon being stopped.
if "$DEST/ftermd" status >/dev/null 2>&1; then
  say "실행 중인 데몬 정지 (교체를 위해)"
  "$DEST/ftermd" stop >/dev/null 2>&1 || true
  sleep 0.5
fi

cp "$RUST_BIN" "$DEST/ftermd"
chmod 700 "$DEST/ftermd"

# The keeper is what lets the app start the daemon by itself: RUN_COMMAND is silently
# ignored by this Termux build, so the app writes a request file and the keeper acts on it.
cp "$KEEPER_SRC" "$DEST/fterm-keeper"
chmod 700 "$DEST/fterm-keeper"

say "설치: $DEST/ftermd ($("$DEST/ftermd" version 2>/dev/null || echo '버전 확인 실패'))"

# The daemon is a long-running process: replacing the file on disk does not change the
# process already in memory. Start it if it is not running, and restart it if it is, so fixes
# (and TERM_PROGRAM) actually take effect. This is why the script restarts rather than
# printing "already running".
#
# The restart runs in its own session. This script is usually run from a fterm tab, and that
# tab's shell is a child of the daemon: `stop` closes the PTY, the shell gets SIGHUP, and the
# script dies halfway through - leaving no daemon at all. setsid keeps the restart alive past
# the shell that asked for it.
if "$DEST/ftermd" status >/dev/null 2>&1; then
  say "데몬 재시작 (실행 중인 프로세스는 옛 코드를 들고 있다)"
  setsid nohup sh -c "'$DEST/ftermd' stop >/dev/null 2>&1; sleep 1; \
    exec '$DEST/ftermd' start" > "$DEST/daemon.log" 2>&1 < /dev/null &
  sleep 3
else
  say "데몬 시작"
  # setsid: detach from the terminal's process group. A plain nohup daemon still dies with
  # SIGHUP when the Termux shell that launched it closes, which is exactly the "ftermd died
  # while I was away" report.
  setsid nohup "$DEST/ftermd" start > "$DEST/daemon.log" 2>&1 < /dev/null &
  sleep 1
fi

"$DEST/ftermd" status

# Start the keeper so the app can bring the daemon up on its own from now on.
if "$DEST/fterm-keeper" status >/dev/null 2>&1; then
  say "keeper 이미 실행 중"
else
  say "keeper 시작"
  setsid nohup "$DEST/fterm-keeper" start > /dev/null 2>&1 < /dev/null &
  sleep 1
fi
"$DEST/fterm-keeper" status

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
  say "keeper 자동실행 훅을 ~/.bashrc에 추가했습니다"
fi

say ""
say "앱을 열면 자동으로 연결됩니다."
say "수동 토큰(필요할 때만):"
"$DEST/ftermd" token

# A freshly downloaded file is deleted when the shell exits because of the trap above; say so
# explicitly rather than leaving the reader to wonder whether DEST depends on it.
if [ -n "$DOWNLOADED" ]; then
  say "(내려받은 임시 파일은 지워집니다. 설치본은 $DEST 에 있습니다.)"
fi
