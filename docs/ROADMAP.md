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
| 4 | 고아 셸 | `S_ERROR` 뒤 `C_CLOSE` 누락 | 다음 PR |
| 5 | FGS 타입 | `dataSync` → `specialUse` | 다음 PR |
| 6 | UI·알림 정리 | 동기 디코딩, 무음 무시, 누락된 정리 | 다음 PR |
| 7 | 프로토콜 중복 | 3중 프레이밍, 거대 클래스 분리 | 그 다음 |
| 8 | 업스트림 기준점 | 벤더 파일의 원본 커밋 고정 | 그 다음 |
| 9 | 스크롤백 상한 | `onTrimMemory` | 그 다음 |

1–3은 끝났습니다. 무엇이 그것을 증명하는지는 각 절 끝의 "검증"에 있고, 러너가 확인하지 못하는
것은 맨 아래 "기기에서만 확인되는 것"에 남아 있습니다.

---

## 1. CI — 지금 아무것도 자동으로 검사되지 않는다

**증상**: `README.md`의 검증 표는 전부 손으로 돌린 결과입니다. Rust 데몬을 붙이는 순간 "무엇이
여전히 맞는지"를 사람이 기억해야 합니다.

**고치는 방법**: `.github/workflows/ci.yml`에 네 갈래를 둡니다.

- `python` — `tests/test_modes.py`(모드 추적기), `tests/test_env.py`(셸 환경), 그리고 실제
  데몬을 띄워 `termux/test_client.py` + `tests/test_modes.py --daemon`.
- `rust` — `cargo test`, 그리고 **같은 두 스위트를 컴파일된 Rust 바이너리로**. `termux/ftermd-rs/`가
  없으면 이 잡은 건너뜁니다.
- `java` — `tests/run_core.sh`(ANSI 파서·폭·팔레트·모드 복원). `FTERM_TMPDIR`만 CI용으로 넘깁니다.
- 전부 `ubuntu-latest`, 의존성 설치 없이. 필요한 것은 `python3`과 JDK뿐입니다.

**검증**: PR의 체크가 초록인 것 자체가 검증입니다. CI가 초록이 아니면 PR은 열린 채로 둡니다.

**한계**: `install.sh`, 진짜 Android PTY, `termux-api`, 폰트 선택 UI는 러너에서 재현할 수
없습니다. 그 목록은 `docs/ROADMAP.md`의 "기기에서만 확인되는 것"에 남깁니다.

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

**설치**: `install.sh` 에 `FTERM_DAEMON=rust` 를 두었습니다. 기본값은 지금과 같은
`python` 이고, `rust` 로 설치하면 같은 자리(`~/fterm/ftermd`)에 Rust 바이너리가 들어갑니다.
두 데몬의 명령줄·출력·종료 코드가 같으므로 앱도 keeper 도 스크립트도 바뀌지 않습니다.

**검증**: `cargo test` 58종(모드 추적기·ring·프레이밍·pidfile·백프레셔·환경·글꼴),
`tests/test_daemon_hardening.py`(낡은 pidfile·느린 클라이언트·막힌 탭 — 파이썬 데몬에서는
실패하는 것이 정상), 그리고 3단계의 프로토콜 스위트를 Rust 바이너리로.

## 3. 프로토콜 호환 — 두 구현을 같은 잣대로

**증상**: 2단계의 "호환된다"는 말은 검증이 없으면 주장일 뿐입니다.

**고치는 방법**: `tests/test_modes.py --daemon`(실제 데몬에 붙어 선언 분할·망각·복원 순서를
확인)과 `termux/test_client.py`(인증·에코·한글·resize·replay·close·OPEN 문자열·모드 복원)를
**Python 데몬과 Rust 데몬 양쪽에** 돌립니다. `tests/run_daemon.sh`가 그 실행기입니다.

**검증**: 같은 출력, 같은 통과 수. 하나라도 다르면 그 차이가 곧 버그입니다.

## 4. 고아 셸 — 에러로 끊긴 세션이 데몬에 남는다

