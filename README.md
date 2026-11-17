# fterm

Android 단독으로 쓰는 터미널. Termux의 셸을 그대로 물려 쓰고, 화면은 Windows Terminal을
닮게 만들었다 — 위에 탭 스트립, 아래에 키 바, 가운데 큰 다크 화면.

| | |
|---|---|
| 앱 | `build/fterm.apk` (2.0MB, `ai.openclaw.fterm`, targetSdk 36 / minSdk 26) |
| 데몬 | `termux/ftermd` (python3, Termux 안에서 실행) |
| 엔진 | Termux `terminal-emulator` + `terminal-view` (Apache-2.0) 벤더링 |
| 폰트 | mterm 스택: Iosevka(라틴) + Nerd Symbols(아이콘) + Pretendard(한글) |

## 왜 데몬이 필요한가

앱은 Termux의 bash를 직접 실행할 수 없다. `/data/data/com.termux/files/usr` 가 `0700` 이고
앱의 uid가 다르기 때문이다. 그래서 PTY는 Termux 안의 `ftermd` 가 소유하고, 앱은
`127.0.0.1:8791` 로 붙는다. 부수 효과가 오히려 장점이다: 앱을 닫아도 셸이 살아 있고,
다시 열면 화면이 그대로 복원된다 (`REPLAY`).

## 설치 (기기에서 직접)

1. Termux에서 데몬 설치:

```bash
cd ~/projects/fterm
bash termux/install.sh
```

출력 마지막 줄의 토큰을 기억한다.

2. APK 설치: `build/fterm.apk` 를 탭해서 설치 (이미 `/sdcard/Download/fterm.apk` 에 복사해 둠).

3. 앱 첫 실행 시 뜨는 창에 토큰을 붙여넣는다. 한 번만 하면 된다.

## 조작

| 동작 | 결과 |
|---|---|
| `+` | 새 탭 |
| 탭 길게 | 탭 닫기 (확인 후 셸 종료) |
| `+` 길게 | 설정 (글자 크기, 색상 구성, 서버 정보) |
| CTRL/ALT/SHIFT | 다음 키 한 번에만 적용 (sticky) |
| 터미널 길게 | 텍스트 선택 → 복사 |
| Ctrl+Shift+C / V | 하드웨어 키보드에서 복사/붙여넣기 |
| Ctrl+Shift+T / W | 새 탭 / 탭 닫기 |

색상은 Campbell(Windows Terminal 기본), One Dark, Solarized Dark, Dracula 4종.

## mterm에서 가져온 것

| 항목 | 원본 | 이 앱에서 |
|---|---|---|
| 폰트 스택 | `mterm/public/js/core/appearance.js` `FONT_STACK` = Iosevka → Nerd Symbols → Pretendard | `Fonts.java` — 코드포인트별 `Typeface` 선택. Iosevka `M` 0.500em, Pretendard `한` 0.864em이므로 2칸에 맞추려면 **115.71%** 필요 (mterm 상수 115.85%, 0.14p 차) — 앱은 실측 셀에서 매번 계산한다 |
| Miku 팔레트 | 같은 파일의 `ONE_HALF_DARK` | `Schemes.miku()` — 16개 ANSI 값을 그대로 옮김 (`SchemesTest`가 대조) |
| Miku 그림 | `mterm/public/img/miku.png` (CC BY-NC) | `assets/img/miku-dusk.png` — `#282c34`와 **미리 multiply**한 것. mterm의 `background-blend-mode: multiply`와 같은 결과이고, 런타임 블렌드가 필요 없다 |

`backgroundOpacity: 0.55`는 mterm에 **선언만 있고 소비처가 없다**(CSS·JS 어디서도 읽지 않는다). 실제 렌더는 불투명 multiply이므로 여기서도 그렇게 했다.

## 앱이 데몬을 스스로 띄운다 (keeper)

처음엔 Termux의 `RUN_COMMAND` 브로드캐스트로 `ftermd start`를 쏘려 했다. **이 기기에서는
조용히 무시된다** — 브로드캐스트는 `result=0`으로 끝나고 `RunCommandService`는 호출되지
않으며 파일도 생기지 않는다(실측: `am broadcast` 두 번, `am start-foreground-service` 한 번
모두 실패). Termux 0.118.3 + Android 17의 동작이다.

그래서 방향을 뒤집었다. 앱이 **공유 저장소에 요청 파일**을 쓰고, Termux 안의 작은 keeper
프로세스가 그것을 폴링해 데몬을 띄우고 토큰을 돌려준다:

