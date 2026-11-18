package ai.openclaw.fterm;

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

    private FontZoom() {}

    /** The new fractional size after one scale event, clamped to the sizes the app offers. */
    public static float next(float current, float scale) {
        if (!(scale > 0f) || Float.isInfinite(scale) || Float.isNaN(scale)) return current;
        return Math.max(MIN_DP, Math.min(MAX_DP, current * scale));
    }

    /** The whole dp size to render at, for a fractional size. */
    public static int dp(float size) {
        return Math.max(MIN_DP, Math.min(MAX_DP, Math.round(size)));
    }
}
