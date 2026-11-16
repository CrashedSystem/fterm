import java.util.HashMap;
import java.util.Map;

/**
 * The colour tables, checked against the source they were copied from.
 *
 * `Schemes.miku()` is a transcription of mterm's `ONE_HALF_DARK` and its 16 ANSI slots must
 * match it exactly - a "close enough" palette is a different theme wearing the same name.
 * The OLED scheme is checked for the property that makes it worth having: a pure black
 * background. Runs on the real class with the Android stubs, not a copy.
 */
public class SchemesTest {

    /** The real Prefs.SCHEME_NAMES length; checked separately on the device. */
    static final int SCHEME_COUNT = 6;

    static int pass = 0;

    static void eq(Object actual, Object expected, String what) {
        if (!java.util.Objects.equals(actual, expected)) {
            throw new AssertionError(what + ": expected <" + expected + "> got <" + actual);
        }
        pass++;
    }

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
        pass++;
    }

    static int rgb(int r, int g, int b) {
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    public static void main(String[] args) {
        // mterm/public/js/core/appearance.js ONE_HALF_DARK, verbatim.
        Map<Integer, Integer> oneHalfDark = new HashMap<>();
        oneHalfDark.put(0, rgb(0x28, 0x2c, 0x34));
        oneHalfDark.put(1, rgb(0xe0, 0x6c, 0x75));
        oneHalfDark.put(2, rgb(0x98, 0xc3, 0x79));
        oneHalfDark.put(3, rgb(0xe5, 0xc0, 0x7b));
        oneHalfDark.put(4, rgb(0x61, 0xaf, 0xef));
        oneHalfDark.put(5, rgb(0xc6, 0x78, 0xdd));
        oneHalfDark.put(6, rgb(0x56, 0xb6, 0xc2));
        oneHalfDark.put(7, rgb(0xdc, 0xdf, 0xe4));
        oneHalfDark.put(8, rgb(0x5a, 0x63, 0x74));
        for (int i = 1; i <= 7; i++) oneHalfDark.put(8 + i, oneHalfDark.get(i));

        int[] miku = ai.openclaw.fterm.Schemes.colors(ai.openclaw.fterm.Schemes.MIKU_INDEX);
        for (Map.Entry<Integer, Integer> e : oneHalfDark.entrySet()) {
            eq(miku[e.getKey()], e.getValue(),
                    "miku ANSI " + e.getKey() + " must equal ONE_HALF_DARK");
        }
        eq(miku[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND], rgb(0x28, 0x2c, 0x34),
                "miku background is One Half Dark's");
        eq(miku[com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND], rgb(0xdc, 0xdf, 0xe4),
                "miku foreground");

        // Every scheme must fill the whole indexed table, or the renderer reads garbage.
        int expectedSize = com.termux.terminal.TextStyle.NUM_INDEXED_COLORS;
        for (int i = 0; i < SCHEME_COUNT; i++) {
            int[] table = ai.openclaw.fterm.Schemes.colors(i);
            eq(table.length, expectedSize, "scheme " + i + " table size");
            for (int c = 0; c < 16; c++) {
                check((table[c] >>> 24) == 0xff, "scheme " + i + " ANSI " + c + " is opaque");
            }
            check((table[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND] >>> 24) == 0xff,
                    "scheme " + i + " background is opaque");
        }

        // OLED must be actual #000000: on an OLED panel that is an unlit pixel.
        int[] oled = ai.openclaw.fterm.Schemes.colors(ai.openclaw.fterm.Schemes.OLED_INDEX);
        eq(oled[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND], 0xff000000,
                "OLED background is pure black");
        check(ai.openclaw.fterm.Schemes.isOled(ai.openclaw.fterm.Schemes.OLED_INDEX),
                "isOled recognises it");

        // The foreground has to stay readable on that black: contrast >= 7:1.
        double fg = luminance(oled[com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND], false);
        double contrast = (fg + 0.05) / 0.05;
        check(contrast >= 7.0, "OLED foreground contrast " + contrast + " is at least 7:1");
        System.out.println("ok  OLED contrast " + Math.round(contrast * 10) / 10.0 + ":1");

        // And each scheme's own foreground must clear 4.5:1 against its background.
        for (int i = 0; i < SCHEME_COUNT; i++) {
            int[] t = ai.openclaw.fterm.Schemes.colors(i);
            double lf = luminance(t[com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND], false);
            double lb = luminance(t[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND], false);
            double hi = Math.max(lf, lb), lo = Math.min(lf, lb);
            double ratio = (hi + 0.05) / (lo + 0.05);
            check(ratio >= 4.5, "scheme " + i
                    + " contrast " + ratio + " is below 4.5:1");
            pass++;
        }

        // Only Miku carries a picture.
        for (int i = 0; i < SCHEME_COUNT; i++) {
            eq(ai.openclaw.fterm.Schemes.hasBackgroundImage(i),
                    i == ai.openclaw.fterm.Schemes.MIKU_INDEX,
                    "hasBackgroundImage(" + i + ")");
        }

        System.out.println("ok  " + pass + " scheme assertions");
        System.out.println("ALL OK");
    }

    static double luminance(int color, boolean unused) {
        double r = channel((color >> 16) & 0xff);
        double g = channel((color >> 8) & 0xff);
        double b = channel(color & 0xff);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    static double channel(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
