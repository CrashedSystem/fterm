# fterm

폰에서 개발한다는 것은, 폰에서 TUI 하네스를 돌린다는 뜻입니다. 하네스를 돌리려면 셸이
필요하고, 셸을 쓰려면 터미널이 필요합니다. 문제는 그 터미널이 우리말을 전제로 만들어지지
않았다는 점입니다.

영어로 프롬프트를 쓰는 편이 정확하고 토큰도 아낍니다. 그것은 사실이고 앞으로도 그럴
것입니다. 그러나 생각을 우리말로 표현하지 못하면 **표현 자체가 제한됩니다.** 코드 리뷰에서
뉘앙스를 살리지 못하고, 기획을 우리말로 적어 두지 못하며, 에러 메시지를 읽으면서 머릿속에서
한 번 더 번역해야 합니다. 개발은 영어로 하는 일이 아니라 생각을 옮기는 일이고, 생각은
모국어로 할 때 가장 정확하게 나옵니다.

그리고 멀티모달 모델이 흔해진 지금, 스크린샷 한 장을 붙이는 데 CLI 도구를 설치하고 경로를
손으로 입력해야 하는 것은 그냥 수치입니다.

fterm은 그 두 가지를 메우는 앱입니다. **우리말로 쓰고 우리말로 읽는 것**을 전제로,
셸은 Termux의 것을 그대로 물려 쓰고 — PTY도 프로세스도 Termux 안에 있습니다 — 화면과
입력만 폰에 맞게 다시 만들었습니다. 정확히는 **한글 친화적 터미널 스킨**입니다. 엔진은
Termux의 `terminal-emulator`/`terminal-view`(Apache-2.0)를 벤더링했으므로, 남의 터미널을
새로 쓴 것이 아니라 이미 있던 터미널 위에 우리말과 우리 손가락에 맞는 껍데기를 씌운
것입니다. 위에 탭 스트립, 아래에 키 바, 가운데 큰 다크 화면입니다.

| | |
|---|---|
| 앱 | `build/fterm.apk` (`io.github.crashedsystem.terminal`, targetSdk 36 / minSdk 26). 그림 에셋 없이 빌드하면 1.8MB입니다 |
| 데몬 | `termux/ftermd-rs` (Rust, Termux 안에서 실행. 같은 프로토콜의 첫 구현이던 python 데몬은 Rust 이식이 와이어 레벨에서 동일함이 증명된 뒤 제거됐습니다) |
| 엔진 | Termux `terminal-emulator` + `terminal-view` (Apache-2.0) 벤더링 |
| 폰트 | mterm 스택: Iosevka(라틴) + Nerd Symbols(아이콘) + Pretendard(한글) |

## 이 문서는 무엇인가

명세서가 아닙니다. 만들어진 것과 **왜 그렇게 만들었는지**를 적은 기록입니다. 이 프로젝트는
"그렇게 하면 될 것 같다"로 정한 것이 거의 없고, 대부분 실기기에서 재 보고 나서 정했습니다.
그래서 본문에 수치와 실패한 시도가 함께 남아 있습니다. 결론만 옮겨 적으면 다음에 같은
실수를 반복하게 되기 때문입니다.

## 왜 데몬이 필요한가

앱은 Termux의 bash를 직접 실행할 수 없습니다. `/data/data/com.termux/files/usr` 가 `0700`
이고 앱의 uid가 다르기 때문입니다. 그래서 PTY는 Termux 안의 `ftermd` 가 소유하고, 앱은
`127.0.0.1:8791` 로 붙습니다. 부수 효과가 오히려 장점입니다. 앱을 닫아도 셸이 살아 있고,
다시 열면 화면이 그대로 복원됩니다(`REPLAY`).

### 왜 터미널 모드까지 복원하는가

화면만 되살리면 절반입니다. TUI는 시작할 때 한 번 `ESC[?1000h`(마우스 보고),
`ESC[?1006h`(SGR), `ESC[?1049h`(alt screen) 같은 선언을 하고 다시는 되풀이하지 않습니다.
앱 프로세스가 재생성되면 에뮬레이터는 새로 만들어지고 ring(최근 256KiB)만 재생되므로,
그 선언이 ring 밖으로 밀려나 있으면 앱은 마우스가 꺼진 채로 시작합니다. 셸은 여전히 켜져
있다고 믿으니 스와이프는 히스토리 스크롤로 새고 탭은 TUI에 닿지 않습니다. 크기가 우연히
달라 `SIGWINCH` 가 갈 때만 프로그램이 다시 선언해서 살아나므로 "가끔"으로 보입니다(실측:
재접속 replay 262144바이트 안에 `ESC[?1000h` 가 없음).

`ftermd` 의 `Modes` 가 출력에서 그 선언들을 뽑아 마지막 상태를 들고 있다가, 재접속한
클라이언트에게 ring보다 **먼저** 되돌려줍니다. 전체 화면 프로그램이 떠 있었으면 창 크기를
1칸 줄였다 되돌려 `SIGWINCH` 를 보내 화면을 다시 그리게 합니다. 앱은 그 선언을 그대로
에뮬레이터에 먹이므로 따로 할 일이 없습니다.

추적기는 스트림이 임의의 바이트 경계에서 끊기는 것을 전제로 합니다. `ESC[?1000;10` 까지
읽고 다음 `06h` 가 다음 read에 오는 경우가 실제로 있고, 그때 선언을 놓치면 고친 버그가
그대로 돌아옵니다. 반대로 끝나지 않는 파라미터 열(깨진 출력)을 무한히 붙들고 있으면
`carry` 가 출력량을 따라 자라므로 64바이트에서 자릅니다. 이 두 경계는 `tests/test_modes.py`
가 데몬이나 pty 없이 `Modes` 만 떼어내 검사합니다(분할 17종, 결합 파라미터, 무관한 CSI,
마지막 선언 우선, carry 상한).

클라이언트 쪽 절반도 따로 검증합니다. `tests/jvm/core/ModeRestoreTest.java` 는 실제
`TerminalEmulator` 에 복원 문자열을 먹이고, 터치 핸들러가 묻는 것과 같은
`isMouseTrackingActive()` 가 참이 되는지 확인합니다. 화면이 맞는 것과 터치가 가는 것은
다른 문제이기 때문입니다. `test_client.py` 의 `test_mode_restore` 는 실제 pty 위의
curses 프로그램으로 같은 것을 확인합니다.

## 설치

