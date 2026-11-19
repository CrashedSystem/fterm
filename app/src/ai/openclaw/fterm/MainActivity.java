package ai.openclaw.fterm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Toast;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalRenderer;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import java.util.ArrayList;
import java.util.List;

/**
 * One screen: a tab strip, the terminal, and the key bar.
 *
 * Windows Terminal is the model for the layout: tabs on top with a "+", one large dark
 * surface, and a compact key row above the keyboard. Nothing here is a fragment or a
 * navigation drawer - a phone terminal should come up on a shell in one frame.
 */
public final class MainActivity extends Activity implements TerminalViewClient, TerminalSessionClient {

    private static final String TAG = "fterm";

    Prefs mPrefs;
    LinearLayout mRoot;
    LinearLayout mTopBar;
    LinearLayout mTabStrip;
    HorizontalScrollView mTabScroll;
    FrameLayout mTermFrame;
    private FindBar mFind;
    KeyBar mKeyBar;
    TerminalView mTermView;

    final List<Tab> mTabs = new ArrayList<>();
    Tab mActive;

    /** Font size in dp; the view wants px, so {@link #fontPx()} converts. */
    int mFontDp;
    /** Iosevka / Nerd Symbols / Pretendard, with the Hangul size-adjust derived per size. */
    private Fonts mFonts;
    /** The scanline overlay, or null when the effect is off; see Appearance. */
    RetroOverlay mRetro;
    /** Loaded once; null when the user has no picture, in which case the scheme shows alone. */
    BackgroundLayer mBackdrop;
    private Bell mBell;
    private SettingsSheet mSettings;
    /** Windows Terminal chrome: the command palette and the icon strip. */
    private Chrome mChrome;
    private Notifier mNotifier;
    DaemonBoot mBoot;
    int mScheme;
    /** Every saved custom scheme, in list order; the source of the scheme index range. */
    CustomSchemes mCustoms;
    /** The active scheme's 19 editable slots, kept in step with Prefs and mCustoms. */
    int[] mCustom;
    /** Fractional font size in dp, so a pinch accumulates instead of rounding every event away. */
    float mZoomDp;

    private boolean mShowKeyboardOnce = true;

    final Handler mUi = new Handler(Looper.getMainLooper());

