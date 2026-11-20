package io.github.crashedsystem.fterm;

import android.view.KeyEvent;

/**
 * The hardware-keyboard bindings, handled before the focused view sees the key.
 *
 * <p>Two of these were advertised in the dropdown and never implemented: Ctrl+Shift+F and
 * Ctrl+, were printed next to io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0063) and io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0306) and, when pressed, typed ^F and a comma into the
 * shell - the terminal's own Ctrl mapping did the ^F, and the comma simply had no mapping.
 * The rest are the keys a keyboard-only DeX session is missing: there was no way to move
 * between tabs or change the font size without a mouse or a pinch.
 *
 * <p>The activity dispatches through here first, which is what makes the bindings
 * focus-independent - before this, Ctrl+Shift+T/W stopped working the moment the cursor was
 * in the find field. Only keys the shell does not want are taken: Ctrl+letter,
 * Ctrl+arrows, Ctrl+Home/End and Ctrl+PageUp/Down are left alone, so ^C, ^D, ^K, ^A, ^E and
 * readline's word movement still reach the program in the terminal.
 */
final class Shortcuts {

    private final MainActivity mActivity;

    Shortcuts(MainActivity activity) {
        mActivity = activity;
    }

    /** @return true when the key belongs to the app and must not reach the shell. */
    boolean handle(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (!e.isCtrlPressed() || e.isAltPressed()) return false;

        final int code = e.getKeyCode();
        final boolean shift = e.isShiftPressed();

        if (code == KeyEvent.KEYCODE_F && shift) {
            mActivity.find();
            return true;
        }
        if (code == KeyEvent.KEYCODE_COMMA) {
            mActivity.showSettings();
            return true;
        }
        if (code == KeyEvent.KEYCODE_TAB) {
            mActivity.cycleTab(shift ? -1 : 1);
            return true;
        }
        // Ctrl+1..9 selects that tab, counting from one, exactly like a browser or WT. Ctrl+0
        // is not a tenth tab: it resets the font size, which is what the zoom row below means.
        if (code >= KeyEvent.KEYCODE_1 && code <= KeyEvent.KEYCODE_9) {
            mActivity.selectTab(code - KeyEvent.KEYCODE_1);
            return true;
        }
        if (code == KeyEvent.KEYCODE_0 || code == KeyEvent.KEYCODE_NUMPAD_0) {
            mActivity.applyFontSize(FontZoom.DEFAULT_DP);
            return true;
        }
        // "=" is the unshifted key that carries "+" on most layouts, so both arrive as
        // KEYCODE_EQUALS; a numpad add sends its own code.
        if (code == KeyEvent.KEYCODE_EQUALS || code == KeyEvent.KEYCODE_PLUS
                || code == KeyEvent.KEYCODE_NUMPAD_ADD) {
            mActivity.applyFontSize(mActivity.mFontDp + 1);
            return true;
        }
        if (code == KeyEvent.KEYCODE_MINUS || code == KeyEvent.KEYCODE_NUMPAD_SUBTRACT) {
            mActivity.applyFontSize(mActivity.mFontDp - 1);
            return true;
        }
        return false;
    }
}
