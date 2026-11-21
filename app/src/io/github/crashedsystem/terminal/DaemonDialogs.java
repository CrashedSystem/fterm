package io.github.crashedsystem.terminal;

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

    /**
     * What the user runs once in Termux when the keeper is missing.
     *
     * <p>It fetches the installer rather than assuming a checkout: the app is downloaded as an
     * APK, so there is no {@code ~/projects/fterm} on a user's phone. The pipe into bash is
     * what the README documents too, so the two never disagree.
     */
    private static final String COMMAND =
            "curl -fsSL https://raw.githubusercontent.com/CrashedSystem/fterm/master/termux/install.sh | bash";

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
    /**
     * Whether a dialog can still be shown on this activity.
     *
     * <p>These dialogs are opened from a worker's callback: the daemon takes seconds to answer,
     * and by then the user may have left. Calling {@code show()} on a finished activity throws
     * {@link android.view.WindowManager.BadTokenException}, which is fatal - it was the crash
     * behind "the app keeps stopping" after closing it while it was connecting.
     */
    private static boolean canShow(Activity a) {
        if (a == null || a.isFinishing()) return false;
        return !(android.os.Build.VERSION.SDK_INT >= 17 && a.isDestroyed());
    }

    static void manualToken(Activity a, final TokenInput onToken) {
        if (!canShow(a)) return;
        final android.widget.EditText input = new android.widget.EditText(a);
        input.setHint(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0072));
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setTextColor(0xffe6e6e6);
        input.setHintTextColor(0xff888888);

        new AlertDialog.Builder(a)
                .setTitle(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0073))
                .setMessage(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0074)
                        + "  ~/fterm/ftermd start\n"
                        + "  ftermd token\n\n"
                        + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0075))
                .setView(input)
                .setCancelable(false)
                .setPositiveButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0057), (d, w) ->
                        onToken.onToken(input.getText().toString().trim()))
                .setNegativeButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0076), (d, w) -> {
                    copy(a, "ftermd start && ftermd token");
                    Toast.makeText(a, io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0077),
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
        if (!canShow(a)) return;
        new AlertDialog.Builder(a)
                .setTitle(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0078))
                .setMessage(DaemonLauncher.keeperInstructions())
                .setPositiveButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0079), (d, w) -> {
                    copy(a, COMMAND);
                    Toast.makeText(a, io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0077),
                            Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0029), (d, w) -> onDismiss.run())
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
        if (!canShow(a)) return;
        new AlertDialog.Builder(a)
                .setTitle(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0080))
                .setMessage(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0081)
                        + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0082)
                        + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0083)
                        + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0084)
                        + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0085)
                        + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0086)
                        + "  ~/fterm/ftermd token")
                .setPositiveButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0029), (d, w) -> onDismiss.run())
                .show();
    }

    static void daemonSlow(Activity a, Runnable onRetry, Runnable onDismiss) {
        if (!canShow(a)) return;
        new AlertDialog.Builder(a)
                .setTitle(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0087))
                .setMessage(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0088)
                        + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0089)
                        + "  ~/fterm/ftermd status\n"
                        + "  tail ~/fterm/daemon.log")
                .setPositiveButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0090), (d, w) -> onRetry.run())
                .setNegativeButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0029), (d, w) -> onDismiss.run())
                .show();
    }
}
