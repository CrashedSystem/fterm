package io.github.crashedsystem.fterm;

import android.content.ComponentCallbacks2;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.util.List;

/**
 * What a tab gives back when Android says memory is short.
 *
 * <p>Eight thousand rows per tab at a hundred columns is roughly eight megabytes, and sixteen
 * tabs of it is a hundred. The system's answer to that pressure is to kill the process, which
 * loses every shell's screen at once - the opposite of what this app is for.
 *
 * <p>The rule is narrow: a tab the user is not looking at may forget its scrollback, and may not
 * change what it is showing. A tab switch after a trim must paint the same screen, which is why
 * only history goes. A full-screen program is skipped entirely by
 * {@link TerminalEmulator#trimScrollback(int)}: its buffer is the program's screen, not history.
 *
 * <p>It lives here rather than in the activity because the policy is worth reading on its own -
 * and because "MainActivity is a coordinator" is something a test measures.
 */
final class TabMemory {

    private TabMemory() {}

    /** Rows to keep for a tab that is not on screen, by how hard Android pushed. */
    private static final int KEEP_WHEN_LOW = 2000;
    private static final int KEEP_WHEN_COMPLETE = 500;

    /**
     * Trim the given background sessions, if this memory level asks for it.
     *
     * @param background every session that is not the visible one; nulls are ignored
     * @param level      the value Android passed to {@code onTrimMemory}
     */
    static void trim(List<TerminalSession> background, int level) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return;
        // Two steps, not one: RUNNING_LOW means "be ready to give some back", COMPLETE means
        // "this is the last chance before the process goes".
        int keep = level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                ? KEEP_WHEN_COMPLETE : KEEP_WHEN_LOW;
        for (TerminalSession session : background) {
            if (session == null) continue;
            TerminalEmulator emulator = session.getEmulator();
            if (emulator != null) emulator.trimScrollback(keep);
        }
    }
}