1. [Releases](https://github.com/CrashedSystem/fterm/releases)에서 `fterm.apk` 를 받아
   설치합니다 (`io.github.crashedsystem.terminal`, targetSdk 36 / minSdk 26).

2. Termux에서 아래 한 줄을 붙여넣고 실행합니다.

```bash
curl -fsSL https://raw.githubusercontent.com/CrashedSystem/fterm/master/termux/setup.sh | bash
```

저장소를 clone 하거나 Rust로 빌드하지 않습니다. 릴리즈에 첨부된 aarch64 바이너리와 keeper를
`~/fterm` 에 놓고, 데몬과 keeper를 띄운 다음 토큰을 `/sdcard/Download/fterm-token` 에 써
둡니다. 그래서 앱으로 돌아오면 기다릴 것 없이 바로 연결됩니다. 32비트 ARM이나 x86
에뮬레이터는 프리빌드 바이너리가 없으므로 저장소에서 `bash termux/install.sh` 로 직접
빌드해야 합니다.

3. 앱을 엽니다. Android 11+ 에서는 첫 실행 때 '모든 파일 접근' 설정 화면이 한 번 뜹니다.
   앱이 keeper에게 데몬을 요청하는 통로가 `/sdcard/Download` 의 파일이고, 스코프드
   스토리지에서는 그 파일을 쓸 수 없기 때문입니다. 허용하고 앱으로 돌아오면 앱이 스스로
   다시 시도해 바로 연결됩니다. keeper도 Termux도 없는 상태라면 스크립트가 띄우지 못한
   것이므로, 창의 '명령 복사 & Termux 열기'로 2번을 다시 실행하면 됩니다.
## 조작

| 동작 | 결과 |
|---|---|
| `+` | 새 탭 |
| 탭 길게 | 탭 닫기 (확인 후 셸 종료) |
| `+` 길게 | 설정 (글자 크기, 색상 구성, 서버 정보) |
| CTRL/ALT/SHIFT | 다음 키 한 번에만 적용 (sticky) |
| 터미널 길게 | 텍스트 선택 → 복사 |
| Ctrl+Shift+C / V | 하드웨어 키보드에서 복사/붙여넣기 |
| Ctrl+Shift+T / W / P | 새 탭 / 탭 닫기 / 명령 팔레트 |
| Ctrl+Shift+F | 찾기 바 (이미 열려 있으면 입력란으로) |
| Ctrl+, | 설정 |
| Ctrl+Tab / Ctrl+Shift+Tab | 다음 탭 / 이전 탭 |
| Ctrl+1 ~ Ctrl+9 | n번째 탭 |
| Ctrl+= / Ctrl+- / Ctrl+0 | 글자 크기 키우기 / 줄이기 / 기본값 |
| 마우스 오른쪽 클릭 | 복사·붙여넣기·전체 선택·찾기·설정 |

단축키는 창 단위로 먼저 처리되므로 찾기 바나 설정이 떠 있어도, 또 터미널이 포커스가
아니어도 동작합니다. 셸이 쓰는 조합(Ctrl+문자, Ctrl+화살표·Home·End)은 건드리지 않습니다.

색상은 Campbell(Windows Terminal 기본), One Dark, Solarized Dark, Dracula 4종입니다.

## 태블릿 · DeX

키보드와 마우스가 붙은 화면을 전제로 한 동작이 따로 있습니다.

| 상황 | 동작 |
|---|---|
| 하드웨어 키보드가 붙음 | 키 바를 자동으로 숨깁니다. `모양 → 하드웨어 키보드에서 키 바 숨김`으로 끌 수 있습니다 |
| 앱 시작·터미널 클릭 | 소프트 키보드를 올리지 않습니다 (키 바의 키보드 버튼으로는 올릴 수 있습니다) |
| 12" 같은 넓은 화면 | 설정·명령 팔레트는 640dp 폭으로 제한해 가운데 놓고, 키 바는 키 폭이 커지지 않도록 중앙에 모읍니다 |
| 탭 스트립 | `+`·▾가 마지막 탭 바로 뒤에 붙습니다 |
| 회전·창 크기 변경 | 시트 높이와 글자 크기를 다시 계산합니다 (액티비티는 재생성되지 않습니다) |

## 이미지 — 탭 한 번

멀티모달 모델에 스크린샷을 보여주는 일은 이제 일상인데, TUI 안에서는 그게 잘 되지
않습니다. opencode의 붙여넣기는 OpenTUI에 클립보드를 묻고, OpenTUI는 다시
**호스트**(X11/Wayland)에 묻습니다. Android에는 그게 없으므로 터미널 안 프로그램은
안드로이드 클립보드를 영영 볼 수 없습니다. OSC 52를 구현해도 그 경로는 바뀌지 않습니다.

그래서 두 갈래로 뚫었습니다.

| 경로 | 동작 |
|---|---|
| 클립보드 패널의 썸네일 탭 | IME가 `commitContent()`로 그림을 넘깁니다. 앱이 PNG로 저장하고 **경로**를 프롬프트에 넣습니다. opencode는 로컬 경로를 `data:` URI 첨부로 바꿉니다 |
| `Ctrl+Shift+V` / 키 바 붙여넣기 | 클립보드가 그림이면 같은 일을 합니다. 텍스트가 없으니 경로를 넣고 토스트로 알립니다 |

저장 위치는 `/sdcard/Download/fterm-clip/` 입니다. 앱 캐시가 아닌 이유는 하나뿐입니다.
경로를 읽는 쪽이 opencode이고, 그 프로세스는 **Termux uid**로 돌아서 앱 내부 디렉터리에
`EACCES`를 받습니다.

포커스에 반응해 자동으로 붙이지는 않습니다. 키보드 클립보드 패널에서 썸네일을
**탭했을 때만** 들어갑니다. "저절로 붙는" 것과 "제가 붙이는" 것은 다르고, 전자는 입력
중인 프롬프트를 망칩니다.

## mterm에서 가져온 것

| 항목 | 원본 | 이 앱에서 |
|---|---|---|
| 폰트 스택 | `mterm/public/js/core/appearance.js` `FONT_STACK` = Iosevka → Nerd Symbols → Pretendard | `Fonts.java` — 코드포인트별 `Typeface` 선택. 얼굴마다 em 크기는 같고, 두 칸을 넘는 글리프만 줄여 상자에 맞춥니다 (Windows Terminal AtlasEngine 방식) |
| One Half Dark 팔레트 | 같은 파일의 `ONE_HALF_DARK` | `Schemes.oneHalfDark()` — 16개 ANSI 값을 그대로 옮겼습니다 (`SchemesTest`가 대조) |

mterm의 배경 그림은 CC BY-NC라 이 앱에 넣지 않았습니다. 대신 **누구나 자기 그림을 쓰는
일반 기능**으로 바꿨습니다(아래 "배경 이미지"). 그래서 Miku라는 이름의 테마와 그림을 읽던
`MikuBackground`, `assets/img/` 빌드 오버레이는 모두 없어졌습니다.

## 배경 이미지

터미널 글자 뒤에 기기 안의 그림 한 장을 그립니다. 색 구성표와 무관하게 적용되므로 어떤
팔레트를 쓰든 같은 그림이 남습니다. `모양 → 배경 이미지`에서:

| 항목 | 뜻 |
|---|---|
| 배경 이미지 | `기기에서 고르기` — `ACTION_OPEN_DOCUMENT`로 고른 그림을 앱 폴더로 복사합니다. `배경 없애기`로 지웁니다 |
| 투명도 | 그림을 얼마나 진하게 그릴지 (0–100%) |
| 맞춤 | `contain` 전체가 보이게 / `cover` 꽉 채우고 잘라내기 |
| 어둡게 | 그림 위에 덮는 검정 비율 (0–90%). 글자가 묻히면 올립니다 |

그림은 `getExternalFilesDir("bg")` — 실제 경로는
`/sdcard/Android/data/io.github.crashedsystem.terminal/files/bg/` — 에 정규화되어(긴 변 2048px) 저장됩니다.
**그 폴더의 가장 최근 이미지 파일이 그려집니다.** 그래서 스크립트는 빌드 오버레이가 아니라
파일 복사와 브로드캐스트만 하면 됩니다:

```bash
DIR=/sdcard/Android/data/io.github.crashedsystem.terminal/files/bg
adb push miku-dusk.png "$DIR"/
adb shell am broadcast -a io.github.crashedsystem.terminal.action.APPLY_BACKGROUND \
  -p io.github.crashedsystem.terminal --es path "$DIR"/miku-dusk.png \
  --ei opacity 100 --es fit contain --ei dim 25
adb shell am broadcast -a io.github.crashedsystem.terminal.action.CLEAR_BACKGROUND -p io.github.crashedsystem.terminal
```

브로드캐스트는 결과 줄(`ok: background.png` / `error: …`)을 돌려주고, 앱이 떠 있으면
바로 다시 그립니다. 파일만 넣어도 앱으로 돌아올 때(`onResume`) 새 그림을 읽습니다.
밝은 그림 위의 `#dcdfe4` 글자는 읽기 어려우므로, 예전 테마 그림처럼 배경색과 multiply 해
둔 그림을 쓰거나 `--ei dim`으로 어둡게 하십시오.

## 제3자 코드와 라이선스

fterm 자체는 MIT License 다(`LICENSE`). Termux 엔진(Apache-2.0), 세 글꼴(OFL-1.1),
Fluent 아이콘(MIT), Retro 셰이더(MIT), 앱 아이콘 그림(MIT, Windows Terminal `Terminal.svg`
파생)을 포함합니다. 저작권 고지와 라이선스 전문은 `NOTICE.md` 와 `licenses/` 에 있고,
같은 파일이 빌드된 APK 안(`assets/`)에도 들어갑니다.

## 앱이 데몬을 스스로 띄운다 (keeper)

처음에는 Termux의 `RUN_COMMAND` 브로드캐스트로 `ftermd start`를 쏘려 했습니다. **이
기기에서는 조용히 무시됩니다.** 브로드캐스트는 `result=0`으로 끝나고 `RunCommandService`는
호출되지 않으며 파일도 생기지 않습니다(실측: `am broadcast` 두 번, `am start-foreground-service`
한 번 모두 실패). Termux 0.118.3 + Android 17의 동작입니다.

그래서 방향을 뒤집었습니다. 앱이 **공유 저장소에 요청 파일**을 쓰고, Termux 안의 작은
keeper 프로세스가 그것을 폴링해 데몬을 띄우고 토큰을 돌려줍니다.

```
/sdcard/Download/fterm-up            앱 → keeper   "데몬 올려라"
/sdcard/Download/fterm-token         keeper → 앱   토큰
/sdcard/Download/fterm-down          앱 → keeper   "데몬 내려라"
/sdcard/Download/fterm-keeper-alive  keeper 1초 heartbeat
```

검증(실기기): `pm clear` 로 토큰과 설정을 지우고 데몬을 죽인 상태에서 앱을 열었더니
데몬이 뜨고 토큰이 발행됐습니다.

```
앱 실행 → keeper log "up: daemon=True"
           daemon: alive, port 8791
           /sdcard/Download/fterm-token = 861f4ee8…
```

`termux/setup.sh` 가 프리빌드 바이너리로 데몬과 keeper를 설치하고, keeper를 띄우고, 토큰을
`/sdcard/Download` 에 써 둡니다. 사용자는 이것만 한 번 하면 이후로는 앱만 열면 됩니다.
저장소에서 직접 빌드하려면 `termux/install.sh` 가 같은 자리에 컴파일한 바이너리를 놓습니다.

## Windows Terminal 느낌

| WT | 이 앱 |
|---|---|
| 탭 스트립 + `+` + 드롭다운 캐럿 | 있습니다. `+` 를 길게 눌러도 드롭다운 |
| 드롭다운 메뉴(새 탭·이름 변경·닫기·붙여넣기·복사·설정) | 있습니다. 단축키를 병기합니다 |
| 명령 팔레트 `Ctrl+Shift+P` | 있습니다. 검색할 수 있습니다 |
| 활성 탭에만 닫기 버튼 | 있습니다 |
| 둥근 상단 탭 plate | 있습니다. 활성 탭은 터미널 배경색과 동일합니다 |
| `Ctrl+Shift+T/W/C/V` | 있습니다 |
| 오른쪽 클릭 메뉴 | 있습니다 (복사·붙여넣기·전체 선택·찾기·설정) |
| 분할 창 | **없습니다** (모바일이라 의도적으로 미구현) |

### 글꼴

기본은 번들 Iosevka이고, `모양 → 글꼴 → 글꼴 설치`에서 세 가지 방법으로 추가합니다.

| 방법 | 하는 일 |
|---|---|
| Termux에서 받기 | 데몬이 `~/.fonts`, `~/.local/share/fonts`, `$PREFIX/share/fonts/TTF`, `~/storage/downloads/fonts`를 훑어 목록으로 보여줍니다. 고르면 소켓으로 바이트를 넘겨받아 앱 저장소에 복사합니다. |
| URL로 받기 | 앱에는 URL 클라이언트도, 다운로드할 수 있는 저장소도 없습니다. 그래서 데몬이 대신 내려받아 `~/.fonts`에 넣고, 그 경로를 다시 받아옵니다. |
| 기기의 파일에서 고르기 | SAF 문서 선택기입니다. 파일이 이미 폰에 있을 때만 씁니다. |

가장 짧은 경로는 Termux에서 한 줄입니다. 그러면 앱을 열었을 때 목록에 바로 있습니다.

```bash
curl -fLo ~/.fonts/JetBrainsMono-Regular.ttf \
  https://raw.githubusercontent.com/JetBrains/JetBrainsMono/master/fonts/ttf/JetBrainsMono-Regular.ttf
```

`/sdcard/Download/fonts` 는 쓰지 않습니다. Android 11부터 앱이 그 디렉터리의 파일을 열 수
없어서, 넣어도 목록에 나오지 않습니다(실측).

목록은 스캔 결과이므로 고정폭 여부를 실제로 측정해 거릅니다. `M`/`i`/`l`/`W`/`0`의 폭이
1% 넘게 차이 나면 비례 글꼴로 보고 제외합니다. 받은 파일도 같은 검사를 통과해야
저장됩니다.

### 한글 글꼴

라틴 얼굴과 한글 얼굴은 **다른 설정**입니다. `모양 → 한글 글꼴` 에서 고릅니다.

| | 라틴 (`글꼴`) | 한글 (`한글 글꼴`) |
|---|---|---|
| 거르는 기준 | 고정폭이어야 합니다 — 한 칸이 한 글자를 정의합니다 | 글리프가 있어야 합니다 — 비례여도 됩니다 |
| 그리는 방식 | 그대로 그립니다 | 같은 em 크기로 그리고, 두 칸을 넘으면 그 글자만 줄여 맞춥니다 |
| 기본값 | 번들 Iosevka | 번들 Pretendard |

한 목록으로 합치면 한글 글꼴을 고를 수 없습니다. 실기기의 한글 글꼴(나눔고딕,
Noto Sans KR, One UI Sans KR)은 전부 비례 폭이라 고정폭 검사에서 걸러지고, 목록에는 앱에
컴파일된 Pretendard만 남습니다(실측). 그래서 한글 쪽은 반대 기준으로 따로 거릅니다.

목록에 `· 자모` 가 붙은 얼굴은 파일 자체에 결합 자모(U+1100~11FF)가 있다는 뜻입니다. 번들
Pretendard에는 없어서, 분해된 자모를 붙여넣으면 플랫폼이 다른 글꼴로 대신 그립니다.

한글 지원 여부는 `Paint.hasGlyph` 로 묻지 않습니다. 그건 파일이 아니라 플랫폼의 폰트
폴백에 묻는 것이라, 실기기에서 모든 글꼴이 "한글 된다"고 답했고 목록에 폰의 글꼴이 전부
올라왔습니다(실측). `FontCoverage` 가 TTF/OTF/TTC의 `cmap` 테이블을 직접 읽어 그 파일
자신이 그 코드 포인트를 갖고 있는지만 답합니다.

#### 왜 한글을 확대하지 않는가

한때 한 글자가 두 칸에 꼭 차도록 한글 얼굴을 **115.85% 키워** 그린 적이 있습니다(mterm의
`size-adjust` 값). 읽기 어려워졌다는 지적이 나왔고, 로그를 재 보니 이유가 수치로
나왔습니다.

| | 행 높이 기준 (라틴) | 한글 (115.85% 확대) |
|---|---|---|
| `getFontSpacing()` | 37.5 px | 41.3 px |
| `ascent()` | -29.0 px | -33.0 px |

행 높이는 라틴 얼굴이 정한 37.5인데, 한글은 41.3짜리 세로 메트릭으로 그려집니다. 글자가
행을 위아래로 3.8px씩 넘치고, 그 상태로 baseline을 공유하니 답답하게 보입니다. 확대한
얼굴은 자기 세로 메트릭을 데려오는데 행 높이는 따라오지 않기 때문입니다.

Windows Terminal의 AtlasEngine은 그래서 **em 크기를 바꾸지 않습니다**
(`src/renderer/atlas/AtlasEngine.cpp`). DirectWrite에 `font->fontSize` 그대로 배치를 묻고,
클러스터의 advance만 셀 폭에 맞춘 뒤 (`glyphAdvances[last] += expectedAdvance - actualAdvance`),
상자를 넘치는 글리프만 줄여 그립니다. 여기서도 같습니다. 확대는 하지 않고,

1. 폭이 두 칸을 넘으면 `2 * cell / advance` 로,
2. 세로 잉크가 라틴 행 높이를 넘으면 `rowHeight / inkHeight` 로

그 코드포인트만 줄입니다. 배율 상한은 1이고, 남은 여백은 베이스라인을 행 중앙으로 옮겨
채웁니다(`shiftFor`). 한글 잉크의 세로 위치가 라틴과 어긋나 행이 아래로 쏠리던 것도 이
오프셋이 함께 고쳤습니다.

터미널 벨은 BEL을 진동이나 비프로 옮깁니다. 무음 모드에서는 진동만 울리고, 활성 탭만
울립니다. 네이티브 알림은 포그라운드 서비스의 상시 알림 하나이고, 탭하면 앱으로
돌아옵니다.

## 셸이 띄우는 알림 — 네이티브, 플러그인 없음

opencode 같은 도구는 데스크톱에서 알림을 띄웁니다. Termux에서는 그것이
`termux-notification`(플러그인 + termux-api 패키지 + 프로세스 실행)을 거쳐야 했습니다.
여기서는 **이스케이프 시퀀스를 터미널이 직접 읽습니다.**

```
ESC ] 9 ; message BEL                  (iTerm2 형식, 대부분의 CLI 도구)
ESC ] 777 ; notify ; title ; body BEL  (rxvt-unicode 형식)
```

`TerminalEmulator.doOsc` 가 이 둘을 파싱해 `TerminalOutput.onNotification()` 을 부르고,
앱이 Android 알림으로 띄웁니다. 셸은 추가 비용이 0입니다. 바이트는 이미 터미널로 가고
있습니다.

실제 파서로 검증한 결과입니다.

```
ESC]9;빌드 완료BEL          → NOTIFY title=[] body=[빌드 완료]
ESC]9;doneESC\              → NOTIFY title=[] body=[done]
ESC]777;notify;테스트;3개 실패BEL → NOTIFY title=[테스트] body=[3개 실패]
ESC]2;그냥 제목BEL          → TITLE=그냥 제목   (알림 아님)
BEL                         → BELL            (알림 아님)
```

서비스 알림은 LOW(상태 표시)이고 셸 알림은 DEFAULT(알려야 하는 것)입니다. 채널이 분리돼
있어 하나만 끌 수 있습니다. 같은 제목의 알림은 대체되고, 다른 제목은 쌓입니다.

### opencode는 이미 이걸 내장하고 있습니다

바이너리(245 MB)를 청크로 훑어 확인한 사실입니다. 플러그인이 필요하지 않습니다.

```
[188215656]  ESC ] 777 ; notify ;        알림 형식
[188215973]  ESC ] 9 ;                   iTerm2 형식
[223678744]  ESC [J ESC ] 9 ; 4 ; 0      진행률 형식
```

**`ESC]9;4;` 는 알림이 아니라 진행률**입니다(ConEmu/Windows Terminal 규격). opencode가
이것을 쏘기 때문에, 모든 OSC 9을 알림으로 처리하면 진행률 업데이트마다 알림이 뜹니다.
파서가 `9;4;` 를 구분해 버립니다. `NotifyTest` 가 이 경우를 고정합니다.

기존 `windows-notify` 플러그인(SSH로 Windows 토스트 + `termux-notification`)은
`plugins.disabled/` 로 옮겨 비활성화했습니다. 되돌리려면 그 폴더를 `plugins/` 로
되돌리면 됩니다.

### 알림이 "안 오는" 진짜 원인: 포커스 게이트 (DECSET 1004)

opencode의 알림은 무조건 쏘는 게 아니었습니다. 바이너리의 attention 코드를 해독하면
다음과 같습니다.

```js
let r = "unknown";                       // 포커스 상태
t.renderer.on("focus", () => r = "focused");
t.renderer.on("blur",  () => r = "blurred");
// when: "blurred" (기본) — 화면 밖일 때만 알림
let m = LW(h?.when ?? "blurred", r);     // r이 "unknown"이면 "focus_unknown"
if (!_ && !R) { if (E && m) return skip(m); }  // ← 스킵
```

blur/focus는 **터미널이 보내는 포커스 리포트**(DECSET 1004 → `CSI I`/`CSI O`)로만 알 수
있습니다. fterm 에뮬레이터는 DECRQM에 "1004 = set"이라고 답하면서(로그에 `ESC[?1004;2$y`
질의 기록) 실제 리포트는 보내지 않았습니다("NOT implemented" 주석). opencode는 포커스가
영원히 "unknown"이 되어 **알림을 영구히 건너뛰었습니다.** OSC 파서는 멀쩡했는데 그 위의
게이트가 열리지 않은 것입니다.

수정: 에뮬레이터가 1004가 켜지면 `CSI I`/`CSI O` 를 내보냅니다. 뷰의
`onWindowFocusChanged`(윈도우 포커스 — "사용자가 앱을 보고 있는가")가 구동하고, 세션 부착
시 현재 상태를 즉시 알려줍니다. `FocusTest` 6개가 고정합니다.

### 알림이 폰에 도달하는 3단계 — 각각 다른 막힘

opencode 턴 완료 → 폰 알림은 세 단계를 거칩니다. 전부 다른 이유로 막혀 있었습니다.

1. **opencode가 알림을 만드는가** — `attention.notifications` 기본값이 OFF입니다.
   바이너리 스키마에 `default:!1` 로 명시돼 있습니다. `~/.config/opencode/cli.json` 에서
   켭니다.
2. **"화면 밖"이라고 판정하는가** — 알림의 기본 조건이 blurred입니다. 포커스는 터미널의
   DECSET 1004 리포트로만 알 수 있는데 fterm이 보내지 않았습니다. 구현해 해결했습니다
   (위 참조).
3. **만든 알림이 폰에 도달하는가** — 근본 문제입니다. opencode의 알림은 데스크톱 전용
   (DBus/Windows 토스트/macOS 알림센터)입니다. 안드로이드 터미널에는 그 시스템이 없어
   **설정을 켜도 폰 알림은 오지 않습니다.** 해결: `fterm-notify` 플러그인이 턴
   완료/실패/권한 요청 시 OSC 9을 출력하고, fterm이 그것을 파싱해 안드로이드 알림을
   올립니다. (fterm 파서 쪽은 dumpsys로 실측 검증했습니다: id=2602,
   "FTERM-NOTIFY-TEST" 도착.)

**2026-11-17 최종 실측 — 체인 전체가 동작합니다.** opencode 턴 종료 → `strace` 로 TUI의
`write(pts, "\x1b]9;fterm · 완료\x07")` 2회 기록 → fterm 파서 → 사용자 보고: "알림
왔다요". 끝부터 끝까지 실측으로 연결됐습니다.

플러그인은 windows-notify의 검증된 구조를 따릅니다. `index.js`(server, 빈 setup) +
`tui.js`(터미널 소유 프로세스, OSC 출력) + package.json exports의 `./tui` 슬롯입니다.
subagent 세션은 알림에서 제외합니다. `TERM_PROGRAM=fterm` 이 아니면 이스케이프를 쓰지
않아 다른 터미널의 스크롤백을 오염시키지 않습니다.

### 완전 내림 후 복구 불가의 두 원인

1. **keeper 사망** — 하트비트가 23분 뒤인데 프로세스는 없었습니다. 앱이 데몬을 되살리는
   유일한 손이 keeper인데, 그것이 죽으면 앱은 어쩔 수 없습니다. `install.sh` 가
   `~/.bashrc` 에 훅을 넣어 Termux가 열릴 때마다 keeper를 되살립니다(멱등).
2. **토큰이 있으면 프로비저닝을 안 함** — `startSessions()` 는 토큰이 *없을 때만*
   provisioning을 돌렸습니다. 토큰이 남아 있고 데몬만 죽었으면 오류 다이얼로그에서
   끝났습니다. 이제 연결 실패 시 provisioning을 다시 돌립니다. keeper는 같은 토큰을 다시
   발행하므로 재시도가 그대로 연결됩니다.

### rish가 조용히 죽는 원인: BOOTCLASSPATH (Shizuku 공통 버그)

**증상**: `rish -c 'id'` 가 rc=0, 출력 0바이트로 즉사합니다(0.15초). Shizuku 서버가 살아
있어도 동일합니다. "Shizuku가 죽었다"고 오인하기 쉽지만 아닙니다.

**원인**: `app_process` 는 안드로이드 앱 안에서 실행될 때 시스템이 `BOOTCLASSPATH`,
`ANDROID_ART_ROOT`, `ANDROID_I18N_ROOT`, `ANDROID_TZDATA_ROOT`, `DEX2OATBOOTCLASSPATH` 를
환경에 넣어주지만, **일반 셸(fterm 데몬, ssh, cron)에서는 이 변수들이 없습니다.** 변수가
없으면 ART가 부트 클래스패스를 만들지 못해 RuntimeInit 로그 한 줄만 남기고 exit(0)합니다.
에러가 stdout/stderr로 나오지 않고 logcat으로만 나오므로 "조용히 죽는 것"처럼 보입니다.

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

**해결**: `ftermd` 가 세션 환경을 만들 때 이 변수들을 물려줍니다. 발견 경로는 세
단계입니다 — 자기 환경, 캐시(`~/.fterm/android.env`, 0600), `/proc` 의 살아있는 프로세스.
데몬이 ssh나 cron에서 떠서 ART 변수를 하나도 못 받은 경우에도 `am` 이 동작합니다.
발견 결과는 캐시되고, 캐시의 jar가 사라졌으면(시스템 업데이트) 버리고 다시 찾습니다.

`DEX2OATBOOTCLASSPATH` 는 `BOOTCLASSPATH` 와 **다른 값**입니다. 같은 61개를 넣으면
`app_process` 가 메시지 없이 abort합니다(rc=134). dex2oat가 컴파일할 목록이지 ART가
로드할 목록이 아니기 때문입니다. 캐시에 있는 값이 21개인 이유입니다.

`~/bin/rish2` 도 같은 캐시를 씁니다. 하드코딩된 61개 경로는 기기 업데이트에서 깨지므로
버렸습니다. `ftermd env` 가 발견 결과를 shell 문법으로 출력하고, 래퍼는 그것을
`eval` 합니다. 데몬이 없으면 `/proc` 을 직접 훑어 채우고 캐시에 남깁니다.

**실측**: ART 변수를 하나도 받지 않은 데몬(`env -u BOOTCLASSPATH ...`)의 세션에서
`termux-battery-status`, `termux-toast`, `termux-clipboard-get`, `am broadcast` 넷 다
rc=0. 대조군으로 데몬 환경을 그대로 물려줬을 때(수정 전)는 `termux-battery-status` 가
출력 없이 멈췄습니다 — 오지 않을 콜백을 기다리고 있었습니다.

**확인 방법** — 다섯 중 하나만 빠져도 `am` 이 죽습니다. Termux 앱 셸에서 하나씩 빼보면
재현됩니다.

```bash
for V in BOOTCLASSPATH DEX2OATBOOTCLASSPATH ANDROID_ART_ROOT ANDROID_I18N_ROOT ANDROID_TZDATA_ROOT; do
  echo "unset $V -> $(env -u $V sh -c 'am broadcast -a android.intent.action.BATTERY_CHANGED 2>&1 | head -1')"
done
# unset ANDROID_DATA, ANDROID_ROOT, TERMUX_APP__* 는 영향 없음
```

Shizuku 이슈 트래커의 "rish returns nothing" 보고 다수가 이것으로 보입니다.

### 데몬은 파일 교체로 갱신되지 않습니다

`termux/install.sh` 가 데몬을 재시작하는 이유입니다. 실행 중인 프로세스는 디스크의 새
코드를 읽지 않습니다. `TERM_PROGRAM` 도 select() 크래시 수정도 **재시작 후에만**
적용됩니다.

재시작은 스크립트 자신의 세션에서 합니다(`setsid`). 스크립트는 보통 fterm 탭에서
돌리고, 그 탭의 셸은 데몬의 자식입니다. `stop` 이 PTY를 닫으면 셸이 SIGHUP을 받아
스크립트가 재시작 도중에 죽고, 데몬이 아예 없는 상태로 남습니다. 실측: 두 번 돌려
pid가 31319에서 31340으로 바뀌고, 교체된 데몬의 세션에서 `termux-battery-status` 가
rc=0으로 답합니다.

**환경 변수 문제를 없애는 원칙** 하나가 남습니다. "Termux에서는 되는데 여기서는 안
된다"가 나오면, 그 차이는 대개 **부모 프로세스가 물려준 환경**입니다. 세션을 띄우는
쪽이 변수 열한 개를 골라 넘기면 그 뒤로는 조용히 깨집니다 — 에러가 아니라 **아무 일도
일어나지 않는 것**으로 보이기 때문입니다. 그래서 지금은 물려받고, 부모의 정체를
설명하는 변수만 거릅니다(SSH_*, TERM, TERM_PROGRAM, LD_*).

## 유휴 사망 — 두 개의 원인, 둘 다 실측

증상은 "유휴 상태가 되면 터미널이 죽는 것"이었습니다. 원인이 **둘**이었고, 앞의 것만
고쳐서는 계속 죽었습니다.

### 1. 앱 프로세스가 동결됨 (Android 12+ freezer)

`dumpsys activity processes` 를 배경 상태에서 읽었습니다.

```
isFrozen=true   curProcState=16 (CACHED_EMPTY)
```

동결된 프로세스는 소켓을 읽지 않습니다. 넣어 둔 핑과 타임아웃은 **죽는 것을 알아채게**
할 뿐 **죽는 것을 막지 못합니다.** 해결은 플랫폼의 정답인 포그라운드 서비스입니다.

```
TerminalService.startForeground()  →  curProcState=4, isFrozen=false   (수정 후 실측)
```

Termux 자신이 같은 이유로 같은 것을 씁니다.

### 2. 데몬이 스스로 크래시 (진짜 원인)

앱을 고친 뒤에도 죽었습니다. `~/fterm/daemon.log` 는 다음과 같았습니다.

```
[ftermd] client idle for 120s, dropped
Traceback ... select.select(rfds, [], [], 1.0)
ValueError: file descriptor cannot be a negative integer (-1)
```

`drop_idle()` 이 **select용 fd 집합을 만든 뒤에** 소켓을 닫고 있었습니다. 닫힌 소켓은
fd `-1` 이 되어 select()가 예외를 던지고, 데몬이 종료되면서 **모든 살아있는 셸을 함께
데려갔습니다.**

수정: `drop_idle()` 을 fd 집합 구성 **전**으로 옮기고, 닫힌 fd는 집합에 넣지 않으며,
select 오류가 루프를 죽이지 않게 했습니다.

### 검증 (실기기)

```
30초 유휴  → curProcState=4, isFrozen=false
150초 유휴 (데몬 timeout 120초 초과) → daemon_alive=True × 8회
유휴 후 입력 → "echo IDLE-OK-$((7*6))"  →  화면에 IDLE-OK-42
```

`tests/jvm/harness/IdleDropTest.java` 가 회귀를 막습니다. 20회 급절단 접속 + 20초 유휴 +
소스 순서 검사(select 전에 drop)를 확인합니다.

## 데몬 — Rust (`termux/ftermd-rs`)

첫 구현은 python(`termux/ftermd`)이었습니다. 잘 돌지만, 구조상 세 가지가 실사용에서
걸렸습니다. 셋 다 **데몬 하나가 죽으면 모든 탭의 셸이 같이 죽는다**는 같은 뿌리입니다.

| 문제 | 지금 동작 | Rust 데몬 |
|---|---|---|
| 낡은 pidfile | `os.kill(pid, 0)` 만 보므로, 크래시 뒤 그 PID 를 다른 프로세스가 받으면 `already running` 을 찍고 영원히 안 뜹니다 | `~/.fterm/ftermd.lock` 의 `flock` — 커널이 소유하므로 어떻게 죽어도 저절로 풀립니다. `stop`/`status` 는 `/proc/<pid>/cmdline` 이 이 데몬인지 확인합니다 |
| 한 루프의 블로킹 | 모든 탭이 `select()` 하나를 공유합니다. PTY 입력이 차서 막힌 `os.write`, `redraw()` 의 50ms, 32MB 폰트 읽기가 그 루프를 잡습니다 | 세션마다 읽기·쓰기 스레드, 나머지는 유한 채널의 태스크. 막힌 탭은 그 탭만 멈춥니다 |
| 백프레셔 없음 | 앱이 소켓을 안 읽는 동안 `Client.outbuf` 가 상한 없이 커져 데몬이 OOM 으로 죽고, **모든 탭의 셸이 같이** 죽습니다 | 클라이언트마다 4MiB 예산. 넘으면 **그 연결만** 끊고, 세션·셸·ring 은 살아 있어 재접속하면 replay 로 복구됩니다 |

프로토콜은 한 바이트도 바꾸지 않았습니다. 상수·프레이밍·`E_*`·`S_ERROR`/`C_CLOSE` 동작,
`ART` 변수 상속과 캐시, `FTERM_HISTORY_BYTES`, 글꼴 경로, 세션 16개, ring 256KiB, `Modes`
복원 순서가 같습니다. `tests/run_daemon.sh` 하나가 이 프로토콜 표를 심사합니다. Rust 이식이
그 심사를 통과한 뒤 파이썬 구현은 삭제했습니다 — 하나의 프로토콜에 구현이 둘이면, 그 일치는
복사가 조심스러웠다는 사실 위에만 서 있고 언젠가 어긋납니다.

### 기기에서 빌드하고 켜기

Termux 안에서 직접 빌드합니다(크로스 컴파일이 아니라 그 기기에서). 프리빌드 바이너리로
설치하는 쪽은 위의 설치 절에 있는 한 줄짜리 `termux/setup.sh` 이고, 이 절은 소스를 고칠 때
쓰는 경로입니다.
```bash
pkg install rust
bash termux/install.sh    # 로컬 target/release/ftermd 가 없으면 cargo build --release 를 부릅니다
```

`install.sh` 는 `~/fterm/ftermd` 자리에 Rust 바이너리를 놓습니다. 그 뒤로는 앱도 keeper 도
같은 소켓으로 이야기합니다.

### 검증

CI 가 **같은 두 스위트**(`termux/test_client.py`, `tests/test_modes.py`)를 Rust 바이너리에
돌립니다. 추가로 `tests/test_daemon_hardening.py` 가 위 표의 세 가지를 실제 소켓으로
확인합니다(다른 프로세스의 pid 를 가진 pidfile, 읽지 않는 클라이언트, 입력을 안 읽는 탭).

## 이전 수정 (핑/타임아웃) — 여전히 유효하지만 충분하지 않았습니다

기기에서 "유휴 아니면 프리징"이 보고됐고, `IdleTest` 가 20초 유휴에서 세션이 죽는 것을
재현했습니다. 로그가 원인을 짚었습니다. 클라이언트가 **15.0초**에 끊겼습니다.

```
SO_TIMEOUT_MS = 15000   (읽기 한도)
IDLE_PING_MS  = 8000    (8초 자고 "8초 이상 조용하면 핑" 검사)
```

8초 자고 나서 "8초 넘게 조용했나"를 물으면 경계에 정확히 걸려 **핑을 건너뛰고**, 다음
기회가 16초가 됩니다. 읽기 한도(15초)보다 1초 늦습니다. 그래서 매번 15초 만에 죽었습니다.

수정: 핑을 **무조건 5초마다** 보내고 읽기 한도를 20초로 늘렸습니다. 핑 두 번을 잃어도
살아남습니다. 데몬 쪽은 120초 무응답 클라이언트를 ghost로 보고 버립니다(동결된 앱이
세션을 붙잡지 않게 하기 위해서입니다).

## 최신 안드로이드 대응 (targetSdk 36)

| 항목 | 처리 |
|---|---|
| API 35+ 강제 edge-to-edge | `EdgeToEdge` — 상태바/내비바/키보드 인셋을 탭 스트립·키 바 **높이**에 더합니다 (패딩만 주면 행이 0높이가 됩니다) |
| `setStatusBarColor` 무시됨 (35+) | 인셋으로 처리하고, 34 이하만 색을 지정합니다 |
| 예측형 백 (33+) | `OnBackInvokedCallback` 등록 — `onBackPressed()` 만으로는 "뒤로=ESC"가 죽습니다 |
| 600dpi 화면 | 글자 크기를 **dp**로 모델링합니다 (`12dp` → 45px). 이전에는 14px=3.7dp라 안 보였습니다 |
| 한글 폰트 | `assets/D2Coding.ttf` 를 실제로 로드합니다 (이전에는 assets에만 있고 쓰지 않았습니다) |
| sticky CTRL 누수 | `onCodePoint` 에서 한 글자 뒤 해제합니다 — 이전에는 `abc` 가 `^B^C^H` 로 갔습니다 |

## 검증 상태

실제 기기에서 설치·실행·렌더링·영문 입력·셸 연결을 확인했습니다. 한글 IME는 아래에서
설명합니다. 이 기기에서 `pm install` 이 `SecurityException: INTERACT_ACROSS_USERS_FULL`
로 막혀 있어 설치 자체는 못 했습니다. 대신 다음을 실제로 돌려 통과시켰습니다.

| 검증 | 방법 | 결과 |
|---|---|---|
| 데몬 프로토콜 9종 (인증 거부, 에코, 한글, resize, replay, close, OPEN 문자열, 링 밖 터미널 모드 복원, 끈 모드 유지) | `tests/run_daemon.sh` → `termux/test_client.py` | 통과 |
| 모드 추적기 와이어 16종 (선언 분할, 결합 파라미터, 무관한 CSI, ring 밖 복원, redraw 후 응답) | `tests/test_modes.py` | 통과 |
| 모드 추적기·환경·프레이밍·pidfile 유닛 58종 | `cargo test` (`termux/ftermd-rs`) | 통과 |
| ART 변수 없는 데몬에서 termux-api 4종 (battery·toast·clipboard·am broadcast) | 격리 포트 `FTERM_PORT=8813` 에 `env -u BOOTCLASSPATH` 로 기동 | 통과 (rc=0) |
| `install.sh` 재시작 (pid 교체 후 새 세션에서 배터리 조회) | 격리 `FTERM_HOME`·`FTERM_RUNDIR` 로 두 번 실행 | 통과 |
| `rish2` 캐시 없이 동작 (`ftermd` 없이 `/proc` 발견) | `env -i` 로 캐시 삭제 후 실행 | 통과 (uid=2000 shell) |
| 복원된 모드가 에뮬레이터에서 실제 마우스 ON이 되는지 7종 | `tests/jvm/core/ModeRestoreTest.java` | 통과 |
| Java `SocketTransport` 9종 (토큰 오류, 왕복, 한글, resize, pid, replay, close, 세션 분리) | 실제 `ftermd` 에 JVM으로 접속 | 통과 |
| `DaemonClient` SYNC 5종 (빈 데몬, 목록, 토큰 오류, 포트 거부, 종료 후 제거) | 같음 | 통과 |
| 폭 계산 15종 (한글 음절 2칸, 자모, emoji, 결합문자, zero-width) | `tests/run_core.sh` | 통과 |
| **ANSI 파서 23종** (한글 2칸 점유, 커서 위치, 자동 줄바꿈이 음절을 쪼개지 않음, SGR, OSC 제목, resize, 패킷 분할 UTF-8) | 실제 `TerminalEmulator` | 통과 |
| APK 빌드·서명 | `./build.sh` | 통과 (V3 서명, targetSdk 36) |
| 붙여넣기 12종 (bracketed paste `\e[200~..\e[201~`, ESC 주입 차단, 모드 해제) | `tests/jvm/core/PasteTest.java` | 통과 |
| 색상 147종 (mterm `ONE_HALF_DARK` 대조, OLED 순검정, 전 테마 4.5:1) | `tests/jvm/core/SchemesTest.java` | 통과 |
| 폰트 라우팅 65종 (한글/아이콘/라틴, 셀에 맞춘 배율, 베이스라인 보정) | `tests/jvm/core/FontsTest.java` | 통과 |
| 탭 닫기 9종 (plate 제거, transport 종료, Enter 경로) | `tests/jvm/core/TabCloseTest.java` | 통과 |
| 알림 17종 (OSC 9 / 777 파싱, 제목·본문, 오탐 방지, 앱 배선) | `tests/jvm/core/NotifyTest.java` | 통과 |
| 벨 13종 (VIBRATE 권한, 진동/비프, 활성 탭만, 설정 토글) | `tests/jvm/core/BellTest.java` | 통과 |
| 기기 설치·실행·렌더링·셸 연결 | `rish` 로 `pm install` 후 화면 확인 | 통과 |
| 기기 영문 입력 (`abc` → `a`,`b`,`c`) | daemon 로그 바이트 확인 | 통과 |
| 유휴/프리징 3종 (20초 유휴 후 세션 생존·사용 가능, 데몬이 클라이언트 유지, 끊긴 링크 즉시 종료) | `tests/jvm/harness/IdleTest.java` | 통과 |
| 데몬 크래시 회귀 5종 (20회 급절단, 세션 생존, 클라이언트 예산 구조) | `tests/jvm/harness/IdleDropTest.java` | 통과 |
| 구조 74종 (MainActivity 1,420줄·SettingsSheet 400줄 상한, 설정 페이지 7개 분리, 서비스·벨·keeper) | `tests/jvm/core/StructureTest.java` | 통과 |
| UI 19종 (드롭다운·팔레트·이름 고정·테마 색·Enter) | `tests/jvm/core/UiTest.java` | 통과 |
| 한글 IME 조합 16종 (ㅎ→하→한, 성장/축소, commit 꼬리, delete, 소스 대조) | `tests/jvm/core/ImeComposeTest.java` | 통과 |

### 자동 검증 (GitHub Actions)

위 표는 손으로 돌린 기록입니다. 지금은 `push`와 PR마다 `.github/workflows/ci.yml`이 러너에서
돌릴 수 있는 것을 자동으로 돌립니다.

| 잡 | 도는 것 | 조건 |
|---|---|---|
| `rust` | `cargo build --release`·`cargo test`, 그리고 데몬을 띄워 `test_client.py`·`test_modes.py`·`test_daemon_hardening.py` | 항상 |
| `java` | `tests/run_core.sh` (ANSI 파서·폭·팔레트·모드 복원) | 항상 |

데몬은 `FTERM_RUNDIR`·`FTERM_PORT`를 따로 받아 뜨므로 폰의 탭과 섞이지 않습니다. 러너가 확인할
수 없는 것(`install.sh`, 진짜 Android PTY, `termux-api`, 폰트 선택 UI, keeper, 도즈)은
`docs/ROADMAP.md` 에 "기기에서만 확인되는 것"으로 적어 두었습니다. 남은 작업 순서도 그 문서에
있습니다.

### 한글 입력 — termlib 방식

ConnectBot이 같은 문제를 푼 방식([connectbot#2352](https://github.com/connectbot/connectbot/issues/2352),
[termlib `ImeInputView`](https://github.com/connectbot/termlib))을 그대로 옮겼습니다.

핵심은 세 가지이고, 모두 소스에 주석으로 남겼습니다.

1. **조합을 터미널에 투영합니다.** IME는 키 입력마다 조합 **전체**를 보냅니다("하" → "한").
   터미널에는 조합 영역이 없으므로 늘어난 꼬리만 write하고, 줄어든 만큼 DEL을 보냅니다.
2. **`finishComposingText` 는 다시 보내지 않습니다.** 이미 화면에 있으므로 중복이 됩니다.
   원본 Termux가 `getEditable()` 을 통째로 보내던 것이 자모 분리의 원인이었습니다.
3. **`deleteSurroundingText` 는 `sendKeyEvent` 를 쓰지 않습니다.** 키 이벤트는 IME의
   editable을 비워서 조합 중인 음절을 중간에 버리게 만듭니다. DEL 바이트를 직접 씁니다.

`tests/jvm/core/ImeComposeTest.java`(16)가 이 규약을 검증하고, `TerminalView.java` 가 그
로직에서 벗어나지 않았는지 소스까지 대조합니다.

## 구조

```
app/src/io/github/crashedsystem/terminal/    MainActivity, Prefs, Schemes
app/src/com.termux/terminal/  벤더링 + Protocol / SessionTransport / SocketTransport / DaemonClient
app/src/com.termux/view/      벤더링 TerminalView / TerminalRenderer
termux/ftermd-rs              PTY 소유 데몬 (Rust: 토큰, ring replay, 세션 상한 16)
termux/fterm-keeper           앱 요청을 받아 데몬을 띄우는 Termux 쪽 절반 (python3)
tests/                        프로토콜, 전송, 파서, 폭 테스트
```

벤더링한 코드에서 고친 곳은 세 군데뿐입니다. `androidx.annotation` 의존 제거,
`TerminalBitmap` 의 `VANILLA_ICE_CREAM` 상수를 숫자 35로 바꾼 것, 그리고 `JNI` PTY 경로와
나란히 `SessionTransport` 분기를 넣은 `TerminalSession` 입니다. 나머지는 Termux 원본
그대로입니다.

## 한글이 왜 정확한가

`WcWidth` 는 U+1100–115F(자모)와 U+AC00–D7A3(음절)을 2칸으로 계산합니다. 렌더러는 측정된
글자 폭이 `wcwidth` 기대값과 다르면 그 코드포인트만 가로로 늘려 그리므로, 한글 글꼴의
전각 advance와 라틴 셀 폭이 어긋나도 격자가 밀리지 않습니다. IME는 `commitText` 로 완성형
음절을 한 번에 보내고, `TerminalEmulator` 는 UTF-8 시퀀스가 패킷 경계에서 끊겨도
버퍼링합니다(테스트 8번).

## 데몬 명령

```bash
ftermd start | stop | status | token | reset-token
ftermd fonts | font <url>     # 앱의 글꼴 목록·내려받기
ftermd env                    # ART 환경 변수를 shell 문법으로 출력 (rish 같은 래퍼가 eval)
```

환경 변수: `FTERM_PORT`(8791), `FTERM_SHELL`, `FTERM_RUNDIR`(`~/.fterm`),
`FTERM_HISTORY_BYTES`(256KiB), `FTERM_FONT_DIRS`(콜론으로 구분한 글꼴 검색 경로).
