package io.github.crashedsystem.fterm;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The settings surface, in the shape Windows Terminal uses.
 *
 * <p>WT's settings are two levels, not one dialog: a navigation column of pages (시작, 모양,
 * 색 구성표, ...), and one page at a time to its right - a breadcrumb at the top, a column of
 * rounded cards, and inside each card a list of labelled rows separated by hairlines. Nothing
 * is a button; values sit on the right of their own row. The previous sheet here was a single
 * scrolling list of platform {@link android.widget.Button}s and {@link android.widget.Switch}es,
 * which is the default look of an Android dialog and reads as a form rather than as a
 * configuration surface.
 *
 * <p>A phone has no room for a permanent sidebar, so the navigation column is the root page and
 * a page pushes over it with a back arrow - WT itself collapses to the same thing when the
 * window is narrow. The reference screenshots are 1920x1040 desktop shots; the structure is
 * what was taken from them, not the desktop proportions.
 *
 * <p>This class is the frame and the router: it owns the dialog, the breadcrumb, and which page
 * is showing. Each page is its own file ({@link SettingsRootPage}, {@link SettingsStartPage},
 * {@link SettingsProfilesPage}, {@link SettingsAppearancePage}, {@link SettingsSchemesPage},
 * {@link SettingsInteractionPage}, {@link SettingsAboutPage}), and the pieces two pages share -
 * the palette and row shapes, the font pickers, the icon grid - are files of their own too. The
 * sheet is a thousand lines shorter for it, and a page can be read without the other five.
 *
 * <p>It owns no state: every control reports through {@link Host}, and the host decides what to
 * persist and repaint.
 */
final class SettingsSheet {

    interface Host {
        int fontSizeDp();

        void onFontSize(int dp);

        int scheme();

        void onScheme(int index);

        /** The active scheme's display name, a built-in's or one the user saved. */
        String schemeName();

        /** How many custom schemes are saved; their scheme indices start at CUSTOM_INDEX. */
        int customSchemeCount();

        /** The saved name of custom scheme i, in list order. */
        String customSchemeName(int index);

        /** The nineteen editable slots of the active scheme (the first custom if a built-in is). */
        int[] customSlots();

        void onCustomSlot(int slot, int color);

        /**
         * Saves a new custom scheme and selects it.
         *
         * @return its scheme index, or -1 when the sixteen are full - the caller says so
         *         rather than dropping the palette on the floor.
         */
        int addCustomScheme(String name, int[] slots);

        void onRenameCustomScheme(int index, String name);

        /** Deletes custom scheme i; the selection moves if it named that one. */
        void onDeleteCustomScheme(int index);

        /** Whether a background picture is installed. */
        boolean backgroundSet();

        /** The picture's file name, or null when there is none. */
        String backgroundName();

        int backgroundOpacity();

        void onBackgroundOpacity(int percent);

        String backgroundFit();

        void onBackgroundFit(String fit);

        /** Where the picture sits when it does not fill the terminal. */
        String backgroundAlign();

        void onBackgroundAlign(String align);

        int backgroundDim();

        void onBackgroundDim(int percent);

        /** The store changed outside the sheet: re-read the file and repaint. */
        void onBackgroundChanged();

        /** WT's retro effect: scanlines and a vignette. */
        boolean retroEffect();

        void onRetroEffect(boolean on);

        /** The mark at the left of every tab: a glyph id or literal text. */
        String tabIcon();

        void onTabIcon(String id);

        /** The cursor colour override, or 0 to follow the scheme. */
        int cursorColor();

        void onCursorColor(int color);

        /** The cursor shape: 0 block, 1 underline, 2 bar. */
        int cursorStyle();

        void onCursorStyle(int style);

        boolean cursorBlink();

        void onCursorBlink(boolean on);

        /** How long the cursor stays in each phase, in milliseconds. */
        int cursorBlinkMs();

        void onCursorBlinkMs(int ms);

        /** The daemon's address, for one-shot font questions. */
        String daemonHost();

        int daemonPort();

        String daemonToken();

        /** The face id from {@link Fonts#catalog}; see {@link Fonts.Face#id}. */
        String fontFamily();

        void onFontFamily(String id);

        /** The face id from {@link Fonts#hangulCatalog}, for Korean syllables. */
        String hangulFamily();

        void onHangulFamily(String id);

        /** The Japanese/Chinese face id; empty means the platform fallback. */
        String cjkFamily();

