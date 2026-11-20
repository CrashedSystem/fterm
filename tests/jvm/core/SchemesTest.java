import java.util.HashMap;
import java.util.Map;

/**
 * The colour tables, checked against the source they were copied from.
 *
 * `Schemes.oneHalfDark()` is a transcription of mterm's `ONE_HALF_DARK` and its 16 ANSI slots must
 * match it exactly - a "close enough" palette is a different theme wearing the same name.
 * The OLED scheme is checked for the property that makes it worth having: a pure black
 * background. Runs on the real class with the Android stubs, not a copy.
 */
public class SchemesTest {

    /** The schemes compiled in; the user's own occupy 6..21 and are not built-ins. */
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

    public static void main(String[] args) throws Exception {
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

        int[] oneHalf = io.github.crashedsystem.terminal.Schemes.colors(
                io.github.crashedsystem.terminal.Schemes.ONE_HALF_DARK_INDEX);
        for (Map.Entry<Integer, Integer> e : oneHalfDark.entrySet()) {
            eq(oneHalf[e.getKey()], e.getValue(),
                    "One Half Dark ANSI " + e.getKey() + " must match mterm's table");
        }
        eq(oneHalf[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND], rgb(0x28, 0x2c, 0x34),
                "One Half Dark background is its own");
        eq(oneHalf[com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND], rgb(0xdc, 0xdf, 0xe4),
                "One Half Dark foreground");

        // Every scheme must fill the whole indexed table, or the renderer reads garbage.
        int expectedSize = com.termux.terminal.TextStyle.NUM_INDEXED_COLORS;
        for (int i = 0; i < SCHEME_COUNT; i++) {
            int[] table = io.github.crashedsystem.terminal.Schemes.colors(i);
            eq(table.length, expectedSize, "scheme " + i + " table size");
            for (int c = 0; c < 16; c++) {
                check((table[c] >>> 24) == 0xff, "scheme " + i + " ANSI " + c + " is opaque");
            }
            check((table[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND] >>> 24) == 0xff,
                    "scheme " + i + " background is opaque");
        }

        // OLED must be actual #000000: on an OLED panel that is an unlit pixel.
        int[] oled = io.github.crashedsystem.terminal.Schemes.colors(io.github.crashedsystem.terminal.Schemes.OLED_INDEX);
        eq(oled[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND], 0xff000000,
                "OLED background is pure black");
        check(io.github.crashedsystem.terminal.Schemes.isOled(io.github.crashedsystem.terminal.Schemes.OLED_INDEX),
                "isOled recognises it");

        // The foreground has to stay readable on that black: contrast >= 7:1.
        double fg = luminance(oled[com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND], false);
        double contrast = (fg + 0.05) / 0.05;
        check(contrast >= 7.0, "OLED foreground contrast " + contrast + " is at least 7:1");
        System.out.println("ok  OLED contrast " + Math.round(contrast * 10) / 10.0 + ":1");

        // And each built-in scheme's foreground must clear 4.5:1 against its background.
        // The custom schemes are excluded: their colours are whatever the user typed, and a
        // palette tool that refuses low contrast is a tool that argues with its user.
        for (int i = 0; i < SCHEME_COUNT; i++) {
            int[] t = io.github.crashedsystem.terminal.Schemes.colors(i);
            double lf = luminance(t[com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND], false);
            double lb = luminance(t[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND], false);
            double hi = Math.max(lf, lb), lo = Math.min(lf, lb);
            double ratio = (hi + 0.05) / (lo + 0.05);
            check(ratio >= 4.5, "scheme " + i
                    + " contrast " + ratio + " is below 4.5:1");
            pass++;
        }

        // The custom slot is not compiled in: it is whatever nineteen ints are handed to it,
        // and the cube/ramp are still generated so the renderer never reads zeros. Index 6 is
        // the first of the sixteen custom slots and every one of them behaves the same.
        int[] slots = new int[io.github.crashedsystem.terminal.Schemes.CUSTOM_SLOTS];
        for (int i = 0; i < 16; i++) slots[i] = rgb(16 * i, 255 - 16 * i, 40 + 4 * i);
        slots[16] = rgb(0xee, 0xee, 0xee);
        slots[17] = rgb(0x11, 0x11, 0x11);
        slots[18] = rgb(0x00, 0xdd, 0xff);
        int[] custom = io.github.crashedsystem.terminal.Schemes.colors(
                io.github.crashedsystem.terminal.Schemes.CUSTOM_INDEX, slots);
        eq(custom.length, expectedSize, "custom table size");
        for (int c = 0; c < 16; c++) eq(custom[c], slots[c], "custom ANSI " + c + " is the stored slot");
        eq(custom[16], rgb(0, 0, 0), "custom cube starts at black");
        eq(custom[17], rgb(0, 0, 0x5f), "custom cube's second step is the 0x5f level");
        eq(custom[16 + 216], rgb(8, 8, 8), "custom grey ramp starts at 8");
        eq(custom[com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND], slots[16],
                "custom foreground");
        eq(custom[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND], slots[17],
                "custom background");
        eq(custom[com.termux.terminal.TextStyle.COLOR_INDEX_CURSOR], slots[18],
                "custom cursor");
        // Without slots the custom index must not return a broken table.
        eq(io.github.crashedsystem.terminal.Schemes.colors(io.github.crashedsystem.terminal.Schemes.CUSTOM_INDEX, null).length,
                expectedSize, "custom without slots still fills the table");
        // A custom scheme with a black background counts as OLED for chrome purposes.
        check(io.github.crashedsystem.terminal.Schemes.isOled(io.github.crashedsystem.terminal.Schemes.CUSTOM_INDEX, slots)
                == false, "custom #111 background is not OLED");
        slots[17] = rgb(0, 0, 0);
        check(io.github.crashedsystem.terminal.Schemes.isOled(io.github.crashedsystem.terminal.Schemes.CUSTOM_INDEX, slots),
                "custom black background is OLED");
        check(io.github.crashedsystem.terminal.Schemes.isOled(io.github.crashedsystem.terminal.Schemes.OLED_INDEX, null),
                "built-in OLED is still OLED");

        // No scheme carries a picture any more: the background is a user setting, independent
        // of the palette. The reference must be gone from the source, not just unused.
        String schemes = read("app/src/io/github/crashedsystem/terminal/Schemes.java");
        check(!schemes.contains("hasBackgroundImage"), "schemes do not claim a picture");
        check(!schemes.toLowerCase().contains("miku"), "no theme image is named in Schemes");

        // --- cursor colour override -------------------------------------------
        // The override is applied in MainActivity on top of whatever palette won, so what is
        // pinned here is the rule, not the plumbing: zero means "follow the scheme", and any
        // other value is written into the cursor slot of the live table.
        String activity = read("app/src/io/github/crashedsystem/terminal/MainActivity.java");
        check(activity.contains("mPrefs.cursorColor()"),
                "the palette application reads the cursor override");
        check(activity.contains("if (cursor != 0)"),
                "and zero is the sentinel for no override, not a colour");
        check(activity.contains("COLOR_INDEX_CURSOR] = cursor"),
                "the override lands in the cursor slot of the live colours");

        String prefs = read("app/src/io/github/crashedsystem/terminal/Prefs.java");
        check(prefs.contains("getInt(\"cursor_color\", 0)"),
                "no override is stored as zero, so a fresh install follows the scheme");
        check(prefs.contains("setCursorColor"),
                "and the override survives a restart");

        String sheet = SettingsSources.all("app/src/io/github/crashedsystem/terminal/SettingsSheet.java");
        check(sheet.contains("커서 색"),
                "the setting is reachable by name in 모양");
        check(sheet.contains("구성표 따름"),
                "with a named way back to the scheme's own colour");
        check(sheet.contains("onCursorColor(0)"),
                "which clears the override rather than writing black");
        check(sheet.contains("parseHex"),
                "and takes a hex code, the form every scheme file is written in");

        System.out.println("ok  " + pass + " scheme assertions");
        System.out.println("ALL OK");
    }

    /** Whole-file read for the source assertions; the rule lives in code, not in a table. */
    static String read(String path) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)), "UTF-8");
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
