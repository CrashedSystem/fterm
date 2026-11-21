package io.github.crashedsystem.terminal;

import android.app.Activity;
import android.os.Build;

/**
 * The two permissions that must not be asked for at launch.
 *
 * <p>Both were requested in {@code onCreate} and both were wrong there, measured on a clean
 * install. The notification prompt drew over the storage dialog that explains the one step the
 * user has to take, so the first thing anyone saw was "allow notifications?" on top of a
 * half-covered instruction. The photo prompt did the same later, after the terminal had finally
 * appeared - a modal in the middle of the first working screen.
 *
 * <p>Neither is needed before a shell exists. Notifications are for the "shell finished"
 * banner; the photo permission is for pasting a screenshot. So they are asked for once the
 * terminal is on screen, which is the first moment either means anything.
 *
 * <p>The "ask once" flag lives in the caller: this class is stateless so the activity can hold
 * the lifetime, and a false return here is cheap.
 */
final class Permissions {

    private static final int REQUEST = 1002;

    private Permissions() {}

    /**
     * Ask for whatever is still missing, once.
     *
     * @param asked the caller's one-shot flag; this method does not remember
     * @return true when a dialog was raised, so the caller can set its flag
     */
    static boolean requestDeferred(Activity activity, boolean asked) {
        if (asked) return false;
        if (Build.VERSION.SDK_INT < 33) return false;
        java.util.ArrayList<String> wanted = new java.util.ArrayList<>();
        if (activity.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            wanted.add("android.permission.POST_NOTIFICATIONS");
        }
        // Gallery images: without this the content:// URI of a screenshot decodes to nothing
        // and "paste my screenshot" silently fails.
        if (activity.checkSelfPermission("android.permission.READ_MEDIA_IMAGES")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            wanted.add("android.permission.READ_MEDIA_IMAGES");
        }
        if (wanted.isEmpty()) return false;
        activity.requestPermissions(wanted.toArray(new String[0]), REQUEST);
        return true;
    }
}
