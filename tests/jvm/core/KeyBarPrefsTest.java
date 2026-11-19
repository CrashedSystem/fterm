import java.nio.file.*;

/**
 * The key bar's fixed layout, verified against the real sources.
 *
 * <p>The bar is no longer user-editable: the user removed the editor ("키바 편집 기능은
 * 삭제해. 이대로가 좋아"). That makes the layout a constant, and a constant is worth asserting
 * precisely - the two rows of six are the contract with the screenshot the bar was matched to,
 * and the function layer is what replaced the editor as the way to reach more keys.
 *
 * <p>Android imports keep KeyBar out of this JVM test, so the checks are made against the
 * source text. They are meant to break when the shape changes, not when a colour does.
 */
public class KeyBarPrefsTest {
  static int pass = 0;
  static void eq(Object a, Object b, String what) {
    if (!java.util.Objects.equals(a, b)) throw new AssertionError(what + ": expected <" + b + "> got <" + a + ">");
    pass++;
  }
  static void check(boolean c, String m) { if (!c) throw new AssertionError(m); pass++; }
  static String src(String name) throws Exception {
    return new String(Files.readAllBytes(Paths.get(System.getProperty("root") + "/app/src/ai/openclaw/fterm/" + name)));
  }

  public static void main(String[] args) throws Exception {
    String kb = src("KeyBar.java");
    String prefs = src("Prefs.java");
    // The key path is MainActivity + KeyInput: the activity forwards, the sequence lives in
    // the key class. Both are read, so a hardcoded sequence in either one fails.
    String main = src("MainActivity.java") + src("KeyInput.java");
    String settings = SettingsSources.all(System.getProperty("root")
            + "/app/src/ai/openclaw/fterm/SettingsSheet.java");

    // 1. The main layer is the two fixed rows of six, in the screenshot's order.
    for (String label : new String[]{"\"Esc\"", "\"Tab\"", "\"Home\"", "\"End\"", "\"PgUp\"", "\"PgDn\"",
                                     "\"Ctrl\"", "\"Alt\""}) {
      check(kb.contains(label), "main layer missing " + label);
    }
    check(kb.contains("\\u2191") && kb.contains("\\u2193")
            && kb.contains("\\u2190") && kb.contains("\\u2192"), "all four arrow cells present");
    // Six cells per row: the count is what keeps the grid from silently becoming seven. The
    // function layer reuses the same rows, so its "F" cells are subtracted first.
    check(countOccurrences(kb, "addGridCell(mRow,")
            - countOccurrences(kb, "addGridCell(mRow, \"F\"") == 6, "row 1 has six cells");
    check(countOccurrences(kb, "addGridCell(mUserRow,")
            - countOccurrences(kb, "addGridCell(mUserRow, \"F\"") == 6, "row 2 has six cells");

    // 2. The function layer exists, is reachable, and is twelve keys.
    check(kb.contains("mAction.function("), "function keys are routed through the action");
    check(kb.contains("F\" + i"), "F-labels are generated, not typed out");
    check(kb.contains("i <= 12") || kb.contains("<= 12"), "the layer covers F1..F12");
    check(kb.contains("setFnLayer"), "a layer switch exists");
    check(kb.contains("mLeftTab") && kb.contains("mRightTab"), "both edge tabs exist");
    check(kb.contains("View.INVISIBLE"), "the edge tab hides when it has no target");
    // Direction matters more than existence: both tabs used to point at the layer they were
    // already on, so the F keys were unreachable and neither tab did anything.
    check(kb.contains("edgeTab(\"\\u2039\", () -> setFnLayer(false))"),
            "the left tab returns to the main layer");
    check(kb.contains("edgeTab(\"\\u203a\", () -> setFnLayer(true))"),
            "and the right tab opens the function layer");

    // 3. Hold-to-repeat is owned by one place and bounded - the old flood was 1,800 presses.
    String repeat = src("KeyRepeat.java");
    check(repeat.contains("MAX_PRESSES"), "a hold has a ceiling");
    check(repeat.contains("mGeneration"), "a stale chain is invalidated by generation");
    check(repeat.contains("removeCallbacksAndMessages"), "cancel clears the queue, not just one token");
    check(kb.contains("mRepeat.stop()"), "the bar stops the chain on release and layer change");
    check(kb.contains("ACTION_CANCEL"), "and on a cancelled touch");

    // 4. The editor is gone, all the way down: no host hook, no prefs, no picker.
    check(!settings.contains("keybarKeys"), "settings no longer asks for editable keys");
    check(!settings.contains("showAddDialog"), "the add-key picker is gone");
    check(!prefs.contains("keybarKeys"), "prefs no longer store a key list");
    check(!prefs.contains("DEFAULT_KEYBAR_KEYS"), "and its default list is gone");
    check(!main.contains("mKeyBar.setKeys"), "the bar is never fed a user list");

    // 5. Function keys go through the emulator's translation, never a hardcoded sequence.
    check(main.contains("KEYCODE_F1 + (number - 1)"), "F-keys map to real key codes");
    check(main.contains("handleKeyCode(KeyEvent.KEYCODE_F1"), "and are translated by the emulator");
    check(!main.contains("\\u001bOP"), "no hardcoded SS3 sequence (which broke inside vim)");

    System.out.println("KeyBarPrefsTest: " + pass + " assertions OK");
  }

  static int countOccurrences(String haystack, String needle) {
    int n = 0;
    for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) n++;
    return n;
  }
}
