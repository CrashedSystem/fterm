package ai.openclaw.fterm;

import com.termux.terminal.TerminalEmulator;

/**
 * Puts the user's appearance choices onto what is already on screen.
 *
 * <p>The settings sheet writes preferences; this is the other half - the part that costs
 * something. A scheme is more than a palette (every chrome surface has to move with it, or an
 * OLED screen keeps a lit #1e1e1e seam), a font size is a fraction that must survive being
 * stored, and the cursor lives in the emulator, so an applied change has to reach a running
 * session rather than the next one.
 *
 * <p>It keeps no state of its own: what it changes is the activity's, and the activity is what
 * holds the views it paints. Splitting it out is what keeps the activity from being where
 * "change one setting" turns into two hundred lines.
 */
final class Appearance {

    private final MainActivity a;

    Appearance(MainActivity activity) {
        this.a = activity;
    }

    void applyFontSize(int dp) {
        setZoom(FontZoom.dp(dp));
    }

    /** Set the size from a fractional dp value; the fraction must survive, see FontZoom. */
    void setZoom(float dp) {
        a.mZoomDp = Math.max(FontZoom.MIN_DP, Math.min(FontZoom.MAX_DP, dp));
        a.mFontDp = FontZoom.dp(a.mZoomDp);
        a.mPrefs.setFontSizeDp(a.mFontDp);
        if (a.mTermView.getCurrentSession() != null) a.mTermView.setTextSize(a.fontPx());
    }

    /** Switch the Latin face; loadFonts() recomputes the cell width the face implies. */
    void applyFontFamily(String id) {
        a.mPrefs.setFontFamily(id);
        a.loadFonts();
    }

    /** Switch the Korean face; it does not change the cell, so the grid is untouched. */
    void applyHangulFamily(String id) {
        a.mPrefs.setHangulFamily(id);
        a.loadFonts();
    }

    /**
     * Install the cursor's shape and blink rate.
     *
     * <p>The shape lives in the emulator (which is also where DECSCUSR writes it), so a
     * running session has to be told; the rate lives in the view. Zero disables blinking,
     * which is the view's own encoding for "off" - not a special case invented here.
     */
    void applyCursor() {
        a.mTermView.setTerminalCursorBlinkerRate(a.mPrefs.cursorBlink() ? 500 : 0);
        a.mTermView.setTerminalCursorBlinkerState(true, true);
        if (a.mActive != null && a.mActive.session != null) {
            TerminalEmulator em = a.mActive.session.getEmulator();
            if (em != null) em.setCursorStyle();
        }
    }

    /** Install or remove the scanline pass; the overlay caches per size on its own. */
    void applyRetro() {
        a.mRetro = a.mPrefs.retroEffect() ? new RetroOverlay() : null;
        a.mTermView.setRetroOverlay(a.mRetro);
    }

    void applyScheme(int index) {
        a.mScheme = index;
        a.mPrefs.setScheme(index);
        // A custom index names one entry of the list, so the editable slots follow it here.
        a.mCustom = a.mCustoms.slotsForScheme(index);
        // On the OLED scheme every surface goes to true black; a #1e1e1e strip next to a
        // #000000 terminal is a visible seam and, on OLED, lit pixels.
        final boolean oled = Schemes.isOled(index, a.mCustom);
        final int chrome = oled ? 0xff000000 : 0xff1e1e1e;
        final int bar = oled ? 0xff0a0a0a : 0xff2b2b2b;
        final int keys = oled ? 0xff0d0d0d : 0xff252526;
        a.mRoot.setBackgroundColor(chrome);
        if (a.mTopBar != null) a.mTopBar.setBackgroundColor(bar);
        if (a.mTabStrip != null) a.mTabStrip.setBackgroundColor(bar);
        if (a.mKeyBar != null) a.mKeyBar.applyTheme(oled);
        if (a.mTabScroll != null) a.mTabScroll.setBackgroundColor(bar);
        if (a.mTermFrame != null) a.mTermFrame.setBackgroundColor(chrome);
        if (a.mActive != null && a.mActive.session != null) a.applySchemeTo(a.mActive.session);
    }
}
