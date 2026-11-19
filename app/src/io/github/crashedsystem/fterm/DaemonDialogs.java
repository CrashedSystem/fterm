package io.github.crashedsystem.fterm;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;

/**
 * The two dialogs shown when the daemon will not come up: the keeper is gone, or the keeper
 * is alive but the daemon is not.
 *
 * <p>They are the same shape - a diagnosis, a command to copy, and a way out - so they live
 * together, and out of the activity, which should be laying out a terminal rather than
 * explaining one.
 */
final class DaemonDialogs {

    private static final String COMMAND = "cd ~/projects/fterm && bash termux/install.sh";

    private DaemonDialogs() {}

    /** Where a manually entered token, or a cancel, comes back to. */
    interface TokenInput {
        /** @param token the pasted token, or null when the user backed out. */
        void onToken(String token);
    }

    /**
     * Last resort when the app cannot provision the daemon itself: the user runs the two
     * commands in Termux and pastes the token once. This used to live in the activity, where
     * it was the longest thing in the class that was not about the terminal.
     */
    static void manualToken(Activity a, final TokenInput onToken) {
        final android.widget.EditText input = new android.widget.EditText(a);
        input.setHint("Termux에서 `ftermd token` 출력값");
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setTextColor(0xffe6e6e6);
        input.setHintTextColor(0xff888888);

        new AlertDialog.Builder(a)
                .setTitle("Termux 연결")
                .setMessage("Termux에서 다음을 한 번 실행하세요:\n\n"
                        + "  ~/fterm/ftermd start\n"
                        + "  ftermd token\n\n"
                        + "출력된 토큰을 붙여넣으세요.")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("저장", (d, w) ->
                        onToken.onToken(input.getText().toString().trim()))
                .setNegativeButton("Termux 명령 복사", (d, w) -> {
                    copy(a, "ftermd start && ftermd token");
                    Toast.makeText(a, "복사됨 — Termux에 붙여넣고 실행하세요",
                            Toast.LENGTH_LONG).show();
                    onToken.onToken(null);
                })
                .show();
    }

    /** The one thing these dialogs have in common besides their shape: a command to copy. */
    private static void copy(Activity a, String command) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                a.getSystemService(Activity.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(
                android.content.ClipData.newPlainText("fterm", command));
    }

    static void keeperMissing(Activity a, Runnable onDismiss) {
        new AlertDialog.Builder(a)
                .setTitle("Termux 준비가 필요합니다")
                .setMessage(DaemonLauncher.keeperInstructions())
                .setPositiveButton("명령 복사", (d, w) -> {
                    copy(a, COMMAND);
                    Toast.makeText(a, "복사됨 — Termux에 붙여넣고 실행하세요",
                            Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("닫기", (d, w) -> onDismiss.run())
                .show();
    }

    /**
     * The request file could not be written, so the keeper cannot have heard anything.
     *
     * <p>Distinct from {@link #keeperMissing}: the keeper may be running perfectly well; what
     * failed is the one channel that reaches it. Saying "install the keeper" here would send the
     * user to fix something that is not broken.
     */
    static void storageBlocked(Activity a, Runnable onDismiss) {
        new AlertDialog.Builder(a)
                .setTitle("저장소에 요청 파일을 쓸 수 없습니다")
                .setMessage("fterm은 /sdcard/Download/fterm-up 파일로 Termux의 keeper에게 데몬을\n"
                        + "요청합니다. 그 파일을 쓰지 못하면 앱이 스스로 데몬을 띄울 수 없습니다.\n\n"
                        + "확인할 것:\n"
                        + "  · 저장소 권한이 허용되어 있는지\n"
                        + "  · /sdcard/Download 가 쓰기 가능한지\n\n"
                        + "Termux에서 한 번 실행하면 토큰을 직접 받을 수도 있습니다:\n"
                        + "  ~/fterm/ftermd token")
                .setPositiveButton("닫기", (d, w) -> onDismiss.run())
                .show();
    }

    static void daemonSlow(Activity a, Runnable onRetry, Runnable onDismiss) {
        new AlertDialog.Builder(a)
                .setTitle("데몬이 응답하지 않습니다")
                .setMessage("keeper는 살아있지만 데몬이 뜨지 않았습니다.\n\n"
                        + "Termux에서 확인:\n"
                        + "  ~/fterm/ftermd status\n"
                        + "  tail ~/fterm/daemon.log")
                .setPositiveButton("다시 시도", (d, w) -> onRetry.run())
                .setNegativeButton("닫기", (d, w) -> onDismiss.run())
                .show();
    }
}
