package ai.openclaw.fterm;

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

    @Override public String startDir() { return a.mPrefs.startDir(); }
    @Override public void onStartDir(String dir) { a.mPrefs.setStartDir(dir); }

    @Override public String startShell() { return a.mPrefs.startShell(); }
    @Override public void onStartShell(String shell) { a.mPrefs.setStartShell(shell); }

    @Override public int[] customSlots() { return a.mCustom; }

    @Override public void onCustomSlot(int slot, int color) {
        if (slot < 0 || slot >= Schemes.CUSTOM_SLOTS) return;
        a.mCustom[slot] = color;
        a.mPrefs.setCustomSlot(slot, color);
        // Editing a slot is only visible if the custom scheme is the one in use, so the edit
        // also selects it - that is what the user clicked into.
        a.applyScheme(Schemes.CUSTOM_INDEX);
    }

    @Override public void onCustomScheme(int[] slots) {
        if (slots == null || slots.length != Schemes.CUSTOM_SLOTS) return;
        a.mCustom = slots.clone();
        a.mPrefs.setCustomSlots(a.mCustom);
        a.applyScheme(Schemes.CUSTOM_INDEX);
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
        if (a.mKeyBar != null) a.mKeyBar.setVisible(visible);
    }

    @Override public boolean keyboardOnStart() { return a.mPrefs.showKeyboardOnStart(); }
    @Override public void onKeyboardOnStart(boolean on) { a.mPrefs.setShowKeyboardOnStart(on); }

    @Override public boolean backIsEscape() { return a.mPrefs.backIsEscape(); }
    @Override public void onBackIsEscape(boolean on) { a.mPrefs.setBackIsEscape(on); }

    @Override public String statusLine() {
        return a.mTabs.size() + "개 · " + a.mPrefs.host() + ":" + a.mPrefs.port();
    }
}
