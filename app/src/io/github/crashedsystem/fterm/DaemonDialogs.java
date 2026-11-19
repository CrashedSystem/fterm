package io.github.crashedsystem.fterm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/**
 * The dialogs shown when the daemon will not come up: the keeper is gone, shared storage is
 * shut, or the keeper is alive but nothing answers.
 *
 * <p>They are the same shape - a diagnosis, one button that opens the screen which fixes it, a
 * way to hand the app a token instead, and a way out - so they live together, and out of the
 * activity, which should be laying out a terminal rather than explaining one.
 *
 * <p>Every one of them names a place the user can go and come back from; the activity retries
 * on that return (see {@code DaemonBoot#retryOnResume}), so "닫고 Termux에서 실행한 뒤 돌아오기"
 * is a working path and not a dead end.
 */
final class DaemonDialogs {

    /** The daemon's token is hex; sixteen characters is the shortest the app will accept. */
    private static final java.util.regex.Pattern TOKEN_SHAPE =
            java.util.regex.Pattern.compile("^[0-9a-fA-F]{16,}$");

    private DaemonDialogs() {}

    /** Where a manually entered token, or a cancel, comes back to. */
    interface TokenInput {
        /** @param token the pasted token, or null when the user backed out. */
        void onToken(String token);
    }

    /**
     * Last resort when the app cannot provision the daemon itself: the user runs one command in
     * Termux and pastes the token once.
     *
     * <p>A token copied in Termux is still on the clipboard when this opens, so it is filled in
     * already - the alternative is a paste into a dark one-line field that shows nothing when it
     * worked.
     */
    static void manualToken(Activity a, final TokenInput onToken) {
        final android.widget.EditText input = new android.widget.EditText(a);
        input.setHint("~/fterm/ftermd token 출력값");
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setTextColor(0xffe6e6e6);
        input.setHintTextColor(0xff888888);

        String copied = clipboardToken(a);
        if (copied != null) input.setText(copied);

        new AlertDialog.Builder(a)
                .setTitle("Termux 연결")
                .setMessage("Termux에서 다음을 한 번 실행하세요:\n\n"
                        + "  ~/fterm/ftermd start\n"
                        + "  ~/fterm/ftermd token\n\n"
                        + (copied != null
                                ? "클립보드에 있던 토큰을 채워 두었습니다. 다르면 지우고 붙여넣으세요."
                                : "출력된 토큰을 붙여넣으세요."))
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("저장", (d, w) ->
                        onToken.onToken(input.getText().toString().trim()))
                .setNeutralButton("명령 복사", (d, w) -> {
                    copy(a, "~/fterm/ftermd start && ~/fterm/ftermd token");
                    toast(a, "복사됨 — Termux에 붙여넣고 실행하세요");
                    onToken.onToken(null);
                })
                .setNegativeButton("닫기", (d, w) -> onToken.onToken(null))
                .show();
    }

    /**
     * The keeper is not there, so nothing is polling for the app's request file.
     *
     * <p>The fix is one line in Termux, and the button copies it before opening Termux: the
     * clipboard is the only thing that crosses between an app's dialog and a shell prompt.
     */
    static void keeperMissing(Activity a, Runnable onManualToken) {
        new AlertDialog.Builder(a)
                .setTitle("Termux 준비가 필요합니다")
                .setMessage(DaemonLauncher.keeperInstructions())
                .setPositiveButton("명령 복사 & Termux 열기", (d, w) -> {
                    copy(a, DaemonLauncher.SETUP_COMMAND);
                    if (!openTermux(a)) {
                        toast(a, "Termux가 설치되어 있지 않습니다 — F-Droid에서 설치한 뒤 다시 시도하세요");
                    }
                })
                .setNeutralButton("직접 토큰 입력", (d, w) -> onManualToken.run())
                .setNegativeButton("닫기", null)
                .show();
    }

