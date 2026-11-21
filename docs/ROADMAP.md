# 로드맵 — 실제로 걸리는 것부터 순서대로

`work/fterm-code-review.md`(개정판)에서 확인한 문제 아홉 개를 **의존 순서대로** 적었습니다. 순서가
중요한 이유는 하나입니다. 1~3은 데몬 바깥에서 고칠 수 없고, 4~6은 데몬이 안정된 뒤에야 재현과
검증이 가능하며, 7~9는 앞의 것들이 만든 코드를 다시 정리하는 일입니다. 뒤에서 먼저 손대면 앞의
수정이 지나간 자리를 다시 뜯게 됩니다.

각 항목은 **증상 → 원인 → 고치는 방법 → 검증** 순서입니다. 검증을 못 붙이는 항목은 그 항목을
닫지 않습니다.

| 단계 | 항목 | 무엇 | 상태 |
|---|---|---|---|
| 1 | CI와 데몬 명세 | GitHub Actions에서 Python·Rust·Java·프로토콜을 돌린다 | **완료** |
| 2 | Rust `ftermd-rs` | 프로토콜 호환 데몬을 Rust로 | **완료** |
| 3 | 프로토콜 호환 검증 | 같은 테스트가 두 데몬을 판정 | **완료** |
| 4 | 고아 셸 | `S_ERROR` 뒤 `C_CLOSE` 누락 | **완료** |
| 5 | FGS 타입 | `dataSync` → `specialUse` | **완료** |
| 6 | UI·알림 정리 | 동기 디코딩, 무음 무시, 누락된 정리 | **완료** |
| 7 | 프로토콜 중복 | 3중 프레이밍, 거대 클래스 분리 | **완료** |
| 8 | 업스트림 기준점 | 벤더 파일의 원본 커밋 고정 | **완료** |
| 9 | 스크롤백 상한 | `onTrimMemory` | **완료** |

1–6, 8, 9는 끝났습니다. 7은 프로토콜 중복이 끝나고 클래스 분리도 `DaemonBoot`·`TabMemory`,
그리고 `SettingsSheet` 를 기능별 열두 파일로 나누는 데까지 끝났습니다. 무엇이 그것을 증명하는지는
각 절 끝의 "검증"에 있고, 러너가 확인하지 못하는 것은 맨 아래 "기기에서만 확인되는 것"에 남아
있습니다.

---

## 1. CI — 지금 아무것도 자동으로 검사되지 않는다

**증상**: `README.md`(지금은 [`docs/ARCHITECTURE.md`](ARCHITECTURE.md))의 검증 표는 전부 손으로 돌린 결과입니다. Rust 데몬을 붙이는 순간 "무엇이
여전히 맞는지"를 사람이 기억해야 합니다.

**고치는 방법**: `.github/workflows/ci.yml`에 잡을 둡니다.

- `rust` — `cargo test`, 그리고 데몬을 띄워 `termux/test_client.py` + `tests/test_modes.py` +
  `tests/test_daemon_hardening.py`. (처음에는 python 잡과 rust 잡이 나뒀고, Rust 이식이 같은
  스위트를 통과한 뒤 python 잡은 지웠습니다 — 파이썬 데몬도 함께 지워졌습니다.)
- `java` — `tests/run_core.sh`(ANSI 파서·폭·팔레트·모드 복원). `FTERM_TMPDIR`만 CI용으로 넘깁니다.
- 전부 `ubuntu-latest`, 의존성 설치 없이. 필요한 것은 rust와 JDK뿐입니다(스위트 구동에 쓰는
  `python3`은 러너에 기본 설치돼 있습니다).

**검증**: PR의 체크가 초록인 것 자체가 검증입니다. CI가 초록이 아니면 PR은 열린 채로 둡니다.