    /** The session path: the daemon, the token, and a tab's shell. */
    private final SessionOpen mSessions = new SessionOpen(this);
    /** The key path: key bar, hardware keys and the sticky modifiers, in one place. */
    private final KeyInput mKeys = new KeyInput(this);
    /** Applying an appearance choice, as opposed to storing it. */
    private final Appearance mLook = new Appearance(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefs = new Prefs(this);
        mFontDp = mPrefs.fontSizeDp();
        mZoomDp = mFontDp;
        mCustoms = mPrefs.customSchemes();
        mScheme = Schemes.clampScheme(mPrefs.scheme(), mCustoms.size());
        mCustom = mCustoms.slotsForScheme(mScheme);

        applyChrome();
        buildUi();
        mBoot = new DaemonBoot(this, mUi, mTermFrame, mTermView);

        // A foreground service keeps this process out of Android's freezer. Measured cause of
        // the idle death: with fterm backgrounded, `dumpsys activity processes` reports
        // isFrozen=true, and a frozen process stops reading its socket.
        TerminalService.start(this);
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1002);
        }
        // Clipboard images (screenshots) live in the gallery; without this permission the
        // content:// URI decodes to nothing and "paste my screenshot" silently fails.
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.READ_MEDIA_IMAGES")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.READ_MEDIA_IMAGES"}, 1003);
        }

        // Nothing is attached yet, so the frame would sit as a blank black area until the
        // live query returns (up to the 2.5s timeout). Say so instead of showing a void -
        // "the first shell is blank" was this empty frame, not a dead shell.
        mBoot.splash("셸에 연결하는 중…");
        mSessions.start();
    }

    private void applyChrome() {
        Window w = getWindow();
        // From API 35 the bar colours are ignored and the app is drawn behind the bars, so
        // the layout insets itself instead. On 30+ the IME inset drives the bottom padding,
        // which makes ADJUST_RESIZE unnecessary (and it is a no-op there anyway).
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        EdgeToEdge.enable(this);
        if (Schemes.isOled(mScheme, mCustom) && android.os.Build.VERSION.SDK_INT < 35) {
            getWindow().setStatusBarColor(0xff000000);
        }
    }

    // ---------------------------------------------------------------- UI build

    private void buildUi() {
        mRoot = new LinearLayout(this);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mRoot.setBackgroundColor(0xff1e1e1e);

        // Tab strip
        mTabStrip = new LinearLayout(this);
        mTabStrip.setOrientation(LinearLayout.HORIZONTAL);
        mTabStrip.setGravity(Gravity.BOTTOM);
        mTabStrip.setBackgroundColor(0xff2b2b2b);
        mTabStrip.setPadding(dp(4), dp(2), dp(4), 0);

        mTabScroll = new HorizontalScrollView(this);
        mTabScroll.setHorizontalScrollBarEnabled(false);
        mTabScroll.setFillViewport(false);
        mTabScroll.setLayoutParams(new LinearLayout.LayoutParams(0, dp(38), 1f));
        mTabScroll.addView(mTabStrip);

        // WT's tab row, and nothing else.
        //
        // TitlebarControl.xaml is a ContentPresenter (the tabs) and then the window buttons;
        // the only control in the row itself is TabRowControl's NewTabButton, which is a
        // SplitButton: a plus that opens a tab, and a chevron that opens the dropdown. There is
        // no hamburger anywhere in WT, and no gear - settings is a row inside that dropdown.
        //
        // This app had both, so the bar was three invented buttons wide and the tab strip lost
        // the space. Removing them is not a simplification, it is the layout WT actually has.
        View plus = Chrome.icon(this, "plus", 28, false, v -> newTab(true));
        View drop = Chrome.icon(this, "chevron", 22, true, v -> showCommandMenu(v));
        // Long-press "+" still opens the dropdown: it is the gesture that was here before, and
        // it costs nothing to keep for a hand that has already learned it.
        plus.setOnLongClickListener(v -> {
            showCommandMenu(v);
            return true;
        });

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.BOTTOM);
        top.setBackgroundColor(0xff2b2b2b);
        top.setPadding(dp(2), 0, dp(2), 0);
        top.addView(mTabScroll);
        top.addView(plus);
        // The chevron sits tight against the plus with no gap: the two halves read as one
        // split button, which is what they are.
        top.addView(drop);
        top.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        mTopBar = top;

        // Terminal
        mTermFrame = new FrameLayout(this);
        mTermFrame.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mTermView = new TerminalView(this, null);
        mTermView.setTerminalViewClient(this);
        // The stock app sets these from XML (activity_termux.xml). Built in code there is no
        // XML, and without focusableInTouchMode the view never takes focus, so the IME has no
        // input connection and hardware keys are delivered to the activity instead.
        mTermView.setFocusable(true);
        mTermView.setFocusableInTouchMode(true);
        mTermView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mTermFrame.addView(mTermView);

        // Key bar
        mKeyBar = new KeyBar(this, new KeyBar.Action() {
            @Override public void special(String name) { sendEscape(name); }
            @Override public void raw(String bytes) { sendRaw(bytes); }
            @Override public void toggleKeyboard() { MainActivity.this.toggleKeyboard(); }
            @Override public void paste() { pasteClipboard(); }
            @Override public void function(int number) { sendFunctionKey(number); }
        });

        // Images arrive by one deliberate tap: the keyboard's clipboard panel hands the
        // picture over through commitContent() (see TerminalView), and this is where it
        // becomes a path in the prompt. Nothing polls the clipboard and nothing fires on
        // focus - the user asked for "tap the clipboard", not "it pastes itself".
        ClipboardImage.selfTestIfRequested(this);

        mRoot.addView(top);
        mFind = new FindBar(this, mTermView);
        mRoot.addView(mFind.view());
        mRoot.addView(mTermFrame);
        mRoot.addView(mKeyBar.view());
        setContentView(mRoot);
        EdgeToEdge.applyInsets(mRoot, mTopBar, mKeyBar.view(), dp(38), dp(88));

        mChrome = new Chrome(this, new Chrome.Commands() {
            @Override public void newTab() { MainActivity.this.newTab(true); }
            @Override public void closeTab() { if (mActive != null) confirmClose(mActive); }
            @Override public void renameTab() { renameActiveTab(); }
            @Override public void nextTab() { cycleTab(); }
            @Override public void paste() { pasteClipboard(); }
            @Override public void copy() { copySelection(); }
            @Override public void biggerFont() { applyFontSize(mFontDp + 1); }
            @Override public void smallerFont() { applyFontSize(mFontDp - 1); }
            @Override public void settings() { showSettings(); }
            @Override public void toggleKeyboard() { MainActivity.this.toggleKeyboard(); }
            @Override public void palette() { showCommandPalette(); }
            @Override public void about() { showAbout(); }
            @Override public java.util.List<Profiles.Profile> profiles() {
                return Profiles.decode(mPrefs.profiles());
            }
            @Override public void openProfile(Profiles.Profile p) {
                MainActivity.this.newTab(p, true);
            }
            @Override public void manageProfiles() { showSettings("profiles"); }
            @Override public void find() { MainActivity.this.find(); }
        });

        loadFonts();
        mBackdrop = new BackgroundLayer(this, mPrefs);
        // IME tracing is how the Hangul path gets diagnosed on a real device; the flag is
        // off unless someone sets it in shared prefs.
        mTermView.setIsTerminalViewKeyLoggingEnabled(mPrefs.imeLog());
        registerBackHandler();
        applyFontSize(mFontDp);
        applyScheme(mScheme);
        applyCursor();
        applyRetro();
        mKeyBar.setVisible(mPrefs.keyBarVisible());
    }

    // ---------------------------------------------------------------- sessions
    //
    // The shell itself is SessionOpen's. What is left here is the tab: creating one, keeping
    // the strip in step, and asking SessionOpen for the session on it.

    void newTab(boolean showKeyboard) {
        newTab(null, showKeyboard);
    }

    /**
     * Open a tab from a profile.
     *
     * <p>The profile's directory and shell go into the OPEN frame, so the session starts
     * there rather than being moved there afterwards - a shell that reads its cwd at startup
     * would otherwise disagree with the prompt it prints. The command is different: it has to
     * run inside the started shell, so it is typed in after the first byte comes back.
     */
    void newTab(Profiles.Profile profile, boolean showKeyboard) {
        Tab tab = new Tab();
        tab.profile = profile;
        tab.id = nextSessionId();
        // The profile names the tab before the chip is built, not after: the chip is drawn
        // once from tab.title, and a title set later would leave the tab reading the default.
        if (profile != null && !profile.name.isEmpty()) {
            tab.title = profile.name;
            tab.userTitled = true;   // the profile names the tab; OSC must not rename it
        }
        mTabs.add(tab);
        addTabChip(tab);
        activate(tab);
        mSessions.openSession(tab, showKeyboard);
    }

    /** Session ids are the daemon's handle; keep them small and unique in this tab set. */
    private int nextSessionId() {
        int max = 0;
        for (Tab t : mTabs) max = Math.max(max, t.id);
        return Math.max(1, max + 1);
    }

    void addTabChip(Tab tab) {
        TabStrip.Chip chip = new TabStrip(this, tabIconFor(tab)).create(tab.title,
                () -> activate(tab), () -> confirmClose(tab));
        tab.plate = chip.plate;
        tab.chip = chip.title;
        tab.closeBox = chip.close;
        tab.chipView = chip;
        mTabStrip.addView(chip.plate);
        updateChip(tab);
    }

    /** Re-tint and re-build every tab's mark after the tab icon preference changes. */
    void refreshTabIcons() {
        for (Tab t : mTabs) updateChip(t);
    }

    /**
     * The mark a tab should carry: its profile's own icon, or the global tab icon.
     *
     * <p>The fallback is per tab rather than resolved once at open time so that changing the
     * global icon still moves every tab that did not ask for something else - a profile icon
     * is an override, not a snapshot.
     */
    private String tabIconFor(Tab tab) {
        if (tab.profile != null && tab.profile.icon != null && !tab.profile.icon.isEmpty()) {
            return tab.profile.icon;
        }
        return mPrefs.tabIcon();
    }

    /** Repaint one tab; the strip owns the shapes and the activity owns which tab is active. */
    private void updateChip(Tab tab) {
        if (tab.chipView == null) return;
        int[] palette = Schemes.colors(mScheme, mCustom);
        new TabStrip(this, tabIconFor(tab)).paint(tab.chipView, tab.title, tab == mActive,
                palette[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND],
                Schemes.isOled(mScheme, mCustom));
    }


    void activate(Tab tab) {
        mActive = tab;
        for (Tab t : mTabs) updateChip(t);
        // Scroll the strip so the newly active tab is on screen. Opening a profile from the
        // menu is exactly the case where it is not: the strip is already full, so the new
        // plate lands past the right edge and the user sees a tab that appears not to exist.
        if (tab.plate != null) {
            mUi.post(() -> mTabScroll.smoothScrollTo(
                    Math.max(0, tab.plate.getLeft() - dp(8)), 0));
        }
        if (tab.session != null) {
            mBoot.restore();
            mTermView.attachSession(tab.session);
            mTermView.setTextSize(fontPx());
            applySchemeTo(tab.session);
            mTermView.requestFocus();
        }
    }

    private void confirmClose(Tab tab) {
        new AlertDialog.Builder(this)
                .setMessage("이 탭을 닫을까요? 셸이 종료됩니다.")
                .setPositiveButton("닫기", (d, w) -> closeTab(tab))
                .setNegativeButton("취소", null)
                .show();
    }

    private void closeTab(Tab tab) {
        // The plate is what was added to the strip; the title is its child. Removing the
        // child leaves an empty plate behind - which is exactly the "tab will not close"
        // that a dead shell showed: the title vanished and the row stayed.
        if (tab.plate != null) {
            mTabStrip.removeView(tab.plate);
            tab.plate = null;
        }
        tab.chip = null;
        tab.closeBox = null;
        tab.chipView = null;

        if (tab.session != null) {
            tab.session.finishIfRunning();
            // A session whose shell already exited still has a transport holding a socket
            // and the daemon's side of the session; closing it is what actually ends it.
            if (tab.transport != null) tab.transport.close();
        }

        mTabs.remove(tab);
        if (mTabs.isEmpty()) {
            newTab(true);
        } else if (mActive == tab) {
            activate(mTabs.get(mTabs.size() - 1));
        } else {
            for (Tab t : mTabs) updateChip(t);
        }
    }

    // ---------------------------------------------------------------- appearance
    //
    // Applying a choice is Appearance's job; these forward the names the settings sheet and
    // the menu already call.

    void applyFontSize(int dp) { mLook.applyFontSize(dp); }

    void applyFontFamily(String id) { mLook.applyFontFamily(id); }

    void applyHangulFamily(String id) { mLook.applyHangulFamily(id); }

    void applyCursor() { mLook.applyCursor(); }

    void applyRetro() { mLook.applyRetro(); }

    void applyScheme(int index) { mLook.applyScheme(index); }

    /**
     * Load mterm's font stack.
     *
     * The stack is Iosevka for Latin, a Nerd symbols face for icons, and Pretendard for
     * Hangul, with the Hangul face size-adjusted so one syllable is two Latin cells. mterm
     * writes that as a `font-family` list plus `size-adjust: 115.85%`; here it becomes a
     * {@link TerminalRenderer.FaceSelector} because Android holds one face per Paint, and
     * the adjustment is recomputed from the measured cell so it stays correct at any size.
     */
    void loadFonts() {
        int px = fontPx();
        float cell = mTermView.getFontWidth();
        if (cell <= 0f) {
            // Not laid out yet: measure the Latin face at the requested size directly.
            android.graphics.Paint probe = new android.graphics.Paint();
            probe.setTypeface(Typeface.MONOSPACE);
            probe.setTextSize(px);
            cell = probe.measureText("M");
        }
        mFonts = Fonts.load(this, cell, px, mPrefs.fontFamily(), mPrefs.hangulFamily());
        mTermView.setFaceSelector(new TerminalRenderer.FaceSelector() {
            @Override public Typeface typefaceFor(int cp) { return mFonts.typefaceFor(cp); }
            @Override public float scaleFor(int cp) { return mFonts.scaleFor(cp); }
            @Override public float shiftFor(int cp) {
                // Read the size live: a pinch changes it without reloading the faces, and the
                // shift is a fraction of the em, so a stale px would drift with every zoom.
                return mFonts.isHangul(cp) ? mFonts.hangulShift(fontPx()) : 0f;
            }
        });
        mTermView.setTypeface(mFonts.latin());
    }

    /** dp -> px at this device's density. */
    int fontPx() {
        return Math.max(8, Math.round(mFontDp * getResources().getDisplayMetrics().density));
    }

    void applySchemeTo(TerminalSession session) {
        if (session == null || session.getEmulator() == null) return;
        TerminalColors colors = session.getEmulator().mColors;
        int[] table = Schemes.colors(mScheme, mCustom);
        System.arraycopy(table, 0, TerminalColors.COLOR_SCHEME.mDefaultColors, 0, table.length);
        System.arraycopy(table, 0, colors.mCurrentColors, 0, table.length);
        // The override is applied on top of whichever palette won, here rather than in
        // Schemes: it is an app setting, not part of any scheme, and putting it in the palette
        // table would make every scheme's stored slots disagree with what is on screen.
        final int cursor = mPrefs.cursorColor();
        if (cursor != 0) {
            TerminalColors.COLOR_SCHEME.mDefaultColors[com.termux.terminal.TextStyle.COLOR_INDEX_CURSOR] = cursor;
            colors.mCurrentColors[com.termux.terminal.TextStyle.COLOR_INDEX_CURSOR] = cursor;
        }
        // The picture is a user setting, not part of a scheme; re-bind it after a palette change.
        if (mBackdrop != null) mBackdrop.attach();
        mTermView.invalidate();
    }

    private void showCommandPalette() {
        if (mChrome != null) mChrome.palette();
    }
    private void cycleTab() {
        if (mTabs.size() < 2) return;
        int i = mTabs.indexOf(mActive);
        activate(mTabs.get((i + 1) % mTabs.size()));
    }

    /**
     * Windows Terminal's dropdown menu.
     *
     * The order is WT's: create things first, then act on the current tab, then the app. Each
     * entry names the keyboard shortcut it mirrors, so the menu also teaches the hardware path.
     */
    private void showCommandMenu(View anchor) {
        // The menu itself lives in Chrome, which is where the glyphs and the plate primitives
        // are. The activity only says "this is the anchor".
        if (mChrome != null) mChrome.menu(anchor);
    }

    /** WT's "Rename tab": the title is cosmetic and remembered until the shell sets its own. */
    private void renameActiveTab() {
        if (mActive == null) return;
        final EditText input = new EditText(this);
        input.setText(mActive.title);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("탭 이름")
                .setView(input)
                .setPositiveButton("저장", (d, w) -> {
                    String t = input.getText().toString().trim();
                    if (!t.isEmpty()) {
                        mActive.title = t;
                        mActive.userTitled = true;
                        updateChip(mActive);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * The font importer is the one place the app still leaves for another activity.
     *
     * <p>A document picker cannot be replaced by anything in-process: the grant on the picked
     * file only exists on the returned intent, so the result has to come back here and be
     * handed to the sheet.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mSettings != null) mSettings.onActivityResult(requestCode, resultCode, data);
    }

    private void showSettings() {
        if (mSettings == null) mSettings = new SettingsSheet(this, new SettingsHost(this));
        mSettings.show();
    }

    /** Open settings on a specific page; the menu's profile block ends with this. */
    private void showSettings(String page) {
        if (mSettings == null) mSettings = new SettingsSheet(this, new SettingsHost(this));
        mSettings.show(page);
    }

    /** WT's find bar; also bound to Ctrl+Shift+F in the key handler. */
    private void find() {
        if (mFind == null) return;
        if (mFind.isOpen()) mFind.hide(); else mFind.show();
    }

    private void showAbout() {
        About.show(this, mTabs.size() + "개 세션 · " + mPrefs.host() + ":" + mPrefs.port());
    }

    // ---------------------------------------------------------------- keys
    //
    // The key path itself is KeyInput; what is left here is the forwarding the key bar, the
    // menu and the hardware handlers call. They are named after what the caller wants, so the
    // call sites did not have to learn a new object when the path moved.

    private void toggleKeyboard() { mKeys.toggleKeyboard(); }

    void showKeyboardSoon() { mKeys.showKeyboardSoon(); }

    private void sendRaw(String s) { mKeys.sendRaw(s); }

    private void sendEscape(String what) { mKeys.sendEscape(what); }

    private void sendFunctionKey(int number) { mKeys.sendFunctionKey(number); }

    private void clearSticky() { mKeys.clearSticky(); }

    void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("fterm", text));
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    // ---------------------------------------------------------------- lifecycle

    /** Android is short of memory: a background tab forgets its scrollback, its screen stays. */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        java.util.List<TerminalSession> background = new java.util.ArrayList<>();
        for (Tab tab : mTabs) {
            if (tab != mActive && tab.session != null) background.add(tab.session);
        }
        TabMemory.trim(background, level);
    }

    @Override
    protected void onPause() {
        if (mBackdrop != null) mBackdrop.pause();   // the changed-broadcast receiver
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // A ToneGenerator holds an audio track for its whole life. Without this, every
        // activity recreation leaves one behind until the process dies.
        if (mBell != null) mBell.release();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBackdrop != null) mBackdrop.resume();
        if (mShowKeyboardOnce && mPrefs.showKeyboardOnStart()) {
            mShowKeyboardOnce = false;
            showKeyboardSoon();
        }
    }

    /**
     * With {@code enableOnBackInvokedCallback="true"} in the manifest, API 33+ calls the
     * OnBackInvokedCallback instead of {@link #onBackPressed()}. Without this, the
     * "Back acts as ESC" setting silently stops working on recent Android.
     */
    private void registerBackHandler() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                new android.window.OnBackInvokedCallback() {
                    @Override
                    public void onBackInvoked() {
                        if (mPrefs.backIsEscape() && mActive != null && mActive.session != null) {
                            sendEscape("esc");
                        } else {
                            finish();
                        }
                    }
                });
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // An open find bar is the innermost thing on screen, so Back closes it first - the
        // same order as every other overlay on the phone.
        if (mFind != null && mFind.isOpen()) {
            mFind.hide();
            return;
        }
        if (mPrefs.backIsEscape() && mActive != null && mActive.session != null) {
            sendEscape("esc");
            return;
        }
        super.onBackPressed();
    }

    // ---------------------------------------------------------------- TerminalViewClient

    @Override
    public float onScale(float scale) {
        mLook.setZoom(FontZoom.next(mZoomDp, scale));
        return 1f;
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        if (mTermView.getCurrentSession() != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(mTermView, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return mPrefs.backIsEscape();
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return mPrefs.enforceCharBasedInput();
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return mPrefs.ctrlSpaceWorkaround();
    }

    @Override
    public boolean isTerminalViewSelected() {
        return true;
    }

    @Override
    public void copyModeChanged(boolean copyMode) {}

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) {
        // A finished shell keeps its tab on screen until Enter: pressing Enter is the
        // "press Enter to close" the exit notice asks for. The session is asked directly as
        // well as the tab, because a shell can die while its tab is in the background and
        // then no onSessionFinished() reaches this tab.
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            Tab t = mActive;
            if (t != null && (!t.running || (t.session != null && !t.session.isRunning()))) {
                closeTab(t);
                return true;
            }
        }

        // Ctrl+Shift+C / Ctrl+Shift+V on a hardware keyboard, like Windows Terminal.
        if (e.isCtrlPressed() && e.isShiftPressed()) {
            if (keyCode == KeyEvent.KEYCODE_C) {
                copySelection();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_V) {
                pasteClipboard();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_T) {
                newTab(true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_W) {
                if (mActive != null) confirmClose(mActive);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_P) {
                showCommandPalette();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        return false;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        return false;
    }

    @Override
    public boolean readControlKey() {
        return mKeyBar != null && mKeyBar.ctrl();
    }

    @Override
    public boolean readAltKey() {
        return mKeyBar != null && mKeyBar.alt();
    }

    @Override
    public boolean readShiftKey() {
        return mKeyBar != null && mKeyBar.shift();
    }

    @Override
    public boolean readFnKey() {
        return mKeys.fn();
    }

    @Override
    public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
        // Soft keyboards send Enter as text, which never reaches onKeyDown(). The terminal
        // view forwards it through onKeyDown() now, so this is only a backstop.
        if (codePoint == '\r' || codePoint == '\n') {
            Tab t = mActive;
            if (t != null && (!t.running || (t.session != null && !t.session.isRunning()))) {
                closeTab(t);
                return true;
            }
        }

        // The FN layer is a latch on one following key; KeyInput releases it either way.
        int fnCode = mKeys.fnKey(codePoint);
        if (fnCode != -1) {
            mTermView.handleKeyCode(fnCode, 0);
            return true;
        }

        // A sticky modifier applies to exactly one following character. The IME path
        // reaches the terminal through inputCodePoint() -> readControlKey(), and nothing
        // there releases the latch, so "abc" with CTRL still held arrived as ^B ^C ^H.
        // This callback is the last point that sees one code point at a time.
        if (mKeyBar != null && (mKeyBar.ctrl() || mKeyBar.alt() || mKeyBar.shift())) clearSticky();
        return false;
    }

    @Override
    public void onEmulatorSet() {
        if (mActive != null && mActive.session != null) {
            applySchemeTo(mActive.session);
        }
    }

    private void copySelection() {
        String sel = getSelectedText();
        if (sel == null || sel.isEmpty()) {
            Toast.makeText(this, "선택된 텍스트가 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }
        copyToClipboard(sel);
        Toast.makeText(this, "복사됨", Toast.LENGTH_SHORT).show();
    }

    private String getSelectedText() {
        if (mTermView.getCurrentSession() == null || mTermView.mEmulator == null) return null;
        return mTermView.getStoredSelectedText();
    }

    private void pasteClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return;
        ClipData clip = cm.getPrimaryClip();
        ClipData.Item item = clip.getItemAt(0);
        if (item == null) return;

        // An image clip has no text at all. Save it and paste the PATH: opencode resolves a
        // local filepath into a data: URI attachment, which is how a screenshot gets in.
        // Saving happens on the worker: decoding a screenshot on the UI thread freezes the
        // terminal while it runs.
        if (ClipboardImage.pasteIfImage(this, mUi, clip, new ClipboardImage.Paste() {
            @Override public boolean ready() { return mActive != null && mActive.session != null; }
            @Override public void insert(String path) {
                pasteText(path);
                Toast.makeText(MainActivity.this, "이미지 경로 붙여넣음: " + path,
                        Toast.LENGTH_LONG).show();
            }
        })) {
            return;
        }

        CharSequence text = item.coerceToText(this);
        if (text == null || text.length() == 0) {
            // A clip that is neither text nor a detected image: tell the user instead of
            // doing nothing, which read as "image paste is broken".
            Toast.makeText(this, "붙여넣을 텍스트나 이미지가 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }
        pasteText(text.toString());
    }

    /** Send text through the emulator as a paste (bracketed when the program asked). */
    private void pasteText(String text) {
        // Through the emulator, not the session: Emulator.paste() strips escapes, turns
        // newlines into CR, and - the part that matters here - wraps the text in
        // \e[200~ .. \e[201~ when the running program asked for bracketed paste
        // (DECSET 2004). A multi-line paste sent raw looks like a series of Return presses,
        // which is why "paste five lines" arrived as five separate submissions instead of
        // one "[5 lines pasted]" block.
        TerminalEmulator emulator = mActive == null || mActive.session == null
                ? null : mActive.session.getEmulator();
        if (emulator != null) {
            emulator.paste(text);
        } else if (mActive != null && mActive.session != null) {
            mActive.session.write(text);
        }
    }


    // logging, routed nowhere in release
    @Override public void logError(String t, String m) { android.util.Log.e(t, m); }
    @Override public void logWarn(String t, String m) { android.util.Log.w(t, m); }
    @Override public void logInfo(String t, String m) { android.util.Log.i(t, m); }
    @Override public void logDebug(String t, String m) {}
    @Override public void logVerbose(String t, String m) {}
    @Override public void logStackTraceWithMessage(String t, String m, Exception e) { android.util.Log.e(t, m, e); }
    @Override public void logStackTrace(String t, Exception e) { android.util.Log.e(t, "", e); }

    // ---------------------------------------------------------------- TerminalSessionClient

    @Override
    public void onTextChanged(TerminalSession changedSession) {
        if (changedSession == (mActive == null ? null : mActive.session)) {
            mTermView.onScreenUpdated();
        }
    }

    @Override
    public void onTitleChanged(TerminalSession changedSession) {
        for (Tab t : mTabs) {
            if (t.session == changedSession) {
                if (t.userTitled) return;
                String title = changedSession.getTitle();
                t.title = (title == null || title.trim().isEmpty())
                        ? Profiles.DEFAULT_NAME : title.trim();
                updateChip(t);
            }
        }
    }

    @Override
    public void onSessionFinished(TerminalSession finishedSession) {
        mUi.post(() -> {
            for (Tab t : mTabs) {
                if (t.session == finishedSession) {
                    // The tab stays until Enter or the tab is closed, so the last output and
                    // the exit line remain readable - the same bargain a terminal window makes.
                    t.running = false;
                }
            }
            Toast.makeText(this, "셸이 종료되었습니다 — Enter로 탭을 닫습니다", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onCommitImage(final android.net.Uri uri, String description,
                              final Runnable afterRead) {
        // The IME's tap. The client is asked because the read needs the activity's content
        // resolver and the result belongs in the active tab's prompt, not in this view.
        ClipboardImage.saveAsync(this, uri, mUi, new ClipboardImage.Paste() {
            @Override public boolean ready() {
                return mActive != null && mActive.session != null;
            }
            @Override public void insert(String path) {
                pasteText(path);
                Toast.makeText(MainActivity.this, "이미지 첨부: " + path, Toast.LENGTH_SHORT).show();
            }
        }, afterRead);
    }

    @Override
    public void onCopyTextToClipboard(TerminalSession session, String text) {
        copyToClipboard(text);
    }

    @Override
    public void onPasteTextFromClipboard(TerminalSession session) {
        pasteClipboard();
    }

    /**
     * OSC 52 read: the program in the terminal asked for the clipboard (opencode's TUI
     * pastes this way). Returning the text is what makes its paste work; the alternative
     * is a paste that silently does nothing.
     */
    @Override
    public String getClipboardText(TerminalSession session) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return null;
        ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
        if (item == null) return null;
        CharSequence text = item.coerceToText(this);
        return text == null ? null : text.toString();
    }

    /**
     * OSC 52 image read: opentui asks with preferredTypes ["image/png", "text/plain"], so
     * an image clipboard must answer with PNG bytes or the paste silently fails. This is
     * the only path a screenshot has into opencode - the bracketed-paste channel is text
     * only.
     */
    @Override
    public byte[] getClipboardImage(TerminalSession session) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return null;
        ClipData clip = cm.getPrimaryClip();
        if (clip.getItemAt(0) == null) return null;
        if (!ClipboardImage.isImage(this, clip)) return null;
        android.net.Uri uri = ClipboardImage.uriOf(clip);
        try {
            android.graphics.Bitmap bmp =
                    android.graphics.BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
            if (bmp == null) return null;
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bo);
            return bo.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void onNotification(TerminalSession session, String title, String body) {
        // Native: the escape sequence was parsed by the terminal, so there is no helper
        // process and no termux-api dependency. A background tab is allowed to notify - that
        // is the point (a build finished while you were reading another tab).
        if (mNotifier == null) mNotifier = new Notifier(this);
        mNotifier.post(title, body);
    }

    @Override
    public void onBell(TerminalSession session) {
        // Ring only for the tab the user is looking at: a background build finishing is not
        // worth a buzz if another tab is on screen.
        if (mActive == null || mActive.session != session) return;
        if (mBell == null) mBell = new Bell(this);
        mBell.ring(mPrefs.bellVibrate(), mPrefs.bellSound());
    }

    @Override
    public void onColorsChanged(TerminalSession session) {
        if (session == (mActive == null ? null : mActive.session)) mTermView.invalidate();
    }

    @Override
    public void onTerminalCursorStateChange(boolean state) {}

    @Override
    public void setTerminalShellPid(TerminalSession session, int pid) {}

    @Override
    public Integer getTerminalCursorStyle() {
        int s = mPrefs.cursorStyle();
        // The emulator validates against its own list, but an out-of-range int from a prefs
        // file is cheap to reject here and the fallback is the shape everyone expects.
        return s == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE
                || s == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR
                ? s : TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
    }
}
