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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
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

    private static final String TAG = "fterm";

    private Prefs mPrefs;
    private LinearLayout mRoot;
    private LinearLayout mTopBar;
    private LinearLayout mTabStrip;
    private HorizontalScrollView mTabScroll;
    private FrameLayout mTermFrame;
    private KeyBar mKeyBar;
    private TerminalView mTermView;

    private final List<Tab> mTabs = new ArrayList<>();
    private Tab mActive;

    /** Font size in dp; the view wants px, so {@link #fontPx()} converts. */
    private int mFontDp;
    /** Iosevka / Nerd Symbols / Pretendard, with the Hangul size-adjust derived per size. */
    private Fonts mFonts;
    /** Loaded once; null when the asset is missing, in which case the theme is palette-only. */
    private MikuBackground mMiku;
    private Bell mBell;
    private SettingsSheet mSettings;
    private Notifier mNotifier;
    private int mScheme;
    private float mScale = 1f;

    private boolean mFn;
    private boolean mShowKeyboardOnce = true;

    private final Handler mUi = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefs = new Prefs(this);
        mFontDp = mPrefs.fontSizeDp();
        mScheme = mPrefs.scheme();

        applyChrome();
        buildUi();

        // A foreground service keeps this process out of Android's freezer. Measured cause of
        // the idle death: with fterm backgrounded, `dumpsys activity processes` reports
        // isFrozen=true, and a frozen process stops reading its socket.
        TerminalService.start(this);
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1002);
        }

        // Nothing is attached yet, so the frame would sit as a blank black area until the
        // live query returns (up to the 2.5s timeout). Say so instead of showing a void -
        // "the first shell is blank" was this empty frame, not a dead shell.
        showSplash("셸에 연결하는 중…");
        startSessions();
    }

    private void applyChrome() {
        Window w = getWindow();
        // From API 35 the bar colours are ignored and the app is drawn behind the bars, so
        // the layout insets itself instead. On 30+ the IME inset drives the bottom padding,
        // which makes ADJUST_RESIZE unnecessary (and it is a no-op there anyway).
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        EdgeToEdge.enable(this);
        if (Schemes.isOled(mScheme) && android.os.Build.VERSION.SDK_INT < 35) {
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

        TextView plus = new TextView(this);
        plus.setText("＋");
        plus.setTextSize(20);
        plus.setTextColor(0xffd0d0d0);
        plus.setGravity(Gravity.CENTER);
        plus.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(38)));
        plus.setOnClickListener(v -> newTab(true));

        // WT's tab bar: "+" to open a tab, and a caret beside it for the dropdown menu
        // (new tab, split, settings, rename, close). Long-press on "+" opens it too, which is
        // the only discoverable gesture on a bar this small.
        TextView menu = new TextView(this);
        menu.setText("\u25be");
        menu.setTextSize(13);
        menu.setTextColor(0xffb0b0b0);
        menu.setGravity(Gravity.CENTER);
        menu.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(38)));
        menu.setOnClickListener(v -> showCommandMenu(v));
        plus.setOnLongClickListener(v -> {
            showCommandMenu(v);
            return true;
        });

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.BOTTOM);
        top.setBackgroundColor(0xff2b2b2b);
        top.addView(mTabScroll);
        top.addView(plus);
        top.addView(menu);
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
        });
        mKeyBar.setKeys(mPrefs.keybarKeys());

        mRoot.addView(top);
        mRoot.addView(mTermFrame);
        mRoot.addView(mKeyBar.view());
        setContentView(mRoot);
        EdgeToEdge.applyInsets(mRoot, mTopBar, mKeyBar.view(), dp(38), dp(40));

        loadFonts();
        mMiku = MikuBackground.load(this);
        // IME tracing is how the Hangul path gets diagnosed on a real device; the flag is
        // off unless someone sets it in shared prefs.
        mTermView.setIsTerminalViewKeyLoggingEnabled(mPrefs.imeLog());
        registerBackHandler();
        applyFontSize(mFontDp);
        applyScheme(mScheme);
        mKeyBar.setVisible(mPrefs.keyBarVisible());
    }

    /**
     * Key bar buttons. ALT/CTRL/SHIFT/FN are sticky: they latch until the next key is sent,
     * which is how a phone keyboard has to work (there is no way to hold two keys at once
     * with one thumb).
     */
    // ---------------------------------------------------------------- sessions

    private static final class Tab {
        TerminalSession session;
        SocketTransport transport;
        TextView chip;
        LinearLayout plate;
        TextView closeBox;
        String title = "shell";
        /** Set once the user renames the tab; the shell's OSC title stops winning. */
        boolean userTitled;
        int id;
        boolean running;
    }

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
     * Bring the daemon up without the user leaving the app.
     *
     * Writes the request file the Termux-side keeper watches, then polls for the token it
     * publishes. If the keeper is not running (never installed, or Termux was killed) the
     * app says so once, with the single command that fixes it - instead of the old flow,
     * which made the user open Termux and copy a token by hand on first run.
     */
    private void beginProvisioning() {
        showSplash("Termux 데몬 준비 중…");
        DaemonLauncher.requestStart();
        pollForToken(0);
    }

    private void showSplash(String text) {
        int px = dp(24);
        TextView splash = new TextView(this);
        splash.setText(text);
        splash.setTextColor(0xffcfcfcf);
        splash.setTextSize(13);
        splash.setPadding(px, px, px, px);
        splash.setGravity(Gravity.CENTER);
        if (mTermFrame != null) {
            mTermFrame.removeAllViews();
            mTermFrame.addView(splash);
        }
    }

    private void pollForToken(int attempt) {
        final int MAX_ATTEMPTS = 15;              // ~4.5 s at 300 ms
        String token = DaemonLauncher.readToken();
        if (token != null) {
            mPrefs.setToken(token);
            restoreTerminalView();
            newTab(true);
            return;
        }
        if (attempt < MAX_ATTEMPTS) {
            if (attempt == 4) DaemonLauncher.requestStart();   // ask once more in case it raced
            mUi.postDelayed(() -> pollForToken(attempt + 1), 300);
            return;
        }
        restoreTerminalView();
        if (!DaemonLauncher.keeperAlive()) {
            showKeeperMissing();
        } else {
            showDaemonSlow();
        }
    }

    private void restoreTerminalView() {
        if (mTermFrame != null && mTermView.getParent() == null) {
            mTermFrame.removeAllViews();
            mTermFrame.addView(mTermView);
        }
    }

    private void showKeeperMissing() {
        mUi.post(() -> new AlertDialog.Builder(this)
                .setTitle("Termux 준비가 필요합니다")
                .setMessage(DaemonLauncher.keeperInstructions())
                .setPositiveButton("명령 복사", (d, w) -> {
                    copyToClipboard("cd ~/projects/fterm && bash termux/install.sh");
                    Toast.makeText(this, "복사됨 — Termux에 붙여넣고 실행하세요",
                            Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("닫기", (d, w) -> openManualTokenDialog())
                .show());
    }

    private void showDaemonSlow() {
        mUi.post(() -> new AlertDialog.Builder(this)
                .setTitle("데몬이 응답하지 않습니다")
                .setMessage("keeper는 살아있지만 데몬이 뜨지 않았습니다.\n\n"
                        + "Termux에서 확인:\n"
                        + "  ~/fterm/ftermd status\n"
                        + "  tail ~/fterm/daemon.log")
                .setPositiveButton("다시 시도", (d, w) -> beginProvisioning())
                .setNegativeButton("닫기", (d, w) -> openManualTokenDialog())
                .show());
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
        Tab tab = new Tab();
        tab.id = nextSessionId();
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

        TerminalSession session = new TerminalSession("", null, null, null, 8000, this);
        SocketTransport transport = new SocketTransport(
                mPrefs.host(), mPrefs.port(), token, tab.id);
        session.setTransport(transport);
        tab.session = session;
        tab.transport = transport;
        tab.running = true;
        session.mSessionName = "shell " + tab.id;

        if (mActive == tab) {
            restoreTerminalView();   // remove any "connecting…" splash still in the frame
            mTermView.attachSession(session);
            mTermView.setTextSize(fontPx());
            applySchemeTo(session);
            mTermView.requestFocus();
        }

        if (showKeyboard) {
            // The transport connects on its own thread; the keyboard follows the window,
            // not the socket. Showing it immediately is right: the shell is one round trip away.
            showKeyboardSoon();
        } else {
            // A reattached session gets its ring replay, but the prompt arrives asynchronously.
            // Poll the delivered byte count and show the keyboard once the screen has content.
            pollForPrompt(tab);
        }
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
                    return;
                }
                if (System.currentTimeMillis() < deadline) mUi.postDelayed(this, 120);
            }
        };
        mUi.postDelayed(r, 120);
    }

    /** The daemon is unreachable or refused us. Say what to run, in the app. */
    private void showDaemonHelp(String reason) {
        mUi.post(() -> new AlertDialog.Builder(this)
                .setTitle("Termux 데몬에 연결하지 못했습니다")
                .setMessage(reason + "\n\nTermux에서 한 번 실행하세요:\n\n"
                        + "  pkg install python\n"
                        + "  ~/fterm/ftermd start\n\n"
                        + "그다음 이 앱으로 돌아오세요.")
                .setPositiveButton("명령 복사", (d, w) -> {
                    copyToClipboard("~/fterm/ftermd start");
                    Toast.makeText(this, "복사됨 — Termux에 붙여넣고 실행하세요",
                            Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("닫기", null)
                .show());
    }

    /**
     * First run: there is no token. Kick off `ftermd token` inside Termux via
     * RUN_COMMAND and ask the user to paste it once. Everything after that is
     * remembered, so this happens exactly once per install.
     */
    private void requestToken(Tab tab) {
        mUi.post(() -> {
            final EditText input = new EditText(this);
            input.setHint("Termux에서 `ftermd token` 출력값");
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setTextColor(0xffe6e6e6);
            input.setHintTextColor(0xff888888);

            new AlertDialog.Builder(this)
                    .setTitle("Termux 연결")
                    .setMessage("Termux에서 다음을 한 번 실행하세요:\n\n"
                            + "  ~/fterm/ftermd start\n"
                            + "  ftermd token\n\n"
                            + "출력된 토큰을 붙여넣으세요.")
                    .setView(input)
                    .setCancelable(false)
                    .setPositiveButton("저장", (d, w) -> {
                        String t = input.getText().toString().trim();
                        if (t.isEmpty()) {
                            finish();
                            return;
                        }
                        mPrefs.setToken(t);
                        openSession(tab, true);
                    })
                    .setNegativeButton("Termux 명령 복사", (d, w) -> {
                        copyToClipboard("ftermd start && ftermd token");
                        Toast.makeText(this, "복사됨 — Termux에 붙여넣고 실행하세요",
                                Toast.LENGTH_LONG).show();
                        finish();
                    })
                    .show();
        });
    }

    /**
     * One tab, shaped like Windows Terminal's.
     *
     * WT's tab is a rounded-top plate whose fill matches the terminal it belongs to, so the
     * active tab reads as continuous with the pane below it and the inactive ones recede into
     * the bar. Inside it: a 16dp icon slot (the shell glyph), the title, and a close box that
     * only appears on the active tab - the same "the tab you are looking at is the one you can
     * close" rule WT uses, and the reason a stray X cannot kill a background build.
     */
    private void addTabChip(Tab tab) {
        LinearLayout plate = new LinearLayout(this);
        plate.setOrientation(LinearLayout.HORIZONTAL);
        plate.setGravity(Gravity.CENTER_VERTICAL);
        plate.setPadding(dp(8), 0, dp(4), 0);
        plate.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        plate.setOnClickListener(v -> activate(tab));
        plate.setOnLongClickListener(v -> {
            confirmClose(tab);
            return true;
        });

        TextView icon = new TextView(this);
        icon.setText(">_");
        icon.setTextSize(9);
        icon.setTextColor(0xff8a8a8a);
        icon.setTypeface(Typeface.MONOSPACE);
        icon.setGravity(Gravity.CENTER);
        icon.setIncludeFontPadding(false);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(34)));
        plate.addView(icon);

        TextView title = new TextView(this);
        title.setTextSize(12);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setMaxWidth(dp(150));
        // A TextView inside a taller row draws its text at the TOP unless told otherwise.
        // The tab title was doing exactly that - the row is 34dp and the text sat on its
        // upper edge, which reads as "글자가 위로 붙었다".
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setIncludeFontPadding(false);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        title.setPadding(dp(2), 0, dp(6), 0);
        plate.addView(title);

        TextView close = new TextView(this);
        close.setText("\u00d7");
        close.setTextSize(15);
        close.setGravity(Gravity.CENTER);
        close.setIncludeFontPadding(false);
        // Right next to the title, not at the far edge: the X belongs to the tab, and a wide
        // gap makes it look like it belongs to the next one.
        close.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(34)));
        close.setOnClickListener(v -> confirmClose(tab));
        plate.addView(close);

        tab.chip = title;
        tab.plate = plate;
        tab.closeBox = close;
        mTabStrip.addView(plate);
        updateChip(tab);
    }

    /**
     * Active tab: the terminal's own background, so the plate and the pane are one surface.
     * Inactive: the bar's colour, dimmed text, and no close box.
     */
    private void updateChip(Tab tab) {
        if (tab.chip == null || tab.plate == null) return;
        boolean active = tab == mActive;
        tab.chip.setText(tab.title);

        int[] palette = Schemes.colors(mScheme);
        int termBg = palette[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND];

        GradientDrawable d = new GradientDrawable();
        // Inactive tabs are a slightly lighter gray than the bar so they read as tabs; the
        // active one takes the terminal's own background and grows into the pane.
        d.setColor(active ? termBg : (Schemes.isOled(mScheme) ? 0xff0a0a0a : 0xff303338));
        d.setCornerRadii(new float[]{dp(7), dp(7), dp(7), dp(7), 0, 0, 0, 0});
        // A 2dp accent at the bottom of the active tab is the cue that survives a dark theme,
        // where the tab fill and the pane are the same colour by design.
        if (active) {
            d.setStroke(dp(2), 0xff4a7fc1);
        }
        tab.plate.setBackground(d);

        tab.chip.setTextColor(active ? 0xffffffff : 0xff9a9a9a);
        tab.closeBox.setTextColor(active ? 0xffcfcfcf : 0x00000000);
        tab.closeBox.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        tab.plate.setPadding(dp(8), 0, dp(4), 0);
    }


    private void activate(Tab tab) {
        mActive = tab;
        for (Tab t : mTabs) updateChip(t);
        if (tab.session != null) {
            restoreTerminalView();
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
        mFonts = Fonts.load(this, cell, px);
        mTermView.setFaceSelector(new TerminalRenderer.FaceSelector() {
            @Override
            public Typeface typefaceFor(int codePoint) {
                return mFonts.typefaceFor(codePoint);
            }

            @Override
            public float scaleFor(int codePoint) {
                return mFonts.scaleFor(codePoint);
            }
        });
        mTermView.setTypeface(mFonts.latin());
    }

    /** dp -> px at this device's density. */
    private int fontPx() {
        return Math.max(8, Math.round(mFontDp * getResources().getDisplayMetrics().density));
    }

    private void applyFontSize(int dp) {
        mFontDp = Math.max(6, Math.min(28, dp));
        mPrefs.setFontSizeDp(mFontDp);
        if (mTermView.getCurrentSession() != null) {
            mTermView.setTextSize(fontPx());
        }
    }

    private void applyScheme(int index) {
        mScheme = index;
        mPrefs.setScheme(index);
        // On the OLED scheme every surface goes to true black; a #1e1e1e strip next to a
        // #000000 terminal is a visible seam and, on OLED, lit pixels.
        final int chrome = Schemes.isOled(index) ? 0xff000000 : 0xff1e1e1e;
        final int bar = Schemes.isOled(index) ? 0xff0a0a0a : 0xff2b2b2b;
        final int keys = Schemes.isOled(index) ? 0xff0d0d0d : 0xff252526;
        mRoot.setBackgroundColor(chrome);
        if (mTopBar != null) mTopBar.setBackgroundColor(bar);
        if (mTabStrip != null) mTabStrip.setBackgroundColor(bar);
        if (mKeyBar != null) mKeyBar.applyTheme(Schemes.isOled(index));
        if (mTabScroll != null) mTabScroll.setBackgroundColor(bar);
        if (mTermFrame != null) mTermFrame.setBackgroundColor(chrome);
        if (mActive != null && mActive.session != null) applySchemeTo(mActive.session);
    }

    private void applySchemeTo(TerminalSession session) {
        if (session == null || session.getEmulator() == null) return;
        TerminalColors colors = session.getEmulator().mColors;
        int[] table = Schemes.colors(mScheme);
        System.arraycopy(table, 0, TerminalColors.COLOR_SCHEME.mDefaultColors, 0, table.length);
        System.arraycopy(table, 0, colors.mCurrentColors, 0, table.length);
        // Only the Miku scheme has a picture; the others must clear it or the last one
        // stays behind the new palette.
        mTermView.setBackgroundImage(
                Schemes.hasBackgroundImage(mScheme) ? mMiku : null);
        mTermView.invalidate();
    }

    /**
     * Windows Terminal's command palette (Ctrl+Shift+P).
     *
     * A phone has no Ctrl+Shift+P, so the same list is also on the "+" menu; this exists for
     * a hardware keyboard and because it is the one WT affordance that scales down to a
     * searchable list rather than a menu of five items.
     */
    private void showCommandPalette() {
        final String[] commands = {
                "새 탭 열기",
                "탭 닫기",
                "탭 이름 바꾸기",
                "다음 탭",
                "붙여넣기",
                "복사",
                "글자 크기 키우기",
                "글자 크기 줄이기",
                "설정 열기",
        };

        final EditText filter = new EditText(this);
        filter.setHint("명령 검색");
        filter.setSingleLine(true);

        final android.widget.ArrayAdapter<String> adapter =
                new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, commands);
        final android.widget.ListView list = new android.widget.ListView(this);
        list.setAdapter(adapter);

        filter.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable e) {
                adapter.getFilter().filter(e.toString());
            }
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
        });

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(filter);
        box.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("명령 팔레트")
                .setView(box)
                .setNegativeButton("닫기", null)
                .create();

        list.setOnItemClickListener((parent, view, position, id) -> {
            runPaletteCommand(adapter.getItem(position));
            dialog.dismiss();
        });
        dialog.show();
    }

    private void runPaletteCommand(String command) {
        switch (command) {
            case "새 탭 열기": newTab(true); break;
            case "탭 닫기": if (mActive != null) confirmClose(mActive); break;
            case "탭 이름 바꾸기": renameActiveTab(); break;
            case "다음 탭": cycleTab(); break;
            case "붙여넣기": pasteClipboard(); break;
            case "복사": copySelection(); break;
            case "글자 크기 키우기": applyFontSize(mFontDp + 1); break;
            case "글자 크기 줄이기": applyFontSize(mFontDp - 1); break;
            case "설정 열기": showSettings(); break;
            default: break;
        }
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
        final String[] items = {
                "새 탭                    Ctrl+Shift+T",
                "이 탭 이름 바꾸기",
                "이 탭 닫기                Ctrl+Shift+W",
                "──",
                "붙여넣기                  Ctrl+Shift+V",
                "복사                      Ctrl+Shift+C",
                "──",
                "설정",
        };

        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        for (int i = 0; i < items.length; i++) {
            if (items[i].equals("──")) continue;
            final int index = i;
            android.view.MenuItem item = popup.getMenu().add(items[i]);
            item.setOnMenuItemClickListener(m -> {
                handleMenuChoice(index);
                return true;
            });
        }
        popup.show();
    }

    private void handleMenuChoice(int index) {
        switch (index) {
            case 0: newTab(true); break;
            case 1: renameActiveTab(); break;
            case 2: if (mActive != null) confirmClose(mActive); break;
            case 4: pasteClipboard(); break;
            case 5: copySelection(); break;
            case 7: showSettings(); break;
            default: break;
        }
        if (index == 0 || index == 2 || index == 7) { /* nothing else to do */ }
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

    private void showSettings() {
        if (mSettings == null) {
            mSettings = new SettingsSheet(this, new SettingsSheet.Host() {
                @Override public int fontSizeDp() { return mFontDp; }
                @Override public void onFontSize(int dp) { applyFontSize(dp); }
                @Override public int scheme() { return mScheme; }
                @Override public void onScheme(int index) { applyScheme(index); }
                @Override public boolean bellVibrate() { return mPrefs.bellVibrate(); }
                @Override public boolean bellSound() { return mPrefs.bellSound(); }
                @Override public void onBell(boolean vibrate, boolean sound) {
                    mPrefs.setBellVibrate(vibrate);
                    mPrefs.setBellSound(sound);
                }
                @Override public boolean keyBarVisible() { return mPrefs.keyBarVisible(); }
                @Override public void onKeyBarVisible(boolean visible) {
                    mPrefs.setKeyBarVisible(visible);
                    if (mKeyBar != null) mKeyBar.setVisible(visible);
                }
                @Override public java.util.ArrayList<String> keybarKeys() {
                    return new java.util.ArrayList<>(mPrefs.keybarKeys());
                }
                @Override public void onKeybarKeys(java.util.ArrayList<String> keys) {
                    mPrefs.setKeybarKeys(keys);
                    if (mKeyBar != null) mKeyBar.setKeys(keys);
                }
                @Override public String statusLine() {
                    return "세션: " + mTabs.size() + " · 서버 "
                            + mPrefs.host() + ":" + mPrefs.port();
                }
            });
        }
        mSettings.show();
    }

    // ---------------------------------------------------------------- keys

    private void toggleKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
    }

    private void showKeyboardSoon() {
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

    private void sendEscape(String what) {
        TerminalSession s = mActive == null ? null : mActive.session;
        if (s == null || s.getEmulator() == null) return;
        TerminalEmulator e = s.getEmulator();
        int code;
        switch (what) {
            case "tab": code = KeyEvent.KEYCODE_TAB; break;
            case "esc": code = KeyEvent.KEYCODE_ESCAPE; break;
            case "up": code = KeyEvent.KEYCODE_DPAD_UP; break;
            case "down": code = KeyEvent.KEYCODE_DPAD_DOWN; break;
            case "left": code = KeyEvent.KEYCODE_DPAD_LEFT; break;
            case "right": code = KeyEvent.KEYCODE_DPAD_RIGHT; break;
            case "home": code = KeyEvent.KEYCODE_MOVE_HOME; break;
            case "end": code = KeyEvent.KEYCODE_MOVE_END; break;
            case "pgup": code = KeyEvent.KEYCODE_PAGE_UP; break;
            case "pgdn": code = KeyEvent.KEYCODE_PAGE_DOWN; break;
            default: return;
        }
        mTermView.handleKeyCode(code, 0);
        clearSticky();
    }

    private void clearSticky() {
        mFn = false;
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

    @Override
    protected void onResume() {
        super.onResume();
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
        if (mPrefs.backIsEscape() && mActive != null && mActive.session != null) {
            sendEscape("esc");
            return;
        }
        super.onBackPressed();
    }

    // ---------------------------------------------------------------- TerminalViewClient

    @Override
    public float onScale(float scale) {
        mScale = Math.max(0.5f, Math.min(3f, scale));
        applyFontSize((int) (mFontDp * mScale));
        mScale = 1f;
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

        // An image clipboard has no text: coerceToText would give null or a bare "res/..."
        // URI string. ClipboardImage saves the bitmap and this pastes the PATH - opencode
        // attaches file:// URIs in the prompt, so a pasted screenshot becomes an image the
        // model can see.
        if (ClipboardImage.isImage(this, clip)) {
            String path = ClipboardImage.save(this, ClipboardImage.uriOf(clip));
            if (path != null) {
                pasteText(path);
                Toast.makeText(this, "이미지 경로 붙여넣음: " + path, Toast.LENGTH_LONG).show();
                return;
            }
        }

        CharSequence text = item.coerceToText(this);
        if (text == null || text.length() == 0) return;
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
                t.title = (title == null || title.trim().isEmpty()) ? "shell" : title.trim();
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
        return TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
    }
}
