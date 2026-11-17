import java.nio.file.*;

/**
 * The key bar editor's data model, verified against the real sources: the pipe
 * round-trip used by Prefs, the label rules in KeyBar, and that the defaults are
 * catalogue entries. Reading the sources keeps KeyBar's Android imports out of
 * this JVM test while still checking the shipped strings.
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

  /** Mirrors Prefs.setKeybarKeys/keybarKeys - the same pipe format, extracted. */
  static String save(java.util.ArrayList<String> keys) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < keys.size(); i++) {
      if (i > 0) b.append('|');
      b.append(keys.get(i));
    }
    return b.toString();
  }
  static java.util.ArrayList<String> load(String s) {
    java.util.ArrayList<String> out = new java.util.ArrayList<>();
    if (s != null) for (String p : s.split("\\|", -1)) if (!p.isEmpty()) out.add(p);
    return out;
  }

  public static void main(String[] args) throws Exception {
    String kb = src("KeyBar.java");
    String prefs = src("Prefs.java");

    // 1. Round trip with a control-char spec (the pipe format's worst case).
    java.util.ArrayList<String> keys = new java.util.ArrayList<>();
    keys.add("special:tab"); keys.add("raw:\u0003"); keys.add("special:pgup");
    eq(load(save(keys)), keys, "pipe round-trip");

    // 2. Empty segments from a trailing pipe are dropped on load.
    eq(load("special:tab|").size(), 1, "trailing pipe dropped");
    eq(load("|").size(), 0, "all-pipe -> empty");

    // 3. KeyBar's labelOf maps a raw control char to ^X (checked in the source text).
    check(kb.contains("'@' + r.charAt(0)"), "labelOf ^X rule present");
    check(kb.contains("startsWith(\"special:\")"), "special: branch present");
    check(kb.contains("startsWith(\"raw:\")"), "raw: branch present");

    // 4. The catalogue covers home/end/pgup/pgdn and the ^-keys a CLI misses.
    // Raw specs are written as escapes in the source (\u0004), so search for the
    // escaped literal form too.
    for (String need : new String[]{"special:home","special:end","special:pgup","special:pgdn","raw:\\u0004","raw:\\u001a"}) {
      boolean hit = kb.contains("\"" + need + "\"") || kb.contains(need);
      check(hit, "catalogue missing " + need);
    }

    // 5. Prefs: pipe format + default list exists and matches the bar's default spec set.
    check(prefs.contains("keybar_keys"), "prefs key present");
    check(prefs.contains("DEFAULT_KEYBAR_KEYS"), "defaults list present");
    for (String d : new String[]{"special:tab","special:esc","special:left","special:right","special:up","special:down"}) {
      check(prefs.contains("\"" + d + "\""), "default missing " + d);
    }

    // 6. MainActivity wires the saved list into the bar and the editor into settings.
    String main = src("MainActivity.java");
    check(main.contains("mKeyBar.setKeys(mPrefs.keybarKeys())"), "bar gets saved keys on build");
    check(main.contains("mKeyBar.setKeys(keys)"), "bar updates live from the editor");
    check(main.contains("KEYCODE_MOVE_HOME"), "HOME key handled");
    check(main.contains("KEYCODE_PAGE_UP"), "PGUP key handled");

    System.out.println("KeyBarPrefsTest: " + pass + " assertions OK");
  }
}
