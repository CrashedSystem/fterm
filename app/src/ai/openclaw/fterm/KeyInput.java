package ai.openclaw.fterm;

import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;

import com.termux.terminal.TerminalSession;

/**
 * Everything the chrome presses, turned into bytes on the active session.
 *
 * <p>Three sources feed this: the key bar's cells, the hardware keyboard, and the key bar's
 * sticky modifiers (ALT/CTRL/SHIFT/FN latch until the next key is sent, which is how a phone
 * keyboard has to work - there is no way to hold two keys at once with one thumb). All three
 * end at {@link com.termux.view.TerminalView#handleKeyCode}, so the emulator translates them
 * with the running program's terminfo instead of this class hard-coding escape sequences.
 *
 * <p>The latch lives here rather than in the activity because it is released by the same code
 * that consumes it; a key path split across two files is how "abc" with CTRL held once arrived
 * as ^B ^C ^H.
 */
final class KeyInput {

    private final MainActivity a;

    /** The key bar's function layer: the next code point is read as F-layer, then cleared. */
    private boolean fn;

    /** Last time each escape sequence went out, for the flood guard below. */
    private final java.util.HashMap<String, Long> lastEscapeAt = new java.util.HashMap<>();

    KeyInput(MainActivity activity) {
        this.a = activity;
    }

    boolean fn() {
        return fn;
    }

    void toggleKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
    }

    void showKeyboardSoon() {
        a.mUi.postDelayed(() -> {
            a.mTermView.requestFocus();
            InputMethodManager imm =
                    (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(a.mTermView, InputMethodManager.SHOW_IMPLICIT);
        }, 150);
    }

    void sendRaw(String s) {
        if (a.mActive == null || a.mActive.session == null) return;
        a.mActive.session.write(s);
        clearSticky();
    }

    void sendEscape(String what) {
        TerminalSession s = a.mActive == null ? null : a.mActive.session;
        if (s == null || s.getEmulator() == null) return;
        // Flood guard: a key source gone bad (a touch listener firing on MOVE, a keyboard
        // driver stuck on repeat) can push hundreds of the same sequence per second - the
        // measured "18:15:52, 200 frames/sec for 9s" flood. Same sequence within 40ms is
        // a synthetic duplicate, not a human keypress: drop it. Hold-to-repeat in the key
        // bar fires every 60ms and stays above this floor.
        long now = android.os.SystemClock.uptimeMillis();
        Long last = lastEscapeAt.get(what);
        if (last != null && now - last < 40) return;
        lastEscapeAt.put(what, now);
        int code;
        switch (what) {
            case "tab": code = KeyEvent.KEYCODE_TAB; break;
            case "esc": code = KeyEvent.KEYCODE_ESCAPE; break;
            case "up": code = KeyEvent.KEYCODE_DPAD_UP; break;
            case "down": code = KeyEvent.KEYCODE_DPAD_DOWN; break;
            case "left": code = KeyEvent.KEYCODE_DPAD_LEFT; break;
            case "right": code = KeyEvent.KEYCODE_DPAD_RIGHT; break;
            case "home": code = KeyEvent.KEYCODE_MOVE_HOME; break;
            case "end": code = KeyEvent.KEYCODE_MOVE_END; break;
            case "pgup": code = KeyEvent.KEYCODE_PAGE_UP; break;
            case "pgdn": code = KeyEvent.KEYCODE_PAGE_DOWN; break;
            default: return;
        }
        a.mTermView.handleKeyCode(code, 0);
        clearSticky();
    }

    /**
     * F1..F12 from the key bar's function layer.
     *
     * <p>Routed through {@link com.termux.view.TerminalView#handleKeyCode} like every other
     * special key, which is what makes the emulator translate it with the running program's
     * terminfo: a TUI that asked for {@code kf1=\eOP} gets that, and a shell that expects
     * {@code \e[11~} gets that. Sending a hardcoded sequence was the reason F-keys used to
     * break inside vim.
     *
     * <p>No flood guard runs here. These cells repeat while held, and the point of a repeat is
     * a stream of identical codes; the guard's 40 ms floor would swallow every other one.
     */
    void sendFunctionKey(int number) {
        if (number < 1 || number > 12) return;
        if (a.mActive == null || a.mActive.session == null) return;
        a.mTermView.handleKeyCode(KeyEvent.KEYCODE_F1 + (number - 1), 0);
        clearSticky();
    }

    /**
     * The FN layer's letters, as the key code the terminal should see; -1 when FN is not down.
     *
     * <p>The latch is released either way: FN applies to exactly one following key, and a
     * letter with no arrow behind it must not leave the layer armed for the next one.
     */
    int fnKey(int codePoint) {
        if (!fn) return -1;
        final int lower = Character.toLowerCase(codePoint);
        int key = -1;
        switch (lower) {
            case 'w': key = KeyEvent.KEYCODE_DPAD_UP; break;
            case 'a': key = KeyEvent.KEYCODE_DPAD_LEFT; break;
            case 's': key = KeyEvent.KEYCODE_DPAD_DOWN; break;
            case 'd': key = KeyEvent.KEYCODE_DPAD_RIGHT; break;
            case 'p': key = KeyEvent.KEYCODE_PAGE_UP; break;
            case 'n': key = KeyEvent.KEYCODE_PAGE_DOWN; break;
            case 't': key = KeyEvent.KEYCODE_TAB; break;
            case 'h': key = KeyEvent.KEYCODE_MOVE_HOME; break;
            case 'e': key = KeyEvent.KEYCODE_MOVE_END; break;
            default: break;
        }
        fn = false;
        return key;
    }

    void clearSticky() {
        fn = false;
        if (a.mKeyBar != null) a.mKeyBar.clear();
    }
}
