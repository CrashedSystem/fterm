package io.github.crashedsystem.terminal;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Starts the daemon by asking the Termux-side keeper for it, through shared storage.
 *
 * The obvious transport is Termux's `RUN_COMMAND` broadcast, and it is the one every guide
 * shows. On this device (Termux 0.118.3, Android 17) it silently does nothing: the broadcast
 * completes with `result=0`, `RunCommandService` is never invoked, and no command runs. That
 * was measured, not assumed - see the note in `termux/fterm-keeper`.
 *
 * So the direction is reversed. The app writes a request file into shared storage, a small
 * keeper process inside Termux polls for it, starts the daemon and publishes the token. Both
 * sides only ever touch /sdcard/Download, which is the one place an app without Termux's
 * private-data access can meet a process that has it.
 *
 * The protocol is three files:
 *
 *      /sdcard/Download/fterm-up       app -> keeper   "bring the daemon up"
 *      /sdcard/Download/fterm-token    keeper -> app   the auth token
 *      /sdcard/Download/fterm-down     app -> keeper   "stop the daemon"
 *      /sdcard/Download/fterm-keeper-alive  keeper heartbeat (epoch seconds)
 */
final class DaemonLauncher {

    private static final String DIR = "/sdcard/Download";
    static final String UP = DIR + "/fterm-up";
    static final String TOKEN_FILE = DIR + "/fterm-token";
    static final String DOWN = DIR + "/fterm-down";
    static final String HEARTBEAT = DIR + "/fterm-keeper-alive";

    /** The keeper rewrites the heartbeat every second, so two missed beats means it is gone. */
    private static final long KEEPER_STALE_SECONDS = 5;

    private DaemonLauncher() {}

    /** True when the keeper wrote its heartbeat recently. */
    static boolean keeperAlive() {
        File f = new File(HEARTBEAT);
        if (!f.isFile()) return false;
        long age = (System.currentTimeMillis() - f.lastModified()) / 1000L;
        return age <= KEEPER_STALE_SECONDS;
    }

    /** Ask the keeper to start the daemon. Returns false when the request could not be written. */
    static boolean requestStart() {
        return write(UP, "1");
    }

    /** Ask the keeper to stop the daemon. */
    static boolean requestStop() {
        return write(DOWN, "1");
    }

    /** The token the keeper published, or null when it is not there yet. */
    static String readToken() {
        try {
            File f = new File(TOKEN_FILE);
            if (!f.isFile()) return null;
            byte[] b = new byte[(int) Math.min(f.length(), 4096)];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int n = in.read(b);
                if (n <= 0) return null;
                String t = new String(b, 0, n, "UTF-8").trim();
                return t.length() >= 16 ? t : null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean write(String path, String content) {
        try {
            File f = new File(path);
            // Not atomic on purpose: the keeper only has to see the file exist, and a
            // zero-length file caused by a crash between create and write still means "up".
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(content.getBytes("UTF-8"));
            }
            f.setReadable(true, false);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The command, for the instructions shown when the keeper is not installed.
     *
     * <p>The same one-liner the README gives, so a reader who finds either copy runs the same
     * thing. It used to be two lines starting with {@code cd ~/projects/fterm}, which only
     * worked on the machine the app was written on - a user who installed the APK has never
     * had that directory, and the dialog sent them to a path that cannot exist.
     */
    static String keeperInstructions() {
        return io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0091)
                + "  curl -fsSL https://raw.githubusercontent.com/CrashedSystem/fterm/master/termux/install.sh | bash\n\n"
                + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0092)
                + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0093);
    }
}
