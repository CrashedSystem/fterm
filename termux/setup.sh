#!/data/data/com.termux/files/usr/bin/bash
# APK만 받은 사람을 위한 한 줄 설치.
#
#   curl -fsSL https://raw.githubusercontent.com/CrashedSystem/fterm/master/termux/setup.sh | bash
#
# install.sh 와 다른 점은 하나입니다: 저장소를 clone 하지 않고 Rust로 빌드하지도 않습니다.
# GitHub Releases에 첨부된 aarch64 바이너리를 그대로 받아 ~/fterm 에 놓습니다. 폰에서
# `cargo build --release` 는 몇 분이 걸리고, 그동안 앱은 "Termux 준비가 필요합니다" 창만
# 띄우고 있었습니다. APK를 받은 사람에게 저장소 경로를 외우게 할 이유도 없습니다.
#
# 재실행해도 안전합니다. 마지막에 토큰을 /sdcard/Download/fterm-token 에 써 두므로,
# Termux를 닫고 앱으로 돌아오면 keeper의 첫 폴링을 기다리지 않고 바로 연결됩니다.
#
# 아래 FTERM_* 변수는 기기 밖에서 이 스크립트를 시험하기 위한 것입니다(CI나 로컬에서
# 가짜 릴리즈를 물려 실행). 기기에서는 비워 두면 됩니다.
set -u

DEST=${FTERM_HOME:-$HOME/fterm}
SHARED=${FTERM_SHARED:-/sdcard/Download}
RAW=${FTERM_RAW:-https://raw.githubusercontent.com/CrashedSystem/fterm/master}
REL=${FTERM_RELEASE:-https://github.com/CrashedSystem/fterm/releases/latest/download}
MACHINE=${FTERM_MACHINE:-$(uname -m)}
ASSET=ftermd-aarch64-linux-android

say() { printf '%s\n' "$*"; }
warn() { printf 'setup.sh: %s\n' "$*" >&2; }
die() { warn "$*"; exit 1; }

# ---------------------------------------------------------------- 1. 무엇이 필요한가

case "$MACHINE" in
  aarch64|arm64) ;;
  *)
    warn "이 기기는 $MACHINE 입니다. 릴리즈에는 aarch64(arm64) 바이너리만 있습니다."
    warn "32비트 ARM이나 x86 에뮬레이터는 저장소에서 직접 빌드해야 합니다:"
    warn "  cd ~/projects/fterm && bash termux/install.sh"
    exit 1
    ;;
esac

if ! command -v curl >/dev/null 2>&1 || ! command -v python3 >/dev/null 2>&1; then
  say "필요한 패키지를 설치합니다 (python curl)"
  command -v pkg >/dev/null 2>&1 || die "pkg 가 없습니다 — 이 스크립트는 Termux 안에서 실행해야 합니다"
  pkg install -y python curl || die "패키지 설치 실패: pkg install -y python curl"
fi

# Both halves of the channel live in /sdcard/Download, and Termux reaches it through
# `termux-setup-storage`. Checked here, before anything is installed, so the one thing the user
# has to do is said while they are still looking at the terminal.
SHARED_OK=0
if mkdir -p "$SHARED" 2>/dev/null && : > "$SHARED/.fterm-write-test" 2>/dev/null; then
  rm -f "$SHARED/.fterm-write-test"
  SHARED_OK=1
fi

mkdir -p "$DEST" || die "$DEST 를 만들 수 없습니다"

# ---------------------------------------------------------------- 2. 내려받기

# A partial download must never land on the real name: curl writes to .new, and only a
# complete, non-empty file is renamed over the binary the app and the keeper will exec.
fetch() {
  curl -fsSL "$1" -o "$2" || die "내려받기 실패: $1"
  [ -s "$2" ] || die "빈 파일을 받았습니다: $1"
  chmod 700 "$2"
}

