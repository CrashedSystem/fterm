package io.github.crashedsystem.terminal;

/**
 * The arithmetic behind pinch-to-zoom on the terminal font.
 *
 * <p>{@code ScaleGestureDetector} reports one small delta per event - a few percent, not the
 * whole gesture - so the size has to stay fractional between events. Rounding each event to
 * a whole dp instead throws the fraction away every frame, and the gesture stops working in
 * the direction where the scale is just above one: 9dp * 1.02 is 9.18, which truncates back
 * to 9, and it truncates to 9 forever. Pinching in looked fine only because 8.82 truncates
 * to 8, so the size fell on the first event and kept falling.
 *
 * <p>Pure JDK and separate from the activity, so a test can run a whole gesture through it
 * instead of asserting that the source says the right thing.
 */
public final class FontZoom {

    public static final int MIN_DP = 4;
    public static final int MAX_DP = 28;
    /** The size a fresh install starts at, and the one Ctrl+0 goes back to. */
    public static final int DEFAULT_DP = 12;

    private FontZoom() {}

    /** The new fractional size after one scale event, clamped to the sizes the app offers. */
    public static float next(float current, float scale) {
        if (!(scale > 0f) || Float.isInfinite(scale) || Float.isNaN(scale)) return current;
        return Math.max(MIN_DP, Math.min(MAX_DP, current * scale));
    }

    /**
     * The size as it is offered and stored: one decimal place, inside the allowed range.
     *
     * <p>One decimal, not whole dp: 12.5 is a size a person types and a size the renderer
     * handles, and rounding it to 12 or 13 makes the field reject an answer it should take.
     */
    public static float oneDecimal(float size) {
        if (Float.isNaN(size)) return DEFAULT_DP;
        float clamped = Math.max(MIN_DP, Math.min(MAX_DP, size));
        return Math.round(clamped * 10f) / 10f;
    }

    /** The whole dp size to render at, for a fractional size. */
    public static int dp(float size) {
        return Math.max(MIN_DP, Math.min(MAX_DP, Math.round(size)));
    }
}
