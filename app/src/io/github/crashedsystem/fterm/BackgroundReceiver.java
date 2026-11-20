package io.github.crashedsystem.fterm;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;

/**
 * The script-facing door to the background picture.
 *
 * <pre>
 * adb shell am broadcast -a io.github.crashedsystem.fterm.action.APPLY_BACKGROUND -p io.github.crashedsystem.fterm \
 *     --es path /sdcard/Android/data/io.github.crashedsystem.fterm/files/bg/miku-dusk.png \
 *     --ei opacity 70 --es fit cover --ei dim 25
 * adb shell am broadcast -a io.github.crashedsystem.fterm.action.CLEAR_BACKGROUND -p io.github.crashedsystem.fterm
 * </pre>
 *
 * <p>This is what makes a personal image a script rather than a build overlay: the picture is
 * copied into the app's own folder and the running terminal repaints, with no APK rebuild and
 * no reinstall. The result line {@code am} prints says what happened, because a script cannot
 * see a toast.
 *
 * <p>Exported on purpose - a script is another uid. It can only write the app's own picture
 * and appearance preferences; it cannot read anything back, and a path outside the app's own
 * data or shared storage is refused.
 */
public final class BackgroundReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        // Decoding and re-encoding a photograph on the main thread is how a broadcast ANRs.
        new Thread(() -> {
            String result;
            try {
                result = handle(app, intent);
            } catch (Throwable t) {
                result = "error: " + t;
            }
            pending.setResultCode(Activity.RESULT_OK);
            pending.setResultData(result);
            pending.finish();
        }, "fterm-background").start();
    }

    private static String handle(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        boolean clear = BackgroundStore.ACTION_CLEAR.equals(action);

        if (!clear) {
            String path = intent.getStringExtra(BackgroundStore.EXTRA_PATH);
            Uri uri = intent.getData();
            boolean ok;
            if (path != null) {
                File file = new File(path);
                if (!allowed(context, file)) return AppStrings.s(R.string.i18n_0036) + path;
                ok = BackgroundStore.installFile(context, file);
            } else if (uri != null) {
                ok = BackgroundStore.install(context, uri);
            } else {
                return AppStrings.s(R.string.i18n_0037);
            }
            if (!ok) return AppStrings.s(R.string.i18n_0038);
        } else {
            BackgroundStore.clear(context);
        }

        Prefs prefs = new Prefs(context);
        if (intent.hasExtra(BackgroundStore.EXTRA_OPACITY)) {
            prefs.setBackgroundOpacity(intent.getIntExtra(BackgroundStore.EXTRA_OPACITY, 100));
        }
        if (intent.hasExtra(BackgroundStore.EXTRA_FIT)) {
            prefs.setBackgroundFit(intent.getStringExtra(BackgroundStore.EXTRA_FIT));
        }
        if (intent.hasExtra(BackgroundStore.EXTRA_ALIGN)) {
            prefs.setBackgroundAlign(intent.getStringExtra(BackgroundStore.EXTRA_ALIGN));
        }
        if (intent.hasExtra(BackgroundStore.EXTRA_DIM)) {
            prefs.setBackgroundDim(intent.getIntExtra(BackgroundStore.EXTRA_DIM, 0));
        }

        BackgroundStore.notifyChanged(context);
        if (clear) return AppStrings.s(R.string.i18n_0039);
        return "ok: " + BackgroundStore.describe(context);
    }

    /**
     * Shared storage and the app's own directories only.
     *
     * <p>Without this an exported receiver would copy any file the app can read - another
     * app's private data included, if the platform ever let it - into a folder the shell can
     * also reach, which is a way to read it back. The check is cheap and the script paths that
     * matter all pass it.
     */
    private static boolean allowed(Context context, File file) {
        try {
            String path = file.getCanonicalPath();
            String own = context.getDataDir().getCanonicalPath();
            if (path.startsWith(own + "/")) return true;
            return path.startsWith("/sdcard/") || path.startsWith("/storage/")
                    || path.startsWith("/mnt/sdcard/") || path.startsWith("/data/local/tmp/");
        } catch (Throwable t) {
            return false;
        }
    }
}
