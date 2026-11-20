package io.github.crashedsystem.fterm;

/**
 * The sheet's view of the activity.
 *
 * <p>{@link SettingsSheet} is deliberately ignorant of where a preference is stored and of
 * what applying one costs: it reads a value, calls a setter, and re-renders. This class is
 * that boundary - every method either forwards to {@link Prefs} or asks the activity to
 * re-apply something that cannot be done by writing a preference alone (the font stack, the
 * emulator's cursor, the live key bar). Keeping it out of the activity is what lets the
 * activity stay a coordinator as the settings surface grows.
 */
final class SettingsHost implements SettingsSheet.Host {

    private final MainActivity a;

    SettingsHost(MainActivity activity) {
        this.a = activity;
    }

    @Override public int fontSizeDp() { return a.mFontDp; }
    @Override public void onFontSize(int dp) { a.applyFontSize(dp); }

    @Override public String daemonHost() { return a.mPrefs.host(); }
    @Override public int daemonPort() { return a.mPrefs.port(); }
    @Override public String daemonToken() { return a.mPrefs.token(); }

    @Override public int scheme() { return a.mScheme; }
    @Override public void onScheme(int index) { a.applyScheme(index); }

    @Override public String schemeName() { return Schemes.nameOf(a.mScheme, a.mCustoms); }

    @Override public int customSchemeCount() { return a.mCustoms.size(); }

    @Override public String customSchemeName(int index) { return a.mCustoms.nameAt(index); }

    @Override public int addCustomScheme(String name, int[] slots) {
        int at = a.mCustoms.add(name, slots);
        if (at < 0) return -1;
        a.mPrefs.setCustomSchemes(a.mCustoms.encode());
        a.applyScheme(Schemes.CUSTOM_INDEX + at);   // an added scheme is the one to use
        return at;
    }

    @Override public void onRenameCustomScheme(int index, String name) {
        if (a.mCustoms.rename(index, name)) a.mPrefs.setCustomSchemes(a.mCustoms.encode());
    }

    @Override public void onDeleteCustomScheme(int index) {
        if (!a.mCustoms.remove(index)) return;
        a.mPrefs.setCustomSchemes(a.mCustoms.encode());
        int scheme = a.mScheme;
        int at = CustomSchemes.themeIndexOf(scheme);
        if (at > index) {
            scheme--;                              // every scheme past the hole moved down
        } else if (at == index) {
            int left = a.mCustoms.size();
            scheme = left == 0 ? 0 : Schemes.CUSTOM_INDEX + Math.min(index, left - 1);
        }
        a.applyScheme(Schemes.clampScheme(scheme, a.mCustoms.size()));
    }

    @Override public int cursorColor() { return a.mPrefs.cursorColor(); }

    @Override public void onCursorColor(int color) {
        a.mPrefs.setCursorColor(color);
        // applyScheme() is what pushes a palette into the emulator, and the cursor colour is
        // written into that palette on the way. Re-applying only the cursor shape would leave
        // the old colour on screen until the next scheme change.
        a.applyScheme(a.mScheme);
    }

    @Override public int cursorStyle() { return a.mPrefs.cursorStyle(); }
    @Override public void onCursorStyle(int style) {
        a.mPrefs.setCursorStyle(style);
        a.applyCursor();
    }

    @Override public boolean cursorBlink() { return a.mPrefs.cursorBlink(); }
    @Override public void onCursorBlink(boolean on) {
        a.mPrefs.setCursorBlink(on);
        a.applyCursor();
    }

    @Override public int cursorBlinkMs() { return a.mPrefs.cursorBlinkMs(); }
    @Override public void onCursorBlinkMs(int ms) {
        a.mPrefs.setCursorBlinkMs(ms);
        a.applyCursor();
    }

    @Override public boolean retroEffect() { return a.mPrefs.retroEffect(); }
    @Override public void onRetroEffect(boolean on) {
        a.mPrefs.setRetroEffect(on);
        a.applyRetro();
    }

    @Override public String tabIcon() { return a.mPrefs.tabIcon(); }
    @Override public void onTabIcon(String id) {
        a.mPrefs.setTabIcon(id);
        a.refreshTabIcons();   // existing chips were built with the old mark
    }

    @Override public String fontFamily() { return a.mPrefs.fontFamily(); }
    @Override public void onFontFamily(String id) { a.applyFontFamily(id); }

    @Override public String hangulFamily() { return a.mPrefs.hangulFamily(); }
    @Override public void onHangulFamily(String id) { a.applyHangulFamily(id); }

