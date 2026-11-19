import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalSessionClient;

/**
 * What a multi-line paste sends to the shell.
 *
 * A program that asks for bracketed paste (DECSET 2004 - opencode, vim, most TUIs) expects
 * the clipboard wrapped in \e[200~ .. \e[201~, which is what turns five newlines into one
 * "[5 lines pasted]" block instead of five submissions. TerminalEmulator.paste() is the only
 * place that knows this, so the app must go through it rather than writing to the session.
 *
 * The test drives the real emulator and records what it writes, then checks that MainActivity
 * still calls emulator.paste() and not session.write() for the clipboard.
 */
public class PasteTest {

    static int pass = 0;

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
        pass++;
    }

    static void eq(Object actual, Object expected, String what) {
        if (!java.util.Objects.equals(actual, expected)) {
            throw new AssertionError(what + ": expected <" + expected + "> got <" + actual);
        }
        pass++;
    }

    static final class Recorder extends TerminalOutput {
        final StringBuilder out = new StringBuilder();

        @Override public void write(byte[] data, int offset, int count) {
            out.append(new String(data, offset, count, java.nio.charset.StandardCharsets.UTF_8));
        }
        @Override public void titleChanged(String o, String n) {}
        @Override public void onCopyTextToClipboard(String text) {}
        @Override public void onPasteTextFromClipboard() {}
        @Override public void onBell() {}
        @Override public void onColorsChanged() {}
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

    public static void main(String[] args) throws Exception {
        Recorder rec = new Recorder();
        TerminalEmulator e = new TerminalEmulator(rec, 80, 24, 10, 20, 2000, client());

        // 1. Without DECSET 2004: newlines become CR, no markers.
        e.paste("a\nb\nc");
        eq(rec.out.toString(), "a\rb\rc", "plain paste: newlines to CR, no markers");
        rec.out.setLength(0);

        // 2. A program asks for bracketed paste, the way opencode does.
        byte[] on = "\u001b[?2004h".getBytes("UTF-8");
        e.append(on, on.length);
        rec.out.setLength(0);
        e.paste("line1\nline2\nline3\nline4\nline5");
        String s = rec.out.toString();
        check(s.startsWith("\u001b[200~"), "paste opens the bracketed block");
        check(s.endsWith("\u001b[201~"), "and closes it");
        eq(s.substring(6, s.length() - 6), "line1\rline2\rline3\rline4\rline5",
                "the payload is CR-separated inside the markers");
        System.out.println("ok  5 lines paste as one bracketed block");

        // 3. The markers survive a CRLF clipboard.
        rec.out.setLength(0);
        e.paste("a\r\nb");
        eq(rec.out.toString(), "\u001b[200~a\rb\u001b[201~", "CRLF is normalised inside the block");

        // 4. Escapes in the clipboard cannot break out of the block.
        rec.out.setLength(0);
        e.paste("\u001b[201~evil");
        // Upstream Termux strips the ESC byte itself (so no escape sequence can be injected)
        // and leaves the printable "[201~evil" behind. That is harmless: the closing marker
        // needs the ESC, and the only ESC-terminated 201~ in the stream is paste()'s own.
        String escaped = rec.out.toString();
        eq(escaped.indexOf("\u001b[201~"), escaped.length() - 6,
                "the only ESC-prefixed closing marker is the one paste() adds");
        check(escaped.contains("[201~evil"), "the printable remainder survives, as upstream");
        // The payload is between paste()'s own opening and closing markers.
        String payload = escaped.substring(6, escaped.length() - 6);
        check(!payload.contains("\u001b"),
                "no ESC from the clipboard survives into the payload");

        // 5. After DECSET 2004 is reset the markers go away again.
        byte[] off = "\u001b[?2004l".getBytes("UTF-8");
        e.append(off, off.length);
        rec.out.setLength(0);
        e.paste("x\ny");
        eq(rec.out.toString(), "x\ry", "markers are gone after the program releases the mode");

        // 6. The app must route the clipboard through the emulator.
        String src = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("main.src",
                        "app/src/io/github/crashedsystem/fterm/MainActivity.java"))), "UTF-8");
        int at = src.indexOf("private void pasteClipboard()");
        check(at > 0, "pasteClipboard() exists");
        String body = src.substring(at, Math.min(src.length(), at + 1400));
        check(body.contains("pasteText("), "pasteClipboard() routes through pasteText()");
        // The why-comment moved with the paste path into pasteText().
        int pt = src.indexOf("private void pasteText(");
        String ptBody = src.substring(pt, Math.min(src.length(), pt + 1400));
        check(ptBody.contains("DECSET 2004") || ptBody.contains("bracketed paste"),
                "and says why");

        // 7. An image clip is saved off the UI thread. The paste key used to call the blocking
        //    save() from the click handler, which decodes a full-resolution screenshot while the
        //    terminal (and the keyboard) waits.
        String clipSrc = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("clip.src",
                        "app/src/io/github/crashedsystem/fterm/ClipboardImage.java"))), "UTF-8");
        int pi = clipSrc.indexOf("static boolean pasteIfImage(");
        check(pi > 0, "ClipboardImage offers the asynchronous image paste");
        String piBody = clipSrc.substring(pi, Math.min(clipSrc.length(), pi + 600));
        check(piBody.contains("saveAsync("), "which goes through the worker thread");
        check(body.contains("ClipboardImage.pasteIfImage("), "pasteClipboard() uses it");
        check(!body.contains("ClipboardImage.save("), "and not the blocking save()");

        System.out.println("ok  " + pass + " paste assertions");
        System.out.println("ALL OK");
    }
}
