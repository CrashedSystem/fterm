package io.github.crashedsystem.fterm;

/**
 * Test stub: the routing rules from {@link Fonts}, without the Android types.
 *
 * The real class loads Typeface/AssetManager, which needs Android. The routing predicates
 * and the size-adjust constant are pure arithmetic, so they are mirrored here and the test
 * additionally checks that the real file still contains the same ranges - a copy that drifts
 * from the original is caught by test 6.
 */
public final class Fonts {

    /** The most a Hangul glyph is scaled: it is fitted to its box, never enlarged. */
    public static final float HANGUL_FIT_MAX = 1.0f;

    /**
     * The catalog is scanned on the device, so the stub carries only the parts a JVM test can
     * assert: the id format, and the rule that an unknown id resolves to the bundled face.
     */
    public static final String ASSET_ID = "asset:iosevka";
    /** The bundled Korean face's id; a separate picker and a separate default. */
    public static final String HANGUL_ASSET_ID = "asset:pretendard";
    /** App-private, because a shared /sdcard folder is unreadable to the app since API 30. */
    public static final String USER_DIR_NAME = "fonts";
    public static final String FILE_PREFIX = "file:";

    public static final class Face {
        public final String id;
        public final String label;
        public final String path;
        public final boolean bundled;

        public Face(String id, String label, String path, boolean bundled) {
            this.id = id;
            this.label = label;
            this.path = path;
            this.bundled = bundled;
        }
    }

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