    @Override public String cjkFamily() { return a.mPrefs.cjkFamily(); }
    @Override public void onCjkFamily(String id) { a.applyCjkFamily(id); }

    @Override public String startDir() { return a.mPrefs.startDir(); }
    @Override public void onStartDir(String dir) { a.mPrefs.setStartDir(dir); }

    @Override public String startShell() { return a.mPrefs.startShell(); }
    @Override public void onStartShell(String shell) { a.mPrefs.setStartShell(shell); }

    @Override public int[] customSlots() { return a.mCustom; }

    @Override public void onCustomSlot(int slot, int color) {
        if (slot < 0 || slot >= Schemes.CUSTOM_SLOTS) return;
        // Built-in schemes are compiled in; only a saved one is editable.
        int theme = CustomSchemes.themeIndexOf(a.mScheme);
        if (theme < 0) return;
        a.mCustom[slot] = color;
        if (a.mCustoms.setSlots(theme, a.mCustom)) {
            a.mPrefs.setCustomSchemes(a.mCustoms.encode());
        }
        // Editing a slot is only visible if the custom scheme is the one in use, so the edit
        // also selects it - that is what the user clicked into.
        a.applyScheme(a.mScheme);
    }

    @Override public boolean backgroundSet() {
        return a.mBackdrop != null && a.mBackdrop.image() != null;
    }

    @Override public String backgroundName() {
        return a.mBackdrop == null ? null : a.mBackdrop.describe();
    }

    @Override public int backgroundOpacity() { return a.mPrefs.backgroundOpacity(); }

    @Override public void onBackgroundOpacity(int percent) {
        a.mPrefs.setBackgroundOpacity(percent);
        if (a.mBackdrop != null) a.mBackdrop.reload();
    }

    @Override public String backgroundFit() { return a.mPrefs.backgroundFit(); }

    @Override public void onBackgroundFit(String fit) {
        a.mPrefs.setBackgroundFit(fit);
        if (a.mBackdrop != null) a.mBackdrop.reload();
    }

    @Override public int backgroundDim() { return a.mPrefs.backgroundDim(); }

    @Override public void onBackgroundDim(int percent) {
        a.mPrefs.setBackgroundDim(percent);
        if (a.mBackdrop != null) a.mBackdrop.reload();
    }

    @Override public String backgroundAlign() { return a.mPrefs.backgroundAlign(); }

    @Override public void onBackgroundAlign(String align) {
        a.mPrefs.setBackgroundAlign(align);
        if (a.mBackdrop != null) a.mBackdrop.reload();
    }

    @Override public void onBackgroundChanged() {
        if (a.mBackdrop != null) a.mBackdrop.reload();
    }

    @Override public java.util.List<Profiles.Profile> profiles() {
        return Profiles.decode(a.mPrefs.profiles());
    }

    @Override public void onProfiles(java.util.List<Profiles.Profile> list) {
        a.mPrefs.setProfiles(Profiles.encode(list));
    }

    @Override public boolean bellVibrate() { return a.mPrefs.bellVibrate(); }
    @Override public boolean bellSound() { return a.mPrefs.bellSound(); }
    @Override public void onBell(boolean vibrate, boolean sound) {
        a.mPrefs.setBellVibrate(vibrate);
        a.mPrefs.setBellSound(sound);
    }

    @Override public boolean keyBarVisible() { return a.mPrefs.keyBarVisible(); }
    @Override public void onKeyBarVisible(boolean visible) {
        a.mPrefs.setKeyBarVisible(visible);
        // Not setVisible(visible): a keyboard that is attached still hides the bar, and this
        // is the one place that decides.
        a.applyKeyBarVisibility();
    }

    @Override public boolean keyBarAutoHide() { return a.mPrefs.keyBarAutoHide(); }
    @Override public void onKeyBarAutoHide(boolean on) {
        a.mPrefs.setKeyBarAutoHide(on);
        a.applyKeyBarVisibility();
    }

    @Override public boolean keyboardOnStart() { return a.mPrefs.showKeyboardOnStart(); }
    @Override public void onKeyboardOnStart(boolean on) { a.mPrefs.setShowKeyboardOnStart(on); }

    @Override public boolean backIsEscape() { return a.mPrefs.backIsEscape(); }
    @Override public void onBackIsEscape(boolean on) { a.mPrefs.setBackIsEscape(on); }

    @Override public String statusLine() {
        return a.mTabs.size() + io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0197) + a.mPrefs.host() + ":" + a.mPrefs.port();
    }
}
