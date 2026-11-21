package io.github.crashedsystem.terminal;

import android.app.Activity;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Getting the daemon up and its token into the app.
 *
 * <p>This is a small state machine with four endings - a token, no keeper, a keeper that cannot
 * reach the daemon, and shared storage that will not take the request file - and it was living
 * in the middle of a terminal layout. Moving it here keeps {@code MainActivity} a coordinator
 * (there is a test that measures exactly that) and gives the four endings one place to be read
 * together.
 *
 * <p>The channel is a file, not a broadcast: Termux's {@code RUN_COMMAND} broadcast is silently
 * ignored by the Termux build on this device, so the app writes a request file, the keeper polls
 * for it, starts the daemon and publishes the token next to it. That is why "storage blocked"
 * is one of the endings - with no writable {@code /sdcard/Download} there is no way to ask.
 *
 * <p>Two of the four endings name something the user can fix outside the app: the all-files
 * switch in Settings, and the Termux install. Both send the user away, and both are worth
 * retrying the moment they come back - that is what {@link #start} and {@link #retryOnResume}
 * are for. The flags below exist so that neither a resume nor a failure can start a second
 * poll on top of the first.
 */
final class DaemonBoot {

    /** What the activity does about each ending. All are called back on the calling thread. */
    interface Host {
        /** The token arrived: store it and open the first tab. */
        void ready(String token);

        /** The keeper is not installed, or Termux was killed and nobody polls any more. */
        void keeperMissing();

        /** The keeper is alive but the daemon did not come up (yet). */
        void daemonSlow();

        /** The request file could not be written, so nothing was ever asked. */
        void storageBlocked();
    }

    /** ~4.5 s of polling: long enough for a keeper that polls once a second to notice. */
    private static final int MAX_ATTEMPTS = 15;
    private static final long ATTEMPT_MS = 300;

    private final Activity mActivity;
    private final Handler mUi;
    private final FrameLayout mFrame;
    private final View mTerminal;

    /** A poll is in flight; a resume must not stack a second one on top of it. */
    private boolean mRunning;
    /**
     * The last attempt ended somewhere the user can come back from: they went to the all-files
     * screen or to Termux to fix it. Cleared as soon as a retry is handed out, so every return
     * buys at most one attempt - otherwise a resume inside a failing attempt would re-enter
     * itself.
     */
    private boolean mWaiting;
    /**
     * The user has left the app since that ending. A resume is only a return if they did: the
     * first onResume of a launch arrives right after onCreate started the first attempt, and
     * retrying there would stack a second dialog on the one that is still on screen.
     */
    private boolean mLeft;

    DaemonBoot(Activity activity, Handler ui, FrameLayout frame, View terminal) {
        mActivity = activity;
        mUi = ui;
        mFrame = frame;
        mTerminal = terminal;
    }

    /**
     * Bring the daemon up without the user leaving the app.
     *
     * <p>On Android 11+ the write into {@code /sdcard/Download} needs all-files access first.
     * Asking before it is granted is not a slow failure, it is a certain one - the channel to
     * the keeper is that file - so the permission ending comes first and no request is written.
     *
     * <p>Writes the request file the Termux-side keeper watches, then polls for the token it
     * publishes. If the write fails there is no point polling either: that ending is reported
     * immediately rather than after fifteen attempts that would blame the keeper for not
     * answering.
     */
    void start(final Host host) {
        if (mRunning) return;
        mRunning = true;
        mWaiting = false;
        splash(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0071));
        if (!hasAllFilesAccess() || !DaemonLauncher.requestStart()) {
            restore();
            settle(true);
            host.storageBlocked();
            return;
        }
        poll(0, host);
    }

    private void poll(final int attempt, final Host host) {
        String token = DaemonLauncher.readToken();
        if (token != null) {
            settle(false);
            host.ready(token);
            return;
        }
        if (attempt < MAX_ATTEMPTS) {
            // Ask once more halfway: the first request can land before the keeper's loop wakes,
            // and the keeper deletes the request file once it has acted on it.
            if (attempt == 4) DaemonLauncher.requestStart();
            mUi.postDelayed(() -> poll(attempt + 1, host), ATTEMPT_MS);
            return;
        }
        restore();
        settle(true);
        if (!DaemonLauncher.keeperAlive()) {
            host.keeperMissing();
        } else {
            host.daemonSlow();
        }
    }

    private void settle(boolean waiting) {
        mRunning = false;
        mWaiting = waiting;
    }

    /** The user is leaving the app; a resume after this is a return worth one attempt. */
    void leftApp() {
        mLeft = true;
    }

    /**
     * True when returning to the app is worth one more attempt.
     *
     * <p>The user left to grant all-files access or to paste the install line into Termux, and
     * both are pointless unless the app tries again by itself: it would sit on a dead frame
     * until they relaunched it. Callers pass whether a tab already exists, because a manual
     * token path opens one and that tab is the user's answer, not a reason to ask again.
     */
    boolean retryOnResume(boolean hasTabs) {
        if (!mLeft) return false;
        mLeft = false;
        if (mRunning || !mWaiting || hasTabs) return false;
        mWaiting = false;
        return true;
    }

    /**
     * True when this app may write outside its sandbox.
     *
     * <p>Android 11 (API 30) is where scoped storage became the rule; below it, shared storage is
     * reachable with the older storage permissions. The check is the same one the platform
     * makes when the write happens, so a failure here is the honest answer rather than a guess.
     */
    static boolean hasAllFilesAccess() {
        if (Build.VERSION.SDK_INT < 30) return true;
        return Environment.isExternalStorageManager();
    }

    /** Say what is happening where the terminal will be, instead of showing a void. */
    void splash(String text) {
        if (mFrame == null) return;
        int px = dp(24);
        TextView splash = new TextView(mActivity);
        splash.setText(text);
        splash.setTextColor(0xffcfcfcf);
        splash.setTextSize(13);
        UiFont.apply(splash, false);
        splash.setPadding(px, px, px, px);
        splash.setGravity(Gravity.CENTER);
        mFrame.removeAllViews();
        mFrame.addView(splash);
    }

    /** Put the terminal view back after a splash. */
    void restore() {
        if (mFrame != null && mTerminal != null && mTerminal.getParent() == null) {
            mFrame.removeAllViews();
            mFrame.addView(mTerminal);
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                mActivity.getResources().getDisplayMetrics());
    }
}
