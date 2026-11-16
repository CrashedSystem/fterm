package ai.openclaw.fterm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

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

    private final Context mContext;
    private int mNextId = BASE_ID;

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
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(content)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setPriority(Notification.PRIORITY_DEFAULT);

        if (Build.VERSION.SDK_INT >= 26) b.setChannelId(CHANNEL_ID);

        NotificationManager nm =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, b.build());
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
