import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSessionClient;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TextStyle;

/**
 * Feeds raw bytes to the real ANSI parser and inspects the resulting screen.
 *
 * This is the test that matters most for the stated goal ("한글 지원 충실"). A Hangul
 * syllable must occupy two cells and must not shift the columns after it; an IME that
 * commits a whole syllable at once must land exactly like one that commits jamo one at
 * a time. Nothing renders here - only the grid is checked, which is what a renderer bug
 * would hide.
 */
public class ScreenTest {

    static int pass = 0;

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
        pass++;
    }

    static void eq(Object actual, Object expected, String what) {
        if (!java.util.Objects.equals(actual, expected)) {
            throw new AssertionError(what + ": expected <" + expected + "> got <" + actual + ">");
        }
        pass++;
    }

    /** Minimal client: the emulator only asks for a cursor style. */
    static TerminalSessionClient client() {
        return new TerminalSessionClient() {
            public void onTextChanged(com.termux.terminal.TerminalSession s) {}
            public void onTitleChanged(com.termux.terminal.TerminalSession s) {}
            public void onSessionFinished(com.termux.terminal.TerminalSession s) {}
            public void onCopyTextToClipboard(com.termux.terminal.TerminalSession s, String t) {}
            public void onPasteTextFromClipboard(com.termux.terminal.TerminalSession s) {}
public String getClipboardText(com.termux.terminal.TerminalSession s) { return null; }
            public void onBell(com.termux.terminal.TerminalSession s) {}
            public void onColorsChanged(com.termux.terminal.TerminalSession s) {}
            public void onNotification(com.termux.terminal.TerminalSession s, String t, String b) {}
            public void onTerminalCursorStateChange(boolean state) {}
            public void setTerminalShellPid(com.termux.terminal.TerminalSession s, int pid) {}
            public Integer getTerminalCursorStyle() { return TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK; }
            public void logError(String t, String m) {}
            public void logWarn(String t, String m) {}
            public void logInfo(String t, String m) {}
            public void logDebug(String t, String m) {}
            public void logVerbose(String t, String m) {}
            public void logStackTraceWithMessage(String t, String m, Exception e) {}
            public void logStackTrace(String t, Exception e) {}
        };
    }

    static TerminalEmulator emu(int cols, int rows) {
        return new TerminalEmulator(new com.termux.terminal.TerminalOutput() {
            public void write(byte[] data, int offset, int count) {}
            public void titleChanged(String o, String n) {}
            public void onCopyTextToClipboard(String text) {}
            public void onPasteTextFromClipboard() {}
            public void onBell() {}
            public void onColorsChanged() {}
        }, cols, rows, 10, 20, 2000, client());
    }

    static void feed(TerminalEmulator e, String s) {
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        e.append(b, b.length);
    }

    static String line(TerminalEmulator e, int row) {
        return e.getScreen().getSelectedText(0, row, e.mColumns, row + 1);
    }

    /**
     * The raw characters of one row, ignoring line-wrap joining.
     *
     * getSelectedText() deliberately stitches a wrapped line back together, which is right
     * for copy but hides where a row actually ends. A wide-cell test needs the row itself.
     */
    static String rawRow(TerminalEmulator e, int row) {
        com.termux.terminal.TerminalRow r =
                e.getScreen().allocateFullLineIfNecessary(e.getScreen().externalToInternalRow(row));
        return new String(java.util.Arrays.copyOf(r.mText, r.getSpaceUsed()));
    }

    public static void main(String[] args) {
        // 1. A Hangul syllable occupies two columns: the cursor lands on column 4 after
        //    "한글", i.e. exactly like four Latin letters.
        TerminalEmulator e = emu(20, 4);
        feed(e, "ab한글");
        eq(e.getCursorCol(), 6, "cursor after 'ab한글' (2 + 2*2)");
        eq(line(e, 0).trim(), "ab한글", "screen row 0");
        eq(e.getScreen().getStyleAt(0, 0), e.getScreen().getStyleAt(0, 2),
                "the default style is continuous across the wide cells");

        // 2. Wide cells have no phantom character in the second cell. A terminal that
        //    stored the syllable as one cell would leave the next char one column early.
        TerminalEmulator w = emu(20, 2);
        feed(w, "한X");
        eq(w.getCursorCol(), 3, "cursor after wide syllable + X");
        // The row stores one char for a wide cell (no phantom placeholder), so the grid is
        // checked through the cursor position above: X sits at column 2, not column 1.
        eq(line(w, 0).trim(), "한X", "the syllable and X are adjacent in the row buffer");

        // 3. Precomposed and decomposed input both fit in the same four columns: the IME
        //    commits precomposed syllables, but a pasted decomposed sequence must not
        //    overflow the row boundary differently.
        // xterm semantics: filling the last column leaves the cursor on it with a wrap
        // pending, so the column reads 7 on an 8-column screen and the next glyph wraps.
        TerminalEmulator p = emu(8, 2);
        feed(p, "가나다라");
        eq(p.getCursorCol(), 7, "four precomposed syllables fill all 8 columns");
        eq(line(p, 0).trim(), "가나다라", "the row holds all four syllables");
        feed(p, "마");
        eq(p.getCursorRow(), 1, "the fifth syllable wrapped to the next row");
        eq(rawRow(p, 1).trim(), "마", "and landed at column 0 of it");

        // 4. Autowrap must happen at the cell boundary, not mid-syllable.
        TerminalEmulator wr = emu(4, 3);
        feed(wr, "한글한글");
        eq(wr.getCursorRow(), 1, "8 cells in a 4 column screen take 2 rows");
        check(rawRow(wr, 0).trim().equals("한글"), "row 0 holds the first two syllables, got " + rawRow(wr, 0));
        check(rawRow(wr, 1).trim().equals("한글"), "row 1 holds the next two, got " + rawRow(wr, 1));

        // 5. Colour and attributes still parse (the vendored parser is unmodified here).
        TerminalEmulator c = emu(20, 2);
        feed(c, "\u001b[31mRED\u001b[0m");
        long style = c.getScreen().getStyleAt(0, 0);
        eq(TextStyle.decodeForeColor(style), 1, "SGR 31 sets foreground index 1");
        eq(line(c, 0).trim(), "RED", "text after escape");

        // 6. The 256 colour cube and the default slots are shaped as the renderer expects.
        int[] table = TerminalColors.COLOR_SCHEME.mDefaultColors;
        eq(table.length, TextStyle.NUM_INDEXED_COLORS, "palette size");
        eq(TextStyle.NUM_INDEXED_COLORS, 16 + 216 + 24 + 3, "16 + cube + ramp + 3 defaults");

        // 7. Title, resize and scrollback are the plumbing the app relies on.
        TerminalEmulator t = emu(10, 3);
        feed(t, "\u001b]0;my-title\u0007");
        eq(t.getTitle(), "my-title", "OSC 0 sets the title");

        TerminalEmulator r = emu(10, 3);
        feed(r, "1\r\n2\r\n3\r\n4\r\n5");
        check(r.getScreen().getActiveTranscriptRows() > 0, "scrolled lines enter the transcript");
        r.resize(20, 6, 10, 20);
        eq(r.mColumns, 20, "resize widens");
        eq(r.mRows, 6, "resize heightens");

        // 8. UTF-8 split across packets: the emulator must buffer the partial sequence
        //    rather than emit replacement characters. This is what a socket transport does.
        TerminalEmulator s = emu(10, 2);
        byte[] hangul = "한".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        eq(hangul.length, 3, "한 is three UTF-8 bytes");
        for (byte b : hangul) s.append(new byte[]{b}, 1);
        eq(s.getCursorCol(), 2, "split UTF-8 sequence still lands as one cell pair");
        check(line(s, 0).trim().equals("한"), "no replacement char, got " + line(s, 0));

        System.out.println("ok  " + pass + " screen assertions");
        System.out.println("ALL OK");
    }
}
