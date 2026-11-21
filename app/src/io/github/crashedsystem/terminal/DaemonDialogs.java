package io.github.crashedsystem.terminal;

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
 * on that return (see {@code DaemonBoot#retryOnResume}), so "close this, run the line in
 * Termux, come back" is a working path and not a dead end.
 *
 * <p>All prose is a resource. The one literal is {@link DaemonLauncher#SETUP_COMMAND}, which is
 * a shell line: translating it would make it unrunnable.
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
        input.setHint(AppStrings.s(R.string.i18n_0072));
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setTextColor(0xffe6e6e6);
        input.setHintTextColor(0xff888888);

        String copied = clipboardToken(a);
        if (copied != null) input.setText(copied);

        new AlertDialog.Builder(a)
                .setTitle(AppStrings.s(R.string.i18n_0073))
                .setMessage(AppStrings.s(R.string.i18n_0074)
                        + "  ~/fterm/ftermd start\n"
                        + "  ~/fterm/ftermd token\n\n"
                        + (copied != null
                                ? AppStrings.s(R.string.i18n_0401)
                                : AppStrings.s(R.string.i18n_0075)))
                .setView(input)
                .setCancelable(false)
                .setPositiveButton(AppStrings.s(R.string.i18n_0057), (d, w) ->
                        onToken.onToken(input.getText().toString().trim()))
                .setNeutralButton(AppStrings.s(R.string.i18n_0079), (d, w) -> {
                    copy(a, "~/fterm/ftermd start && ~/fterm/ftermd token");
                    toast(a, AppStrings.s(R.string.i18n_0077));
                    onToken.onToken(null);
                })
                .setNegativeButton(AppStrings.s(R.string.i18n_0029), (d, w) -> onToken.onToken(null))
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
                .setTitle(AppStrings.s(R.string.i18n_0078))
                .setMessage(DaemonLauncher.keeperInstructions())
                .setPositiveButton(AppStrings.s(R.string.i18n_0402), (d, w) -> {
                    copy(a, DaemonLauncher.SETUP_COMMAND);
                    if (!openTermux(a)) {
                        toast(a, AppStrings.s(R.string.i18n_0403));
                    }
                })
                .setNeutralButton(AppStrings.s(R.string.i18n_0404), (d, w) -> onManualToken.run())
                .setNegativeButton(AppStrings.s(R.string.i18n_0029), null)
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
                .setTitle(AppStrings.s(allowed ? R.string.i18n_0080 : R.string.i18n_0405))
                .setMessage(AppStrings.s(R.string.i18n_0081)
                        + AppStrings.s(R.string.i18n_0082)
                        + (allowed
                                ? AppStrings.s(R.string.i18n_0406)
                                : AppStrings.s(R.string.i18n_0407))
                        + "\n\n" + AppStrings.s(R.string.i18n_0408)
                        + "\n  ~/fterm/ftermd token")
                .setPositiveButton(AppStrings.s(R.string.i18n_0409), (d, w) -> openStorageSettings(a))
                .setNeutralButton(AppStrings.s(R.string.i18n_0404), (d, w) -> onManualToken.run())
                .setNegativeButton(AppStrings.s(R.string.i18n_0029), null)
                .show();
    }

    static void daemonSlow(Activity a, Runnable onRetry, Runnable onManualToken) {
        new AlertDialog.Builder(a)
                .setTitle(AppStrings.s(R.string.i18n_0087))
                .setMessage(AppStrings.s(R.string.i18n_0088)
                        + AppStrings.s(R.string.i18n_0089)
                        + "  ~/fterm/ftermd status\n"
                        + "  tail ~/fterm/daemon.log")
                .setPositiveButton(AppStrings.s(R.string.i18n_0090), (d, w) -> onRetry.run())
                .setNeutralButton(AppStrings.s(R.string.i18n_0404), (d, w) -> onManualToken.run())
                .setNegativeButton(AppStrings.s(R.string.i18n_0029), null)
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
                toast(a, AppStrings.s(R.string.i18n_0410));
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
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("terminal", command));
    }

    private static void toast(Activity a, String text) {
        Toast.makeText(a, text, Toast.LENGTH_LONG).show();
    }
}
