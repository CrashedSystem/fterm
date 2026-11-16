import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalSessionClient;

/**
 * OSC 9 and OSC 777 become notifications.
 *
 * The point of this path is that a tool which already notifies on a desktop also notifies on
 * the phone, with no plugin and no termux-api. So the test drives the real emulator with the
 * exact byte sequences those tools emit and checks what reaches the app.
 */
public class NotifyTest {

    static int pass = 0;

    static void eq(Object a, Object b, String what) {
        if (!java.util.Objects.equals(a, b)) {
            throw new AssertionError(what + ": expected <" + b + "> got <" + a);
        }
        pass++;
    }

    static void check(boolean c, String what) {
        if (!c) throw new AssertionError(what);
        pass++;
    }

    /** Records the notifications the emulator raises. */
    static final class Recorder extends TerminalOutput {
        String title, body;
        int count;

        @Override public void write(byte[] d, int o, int n) {}
        @Override public void titleChanged(String a, String b) {}
        @Override public void onCopyTextToClipboard(String t) {}
        @Override public void onPasteTextFromClipboard() {}
        @Override public void onBell() {}
        @Override public void onColorsChanged() {}

        @Override public void onNotification(String t, String b) {
            title = t;
            body = b;
            count++;
        }
    }

    static TerminalSessionClient client() {
        return new TerminalSessionClient() {
            public void onTextChanged(com.termux.terminal.TerminalSession s) {}
            public void onTitleChanged(com.termux.terminal.TerminalSession s) {}
            public void onSessionFinished(com.termux.terminal.TerminalSession s) {}
            public void onCopyTextToClipboard(com.termux.terminal.TerminalSession s, String t) {}
            public void onPasteTextFromClipboard(com.termux.terminal.TerminalSession s) {}
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

    static Recorder feed(String text) {
        Recorder r = new Recorder();
        TerminalEmulator e = new TerminalEmulator(r, 80, 24, 10, 20, 200, client());
        byte[] b = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        e.append(b, b.length);
        return r;
    }

    public static void main(String[] args) {
        // 1. OSC 9, body only - the common case (iTerm2 form used by most CLI tools).
        Recorder a = feed("\u001b]9;빌드가 끝났습니다\u0007");
        eq(a.count, 1, "OSC 9 raises one notification");
        eq(a.title, "", "OSC 9 carries no title");
        eq(a.body, "빌드가 끝났습니다", "and the body is the message");

        // 2. ST-terminated form (ESC \) instead of BEL.
        Recorder b = feed("\u001b]9;done\u001b\\");
        eq(b.count, 1, "ST termination works too");
        eq(b.body, "done", "body");

        // 3. OSC 777 with title and body - rxvt-unicode.
        Recorder c = feed("\u001b]777;notify;테스트;3개 실패\u0007");
        eq(c.count, 1, "OSC 777 notifies");
        eq(c.title, "테스트", "title parsed");
        eq(c.body, "3개 실패", "body parsed");

        // 4. OSC 777 with a body containing a semicolon keeps it whole.
        Recorder d = feed("\u001b]777;notify;t;a;b;c\u0007");
        eq(d.title, "t", "title before the first semicolon");
        eq(d.body, "a;b;c", "the rest is the body, semicolons and all");

        // 5. OSC 777 without title+body form is not a notification.
        Recorder e = feed("\u001b]777;somethingelse;x\u0007");
        eq(e.count, 0, "only the notify form notifies");

        // 6. An OSC title change must not be mistaken for a notification.
        Recorder f = feed("\u001b]2;my-title\u0007");
        eq(f.count, 0, "OSC 2 sets the title, it does not notify");

        // 7. A bell is still a bell, not a notification.
        Recorder g = feed("\u0007");
        eq(g.count, 0, "BEL alone is not a notification");

        // 8. The app must wire it.
        try {
            String main = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(System.getProperty("main.src",
                            "app/src/ai/openclaw/fterm/MainActivity.java"))), "UTF-8");
            check(main.contains("public void onNotification"), "MainActivity implements onNotification");
            check(main.contains("mNotifier.post("), "and posts through Notifier");
            String notifier = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(System.getProperty("notifier.src",
                            "app/src/ai/openclaw/fterm/Notifier.java"))), "UTF-8");
            check(notifier.contains("IMPORTANCE_DEFAULT"),
                    "shell notifications are noticeable (unlike the service's LOW)");
            // The class comment explains what this replaces, so strip comments before
            // checking that no termux-api call survives.
            StringBuilder code = new StringBuilder();
            for (String line : notifier.split("\n")) {
                String t = line.trim();
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) continue;
                code.append(line).append('\n');
            }
            check(!code.toString().contains("termux"),
                    "no termux-api call in the notification path");
        } catch (java.io.IOException ex) {
            throw new AssertionError(ex);
        }

        System.out.println("ok  " + pass + " notification assertions");
        System.out.println("ALL OK");
    }
}