**한계**: `install.sh`, 진짜 Android PTY, `termux-api`, 폰트 선택 UI는 러너에서 재현할 수
없습니다. 그 목록은 아래 "기기에서만 확인되는 것"에 남깁니다. 그리고 `java` 잡은
`tests/run_core.sh`(터미널 코어·전송 계층)만 돌립니다 — `app/` 전체를 컴파일하려면
`android.jar` 와 `aapt2` 가 필요한데, 러너에 그걸 받아 오는 단계를 넣으면 CI 가 "의존성 없는
검사"라는 성질을 잃습니다. 대신 앱 코드는 API 35 `android.jar` 로 로컬 타입체크를 했고,
APK 빌드 자체는 지금도 기기에서만 확인됩니다.

## 2. Rust `ftermd-rs` — 데몬을 Rust로

**증상**: 세 가지가 실사용에서 걸립니다.

1. **낡은 pidfile** — `os.kill(pid, 0)`만 보므로, 데몬이 비정상 종료된 뒤 같은 PID를 다른
   프로세스가 받으면 `already running`을 찍고 즉시 끝납니다. 앱은 계속 연결에 실패합니다.
2. **단일 `select()` 루프의 블로킹** — 모든 탭이 한 루프를 공유하는데 `os.write`(PTY 입력이
   차면 멈춤), `redraw()`의 `sleep(0.05)`, `send_font()`의 최대 32MB 통짜 읽기가 그 루프를
   붙잡습니다. 한 탭이 막히면 모든 탭이 멈춥니다.
3. **백프레셔 없음** — 앱이 소켓을 안 읽는 동안 `cat`이 돌면 `Client.outbuf`가 상한 없이
   커져 데몬이 OOM으로 죽고, **모든 탭의 셸이 같이** 죽습니다.

**고치는 방법**: `termux/ftermd-rs/`에 tokio + libc만 쓰는 데몬을 둡니다.

- **pidfile** — `~/.fterm/ftermd.lock`에 `flock(LOCK_EX|LOCK_NB)`를 잡고 데몬 수명 동안 놓지
  않습니다. 잠금은 커널이 소유하므로 비정상 종료 뒤에도 저절로 풀립니다. `status`/`stop`은
  `/proc/<pid>/cmdline`이 이 데몬인지 확인하고, 아니면 낡은 pidfile로 보고 지웁니다.
- **블로킹 분리** — 세션마다 PTY 읽기 스레드와 쓰기 스레드를 두고, 그 사이는 유한 채널입니다.
  `redraw()`의 기다림은 `tokio::time::sleep`이라 런타임 스레드를 잡지 않습니다. 폰트는
  청크로 흘려보냅니다.
- **백프레셔** — 클라이언트마다 큐에 쌓인 바이트를 세고 상한(4 MiB)을 넘으면 **그 클라이언트만**
  끊습니다. 세션과 ring은 그대로 살아 있고, 다시 붙으면 replay로 복구됩니다.

프로토콜은 한 바이트도 바꾸지 않습니다. 상수·프레이밍·`E_*` 코드·`S_ERROR`/`C_CLOSE` 동작,
`ART` 환경 상속과 캐시, `FTERM_HISTORY_BYTES`, `FTERM_FONT_DIRS`가 가리키는 글꼴 경로, 세션 16개
한도, ring 256KiB, `Modes`의 복원 순서가 전부 같아야 합니다. 앱은 어느 데몬에 붙었는지
알아채지 못해야 합니다.

**설치**: `install.sh` 는 `~/fterm/ftermd` 자리에 Rust 바이너리를 놓습니다. 파이썬 데몬은
Rust 이식이 같은 스위트를 통과한 뒤 삭제됐고, 기본값도 rust 입니다.
두 데몬의 명령줄·출력·종료 코드가 같았으므로 앱도 keeper 도 스크립트도 바뀌지 않았습니다.

**검증**: `cargo test` 58종(모드 추적기·ring·프레이밍·pidfile·백프레셔·환경·글꼴),
`tests/test_daemon_hardening.py`(낡은 pidfile·느린 클라이언트·막힌 탭), 그리고 3단계의
프로토콜 스위트를 Rust 바이너리로.