```
/sdcard/Download/fterm-up            앱 → keeper   "데몬 올려라"
/sdcard/Download/fterm-token         keeper → 앱   토큰
/sdcard/Download/fterm-down          앱 → keeper   "데몬 내려라"
/sdcard/Download/fterm-keeper-alive  keeper 1초 heartbeat
```

검증(실기기): `pm clear` 로 토큰·설정을 지우고 데몬을 죽인 상태에서 앱을 열었더니
데몬이 뜨고 토큰이 발행됐다.

```
앱 실행 → keeper log "up: daemon=True"
           daemon: alive, port 8791
           /sdcard/Download/fterm-token = 861f4ee8…
```

`termux/install.sh`가 데몬·keeper를 설치하고 keeper를 띄운다. 사용자는 이것만 한 번 하면
이후로는 앱만 열면 된다.

## Windows Terminal 느낌

| WT | 이 앱 |
|---|---|
| 탭 스트립 + `+` + 드롭다운 캐럿 | 있음. `+` 길게 눌러도 드롭다운 |
| 드롭다운 메뉴(새 탭·이름 변경·닫기·붙여넣기·복사·설정) | 있음, 단축키 병기 |
| 명령 팔레트 `Ctrl+Shift+P` | 있음, 검색 가능 |
| 활성 탭에만 닫기 버튼 | 있음 |
| 둥근 상단 탭 plate | 있음, 활성 탭은 터미널 배경색과 동일 |
| `Ctrl+Shift+T/W/C/V` | 있음 |
| 분할 창 | **없음** (모바일이라 의도적으로 미구현) |

터미널 벨은 BEL을 진동/비프로 옮긴다 — 무음 모드에서는 진동만, 활성 탭만 울린다.
네이티브 알림은 포그라운드 서비스의 상시 알림 하나이고, 탭하면 앱으로 돌아온다.

## 셸이 띄우는 알림 — 네이티브, 플러그인 없음

opencode 같은 도구는 데스크톱에서 알림을 띄운다. Termux에서는 그게 `termux-notification`
(플러그인 + termux-api 패키지 + 프로세스 실행)을 거쳐야 했다. 여기서는 **이스케이프
시퀀스를 터미널이 직접 읽는다**:

```
ESC ] 9 ; message BEL                  (iTerm2 형식, 대부분의 CLI 도구)
ESC ] 777 ; notify ; title ; body BEL  (rxvt-unicode 형식)
```

`TerminalEmulator.doOsc`가 이 둘을 파싱해 `TerminalOutput.onNotification()`을 부르고,
앱이 Android 알림으로 띄운다. 셸은 추가 비용이 0이다 — 바이트는 이미 터미널로 가고 있다.

실제 파서로 검증:

```
ESC]9;빌드 완료BEL          → NOTIFY title=[] body=[빌드 완료]
ESC]9;doneESC\              → NOTIFY title=[] body=[done]
ESC]777;notify;테스트;3개 실패BEL → NOTIFY title=[테스트] body=[3개 실패]
ESC]2;그냥 제목BEL          → TITLE=그냥 제목   (알림 아님)
BEL                         → BELL            (알림 아님)
```

서비스 알림은 LOW(상태 표시), 셸 알림은 DEFAULT(알려야 하는 것) — 채널이 분리돼 있어
하나만 끌 수 있다. 같은 제목의 알림은 대체되고, 다른 제목은 쌓인다.

### opencode는 이미 이걸 내장하고 있다

바이너리(245 MB)를 청크로 훑어 확인한 사실 — 플러그인이 필요 없다:

```
[188215656]  ESC ] 777 ; notify ;        알림 형식
[188215973]  ESC ] 9 ;                   iTerm2 형식
[223678744]  ESC [J ESC ] 9 ; 4 ; 0      진행률 형식
```

**`ESC]9;4;`는 알림이 아니라 진행률**이다(ConEmu/Windows Terminal 규격). opencode가 이걸
쏘기 때문에, 모든 OSC 9을 알림으로 처리하면 진행률 업데이트마다 알림이 뜬다. 파서가
`9;4;`를 구분해 버린다 — `NotifyTest`가 이 경우를 고정한다.

기존 `windows-notify` 플러그인(SSH로 Windows 토스트 + `termux-notification`)은
`plugins.disabled/`로 옮겨 비활성화했다. 되돌리려면 그 폴더를 `plugins/`로 되돌린다.

