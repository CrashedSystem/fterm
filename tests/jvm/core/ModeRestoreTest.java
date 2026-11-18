package com.termux.terminal;

/**
 * A reattached emulator must end up with mouse reporting on, not merely look right.
 *
 * <p>The daemon keeps the DECSET declarations per session and replays them ahead of the ring
 * (see {@code Modes} in ftermd). That is only half the contract: the client has to treat what
 * arrives as ordinary output, in order, before the ring. The failure it prevents is the one
 * that was reported - swipes become history scrolling and taps never reach the program, while
 * the screen itself looks perfectly normal, because the pixels came back and the mode did not.
 *
 * <p>This runs the real {@link TerminalEmulator} over the exact byte sequence a reattached
 * client receives: the daemon's restore string, then a ring that has lost the declaration. It
 * asserts the emulator's own answer to "should a touch go to the program" - the same
 * {@code isMouseTrackingActive()} the touch handler asks - rather than the presence of bytes.
 */
public class ModeRestoreTest {

    static int pass = 0;

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
        pass++;
    }

    static class Sink extends TerminalOutput {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        @Override public void write(byte[] data, int offset, int count) { out.write(data, offset, count); }
        @Override public void titleChanged(String o, String n) {}
        @Override public void onCopyTextToClipboard(String t) {}
        @Override public void onPasteTextFromClipboard() {}
        @Override public void onBell() {}
        @Override public void onColorsChanged() {}
    }

    /** The emulator asks only for a cursor style; every other callback is noise here. */
    static TerminalSessionClient client() {
        return new TerminalSessionClient() {
            public void onTextChanged(TerminalSession s) {}
            public void onTitleChanged(TerminalSession s) {}
            public void onSessionFinished(TerminalSession s) {}
            public void onCopyTextToClipboard(TerminalSession s, String t) {}
            public void onPasteTextFromClipboard(TerminalSession s) {}
            public String getClipboardText(TerminalSession s) { return null; }
            public byte[] getClipboardImage(TerminalSession s) { return null; }
            public void onBell(TerminalSession s) {}
            public void onColorsChanged(TerminalSession s) {}
            public void onNotification(TerminalSession s, String t, String b) {}
            public void onTerminalCursorStateChange(boolean b) {}
            public void setTerminalShellPid(TerminalSession s, int pid) {}
            public Integer getTerminalCursorStyle() { return 0; }
            public void logError(String t, String m) {}
            public void logWarn(String t, String m) {}
            public void logInfo(String t, String m) {}
            public void logDebug(String t, String m) {}
            public void logVerbose(String t, String m) {}
            public void logStackTraceWithMessage(String t, String m, Exception e) {}
            public void logStackTrace(String t, Exception e) {}
        };
    }

    static TerminalEmulator emulator() {
        return new TerminalEmulator(new Sink(), 80, 24, 10, 20, 200, client());
    }

    static void feed(TerminalEmulator e, String s) {
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        e.append(b, b.length);
    }

    /** What ftermd's Modes.restore() sends for a full screen TUI with the mouse on. */
    static final String RESTORE =
        "\033[?7h\033[?1h\033[?1000h\033[?1006h\033[?1049h";

    public static void main(String[] args) throws Exception {
        // 1. A fresh emulator, exactly as a recreated process starts: mouse reporting is off.
        TerminalEmulator e = emulator();
        check(!e.isMouseTrackingActive(), "a new emulator starts with reporting off");

        // 2. The ring alone leaves it off - this is the bug, stated as a control. The daemon
        //    measured a 262144 byte replay with no ESC[?1000h in it.
        TerminalEmulator ringOnly = emulator();
        feed(ringOnly, "\033[H\033[2J");
        for (int i = 0; i < 200; i++) feed(ringOnly, "00000 " + "y".repeat(71));
        check(!ringOnly.isMouseTrackingActive(), "a replay that outgrew the ring leaves it off");

        // 3. The restore string, fed as normal output, turns reporting on in the emulator -
        //    which is what makes the touch handler hand swipes to the program.
        feed(e, RESTORE);
        check(e.isMouseTrackingActive(), "the restored modes turn mouse reporting on");

        // 4. And the emulator is on the alternate screen, so drawing goes where the program
        //    expects instead of over scrollback.
        check(e.isAlternateBufferActive(), "the restored modes put it back on the alt screen");

        // 5. A cleared mode must not come back: the daemon omits it, and the emulator that
        //    was told 1000l keeps reporting off.
        TerminalEmulator off = emulator();
        feed(off, "\033[?1000h\033[?1006h");
        check(off.isMouseTrackingActive(), "reporting can be turned on first");
        feed(off, "\033[?1000l");
        check(!off.isMouseTrackingActive(), "and a reset turns it off again");

        // 6. The daemon's RESTORE_ORDER puts the alt screen last, but the contract is that the
        //    restore string is order-independent for the emulator: the same three modes with
        //    the alt screen first must also leave reporting on, so a future reordering of
        //    RESTORE_ORDER cannot silently break touch.
        TerminalEmulator ordered = emulator();
        feed(ordered, "\033[?1049h\033[?1000h\033[?1006h");
        check(ordered.isMouseTrackingActive() && ordered.isAlternateBufferActive(),
              "reporting survives the alt screen switch in either order");

        System.out.println("ModeRestoreTest: " + pass + " assertions OK");
    }
}
