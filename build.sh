#!/data/data/com.termux/files/usr/bin/bash
# fterm APK 빌드: aapt2 -> javac -> d8 -> zipalign -> apksigner
# 이 기기(Termux)에서만 검증됨. gradle 없이 SDK build-tools만 쓴다.
set -e

ROOT=$(cd "$(dirname "$0")" && pwd)
# API 36 (Android 16) platform jar. The one at ~/build/android/android.jar is API 34 and
# lacks symbols added since, so it is only a fallback.
SDKJAR=${SDKJAR:-/data/data/com.termux/files/usr/tmp/android36.jar}
[ -f "$SDKJAR" ] || SDKJAR=$HOME/build/android/android.jar
KS=${KS:-$HOME/build/android/ks.jks}
KS_PASS=${KS_PASS:-openclaw}
OUT=$ROOT/build
PKG=ai.openclaw.fterm

MIN_SDK=26
TARGET_SDK=36

[ -f "$SDKJAR" ] || { echo "android.jar 없음: $SDKJAR" >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/obj" "$OUT/dex" "$OUT/assets"

echo "[1/6] 리소스 컴파일"
aapt2 compile --dir "$ROOT/res" -o "$OUT/compiled/res.zip"

echo "[2/6] 링크"
# 폰트는 assets/ 에서 읽는다. aapt2가 ttf를 리소스로 묶으면 압축이 안 되고 크기만 커진다.
# mterm의 폰트 스택: Iosevka(라틴) / Nerd Symbols(아이콘) / Pretendard(한글)
mkdir -p "$OUT/assets/fonts"
cp "$ROOT/assets/fonts/Iosevka.ttf" "$OUT/assets/fonts/Iosevka.ttf"
cp "$ROOT/assets/fonts/PretendardHangul.ttf" "$OUT/assets/fonts/PretendardHangul.ttf"
cp "$ROOT/assets/fonts/NerdSymbols.ttf" "$OUT/assets/fonts/NerdSymbols.ttf"
# 고지문도 APK 안에 넣는다. MIT·Apache-2.0·OFL 모두 "라이선스 사본을 배포물에 포함"을
# 요구하므로, 앱을 설치한 사람이 저장소 없이 읽을 수 있어야 한다. 사본은 build/ 안
# (gitignore, 그리고 이 스크립트가 매번 rm -rf)에만 두고 저장소 assets/ 는 건드리지 않는다.
mkdir -p "$OUT/assets/licenses"
cp "$ROOT/LICENSE" "$OUT/assets/LICENSE"
cp "$ROOT/NOTICE.md" "$OUT/assets/NOTICE.md"
cp "$ROOT/licenses/"*.txt "$OUT/assets/licenses/"
aapt2 link -o "$OUT/base.apk" -I "$SDKJAR" \
  --manifest "$ROOT/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version $MIN_SDK --target-sdk-version $TARGET_SDK \
  -A "$OUT/assets" \
  "$OUT/compiled/res.zip"

echo "[3/6] 컴파일"
find "$ROOT/app/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
# javac's status must survive the pipe. It used to be `javac ... | grep -v bootstrap || true`,
# which turned every compile error into a warning: javac still emits classes for the files it
# did compile, so MainActivity.class existed, the guard below passed, and a broken APK was
# packaged and signed as "BUILD OK". The bootstrap warning is filtered, the exit code is not.
set -o pipefail
javac -nowarn -source 11 -target 11 \
  -classpath "$SDKJAR" \
  -d "$OUT/obj" @"$OUT/sources.txt" 2>&1 | grep -v 'bootstrap class path'
set +o pipefail
[ -f "$OUT/obj/ai/openclaw/fterm/MainActivity.class" ] || { echo "컴파일 실패" >&2; exit 1; }

echo "[4/6] dex"
find "$OUT/obj" -name '*.class' > "$OUT/classes.txt"
d8 --lib "$SDKJAR" --min-api $MIN_SDK --release \
  --output "$OUT/dex" @"$OUT/classes.txt"

echo "[5/6] 패키징"
# classes.dex를 base.apk에 무압축으로 붙인다. 경로는 절대경로로 넘긴다 —
# 이전 버전은 cd 이후 상대경로를 써서 dex가 들어간 파일을 엉뚱한 곳에 만들었다.
cp "$OUT/base.apk" "$OUT/app.apk"
python3 - "$OUT/app.apk" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, 'a', zipfile.ZIP_STORED) as z:
    z.write(dex, 'classes.dex')
PY
python3 - "$OUT/app.apk" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    names = z.namelist()
assert 'classes.dex' in names, "classes.dex missing from the APK: %s" % names
# 라이선스 고지는 배포 요건이다 — assets 스테이징이 조용히 빠지면 여기서 멈춘다.
for want in ('assets/LICENSE', 'assets/NOTICE.md', 'assets/licenses/Apache-2.0.txt',
             'assets/licenses/OFL-1.1.txt', 'assets/licenses/MIT.txt'):
    assert want in names, "%s missing from the APK: %s" % (want, names)
print("  dex %d bytes, %d entries" % (z.getinfo('classes.dex').file_size, len(names)))
PY
zipalign -f 4 "$OUT/app.apk" "$OUT/app-aligned.apk"

echo "[6/6] 서명"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias gw -storepass "$KS_PASS" \
    -keypass "$KS_PASS" -dname "CN=fterm" -keyalg RSA -keysize 2048 \
    -validity 10000 >/dev/null 2>&1
fi
apksigner sign --ks "$KS" --ks-pass pass:"$KS_PASS" --key-pass pass:"$KS_PASS" \
  --out "$OUT/fterm.apk" "$OUT/app-aligned.apk"

ls -lh "$OUT/fterm.apk"
echo "BUILD OK: $OUT/fterm.apk"