### 알림이 "안 오는" 진짜 원인: 포커스 게이트 (DECSET 1004)

opencode의 알림은 무조건 쏘는 게 아니었다. 바이너리의 attention 코드를 해독하면:

```js
let r = "unknown";                       // 포커스 상태
t.renderer.on("focus", () => r = "focused");
t.renderer.on("blur",  () => r = "blurred");
// when: "blurred" (기본) — 화면 밖일 때만 알림
let m = LW(h?.when ?? "blurred", r);     // r이 "unknown"이면 "focus_unknown"
if (!_ && !R) { if (E && m) return skip(m); }  // ← 스킵
```

blur/focus는 **터미널이 보내는 포커스 리포트**(DECSET 1004 → `CSI I`/`CSI O`)로만 알 수 있다.
fterm 에뮬레이터는 DECRQM에 "1004 = set"이라 답하면서(로그에 `ESC[?1004;2$y` 질의 기록)
실제 리포트는 안 보냈다("NOT implemented" 주석). opencode는 포커스가 영원히 "unknown" →
**알림 영구 스킵**. OSC 파서는 멀쩡했는데 그 위의 게이트가 안 열린 것.

수정: 에뮬레이터가 1004 켜짐 시 `CSI I`/`CSI O`를 내보낸다. 뷰의
`onWindowFocusChanged`(윈도우 포커스 — "사용자가 앱을 보고 있는가")가 구동하고,
세션 부착 시 현재 상태를 즉시 알려준다. `FocusTest` 6개가 고정한다.

### 알림이 폰에 도달하는 3단계 — 각각 다른 막힘

opencode 턴 완료 → 폰 알림은 세 단계를 거친다. 전부 다른 이유로 막혀 있었다.

1. **opencode가 알림을 만드는가** — `attention.notifications` 기본값 OFF.
   바이너리 스키마에 `default:!1`로 명시. `~/.config/opencode/cli.json`에서 ON.
2. **"화면 밖"이라고 판정하는가** — 알림의 기본 조건이 blurred. 포커스는 터미널의
   DECSET 1004 리포트로만 알 수 있는데 fterm이 안 보냈다. 구현해 해결(위 참조).
3. **만든 알림이 폰에 도달하는가** — 근본 문제. opencode의 알림은 데스크톱 전용
   (DBus/Windows 토스트/macOS 알림센터). 안드로이드 터미널에는 그 시스템이 없어
   **설정을 켜도 폰 알림은 안 온다.** 해결: `fterm-notify` 플러그인이 턴 완료/실패/
   권한 요청 시 OSC 9을 출력하고, fterm이 그것을 파싱해 안드로이드 알림을 올린다.
   (fterm 파서 쪽은 dumpsys로 실측 검증: id=2602, "FTERM-NOTIFY-TEST" 도착.)

**2026-11-17 최종 실측 — 체인 전체가 동작한다.** opencode 턴 종료 →
`strace`로 TUI의 `write(pts, "\x1b]9;fterm · 완료\x07")` 2회 기록 → fterm 파서 →
사용자 보고: "알림 왔다요". 끝부터 끝까지 실측으로 연결됐다.

플러그인은 windows-notify의 검증된 구조를 따른다: `index.js`(server, 빈 setup) +
`tui.js`(터미널 소유 프로세스, OSC 출력) + package.json exports에 `./tui` 슬롯.
subagent 세션은 알림에서 제외. `TERM_PROGRAM=fterm`이 아니면 이스케이프를 쓰지
않아 다른 터미널의 스크롤백을 오염시키지 않는다.

### 완전 내림 후 복구 불가의 두 원인

1. **keeper 사망** — 하트비트가 23분 뒤인데 프로세스는 없었다. 앱이 데몬을 되살리는
   유일한 손이 keeper인데, 그게 죽으면 앱은 어쩔 수 없다. `install.sh`가 `~/.bashrc`에
   훅을 넣어 Termux가 열릴 때마다 keeper를 되살린다(멱등).
2. **토큰이 있으면 프로비저닝을 안 함** — `startSessions()`는 토큰이 *없을 때만*
   provisioning을 돌렸다. 토큰이 남아 있고 데몬만 죽었으면 오류 다이얼로그에서 끝났다.
   이제 연결 실패 시 provisioning을 다시 돌린다. keeper는 같은 토큰을 다시 발행하므로
   재시도가 그대로 연결된다.

### rish가 조용히 죽는 원인: BOOTCLASSPATH (Shizuku 공통 버그)

