package ai.openclaw.fterm;

import com.termux.terminal.TextStyle;

/**
 * Colour schemes for the terminal, in the shape the vendored emulator reads them.
 *
 * The array is indexed exactly like {@link TerminalColorScheme}: 16 ANSI colours, a 216
 * colour cube, 24 greys, then default foreground, background and cursor. Only the
 * meaningful slots are spelled out; the cube and the ramp are generated because they are
 * formulaic and writing 240 literals four times invites typos.
 */
public final class Schemes {

    /** The index of the OLED scheme in {@link Prefs#SCHEME_NAMES}. */
    public static final int OLED_INDEX = 4;

    /** mterm's Miku theme: One Half Dark plus the illustration. */
    public static final int MIKU_INDEX = 5;

    /**
     * The editable scheme. Its nineteen slots (16 ANSI, foreground, background, cursor) are
     * stored by {@link Prefs}, not compiled in, which is the whole point of it existing.
     */
    public static final int CUSTOM_INDEX = 6;

    /** The slots the custom scheme stores: sixteen ANSI, then foreground, background, cursor. */
    public static final int CUSTOM_SLOTS = 19;

    private Schemes() {}

    public static int[] colors(int index) {
        switch (index) {
            case 1: return oneDark();
            case 2: return solarizedDark();
            case 3: return dracula();
            case 4: return oled();
            case 5: return miku();
            default: return campbell();
        }
    }

    /**
     * The palette for a scheme index, with the user's own colours when the index is the
     * custom slot.
     *
     * <p>Kept separate from {@link #colors(int)} because that one is a pure function of the
     * index and is what the tests pin; the custom scheme is a function of stored state, so it
     * needs the state passed in rather than read from a singleton.
     */
    public static int[] colors(int index, int[] customSlots) {
        if (index == CUSTOM_INDEX && customSlots != null
                && customSlots.length == CUSTOM_SLOTS) {
            return fromSlots(customSlots);
        }
        return colors(index);
    }

    /** Builds a full palette (ANSI + cube + ramp + defaults) from the 19 stored slots. */
    public static int[] fromSlots(int[] slots) {
        int[] ansi = new int[16];
        System.arraycopy(slots, 0, ansi, 0, 16);
        return build(ansi, slots[16], slots[17], slots[18]);
    }

