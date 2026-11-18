package ai.openclaw.fterm;

import android.content.Context;
import android.content.SharedPreferences;

import com.termux.terminal.TextStyle;

/** Everything the terminal remembers between launches. */
public final class Prefs {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8791;

    /** Windows Terminal "Campbell", One Dark, Solarized Dark, Dracula, OLED, Miku, 사용자 지정. */
    public static final String[] SCHEME_NAMES = {
            "Campbell", "One Dark", "Solarized Dark", "Dracula", "OLED (순수 검정)", "Miku",
            "사용자 지정"
    };

    /** The index of "사용자 지정" in {@link #SCHEME_NAMES}; its sixteen colours are editable. */
    public static final int CUSTOM_SCHEME = SCHEME_NAMES.length - 1;

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

    /**
     * The custom scheme's nineteen slots: sixteen ANSI colours, then foreground, background
     * and cursor. Stored as nineteen ints rather than one string because each one is edited
     * independently and a partial write should not corrupt the rest.
     *
     * <p>Seeded from Campbell so a fresh install showing "사용자 지정" shows a real palette
     * rather than black on black.
     */
    public int[] customSlots() {
        int[] campbell = Schemes.campbell();
        int[] out = new int[Schemes.CUSTOM_SLOTS];
        for (int i = 0; i < 16; i++) {
            out[i] = sp().getInt("custom_" + i, campbell[i]);
        }
        out[16] = sp().getInt("custom_fg", campbell[TextStyle.COLOR_INDEX_FOREGROUND]);
        out[17] = sp().getInt("custom_bg", campbell[TextStyle.COLOR_INDEX_BACKGROUND]);
        out[18] = sp().getInt("custom_cursor", campbell[TextStyle.COLOR_INDEX_CURSOR]);
        return out;
    }

    public void setCustomSlot(int slot, int color) {
        String key = slot < 16 ? "custom_" + slot
                : slot == 16 ? "custom_fg" : slot == 17 ? "custom_bg" : "custom_cursor";
        sp().edit().putInt(key, color).apply();
    }

    public void setCustomSlots(int[] slots) {
        if (slots == null || slots.length != Schemes.CUSTOM_SLOTS) return;
        android.content.SharedPreferences.Editor e = sp().edit();
        for (int i = 0; i < 16; i++) e.putInt("custom_" + i, slots[i]);
        e.putInt("custom_fg", slots[16]);
        e.putInt("custom_bg", slots[17]);
        e.putInt("custom_cursor", slots[18]);
        e.apply();
    }

    public boolean keyBarVisible() { return sp().getBoolean("keybar", true); }
    public void setKeyBarVisible(boolean v) { sp().edit().putBoolean("keybar", v).apply(); }

    /** The Latin face for the terminal: an id from {@link Fonts#LATIN_IDS}. */
    public String fontFamily() { return sp().getString("font_family", "iosevka"); }
    public void setFontFamily(String v) {
        sp().edit().putString("font_family", v == null ? "iosevka" : v).apply();
    }

    /** Where a new session starts; "" means the daemon's home directory. */
    public String startDir() { return sp().getString("start_dir", ""); }
    public void setStartDir(String v) { sp().edit().putString("start_dir", v == null ? "" : v).apply(); }

    /** Which program a new session runs; "" means the daemon's default shell. */
    public String startShell() { return sp().getString("start_shell", ""); }
    public void setStartShell(String v) { sp().edit().putString("start_shell", v == null ? "" : v).apply(); }

    /**
     * The mark at the left of every tab: a glyph id, or literal text.
     *
     * <p>Stored as whatever the user entered. {@link TabIcons} is what decides whether that
     * is one of its paths or a string to print, so adding a glyph later does not need a
     * migration - only a longer switch in one place.
     */
    public String tabIcon() { return sp().getString("tab_icon", TabIcons.DEFAULT_ID); }
    public void setTabIcon(String v) {
        sp().edit().putString("tab_icon", v == null ? "" : v).apply();
    }

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

    /**
     * The cursor shape: 0 block, 1 underline, 2 bar. Stored as the emulator's own constants
     * so the only translation is the one that has to exist (an unknown value -> block).
     */
    public int cursorStyle() { return sp().getInt("cursor_style", 0); }
    public void setCursorStyle(int style) { sp().edit().putInt("cursor_style", style).apply(); }

    /**
     * The user's profiles, encoded by {@link Profiles#encode}.
     *
     * <p>One string rather than a set of keys: a profile is added and removed as a unit, and
     * a shared-preference key per profile would leave orphans behind on delete.
     */
    public String profiles() { return sp().getString("profiles", ""); }
    public void setProfiles(String v) {
        sp().edit().putString("profiles", v == null ? "" : v).apply();
    }

    /** WT's experimental.retroTerminalEffect: a glow and scanlines over the text. */
    public boolean retroEffect() { return sp().getBoolean("retro_effect", false); }
    public void setRetroEffect(boolean v) { sp().edit().putBoolean("retro_effect", v).apply(); }

    /** Whether the cursor blinks at all. WT calls this cursorBlink. */
    public boolean cursorBlink() { return sp().getBoolean("cursor_blink_on", true); }
    public void setCursorBlink(boolean v) { sp().edit().putBoolean("cursor_blink_on", v).apply(); }

    public boolean showKeyboardOnStart() { return sp().getBoolean("kb_on_start", true); }
    public void setShowKeyboardOnStart(boolean v) { sp().edit().putBoolean("kb_on_start", v).apply(); }
}