**증상**: `rish -c 'id'`가 rc=0, 출력 0바이트로 즉사(0.15초). Shizuku 서버가 살아 있어도 동일.
"Shizuku가 죽었다"로 오인하기 쉽지만 아니다.

**원인**: `app_process`는 안드로이드 앱 안에서 실행될 때 시스템이 `BOOTCLASSPATH`,
`ANDROID_ART_ROOT`, `ANDROID_I18N_ROOT`, `ANDROID_TZDATA_ROOT`, `DEX2OATBOOTCLASSPATH`를
환경에 넣어주지만, **일반 셸(fterm 데몬, ssh, cron)에서는 이 변수들이 없다.** 변수가
없으면 ART가 부트 클래스패스를 못 만들어 RuntimeInit 로그 한 줄만 남기고 exit(0)한다.
에러가 stdout/stderr로 안 나오고 logcat으로만 나오므로 "조용히 죽는 것"처럼 보인다.

**재현/검증**:
```bash
# 실패 (출력 없음, rc=0)
ANDROID_DATA=/data ANDROID_ROOT=/system rish -c id

# 성공 — Termux 앱 셸이 물려받은 BOOTCLASSPATH를 넣으면
BCP=$(tr '\0' '\n' < /proc/<termux-shell-pid>/environ | grep '^BOOTCLASSPATH=' | cut -d= -f2-)
export BOOTCLASSPATH="$BCP"
export ANDROID_ART_ROOT=/apex/com.android.art
export ANDROID_I18N_ROOT=/apex/com.android.i18n
export ANDROID_TZDATA_ROOT=/apex/com.android.tzdata
export DEX2OATBOOTCLASSPATH=...   # BOOTCLASSPATH와 같은 값의 DEX2OAT variant
rish -c id                        # → uid=2000(shell)
```

**해결**: `~/bin/rish2` 래퍼가 이 변수들을 채워 실행한다. fterm 데몬 환경에서도 동작.
Shizuku 이슈 트래커의 "rish returns nothing" 보고 다수가 이것으로 보인다.

### 데몬은 파일 교체로 갱신되지 않는다

`termux/install.sh`가 데몬을 재시작하는 이유: 실행 중인 프로세스는 디스크의 새 코드를
읽지 않는다. `TERM_PROGRAM`도 select() 크래시 수정도 **재시작 후에만** 적용된다.

## 유휴 사망 — 두 개의 원인, 둘 다 실측

증상은 "유휴 상태되면 터미널 죽음"이었다. 원인이 **둘**이었고, 앞의 것만 고쳐서는
계속 죽었다.

### 1. 앱 프로세스가 동결됨 (Android 12+ freezer)

`dumpsys activity processes`를 배경 상태에서 읽었다:

```
isFrozen=true   curProcState=16 (CACHED_EMPTY)
```

동결된 프로세스는 소켓을 읽지 않는다. 제가 넣은 핑·타임아웃은 **죽는 것을 알아채게** 할
뿐 **죽는 것을 막지 못한다**. 해결은 플랫폼의 정답인 포그라운드 서비스다:

```
TerminalService.startForeground()  →  curProcState=4, isFrozen=false   (수정 후 실측)
```

Termux 자신이 같은 이유로 같은 것을 쓴다.

### 2. 데몬이 스스로 크래시 (진짜 원인)

앱을 고친 뒤에도 죽었다. `~/fterm/daemon.log`:

```
[ftermd] client idle for 120s, dropped
Traceback ... select.select(rfds, [], [], 1.0)
ValueError: file descriptor cannot be a negative integer (-1)
```

`drop_idle()`이 **select용 fd 집합을 만든 뒤에** 소켓을 닫고 있었다. 닫힌 소켓은 fd `-1`이
되어 select()가 예외를 던지고, 데몬이 종료되면서 **모든 살아있는 셸을 함께 데려갔다**.

수정: `drop_idle()`을 fd 집합 구성 **전**으로 옮기고, 닫힌 fd는 집합에 넣지 않으며,
select 오류는 루프를 죽이지 않게 했다.

### 검증 (실기기)

```
30초 유휴  → curProcState=4, isFrozen=false
150초 유휴 (데몬 timeout 120초 초과) → daemon_alive=True × 8회
유휴 후 입력 → "echo IDLE-OK-$((7*6))"  →  화면에 IDLE-OK-42
```

`tests/jvm/harness/IdleDropTest.java`가 회귀를 막는다: 20회 급절단 접속 + 20초 유휴 +
소스 순서 검사(select 전에 drop).

