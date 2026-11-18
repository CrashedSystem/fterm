package ai.openclaw.fterm;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.termux.terminal.TextStyle;

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
 * <p>It owns no state: every control reports through {@link Host}, and the host decides what to
 * persist and repaint.
 */
final class SettingsSheet {

    interface Host {
        int fontSizeDp();

        void onFontSize(int dp);

        int scheme();

        void onScheme(int index);

        /** The nineteen editable slots: 16 ANSI, then foreground, background, cursor. */
        int[] customSlots();

        void onCustomSlot(int slot, int color);

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

        boolean keyboardOnStart();

        void onKeyboardOnStart(boolean on);

        boolean backIsEscape();

        void onBackIsEscape(boolean on);

        /** e.g. "세션: 3 · 서버 127.0.0.1:8791" */
        String statusLine();
    }

    // WT's settings window: a dark surface, cards a step lighter, one accent colour.
    private static final int WINDOW = 0xff1f2126;
    private static final int CARD = 0xff2a2d33;
    private static final int CARD_HI = 0xff34383f;
    private static final int TEXT = 0xffe8eaee;
    private static final int HINT = 0xff8a8f99;
    private static final int HAIRLINE = 0xff34383f;
    private static final int ACCENT = 0xff4a9eff;

    /** Root navigation when null; otherwise the page key. */
    private String mPage;

    private final Activity mActivity;
    private final Host mHost;
    private android.app.Dialog mDialog;

    SettingsSheet(Activity activity, Host host) {
        mActivity = activity;
        mHost = host;
    }

    void show() {
        mPage = null;
        render();
    }

    /** Open directly on a page, which is what the menu's "프로필 관리" does. */
    void show(String page) {
        mPage = page;
        render();
    }

    private void open(String page) {
        mPage = page;
        render();
    }

    // ------------------------------------------------------------------ frame

