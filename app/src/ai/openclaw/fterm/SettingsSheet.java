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

        /** The cursor shape: 0 block, 1 underline, 2 bar. */
        int cursorStyle();

        void onCursorStyle(int style);

        boolean cursorBlink();

        void onCursorBlink(boolean on);

        /** The Latin face id; see Fonts.LATIN_IDS. */
        String fontFamily();

        void onFontFamily(String id);

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
                final Profiles.Profile p = list.get(i);
                card.addView(valueRow(p.name.isEmpty() ? "(이름 없음)" : p.name, p.subtitle(),
                        null, () -> editProfile(p, false), i == list.size() - 1));
            }
        }

        card.addView(valueRow("프로필 추가", "예: ssh 서버 이름과 명령", "+",
                () -> editProfile(new Profiles.Profile("", "", "", ""), true), true));

        hint(col, "줄을 누르면 편집합니다. 탭을 열 때는 왼쪽 위 메뉴 첫 블록에서 "
                + "프로필을 고르세요. 명령은 셸이 뜬 뒤에 입력되므로 ssh가 로그인 셸의 키와 "
                + "설정을 그대로 씁니다.");
    }

    /** The four fields, edited as one dialog: they are one thing. */
    private void editProfile(final Profiles.Profile existing, final boolean isNew) {
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);

        final android.widget.EditText name = field(existing.name, "예: 서버");
        final android.widget.EditText dir = field(existing.dir, "/data/data/com.termux/files/home");
        final android.widget.EditText shell = field(existing.shell, "/data/data/com.termux/files/usr/bin/bash");
        final android.widget.EditText command = field(existing.command, "ssh myhost");

        box.addView(label("이름"));
        box.addView(name);
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
                            command.getText().toString().trim());
                    java.util.List<Profiles.Profile> list = mHost.profiles();
                    if (isNew) {
                        list.add(p);
                    } else {
                        // Editing in place keeps the menu order the user built.
                        for (int i = 0; i < list.size(); i++) {
                            if (list.get(i) == existing) { list.set(i, p); break; }
                        }
                    }
                    mHost.onProfiles(list);
                    render();
                })
                .setNegativeButton("취소", null);
        if (!isNew) {
            b.setNeutralButton("삭제", (d, w) -> {
                java.util.List<Profiles.Profile> list = mHost.profiles();
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) == existing) { list.remove(i); break; }
                }
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

        card.addView(valueRow("기본 프로필", "새 탭을 열 때 쓰는 프로필", "shell", null, false));
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
     * The Latin face picker. Each candidate is rendered in itself, so the choice is made by
     * looking rather than by reading a name - the only honest way to pick a monospace face.
     */
    private void chooseFont() {
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(4), dp(8), 0);

        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];
        String current = mHost.fontFamily();
        for (int i = 0; i < Fonts.LATIN_NAMES.length; i++) {
            final String id = Fonts.LATIN_IDS[i];
            LinearLayout row = new LinearLayout(mActivity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setBackground(touchable());
            row.setClickable(true);

            TextView sample = new TextView(mActivity);
            sample.setText("Mmm 0123 한");
            sample.setTextSize(15);
            sample.setTextColor(TEXT);
            sample.setTypeface(previewFace(i));
            sample.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(sample);

            TextView name = new TextView(mActivity);
            name.setText(Fonts.LATIN_NAMES[i]);
            name.setTextSize(11);
            name.setTextColor(HINT);
            name.setPadding(0, 0, dp(10), 0);
            row.addView(name);

            if (id.equals(current)) row.addView(Chrome.icon(mActivity, "check", 16, false, null));

            row.setOnClickListener(v -> {
                mHost.onFontFamily(id);
                if (holder[0] != null) holder[0].dismiss();
                render();
            });
            box.addView(row);
            if (i < Fonts.LATIN_NAMES.length - 1) box.addView(hairline(dp(12), dp(0)));
        }

        holder[0] = new android.app.AlertDialog.Builder(mActivity)
                .setTitle("글꼴")
                .setView(box)
                .setNegativeButton("닫기", null)
                .show();
    }

    /** The actual face for one candidate, so the sample is the font and not a lookalike. */
    private Typeface previewFace(int index) {
        Typeface face = null;
        if (Fonts.LATIN_PATHS[index] != null) {
            try {
                face = Typeface.createFromFile(Fonts.LATIN_PATHS[index]);
            } catch (Throwable ignored) {
            }
        }
        if (face == null) {
            try {
                face = Typeface.createFromAsset(mActivity.getAssets(),
                        Fonts.ASSET_PATH);
            } catch (Throwable ignored) {
            }
        }
        return face != null ? face : Typeface.MONOSPACE;
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
        size.setMax(22);
        size.setProgress(Math.max(0, mHost.fontSizeDp() - 6));
        size.setLayoutParams(new LinearLayout.LayoutParams(dp(150),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) {
                    mHost.onFontSize(p + 6);
                    sub.setText((p + 6) + "dp");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        sizeRow.addView(size);
        card.addView(cardRow(sizeRow, false));

        card.addView(valueRow("글꼴", "라틴 문자에 쓰는 고정폭 글꼴",
                Fonts.LATIN_NAMES[Fonts.latinIndexOf(mHost.fontFamily())],
                this::chooseFont, false));
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

    private int dp(float v) {
        Resources r = mActivity.getResources();
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, r.getDisplayMetrics()));
    }
}