## 이전 수정 (핑/타임아웃) — 여전히 유효하지만 충분하지 않았다

기기에서 "유휴 아니면 프리징"이 보고됐고, `IdleTest`가 20초 유휴에서 세션이 죽는 것을
재현했다. 로그가 원인을 짚었다: 클라이언트가 **15.0초**에 끊겼다.

```
SO_TIMEOUT_MS = 15000   (읽기 한도)
IDLE_PING_MS  = 8000    (8초 자고 "8초 이상 조용하면 핑" 검사)
```

8초 자고 나서 "8초 넘게 조용했나"를 물으면 경계에 정확히 걸려 **핑을 건너뛰고**, 다음
기회가 16초 — 읽기 한도(15초)보다 1초 늦다. 그래서 매번 15초 만에 죽었다.

수정: 핑을 **무조건 5초마다** 보내고 읽기 한도를 20초로. 핑 두 번을 잃어도 살아남는다.
데몬 쪽은 120초 무응답 클라이언트를 ghost로 보고 버린다(동결된 앱이 세션을 붙잡지 않게).

## 최신 안드로이드 대응 (targetSdk 36)

| 항목 | 처리 |
|---|---|
| API 35+ 강제 edge-to-edge | `EdgeToEdge` — 상태바/내비바/키보드 인셋을 탭 스트립·키 바 **높이**에 더한다 (패딩만 주면 행이 0높이가 된다) |
| `setStatusBarColor` 무시됨 (35+) | 인셋으로 처리, 34 이하만 색 지정 |
| 예측형 백 (33+) | `OnBackInvokedCallback` 등록 — `onBackPressed()`만으론 "뒤로=ESC"가 죽는다 |
| 600dpi 화면 | 글자 크기를 **dp**로 모델링 (`12dp` → 45px). 이전엔 14px=3.7dp라 안 보였다 |
| 한글 폰트 | `assets/D2Coding.ttf` 실제 로드 (이전엔 assets에만 있고 안 썼다) |
| sticky CTRL 누수 | `onCodePoint`에서 한 글자 뒤 해제 — 이전엔 `abc`가 `^B^C^H`로 갔다 |

## 검증 상태

실제 기기에서 설치·실행·렌더링·영문 입력·셸 연결을 확인했다. 한글 IME는 아래 참조. 이 기기에서 `pm install` 이
`SecurityException: INTERACT_ACROSS_USERS_FULL` 로 막혀 있어 설치 자체를 못 했다.
대신 다음을 실제로 돌려 통과시켰다.

| 검증 | 방법 | 결과 |
|---|---|---|
| 데몬 프로토콜 6종 (인증 거부, 에코, 한글, resize, replay, close) | `tests/run_all.sh` 의 python 파트 | 통과 |
| Java `SocketTransport` 9종 (토큰 오류, 왕복, 한글, resize, pid, replay, close, 세션 분리) | 실제 `ftermd` 에 JVM으로 접속 | 통과 |
| `DaemonClient` SYNC 5종 (빈 데몬, 목록, 토큰 오류, 포트 거부, 종료 후 제거) | 같음 | 통과 |
| 폭 계산 15종 (한글 음절 2칸, 자모, emoji, 결합문자, zero-width) | `tests/run_core.sh` | 통과 |
| **ANSI 파서 23종** (한글 2칸 점유, 커서 위치, 자동 줄바꿈이 음절을 쪼개지 않음, SGR, OSC 제목, resize, 패킷 분할 UTF-8) | 실제 `TerminalEmulator` | 통과 |
| APK 빌드·서명 | `./build.sh` | 통과 (V3 서명, targetSdk 36) |
| 붙여넣기 12종 (bracketed paste `\e[200~..\e[201~`, ESC 주입 차단, 모드 해제) | `tests/jvm/core/PasteTest.java` | 통과 |
| 색상 147종 (mterm `ONE_HALF_DARK` 대조, OLED 순검정, 전 테마 4.5:1) | `tests/jvm/core/SchemesTest.java` | 통과 |
| 폰트 라우팅 23종 (한글/아이콘/라틴, 상수 115.85) | `tests/jvm/core/FontsTest.java` | 통과 |
| 탭 닫기 9종 (plate 제거, transport 종료, Enter 경로) | `tests/jvm/core/TabCloseTest.java` | 통과 |
| 알림 17종 (OSC 9 / 777 파싱, 제목·본문, 오탐 방지, 앱 배선) | `tests/jvm/core/NotifyTest.java` | 통과 |
| 벨 13종 (VIBRATE 권한, 진동/비프, 활성 탭만, 설정 토글) | `tests/jvm/core/BellTest.java` | 통과 |
| 기기 설치·실행·렌더링·셸 연결 | `rish`로 `pm install` 후 화면 확인 | 통과 |
| 기기 영문 입력 (`abc` → `a`,`b`,`c`) | daemon 로그 바이트 확인 | 통과 |
| 유휴/프리징 3종 (20초 유휴 후 세션 생존·사용 가능, 데몬이 클라이언트 유지, 끊긴 링크 즉시 종료) | `tests/jvm/harness/IdleTest.java` | 통과 |
| 데몬 크래시 회귀 5종 (20회 급절단, 세션 생존, select 전 drop 순서) | `tests/jvm/harness/IdleDropTest.java` | 통과 |
| 구조 14종 (MainActivity 1264줄 상한, KeyBar/SettingsSheet 분리, 서비스·벨·keeper) | `tests/jvm/core/StructureTest.java` | 통과 |
| UI 19종 (드롭다운·팔레트·이름 고정·테마 색·Enter) | `tests/jvm/core/UiTest.java` | 통과 |
| 한글 IME 조합 16종 (ㅎ→하→한, 성장/축소, commit 꼬리, delete, 소스 대조) | `tests/jvm/core/ImeComposeTest.java` | 통과 |

