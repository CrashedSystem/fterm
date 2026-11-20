package io.github.crashedsystem.terminal;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * The terminal bell.
 *
 * A shell rings BEL (0x07) for a finished command in some setups, for `printf '\a'`, and for
 * any TUI that wants attention. A phone has no speaker bell to ring, so this maps the three
 * things a bell can mean onto the three things a phone can do:
 *
 *   - a short vibration by default, because a silent phone is the normal case
 *   - an audible beep when the ringer is not silent
 *   - neither, when the user turned the bell off
 *
 * It is deliberately not a notification: BEL is a per-keystroke-ish signal and a notification
 * tray full of them is worse than silence.
 */
final class Bell {

    private static final long[] PATTERN = {0, 40, 60, 40};
    private static final int BEEP_MS = 90;

    private final Context mContext;
    private ToneGenerator mTone;

    Bell(Context context) {
        mContext = context.getApplicationContext();
    }

    /** Ring the bell, honouring the device's silent mode. */
    void ring(boolean vibrate, boolean sound) {
        if (vibrate) vibrateNow();
        if (sound && !isSilent()) beep();
    }

    private void vibrateNow() {
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager)
                        mContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm == null ? null : vm.getDefaultVibrator();
            } else {
                v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(PATTERN, -1));
            } else {
                v.vibrate(PATTERN, -1);
            }
        } catch (Throwable ignored) {
            // A device without a vibrator is not an error.
        }
    }

    /** True when the ringer is silent or vibrate, so a beep would be rude. */
    private boolean isSilent() {
        try {
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return true;
            return am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
        } catch (Throwable t) {
            return true;
        }
    }

    private void beep() {
        try {
            if (mTone == null) {
                // STREAM_NOTIFICATION so it follows the notification volume; created lazily
                // because a ToneGenerator holds an audio track for its whole life.
                mTone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55);
            }
            mTone.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS);
        } catch (Throwable t) {
            mTone = null;
        }
    }

    void release() {
        if (mTone != null) {
            try {
                mTone.release();
            } catch (Throwable ignored) {
            }
            mTone = null;
        }
    }
}
