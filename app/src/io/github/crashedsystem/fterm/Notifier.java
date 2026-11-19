package io.github.crashedsystem.fterm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Notifications raised by the program in the shell.
 *
 * This is the native path the request asked for: no termux-api, no plugin, no helper binary.
 * A CLI tool that already notifies on a desktop - `notify-send` shims, build tools, test
 * runners, and `opencode` itself - emits one of two escape sequences, and the terminal
 * parses them and calls here:
 *
 *     ESC ] 9 ; message BEL                       (iTerm2; what most tools emit)
 *     ESC ] 777 ; notify ; title ; body BEL       (rxvt-unicode)
 *
 * The alternative in this project's history was a plugin plus `termux-notification`, which
 * costs a process launch per notification and needs Termux:API installed. Reading the escape
 * sequence means the shell pays nothing: the bytes are already going to the terminal.
 *
 * These notifications are DIFFERENT from the service's ongoing one: that says "a shell is
 * attached", these say "the shell has something to tell you". Separate channel, so a user can
 * silence one without silencing the other.
 */
final class Notifier {

    private static final String CHANNEL_ID = "fterm.notify";
    private static final String CHANNEL_NAME = "셸 알림";

    /** The service's notification owns low ids; these start where it cannot collide. */
    private static final int BASE_ID = 2000;

    /**
     * Least time between two buzzes.
     *
     * <p>A TUI that notifies per step - a build, an agent loop - produces a stream of them, and
     * the shade already collapses them by title. The hand cannot collapse anything, so the
     * vibration is rate limited: the first one says "notice me", the next twenty would only say
     * "the phone is buzzing". Two seconds is longer than a person needs to look.
     */
    private static final long VIBRATE_GAP_MS = 2000;

    private final Context mContext;
    private int mNextId = BASE_ID;
    private long mLastVibrate;

    Notifier(Context context) {
        mContext = context.getApplicationContext();
        createChannel();
    }

    /**
     * Post one notification.
     *
     * @param title the title from the sequence, or empty; the tab name is used then
     * @param body  the message
     */
    void post(String title, String body) {
        if (body == null || body.isEmpty()) return;

        // A tool that sends only a body gets a sensible title rather than an empty one.
        final String useTitle = (title == null || title.trim().isEmpty()) ? "fterm" : title.trim();

        // Collapse repeated notifications from the same title: a build that reports every
        // step should not fill the shade. Same title replaces, different titles stack.
        int id = BASE_ID + Math.abs(useTitle.hashCode() % 1000);

        Intent open = new Intent(mContext, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(mContext, id, open, flags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(mContext, CHANNEL_ID)
                : new Notification.Builder(mContext);

        b.setContentTitle(useTitle)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_notify_terminal)
                .setContentIntent(content)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setPriority(Notification.PRIORITY_DEFAULT);

        if (Build.VERSION.SDK_INT >= 26) b.setChannelId(CHANNEL_ID);

        NotificationManager nm =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, b.build());

        // The user asked for a buzz even while looking at the terminal: a notification the
        // user is staring at still has to say "now" through the hand. Android suppresses
        // notification vibration on the focused app's channel, so post with the hand here -
        // a short double-pulse, distinct from the bell's single buzz.
        vibrate();
    }

    private void vibrate() {
        // A phone on silent asked not to be buzzed. RINGER_MODE_VIBRATE is the opposite: it
        // asked for exactly this. Only SILENT is a "do not".
        try {
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.getRingerMode() == AudioManager.RINGER_MODE_SILENT) return;
        } catch (Throwable ignored) {
        }
        long now = System.currentTimeMillis();
        if (now - mLastVibrate < VIBRATE_GAP_MS) return;
        mLastVibrate = now;
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
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 35, 70, 35}, -1));
            } else {
                v.vibrate(new long[]{0, 35, 70, 35}, -1);
            }
        } catch (Throwable ignored) {
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        // DEFAULT, not LOW: this one is meant to be noticed, unlike the service's status line.
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        ch.setShowBadge(true);
        nm.createNotificationChannel(ch);
    }
}
