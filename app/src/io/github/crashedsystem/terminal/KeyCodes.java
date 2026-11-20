package io.github.crashedsystem.terminal;

import android.view.KeyEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * The key bar's names mapped to Android key codes, plus the repeat guard.
 *
 * <p>The bar sends names ("up", "pgdn") because that is what its cells are labelled with; the
 * terminal wants a key code, because {@code handleKeyCode} is what applies the running
 * program's terminfo. This is that translation, and it lives here rather than in the activity
 * for the same reason the bar does: the activity coordinates, the bar knows its own keys.
 */
final class KeyCodes {

    private static final Map<String, Integer> CODES = new HashMap<>();

    static {
        CODES.put("tab", KeyEvent.KEYCODE_TAB);
        CODES.put("esc", KeyEvent.KEYCODE_ESCAPE);
        CODES.put("up", KeyEvent.KEYCODE_DPAD_UP);
        CODES.put("down", KeyEvent.KEYCODE_DPAD_DOWN);
        CODES.put("left", KeyEvent.KEYCODE_DPAD_LEFT);
        CODES.put("right", KeyEvent.KEYCODE_DPAD_RIGHT);
        CODES.put("home", KeyEvent.KEYCODE_MOVE_HOME);
        CODES.put("end", KeyEvent.KEYCODE_MOVE_END);
        CODES.put("pgup", KeyEvent.KEYCODE_PAGE_UP);
        CODES.put("pgdn", KeyEvent.KEYCODE_PAGE_DOWN);
    }

    /**
     * How soon the same name counts as a synthetic duplicate.
     *
     * <p>A key source gone bad - a touch listener firing on MOVE, a keyboard driver stuck on
     * repeat - can push hundreds of the same sequence per second; the measured case was "200
     * frames/sec for 9s". Hold-to-repeat in the bar fires every 60ms, so this floor sits just
     * under it: real repeats pass, the flood does not.
     */
    private static final long REPEAT_FLOOR_MS = 40;

    private final Map<String, Long> mLastAt = new HashMap<>();

    /** The code for a name, or -1 when the name is not one of ours. */
    static int codeFor(String name) {
        Integer code = CODES.get(name);
        return code == null ? -1 : code;
    }

    /**
     * Whether this press should go out.
     *
     * <p>Stateful, so the caller keeps one instance: the guard is about how often the same
     * name was seen, which cannot be answered without remembering the last time.
     */
    boolean allow(String name) {
        long now = android.os.SystemClock.uptimeMillis();
        Long last = mLastAt.get(name);
        if (last != null && now - last < REPEAT_FLOOR_MS) return false;
        mLastAt.put(name, now);
        return true;
    }
}
