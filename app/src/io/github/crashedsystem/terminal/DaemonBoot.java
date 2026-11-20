package io.github.crashedsystem.terminal;

import android.app.Activity;
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

    DaemonBoot(Activity activity, Handler ui, FrameLayout frame, View terminal) {
        mActivity = activity;
        mUi = ui;
        mFrame = frame;
        mTerminal = terminal;
    }

    /**
     * Bring the daemon up without the user leaving the app.
     *
     * <p>Writes the request file the Termux-side keeper watches, then polls for the token it
     * publishes. If the write fails there is no point polling: the one channel to the keeper is
     * the file, so that ending is reported immediately rather than after fifteen attempts that
     * would blame the keeper for not answering.
     */
    void start(final Host host) {
        splash(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0071));
        if (!DaemonLauncher.requestStart()) {
            restore();
            host.storageBlocked();
            return;
        }
        poll(0, host);
    }

    private void poll(final int attempt, final Host host) {
        String token = DaemonLauncher.readToken();
        if (token != null) {
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
        if (!DaemonLauncher.keeperAlive()) {
            host.keeperMissing();
        } else {
            host.daemonSlow();
        }
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