## 3. 프로토콜 호환 — 두 구현을 같은 잣대로

**증상**: 2단계의 "호환된다"는 말은 검증이 없으면 주장일 뿐입니다.

**고치는 방법**: `tests/test_modes.py`(실제 데몬에 붙어 선언 분할·망각·복원 순서를
확인)과 `termux/test_client.py`(인증·에코·한글·resize·replay·close·OPEN 문자열·모드 복원)를
데몬 바이너리에 돌립니다. `tests/run_daemon.sh`가 그 실행기입니다. 이식 당시에는 Python
데몬과 Rust 데몬 양쪽에 같은 스위트를 돌려 동일성을 증명했고, 증명 뒤 파이썬 구현은
삭제했습니다. 지금은 Rust 바이너리 하나를 심사합니다.

**검증**: 같은 출력, 같은 통과 수. 하나라도 다르면 그 차이가 곧 버그입니다.

## 4. 고아 셸 — 에러로 끊긴 세션이 데몬에 남는다

**증상**: `SocketTransport.finishIfRunning()`은 `!isRunning()`이면 `C_CLOSE`를 보내지 않습니다.
`S_ERROR`로 끊긴 세션은 데몬 쪽에 셸째 남아 세션 16개 한도를 채웁니다.

**고치는 방법**: `SocketTransport` 가 `OPENED` 를 받은 순간부터 "이 세션의 셸은 데몬에 있다"고
기억하고, 끝나는 세 갈래(`S_ERROR`, 재시도 한도 초과, 링크가 없을 때의 탭 닫기)에서 한 번만
`C_CLOSE` 를 보냅니다. 링크가 없으면 짧은 연결을 하나 열어 HELLO 와 CLOSE 를 함께 보냅니다 —
데몬은 `S_ERROR` 뒤에 연결을 닫아 버리므로 같은 소켓으로는 전달되지 않습니다.

**검증**: `tests/jvm/harness/OrphanCloseTest.java`. 데몬 없이, 소켓 위에서 프로토콜만 흉내 내는
가짜 데몬을 씁니다(HELLO → OPENED → ERROR → 연결 끊기, 그다음 연결에 CLOSE 가 오는지). 이 검사는
`tests/run_core.sh` 의 3단계로 CI 에서 돕니다. 수정을 되돌리면 실패하는 것도 확인했습니다.

## 5. FGS 타입 — `dataSync`의 6시간 상한

**증상**: Android 15+에서 `dataSync` 포그라운드 서비스는 24시간 내 6시간이 지나면 시스템이
제한할 수 있습니다. 유휴 세션 유지가 앱의 존재 이유이므로 여기서 걸리면 제품이 멈춥니다.

**고치는 방법**: `AndroidManifest.xml`의 타입을 `specialUse` 로, 권한을
`FOREGROUND_SERVICE_SPECIAL_USE` 로 바꾸고 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 에 사유를 적습니다.
`TerminalService` 는 API 34+ 에서 타입을 인자로 넘기는 `startForeground` 를 씁니다(상수는 리터럴로
적어 옛 android.jar 로도 컴파일됩니다).

**검증**: 매니페스트·서비스 소스 검사(`BellTest`, `StructureTest` 의 51종 구조 검사). 타입이 실제로
6시간 상한을 피하는지는 **기기에서만** 확인됩니다: `dumpsys activity services | grep -A2 fterm`.

## 6. UI·알림 정리

- `ClipboardImage.save()`를 클릭 핸들러에서 동기 호출 → `pasteIfImage()` + `saveAsync()` 로
  작업 스레드에서 저장하고 완료 후 경로를 붙여넣습니다.
- `Notifier`가 무음 모드·알림 빈도를 보지 않고 무조건 진동 → `RINGER_MODE_SILENT` 에서는 울리지
  않고, `VIBRATE_GAP_MS`(2초) 안의 두 번째 알림은 진동하지 않습니다. 음소거는 "하지 말라"는
  뜻이고, 진동 모드는 "이걸로 하라"는 뜻이라 진동 모드는 그대로 둡니다.