    /**
     * The request file could not be written, so the keeper cannot have heard anything.
     *
     * <p>Distinct from {@link #keeperMissing}: the keeper may be running perfectly well; what
     * failed is the one channel that reaches it. Saying "install the keeper" here would send the
     * user to fix something that is not broken. On Android 11+ the usual cause is the all-files
     * switch, which no runtime prompt can ask for - only a settings screen the user taps
     * through - so that screen is the positive button.
     */
    static void storageBlocked(Activity a, Runnable onManualToken) {
        boolean allowed = DaemonBoot.hasAllFilesAccess();
        new AlertDialog.Builder(a)
                .setTitle(allowed ? "저장소에 요청 파일을 쓸 수 없습니다" : "저장소 권한이 필요합니다")
                .setMessage("fterm은 /sdcard/Download/fterm-up 파일로 Termux의 keeper에게 데몬을\n"
                        + "요청합니다. 그 파일을 쓰지 못하면 앱이 스스로 데몬을 띄울 수 없습니다.\n\n"
                        + (allowed
                                ? "권한은 허용되어 있는데도 쓰지 못했습니다. /sdcard/Download 가 있는지,\n"
                                        + "쓰기 가능한지 확인하세요."
                                : "아래 버튼이 '모든 파일 접근' 설정 화면을 엽니다. 허용한 뒤 이 앱으로\n"
                                        + "돌아오면 바로 다시 시도합니다.")
                        + "\n\nTermux에서 한 번 실행하면 토큰을 직접 받을 수도 있습니다:\n"
                        + "  ~/fterm/ftermd token")
                .setPositiveButton("권한 설정 열기", (d, w) -> openStorageSettings(a))
                .setNeutralButton("직접 토큰 입력", (d, w) -> onManualToken.run())
                .setNegativeButton("닫기", null)
                .show();
    }

    static void daemonSlow(Activity a, Runnable onRetry, Runnable onManualToken) {
        new AlertDialog.Builder(a)
                .setTitle("데몬이 응답하지 않습니다")
                .setMessage("keeper는 살아있지만 데몬이 뜨지 않았습니다.\n\n"
                        + "Termux에서 확인:\n"
                        + "  ~/fterm/ftermd status\n"
                        + "  tail ~/fterm/daemon.log")
                .setPositiveButton("다시 시도", (d, w) -> onRetry.run())
                .setNeutralButton("직접 토큰 입력", (d, w) -> onManualToken.run())
                .setNegativeButton("닫기", null)
                .show();
    }

    /**
     * The all-files screen for this app, with the list-wide screen as fallback.
     *
     * <p>Some ROMs drop the per-app intent; the general list is one extra tap and beats no
     * screen at all. Below Android 11 there is no such switch - shared storage is a normal
     * runtime permission there - so the app's own page, where that permission lives, is the
     * closest thing to the screen being asked for.
     */
    private static void openStorageSettings(Activity a) {
        Uri self = Uri.parse("package:" + a.getPackageName());
        if (Build.VERSION.SDK_INT < 30) {
            open(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, self));
            return;
        }
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, self));
        } catch (Exception e) {
            if (!open(a, new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))) {
                toast(a, "설정 > 앱 > 터미널 > 모든 파일 접근을 허용하세요");
            }
        }
    }

    /** Termux, if it is installed. False means the caller has to tell the user where to get it. */
    private static boolean openTermux(Activity a) {
        Intent launch = a.getPackageManager().getLaunchIntentForPackage("com.termux");
        if (launch == null) return false;
        return open(a, launch);
    }

    private static boolean open(Activity a, Intent intent) {
        try {
            a.startActivity(intent);
            return true;
        } catch (Exception e) {
            // A ROM without the activity, or a launch the system refuses: the caller falls back
            // to naming the screen instead of dying on an unhandled exception.
            return false;
        }
    }

    /** The clipboard, when it holds something shaped like a token. */
    private static String clipboardToken(Activity a) {
        ClipboardManager cm = (ClipboardManager) a.getSystemService(Activity.CLIPBOARD_SERVICE);
        if (cm == null) return null;
        try {
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence text = clip.getItemAt(0).coerceToText(a);
            if (text == null) return null;
            String candidate = text.toString().trim();
            return TOKEN_SHAPE.matcher(candidate).matches() ? candidate : null;
        } catch (Throwable t) {
            // hasPrimaryClip() is API 28 and this app runs from 26; a clip the system refuses to
            // hand over is simply not a token.
            return null;
        }
    }

    /** The one thing these dialogs have in common besides their shape: a command to copy. */
    private static void copy(Activity a, String command) {
        ClipboardManager cm = (ClipboardManager) a.getSystemService(Activity.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("fterm", command));
    }

    private static void toast(Activity a, String text) {
        Toast.makeText(a, text, Toast.LENGTH_LONG).show();
    }
}
