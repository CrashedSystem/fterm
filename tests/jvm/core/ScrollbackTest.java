package com.termux.terminal;

/**
 * Scrollback trimming: what {@code onTrimMemory} asks for when Android needs the memory back.
 *
 * <p>The report this answers is the 16-tab one: 8,000 rows per tab at a hundred columns is
 * roughly eight megabytes each, so a handful of old tabs is a hundred megabytes the system has
 * to take from somewhere - and what it takes is the whole process, shells included.
 *
 * <p>The contract is narrow on purpose: history goes, the visible screen stays. A tab the user
 * is not looking at may lose its scrollback; it may not lose what it is showing, because a tab
 * switch must not paint a different screen. Both halves are checked here against the real
 * parser, and a full-screen program (alternate buffer) is left alone.
 *
 *   java -cp <classes> com.termux.terminal.ScrollbackTest
 *
 * <p>In the com.termux.terminal package for the same reason ModeRestoreTest is: the question is
 * about the buffer's own bookkeeping (total rows, transcript rows), which is package state.
 */
public class ScrollbackTest {

    static int pass = 0;

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
        pass++;
    }

    static TerminalSessionClient client() {
        return new TerminalSessionClient() {
            public void onTextChanged(com.termux.terminal.TerminalSession s) {}
            public void onTitleChanged(com.termux.terminal.TerminalSession s) {}
            public void onSessionFinished(com.termux.terminal.TerminalSession s) {}
            public void onCopyTextToClipboard(com.termux.terminal.TerminalSession s, String t) {}
            public void onPasteTextFromClipboard(com.termux.terminal.TerminalSession s) {}
            public String getClipboardText(com.termux.terminal.TerminalSession s) { return null; }
            public byte[] getClipboardImage(com.termux.terminal.TerminalSession s) { return null; }
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

    static TerminalEmulator emu(int cols, int rows, int transcriptRows) {
        return new TerminalEmulator(new TerminalOutput() {
            public void write(byte[] data, int offset, int count) {}
            public void titleChanged(String o, String n) {}
            public void onCopyTextToClipboard(String text) {}
            public void onPasteTextFromClipboard() {}
            public void onBell() {}
            public void onColorsChanged() {}
        }, cols, rows, 10, 20, transcriptRows, client());
    }

    static void feed(TerminalEmulator e, String s) {
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        e.append(b, b.length);
    }

    /** The visible screen as text, one row per line: what a user would see. */
    static String screen(TerminalEmulator e) {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < e.mRows; row++) {
            sb.append(e.getScreen().getSelectedText(0, row, e.mColumns, row + 1)).append('\n');
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        // 1. A buffer with more history than the screen holds, then trimmed to 100 lines.
        TerminalEmulator e = emu(40, 10, 2000);
        for (int i = 0; i < 400; i++) feed(e, "line-" + i + "\r\n");
        int before = e.getScreen().getActiveTranscriptRows();
        check(before > 100, "the test has history to lose (" + before + " rows)");
        String visibleBefore = screen(e);
        int totalBefore = e.getScreen().mTotalRows;

        e.trimScrollback(100);

        check(e.getScreen().mTotalRows < totalBefore,
                "the buffer shrank (" + totalBefore + " -> " + e.getScreen().mTotalRows + ")");
        check(e.getScreen().mTotalRows == e.mRows + 100,
                "to the screen plus the requested history");
        check(e.getScreen().getActiveTranscriptRows() <= 100,
                "and no more than 100 history rows are left");
        check(screen(e).equals(visibleBefore), "the visible screen did not change");

        // 2. The newest line is still the newest line: trimming drops the oldest history, not the
        //    tail of the screen.
        feed(e, "after-trim\r\n");
        check(screen(e).contains("after-trim"), "the session keeps working after a trim");

        // 3. Trimming again asks for nothing: a buffer already at or under the target is left
        //    alone, so onTrimMemory can be called as often as Android likes.
        int total = e.getScreen().mTotalRows;
        e.trimScrollback(100);
        check(e.getScreen().mTotalRows == total, "a second trim at the same target is a no-op");

        // 4. A full-screen program is not touched. Its screen belongs to the program, and a
        //    trim that re-wrapped it would paint garbage; the memory it holds is not history.
        TerminalEmulator tui = emu(40, 10, 2000);
        for (int i = 0; i < 300; i++) feed(tui, "history-" + i + "\r\n");
        feed(tui, "\u001b[?1049h");                 // alternate screen, like vim or opencode
        feed(tui, "\u001b[2J\u001b[Hzzz");
        int tuiTotal = tui.getScreen().mTotalRows;
        String tuiScreen = screen(tui);
        tui.trimScrollback(100);
        check(tui.getScreen().mTotalRows == tuiTotal, "an alternate-screen session is not trimmed");
        check(screen(tui).equals(tuiScreen), "and its screen is untouched");

        System.out.println("ScrollbackTest: " + pass + " assertions OK");
    }
}
