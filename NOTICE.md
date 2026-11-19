# 제3자 고지

fterm은 다음 제3자 코드·글꼴·아이콘을 포함한다. 각 항목의 원문 라이선스는
`licenses/` 에 있다. 이 문서와 `LICENSE`, `licenses/` 는 빌드할 때 APK 안
(`assets/NOTICE.md`, `assets/LICENSE`, `assets/licenses/`)에 함께 들어간다 —
앱을 설치한 사람이 저장소 없이도 고지를 읽을 수 있어야 하기 때문이다.

## Termux terminal-emulator / terminal-view (Apache-2.0)

`app/src/com/termux/` 아래의 터미널 엔진·뷰는 [termux/termux-app] 의
`terminal-emulator` 와 `terminal-view` 라이브러리에서 가져왔다.

- 원본: https://github.com/termux/termux-app
- 라이선스: Apache License 2.0 (`licenses/Apache-2.0.txt`)

termux-app 저장소 전체는 GPLv3이지만, 위 두 라이브러리는 저장소의
`LICENSE.md` 에서 Apache-2.0 예외로 명시되어 있다. fterm이 가져온 것은 그 두
라이브러리뿐이고, `termux-shared` 등 GPLv3 부분은 포함하지 않는다.

Apache-2.0은 다음을 요구한다(4조): 저작권 고지와 라이선스 사본을 배포물에 포함할
것, 수정한 파일에 수정 사실을 밝힐 것. 이 저장소의 해당 파일들은 수정되었으며,
수정 내역은 git 이력에 있다.

    Copyright (C) 2012-2024 Termux contributors
    Copyright (C) 2012 Jack Palevich (Android Terminal Emulator)

## 글꼴 (SIL Open Font License 1.1)

`assets/fonts/` 의 세 글꼴은 모두 OFL-1.1 로 배포된다
(`licenses/OFL-1.1.txt`). 세 글꼴 모두 Reserved Font Name 을 선언하지 않으므로,
파일명을 바꾸지 않은 채 그대로 재배포한다.

| 파일 | 글꼴 | 저작권 |
|---|---|---|
| `Iosevka.ttf` | Iosevka | Copyright 2015-2025 Renzhi Li (Belleve Invis) |
| `PretendardHangul.ttf` | Pretendard | Copyright © 2023 길형진 (Kil Hyung-jin). Inter © Rasmus Andersson, Noto Sans CJK © Adobe, M PLUS 1p © Coji Morishita |
| `NerdSymbols.ttf` | Noto Emoji | Copyright 2013 Google LLC |

## Fluent UI System Icons (MIT)

`app/src/io/github/crashedsystem/fterm/GlyphPaths.java` 는 Microsoft의
[fluentui-system-icons] 에서 24px regular 경로를 추출해 생성한 것이다
(생성 스크립트: `tests/tools/icons.py`).

- 원본: https://github.com/microsoft/fluentui-system-icons
- 라이선스: MIT (`licenses/MIT.txt`)

    Copyright (c) Microsoft Corporation.

## Windows Terminal Retro 셰이더 (MIT)

`app/src/io/github/crashedsystem/fterm/RetroShader.java` 는 Windows Terminal 저장소의
`samples/PixelShaders/Retro.hlsl` 을 AGSL 로 옮긴 것이다. 셰이더 상수와 순서는
원본 그대로이고, 블러 커널만 모바일 GPU에 맞게 줄였다(파일 주석에 근거 기록).

- 원본: https://github.com/microsoft/terminal
- 라이선스: MIT (`licenses/MIT.txt`)

    Copyright (c) Microsoft Corporation.

## 앱 아이콘 (MIT, Windows Terminal 그림에서 파생)

`res/drawable/ic_launcher_fg.xml` 은 같은 저장소의 Windows Terminal 앱 아이콘
`src/cascadia/TerminalApp/Images/Terminal.svg` 를 Android 벡터 드로어블로 다시 적은
것이다. 3분할 헤더와 프롬프트(`>_`)의 도형 좌표를 원본에서 그대로 옮겼고, 색만 앱
팔레트(`#36383E` → `#202227` 배경)에 맞게 바꿨다. 원본과 마찬가지로 MIT 로 배포한다.

- 원본: https://github.com/microsoft/terminal/blob/main/src/cascadia/TerminalApp/Images/Terminal.svg
- 라이선스: MIT (`licenses/MIT.txt`)

    Copyright (c) Microsoft Corporation.

[termux/termux-app]: https://github.com/termux/termux-app
[fluentui-system-icons]: https://github.com/microsoft/fluentui-system-icons

## fterm 자체

위 제3자 부분을 뺀 fterm의 나머지 코드는 MIT License 다. 전문은 저장소 루트의
`LICENSE` 에 있다(Copyright (c) 2026 CrashedSystem).

## 포함하지 않은 것

mterm의 Miku 테마 배경 그림은 CC BY-NC(비영리) 라이선스라 이 저장소에 넣지 않았다.
색 구성표(`Schemes.oneHalfDark()`)는 그 테마의 One Half Dark 팔레트만 옮긴 것이고 그림과
무관하게 동작한다. 그림은 앱에 내장하지 않는다 — 사용자가 기기에서 고른 이미지를 앱 폴더에
복사해 쓰는 일반 기능(`BackgroundStore`)으로 대체했고, 어떤 이미지도 저장소에 커밋되지
않는다.
