# 업스트림 기준점

`app/src/com.termux/` 의 파일은 [termux/termux-app](https://github.com/termux/termux-app)에서
가져와 이 앱에 맞게 고친 것입니다. 그런데 **어느 커밋에서 가져왔는지가 기록되어 있지 않았습니다.**
그래서 나중에 업스트림의 터미널 코어 버그 수정(이스케이프 처리, 폭 계산, 식스엘 등)을 가져오려면
"우리가 무엇을 고쳤는지"를 다시 알아내야 하고, 그 사이 업스트림은 계속 움직입니다.

## 기준점

```
termux/termux-app  master  8629e632fcb95da272221be327db653fb24befe9
```

이 커밋을 기준으로 잡은 근거는 **바이트 단위 일치**입니다. 아래 "동일"로 표시된 파일들은 이
커밋의 파일과 한 바이트도 다르지 않고, 특히 `TerminalSixel.java`·`ITermImage.java`·
`AndroidUtils.java` 는 이 커밋에는 있고 v0.118.3 태그에는 없는 파일입니다. 즉 벤더 시점은
v0.118.3 이후이고, 이 커밋과 내용이 일치합니다.

> 기기에 설치된 Termux 는 0.118.3 이지만, 벤더한 코드는 그 태그가 아닙니다. 둘을 섞으면
> "왜 식스엘 코드가 없지"로 시작해 한 시간을 잃습니다.

## 파일별 상태

`tools/upstream-diff.sh` 로 언제든 다시 뽑을 수 있습니다. 지금: 동일 15 · 수정 11 · 이 저장소 4.

| 파일 | 기준점 대비 |
|---|---|
| `terminal/AndroidUtils.java` | 동일 |
| `terminal/ByteQueue.java` | 동일 |
| `terminal/ITermImage.java` | 동일 |
| `terminal/JNI.java` | 동일 |
| `terminal/KeyHandler.java` | 동일 |
| `terminal/Logger.java` | 동일 |
| `terminal/TerminalColorScheme.java` | 동일 |
| `terminal/TerminalColors.java` | 동일 |
| `terminal/TerminalRow.java` | 동일 |
| `terminal/TerminalSixel.java` | 동일 |
| `terminal/TextStyle.java` | 동일 |
| `terminal/WcWidth.java` | 동일 |
| `view/GestureAndScaleRecognizer.java` | 동일 |
| `view/support/PopupWindowCompatGingerbread.java` | 동일 |
| `view/textselection/CursorController.java` | 동일 |
| `terminal/TerminalBitmap.java` | +1 / -1 |
| `terminal/TerminalBuffer.java` | +27 / -1 |
| `terminal/TerminalEmulator.java` | +92 / -4 |
| `terminal/TerminalOutput.java` | +31 / -0 |
| `terminal/TerminalSession.java` | +102 / -9 |
| `terminal/TerminalSessionClient.java` | +23 / -10 |
| `view/TerminalRenderer.java` | +71 / -0 |
| `view/TerminalView.java` | +485 / -71 |
| `view/TerminalViewClient.java` | +15 / -0 |
| `view/textselection/TextSelectionCursorController.java` | +21 / -3 |
| `view/textselection/TextSelectionHandleView.java` | +1 / -1 |
| `terminal/DaemonClient.java` | 이 저장소 (업스트림에 없음) |
| `terminal/Protocol.java` | 이 저장소 (업스트림에 없음) |
| `terminal/SessionTransport.java` | 이 저장소 (업스트림에 없음) |
| `terminal/SocketTransport.java` | 이 저장소 (업스트림에 없음) |

"이 저장소" 로 표시한 네 파일은 데몬 링크(프레이밍, 세션 전송, 프로토콜 상수)라 업스트림에
대응물이 없습니다. `Protocol.java` 의 상수는 `termux/ftermd`·`termux/ftermd-rs` 와 같은 값이어야
하고, 그것은 `tests/test_protocol.py` 가 검사합니다.

`TerminalView`·`TextSelectionCursorController` 의 최근 증가분은 마우스 오른쪽 클릭 메뉴 때문입니다.
업스트림은 `showContextMenu()` 가 액티비티의 `onCreateContextMenu` 에 닿는 것을 전제하는데 이 앱은
그 리스너를 두지 않으므로, 뷰가 `ContextMenuRequest` 로 호스트에 넘기고(복사·붙여넣기·전체 선택·
찾기·설정), "전체 선택" 은 터치 지점에서만 시작할 수 있는 선택 컨트롤러에 범위를 직접 주는
`selectAll(columns, firstRow, lastRow)` 를 추가했습니다. 업스트림 수정을 3-way 로 합칠 때 이 두
갈래가 우리 쪽에 남아야 합니다.

## 업스트림 수정을 가져오는 순서

```bash
bash tools/upstream-diff.sh                    # 1. 지금 우리가 무엇을 고쳤는지 확인
bash tools/upstream-diff.sh <new-commit>       # 2. 새 기준점과의 차이
```

1. 위 스크립트로 "우리가 고친 것"을 먼저 눈으로 익힙니다. 이 목록이 없으면 3-way 병합은
   업스트림 변경을 우리 변경 위에 겹쳐 놓는 일이 아니라, 우리 변경을 지우는 일이 됩니다.
2. 새 기준점의 파일을 받아 우리 파일과 3-way diff(`git merge-file`)로 합칩니다.
3. `tests/run_core.sh`(ANSI 파서·폭·모드 복원)와 `tests/test_protocol.py` 를 돌립니다.
   이 두 개가 터미널 코어와 데몬 경계를 지킵니다.
4. **이 문서의 해시와 표를 새 값으로 바꿉니다.** 표가 낡으면 다음 사람이 같은 일을 다시 합니다.