say "데몬을 내려받습니다: $REL/$ASSET"
fetch "$REL/$ASSET" "$DEST/ftermd.new"
say "keeper를 내려받습니다: $RAW/termux/fterm-keeper"
fetch "$RAW/termux/fterm-keeper" "$DEST/fterm-keeper.new"

# The daemon is fetched over the network and then run with the user's Termux uid, which is
# every app's private data. A truncated or substituted file has to stop here rather than at
# first launch, so the release's SHA256SUMS.txt judges both downloads.
#
# The keeper comes from raw.githubusercontent rather than the release, so it has no line in
# that file; it is a script whose content is visible, and the daemon is the binary that matters.
SUMS=$(mktemp "${TMPDIR:-/tmp}/fterm-sums.XXXXXX")
if curl -fsSL "$REL/SHA256SUMS.txt" -o "$SUMS" 2>/dev/null && [ -s "$SUMS" ]; then
  WANT=$(awk -v a="$ASSET" '$2 == a { print $1 }' "$SUMS")
  GOT=$(sha256sum "$DEST/ftermd.new" | awk '{ print $1 }')
  if [ -z "$WANT" ]; then
    warn "SHA256SUMS.txt 에 $ASSET 항목이 없어 검증을 건너뜁니다"
  elif [ "$WANT" != "$GOT" ]; then
    rm -f "$DEST/ftermd.new" "$SUMS"
    die "체크섬이 맞지 않습니다 (기대 $WANT, 실제 $GOT). 받은 파일을 쓰지 않습니다."
  else
    say "체크섬 확인: ${GOT%????????????????????????????????????????????????}"
  fi
else
  warn "SHA256SUMS.txt 를 받지 못해 검증을 건너뜁니다"
fi
rm -f "$SUMS"

# rename, not cp: a running executable cannot be truncated (ETXTBSY), but its directory entry
# can be replaced. The running daemon keeps the inode it was started from; the restart below is
# what makes the new file take effect.
mv -f "$DEST/ftermd.new" "$DEST/ftermd" || die "$DEST/ftermd 를 교체하지 못했습니다"
mv -f "$DEST/fterm-keeper.new" "$DEST/fterm-keeper" || die "$DEST/fterm-keeper 를 교체하지 못했습니다"
chmod 700 "$DEST/ftermd" "$DEST/fterm-keeper" || die "실행 권한을 주지 못했습니다"
say "설치: $DEST/ftermd, $DEST/fterm-keeper"

# ---------------------------------------------------------------- 3. 띄우기

# The restart runs in its own session. This script is often started from a fterm tab, and that
# tab's shell is a child of the daemon being stopped: `ftermd stop` closes the PTY, the shell
# gets SIGHUP, and this script dies halfway through - having replaced the binaries and left no
# daemon at all. The detached script finishes the job whoever is left; the waiting below is
# only so the user sees an answer.
RESTART="$DEST/.setup-restart.sh"
cat > "$RESTART" <<RESTART_EOF
#!/bin/sh
d="$DEST/ftermd"
k="$DEST/fterm-keeper"
"\$d" stop >/dev/null 2>&1
"\$k" stop >/dev/null 2>&1
sleep 1
setsid nohup "\$d" start > "$DEST/daemon.log" 2>&1 < /dev/null &
i=0
while [ \$i -lt 40 ] && ! "\$d" status >/dev/null 2>&1; do i=\$((i+1)); sleep 0.25; done
setsid nohup "\$k" start > /dev/null 2>&1 < /dev/null &
sleep 1
# 어떤 순서로 설치되든 상관없이, 앱이 읽는 곳에 토큰을 한 번 써 둔다. keeper도 같은 일을
# 하지만 keeper의 첫 폴링은 최대 1초 뒤이고, 그 전에 앱이 열리면 앱은 4.5초를 기다린 끝에
# "keeper 없음"을 보게 된다.
t=\$("\$d" token 2>/dev/null | tr -d ' \r\n')
if [ -n "\$t" ]; then
  printf '%s' "\$t" > "$SHARED/fterm-token.tmp" 2>/dev/null &&
    chmod 644 "$SHARED/fterm-token.tmp" 2>/dev/null &&
    mv -f "$SHARED/fterm-token.tmp" "$SHARED/fterm-token" 2>/dev/null
