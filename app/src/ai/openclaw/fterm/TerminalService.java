package ai.openclaw.fterm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps the terminal process out of Android's freezer.
 *
 * The reported bug - "the terminal dies when idle" - was measured, not guessed. With fterm in
 * the background, `dumpsys activity processes` reports:
 *
 *     isFrozen=true   curProcState=16 (CACHED_EMPTY)
 *
 * Android 12+ freezes cached app processes (SIGSTOP). A frozen process stops reading its
 * socket, so the daemon sees a silent client, and on return the socket is a corpse. A ping and
 * a read timeout (already in {@link com.termux.terminal.SocketTransport}) make the client
 * *notice* a dead link; they cannot stop it dying.
 *
 * A foreground service is the platform's own answer: `startForeground()` moves the process out
 * of CACHED and it is never frozen. That is exactly what Termux itself does, and why its
 * session survives.
 *
 * The notification is also the terminal's status surface: it names the connected daemon and
 * gives a way back into the app. It is `ongoing` and low-priority, so it stays in the shade
 * without a sound.
 */
public final class TerminalService extends Service {

    private static final String CHANNEL_ID = "fterm.session";
    private static final int NOTIFICATION_ID = 1001;

    /**
     * {@code ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE}, spelled out.
     *
     * <p>Constant inlined as a literal so this file still compiles against an older android.jar:
     * the value is part of the API 34 surface, but the bit is simply ignored by older platforms,
     * where the manifest attribute is what Android reads.
     */
    private static final int FGS_TYPE_SPECIAL_USE = 1 << 30;

    /** Start (or keep) the service. Idempotent. */
    static void start(Context context) {
        Intent intent = new Intent(context, TerminalService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable ignored) {
            // A foreground service can be refused right after boot or in the background;
            // the terminal still works, it is just freezable.
        }
    }

    static void stop(Context context) {
        try {
            context.stopService(new Intent(context, TerminalService.class));
        } catch (Throwable ignored) {
        }
    }

    /** Update the notification's text, e.g. after connecting or when the shell exits. */
    static void update(Context context, String title, String text) {
        try {
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, build(context, title, text));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel(this);
        Notification n = build(this, "fterm", "터미널 준비 중");
        if (Build.VERSION.SDK_INT >= 34) {
            // The type is declared in the manifest too; passing it here is what Android 14+
            // asks for explicitly, and it is what makes the specialUse exemption checkable.
            startForeground(NOTIFICATION_ID, n, FGS_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY: if the system does kill the process under memory pressure, the
        // service is restarted - the daemon still owns the shell, so the app reattaches.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        // LOW: no sound, no heads-up. The terminal's notification is a status line, not an alert.
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "터미널 세션", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }

    private static Notification build(Context context, String title, String text) {
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content =
                PendingIntent.getActivity(context, 0, open, pendingFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        b.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(content)
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(Notification.PRIORITY_LOW);

        if (Build.VERSION.SDK_INT >= 26) b.setChannelId(CHANNEL_ID);
        return b.build();
    }
}
