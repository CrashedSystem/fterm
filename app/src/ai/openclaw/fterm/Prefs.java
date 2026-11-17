package ai.openclaw.fterm;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything the terminal remembers between launches. */
public final class Prefs {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8791;

    /** Windows Terminal "Campbell", One Dark, Solarized Dark, Dracula, OLED. */
    public static final String[] SCHEME_NAMES = {
            "Campbell", "One Dark", "Solarized Dark", "Dracula", "OLED (순수 검정)", "Miku"
    };

    private static final String FILE = "fterm";

    private final SharedPreferences mSp;

    public Prefs(Context context) {
        mSp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private SharedPreferences sp() {
        return mSp;
    }

    public String host() { return sp().getString("host", DEFAULT_HOST); }
    public int port() { return sp().getInt("port", DEFAULT_PORT); }
    public String token() { return sp().getString("token", ""); }
    public void setToken(String t) { sp().edit().putString("token", t == null ? "" : t).apply(); }

    /**
     * Terminal font size in dp.
     *
     * Android text size is in px, and this screen is 600 dpi (density 3.75), so a raw
     * "14" would be 3.7dp - unreadable. Model it in dp like a settings screen would,
     * and convert at the point of use.
     */
    public int fontSizeDp() { return sp().getInt("font_dp", 12); }
    public void setFontSizeDp(int dp) { sp().edit().putInt("font_dp", dp).apply(); }

    public int scheme() { return sp().getInt("scheme", 0); }
    public void setScheme(int s) { sp().edit().putInt("scheme", s).apply(); }

    public boolean keyBarVisible() { return sp().getBoolean("keybar", true); }
    public void setKeyBarVisible(boolean v) { sp().edit().putBoolean("keybar", v).apply(); }

    /** Some IMEs (Samsung in particular) mangle a TYPE_NULL input connection. */
    /**
     * Some IMEs mangle a TYPE_NULL connection: the Samsung keyboard commits each jamo of a
     * composition separately, so "한" arrives as U+1112 U+1161 U+1102 and every intermediate
     * state is echoed. TYPE_TEXT_VARIATION_VISIBLE_PASSWORD makes the IME treat the field as
     * normal text with suggestions off, which gives a proper composing region.
     */
    public boolean enforceCharBasedInput() { return sp().getBoolean("enforce_char", true); }
    public void setEnforceCharBasedInput(boolean v) { sp().edit().putBoolean("enforce_char", v).apply(); }

    /** Debug switch: log every IME and key event to logcat. */
    public boolean imeLog() { return sp().getBoolean("ime_log", false); }
    public void setImeLog(boolean v) { sp().edit().putBoolean("ime_log", v).apply(); }

    /** Terminal bell: vibration and/or a beep when the shell rings BEL. */
    public boolean bellVibrate() { return sp().getBoolean("bell_vibrate", true); }
    public void setBellVibrate(boolean v) { sp().edit().putBoolean("bell_vibrate", v).apply(); }

    public boolean bellSound() { return sp().getBoolean("bell_sound", true); }
    public void setBellSound(boolean v) { sp().edit().putBoolean("bell_sound", v).apply(); }

    public boolean ctrlSpaceWorkaround() { return sp().getBoolean("ctrl_space", false); }
    public void setCtrlSpaceWorkaround(boolean v) { sp().edit().putBoolean("ctrl_space", v).apply(); }

    public boolean backIsEscape() { return sp().getBoolean("back_escape", false); }
    public void setBackIsEscape(boolean v) { sp().edit().putBoolean("back_escape", v).apply(); }

    public int cursorBlinkMs() { return sp().getInt("cursor_blink", 500); }

    public boolean showKeyboardOnStart() { return sp().getBoolean("kb_on_start", true); }
    public void setShowKeyboardOnStart(boolean v) { sp().edit().putBoolean("kb_on_start", v).apply(); }

    /**
     * The user-configured key bar keys, as action specs ("special:tab", "raw:\u0003").
     * Stored as one pipe-delimited string: the specs never contain a pipe (control chars
     * and punctuation from the catalogue only), so splitting is exact.
     */
    public static final String[] DEFAULT_KEYBAR_KEYS = {
            "mod:ctrl", "mod:alt", "mod:shift",
            "special:tab", "special:esc", "raw:\u0003",
            "special:left", "special:right", "special:up", "special:down",
    };

    public java.util.List<String> keybarKeys() {
        String s = sp().getString("keybar_keys", null);
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (s != null) {
            for (String part : s.split("\\|", -1)) {
                if (!part.isEmpty()) out.add(part);
            }
            // Migrate a pre-modifier list: the modifiers were fixed then, and a saved
            // list without any of them would lose CTRL/ALT/SHIFT on first run.
            boolean hasMod = false;
            for (String k : out) if (k.startsWith("mod:")) hasMod = true;
            if (!hasMod) {
                out.add(0, "mod:ctrl");
                out.add(1, "mod:alt");
                out.add(2, "mod:shift");
            }
        }
        if (out.isEmpty()) {
            for (String d : DEFAULT_KEYBAR_KEYS) out.add(d);
        }
        return out;
    }

    public void setKeybarKeys(java.util.List<String> keys) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) b.append('|');
            b.append(keys.get(i));
        }
        sp().edit().putString("keybar_keys", b.toString()).apply();
    }
}