    private void render() {
        LinearLayout root = new LinearLayout(mActivity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(WINDOW);
        root.setPadding(dp(14), dp(2), dp(14), 0);
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
                w.setBackgroundDrawable(Chrome.plate(WINDOW, 12));
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (mActivity.getResources().getDisplayMetrics().heightPixels * 0.90f));
            }
        }
    }

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
        row.setPadding(0, dp(8), 0, dp(8));

        if (mPage != null) {
            row.addView(Chrome.icon(mActivity, "left", 22, false, v -> show()));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    dp(20), dp(20));
            lp.leftMargin = dp(4);
            lp.rightMargin = dp(8);
            // Spacer keeps the title from sliding under the close glyph when the title is long.
            row.addView(new View(mActivity), new LinearLayout.LayoutParams(0, 1, 0f));
        } else {
            View mark = Chrome.icon(mActivity, "gear", 22, false, null);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(22), dp(22));
            mlp.rightMargin = dp(6);
            mark.setLayoutParams(mlp);
            row.addView(mark);
        }

        LinearLayout titles = new LinearLayout(mActivity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView crumb = new TextView(mActivity);
        // WT renders the trail as "색 구성표 › Miku": the ancestors dimmed, the leaf bright.
        // There is one level here, so the trail is the page name alone unless we are at root.
        crumb.setText(mPage == null ? "설정" : "설정  ›  " + titleOf(mPage));
        crumb.setTextColor(TEXT);
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
            case "start": return "시작";
            case "profiles": return "프로필";
            case "appearance": return "모양";
            case "schemes": return "색 구성표";
            case "interaction": return "상호 작용";
            default: return "정보";
        }
    }

    private View body() {
        LinearLayout col = new LinearLayout(mActivity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(2), 0, dp(24));

        if (mPage == null) {
            nav(col);
        } else if ("start".equals(mPage)) {
            startPage(col);
        } else if ("profiles".equals(mPage)) {
            profilesPage(col);
        } else if ("appearance".equals(mPage)) {
            appearancePage(col);
        } else if ("schemes".equals(mPage)) {
            schemesPage(col);
        } else if ("interaction".equals(mPage)) {
            interactionPage(col);
        } else {
            aboutPage(col);
        }
        return col;
    }

    // ------------------------------------------------------------------ root navigation

    /**
     * The navigation column. WT draws each entry as glyph + label, and so does this; "프로필" is
     * not a real page on the phone build, so it is not listed as one.
     */
    private void nav(LinearLayout col) {
        col.addView(navRow("terminal", "시작", "기본 프로필, 셸, 시작 동작", "start"));
        col.addView(navRow("ssh", "프로필", "ssh처럼 명령으로 시작하는 탭", "profiles"));
        col.addView(navRow("pencil", "모양", "글자 크기, 키 바", "appearance"));
        col.addView(navRow("swatch", "색 구성표", "16색 팔레트와 미리보기", "schemes"));
        col.addView(navRow("keyboard", "상호 작용", "벨, 뒤로 가기 키", "interaction"));
        col.addView(navRow("info", "정보", "버전과 연결 상태", "about"));
    }

    private View navRow(String glyph, String label, String hint, final String page) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(9), dp(8), dp(9));
        row.setBackground(touchable());
        row.setClickable(true);
        row.setOnClickListener(v -> open(page));

        View ic = Chrome.icon(mActivity, glyph, 20, false, null);
        // The gap is a margin, not padding: the glyph fills its ImageView's content box, so
        // padding here would shrink the icon instead of spacing it (that is why the sidebar
        // icons came out at a third of their size).
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(20), dp(20));
        ilp.rightMargin = dp(14);
        ic.setLayoutParams(ilp);
        row.addView(ic);

        LinearLayout text = new LinearLayout(mActivity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(mActivity);
        t.setText(label);
        t.setTextColor(TEXT);
        t.setTextSize(14.5f);
        UiFont.apply(t, false);
        text.addView(t);
        TextView h = new TextView(mActivity);
        h.setText(hint);
        h.setTextColor(HINT);
        h.setTextSize(11);
        text.addView(h);
        row.addView(text);

        // A right-pointing chevron: WT uses it on every row that leads somewhere else.
        View more = Chrome.icon(mActivity, "right", 16, true, null);
        row.addView(more);

        LinearLayout wrap = new LinearLayout(mActivity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row);
        wrap.addView(hairline(dp(10), dp(0)));
        return wrap;
    }

    // ------------------------------------------------------------------ pages

    /** WT's 시작 page: rows that mostly name a default and show its value on the right. */
    /**
     * WT's profiles page: a list of them, and an editor per profile.
     *
     * <p>A profile here is the four things a tab can differ in. The command is the field that
     * makes an "ssh profile" possible, and it is typed into the shell rather than replacing
     * it - see {@link Profiles}.
     */
    private void profilesPage(LinearLayout col) {
        LinearLayout card = card(col);
        java.util.List<Profiles.Profile> list = mHost.profiles();
        if (list.isEmpty()) {
            LinearLayout empty = new LinearLayout(mActivity);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setPadding(dp(14), dp(14), dp(14), dp(14));
            TextView t = new TextView(mActivity);
            t.setText("프로필이 없습니다");
            t.setTextColor(TEXT);
            t.setTextSize(13);
            empty.addView(t);
            TextView sub = new TextView(mActivity);
            sub.setText("아래에서 추가하면 메뉴 첫 블록에 나타납니다.");
            sub.setTextColor(HINT);
            sub.setTextSize(10.5f);
            sub.setPadding(0, dp(3), 0, 0);
            empty.addView(sub);
            card.addView(empty);
        } else {
            for (int i = 0; i < list.size(); i++) {
                final int index = i;
                final Profiles.Profile p = list.get(i);
                card.addView(profileRow(p, () -> editProfile(index, false),
                        i == list.size() - 1));
            }
        }

        card.addView(valueRow("프로필 추가", "예: ssh 서버 이름과 명령", "+",
                () -> editProfile(-1, true), false));

        // An example, not documentation. "명령에 opencode를 넣으세요" is a sentence the user
        // has to translate into four fields and an icon; a row that installs the finished
        // profile is the same information in one tap, and it doubles as the only place the
        // OpenCode mark is discoverable without opening the icon grid.
        card.addView(valueRow("예제: opencode", "OpenCode를 띄우는 프로필을 추가합니다", "",
                this::addOpencodeExample, true));

        hint(col, "줄을 누르면 편집합니다. 탭을 열 때는 왼쪽 위 메뉴 첫 블록에서 "
                + "프로필을 고르세요. 명령은 셸이 뜬 뒤에 입력되므로 ssh가 로그인 셸의 키와 "
                + "설정을 그대로 씁니다.");
    }

    /**
     * The example profile: opencode, with its own mark.
     *
     * <p>Four fields are already filled in by {@link Profiles#opencodeExample()}, so this is
     * only the confirmation step - the row says what it will do, the dialog names the command,
     * and a second opencode profile is refused rather than piled up.
     */
    private void addOpencodeExample() {
        Profiles.Profile example = Profiles.opencodeExample();
        boolean present = false;
        for (Profiles.Profile p : mHost.profiles()) {
            if (example.name.equals(p.name) && example.command.equals(p.command)) present = true;
        }
        if (present) {
            new android.app.AlertDialog.Builder(mActivity)
                    .setTitle("예제: opencode")
                    .setMessage("opencode 프로필이 이미 있습니다. 프로필 목록에서 눌러 편집하세요.")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }
        new android.app.AlertDialog.Builder(mActivity)
                .setTitle("예제: opencode")
                .setMessage("이름 「" + example.name + "」, 시작 명령 「" + example.command
                        + "」, 아이콘 OpenCode로 프로필을 추가합니다.\n\n"
                        + "탭을 열면 셸 위에서 opencode가 실행됩니다. "
                        + "설치 경로가 PATH에 있어야 합니다.")
                .setPositiveButton("추가", (d, w) -> {
                    java.util.List<Profiles.Profile> list = mHost.profiles();
                    list.add(example);
                    mHost.onProfiles(list);
                    render();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * One row of the profile list: the mark the tab will carry, the name, the subtitle.
     *
     * <p>The mark is shown here rather than only in the editor because the list is where the
     * user decides which profile is which. A name alone makes two profiles called "서버"
     * indistinguishable; the icon is the thing that would have told them apart in the tab
     * strip, so it belongs in the list that configures the strip.
     */
    private View profileRow(Profiles.Profile p, Runnable onClick, boolean last) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        row.setClickable(true);
        row.setBackground(touchable());
        row.setOnClickListener(v -> onClick.run());

        LinearLayout slot = new LinearLayout(mActivity);
        slot.setGravity(Gravity.CENTER);
        slot.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        String icon = p.icon == null || p.icon.isEmpty() ? "" : p.icon;
        slot.addView(icon.isEmpty()
                ? Chrome.icon(mActivity, "terminal", 16, true, null)
                : TabIcons.build(mActivity, icon, HINT));
        LinearLayout.LayoutParams slp = (LinearLayout.LayoutParams) slot.getLayoutParams();
        slp.rightMargin = dp(10);
        slot.setLayoutParams(slp);
        row.addView(slot);

        LinearLayout text = new LinearLayout(mActivity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(mActivity);
        name.setText(p.name.isEmpty() ? "(이름 없음)" : p.name);
        name.setTextColor(TEXT);
        name.setTextSize(13.5f);
        UiFont.apply(name, false);
        text.addView(name);
        String sub = p.subtitle();
        if (!sub.isEmpty()) {
            TextView s = new TextView(mActivity);
            s.setText(sub);
            s.setTextColor(HINT);
            s.setTextSize(10.5f);
            text.addView(s);
        }
        row.addView(text);
        row.addView(Chrome.icon(mActivity, "right", 16, true, null));
        return cardRow(row, last);
    }

    /** The four fields, edited as one dialog: they are one thing. */
    /**
     * Edit or create the profile at {@code index}; {@code -1} means a new one.
     *
     * <p>The index, not the object, is the handle. {@link SettingsHost#profiles()} decodes the
     * stored string afresh on every call, so the instance this dialog was built from is never
     * the instance the save path sees - identity comparison there silently did nothing, and a
     * deleted profile reappeared on the next render.
     */
    private void editProfile(final int index, final boolean isNew) {
        java.util.List<Profiles.Profile> current = mHost.profiles();
        if (!isNew && (index < 0 || index >= current.size())) return;
        final Profiles.Profile existing = isNew ? new Profiles.Profile() : current.get(index);

        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);

        final android.widget.EditText name = field(existing.name, "예: 서버");
        final android.widget.EditText dir = field(existing.dir, "/data/data/com.termux/files/home");
        final android.widget.EditText shell = field(existing.shell, "/data/data/com.termux/files/usr/bin/bash");
        final android.widget.EditText command = field(existing.command, "ssh myhost");

        // The icon is picked, not typed, so it lives in a mutable holder rather than in an
        // EditText: the dialog reads it when Save is pressed, exactly like the fields.
        final String[] icon = {existing.icon == null ? "" : existing.icon};

        box.addView(label("이름"));
        box.addView(name);

        LinearLayout iconRow = new LinearLayout(mActivity);
        iconRow.setOrientation(LinearLayout.HORIZONTAL);
        iconRow.setGravity(Gravity.CENTER_VERTICAL);
        iconRow.setPadding(0, dp(8), 0, 0);
        iconRow.setClickable(true);
        iconRow.setBackground(touchable());
        LinearLayout iconText = new LinearLayout(mActivity);
        iconText.setOrientation(LinearLayout.VERTICAL);
        iconText.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView iconTitle = new TextView(mActivity);
        iconTitle.setText("아이콘");
        iconTitle.setTextColor(TEXT);
        iconTitle.setTextSize(13.5f);
        UiFont.apply(iconTitle, false);
        iconText.addView(iconTitle);
        TextView iconValue = new TextView(mActivity);
        iconValue.setTextColor(HINT);
        iconValue.setTextSize(11);
        iconText.addView(iconValue);
        iconRow.addView(iconText);
        // A live preview of the mark beside its name, drawn by the same builder the tab uses.
        LinearLayout iconPreview = new LinearLayout(mActivity);
        iconPreview.setGravity(Gravity.CENTER);
        iconPreview.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(30)));
        iconRow.addView(iconPreview);
        box.addView(iconRow);

        final Runnable repaintIcon = () -> {
            String id = icon[0];
            String shown = id.isEmpty() ? "탭 아이콘과 같음"
                    : TabIcons.isGlyph(id) ? TabIcons.GLYPH_NAMES[glyphIndexOf(id)]
                    : id;
            iconValue.setText(shown);
            iconPreview.removeAllViews();
            iconPreview.addView(TabIcons.build(mActivity, id.isEmpty()
                    ? TabIcons.DEFAULT_ID : id, TEXT));
        };
        repaintIcon.run();
        iconRow.setOnClickListener(v -> pickIcon("프로필 아이콘", icon[0], "없음", id -> {
            icon[0] = id;
            repaintIcon.run();
        }));

        box.addView(label("시작 디렉터리 (선택)"));
        box.addView(dir);
        box.addView(label("셸 (선택)"));
        box.addView(shell);
        box.addView(label("시작 명령 (선택)"));
        box.addView(command);
        TextView note = new TextView(mActivity);
        note.setText("명령은 한 줄이어야 합니다. 비우면 그냥 셸이 뜹니다.");
        note.setTextColor(HINT);
        note.setTextSize(10.5f);
        note.setPadding(0, dp(6), 0, 0);
        box.addView(note);

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(mActivity)
                .setTitle(isNew ? "프로필 추가" : "프로필 편집")
                .setView(box)
                .setPositiveButton("저장", (d, w) -> {
                    Profiles.Profile p = new Profiles.Profile(
                            name.getText().toString().trim(),
                            dir.getText().toString().trim(),
                            shell.getText().toString().trim(),
                            command.getText().toString().trim(),
                            icon[0]);
                    java.util.List<Profiles.Profile> list = mHost.profiles();
                    if (isNew || index >= list.size()) {
                        list.add(p);
                    } else {
                        // Editing in place keeps the menu order the user built.
                        list.set(index, p);
                    }
                    mHost.onProfiles(list);
                    render();
                })
                .setNegativeButton("취소", null);
        if (!isNew) {
            b.setNeutralButton("삭제", (d, w) -> {
                java.util.List<Profiles.Profile> list = mHost.profiles();
                if (index < list.size()) list.remove(index);
                mHost.onProfiles(list);
                render();
            });
        }
        b.show();
    }

    private android.widget.EditText field(String value, String hint) {
        android.widget.EditText f = new android.widget.EditText(mActivity);
        f.setText(value);
        f.setSingleLine(true);
        f.setTextSize(13);
        f.setTypeface(Typeface.MONOSPACE);
        f.setTextColor(TEXT);
        f.setHintTextColor(HINT);
        f.setHint(hint);
        return f;
    }

    private TextView label(String text) {
        TextView t = new TextView(mActivity);
        t.setText(text);
        t.setTextColor(HINT);
        t.setTextSize(10.5f);
        t.setPadding(0, dp(8), 0, dp(2));
        return t;
    }

    private void startPage(LinearLayout col) {
        LinearLayout card = card(col);

        card.addView(valueRow("기본 프로필", "새 탭을 열 때 쓰는 프로필",
                Profiles.DEFAULT_NAME, null, false));
        card.addView(valueRow("시작 디렉터리", "새 세션이 시작하는 위치",
                mHost.startDir().isEmpty() ? "홈" : mHost.startDir(),
                this::editStartDir, false));
        card.addView(valueRow("시작 셸", "새 세션이 실행하는 프로그램",
                mHost.startShell().isEmpty() ? "기본 셸" : mHost.startShell(),
                this::editStartShell, false));
        card.addView(toggleCardRow("앱을 열 때 키보드", "시작하자마자 입력할 수 있습니다",
                mHost.keyboardOnStart(), mHost::onKeyboardOnStart, true));

        hint(col, "디렉터리와 셸은 다음에 여는 탭부터 적용됩니다. "
                + "이미 열린 탭은 그대로입니다. 셸은 데몬이 허용한 경로만 실행됩니다.");
    }

    /**
     * A one-line text prompt, used by both start-page rows.
     *
     * <p>WT opens a file picker for the directory; a phone has no such picker that reaches
     * the Termux home, and the path is already the thing the user thinks in, so the field is
     * the path itself. Empty means "the daemon's default", which is why the hint says so.
     */
    private void prompt(String title, String value, String placeholder, String noteText,
                        java.util.function.Consumer<String> onOk) {
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);

        final android.widget.EditText field = new android.widget.EditText(mActivity);
        field.setText(value);
        field.setSingleLine(true);
        field.setTextSize(14);
        field.setTypeface(Typeface.MONOSPACE);
        field.setTextColor(TEXT);
        field.setHintTextColor(HINT);
        field.setHint(placeholder);
        box.addView(field);

        TextView note = new TextView(mActivity);
        note.setText(noteText);
        note.setTextColor(HINT);
        note.setTextSize(10.5f);
        note.setPadding(0, dp(6), 0, 0);
        box.addView(note);

        new android.app.AlertDialog.Builder(mActivity)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("저장", (d, w) -> onOk.accept(field.getText().toString().trim()))
                .setNegativeButton("취소", null)
                .setNeutralButton("기본값", (d, w) -> onOk.accept(""))
                .show();
    }

    /**
     * The face picker: every fixed-advance font on the device, each rendered in itself.
     *
     * <p>Rendered in itself is the whole point - the only honest way to pick a monospace face
     * is to look at it, and a name tells you nothing about whether "M" is legible at 12dp.
     *
     * <p>The list is scanned, not enumerated, and it is long: this device has hundreds of
     * fonts and a handful are fixed-advance. So the scroll is capped, the current face is
     * pinned at the top, and there is a way to rescan after the user copies a file in.
     */
    private void chooseFont() {
        final java.util.List<Fonts.Face> faces = Fonts.catalog(mActivity);
        String current = mHost.fontFamily();

        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(4), dp(8), 0);

        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];

        // One row for every way a font arrives, above the list rather than as a fourth dialog
        // button. An AlertDialog has three buttons and they are already spent on rescan/close;
        // more importantly, "how do I get a font in here" is the question the list itself
        // should answer, in the place the new font will appear.
        LinearLayout install = new LinearLayout(mActivity);
        install.setOrientation(LinearLayout.HORIZONTAL);
        install.setGravity(Gravity.CENTER_VERTICAL);
        install.setPadding(dp(12), dp(12), dp(12), dp(12));
        install.setBackground(touchable());
        install.setClickable(true);
        install.addView(Chrome.icon(mActivity, "folder", 18, false, null));
        TextView installLabel = new TextView(mActivity);
        installLabel.setText("글꼴 설치 — Termux에서 받기, 기기에서 고르기");
        installLabel.setTextColor(ACCENT);
        installLabel.setTextSize(12.5f);
        installLabel.setPadding(dp(10), 0, 0, 0);
        install.addView(installLabel);
        install.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            installFont();
        });
        box.addView(install);
        box.addView(hairline(dp(12), dp(0)));

        for (int i = 0; i < faces.size(); i++) {
            final Fonts.Face f = faces.get(i);
            LinearLayout row = new LinearLayout(mActivity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(11), dp(12), dp(11));
            row.setBackground(touchable());
            row.setClickable(true);

            TextView sample = new TextView(mActivity);
            sample.setText("Mmm 0123 한");
            sample.setTextSize(15);
            sample.setTextColor(TEXT);
            sample.setTypeface(faceFor(f));
            sample.setSingleLine(true);
            sample.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(sample);

            TextView name = new TextView(mActivity);
            name.setText(f.label);
            name.setTextSize(11);
            name.setTextColor(f.id.equals(current) ? ACCENT : HINT);
            name.setPadding(0, 0, dp(10), 0);
            row.addView(name);

            if (f.id.equals(current)) row.addView(Chrome.icon(mActivity, "check", 16, false, null));

            row.setOnClickListener(v -> {
                mHost.onFontFamily(f.id);
                if (holder[0] != null) holder[0].dismiss();
                render();
            });
            box.addView(row);
            if (i < faces.size() - 1) box.addView(hairline(dp(12), dp(0)));
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(mActivity);
        scroll.setFillViewport(true);
        scroll.addView(box);
        // A cap, not a full-screen list: the dialog keeps its title and buttons visible while
        // the user scrolls, and a device with four hundred fonts does not become four hundred
        // rows of chrome.
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(340)));

        holder[0] = new android.app.AlertDialog.Builder(mActivity)
                .setTitle("글꼴")
                .setView(scroll)
                .setPositiveButton("다시 찾기", (d, w) -> {
                    Fonts.rescan();
                    render();
                    chooseFont();
                })
                .setNegativeButton("닫기", null)
                .create();
        holder[0].show();
    }

    /**
     * The Korean face, from its own list.
     *
     * <p>A separate picker from {@link #chooseFont} because it is a separate question with a
     * separate filter. The Latin list is fixed-advance only; every Korean font on a real
     * device is proportional, so that list was structurally unable to contain one. Here the
     * only requirement is that the face answers Hangul, and the sample is a Korean word plus
     * conjoining jamo - the jamo line is what tells the user whether the face can follow an
     * IME, which the bundled Pretendard cannot.
     */
    private void chooseHangulFont() {
        final java.util.List<Fonts.Face> faces = Fonts.hangulCatalog(mActivity);
        String current = mHost.hangulFamily();

        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(4), dp(8), 0);

        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];

        for (int i = 0; i < faces.size(); i++) {
            final Fonts.Face f = faces.get(i);
            LinearLayout row = new LinearLayout(mActivity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(11), dp(12), dp(11));
            row.setBackground(touchable());
            row.setClickable(true);

            TextView sample = new TextView(mActivity);
            sample.setText(i == 0 ? "한글 가나다 12" : "한글 가나다 ▲");
            sample.setTextSize(15);
            sample.setTextColor(TEXT);
            sample.setTypeface(faceForHangul(f));
            sample.setSingleLine(true);
            sample.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(sample);

            TextView name = new TextView(mActivity);
            // "자모" marks a face whose own cmap has conjoining jamo. The bundled Pretendard
            // does not - syllables only - and the platform fills the gap with a fallback face,
            // so the mark is about which font draws the text, not about whether it appears.
            name.setText(Fonts.faceHasJamo(mActivity, f) ? f.label + " · 자모" : f.label);
            name.setTextSize(11);
            name.setTextColor(f.id.equals(current) ? ACCENT : HINT);
            name.setPadding(0, 0, dp(10), 0);
            row.addView(name);

            if (f.id.equals(current)) row.addView(Chrome.icon(mActivity, "check", 16, false, null));

            row.setOnClickListener(v -> {
                mHost.onHangulFamily(f.id);
                if (holder[0] != null) holder[0].dismiss();
                render();
            });
            box.addView(row);
            if (i < faces.size() - 1) box.addView(hairline(dp(12), dp(0)));
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(mActivity);
        scroll.setFillViewport(true);
        scroll.addView(box);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(340)));

        holder[0] = new android.app.AlertDialog.Builder(mActivity)
                .setTitle("한글 글꼴")
                .setView(scroll)
                .setPositiveButton("다시 찾기", (d, w) -> {
                    Fonts.rescan();
                    render();
                    chooseHangulFont();
                })
                .setNeutralButton("설치", (d, w) -> installFont())
                .setNegativeButton("닫기", null)
                .create();
        holder[0].show();
    }

    /**
     * Every way a font gets in, in one dialog.
     *
     * <p>There are two real answers and they are not equally easy. The device picker works
     * but requires the file to already be on the phone, which means the user has to have
     * downloaded it in a browser first. Termux is the easier one: the daemon already runs
     * there, it has `curl`, and it can see {@code ~/.fonts} - so a URL typed here is fetched
     * on that side and handed over, which is one step instead of three apps.
     *
     * <p>The list is asked for on a background thread because it is a socket round trip and
     * a directory scan on the far end; doing it on the UI thread is the freeze that makes a
     * settings screen feel broken.
     */
    private void installFont() {
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(6), dp(8), 0);

        LinearLayout status = new LinearLayout(mActivity);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(dp(12), dp(4), dp(12), dp(8));
        box.addView(status);

        TextView loading = new TextView(mActivity);
        loading.setText("Termux에서 글꼴을 찾는 중…");
        loading.setTextColor(HINT);
        loading.setTextSize(12);
        status.addView(loading);

        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];

        LinearLayout device = new LinearLayout(mActivity);
        device.setOrientation(LinearLayout.HORIZONTAL);
        device.setGravity(Gravity.CENTER_VERTICAL);
        device.setPadding(dp(12), dp(12), dp(12), dp(12));
        device.setBackground(touchable());
        device.setClickable(true);
        device.addView(Chrome.icon(mActivity, "folder", 16, false, null));
        TextView deviceLabel = new TextView(mActivity);
        deviceLabel.setText("이 기기의 파일에서 고르기");
        deviceLabel.setTextColor(TEXT);
        deviceLabel.setTextSize(13);
        deviceLabel.setPadding(dp(10), 0, 0, 0);
        device.addView(deviceLabel);
        device.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            fontImport();
        });
        box.addView(device);
        box.addView(hairline(dp(12), dp(0)));

        LinearLayout url = new LinearLayout(mActivity);
        url.setOrientation(LinearLayout.HORIZONTAL);
        url.setGravity(Gravity.CENTER_VERTICAL);
        url.setPadding(dp(12), dp(12), dp(12), dp(12));
        url.setBackground(touchable());
        url.setClickable(true);
        url.addView(Chrome.icon(mActivity, "cloud", 16, false, null));
        TextView urlLabel = new TextView(mActivity);
        urlLabel.setText("URL로 받기 (Termux가 대신 내려받음)");
        urlLabel.setTextColor(TEXT);
        urlLabel.setTextSize(13);
        urlLabel.setPadding(dp(10), 0, 0, 0);
        url.addView(urlLabel);
        url.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            fontFromUrl();
        });
        box.addView(url);
        box.addView(hairline(dp(12), dp(0)));

        android.widget.ScrollView scroll = new android.widget.ScrollView(mActivity);
        scroll.addView(box);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));

        holder[0] = new android.app.AlertDialog.Builder(mActivity)
                .setTitle("글꼴 설치")
                .setView(scroll)
                .setNegativeButton("닫기", null)
                .create();
        holder[0].show();

        final android.app.AlertDialog dialog = holder[0];
        new Thread(() -> {
            final java.util.List<com.termux.terminal.DaemonClient.RemoteFont> remote;
            final String error;
            try {
                remote = com.termux.terminal.DaemonClient.fonts(
                        mHost.daemonHost(), mHost.daemonPort(), mHost.daemonToken(), 4000);
                error = null;
            } catch (com.termux.terminal.DaemonClient.Failure f) {
                mActivity.runOnUiThread(() -> reportFonts(status, loading, null, f.getMessage()));
                return;
            }
            mActivity.runOnUiThread(() -> reportFonts(status, loading, remote, error));
        }, "fterm-font-list").start();
    }

    /**
     * Replace the "looking…" line with what Termux has, or with why it could not be asked.
     *
     * <p>A daemon that cannot be reached is not an error worth a modal: the other two ways in
     * still work, so the message says so and stays out of the way.
     */
    private void reportFonts(LinearLayout status, TextView loading,
                             java.util.List<com.termux.terminal.DaemonClient.RemoteFont> fonts,
                             String error) {
        status.removeAllViews();
        if (error != null) {
            TextView t = new TextView(mActivity);
            t.setText("Termux 글꼴 목록을 가져오지 못했습니다.\n" + error);
            t.setTextColor(HINT);
            t.setTextSize(11);
            status.addView(t);
            return;
        }
        if (fonts == null || fonts.isEmpty()) {
            TextView t = new TextView(mActivity);
            t.setText("Termux 쪽에 글꼴이 없습니다. 아래 URL로 받거나, Termux에서 "
                    + "~/.fonts 에 .ttf를 넣으세요.");
            t.setTextColor(HINT);
            t.setTextSize(11);
            status.addView(t);
            return;
        }
        TextView head = new TextView(mActivity);
        head.setText("Termux에서 찾은 글꼴 " + fonts.size() + "개");
        head.setTextColor(HINT);
        head.setTextSize(11);
        status.addView(head);
        for (com.termux.terminal.DaemonClient.RemoteFont f : fonts) {
            LinearLayout row = new LinearLayout(mActivity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(touchable());
            row.setClickable(true);

            TextView name = new TextView(mActivity);
            name.setText(f.name());
            name.setTextColor(TEXT);
            name.setTextSize(12.5f);
            name.setSingleLine(true);
            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(name);

            TextView size = new TextView(mActivity);
            size.setText((f.size / 1024) + " KB");
            size.setTextColor(HINT);
            size.setTextSize(10.5f);
            row.addView(size);

            row.setOnClickListener(v -> pullFromTermux(f));
            status.addView(row);
        }
    }

    /** Fetch one Termux-side font and install it as if it had been picked locally. */
    private void pullFromTermux(com.termux.terminal.DaemonClient.RemoteFont f) {
        android.widget.Toast.makeText(mActivity, f.name() + " 받는 중…",
                android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final Fonts.Import result;
            try {
                byte[] data = com.termux.terminal.DaemonClient.font(
                        mHost.daemonHost(), mHost.daemonPort(), mHost.daemonToken(),
                        f.path, 8000);
                result = Fonts.install(mActivity, f.name(), data);
            } catch (com.termux.terminal.DaemonClient.Failure e) {
                mActivity.runOnUiThread(() -> showFontError(e.getMessage()));
                return;
            }
            mActivity.runOnUiThread(() -> applyImported(result));
        }, "fterm-font-get").start();
    }

    /** A URL for the daemon to download, because this app has no way to fetch one itself. */
    private void fontFromUrl() {
        prompt("글꼴 URL", "", "https://example.com/MyFont.ttf",
                "Termux가 대신 내려받아 ~/.fonts 에 저장한 뒤 앱으로 넘깁니다. "
                        + ".ttf/.otf로 끝나는 http(s) 주소여야 합니다.",
                v -> {
                    if (v.isEmpty()) return;
                    if (!v.startsWith("http://") && !v.startsWith("https://")) {
                        showFontError("http:// 또는 https:// 주소가 아닙니다.");
                        return;
                    }
                    android.widget.Toast.makeText(mActivity, "내려받는 중…",
                            android.widget.Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        final Fonts.Import result;
                        try {
                            String path = com.termux.terminal.DaemonClient.fetchFont(
                                    mHost.daemonHost(), mHost.daemonPort(), mHost.daemonToken(),
                                    v, 12000);
                            byte[] data = com.termux.terminal.DaemonClient.font(
                                    mHost.daemonHost(), mHost.daemonPort(), mHost.daemonToken(),
                                    path, 8000);
                            String name = path.substring(path.lastIndexOf('/') + 1);
                            result = Fonts.install(mActivity, name, data);
                        } catch (com.termux.terminal.DaemonClient.Failure e) {
                            mActivity.runOnUiThread(() -> showFontError(e.getMessage()));
                            return;
                        }
                        mActivity.runOnUiThread(() -> applyImported(result));
                    }, "fterm-font-url").start();
                });
    }

    private void showFontError(String message) {
        new android.app.AlertDialog.Builder(mActivity)
                .setTitle("글꼴")
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show();
    }

    /**
     * Install a font, select it, and reopen the picker it belongs in.
     *
     * <p>A Korean font is proportional, so the Latin list can never contain it - reopening
     * that list after installing one would show a list the new row is not in and read as a
     * failed install. The rule is the same one {@link Fonts#adopt} uses to accept the file.
     */
    private void applyImported(Fonts.Import imported) {
        if (imported.id == null) {
            showFontError(imported.reason);
            return;
        }
        boolean latin = Fonts.isLatinFace(imported.id);
        if (latin) mHost.onFontFamily(imported.id);
        else mHost.onHangulFamily(imported.id);
        render();
        if (latin) chooseFont();
        else chooseHangulFont();
    }

    /**
     * Import a font file the user picked, by copying it into app storage.
     *
     * <p>A system file picker, not a text field. The previous version asked for an absolute
     * path, which reads as the more powerful option and was the less capable one: since
     * Android 11 this app cannot open an arbitrary {@code /sdcard} path, so the field accepted
     * paths it could never load. The picker hands back a {@code content://} document that the
     * platform has already granted, and {@link Fonts#importFont} copies it somewhere the
     * terminal can read forever.
     *
     * <p>No separate result plumbing: {@link Activity#startActivityForResult} is deprecated but
     * the sheet is a Dialog, not a fragment host, and a contract launcher would need the same
     * hop back through the activity. The activity forwards the result here.
     */
    private static final int REQ_IMPORT_FONT = 0x4f4e;

    private void fontImport() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        // Any file, not font/*. The system picker resolves a filter against the MIME type
        // MediaStore recorded for each file, and a .ttf that arrived by adb or a file manager
        // often has none - filtered to font/*, the folder the user just filled shows as empty.
        // So the picker shows everything and the font check happens after the copy, where a
        // wrong pick is refused with a reason instead of never being offered.
        i.setType("*/*");
        try {
            mActivity.startActivityForResult(i, REQ_IMPORT_FONT);
        } catch (Throwable t) {
            new android.app.AlertDialog.Builder(mActivity)
                    .setTitle("글꼴")
                    .setMessage("파일 선택기를 열 수 없습니다.\n\n" + t)
                    .setPositiveButton("확인", null)
                    .show();
        }
    }

    /**
     * The result of {@link #fontImport()}: copy the pick, then apply it.
     *
     * <p>Called by the activity. A refused file says why - "nothing happened" after picking a
     * file is the response that makes a user think the feature is broken.
     */
    void onFontImportResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode != REQ_IMPORT_FONT) return;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        // The reason, not a shrug: a wrong file and a broken picker need different responses
        // from the user, and "could not read that" reads as the latter.
        applyImported(Fonts.importFont(mActivity, data.getData()));
    }

    /** The actual face for one catalog entry, so the sample is the font and not a lookalike. */
    private Typeface faceFor(Fonts.Face f) {
        if (f.path == null) {
            try {
                return Typeface.createFromAsset(mActivity.getAssets(), Fonts.ASSET_PATH);
            } catch (Throwable ignored) {
            }
        } else {
            try {
                return Typeface.createFromFile(f.path);
            } catch (Throwable ignored) {
            }
        }
        return Typeface.MONOSPACE;
    }

    /** The actual Korean face for a catalog entry; the first row is the bundled one. */
    private Typeface faceForHangul(Fonts.Face f) {
        if (f.path == null) {
            try {
                return Typeface.createFromAsset(mActivity.getAssets(), Fonts.HANGUL_PATH);
            } catch (Throwable ignored) {
            }
        } else {
            try {
                return Typeface.createFromFile(f.path);
            } catch (Throwable ignored) {
            }
        }
        return Typeface.DEFAULT;
    }

    private void editStartDir() {
        prompt("시작 디렉터리", mHost.startDir(), "/data/data/com.termux/files/home",
                "비우면 데몬의 홈 디렉터리에서 시작합니다. 존재하지 않는 경로를 적으면 "
                        + "홈으로 대체됩니다.",
                v -> {
                    mHost.onStartDir(v);
                    render();
                });
    }

    private void editStartShell() {
        prompt("시작 셸", mHost.startShell(), "/data/data/com.termux/files/usr/bin/bash",
                "데몬이 허용한 경로만 실행됩니다. 비우면 데몬 기본 셸을 씁니다.",
                v -> {
                    mHost.onStartShell(v);
                    render();
                });
    }

    /** 모양: the same rows as WT's appearance page, restricted to what this app has. */
    private void appearancePage(LinearLayout col) {
        LinearLayout card = card(col);

        LinearLayout sizeRow = new LinearLayout(mActivity);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        sizeRow.setPadding(0, dp(10), 0, dp(10));

        LinearLayout text = new LinearLayout(mActivity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(mActivity);
        t.setText("글자 크기");
        t.setTextColor(TEXT);
        t.setTextSize(14);
        UiFont.apply(t, false);
        text.addView(t);
        TextView sub = new TextView(mActivity);
        sub.setText(mHost.fontSizeDp() + "dp");
        sub.setTextColor(HINT);
        sub.setTextSize(11);
        text.addView(sub);
        sizeRow.addView(text);

        SeekBar size = new SeekBar(mActivity);
        size.setMax(FontZoom.MAX_DP - FontZoom.MIN_DP);
        size.setProgress(Math.max(0, mHost.fontSizeDp() - FontZoom.MIN_DP));
        size.setLayoutParams(new LinearLayout.LayoutParams(dp(150),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) {
                    mHost.onFontSize(p + FontZoom.MIN_DP);
                    sub.setText((p + FontZoom.MIN_DP) + "dp");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        sizeRow.addView(size);
        card.addView(cardRow(sizeRow, false));

        card.addView(valueRow("글꼴", "라틴 문자에 쓰는 고정폭 글꼴",
                Fonts.labelOf(mActivity, mHost.fontFamily()),
                this::chooseFont, false));
        card.addView(valueRow("한글 글꼴", "한 글자가 정확히 두 칸을 차지하게 맞춤",
                Fonts.labelOfHangul(mActivity, mHost.hangulFamily()),
                this::chooseHangulFont, false));
        card.addView(valueRow("색 구성표", "미리보기와 16색 팔레트",
                Prefs.SCHEME_NAMES[Math.max(0, Math.min(Prefs.SCHEME_NAMES.length - 1,
                        mHost.scheme()))],
                () -> open("schemes"), false));
        card.addView(toggleCardRow("키 바 표시", "화면 아래 두 줄짜리 키 바",
                mHost.keyBarVisible(), mHost::onKeyBarVisible, false));

        // WT's cursor page, folded into this one: three shapes and a blink switch.
        card.addView(valueRow("커서 모양", "블록 · 밑줄 · 세로 막대",
                CURSOR_NAMES[Math.max(0, Math.min(CURSOR_NAMES.length - 1, mHost.cursorStyle()))],
                this::chooseCursor, false));
        card.addView(toggleCardRow("커서 깜빡임", "끄면 항상 보입니다",
                mHost.cursorBlink(), mHost::onCursorBlink, true));
        card.addView(valueRow("커서 색", "구성표를 따르지 않고 직접 지정",
                mHost.cursorColor() == 0 ? "구성표 따름"
                        : String.format("#%06X", mHost.cursorColor() & 0xffffff),
                this::chooseCursorColor, false));
        card.addView(valueRow("탭 아이콘", "탭 제목 앞의 작은 표시",
                TabIcons.isGlyph(mHost.tabIcon())
                        ? TabIcons.GLYPH_NAMES[glyphIndexOf(mHost.tabIcon())]
                        : mHost.tabIcon(),
                this::chooseTabIcon, false));
        card.addView(toggleCardRow("레트로 효과", "형광 글로우와 주사선 (Windows Terminal)",
                mHost.retroEffect(), mHost::onRetroEffect, true));
    }

    private static final String[] CURSOR_NAMES = {"블록", "밑줄", "세로 막대"};

    private void chooseCursor() {
        new android.app.AlertDialog.Builder(mActivity)
                .setTitle("커서 모양")
                .setSingleChoiceItems(CURSOR_NAMES, mHost.cursorStyle(), (d, which) -> {
                    mHost.onCursorStyle(which);
                    d.dismiss();
                    render();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * The cursor colour: a hex field plus a chip, and "구성표 따름" to go back.
     *
     * <p>Not a colour wheel. The value a user wants is almost always one they read off
     * something else - a scheme file, a screenshot, another terminal - and that arrives as a
     * hex code. The chip is the only confirmation needed, and the reset is a named button
     * rather than an empty field, because "no colour" is a state worth choosing on purpose.
     */
    private void chooseCursorColor() {
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        final int current = mHost.cursorColor();
        int seed = current != 0 ? current : mHost.customSlots()[18];

        final View chip = new View(mActivity);
        chip.setBackground(Chrome.plate(seed, 4));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        clp.bottomMargin = dp(10);
        chip.setLayoutParams(clp);
        box.addView(chip);

        final android.widget.EditText hex = new android.widget.EditText(mActivity);
        hex.setText(current == 0 ? "" : String.format("#%06X", current & 0xffffff));
        hex.setHint("비우면 구성표를 따릅니다");
        hex.setHintTextColor(HINT);
        hex.setSingleLine(true);
        hex.setTextSize(15);
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setTextColor(TEXT);
        box.addView(hex);

        hex.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable e) {
                Integer c = parseHex(e.toString());
                chip.setBackground(Chrome.plate(c != null ? c : seed, 4));
            }
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
        });

        new android.app.AlertDialog.Builder(mActivity)
                .setTitle("커서 색")
                .setView(box)
                .setPositiveButton("저장", (d, w) -> {
                    String t = hex.getText().toString().trim();
                    Integer c = parseHex(t);
                    if (t.isEmpty() || t.equals("#")) {
                        mHost.onCursorColor(0);   // empty means "follow the scheme"
                    } else if (c == null) {
                        // A typo must not silently do nothing; keeping the old value while
                        // the sheet reopens is exactly the "the setting is broken" report.
                        android.widget.Toast.makeText(mActivity, "색 형식이 아닙니다: " + t,
                                android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        mHost.onCursorColor(c);
                    }
                    render();
                })
                .setNegativeButton("취소", null)
                .setNeutralButton("구성표 따름", (d, w) -> {
                    mHost.onCursorColor(0);
                    render();
                })
                .show();
    }

    private static int glyphIndexOf(String id) {
        for (int i = 0; i < TabIcons.GLYPHS.length; i++) {
            if (TabIcons.GLYPHS[i].equals(id)) return i;
        }
        return 0;
    }

    /**
     * An icon picker: a grid of every glyph, then an option to type one.
     *
     * <p>A grid, not a list. The set is thirty-six because a tab mark is how a glance tells
     * an ssh session from a database console, and a list that long is a scroll with no
     * overview - the whole point of choosing an icon is seeing the candidates at once. Each
     * cell draws the real mark with the same {@link TabIcons#build} the tab strip uses, so
     * nothing here can promise a size the tab will not deliver.
     *
     * <p>Shared by the global tab icon and the per-profile icon. The two differ only in what
     * "no icon" means, which is the {@code clearLabel} parameter: the global setting has a
     * real default to fall back to, a profile has "use the global one".
     *
     * @param current    the value in force, for the tick
     * @param clearLabel the label for "no icon", or null when there is nothing to clear
     * @param onPick     called with a glyph id or literal text; the dialog has already closed
     */
    private void pickIcon(String title, String current, String clearLabel,
                          java.util.function.Consumer<String> onPick) {
        final int columns = 5;
        // 5 columns at 62dp: wide enough for "데이터베이스" on two lines, narrow enough that
        // the grid still fits a phone dialog without overflowing it.
        final int CELL_DP = 62;
        final java.util.List<String> ids = new java.util.ArrayList<>();
        final java.util.List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < TabIcons.GLYPHS.length; i++) {
            ids.add(TabIcons.GLYPHS[i]);
            names.add(TabIcons.GLYPH_NAMES[i]);
        }
        // The default is a cell like any other, at the end. Its label is not the mark itself -
        // the cell already draws ">_" above the label, and printing it twice reads as a
        // duplicated row rather than as a name.
        ids.add(TabIcons.DEFAULT_ID);
        names.add("기본");
        if (clearLabel != null) {
            // An empty id. TabIcons.build would draw the default for it, so the cell would
            // lie about what the choice means; the label carries the meaning instead.
            ids.add("");
            names.add(clearLabel);
        }

        LinearLayout grid = new LinearLayout(mActivity);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(6), 0, dp(6));
        // MATCH_PARENT so each row is as wide as the dialog. The rows are centred, and a
        // wrap_content row has no width to centre inside: the final row holds one cell, so it
        // would sit against the left edge while every other row is centred.
        grid.setLayoutParams(new android.widget.ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];
        LinearLayout line = null;
        for (int i = 0; i < ids.size(); i++) {
            if (i % columns == 0) {
                line = new LinearLayout(mActivity);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER);
                line.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                grid.addView(line);
            }
            final String id = ids.get(i);
            // One comparison covers every case: a glyph id matches only its own cell, ">_"
            // matches the default cell, "" matches the "no icon" cell, and a user-typed mark
            // matches nothing - which is honest, since it is not one of the cells on screen.
            boolean chosen = id.equals(current);

            LinearLayout cell = new LinearLayout(mActivity);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(4), dp(6), dp(4), dp(5));
            cell.setBackground(chosen ? touchableSelected() : touchable());
            // A fixed width, not weight. The dialog wraps this view in its own container, and
            // weight inside a container whose width is resolved later collapses: the last
            // row's lone cell came out half-size with its label hanging outside the fill.
            // A dp width is the same on every row and needs no filler views.
            cell.setLayoutParams(new LinearLayout.LayoutParams(dp(CELL_DP),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.setOnClickListener(v -> {
                holder[0].dismiss();
                onPick.accept(id);
            });

            // The mark sits in a tab-sized slot, so a cell is the tab at a glance.
            LinearLayout slot = new LinearLayout(mActivity);
            slot.setGravity(Gravity.CENTER);
            slot.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
            slot.addView(id.isEmpty()
                    ? absentMark(chosen)
                    : TabIcons.build(mActivity, id, chosen ? TEXT : HINT));
            cell.addView(slot);

            TextView label = new TextView(mActivity);
            label.setText(names.get(i));
            label.setTextColor(chosen ? ACCENT : HINT);
            label.setTextSize(9.5f);
            // Two lines: a name like "데이터베이스" is one word and cannot wrap, so a
            // single-line label truncates it to "데이터베…" and the name stops being a name.
            label.setSingleLine(false);
            label.setMaxLines(2);
            label.setGravity(Gravity.CENTER);
            label.setMaxWidth(dp(CELL_DP - 4));
            cell.addView(label);

            line.addView(cell);
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(mActivity);
        scroll.setFillViewport(true);
        scroll.addView(grid);

        holder[0] = new android.app.AlertDialog.Builder(mActivity)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("직접 입력", (d, w) -> promptIconText(title, current, onPick))
                .setNegativeButton("취소", null)
                .create();
        holder[0].show();
    }

    /**
     * The mark for "no icon": a dashed outline of the slot.
     *
     * <p>An empty cell would read as a rendering failure, and the default mark would read as
     * "this is the mark" - which is the opposite of what choosing it does.
     */
    private View absentMark(boolean bright) {
        View v = new View(mActivity);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(0x00000000);
        d.setStroke(Math.max(1, dp(1)), bright ? ACCENT : 0xff55595f);
        d.setCornerRadius(dp(4));
        v.setBackground(d);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(16), dp(20)));
        return v;
    }

    /** A one-line prompt for a custom mark; the slot clips long text, so the note says so. */
    private void promptIconText(String title, String current,
                                java.util.function.Consumer<String> onPick) {
        prompt(title, TabIcons.isGlyph(current) ? "" : current, ">_",
                "한두 글자가 가장 잘 맞습니다. 길면 작게 그려집니다.",
                v -> onPick.accept(v.trim()));
    }

    private void chooseTabIcon() {
        pickIcon("탭 아이콘", mHost.tabIcon(), null, id -> {
            mHost.onTabIcon(id);
            render();
        });
    }

    /**
     * 색 구성표, laid out like the reference: a terminal preview first, then the 16 colours as
     * eight rows of two swatches, then the list of schemes with the active one marked.
     *
     * <p>The preview is not decoration - it is the only place the foreground, background and
     * all eight normal/bright pairs are shown together, which is what makes a scheme's
     * legibility judgeable before it is applied.
     */
    private void schemesPage(LinearLayout col) {
        int[] slots = mHost.customSlots();
        col.addView(preview(mHost.scheme(), slots));

        LinearLayout colours = card(col);
        colours.addView(cardTitle("색"));
        int[] table = Schemes.colors(mHost.scheme(), slots);
        String[] names = {"검정", "빨강", "녹색", "노랑", "파랑", "보라색", "녹청색", "흰색"};
        final boolean editable = mHost.scheme() == Schemes.CUSTOM_INDEX;
        for (int i = 0; i < names.length; i++) {
            colours.addView(swatchRow(names[i], table[i], table[i + 8], i == names.length - 1,
                    editable, i, slots));
        }

        LinearLayout list = card(col);
        list.addView(cardTitle("구성표"));
        for (int i = 0; i < Prefs.SCHEME_NAMES.length; i++) {
            final int idx = i;
            list.addView(schemeRow(Prefs.SCHEME_NAMES[i], mHost.scheme() == idx,
                    i == Prefs.SCHEME_NAMES.length - 1, () -> {
                        mHost.onScheme(idx);
                        render();          // so the preview and the palette follow the pick
                    }));
        }

        hint(col, editable
                ? "색을 누르면 그 자리의 값을 바꿀 수 있습니다. 왼쪽이 보통색, "
                        + "오른쪽이 밝은색입니다."
                : "색 구성표는 앱에 내장되어 있습니다. \"사용자 지정\"을 고르면 "
                        + "16색을 직접 정할 수 있습니다.");
    }

    /**
     * A colour editor for one slot: a hex field, a live chip, and a preview of the result.
     *
     * <p>A full HSV picker would be another screen of custom drawing; the terminal's own
     * vocabulary is the hex code, and every colour a user would paste from a scheme file is
     * written that way. The chip exists so a typo is visible before it is committed.
     */
    private void editSlot(final int slot, int current, final int[] slots) {
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        final View chip = new View(mActivity);
        chip.setBackground(Chrome.plate(current | 0xff000000, 4));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        clp.bottomMargin = dp(10);
        chip.setLayoutParams(clp);
        box.addView(chip);

        final android.widget.EditText hex = new android.widget.EditText(mActivity);
        hex.setText(String.format("#%06X", current & 0xffffff));
        hex.setSingleLine(true);
        hex.setTextSize(15);
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setTextColor(TEXT);
        hex.setHintTextColor(HINT);
        box.addView(hex);

        TextView label = new TextView(mActivity);
        label.setText(slot < 16 ? (slot < 8 ? "보통색 " : "밝은색 ") + (slot % 8 + 1) + "번"
                : slot == 16 ? "전경" : slot == 17 ? "배경" : "커서");
        label.setTextColor(HINT);
        label.setTextSize(11);
        label.setPadding(0, dp(6), 0, 0);
        box.addView(label);

        hex.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable e) {
                Integer c = parseHex(e.toString());
                if (c != null) chip.setBackground(Chrome.plate(c, 4));
            }
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
        });

        new android.app.AlertDialog.Builder(mActivity)
                .setTitle("색 지정")
                .setView(box)
                .setPositiveButton("저장", (d, w) -> {
                    Integer c = parseHex(hex.getText().toString());
                    if (c != null) {
                        mHost.onCustomSlot(slot, c);
                        slots[slot] = c;
                        render();
                    }
                })
                .setNegativeButton("취소", null)
                .setNeutralButton("전경으로", (d, w) -> {
                    mHost.onCustomSlot(slot, slots[16]);
                    slots[slot] = slots[16];
                    render();
                })
                .show();
    }

    /** Accepts "#rrggbb", "rrggbb" and "#rgb"; returns null when it is not a colour. */
    static Integer parseHex(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 3) {
            t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1)
                    + t.charAt(2) + t.charAt(2);
        }
        if (t.length() != 6) return null;
        try {
            return 0xff000000 | Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A miniature terminal: the scheme's own background, and the eight colour names written in
     * the colours they name, normal then bright, one row each.
     */
    private View preview(int schemeIndex, int[] slots) {
        int[] table = Schemes.colors(schemeIndex, slots);
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(Chrome.plate(Schemes.background(schemeIndex, slots), 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        box.setLayoutParams(lp);

        String[] names = {"전경", "빨강", "녹색", "노랑", "파랑", "보라색", "녹청색", "흰색"};
        int[] normal = {table[TextStyle.COLOR_INDEX_FOREGROUND],
                table[1], table[2], table[3], table[4], table[5], table[6], table[7]};
        int[] bright = {table[15], table[9], table[10], table[11], table[12], table[13],
                table[14], table[15]};

        for (int i = 0; i < names.length; i++) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            int s = sb.length();
            sb.append(names[i]);
            sb.setSpan(new ForegroundColorSpan(normal[i]), s, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append("  ");
            s = sb.length();
            sb.append("밝은 ").append(names[i]);
            sb.setSpan(new ForegroundColorSpan(bright[i]), s, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            TextView line = new TextView(mActivity);
            line.setText(sb);
            line.setTextSize(11);
            line.setTypeface(Typeface.MONOSPACE);
            line.setIncludeFontPadding(false);
            line.setLineSpacing(dp(1), 1f);
            box.addView(line);
        }
        return box;
    }

    /**
     * One palette entry: name, its normal swatch, its bright swatch.
     *
     * <p>When the custom scheme is active the two swatches become tappable, because that is
     * where editing lives - WT's colour page is the same idea with a picker instead of a hex
     * field. The row itself stays a row: a value you can change, not a form control.
     */
    private View swatchRow(String name, int normal, int bright, boolean last,
                           boolean editable, final int base, final int[] slots) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        TextView t = new TextView(mActivity);
        t.setText(name);
        t.setTextColor(TEXT);
        t.setTextSize(13);
        UiFont.apply(t, false);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);

        if (editable) {
            final int slot = base;
            View n = swatch(normal);
            n.setOnClickListener(v -> editSlot(slot, slots[slot], slots));
            row.addView(n);
            View b = swatch(bright);
            b.setOnClickListener(v -> editSlot(slot + 8, slots[slot + 8], slots));
            row.addView(b);
        } else {
            row.addView(swatch(normal));
            row.addView(swatch(bright));
        }
        return cardRow(row, last);
    }

    /**
     * A colour chip. Black needs a hairline or it disappears into the card, and a stroke on
     * every chip is what WT draws anyway - the border is part of the swatch, not a fallback.
     */
    private View swatch(int color) {
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(3));
        d.setStroke(Math.max(1, dp(0.7f)), CARD_HI);
        View v = new View(mActivity);
        v.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(20));
        lp.leftMargin = dp(8);
        v.setLayoutParams(lp);
        return v;
    }

    /** A scheme as a selectable row: accent bar when active, name, check glyph. */
    private View schemeRow(String name, boolean active, boolean last, Runnable onPick) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), 0, 0, 0);
        row.setClickable(true);
        row.setOnClickListener(v -> onPick.run());
        if (!active) row.setBackground(touchable());

        View bar = new View(mActivity);
        bar.setBackgroundColor(active ? ACCENT : 0x00000000);
        bar.setLayoutParams(new LinearLayout.LayoutParams(Math.max(2, dp(2)), dp(30)));
        row.addView(bar);

        TextView t = new TextView(mActivity);
        t.setText(name);
        t.setTextColor(active ? TEXT : 0xffc8ccd4);
        t.setTextSize(13.5f);
        t.setPadding(dp(10), dp(9), 0, dp(9));
        UiFont.apply(t, active);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);

        if (active) row.addView(Chrome.icon(mActivity, "check", 18, false, null));
        return cardRow(row, last);
    }

    private void interactionPage(LinearLayout col) {
        LinearLayout card = card(col);
        card.addView(toggleCardRow("터미널 벨 · 진동", "BEL 문자를 받으면 짧게 진동합니다",
                mHost.bellVibrate(), on -> mHost.onBell(on, mHost.bellSound()), false));
        card.addView(toggleCardRow("터미널 벨 · 소리", "무음 모드가 아닐 때만 울립니다",
                mHost.bellSound(), on -> mHost.onBell(mHost.bellVibrate(), on), false));
        card.addView(toggleCardRow("뒤로 가기 = Esc", "vim·less에서 뒤로 가기가 취소로 동작",
                mHost.backIsEscape(), mHost::onBackIsEscape, true));

        hint(col, "길게 누르기: 텍스트 선택 · 탭 길게 누르기: 탭 닫기 · 키 바 가장자리: F1~F12");
    }

    private void aboutPage(LinearLayout col) {
        LinearLayout card = card(col);
        card.addView(valueRow("세션", mHost.statusLine(), null, null, false));
        card.addView(valueRow("데몬", "127.0.0.1 의 PTY 서버, 토큰 인증", null, null, false));
        card.addView(valueRow("렌더링", "xterm-256color · 한글 2칸 폭", null, null, true));

        hint(col, "fterm — Android 위의 Windows Terminal 모양을 목표로 하는 터미널. "
                + "셸은 Termux의 데몬이 소유하고, 앱은 화면과 입력만 맡습니다.");
    }

    // ------------------------------------------------------------------ cards and rows

    /** A WT card: rounded, one step lighter than the window, with a small inset. */
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(mActivity);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Chrome.plate(CARD, 8));
        c.setPadding(dp(12), dp(2), dp(12), dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        parent.addView(c, lp);
        return c;
    }

    private View cardTitle(String title) {
        TextView t = new TextView(mActivity);
        t.setText(title);
        t.setTextColor(HINT);
        t.setTextSize(11);
        t.setLetterSpacing(0.06f);
        t.setPadding(0, dp(9), 0, dp(7));
        UiFont.apply(t, true);
        return t;
    }

    /** Appends a row plus its hairline; the last row of a card has no trailing rule. */
    private View cardRow(View row, boolean last) {
        LinearLayout wrap = new LinearLayout(mActivity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (!last) wrap.addView(hairline(0, 0));
        return wrap;
    }

    /**
     * A row that names a value: title and hint on the left, the value on the right.
     *
     * <p>This is the shape of every row on WT's 시작 page, and the reason it does not read as
     * a form: the value carries its own affordance (a chevron or a toggle) instead of sitting
     * in a separate button column.
     */
    private View valueRow(String name, String hint, String value, Runnable onClick,
                          boolean last) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        if (onClick != null) {
            row.setClickable(true);
            row.setBackground(touchable());
            row.setOnClickListener(v -> onClick.run());
        }

        LinearLayout text = new LinearLayout(mActivity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(mActivity);
        t.setText(name);
        t.setTextColor(TEXT);
        t.setTextSize(13.5f);
        UiFont.apply(t, false);
        text.addView(t);

        if (hint != null) {
            TextView h = new TextView(mActivity);
            h.setText(hint);
            h.setTextColor(HINT);
            h.setTextSize(10.5f);
            h.setLineSpacing(dp(1), 1f);
            text.addView(h);
        }
        row.addView(text);

        if (value != null) {
            TextView v = new TextView(mActivity);
            v.setText(value);
            v.setTextColor(onClick == null ? HINT : TEXT);
            v.setTextSize(12);
            v.setSingleLine(true);
            v.setGravity(Gravity.END);
            v.setPadding(dp(10), 0, 0, 0);
            UiFont.apply(v, false);
            row.addView(v);
        }
        if (onClick != null) {
            View more = Chrome.icon(mActivity, "right", 15, true, null);
            row.addView(more);
        }
        return cardRow(row, last);
    }

    private View toggleCardRow(String name, String hint, boolean checked, Chrome.OnToggle on,
                               boolean last) {
        return toggled(name, hint, checked, on, last);
    }

    /** A card row whose control is the sheet's own toggle, right-aligned. */
    private View toggled(String name, String hint, boolean checked, Chrome.OnToggle on,
                         boolean last) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout text = new LinearLayout(mActivity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(mActivity);
        t.setText(name);
        t.setTextColor(TEXT);
        t.setTextSize(13.5f);
        UiFont.apply(t, false);
        text.addView(t);
        if (hint != null) {
            TextView h = new TextView(mActivity);
            h.setText(hint);
            h.setTextColor(HINT);
            h.setTextSize(10.5f);
            text.addView(h);
        }
        row.addView(text);
        row.addView(Chrome.toggle(mActivity, checked, on));
        return cardRow(row, last);
    }

    /** A paragraph of gray text under the cards, where WT puts its explanatory notes. */
    private void hint(LinearLayout parent, String text) {
        TextView t = new TextView(mActivity);
        t.setText(text);
        t.setTextColor(HINT);
        t.setTextSize(10.5f);
        t.setLineSpacing(dp(2), 1f);
        t.setPadding(dp(4), dp(2), dp(4), 0);
        parent.addView(t);
    }

    private View hairline(int leftInset, int rightInset) {
        View line = new View(mActivity);
        line.setBackgroundColor(HAIRLINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f)));
        lp.leftMargin = leftInset;
        lp.rightMargin = rightInset;
        line.setLayoutParams(lp);
        return line;
    }

    /** The platform ripple, so a tap is acknowledged without repainting the card. */
    private android.graphics.drawable.Drawable touchable() {
        TypedValue tv = new TypedValue();
        if (mActivity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, tv, true)) {
            return mActivity.getDrawable(tv.resourceId);
        }
        return null;
    }

    /** The ripple, plus a quiet fill so the chosen cell is visible before it is touched. */
    private android.graphics.drawable.Drawable touchableSelected() {
        android.graphics.drawable.Drawable base = touchable();
        android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
        fill.setColor(0x224a9eff);
        fill.setCornerRadius(dp(8));
        if (base == null) return fill;
        return new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{fill, base});
    }

    private int dp(float v) {
        Resources r = mActivity.getResources();
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, r.getDisplayMetrics()));
    }
}
