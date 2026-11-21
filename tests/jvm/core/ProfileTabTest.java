/**
 * The profile path, asserted against the source that implements it.
 *
 * The three bugs this file exists to prevent all came from the same place: the profile was
 * threaded through the tab's opening path and each step broke silently instead of loudly.
 * A title set after the chip was built left the tab reading "shell"; a command injected only
 * on the reattach path never reached a fresh tab; an active tab past the right edge of a full
 * strip looked like it had not opened at all. Each is one line of source, so each is one
 * assertion here.
 */
public class ProfileTabTest {

    static int pass = 0;

    static void check(boolean c, String what) {
        if (!c) throw new AssertionError(what);
        pass++;
    }

    static String read(String path) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)), "UTF-8");
    }

    public static void main(String[] args) throws Exception {
        String main = read(System.getProperty("main.src",
                "app/src/io/github/crashedsystem/terminal/MainActivity.java"));

        // 1. The title has to be set before addTabChip(), because the chip is drawn once.
        int newTab = main.indexOf("void newTab(Profiles.Profile profile");
        check(newTab > 0, "the profile overload of newTab() exists");
        String body = main.substring(newTab, main.indexOf("private int nextSessionId()"));
        int titleAt = body.indexOf("tab.title = profile.name");
        int chipAt = body.indexOf("addTabChip(tab)");
        check(titleAt > 0, "the profile names the tab");
        check(chipAt > 0, "and the tab gets a chip");
        check(titleAt < chipAt, "and the name is set before the chip is built");

        // 2. The pending command must be polled for on both paths, not only the reattach one.
        int open = main.indexOf("private void openSession(Tab tab, boolean showKeyboard)");
        String openBody = main.substring(open, main.indexOf("private void runPendingCommand"));
        check(openBody.contains("\n        pollForPrompt(tab);\n"),
                "the poll runs at method level, not inside a branch: a fresh tab gets its command too");
        check(openBody.contains("if (showKeyboard) {") && openBody.contains("showKeyboardSoon();"),
                "and the keyboard still shows eagerly");

        // 3. Opening a tab has to scroll the strip to it; a full strip hides the new plate.
        String activate = main.substring(main.indexOf("private void activate(Tab tab)"));
        activate = activate.substring(0, activate.indexOf("private void confirmClose"));
        check(activate.contains("mTabScroll.smoothScrollTo"),
                "activating a tab scrolls the strip to its plate");

        // 4. The command is written as bytes to the shell, never exec'd by the daemon.
        String run = main.substring(main.indexOf("private void runPendingCommand"));
        run = run.substring(0, Math.min(run.length(), 900));
        check(run.contains("session.write(") || run.contains(".write(command"),
                "the command is typed into the shell");
        check(!run.contains("ProcessBuilder") && !run.contains("Runtime.getRuntime"),
                "and not executed in-process");

        // 5. A multi-line command is refused before it can be sent.
        check(main.contains("Profiles.commandIsOneLine("),
                "one-line-ness is checked before the command becomes pending");

        // 6. A profile's icon has to reach the chip, and has to be an override rather than a
        //    snapshot. The chip is built once and repainted on every activate/close, so a
        //    resolved-once value would leave a profile tab stuck with the mark it had at
        //    open time while every other tab followed the global setting.
        int resolver = main.indexOf("private String tabIconFor(Tab tab)");
        check(resolver > 0, "one place decides a tab's mark");
        String resolverBody = main.substring(resolver,
                Math.min(main.length(), resolver + 700));
        check(resolverBody.contains("tab.profile.icon"),
                "a profile's own icon wins");
        check(resolverBody.contains("mPrefs.tabIcon()"),
                "and a tab without one follows the global setting");
        check(!resolverBody.contains("mIcon ="),
                "nothing is cached: the fallback is re-read on every repaint");

        int addChip = main.indexOf("private void addTabChip(Tab tab)");
        String addBody = main.substring(addChip, main.indexOf("private void updateChip"));
        check(addBody.contains("tabIconFor(tab)"),
                "the chip is created from the resolver");
        int updateChip = main.indexOf("private void updateChip(Tab tab)");
        String updateBody = main.substring(updateChip,
                Math.min(main.length(), updateChip + 600));
        check(updateBody.contains("tabIconFor(tab)"),
                "and repainted from it too, or a change would need a reopen");

        // 7. The dropdown and the editor have to show the same mark, or the profile that
        //    opens from the menu is not the one the editor described.
        String chrome = read(System.getProperty("chrome.src",
                "app/src/io/github/crashedsystem/terminal/Chrome.java"));
        check(chrome.contains("profile.icon"), "the dropdown uses the profile's icon");
        String sheet = SettingsSources.all(System.getProperty("sheet.src",
                "app/src/io/github/crashedsystem/terminal/SettingsSheet.java"));
        // The grid is a file of its own now (SettingsIconPicker), so the check is that the
        // editor goes through it and names the pick - not that the call sits on one line.
        // Labels moved into resources; the picker call and the keys are what the source has.
        String strings = read("res/values-ko/strings.xml");
        check(sheet.contains("SettingsIconPicker") && sheet.contains("R.string.i18n_0233"),
                "the editor offers the same picker as the global setting");
        check(strings.contains(">프로필 아이콘<"), "labelled 프로필 아이콘");
        check(sheet.contains("R.string.i18n_0232"),
                "and says plainly what an empty icon means");
        check(strings.contains(">탭 아이콘과 같음<"), "which is 탭 아이콘과 같음");

        System.out.println("ok  " + pass + " profile-tab assertions");
        System.out.println("ALL OK");
    }
}
