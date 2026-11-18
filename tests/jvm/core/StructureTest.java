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
        check(main.contains("new TabStrip("), "tab plates live in TabStrip");
        check(!main.contains("TruncateAt.MARQUEE"), "and the marquee is not wired here");
        check(main.contains("new SettingsSheet("), "settings live in SettingsSheet");
        check(main.contains("new SettingsHost(this)"), "the Host glue lives in SettingsHost");
        check(!main.contains("new SettingsSheet.Host()"), "and is not inlined in the activity");
        check(!main.contains("SeekBar size = new SeekBar(this)"), "and are not built here");

        // 2b. Daemon provisioning is a state machine with four endings, and it has its own file.
        String boot = read(dir + "DaemonBoot.java");
        check(boot.contains("void ready(") && boot.contains("void keeperMissing()")
                        && boot.contains("void daemonSlow()") && boot.contains("void storageBlocked()"),
                "every way the daemon can fail to come up has an ending");
        check(main.contains("new DaemonBoot("), "and the activity asks that class, not its own copy");
        check(!main.contains("pollForToken("), "the polling loop is not inlined again");

        // 3. The foreground service is what stops the freezer, so it must exist and be started.
        check(main.contains("TerminalService.start(this)"), "the service is started with the activity");
        String svc = read(dir + "TerminalService.java");
        check(svc.contains("startForeground("), "and calls startForeground (the anti-freeze)");
        check(svc.contains("START_STICKY"), "and asks to be restarted");
        check(svc.contains("IMPORTANCE_LOW"), "with a silent channel");
        // The type is specialUse, not dataSync: Android 15 caps dataSync at six hours in any 24,
        // which is shorter than a shell session is meant to live.
        String manifest = read("AndroidManifest.xml");
        check(manifest.contains("foregroundServiceType=\"specialUse\""),
                "the service is specialUse, not dataSync");
        check(!manifest.contains("foregroundServiceType=\"dataSync\""),
                "and dataSync is gone");
        check(manifest.contains("PROPERTY_SPECIAL_USE_FGS_SUBTYPE"),
                "with the subtype Android asks for");
        check(manifest.contains("FOREGROUND_SERVICE_SPECIAL_USE"),
                "and the matching permission");

        // 3b. What the activity holds, it releases. A ToneGenerator owns an audio track for its
        //     whole life, so a recreated activity would leave one behind per recreation.
        check(main.contains("protected void onDestroy()") && main.contains("mBell.release()"),
                "onDestroy releases the bell's tone generator");

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

        // 5b. Vendored upstream code keeps a pinned baseline. Without one, taking an upstream
        //     fix means rediscovering what we changed, which is how a 3-way merge quietly
        //     deletes the reasons this app exists.
        String upstream = read("docs/UPSTREAM.md");
        check(upstream.matches("(?s).*\\b[0-9a-f]{40}\\b.*"),
                "docs/UPSTREAM.md pins an upstream commit");
        check(upstream.contains("TerminalEmulator.java") && upstream.contains("TerminalView.java"),
                "and lists the vendored files");
        check(read("tools/upstream-diff.sh").contains("raw.githubusercontent.com/termux/termux-app"),
                "with a script that reproduces the comparison");

        // 5c. The wire protocol has one Java home. The four-way agreement is checked by
        //     tests/test_protocol.py; what is checked here is that the app has one table and not
        //     the two it used to.
        String proto = read(dir + "../../../com.termux/terminal/Protocol.java");
        check(proto.contains("C_HELLO") && proto.contains("S_ERROR") && proto.contains("E_TOO_MANY"),
                "the protocol constants live in Protocol");
        check(!read(dir + "../../../com.termux/terminal/SocketTransport.java")
                        .contains("private static final int C_HELLO"),
                "the transport does not keep a second copy");
        check(!read(dir + "../../../com.termux/terminal/DaemonClient.java")
                        .contains("private static final int C_HELLO"),
                "and neither does the one-shot client");

        // 5d. Memory pressure has a policy of its own, and it is what Android's onTrimMemory
        //     calls: background tabs give history back, the visible screen is untouched.
        String memory = read(dir + "TabMemory.java");
        check(memory.contains("TRIM_MEMORY_COMPLETE"), "the trim policy has two steps");
        check(main.contains("TabMemory.trim("), "and the activity asks it on onTrimMemory");
        check(read(dir + "../../../com.termux/terminal/TerminalEmulator.java")
                        .contains("public void trimScrollback(int keep)"),
                "the emulator can drop scrollback without touching the screen");

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

        // 7. The chrome icons are Fluent, and the tab row is WT's.
        //
        //    Both were wrong in the same way: plausible-looking but invented. The icons were
        //    hand-drawn strokes standing in for an icon font, and the row had a hamburger and
        //    a gear that WT has never had. A screenshot cannot tell either apart from the real
        //    thing, so these assertions pin the source and the layout.
        String glyphs = read(dir + "GlyphPaths.java");
        check(glyphs.contains("microsoft/fluentui-system-icons"),
                "icons are generated from the Fluent set WT uses");
        check(glyphs.contains("Path.Style") || glyphs.contains("p.cubicTo("),
                "and are filled outlines, not constant-width strokes");
        check(!glyphs.contains("setStrokeWidth"),
                "no stroke width: a stroke is what made them look foreign");
        for (String needed : new String[]{"plus", "chevron", "settings", "search", "copy",
                "clipboard", "pencil", "close", "info", "terminal"}) {
            check(glyphs.contains("case \"" + needed + "\""),
                    "the set has the \"" + needed + "\" glyph the toolbar asks for");
        }
        // WT's TabRowControl: tabs, then a SplitButton (plus + chevron). Nothing else.
        check(main.contains("Chrome.icon(this, \"chevron\""),
                "the dropdown hangs off a chevron, like WT's split button");
        check(!main.contains("Chrome.icon(this, \"menu\""),
                "no hamburger: WT's tab row does not have one");
        check(!main.contains("Chrome.icon(this, \"gear\""),
                "no gear in the tab row: settings is a row in the dropdown");
        check(main.contains("top.addView(plus);") && main.contains("top.addView(drop);"),
                "the plus and the chevron are both in the row");
        int iPlus = main.indexOf("top.addView(plus);");
        int iDrop = main.indexOf("top.addView(drop);");
        check(iPlus >= 0 && iDrop > iPlus, "and the chevron sits after the plus");

        System.out.println("ok  " + pass + " structure assertions");
        System.out.println("ALL OK");
    }
}