- `onDestroy`에서 `Bell.release()` 를 부릅니다(ToneGenerator 는 오디오 트랙을 계속 쥡니다).
- `DaemonLauncher.requestStart()` 의 반환값을 봅니다. 요청 파일을 쓰지 못하면 keeper 는 영영
  듣지 못하므로, 15번 폴링한 뒤 "keeper 가 없다"고 잘못 말하는 대신 즉시 저장소 문제로 알립니다.
  그 상태기계는 `DaemonBoot` 로 옮겨 `MainActivity` 를 1,396줄로 유지했습니다(구조 검사의 상한
  1,420줄).

**검증**: `PasteTest`(이미지 경로가 워커 스레드를 지나는지), `NotifyTest`(무음·빈도), `PasteTest`·
`StructureTest`(onDestroy·DaemonBoot). 무음 모드에서 실제로 진동하지 않는지는 기기 확인 항목입니다.

## 7. 프로토콜 3중 구현과 거대 클래스

**프로토콜**: `app/src/com.termux/terminal/Protocol.java` 하나에 상수·프레이밍·HELLO 를 모으고
`SocketTransport`·`DaemonClient` 의 사본을 지웠습니다. Rust 데몬까지 넣으면 구현이 넷이므로,
Java 끼리 코드를 공유하는 것만으로는 부족합니다 — `tests/test_protocol.py` 가 파이썬 데몬·
Rust 데몬·`Protocol.java` 에서 상수 표를 읽어 **값이 다르면 실패**합니다. 한 곳에 프레임 타입을
더하고 다른 곳에 안 더하면 그 증상은 크래시가 아니라 "아무 일도 일어나지 않는 탭"이기 때문에,
이 검사가 유일한 방어입니다.

**클래스**: `MainActivity` 에서 데몬 준비 상태기계를 `DaemonBoot`, 메모리 정책을 `TabMemory` 로
빼 1,406줄입니다(구조 검사 상한 1,420). `SettingsSheet`(1,985줄)는 기능별 열두 파일로 나눴습니다
— 셸(라우터) `SettingsSheet` 315줄, 공용 팔레트·행 모양 `SettingsUi` 300, 페이지 기반
`SettingsPage` 44, 페이지 일곱 개(`SettingsRootPage` 77 · `SettingsStartPage` 55 ·
`SettingsProfilesPage` 275 · `SettingsAppearancePage` 189 · `SettingsSchemesPage` 256 ·
`SettingsInteractionPage` 30 · `SettingsAboutPage` 27), 둘 이상의 페이지가 함께 쓰는
`SettingsFontPicker` 541 · `SettingsIconPicker` 172.

동작과 시그니처는 그대로입니다. 근거는 두 가지입니다. 분리 전후로 문자열 리터럴이 **293개로
같고 multiset 도 같으며**(사용자에게 보이는 문구가 하나도 빠지지 않았다), Android API 호출
개수도 항목별로 같습니다 — `addView` 146, `new LinearLayout` 43, `new TextView` 39,
`setPadding` 44, `AlertDialog.Builder` 14, `Chrome.icon` 14, `UiFont.apply` 11. 검사가 읽는
파일도 한 파일에서 표면 전체(`Settings*.java`)로 넓혔습니다(`tests/jvm/core/SettingsSources.java`):
검사가 주장하는 것은 "그 설정이 설정 화면에 있다"이지 "그 줄이 이 파일에 있다"가 아닙니다.

**검증**: `tests/test_protocol.py`(상수 4중 일치), `StructureTest`(한 곳에만 있다 · 셸 400줄
상한 · 열두 파일 각 600줄 상한 — 구조 74종), `tests/run_core.sh`(리팩터링 뒤에도 전부 통과),
API 35 `android.jar` 로컬 타입체크(`app/src` + `R.java` 스텁 73파일, 오류 0). APK 빌드는 기기 확인
항목입니다.

