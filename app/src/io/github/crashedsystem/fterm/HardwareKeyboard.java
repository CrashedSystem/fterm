package io.github.crashedsystem.fterm;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Whether a keyboard that can type is attached right now.
 *
 * <p>DeX, a book-cover keyboard and a Bluetooth board all report the same thing: a keyboard
 * of some kind, and it is not folded away. Both halves matter - {@code keyboard} stays
 * {@code KEYBOARD_NOKEYS} on a phone, and a detachable keyboard that has been folded behind
 * the screen reports {@code hardKeyboardHidden == HARDKEYBOARDHIDDEN_YES}, at which point the
 * user is back to the on-screen keyboard and the key bar is useful again.
 *
 * <p>The app never asked this before, so on a tablet with a keyboard the key bar held a sixth
 * of the screen and opening the app raised the IME over the terminal. The answer is
 * re-evaluated in {@code onConfigurationChanged}, because plugging a keyboard in does not
 * restart the activity ({@code configChanges} includes {@code keyboard}).
 */
final class HardwareKeyboard {

    private HardwareKeyboard() {}

    static boolean present(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return config.keyboard != Configuration.KEYBOARD_NOKEYS
                && config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
    }
}