fi
RESTART_EOF
chmod 700 "$RESTART"

# 이전 실행이 남긴 토큰 파일을 지우고 시작한다. 아래 기다림이 "이번 설치가 썼다"는 뜻이
# 되게 하려는 것이고, 데몬의 토큰 자체는 ~/.fterm 에 남아 있으므로 지워도 잃는 것이 없다.
[ "$SHARED_OK" = 1 ] && rm -f "$SHARED/fterm-token"

setsid nohup /bin/sh "$RESTART" > /dev/null 2>&1 < /dev/null &

i=0
while [ $i -lt 40 ] && ! "$DEST/ftermd" status >/dev/null 2>&1; do i=$((i+1)); sleep 0.25; done
# The detached script publishes the token after it has the daemon up; wait for the file rather
# than printing an answer from the race between the two.
i=0
while [ "$SHARED_OK" = 1 ] && [ $i -lt 40 ] && [ ! -s "$SHARED/fterm-token" ]; do
  i=$((i+1)); sleep 0.25
done

if "$DEST/ftermd" status >/dev/null 2>&1; then
  say "데몬이 떴습니다."
else
  warn "데몬이 뜨지 않았습니다 — $DEST/daemon.log 를 보세요."
fi
rm -f "$RESTART"

# ---------------------------------------------------------------- 4. 다음에도 살아 있게

# Re-arm the keeper on every Termux start: it is the app's only handle for starting the daemon,
# so a keeper that died quietly must come back without the user remembering this script.
BASHRC="$HOME/.bashrc"
touch "$BASHRC" 2>/dev/null || true
if [ -f "$BASHRC" ] && ! grep -q "fterm-keeper" "$BASHRC"; then
  cat >> "$BASHRC" <<'HOOK'

# fterm: keep the daemon-keeper alive. The fterm app asks the keeper (via a file in
# /sdcard/Download) to start the daemon; without the keeper the app cannot revive it.
if [ -x "$HOME/fterm/fterm-keeper" ] && ! "$HOME/fterm/fterm-keeper" status >/dev/null 2>&1; then
  setsid nohup "$HOME/fterm/fterm-keeper" start >/dev/null 2>&1 </dev/null &
fi
HOOK
  say "keeper 자동실행 훅을 ~/.bashrc 에 추가했습니다"
fi

say ""
if [ "$SHARED_OK" = 1 ] && [ -s "$SHARED/fterm-token" ]; then
  say "토큰을 $SHARED/fterm-token 에 써 두었습니다 — 이제 앱으로 돌아오면 바로 연결됩니다."
  say ""
  say "수동으로 넣어야 할 때만 쓰는 토큰:"
  cat "$SHARED/fterm-token"; say ""
elif [ "$SHARED_OK" = 1 ]; then
  t=$("$DEST/ftermd" token 2>/dev/null | tr -d ' \r\n')
  if [ -n "$t" ]; then
    say "토큰 파일을 쓰지 못했습니다. 앱에서 '직접 토큰 입력'을 누르고 아래 값을 붙여넣으세요:"
    say "  $t"
  else
    warn "토큰을 읽지 못했습니다 — 데몬이 뜨지 않았으니 먼저 $DEST/daemon.log 를 보세요."
    exit 1
  fi
else
  warn "$SHARED 는 쓸 수 없습니다. Termux에서 termux-setup-storage 를 실행하고 이 스크립트를"
  warn "다시 실행하세요. 앱과 keeper가 만나는 곳이 그 디렉터리뿐입니다."
  warn "지금도 수동 입력은 됩니다 — Termux에서 ~/fterm/ftermd token 의 출력을 앱에 붙여넣으세요."
  exit 1
fi