## 8. 업스트림 기준점

`com.termux` 파일이 어느 업스트림 커밋에서 왔는지 기록이 없었습니다.

**기준점**: `termux/termux-app` master `8629e632fcb95da272221be327db653fb24befe9`. 근거는 바이트
단위 일치입니다 — 16개 파일이 이 커밋과 한 바이트도 다르지 않고, 특히 `TerminalSixel.java`·
`ITermImage.java`·`AndroidUtils.java` 는 **v0.118.3 태그에는 없고 이 커밋에는 있는** 파일이라
"기기의 Termux 0.118.3 에서 땄다"는 추측이 틀렸다는 것도 함께 드러났습니다.

`docs/UPSTREAM.md` 에 파일별 상태(동일 16 · 수정 10 · 이 저장소 4)를 적었고,
`tools/upstream-diff.sh [commit]` 로 언제든 다시 뽑습니다.

**검증**: `StructureTest` 가 문서에 40자 해시와 벤더 파일 목록이 있는지, 스크립트가 비교를
재현하는지 확인합니다. 표를 낡게 두면 다음 사람이 같은 일을 다시 하므로, 갱신 절차를 문서 끝에
적었습니다.

## 9. 스크롤백 메모리

탭당 8,000행(100열 기준 ≈8MB)이라 16탭을 오래 쓰면 100MB대에 가깝습니다. `onTrimMemory`에서
비활성 탭의 스크롤백을 줄입니다.

**검증**: `dumpsys meminfo`로 16탭 유지 시 메모리, `onTrimMemory` 호출 후 감소.

---

## 기기에서만 확인되는 것

CI 러너는 리눅스이고 폰이 아닙니다. 아래는 **초록불이어도 검증되지 않은 것**입니다.

| 항목 | 왜 러너에서 안 되나 | 기기에서 확인할 명령 |
|---|---|---|
| `termux/install.sh` | Termux 경로·`pkg`·`setsid`·`.bashrc` 훅 | `bash termux/install.sh` 두 번 |
| `termux/setup.sh` | 릴리즈 자산 내려받기·`termux-setup-storage`·`pkg` | `curl -fsSL .../termux/setup.sh \| bash` 두 번 |
| 진짜 Android PTY·`SIGHUP` | 러너의 PTY는 리눅스 것 | 탭에서 `install.sh` 재실행 |
| `termux-api`·`ART` 변수 | `am`·`app_process`가 없음 | `termux-battery-status` |
| 폰트 목록·내려받기 | `/sdcard`, Termux 저장소 | 앱 `모양 → 글꼴` |
| `fterm-keeper` | `/sdcard/Download` 요청 파일 | `pm clear` 후 앱 실행 |
| idle 생존(도즈) | 화면·배터리 최적화가 없음 | 실기기 방치 |
| APK 빌드(`build.sh`)와 앱 모듈 컴파일 | 러너에 `android.jar`·`aapt2` 없음 | `bash build.sh` |
| FGS 가 실제로 6시간 상한을 피하는지 | 시간이 지나야 드러남 | `dumpsys activity services` |
| 무음 모드에서 진동하지 않는지 | 러너에 무음 모드가 없음 | 기기 설정에서 무음 후 `printf '\a'` |
| 배경 이미지 피커·브로드캐스트 | SAF·`Android/data`·`am` 이 러너에 없음 | `모양 → 배경 이미지`에서 고르기, `adb shell am broadcast -a io.github.crashedsystem.terminal.action.APPLY_BACKGROUND -p io.github.crashedsystem.terminal --es path …` |

## 이 문서를 고치는 규칙

항목을 닫을 때 **검증 결과를 이 문서에 적고** 닫습니다. "고쳤다"만 적힌 항목은 다음 사람에게
없는 것과 같습니다.
