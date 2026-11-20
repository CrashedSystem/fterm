/**
 * The tab-close path, checked against the source that implements it.
 *
 * A dead shell's tab could not be closed: the title TextView was removed from the strip, but
 * the LinearLayout plate that actually holds it was not, so an empty plate stayed on screen
 * and the tab looked alive. That is a structural bug, so it is asserted structurally here -
 * closeTab() must remove the plate, and must close the transport (which is what ends the
 * daemon-side session) rather than only signalling the shell.
 */
public class TabCloseTest {

    static int pass = 0;

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
        pass++;
    }

    public static void main(String[] args) throws Exception {
        String src = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("main.src",
                        "app/src/io/github/crashedsystem/terminal/MainActivity.java"))), "UTF-8");

        int at = src.indexOf("private void closeTab(Tab tab)");
        check(at > 0, "closeTab() exists");
        String body = src.substring(at, Math.min(src.length(), at + 1200));

        check(body.contains("mTabStrip.removeView(tab.plate)"),
                "closeTab() removes the plate that is actually in the strip");
        check(!body.contains("mTabStrip.removeView(tab.chip)"),
                "and does not try to remove the title, which is a child of the plate");
        check(body.contains("tab.transport.close()"),
                "closing a tab also closes the transport, which ends the daemon session");
        check(body.contains("finishIfRunning()"),
                "and signals the shell as before");

        check(body.contains("tab.chipView = null"), "and drops the chip object with it");

        // A dead tab must be closable from Enter on either keyboard type.
        int kd = src.indexOf("public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session)");
        String keyBody = src.substring(kd, Math.min(src.length(), kd + 1600));
        check(keyBody.contains("KEYCODE_ENTER"), "onKeyDown handles Enter");
        check(keyBody.contains("isRunning()"), "and checks the session, not only the tab flag");
        check(keyBody.contains("closeTab("), "then closes the tab");

        // The soft keyboard's Enter goes through the view, which forwards it to onKeyDown.
        String view = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("view.src",
                        "app/src/com.termux/view/TerminalView.java"))), "UTF-8");
        check(view.contains("mClient.onKeyDown(KeyEvent.KEYCODE_ENTER"),
                "the terminal view forwards a text Enter to onKeyDown()");

        System.out.println("ok  " + pass + " tab-close assertions");
        System.out.println("ALL OK");
    }
}
