#!/data/data/com.termux/files/usr/bin/bash
# Robolectric 실행: 실제 MainActivity를 JVM에서 띄워 인플레이트를 검증한다.
# Android 기기 설치 없이 UI 트리가 만들어지는지 확인하는 것이 목적.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
RL=/data/data/com.termux/files/usr/tmp/rl
SDKJAR=${SDKJAR:-$HOME/build/android/android.jar}
OUT=/data/data/com.termux/files/usr/tmp/fterm-rl

[ -f "$RL/and.jar" ] || { echo "robolectric android-all 없음" >&2; exit 1; }

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/gen"

echo "[1/4] 리소스 링크 (R.java 재생성)"
aapt2 compile --dir "$ROOT/res" -o "$OUT/res.zip"
aapt2 link -o "$OUT/res.apk" -I "$SDKJAR" \
  --manifest "$ROOT/AndroidManifest.xml" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --java "$OUT/gen" "$OUT/res.zip"

echo "[2/4] 앱 + 테스트 컴파일"
CP="$SDKJAR:$RL/j/robolectric-4.18.jar:$RL/j/annotations-4.18.jar:$RL/j/junit-4.18.jar:$RL/j/shadows-framework-4.18.jar:$RL/j/utils-4.18.jar:$RL/j/resources-4.18.jar:$RL/j/sandbox-4.18.jar:$RL/j/pluginapi-4.18.jar:$RL/j/plugins-maven-dependency-resolver-4.18.jar:$RL/j/utils-reflector-4.18.jar:$RL/j/nativeruntime-4.18.jar:$RL/j/junit-4.13.2.jar:$RL/j/hamcrest-core-1.3.jar:$RL/j/guava-33.7.2-jre.jar"
find "$ROOT/app/src" "$OUT/gen" "$ROOT/tests/robolectric/src" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -source 11 -target 11 -classpath "$CP" -d "$OUT/classes" @"$OUT/sources.txt"

echo "[3/4] 런타임 classpath"
RUNCP="$OUT/classes:$OUT/gen:$SDKJAR:$RL/j/robolectric-4.18.jar:$RL/j/annotations-4.18.jar:$RL/j/junit-4.18.jar:$RL/j/shadows-framework-4.18.jar:$RL/j/utils-4.18.jar:$RL/j/resources-4.18.jar:$RL/j/sandbox-4.18.jar:$RL/j/pluginapi-4.18.jar:$RL/j/plugins-maven-dependency-resolver-4.18.jar:$RL/j/utils-reflector-4.18.jar:$RL/j/nativeruntime-4.18.jar:$RL/j/junit-4.13.2.jar:$RL/j/hamcrest-core-1.3.jar:$RL/j/guava-33.7.2-jre.jar:$RL/j/shadowapi-4.18.jar:$RL/j/javax.annotation-api-1.3.2.jar:$RL/j/asm-9.11.1.jar:$RL/j/asm-commons-9.11.1.jar:$RL/j/asm-tree-9.11.1.jar:$RL/j/asm-util-9.11.1.jar:$RL/j/asm-analysis-9.11.1.jar:$RL/j/icu4j-75.1.jar:$RL/j/javax.inject-1.jar:$RL/j/monitor/classes.jar"

echo "[4/4] 테스트"
java -Drobolectric.offline=true -Drobolectric.graphicsMode=LEGACY \
  -Drobolectric.dependency.dir="$RL" \
  -Drobolectric.nativeruntime.dir="$RL/nat" \
  -Dandroid-all.jar="$RL/and17.jar" \
  -Djava.library.path="$RL/nat/native/linux/aarch64" \
  -cp "$RUNCP" org.junit.runner.JUnitCore io.github.crashedsystem.terminal.MainActivityTest 2>&1 | tail -60
