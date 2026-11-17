/**
 * The Windows-Terminal-shaped affordances, checked structurally.
 *
 * These are UI behaviours that need a device to see, so the assertions are about the wiring
 * that makes them possible: the dropdown exists and is reachable without a hardware keyboard,
 * the palette is bound to Ctrl+Shift+P, a renamed tab keeps its name, and the icons are the
 * scheme's own colours.
 */
public class UiTest {

    static int pass = 0;

    static void check(boolean c, String what) {
        if (!c) throw new AssertionError(what);
        pass++;
    }

    public static void main(String[] args) throws Exception {
        String src = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("main.src",
                        "app/src/ai/openclaw/fterm/MainActivity.java"))), "UTF-8");

        // Dropdown: WT's hamburger at the left of the title bar, and long-press on "+".
        check(src.contains("showCommandMenu(v)"), "the tab bar has a dropdown trigger");
        check(src.contains("plus.setOnLongClickListener"), "long-press on + opens it");
        check(src.contains("Chrome.icon(this, \"menu\""), "the trigger is the hamburger glyph");

        // A phone has no Ctrl+Shift+P, so the same commands must be on the menu. The menu
        // itself moved to Chrome.java, so the labels are checked where they live now.
        String chrome = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("app/src/ai/openclaw/fterm/Chrome.java")), "UTF-8");
        for (String cmd : new String[]{"새 탭", "붙여넣기", "복사", "설정"}) {
            check(chrome.contains(cmd), "the menu offers: " + cmd);
        }

        // Palette: WT's Ctrl+Shift+P. The list itself moved to Chrome.java when the activity
        // had to shrink below its line budget, so the assertions follow it there.
        check(src.contains("KEYCODE_P") && src.contains("showCommandPalette()"),
                "Ctrl+Shift+P opens the palette");
        for (String cmd : new String[]{"새 탭 열기", "탭 닫기", "다음 탭", "글자 크기 키우기"}) {
            check(chrome.contains(cmd), "the palette lists: " + cmd);
        }
        check(chrome.contains("getFilter().filter"), "the palette is searchable");

        // Renaming a tab must survive the shell's OSC title.
        check(src.contains("userTitled"), "a renamed tab is marked");
        check(src.contains("if (t.userTitled) return;"),
                "and the shell title stops overwriting it");

        // The key bar and the tab close box must use the scheme's colours, not literals.
        check(src.contains("Schemes.colors(mScheme)"), "chrome follows the active scheme");
        check(src.contains("Schemes.isOled(mScheme)"), "and OLED gets true black");

        // Enter closes a dead tab from either keyboard.
        check(src.contains("KEYCODE_ENTER"), "hardware Enter is handled");
        check(src.contains("!t.session.isRunning()"),
                "and the dead-session check is there");

        System.out.println("ok  " + pass + " UI assertions");
        System.out.println("ALL OK");
    }
}