        void onCjkFamily(String id);

        /** The directory new sessions start in; empty means the daemon's home. */
        String startDir();

        void onStartDir(String dir);

        /** The shell new sessions run; empty means the daemon's default. */
        String startShell();

        void onStartShell(String shell);

        /** The user's profiles, in menu order. Never null. */
        java.util.List<Profiles.Profile> profiles();

        void onProfiles(java.util.List<Profiles.Profile> list);

        boolean bellVibrate();

        boolean bellSound();

        void onBell(boolean vibrate, boolean sound);

        boolean keyBarVisible();

        void onKeyBarVisible(boolean visible);

        /** Whether a hardware keyboard hides the key bar by itself. */
        boolean keyBarAutoHide();
        void onKeyBarAutoHide(boolean on);

        boolean keyboardOnStart();

        void onKeyboardOnStart(boolean on);

        boolean backIsEscape();

        void onBackIsEscape(boolean on);

        /** e.g. io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0303) */
        String statusLine();
    }

    /** Root navigation when null; otherwise the page key. */
    private String mPage;

    private final Activity mActivity;
    private final Host mHost;
    private final SettingsUi mUi;

    /** Built the first time a font row is tapped; it also owns the import result. */
    private SettingsFontPicker mFonts;

    /** The 모양 page's picture rows and the image picker result. */
    private SettingsBackgroundPicker mBackground;

    /** The 색 구성표 page's Windows Terminal JSON import. */
    private SettingsSchemeImport mSchemeImport;

    /** The 색 구성표 page's rename/delete dialog for a saved scheme. */
    private SettingsSchemeEditor mSchemeEditor;

    private android.app.Dialog mDialog;

    SettingsSheet(Activity activity, Host host) {
        mActivity = activity;
        mHost = host;
        mUi = new SettingsUi(activity);
    }

    Activity activity() {
        return mActivity;
    }

    Host host() {
        return mHost;
    }

    SettingsUi ui() {
        return mUi;
    }

    /**
     * The font pickers, one instance for the sheet's life.
     *
     * <p>One instance because a picked file comes back through the activity to whichever object
     * started the picker; a fresh picker per tap would have nothing to hand the result to.
     */
    SettingsFontPicker fonts() {
        if (mFonts == null) mFonts = new SettingsFontPicker(this);
        return mFonts;
    }

    SettingsBackgroundPicker background() {
        if (mBackground == null) mBackground = new SettingsBackgroundPicker(this);
        return mBackground;
    }

    SettingsSchemeImport schemes() {
        if (mSchemeImport == null) mSchemeImport = new SettingsSchemeImport(this);
        return mSchemeImport;
    }

    /** The rename/delete dialog, one instance so a render does not stack dialogs. */
    SettingsSchemeEditor schemeEditor() {
        if (mSchemeEditor == null) mSchemeEditor = new SettingsSchemeEditor(this);
        return mSchemeEditor;
    }

    void show() {
        mPage = null;
        render();
    }

    /** Open directly on a page, which is what the menu's io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0304) does. */
    void show(String page) {
        mPage = page;
        render();
    }

    void open(String page) {
        mPage = page;
        render();
    }

    // ------------------------------------------------------------------ frame

