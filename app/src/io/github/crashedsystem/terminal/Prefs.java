package io.github.crashedsystem.terminal;

import android.content.Context;
import android.content.SharedPreferences;

import com.termux.terminal.TextStyle;

/** Everything the terminal remembers between launches. */
public final class Prefs {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8791;

    /** One string holding every custom scheme; see {@link CustomSchemes#encode}. */
    private static final String CUSTOM_SCHEMES_KEY = "custom_schemes";

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
    public int fontSizeDp() { return sp().getInt("font_dp", FontZoom.DEFAULT_DP); }
    public void setFontSizeDp(int dp) { sp().edit().putInt("font_dp", dp).apply(); }

    public int scheme() { return sp().getInt("scheme", 0); }
    public void setScheme(int s) { sp().edit().putInt("scheme", s).apply(); }

    /**
     * The saved custom schemes, in list order. Never null; empty when the user deleted them
     * all.
     *
     * <p>An absent key means the app is older than this method or has never saved a scheme.
     * That case is migrated from the nineteen keys the single palette used to live in, and
     * written back in the new form; the old keys are left in place so nothing is lost if a
     * later build reads them again.
     */
    public CustomSchemes customSchemes() {
        String stored = sp().getString(CUSTOM_SCHEMES_KEY, null);
        if (stored != null) return CustomSchemes.decode(stored);
        CustomSchemes out = CustomSchemes.fromLegacy(CustomSchemes.defaultName(),
                legacyCustomSlots());
        setCustomSchemes(out.encode());
        return out;
    }

    /** Stores every custom scheme as one string; "" is a real value and means "none". */
    public void setCustomSchemes(String encoded) {
        sp().edit().putString(CUSTOM_SCHEMES_KEY, encoded == null ? "" : encoded).apply();
    }

    /**
     * The single custom palette this app used to store: sixteen ANSI keys plus fg/bg/cursor.
     *
     * <p>Seeded from Campbell per key, so an installation that changed two colours keeps the
     * other seventeen as it showed them rather than as black.
     */
    private int[] legacyCustomSlots() {
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

    /**
     * The cursor colour override, or 0 for "follow the scheme".
     *
     * <p>Zero is not a colour: it has no alpha, so it cannot be written by the hex editor and
     * cannot collide with one. Stored separately from the custom scheme's cursor slot because
     * the two answer different questions - the slot is part of one palette, this is "my cursor
     * is this colour whatever palette I pick", which is the usual reason to override it.
     */
    public int cursorColor() { return sp().getInt("cursor_color", 0); }

    public void setCursorColor(int color) { sp().edit().putInt("cursor_color", color).apply(); }

    public boolean keyBarVisible() { return sp().getBoolean("keybar", true); }
    public void setKeyBarVisible(boolean v) { sp().edit().putBoolean("keybar", v).apply(); }

    /**
     * Whether a keyboard that can type hides the key bar.
     *
     * <p>On by default: on DeX and on a tablet with a cover keyboard the bar repeats keys the
     * keyboard already has and takes 88dp of the window to do it. It is a separate switch from
     * {@link #keyBarVisible()} so a user who wants the bar always - or never, keyboard or not -
     * can say so without giving up the other behaviour.
     */
    public boolean keyBarAutoHide() { return sp().getBoolean("keybar_auto_hide", true); }
    public void setKeyBarAutoHide(boolean v) {
        sp().edit().putBoolean("keybar_auto_hide", v).apply();
    }

    /**
     * The terminal face: an id from {@link Fonts#catalog}.
     *
     * <p>An id, not a path, because the catalog scans: a path stored today may be a deleted
     * file tomorrow, and an id that no longer resolves to a font falls back to the bundled
     * face instead of leaving the terminal blank. Old stores hold "iosevka"/"droid"/"cutive";
     * those no longer match anything, which is the intended migration - all three resolve to
     * the bundled face, which is what the first of them already meant.
     */
    public String fontFamily() { return sp().getString("font_family", Fonts.ASSET_ID); }
    public void setFontFamily(String v) {
        sp().edit().putString("font_family", v == null ? Fonts.ASSET_ID : v).apply();
    }

    /**
     * The Korean face: an id from {@link Fonts#hangulCatalog}.
     *
     * <p>Separate from {@link #fontFamily} because the two lists are filtered by opposite
     * rules - the Latin face must define a cell, the Korean face only has to have the glyphs -
     * and because one preference was silently forcing the compiled-in Pretendard for every
     * Korean syllable no matter what the picker said.
     */
    public String hangulFamily() {
        return sp().getString("hangul_family", Fonts.HANGUL_ASSET_ID);
    }
    /**
     * The face for Japanese and Chinese text, or empty to let the platform choose.
     *
     * <p>Empty is the default on purpose: a terminal that prints an ASCII program should not
     * have to load a 30 MB CJK face to be correct, and the platform's own fallback chain
     * answer the occasional kanji. The setting exists because a device's fallback is often
     * the wrong regional variant - a Korean phone picks the Korean glyph forms for Han - and
     * a user reading Japanese wants the Japanese ones.
     */
    public String cjkFamily() {
        return sp().getString("cjk_font", "");
    }

    public void setCjkFamily(String v) {
        sp().edit().putString("cjk_font", v == null ? "" : v).apply();
    }

    public void setHangulFamily(String v) {
        sp().edit().putString("hangul_family", v == null ? Fonts.HANGUL_ASSET_ID : v).apply();
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
     * composition separately, so io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0023) arrives as U+1112 U+1161 U+1102 and every intermediate
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
    public void setCursorBlinkMs(int ms) { sp().edit().putInt("cursor_blink", ms).apply(); }

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

    // ------------------------------------------------------------- background image

    /** The picture itself lives in {@link BackgroundStore}; these are how it is drawn. */
    public int backgroundOpacity() {
        return BackgroundSpec.opacity(sp().getInt("bg_opacity", BackgroundSpec.DEFAULT_OPACITY));
    }
    public void setBackgroundOpacity(int percent) {
        sp().edit().putInt("bg_opacity", BackgroundSpec.opacity(percent)).apply();
    }

    /** "contain" or "cover"; see {@link BackgroundSpec}. */
    public String backgroundFit() {
        return BackgroundSpec.fit(sp().getString("bg_fit", BackgroundSpec.FIT_CONTAIN));
    }
    public void setBackgroundFit(String fit) {
        sp().edit().putString("bg_fit", BackgroundSpec.fit(fit)).apply();
    }

    /** Percent of black over the picture, 0 to {@link BackgroundSpec#MAX_DIM}. */
    public int backgroundDim() {
        return BackgroundSpec.dim(sp().getInt("bg_dim", 0));
    }
    public void setBackgroundDim(int percent) {
        sp().edit().putInt("bg_dim", BackgroundSpec.dim(percent)).apply();
    }

    /** Where the picture sits when it does not fill the terminal; see {@link BackgroundSpec}. */
    public String backgroundAlign() {
        return BackgroundSpec.align(sp().getString("bg_align", BackgroundSpec.DEFAULT_ALIGN));
    }
    public void setBackgroundAlign(String align) {
        sp().edit().putString("bg_align", BackgroundSpec.align(align)).apply();
    }
}
