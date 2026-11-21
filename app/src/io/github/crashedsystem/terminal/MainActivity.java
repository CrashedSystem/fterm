package io.github.crashedsystem.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.terminal.SocketTransport;
import com.termux.terminal.DaemonClient;
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

    Prefs mPrefs;
    private LinearLayout mRoot;
    private LinearLayout mTopBar;
    private LinearLayout mTabStrip;
    private HorizontalScrollView mTabScroll;
    private FrameLayout mTermFrame;
    private FindBar mFind;
    KeyBar mKeyBar;
    TerminalView mTermView;

    final List<Tab> mTabs = new ArrayList<>();
    private Tab mActive;

    /** Font size in dp; the view wants px, so {@link #fontPx()} converts. */
    int mFontDp;
    /** Iosevka / Nerd Symbols / Pretendard, with the Hangul size-adjust derived per size. */
    private Fonts mFonts;
    /** The scanline overlay, or null when the effect is off. */
    private RetroOverlay mRetro;
    /** Loaded once; null when the user has no picture, in which case the scheme shows alone. */
    BackgroundLayer mBackdrop;
    private Bell mBell;
    private SettingsSheet mSettings;
    /** Windows Terminal chrome: the command palette and the icon strip. */
    private Chrome mChrome;
    private Notifier mNotifier;
    private DaemonBoot mBoot;
    int mScheme;
    /** Every saved custom scheme, in list order; the source of the scheme index range. */
    CustomSchemes mCustoms;
    /** The active scheme's 19 editable slots, kept in step with Prefs and mCustoms. */
    int[] mCustom;
    /** Fractional font size in dp, so a pinch accumulates instead of rounding every event away. */
    private float mZoomDp;

    private boolean mFn;
    private boolean mShowKeyboardOnce = true;
    /** Hardware keys, handled before the focused view sees them. */
    private Shortcuts mShortcuts;
    /** The density the dp-derived px were built at; a change means they have to be redone. */
    private int mDensityDpi;

    private final Handler mUi = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Must run before any UI is built: every label in this app goes through
        // AppStrings.s(), which returns "" until it holds a Context.
        AppStrings.init(this);
        mPrefs = new Prefs(this);
        mDensityDpi = getResources().getConfiguration().densityDpi;
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
        mBoot.splash(AppStrings.s(R.string.splash_connecting));
        startSessions();
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

        // Tab strip + split button, in TabBar: the row is its own layout, and this method
        // was over the line budget the structure test enforces.
        TabBar.Parts bar = TabBar.build(this, () -> newTab(true), this::showCommandMenu);
        mTopBar = bar.top;
        mTabStrip = bar.strip;
        mTabScroll = bar.scroll;

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
        // Right-click on a tablet or in DeX: the view hands the event over, the menu is ours.
        TerminalMenu.attach(this, mTermView);

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

        mRoot.addView(mTopBar);
        mFind = new FindBar(this, mTermView);
        mRoot.addView(mFind.view());
        mRoot.addView(mTermFrame);
        mRoot.addView(mKeyBar.view());
        setContentView(mRoot);
        EdgeToEdge.applyInsets(mRoot, mTopBar, mKeyBar.view(), mTermFrame, 38, 88);

        mChrome = new Chrome(this, new Chrome.Commands() {
            @Override public void newTab() { MainActivity.this.newTab(true); }
            @Override public void closeTab() { if (mActive != null) confirmClose(mActive); }
            @Override public void renameTab() { renameActiveTab(); }
            @Override public void nextTab() { cycleTab(1); }
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
        applyKeyBarVisibility();
        mShortcuts = new Shortcuts(this);
    }

    // ---------------------------------------------------------------- sessions

    /**
     * Bring the terminal up.
     *
     * The daemon owns the shells, so the first question is "what is still alive?". A shell
     * left over from a previous launch keeps its screen and its running process; only when
     * the daemon has none do we open a fresh one. That is the whole point of keeping the
     * PTY outside the app: closing the app must not end a build.
     */
    private void startSessions() {
        final String token = mPrefs.token();
        if (token.isEmpty()) {
            // First run (or after a reinstall): start the daemon ourselves and read the token
            // it publishes, instead of telling the user to go and run two commands by hand.
            beginProvisioning();
            return;
        }

        new Thread(() -> {
            List<Integer> live = null;
            String failure = null;
            try {
                live = DaemonClient.liveSessions(mPrefs.host(), mPrefs.port(), token, 2500);
            } catch (DaemonClient.Failure f) {
                failure = f.getMessage();
            }
            final List<Integer> ids = live;
            final String err = failure;
            mUi.post(() -> {
                if (err != null) {
                    // The token exists but the daemon did not answer. This is the
                    // "swiped the app, came back, nothing works" hole: provisioning (which
                    // would ask the keeper to revive the daemon) only runs when the token is
                    // MISSING, so a stale token meant the app sat on an error dialog forever.
                    // Re-provisioning is safe here - the keeper publishes the same token
                    // unless the daemon regenerated it, and if it did, the new token lands
                    // and the retry below connects.
                    beginProvisioning();
                    return;
                }
                for (int id : ids) {
                    Tab tab = new Tab();
                    tab.id = id;
                    mTabs.add(tab);
                    addTabChip(tab);
                    openSession(tab, false);
                }
                if (mTabs.isEmpty()) {
                    newTab(true);
                } else {
                    activate(mTabs.get(0));
                    showKeyboardSoon();
                }
            });
        }, "fterm-start").start();
    }

    /**
     * Ask {@link DaemonBoot} for the daemon and act on how it ends: a token opens a tab, and
     * every other ending is a dialog naming the screen that fixes it.
     */
    private void beginProvisioning() {
        mBoot.start(new DaemonBoot.Host() {
            @Override public void ready(String token) {
                mPrefs.setToken(token);
                mBoot.restore();
                newTab(true);
            }

            @Override public void keeperMissing() {
                mUi.post(() -> DaemonDialogs.keeperMissing(MainActivity.this,
                        MainActivity.this::openManualTokenDialog));
            }

            @Override public void daemonSlow() {
                mUi.post(() -> DaemonDialogs.daemonSlow(MainActivity.this,
                        MainActivity.this::beginProvisioning,
                        MainActivity.this::openManualTokenDialog));
            }

            @Override public void storageBlocked() {
                mUi.post(() -> DaemonDialogs.storageBlocked(MainActivity.this,
                        MainActivity.this::openManualTokenDialog));
            }
        });
    }

    /** Last resort: let the user paste a token they obtained themselves. */
    private void openManualTokenDialog() {
        Tab tab = new Tab();
        tab.id = 1;
        mTabs.add(tab);
        addTabChip(tab);
        activate(tab);
        requestToken(tab);
    }

    private void newTab(boolean showKeyboard) {
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
        openSession(tab, showKeyboard);
    }

    /** Session ids are the daemon's handle; keep them small and unique in this tab set. */
    private int nextSessionId() {
        int max = 0;
        for (Tab t : mTabs) max = Math.max(max, t.id);
        return Math.max(1, max + 1);
    }

    private void openSession(Tab tab, boolean showKeyboard) {
        String token = mPrefs.token();
        if (token.isEmpty()) {
            requestToken(tab);
            return;
        }

        Profiles.Profile profile = tab.profile;
        String shell = profile != null && !profile.shell.isEmpty()
                ? profile.shell : mPrefs.startShell();
        String dir = profile != null && !profile.dir.isEmpty()
                ? profile.dir : mPrefs.startDir();

        TerminalSession session = new TerminalSession("", null, null, null, 8000, this);
        SocketTransport transport = new SocketTransport(
                mPrefs.host(), mPrefs.port(), token, tab.id, shell, dir);
        session.setTransport(transport);
        tab.session = session;
        tab.transport = transport;
        tab.running = true;
        tab.pendingCommand = profile != null && Profiles.commandIsOneLine(profile.command)
                && !profile.command.isEmpty() ? profile.command : null;
        session.mSessionName = profile != null && !profile.name.isEmpty()
                ? profile.name : Profiles.DEFAULT_NAME + " " + tab.id;

        if (mActive == tab) {
            mBoot.restore();   // remove any "connecting…" splash still in the frame
            mTermView.attachSession(session);
            mTermView.setTextSize(fontPx());
            applySchemeTo(session);
            mTermView.requestFocus();
        }

        if (showKeyboard) {
            // The transport connects on its own thread; the keyboard follows the window,
            // not the socket. Showing it immediately is right: the shell is one round trip away.
            showKeyboardSoon();
        }
        // A profile's command has to wait for the shell either way - a fresh session has not
        // printed its prompt yet, and a reattached one replays its ring asynchronously. The
        // poll is what tells those apart, so it runs in both cases.
        pollForPrompt(tab);
    }

    /**
     * Type the profile's command into the shell that is now running.
     *
     * <p>Sent as bytes to the PTY, not executed by the daemon: an ssh profile has to inherit
     * the login shell's environment to find its keys and its config, and the daemon's
     * allowlist stays the only thing that decides what binary a session may run.
     */
    private void runPendingCommand(Tab tab) {
        String command = tab.pendingCommand;
        if (command == null) return;
        tab.pendingCommand = null;
        TerminalSession s = tab.session;
        if (s == null) return;
        // A short pause so the shell has finished printing its prompt; a command written
        // mid-prompt is not lost by bash, but it reads as if the user typed over it.
        mUi.postDelayed(() -> {
            if (tab.session != null && tab.running) tab.session.write(command + "\r");
        }, 120);
    }

    private void pollForPrompt(Tab tab) {
        final long deadline = System.currentTimeMillis() + 4000;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                TerminalSession s = tab.session;
                boolean ready = s != null && s.getDeliveredBytes() > 0;
                if (ready) {
                    // Clear whatever the reattach left typed, so the shell prompt is not
                    // prefixed with a stale line.
                    if (mActive == tab) showKeyboardSoon();
                    runPendingCommand(tab);
                    return;
                }
                if (System.currentTimeMillis() < deadline) mUi.postDelayed(this, 120);
            }
        };
        mUi.postDelayed(r, 120);
    }

    /**
     * First run: there is no token. Ask once by hand - one command in Termux, then the token -
     * for the case where the keeper is not there to answer the request file.
     */
    private void requestToken(Tab tab) {
        mUi.post(() -> DaemonDialogs.manualToken(this, token -> {
            if (token == null || token.isEmpty()) {
                finish();
                return;
            }
            mPrefs.setToken(token);
            openSession(tab, true);
        }));
    }

    private void addTabChip(Tab tab) {
        TabStrip.Chip chip = new TabStrip(this, tabIconFor(tab)).create(tab.title,
                () -> activate(tab), () -> confirmClose(tab), () -> showTabMenu(tab));
        tab.plate = chip.plate;
        tab.chip = chip.title;
        tab.closeBox = chip.close;
        tab.chipView = chip;
        mTabStrip.addView(chip.plate);
        updateChip(tab);
    }

    /**
     * The tab's own menu: what you get by holding the tab instead of hitting its X.
     *
     * <p>Rename and colour are per tab and cosmetic; duplicate and move change the tab set.
     * Close is repeated here so the menu is the one place that lists the tab's whole
     * vocabulary - the X stays the fast path, not the only path.
     *
     * <p>Move entries are only listed when there is somewhere to move to. A greyed-out row
     * is a row the user has to read and reject; a list that only offers what is possible is
     * shorter and never lies.
     */
    private void showTabMenu(Tab tab) {
        if (mChrome == null) return;
        Chrome.Row[] rows = TabMenu.rowsFor(mTabs.indexOf(tab), mTabs.size(),
                new TabMenu.Actions() {
                    @Override public void rename() { renameTab(tab); }
                    @Override public void chooseColor() {
                        TabMenu.chooseColor(MainActivity.this, tab, () -> updateChip(tab));
                    }
                    @Override public void duplicate() {
                        // A duplicate is a new session with the same title and colour; two
                        // views of one PTY would fight over the cursor and the window size.
                        newTab(tab.profile, true);
                        Tab fresh = mActive;
                        if (fresh != null && fresh != tab) {
                            fresh.title = tab.title;
                            fresh.userTitled = tab.userTitled;
                            fresh.color = tab.color;
                            updateChip(fresh);
                        }
                    }
                    @Override public void move(int delta) {
                        TabMenu.move(mTabs, mTabStrip, tab, delta,
                                () -> { for (Tab t : mTabs) updateChip(t); });
                    }
                    @Override public void close() { confirmClose(tab); }
                });
        mChrome.tabMenu(tab.title, rows);
    }

    /** Rename one tab, not necessarily the active one. */
    private void renameTab(Tab tab) {
        Chrome.renameTab(this, tab.title, name -> {
            if (name.isEmpty()) return;
            tab.title = name;
            tab.userTitled = true;
            updateChip(tab);
        });
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
                Schemes.isOled(mScheme, mCustom), tab.color);
    }

    private void activate(Tab tab) {
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
                .setMessage(AppStrings.s(R.string.close_tab_message))
                .setPositiveButton(AppStrings.s(R.string.close_tab_confirm), (d, w) -> closeTab(tab))
                .setNegativeButton(AppStrings.s(R.string.i18n_0058), null)
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

    /**
     * Load mterm's font stack.
     *
     * The stack is Iosevka for Latin, a Nerd symbols face for icons, and Pretendard for
     * Hangul, with the Hangul face size-adjusted so one syllable is two Latin cells. mterm
     * writes that as a `font-family` list plus `size-adjust: 115.85%`; here it becomes a
     * {@link TerminalRenderer.FaceSelector} because Android holds one face per Paint, and
     * the adjustment is recomputed from the measured cell so it stays correct at any size.
     */
    private void loadFonts() {
        int px = fontPx();
        float cell = mTermView.getFontWidth();
        if (cell <= 0f) {
            // Not laid out yet: measure the Latin face at the requested size directly.
            android.graphics.Paint probe = new android.graphics.Paint();
            probe.setTypeface(Typeface.MONOSPACE);
            probe.setTextSize(px);
            cell = probe.measureText("M");
        }
        mFonts = Fonts.load(this, cell, px, mPrefs.fontFamily(), mPrefs.hangulFamily(),
                mPrefs.cjkFamily());
        mTermView.setFaceSelector(new TerminalRenderer.FaceSelector() {
            @Override public Typeface typefaceFor(int cp) { return mFonts.typefaceFor(cp); }
            @Override public float scaleFor(int cp) { return mFonts.scaleFor(cp); }
            @Override public float shiftFor(int cp) {
                // Read the size live: a pinch changes it without reloading the faces, and the
                // shift is a fraction of the em, so a stale px would drift with every zoom.
                if (mFonts.isHangul(cp)) return mFonts.hangulShift(fontPx());
                if (Fonts.isCjk(cp)) return mFonts.cjkShift(fontPx());
                return 0f;
            }
        });
        mTermView.setTypeface(mFonts.latin());
    }

    /** dp -> px at this device's density. */
    private int fontPx() {
        return Math.max(8, Math.round(mFontDp * getResources().getDisplayMetrics().density));
    }

    void applyFontSize(int dp) {
        setZoom(FontZoom.dp(dp));
    }

    /** Set the size from a fractional dp value; the fraction must survive, see FontZoom. */
    private void setZoom(float dp) {
        mZoomDp = Math.max(FontZoom.MIN_DP, Math.min(FontZoom.MAX_DP, dp));
        mFontDp = FontZoom.dp(mZoomDp);
        mPrefs.setFontSizeDp(mFontDp);
        if (mTermView.getCurrentSession() != null) mTermView.setTextSize(fontPx());
    }

    /** Switch the Latin face; loadFonts() recomputes the cell width the face implies. */
    void applyFontFamily(String id) {
        mPrefs.setFontFamily(id);
        loadFonts();
    }

    /** Switch the Korean face; it does not change the cell, so the grid is untouched. */
    void applyHangulFamily(String id) {
        mPrefs.setHangulFamily(id);
        loadFonts();
    }

    /** Switch the CJK face; like the Korean one it never changes the cell. */
    void applyCjkFamily(String id) {
        mPrefs.setCjkFamily(id);
        loadFonts();
    }

    /**
     * Install the cursor's shape and blink rate.
     *
     * <p>The shape lives in the emulator (which is also where DECSCUSR writes it), so a
     * running session has to be told; the rate lives in the view. Zero disables blinking,
     * which is the view's own encoding for "off" - not a special case invented here.
     */
    void applyCursor() {
        int rate = mPrefs.cursorBlink() ? mPrefs.cursorBlinkMs() : 0;
        mTermView.setTerminalCursorBlinkerRate(rate);
        mTermView.setTerminalCursorBlinkerState(true, true);
        if (mActive != null && mActive.session != null) {
            TerminalEmulator em = mActive.session.getEmulator();
            if (em != null) em.setCursorStyle();
        }
    }

    /** Install or remove the scanline pass; the overlay caches per size on its own. */
    void applyRetro() {
        mRetro = mPrefs.retroEffect() ? new RetroOverlay() : null;
        mTermView.setRetroOverlay(mRetro);
    }

    void applyScheme(int index) {
        mScheme = index;
        mPrefs.setScheme(index);
        // A custom index names one entry of the list, so the editable slots follow it here.
        mCustom = mCustoms.slotsForScheme(index);
        // On the OLED scheme every surface goes to true black; a #1e1e1e strip next to a
        // #000000 terminal is a visible seam and, on OLED, lit pixels.
        final boolean oled = Schemes.isOled(index, mCustom);
        final int chrome = oled ? 0xff000000 : 0xff1e1e1e;
        final int bar = oled ? 0xff0a0a0a : 0xff2b2b2b;
        final int keys = oled ? 0xff0d0d0d : 0xff252526;
        mRoot.setBackgroundColor(chrome);
        if (mTopBar != null) mTopBar.setBackgroundColor(bar);
        if (mTabStrip != null) mTabStrip.setBackgroundColor(bar);
        if (mKeyBar != null) mKeyBar.applyTheme(oled);
        if (mTabScroll != null) mTabScroll.setBackgroundColor(bar);
        if (mTermFrame != null) mTermFrame.setBackgroundColor(chrome);
        if (mActive != null && mActive.session != null) applySchemeTo(mActive.session);
    }

    private void applySchemeTo(TerminalSession session) {
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
    /** Move to another tab by delta, wrapping both ways; the keyboard's Ctrl+Tab. */
    void cycleTab(int delta) {
        if (mTabs.size() < 2) return;
        int n = mTabs.size();
        int i = mTabs.indexOf(mActive);
        activate(mTabs.get(((i + delta) % n + n) % n));
    }

    /** The nth tab, counting from zero (Ctrl+1..9); out of range is a no-op. */
    void selectTab(int index) {
        if (index >= 0 && index < mTabs.size()) activate(mTabs.get(index));
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
        Chrome.renameTab(this, mActive.title, name -> {
            if (name.isEmpty()) return;
            mActive.title = name;
            mActive.userTitled = true;
            updateChip(mActive);
        });
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

    void showSettings() {
        if (mSettings == null) mSettings = new SettingsSheet(this, new SettingsHost(this));
        mSettings.show();
    }

    /** Open settings on a specific page; the menu's profile block ends with this. */
    private void showSettings(String page) {
        if (mSettings == null) mSettings = new SettingsSheet(this, new SettingsHost(this));
        mSettings.show(page);
    }

    /**
     * WT's find bar; also bound to Ctrl+Shift+F in the key handler. Pressing it again while
     * the bar is open puts the cursor back in the field rather than closing it.
     */
    void find() {
        if (mFind == null) return;
        if (mFind.isOpen()) mFind.focus(); else mFind.show();
    }

    private void showAbout() {
        About.show(this, mTabs.size() + AppStrings.s(R.string.about_status) + mPrefs.host() + ":" + mPrefs.port());
    }

    // ---------------------------------------------------------------- keys

    private void toggleKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
    }

    private void showKeyboardSoon() {
        // A keyboard that can type makes the IME optional, and on DeX it covers half the
        // window while the user is looking at a shell they can already type into. The key
        // bar's keyboard button still raises it for the times the on-screen one is wanted.
        if (HardwareKeyboard.present(this)) return;
        mUi.postDelayed(() -> {
            mTermView.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(mTermView, InputMethodManager.SHOW_IMPLICIT);
        }, 150);
    }

    private void sendRaw(String s) {
        if (mActive == null || mActive.session == null) return;
        mActive.session.write(s);
        clearSticky();
    }

    /** Flood guard for the key bar; see {@link KeyCodes#allow}. */
    private final KeyCodes mKeyCodes = new KeyCodes();

    private void sendEscape(String what) {
        TerminalSession s = mActive == null ? null : mActive.session;
        if (s == null || s.getEmulator() == null) return;
        int code = KeyCodes.codeFor(what);
        if (code < 0) return;
        if (!mKeyCodes.allow(what)) return;
        mTermView.handleKeyCode(code, 0);
        clearSticky();
    }

    /**
     * F1..F12 from the key bar's function layer.
     *
     * <p>Routed through {@link TerminalView#handleKeyCode} like every other special key, which
     * is what makes the emulator translate it with the running program's terminfo: a TUI that
     * asked for {@code kf1=\eOP} gets that, and a shell that expects {@code \e[11~} gets that.
     * Sending a hardcoded sequence was the reason F-keys used to break inside vim.
     *
     * <p>No flood guard runs here. These cells repeat while held, and the point of a repeat is
     * a stream of identical codes; the guard's 40 ms floor would swallow every other one.
     */
    private void sendFunctionKey(int number) {
        if (number < 1 || number > 12) return;
        if (mActive == null || mActive.session == null) return;
        mTermView.handleKeyCode(KeyEvent.KEYCODE_F1 + (number - 1), 0);
        clearSticky();
    }

    private void clearSticky() {        mFn = false;
        if (mKeyBar != null) mKeyBar.clear();
    }

    private void copyToClipboard(String text) {
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
        mBoot.leftApp();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // Anything queued on mUi belongs to this activity: a daemon reply, a retry, a keyboard
        // request. Running one after onDestroy is how a dialog ended up on a dead window and
        // threw BadTokenException - the crash behind "the app keeps stopping". Dropping the
        // queue here is cheaper than guarding every callback.
        mUi.removeCallbacksAndMessages(null);
        // A ToneGenerator holds an audio track for its whole life. Without this, every
        // activity recreation leaves one behind until the process dies.
        if (mBell != null) mBell.release();
        super.onDestroy();
    }

    /**
     * The configuration changed without the activity being recreated, which is the deal the
     * manifest's {@code configChanges} makes - a terminal must not lose its sessions to a
     * rotation - so everything derived from the configuration is re-derived here.
     */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (mSettings != null) mSettings.relayout();
        if (mChrome != null) mChrome.relayout();
        applyKeyBarVisibility();
        // DeX on an external monitor changes the density, and every dp in this layout was
        // multiplied out to px once, in buildUi.
        if (config.densityDpi != mDensityDpi) {
            mDensityDpi = config.densityDpi;
            mTermView.setTextSize(fontPx());
            loadFonts();
        }
        // The insets listener re-derives both bar heights from their dp bases on every pass,
        // so asking for the insets again is what refreshes them.
        mRoot.requestApplyInsets();
    }

    /**
     * Whether the key bar belongs on screen right now.
     *
     * <p>"키 바 표시" is the user's switch, and off means off. On top of it, a keyboard that
     * can type hides a bar holding the keys that keyboard already has; the 모양 page's
     * auto-hide row is what lets a tablet that is sometimes a tablet keep the bar.
     */
    void applyKeyBarVisibility() {
        if (mKeyBar == null) return;
        mKeyBar.setVisible(mPrefs.keyBarVisible()
                && !(mPrefs.keyBarAutoHide() && HardwareKeyboard.present(this)));
        // The inset that the bar is not using moves to the terminal; ask for them again so
        // the move happens now rather than at the next rotation.
        if (mRoot != null) mRoot.requestApplyInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBackdrop != null) mBackdrop.resume();
        // Back from the all-files screen or from Termux: try again by itself. DaemonBoot hands
        // out one retry per resume; a tab that already exists is the user's answer already.
        if (mBoot.retryOnResume(mTabs.isEmpty())) startSessions();
        if (mShowKeyboardOnce && mPrefs.showKeyboardOnStart() && !HardwareKeyboard.present(this)) {
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
                this::handleBack);
    }

    /**
     * Back, from either path (the API 33+ callback or {@link #onBackPressed()}).
     *
     * <p>The order is the point: the find bar is the innermost thing on screen, so Back closes
     * it first. The API 33+ callback was written without that check, so on a current device
     * Back closed the whole app while the find bar was open - the two paths disagreed.
     */
    private void handleBack() {
        if (mFind != null && mFind.isOpen()) {
            mFind.hide();
            return;
        }
        if (mPrefs.backIsEscape() && mActive != null && mActive.session != null) {
            sendEscape("esc");
            return;
        }
        finish();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        handleBack();
    }

    /**
     * Hardware keys before the focused view sees them.
     *
     * <p>The terminal was the only listener, so every shortcut stopped working the moment
     * focus moved to the find field. Dispatching here makes the bindings a property of the
     * window, which is what a keyboard user expects on a tablet or in DeX.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (mShortcuts != null && mShortcuts.handle(e)) return true;
        return super.dispatchKeyEvent(e);
    }

    // ---------------------------------------------------------------- TerminalViewClient

    @Override
    public float onScale(float scale) {
        setZoom(FontZoom.next(mZoomDp, scale));
        return 1f;
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        if (mTermView.getCurrentSession() == null) return;
        // A click is not a request for the keyboard. Tapping the terminal to give it focus in
        // DeX used to raise the IME over the pane, which is the opposite of what a mouse user
        // wants; and with a hardware keyboard attached the IME is redundant either way.
        if (e.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) return;
        if (HardwareKeyboard.present(this)) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(mTermView, InputMethodManager.SHOW_IMPLICIT);
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
        return mFn;
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

        if (mFn) {
            final int lower = Character.toLowerCase(codePoint);
            int key = -1;
            switch (lower) {
                case 'w': key = KeyEvent.KEYCODE_DPAD_UP; break;
                case 'a': key = KeyEvent.KEYCODE_DPAD_LEFT; break;
                case 's': key = KeyEvent.KEYCODE_DPAD_DOWN; break;
                case 'd': key = KeyEvent.KEYCODE_DPAD_RIGHT; break;
                case 'p': key = KeyEvent.KEYCODE_PAGE_UP; break;
                case 'n': key = KeyEvent.KEYCODE_PAGE_DOWN; break;
                case 't': key = KeyEvent.KEYCODE_TAB; break;
                case 'h': key = KeyEvent.KEYCODE_MOVE_HOME; break;
                case 'e': key = KeyEvent.KEYCODE_MOVE_END; break;
                default: break;
            }
            mFn = false;
            if (key != -1) {
                mTermView.handleKeyCode(key, 0);
                return true;
            }
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

    void copySelection() {
        String sel = getSelectedText();
        if (sel == null || sel.isEmpty()) {
            Toast.makeText(this, AppStrings.s(R.string.copy_nothing_selected), Toast.LENGTH_SHORT).show();
            return;
        }
        copyToClipboard(sel);
        Toast.makeText(this, AppStrings.s(R.string.copy_done), Toast.LENGTH_SHORT).show();
    }

    private String getSelectedText() {
        if (mTermView.getCurrentSession() == null || mTermView.mEmulator == null) return null;
        // The live selection first - that is what the highlight on screen is - and the text the
        // selection toolbar stored before closing as the fallback (the old Ctrl+Shift+C path).
        String live = mTermView.getSelectedText();
        return live != null ? live : mTermView.getStoredSelectedText();
    }

    void pasteClipboard() {
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
                Toast.makeText(MainActivity.this, AppStrings.s(R.string.paste_image_path) + path,
                        Toast.LENGTH_LONG).show();
            }
        })) {
            return;
        }

        CharSequence text = item.coerceToText(this);
        if (text == null || text.length() == 0) {
            // A clip that is neither text nor a detected image: tell the user instead of
            // doing nothing, which read as "image paste is broken".
            Toast.makeText(this, AppStrings.s(R.string.paste_nothing), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, AppStrings.s(R.string.session_finished), Toast.LENGTH_SHORT).show();
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
                Toast.makeText(MainActivity.this, AppStrings.s(R.string.paste_image_attached) + path, Toast.LENGTH_SHORT).show();
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