    void render() {
        LinearLayout root = new LinearLayout(mActivity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(mUi.WINDOW);
        root.setPadding(mUi.dp(14), mUi.dp(2), mUi.dp(14), 0);
        root.addView(header());

        ScrollView sv = new ScrollView(mActivity);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.addView(body());
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (mDialog == null) {
            mDialog = new android.app.Dialog(mActivity);
            mDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            mDialog.setCanceledOnTouchOutside(true);
        }
        // setContentView on a dismissed window paints nothing: the dialog has to be shown
        // again on every render, which is what re-opening settings after closing it needs.
        mDialog.setContentView(root);
        if (!mDialog.isShowing()) {
            mDialog.show();
            android.view.Window w = mDialog.getWindow();
            if (w != null) {
                // The window's own background would draw the platform's card behind the
                // surface; on OneUI that card is light and it showed as a white rim.
                w.setBackgroundDrawable(Chrome.plate(mUi.WINDOW, 12));
            }
        }
        // Every render, not once: configChanges keeps the activity alive across a rotation, so
        // a portrait sheet would otherwise keep its height and cut its bottom edge off.
        DialogSize.sheet(mDialog, mActivity);
    }

    /** Re-fit the window after the screen changed size or orientation. */
    void relayout() { if (mDialog != null && mDialog.isShowing()) DialogSize.sheet(mDialog, mActivity); }

    /**
     * Breadcrumb on the left, close glyph on the right, exactly like WT's page header.
     *
     * <p>The left glyph is the back arrow on a page and the app's own mark on the root; WT puts
     * the hamburger there because its sidebar toggles, which is meaningless on a phone.
     */
    private View header() {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, mUi.dp(8), 0, mUi.dp(8));

        if (mPage != null) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    mUi.dp(22), mUi.dp(22));
            blp.leftMargin = mUi.dp(4);
            blp.rightMargin = mUi.dp(8);
            row.addView(Chrome.icon(mActivity, "left", 22, false, v -> show()), blp);
        } else {
            View mark = Chrome.icon(mActivity, "gear", 22, false, null);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(mUi.dp(22), mUi.dp(22));
            mlp.rightMargin = mUi.dp(6);
            mark.setLayoutParams(mlp);
            row.addView(mark);
        }

        LinearLayout titles = new LinearLayout(mActivity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView crumb = new TextView(mActivity);
        // WT renders the trail as a single span: the ancestors dimmed, the leaf bright.
        // Building it as one Spannable instead of concatenating two resources also removes the
        // dependency on leading/trailing spaces, which aapt2 trims unless the value is quoted -
        // that trimming was why "설정 ›" and the page name ran together.
        if (mPage == null) {
            crumb.setText(AppStrings.s(R.string.i18n_0306));
        } else {
            String ancestor = AppStrings.s(R.string.i18n_0306);
            String separator = "  \u203a  ";
            String leaf = titleOf(mPage);
            android.text.SpannableString trail =
                    new android.text.SpannableString(ancestor + separator + leaf);
            trail.setSpan(new android.text.style.ForegroundColorSpan(mUi.HINT),
                    0, ancestor.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            trail.setSpan(new android.text.style.ForegroundColorSpan(mUi.TEXT),
                    ancestor.length() + separator.length(), trail.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            crumb.setText(trail);
        }
        crumb.setTextColor(mUi.TEXT);
        crumb.setTextSize(mPage == null ? 18 : 13);
        crumb.setSingleLine(true);
        crumb.setEllipsize(android.text.TextUtils.TruncateAt.END);
        UiFont.apply(crumb, mPage == null);
        titles.addView(crumb);
        row.addView(titles);

        row.addView(Chrome.icon(mActivity, "close", 20, false, v -> {
            if (mDialog != null) mDialog.dismiss();
        }));
        return row;
    }

    private static String titleOf(String page) {
        switch (page) {
            case "start": return io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0241);
            case "profiles": return io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0240);
            case "appearance": return io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0244);
            case "schemes": return io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0128);
            case "interaction": return AppStrings.s(R.string.i18n_0247);
            case "language": return AppStrings.s(R.string.i18n_0388);
            default: return io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0024);
        }
    }

    private View body() {
        LinearLayout col = new LinearLayout(mActivity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, mUi.dp(2), 0, mUi.dp(24));
        page().build(col);
        return col;
    }

    /**
     * The page for the current key.
     *
     * <p>A new instance per render, on purpose: a page holds nothing, so there is nothing to
     * carry over, and a stale page would be a second copy of the truth.
     */
    private SettingsPage page() {
        if (mPage == null) return new SettingsRootPage(this);
        if ("start".equals(mPage)) return new SettingsStartPage(this);
        if ("profiles".equals(mPage)) return new SettingsProfilesPage(this);
        if ("appearance".equals(mPage)) return new SettingsAppearancePage(this);
        if ("schemes".equals(mPage)) return new SettingsSchemesPage(this);
        if ("interaction".equals(mPage)) return new SettingsInteractionPage(this);
        if ("language".equals(mPage)) return new SettingsLanguagePage(this);
        return new SettingsAboutPage(this);
    }

    /** The activity's file-picker result, handed to whichever picker asked for it. */
    void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (mFonts != null) mFonts.onFontImportResult(requestCode, resultCode, data);
        if (mBackground != null) mBackground.onActivityResult(requestCode, resultCode, data);
        if (mSchemeImport != null) mSchemeImport.onActivityResult(requestCode, resultCode, data);
    }
}
