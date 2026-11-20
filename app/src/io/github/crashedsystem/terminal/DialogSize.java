package io.github.crashedsystem.terminal;

import android.app.Dialog;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

/**
 * How wide a floating surface may get on a big screen.
 *
 * <p>The settings sheet and the command palette were {@code MATCH_PARENT} wide by design: on a
 * phone that is the whole width of the thing you are using, which is right. On a 12" tablet it
 * is a page of rows whose label and value sit a thousand dp apart, and the eye has to travel
 * the width of the screen to read one setting. Windows Terminal does not do this - its
 * settings page is a column - so the width is capped here and the surface is centred.
 *
 * <p>The size is applied on every {@code render()}/{@code show()}, not once: the manifest
 * keeps the activity alive across a rotation ({@code configChanges} has orientation and
 * screenSize), so a sheet opened in portrait kept the portrait height when the device turned
 * and cut off its own bottom edge.
 */
final class DialogSize {

    /** The widest the settings sheet grows; a comfortable column, not a full 12" window. */
    private static final int SHEET_MAX_DP = 640;
    /** The palette is a list of short labels, so it caps narrower than the sheet. */
    private static final int PALETTE_MAX_DP = 560;

    private DialogSize() {}

    /** Settings: as tall as it was, but no wider than a column, and centred. */
    static void sheet(Dialog dialog, Context context) {
        width(dialog, context, SHEET_MAX_DP,
                (int) (context.getResources().getDisplayMetrics().heightPixels * 0.90f));
    }

    /** The command palette: the same treatment, at a height that keeps the list usable. */
    static void palette(Dialog dialog, Context context) {
        width(dialog, context, PALETTE_MAX_DP,
                (int) (context.getResources().getDisplayMetrics().heightPixels * 0.72f));
    }

    private static void width(Dialog dialog, Context context, int maxDp, int heightPx) {
        Window window = dialog.getWindow();
        if (window == null) return;
        int available = context.getResources().getDisplayMetrics().widthPixels;
        window.setLayout(Math.min(available, dp(context, maxDp)), heightPx);
        // setLayout does not move a window that was laid out at the screen edge; saying the
        // gravity out loud is what actually centres the capped column.
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.gravity = Gravity.CENTER;
        window.setAttributes(lp);
    }

    private static int dp(Context context, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, context.getResources().getDisplayMetrics()));
    }
}