### 한글 입력 — termlib 방식

ConnectBot이 같은 문제를 푼 방식([connectbot#2352](https://github.com/connectbot/connectbot/issues/2352),
[termlib `ImeInputView`](https://github.com/connectbot/termlib))을 그대로 옮겼다.

핵심 세 가지, 모두 소스에 주석으로 남겼다:

1. **조합을 터미널에 투영한다.** IME는 키 입력마다 조합 **전체**를 보낸다("하" → "한").
   터미널에는 조합 영역이 없으므로 늘어난 꼬리만 write하고, 줄어든 만큼 DEL을 보낸다.
2. **`finishComposingText`는 다시 보내지 않는다.** 이미 화면에 있으므로 중복이 된다 —
   원본 Termux가 `getEditable()`을 통째로 보내던 것이 자모 분리의 원인이었다.
3. **`deleteSurroundingText`는 `sendKeyEvent`를 쓰지 않는다.** 키 이벤트는 IME의 editable을
   비워서, 조합 중인 음절을 중간에 버리게 만든다. DEL 바이트를 직접 쓴다.

`tests/jvm/core/ImeComposeTest.java`(16)가 이 규약을 검증하고, `TerminalView.java`가 그
로직에서 벗어나지 않았는지 소스까지 대조한다.

## 구조

```
app/src/ai/openclaw/fterm/    MainActivity, Prefs, Schemes
app/src/com.termux/terminal/  벤더링 + SessionTransport / SocketTransport / DaemonClient
app/src/com.termux/view/      벤더링 TerminalView / TerminalRenderer
termux/ftermd                 PTY 소유 데몬 (토큰, ring replay, 세션 상한 16)
tests/                        프로토콜, 전송, 파서, 폭 테스트
```

벤더링한 코드에서 고친 곳은 세 군데뿐이다: `androidx.annotation` 의존 제거,
`TerminalBitmap` 의 `VANILLA_ICE_CREAM` 상수를 숫자 35로, 그리고 `JNI` PTY 경로와 나란히
`SessionTransport` 분기를 넣은 `TerminalSession`. 나머지는 Termux 원본 그대로다.

## 한글이 왜 정확한가

`WcWidth` 는 U+1100–115F(자모)와 U+AC00–D7A3(음절)을 2칸으로 계산한다. 렌더러는 측정된
글자 폭이 `wcwidth` 기대값과 다르면 그 코드포인트만 가로로 늘려 그리므로, 한글 글꼴의
전각 advance와 라틴 셀 폭이 어긋나도 격자가 밀리지 않는다. IME는 `commitText` 로 완성형
음절을 한 번에 보내고, `TerminalEmulator` 는 UTF-8 시퀀스가 패킷 경계에서 끊겨도
버퍼링한다(테스트 8번).

## 데몬 명령

```bash
ftermd start | stop | status | token | reset-token
```

환경 변수: `FTERM_PORT`(8791), `FTERM_SHELL`, `FTERM_RUNDIR`(`~/.fterm`),
`FTERM_HISTORY_BYTES`(256KiB).