**증상**: `SocketTransport.finishIfRunning()`은 `!isRunning()`이면 `C_CLOSE`를 보내지 않습니다.
`S_ERROR`로 끊긴 세션은 데몬 쪽에 셸째 남아 세션 16개 한도를 채웁니다.

**고치는 방법**: 전송 계층이 세션을 "열었다가 실패한" 상태와 "연결만 잃은" 상태를 구분해,
후자에서도 `C_CLOSE`를 보냅니다. 데몬은 이미 `C_CLOSE`에 대해 옳게 동작하므로 앱 쪽 문제입니다.

**검증**: 데몬을 17번째 세션에서 거절시키는 테스트. `E_TOO_MANY`가 아니라 "16개가 남지 않음"을
확인합니다.

## 5. FGS 타입 — `dataSync`의 6시간 상한

**증상**: Android 15+에서 `dataSync` 포그라운드 서비스는 24시간 내 6시간이 지나면 시스템이
제한할 수 있습니다. 유휴 세션 유지가 앱의 존재 이유이므로 여기서 걸리면 제품이 멈춥니다.

**고치는 방법**: `AndroidManifest.xml`의 서비스 타입을 `specialUse`로 옮기고
`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`에 사유를 적습니다.

**검증**: 실기기에서 `dumpsys activity services`의 타입 확인, 그리고 6시간 뒤에도 살아 있는지.

## 6. UI·알림 정리

- `ClipboardImage.save()`를 클릭 핸들러에서 동기 호출 → 고화소 이미지에서 버벅임. 작업 스레드로
  옮기고 완료 후 경로를 붙여넣습니다.
- `Notifier`가 무음 모드·알림 빈도를 보지 않고 무조건 진동 → `Bell`과 같은 게이트를 씁니다.
- `onDestroy`에서 `Bell.release()` 호출 없음, `DaemonLauncher.requestStart()` 반환값 무시로 자동
  기동 실패가 조용히 묻힘 → 반환값을 로그·토스트로 드러냅니다.

**검증**: 무음 모드에서 진동 없음, 연속 알림의 빈도 제한, 이미지 붙여넣기 중 프레임 드랍 없음.

## 7. 프로토콜 3중 구현과 거대 클래스

`SocketTransport`(619줄), `DaemonClient`(350줄), `ftermd`가 프레이밍과 상수를 각자 갖고 있습니다.
Rust 데몬이 들어오면 네 번째가 됩니다. 상수·프레이밍을 한 곳(문서 + 생성된 헤더)에서 정의하고,
`MainActivity`(1,416줄)·`SettingsSheet`(1,985줄)를 책임 단위로 나눕니다.

**검증**: 상수 일치 테스트(문서에서 생성한 값을 두 구현이 같은지), 그리고 순수 이동 리팩터링은
CI 초록만으로 판정합니다.

## 8. 업스트림 기준점

`com.termux` 7파일(약 844줄) 수정분이 어느 업스트림 커밋에서 왔는지 기록이 없습니다. 파일 머리말과
`NOTICE.md`에 커밋 해시를 적고, 업스트림 대비 diff를 남깁니다.

**검증**: 기록된 해시에서 파일을 받아 우리 수정분만 diff로 나오는지.

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
| 진짜 Android PTY·`SIGHUP` | 러너의 PTY는 리눅스 것 | 탭에서 `install.sh` 재실행 |
| `termux-api`·`ART` 변수 | `am`·`app_process`가 없음 | `termux-battery-status` |
| 폰트 목록·내려받기 | `/sdcard`, Termux 저장소 | 앱 `모양 → 글꼴` |
| `fterm-keeper` | `/sdcard/Download` 요청 파일 | `pm clear` 후 앱 실행 |
| idle 생존(도즈) | 화면·배터리 최적화가 없음 | 실기기 방치 |

## 이 문서를 고치는 규칙

항목을 닫을 때 **검증 결과를 이 문서에 적고** 닫습니다. "고쳤다"만 적힌 항목은 다음 사람에게
없는 것과 같습니다.