    private static int rgb(int r, int g, int b) {
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private static int[] build(int[] ansi, int fore, int back, int cursor) {
        int[] out = new int[TextStyle.NUM_INDEXED_COLORS];
        System.arraycopy(ansi, 0, out, 0, 16);
        // 6x6x6 cube, the standard xterm levels.
        int[] level = {0, 0x5f, 0x87, 0xaf, 0xd7, 0xff};
        int at = 16;
        for (int r = 0; r < 6; r++) {
            for (int g = 0; g < 6; g++) {
                for (int b = 0; b < 6; b++) {
                    out[at++] = rgb(level[r], level[g], level[b]);
                }
            }
        }
        // 24-step grey ramp.
        for (int i = 0; i < 24; i++) {
            int v = 8 + i * 10;
            out[at++] = rgb(v, v, v);
        }
        out[TextStyle.COLOR_INDEX_FOREGROUND] = fore;
        out[TextStyle.COLOR_INDEX_BACKGROUND] = back;
        out[TextStyle.COLOR_INDEX_CURSOR] = cursor;
        return out;
    }

    /**
     * mterm's Miku theme: One Half Dark, with the illustration behind it.
     *
     * The palette is transcribed from `~/projects/mterm/public/js/core/appearance.js`
     * (`ONE_HALF_DARK`), not reinvented, so the two apps show the same colours for the same
     * ANSI codes. The image is drawn by {@link MikuBackground} at 55% opacity - mterm's
     * measured value, chosen there because 100% puts `#dcdfe4` on `#e8e2e0` (1.9:1).
     */
    public static int[] miku() {
        int[] ansi = {
                rgb(0x28, 0x2c, 0x34), rgb(0xe0, 0x6c, 0x75), rgb(0x98, 0xc3, 0x79), rgb(0xe5, 0xc0, 0x7b),
                rgb(0x61, 0xaf, 0xef), rgb(0xc6, 0x78, 0xdd), rgb(0x56, 0xb6, 0xc2), rgb(0xdc, 0xdf, 0xe4),
                rgb(0x5a, 0x63, 0x74), rgb(0xe0, 0x6c, 0x75), rgb(0x98, 0xc3, 0x79), rgb(0xe5, 0xc0, 0x7b),
                rgb(0x61, 0xaf, 0xef), rgb(0xc6, 0x78, 0xdd), rgb(0x56, 0xb6, 0xc2), rgb(0xdc, 0xdf, 0xe4),
        };
        return build(ansi, rgb(0xdc, 0xdf, 0xe4), rgb(0x28, 0x2c, 0x34), rgb(0xdc, 0xdf, 0xe4));
    }

    /** True when the scheme draws the illustration behind the text. */
    public static boolean hasBackgroundImage(int index) {
        return index == MIKU_INDEX;
    }

    /** True when the scheme paints pure black, so the chrome around it should too. */
    public static boolean isOled(int index) {
        return index == OLED_INDEX;
    }

    /**
     * As {@link #isOled(int)}, but a custom scheme counts as OLED when its background is
     * actual black: the point of the flag is that lit pixels and grey seams are wrong there,
     * and that follows the colour, not which slot it came from.
     */
    public static boolean isOled(int index, int[] slots) {
        if (isOled(index)) return true;
        return index == CUSTOM_INDEX && slots != null
                && slots.length == CUSTOM_SLOTS && (slots[17] & 0xffffff) == 0;
    }

    /** Windows Terminal's default. */
    public static int[] campbell() {
        int[] ansi = {
                rgb(12, 12, 12), rgb(197, 15, 31), rgb(19, 161, 14), rgb(193, 156, 0),
                rgb(0, 55, 218), rgb(136, 23, 152), rgb(58, 150, 221), rgb(204, 204, 204),
                rgb(118, 118, 118), rgb(231, 72, 86), rgb(22, 198, 12), rgb(249, 241, 165),
                rgb(59, 120, 255), rgb(180, 0, 158), rgb(97, 214, 214), rgb(242, 242, 242),
        };
        return build(ansi, rgb(204, 204, 204), rgb(12, 12, 12), rgb(242, 242, 242));
    }

    public static int[] oneDark() {
        int[] ansi = {
                rgb(40, 44, 52), rgb(224, 108, 117), rgb(152, 195, 121), rgb(229, 192, 123),
                rgb(97, 175, 239), rgb(198, 120, 221), rgb(86, 182, 194), rgb(171, 178, 191),
                rgb(92, 99, 112), rgb(224, 108, 117), rgb(152, 195, 121), rgb(229, 192, 123),
                rgb(97, 175, 239), rgb(198, 120, 221), rgb(86, 182, 194), rgb(220, 223, 228),
        };
        return build(ansi, rgb(171, 178, 191), rgb(40, 44, 52), rgb(82, 139, 255));
    }

    public static int[] solarizedDark() {
        int[] ansi = {
                rgb(7, 54, 66), rgb(220, 50, 47), rgb(133, 153, 0), rgb(181, 137, 0),
                rgb(38, 139, 210), rgb(211, 54, 130), rgb(42, 161, 152), rgb(238, 232, 213),
                rgb(0, 43, 54), rgb(203, 75, 22), rgb(88, 110, 117), rgb(101, 123, 131),
                rgb(131, 148, 150), rgb(108, 113, 196), rgb(147, 161, 161), rgb(253, 246, 227),
        };
        return build(ansi, rgb(131, 148, 150), rgb(0, 43, 54), rgb(147, 161, 161));
    }

    public static int[] dracula() {
        int[] ansi = {
                rgb(33, 34, 44), rgb(255, 85, 85), rgb(80, 250, 123), rgb(241, 250, 140),
                rgb(189, 147, 249), rgb(255, 121, 198), rgb(139, 233, 253), rgb(248, 248, 242),
                rgb(98, 114, 164), rgb(255, 110, 110), rgb(105, 255, 148), rgb(255, 255, 165),
                rgb(214, 172, 255), rgb(255, 146, 208), rgb(164, 255, 255), rgb(255, 255, 255),
        };
        return build(ansi, rgb(248, 248, 242), rgb(40, 42, 54), rgb(248, 248, 242));
    }

    /**
     * Pure black, for OLED panels.
     *
     * On OLED a black pixel is an off pixel, so a #000000 background saves power and removes
     * the grey wash that a #1e1e1e panel shows in a dark room. The ANSI colours are raised in
     * brightness to compensate: the same palette on pure black reads dimmer than on #282c34,
     * so the normal (non-bright) colours are the bright ones from One Dark and the bright
     * variants go further still.
     */
    public static int[] oled() {
        int[] ansi = {
                rgb(24, 24, 24), rgb(240, 122, 130), rgb(160, 212, 132), rgb(238, 204, 142),
                rgb(120, 190, 255), rgb(212, 138, 232), rgb(104, 200, 212), rgb(200, 205, 214),
                rgb(110, 118, 132), rgb(255, 145, 152), rgb(180, 234, 152), rgb(255, 226, 168),
                rgb(148, 208, 255), rgb(232, 164, 248), rgb(128, 228, 238), rgb(255, 255, 255),
        };
        return build(ansi, rgb(232, 235, 240), rgb(0, 0, 0), rgb(120, 190, 255));
    }

    /** The background of a scheme, for painting the view's edge under the last row. */
    public static int background(int index) {
        return colors(index)[TextStyle.COLOR_INDEX_BACKGROUND];
    }

    public static int foreground(int index) {
        return colors(index)[TextStyle.COLOR_INDEX_FOREGROUND];
    }

    public static int background(int index, int[] customSlots) {
        return colors(index, customSlots)[TextStyle.COLOR_INDEX_BACKGROUND];
    }

    public static int foreground(int index, int[] customSlots) {
        return colors(index, customSlots)[TextStyle.COLOR_INDEX_FOREGROUND];
    }
}
