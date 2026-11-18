#!/data/data/com.termux/files/usr/bin/bash
# ANSI 파서 검증: 실제 TerminalEmulator를 안드로이드 스텁 위에서 돌린다.
# 렌더링은 안 하고 그리드만 확인한다 — 한글이 2칸을 차지하는지가 핵심.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
# Termux's tmp is the natural scratch dir on a phone; a Linux CI runner has no such path, so
# it can point this somewhere else and run the same suite unmodified.
OUT=${FTERM_TMPDIR:-/data/data/com.termux/files/usr/tmp}/fterm-core
rm -rf "$OUT"; mkdir -p "$OUT"

STUB=$ROOT/tests/androidstub
CORE=$ROOT/app/src/com.termux/terminal

echo "[1/3] 컴파일 (엔진 + 스텁 + 테스트)"
javac -nowarn -d "$OUT" \
  $(find "$STUB" -name '*.java') \
  "$CORE/ByteQueue.java" \
  "$CORE/WcWidth.java" \
  "$CORE/TextStyle.java" \
  "$CORE/TerminalColorScheme.java" \
  "$CORE/TerminalColors.java" \
  "$CORE/Logger.java" \
  "$CORE/AndroidUtils.java" \
  "$CORE/TerminalSixel.java" \
  "$CORE/TerminalBitmap.java" \
  "$CORE/ITermImage.java" \
  "$CORE/TerminalRow.java" \
  "$CORE/TerminalBuffer.java" \
  "$CORE/TerminalEmulator.java" \
  "$CORE/KeyHandler.java" \
  "$CORE/TerminalSessionClient.java" \
  "$ROOT/tests/corestub/com/termux/terminal/TerminalSession.java" \
  "$ROOT/tests/corestub/ai/openclaw/fterm/Fonts.java" \
  "$CORE/TerminalOutput.java" \
  "$ROOT/tests/jvm/core/WidthTest.java" \
  "$ROOT/tests/jvm/core/ScreenTest.java" \
  "$ROOT/tests/jvm/core/SchemesTest.java" \
  "$ROOT/tests/jvm/core/PasteTest.java" \
  "$ROOT/tests/jvm/core/FontsTest.java" \
  "$ROOT/tests/jvm/core/TabCloseTest.java" \
  "$ROOT/tests/jvm/core/TabTitleTest.java" \
  "$ROOT/tests/jvm/core/ImeComposeTest.java" \
  "$ROOT/tests/jvm/core/UiTest.java" \
  "$ROOT/tests/jvm/core/StructureTest.java" \
  "$ROOT/tests/jvm/core/BellTest.java" \
  "$ROOT/tests/jvm/core/NotifyTest.java" \
  "$ROOT/tests/jvm/core/FocusTest.java" \
  "$ROOT/tests/jvm/core/KeyBarPrefsTest.java" \
  "$ROOT/app/src/ai/openclaw/fterm/Schemes.java" \
  "$ROOT/app/src/ai/openclaw/fterm/Profiles.java" \
  "$ROOT/tests/jvm/core/ProfilesTest.java" \
  "$ROOT/tests/jvm/core/ProfileTabTest.java" \
  "$ROOT/app/src/ai/openclaw/fterm/Search.java" \
  "$ROOT/tests/jvm/core/SearchTest.java" \
  "$ROOT/app/src/ai/openclaw/fterm/RetroShader.java" \
  "$ROOT/tests/jvm/core/RetroTest.java" \
  "$ROOT/app/src/ai/openclaw/fterm/FontCoverage.java" \
  "$ROOT/app/src/ai/openclaw/fterm/FontZoom.java" \
  "$ROOT/tests/jvm/core/FontCoverageTest.java" \
  "$ROOT/tests/jvm/core/FontZoomTest.java" \
  "$ROOT/tests/jvm/core/ModeRestoreTest.java" \
  "$ROOT/tests/jvm/core/ScrollbackTest.java"


echo "[2/3] 실행"
java -cp "$OUT" WidthTest
java -cp "$OUT" ScreenTest
java -cp "$OUT" SchemesTest
java -Dmain.src="$ROOT/app/src/ai/openclaw/fterm/MainActivity.java" \
  -Dclip.src="$ROOT/app/src/ai/openclaw/fterm/ClipboardImage.java" -cp "$OUT" PasteTest
java -Dmain.src="$ROOT/app/src/ai/openclaw/fterm/MainActivity.java" -cp "$OUT" FontsTest
java -Dmain.src="$ROOT/app/src/ai/openclaw/fterm/MainActivity.java" \
  -Dview.src="$ROOT/app/src/com.termux/view/TerminalView.java" -cp "$OUT" TabCloseTest
java -Dview.src="$ROOT/app/src/com.termux/view/TerminalView.java" -cp "$OUT" ImeComposeTest
java -Dmain.src="$ROOT/app/src/ai/openclaw/fterm/MainActivity.java" -cp "$OUT" TabTitleTest
java -Dmain.src="$ROOT/app/src/ai/openclaw/fterm/MainActivity.java" -cp "$OUT" UiTest
java -Dapp.dir="$ROOT/app/src/ai/openclaw/fterm/" -cp "$OUT" StructureTest
java -Droot="$ROOT" -cp "$OUT" BellTest
java -Dmain.src="$ROOT/app/src/ai/openclaw/fterm/MainActivity.java" \
  -Dnotifier.src="$ROOT/app/src/ai/openclaw/fterm/Notifier.java" -cp "$OUT" NotifyTest
java -Droot="$ROOT" -cp "$OUT" com.termux.terminal.FocusTest
java -Droot="$ROOT" -cp "$OUT" KeyBarPrefsTest
java -cp "$OUT" ProfilesTest
java -cp "$OUT" ProfileTabTest
java -cp "$OUT" SearchTest
java -Dview.src="$ROOT/app/src/com.termux/view/TerminalView.java" -cp "$OUT" RetroTest
java -Dhangul.src="$ROOT/assets/fonts/PretendardHangul.ttf" \
     -Dlatin.src="$ROOT/assets/fonts/Iosevka.ttf" \
     -Dhome="$HOME" \
     -Dview.src="$ROOT/app/src/com.termux/view/TerminalView.java" \
     -cp "$OUT" FontCoverageTest
java -Dmain.src="$ROOT/app/src/ai/openclaw/fterm/MainActivity.java" \
     -Drenderer.src="$ROOT/app/src/com.termux/view/TerminalRenderer.java" \
     -cp "$OUT" FontZoomTest
java -cp "$OUT" com.termux.terminal.ModeRestoreTest
java -cp "$OUT" com.termux.terminal.ScrollbackTest

echo
echo "[3/3] 전송 계층 (데몬 없이, 가짜 데몬으로)"
bash "$ROOT/tests/run_transport_fake.sh"
