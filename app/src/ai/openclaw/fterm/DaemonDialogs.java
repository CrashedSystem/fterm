package ai.openclaw.fterm;

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

    static void keeperMissing(Activity a, Runnable onDismiss) {
        new AlertDialog.Builder(a)
                .setTitle("Termux 준비가 필요합니다")
                .setMessage(DaemonLauncher.keeperInstructions())
                .setPositiveButton("명령 복사", (d, w) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            a.getSystemService(Activity.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(
                            android.content.ClipData.newPlainText("fterm", COMMAND));
                    Toast.makeText(a, "복사됨 — Termux에 붙여넣고 실행하세요",
                            Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("닫기", (d, w) -> onDismiss.run())
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
