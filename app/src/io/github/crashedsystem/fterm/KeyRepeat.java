package io.github.crashedsystem.fterm;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/**
 * Hold-to-repeat for one key cell: press once, hold, and the key fires again.
 *
 * <p>This exists because the previous attempt flooded the PTY - one touch produced ~1,800
 * synthetic presses over nine seconds (measured: ~200 frames/s). The chain itself was not
 * the bug. The bug was that cancelling it had several paths (touch up, long-click end,
 * visibility change, rebuild) and any one of them left a posted Runnable alive, which then
 * re-posted itself forever. Three rules keep that from happening again:
 *
 * <ol>
 *   <li><b>One owner.</b> A repeat is identified by the identity of its own Runnable. A tick
 *       that is not the current Runnable does nothing and does not reschedule, so a stale
 *       chain dies on its next frame instead of running to the end of time.</li>
 *   <li><b>One cancel.</b> {@link #stop()} bumps the generation and clears the queue. Every
 *       path calls the same method; none of them tries to reason about which callback is
 *       pending.</li>
 *   <li><b>One ceiling.</b> {@link #MAX_PRESSES} caps a single hold even if every cancel
 *       path were missed. A phone key bar has no use for more, and a bounded failure is a
 *       stuck key for a second instead of a dead terminal.</li>
 * </ol>
 *
 * <p>The first repeat is delayed further than the repeat period, the way a hardware keyboard
 * does it: hold longer, get faster. A tap never reaches this class at all - the caller only
 * starts a repeat once the key has been held past the delay.
 */
final class KeyRepeat {

    private static final String TAG = "fterm";

    /** Hold this long before the key starts repeating. Below the platform's 500 ms long-press. */
    static final long FIRST_MS = 420;

    /** Then this often. Matches the ~16 presses/s a desktop terminal delivers. */
    static final long PERIOD_MS = 60;

    /** Hard ceiling for one hold: ~7 seconds of continuous repeat. */
    static final int MAX_PRESSES = 120;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    /** Bumped by every start and every stop; a tick from an older generation is dead. */
    private int mGeneration;
    private Runnable mTick;
    private int mPresses;
    private long mStartedAt;

    /** True while a hold is producing repeats (not counting the initial press). */
    boolean running() {
        return mTick != null;
    }

    /**
     * Begin repeating {@code action} until {@link #stop()}.
     *
     * <p>Calling this twice without a stop is a caller bug, but it is handled: the older
     * chain is stopped first, so a second start cannot double the rate.
     */
    void start(final Runnable action) {
        stop();
        final int generation = ++mGeneration;
        mPresses = 0;
        mStartedAt = SystemClock.uptimeMillis();
        mTick = () -> {
            // Rule 1: this Runnable is the current one, or it is debris from an earlier hold.
            if (generation != mGeneration) return;
            if (mPresses >= MAX_PRESSES) {
                finish();
                return;
            }
            mPresses++;
            action.run();
            mHandler.postDelayed(mTick, PERIOD_MS);
        };
        mHandler.postDelayed(mTick, FIRST_MS);
    }

    /**
     * Stop any pending or running repeat. Idempotent, and safe to call from any of the
     * cancel paths (touch up, touch cancel, view detached, bar rebuilt, activity paused).
     */
    void stop() {
        if (mTick != null) finish();
    }

    private void finish() {
        mGeneration++;
        mHandler.removeCallbacksAndMessages(null);
        mTick = null;
        if (mPresses > 0) {
            // One line per hold. The number that matters is whether a 2-second press
            // produces ~25 presses or thousands; the old bug was visible right here.
            Log.i(TAG, "repeat: " + mPresses + " presses in "
                    + (SystemClock.uptimeMillis() - mStartedAt) + "ms");
        }
        mPresses = 0;
    }
}
