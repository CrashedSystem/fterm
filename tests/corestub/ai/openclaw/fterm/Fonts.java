package ai.openclaw.fterm;

/**
 * Test stub: the routing rules from {@link Fonts}, without the Android types.
 *
 * The real class loads Typeface/AssetManager, which needs Android. The routing predicates
 * and the size-adjust constant are pure arithmetic, so they are mirrored here and the test
 * additionally checks that the real file still contains the same ranges - a copy that drifts
 * from the original is caught by test 6.
 */
public final class Fonts {

    /** mterm's measured size-adjust for the Hangul face. */
    public static final float HANGUL_SIZE_ADJUST_PCT = 115.85f;

    private Fonts() {}

    public static boolean isHangul(int c) {
        return (c >= 0x1100 && c <= 0x11FF)
                || (c >= 0x3130 && c <= 0x318F)
                || (c >= 0xA960 && c <= 0xA97F)
                || (c >= 0xAC00 && c <= 0xD7A3)
                || (c >= 0xD7B0 && c <= 0xD7FF);
    }

    public static boolean isNerdIcon(int c) {
        return (c >= 0xE0A0 && c <= 0xE0B3)
                || (c >= 0xE0B4 && c <= 0xE0C8)
                || (c >= 0xF000 && c <= 0xF2FF);
    }
}
