#!/data/data/com.termux/files/usr/bin/bash
# fterm APK 빌드 — Gradle 로 넘긴다.
#
# 이 스크립트는 남겨 둔다. 오래 쓰던 이름이고, 새 폰 설치 스크립트와 문서가 이걸
# 부르기 때문이다. 실제 빌드 파이프라인은 build.gradle.kts 이고, 이 파일은 그 위에
# 세 가지를 얹는다: SDK 경로 자동 탐지, 도구 존재 확인, 그리고 실패했을 때 무엇이
# 없는지 한 줄로 알려 주는 일.
#
# 실제 단계(aapt2 compile/link, javac, d8, zipalign, apksigner)와 그 이유는
# build.gradle.kts 주석에 있다. 여기에 다시 적지 않는다 — 두 곳에 적으면 어긋난다.
#
#   ./build.sh                     build/fterm.apk
#   ./build.sh --clean             산출물을 지우고 처음부터
set -euo pipefail

ROOT=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT"

die() { echo "빌드 중단: $*" >&2; exit 1; }

need() {
  command -v "$1" >/dev/null 2>&1 || die "$1 이(가) 없습니다. $2"
}
need aapt2 "pkg install aapt2 (또는 build-tools)"
need d8 "pkg install d8"
need apksigner "pkg install apksigner"
need zipalign "pkg install zipalign"
need javac "pkg install openjdk-21"
need python3 "pkg install python"

# android.jar. Gradle 이 같은 규칙(가장 높은 platforms/android-N)으로 찾지만, 여기서
# 먼저 확인해 두면 래퍼가 도는 20초를 기다리지 않고 바로 알려 줄 수 있다.
SDKJAR=${SDKJAR:-}
if [ -z "$SDKJAR" ]; then
  for cand in \
      "$HOME/android-sdk"/platforms/android-*/android.jar \
      "$HOME/android-sdk"/android-*/android.jar \
      "${ANDROID_HOME:-}"/platforms/android-*/android.jar \
      "$HOME/build/android/android.jar"; do
    [ -f "$cand" ] && SDKJAR=$cand
  done
fi
[ -n "$SDKJAR" ] && [ -f "$SDKJAR" ] || die "android.jar 을 찾지 못했습니다.
  SDK를 받아 두십시오:  ~/android-sdk/android-36/android.jar
  또는 경로를 지정:      SDKJAR=/path/to/android.jar ./build.sh"

echo "android.jar: $SDKJAR"
echo "version:     $(grep -o 'versionName="[^"]*"' AndroidManifest.xml | cut -d'"' -f2)"

ARGS=(buildApk "-PandroidJar=$SDKJAR")
[ "${1:-}" = "--clean" ] && ARGS+=(--rerun-tasks)

exec ./gradlew --no-daemon "${ARGS[@]}"
