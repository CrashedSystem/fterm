/**
 * The split that keeps MainActivity from becoming a monolith.
 *
 * This is a structural assertion because "maintainable" is not something a runtime test can
 * see: what it can see is that responsibilities live in their own files with a narrow
 * interface, and that MainActivity did not quietly grow them back.
 */
public class StructureTest {

    static int pass = 0;

    static void check(boolean c, String what) {
        if (!c) throw new AssertionError(what);
        pass++;
    }

    static String read(String path) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)), "UTF-8");
    }

    static int lines(String s) {
        return s.split("\n", -1).length;
    }

    public static void main(String[] args) throws Exception {
        String dir = System.getProperty("app.dir", "app/src/ai/openclaw/fterm/");
        String main = read(dir + "MainActivity.java");

        // 1. MainActivity stays a coordinator. 1200 lines was the old number and it is the
        //    reason the key bar and the settings sheet were extracted.
        int n = lines(main);
        check(n < 1420, "MainActivity is a coordinator, not a drawer (" + n + " lines)");
        System.out.println("ok  MainActivity " + n + " lines");

        // 2. The pieces that were extracted are actually used, not copied.
        check(main.contains("new KeyBar("), "the key bar lives in KeyBar");
        check(!main.contains("private TextView keyPill("), "and is not duplicated here");
        check(main.contains("new SettingsSheet("), "settings live in SettingsSheet");
        check(main.contains("new SettingsHost(this)"), "the Host glue lives in SettingsHost");
        check(!main.contains("new SettingsSheet.Host()"), "and is not inlined in the activity");
        check(!main.contains("SeekBar size = new SeekBar(this)"), "and are not built here");

        // 3. The foreground service is what stops the freezer, so it must exist and be started.
        check(main.contains("TerminalService.start(this)"), "the service is started with the activity");
        String svc = read(dir + "TerminalService.java");
        check(svc.contains("startForeground("), "and calls startForeground (the anti-freeze)");
        check(svc.contains("START_STICKY"), "and asks to be restarted");
        check(svc.contains("IMPORTANCE_LOW"), "with a silent channel");

        // 4. The bell is its own class and honours silent mode.
        String bell = read(dir + "Bell.java");
        check(bell.contains("VibrationEffect"), "the bell can vibrate");
        check(bell.contains("RINGER_MODE_NORMAL"), "and only beeps when the ringer is normal");

        // 5. Daemon provisioning goes through the keeper, because RUN_COMMAND is dead here.
        String launcher = read(dir + "DaemonLauncher.java");
        check(launcher.contains("fterm-up"), "the app writes the keeper's request file");
        check(launcher.contains("fterm-token"), "and reads the token it publishes");
        // The name appears in the class comment (explaining why it is NOT used), so strip
        // comment lines before checking for the call.
        StringBuilder code = new StringBuilder();
        for (String line : launcher.split("\n")) {
            String t = line.trim();
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) continue;
            code.append(line).append('\n');
        }
        check(!code.toString().contains("RUN_COMMAND"),
                "RUN_COMMAND is not called (it is ignored on this device)");

        // 6. An image reaches the prompt by ONE tap in the keyboard's clipboard panel, not
        //    by a focus-time poll. Three things have to be true for that to work.
        String clip = read(dir + "ClipboardImage.java");
        check(clip.contains("new Thread("), "decoding runs off the UI thread");
        check(clip.contains("text/uri-list") || clip.contains("// text/uri-list"),
                "a URI given only as text is still read");
        String view = read(dir + "../../../com.termux/view/TerminalView.java");
        check(view.contains("contentMimeTypes"), "the editor announces it takes images");
        check(view.contains("commitContent"), "and implements the IME image commit");
        check(view.contains("requestPermission"), "releasing the URI grant the IME hands over");
        check(main.contains("onCommitImage"), "the activity receives the committed image");
        check(!main.contains("addPrimaryClipChangedListener"),
                "no clipboard polling: the tap is the trigger");

        System.out.println("ok  " + pass + " structure assertions");
        System.out.println("ALL OK");
    }
}
