package ai.openclaw.fterm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * The settings sheet, in the shape Windows Terminal uses.
 *
 * <p>WT's settings are a flat list of labelled rows: a section heading in small caps, then one
 * row per setting with its name on the left and its control on the right, separated by hairlines
 * and nothing else. No cards, no coloured buttons, no dialog-per-value. The earlier version here
 * was a stack of full-width Android {@link android.widget.Button}s and {@link android.widget.CheckBox}es,
 * which is the default look of an Android dialog and reads as a form rather than as a
 * configuration surface.
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

        boolean bellVibrate();

        boolean bellSound();

        void onBell(boolean vibrate, boolean sound);

        boolean keyBarVisible();

        void onKeyBarVisible(boolean visible);

        /** e.g. "3 tabs · 127.0.0.1:8791" */
        String statusLine();
    }

    private static final int BG = 0xff1f2126;        // the sheet's surface
    private static final int TEXT = 0xffe8eaee;
    private static final int HINT = 0xff8a8f99;
    private static final int HAIRLINE = 0xff2c2f36;
    private static final int ACCENT = 0xff4a9eff;

    private final Activity mActivity;
    private final Host mHost;
    private android.app.Dialog mDialog;

    SettingsSheet(Activity activity, Host host) {
        mActivity = activity;
        mHost = host;
    }

    void show() {
        ScrollView sv = new ScrollView(mActivity);
        sv.setBackgroundColor(BG);
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(4), dp(18), dp(18));
        box.setBackgroundColor(BG);
        sv.addView(box);

        // ---- appearance
        section(box, "모양");

        box.addView(row("글자 크기", mHost.fontSizeDp() + "dp", sizeControl()));

        // ---- colour scheme: one row per scheme, the active one marked with an accent bar
        box.addView(row("색상 구성", null, null));
        for (int i = 0; i < Prefs.SCHEME_NAMES.length; i++) {
            final int idx = i;
            box.addView(schemeRow(Prefs.SCHEME_NAMES[i], mHost.scheme() == idx, () -> {
                mHost.onScheme(idx);
                show();                       // redraw so the accent bar moves
            }));
        }

        // ---- behaviour
        section(box, "동작");
        box.addView(toggleRow("터미널 벨 · 진동", mHost.bellVibrate(),
                on -> mHost.onBell(on, mHost.bellSound())));
        box.addView(toggleRow("터미널 벨 · 소리", mHost.bellSound(),
                on -> mHost.onBell(mHost.bellVibrate(), on)));
        box.addView(toggleRow("키 바 표시", mHost.keyBarVisible(), mHost::onKeyBarVisible));

        // ---- about
        section(box, "정보");
        TextView about = new TextView(mActivity);
        about.setText(mHost.statusLine() + "\n길게 누르기: 텍스트 선택 · 탭 길게: 닫기");
        about.setTextColor(HINT);
        about.setTextSize(12);
        about.setLineSpacing(dp(3), 1f);
        about.setPadding(0, dp(4), 0, 0);
        box.addView(about);

        mDialog = new AlertDialog.Builder(mActivity)
                .setTitle("설정")
                .setView(sv)
                .setPositiveButton("닫기", null)
                .create();
        mDialog.show();
        // Match the sheet's surface instead of the platform's white card; on OneUI the
        // default is light, which is what made this look unlike the terminal behind it.
        android.view.Window w = mDialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(Chrome.plate(BG, 10));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (mActivity.getResources().getDisplayMetrics().heightPixels * 0.78f));
        }
    }

    // ------------------------------------------------------------------ rows

    /** A small all-caps heading with a hairline under it, like WT's section labels. */
    private void section(LinearLayout parent, String title) {
        if (parent.getChildCount() > 0) {
            View gap = new View(mActivity);
            gap.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(10)));
            parent.addView(gap);
        }
        TextView t = new TextView(mActivity);
        t.setText(title.toUpperCase());
        t.setTextColor(HINT);
        t.setTextSize(10);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, dp(4), 0, dp(4));
        UiFont.apply(t, true);
        parent.addView(t);

        View line = new View(mActivity);
        line.setBackgroundColor(HAIRLINE);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f))));
        parent.addView(line);
    }

    /**
     * One settings row: name on the left (with an optional hint under it), control on the right.
     * The whole row is as tall as its tallest part, so a {@link Switch} is a full-height target.
     */
    private View row(String name, String value, View control) {
        LinearLayout r = new LinearLayout(mActivity);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(9), 0, dp(9));

        LinearLayout text = new LinearLayout(mActivity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(mActivity);
        t.setText(name);
        t.setTextColor(TEXT);
        t.setTextSize(14);
        UiFont.apply(t, false);
        text.addView(t);

        if (value != null) {
            TextView v = new TextView(mActivity);
            v.setText(value);
            v.setTextColor(HINT);
            v.setTextSize(11);
            text.addView(v);
        }
        r.addView(text);

        if (control != null) r.addView(control);

        View line = new View(mActivity);
        line.setBackgroundColor(HAIRLINE);
        LinearLayout wrap = new LinearLayout(mActivity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(r);
        wrap.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f))));
        return wrap;
    }

    private View sizeControl() {
        SeekBar size = new SeekBar(mActivity);
        size.setMax(22);
        size.setProgress(Math.max(0, mHost.fontSizeDp() - 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        size.setLayoutParams(lp);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) mHost.onFontSize(p + 6);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        return size;
    }

    /** A scheme as a row with an accent bar on the left when it is the active one. */
    private View schemeRow(String name, boolean active, Runnable onPick) {
        LinearLayout r = new LinearLayout(mActivity);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, 0, 0, 0);
        r.setClickable(true);
        r.setOnClickListener(v -> onPick.run());

        View bar = new View(mActivity);
        bar.setBackgroundColor(active ? ACCENT : 0x00000000);
        bar.setLayoutParams(new LinearLayout.LayoutParams(Math.max(2, dp(2)), dp(30)));
        r.addView(bar);

        TextView t = new TextView(mActivity);
        t.setText(name);
        t.setTextColor(active ? TEXT : HINT);
        t.setTextSize(14);
        t.setPadding(dp(10), dp(9), 0, dp(9));
        UiFont.apply(t, active);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(t);

        if (active) {
            TextView tick = new TextView(mActivity);
            tick.setText("\u2713");
            tick.setTextColor(ACCENT);
            tick.setTextSize(14);
            tick.setPadding(0, 0, dp(6), 0);
            r.addView(tick);
        }

        View line = new View(mActivity);
        line.setBackgroundColor(HAIRLINE);
        LinearLayout wrap = new LinearLayout(mActivity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(r);
        wrap.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f))));
        return wrap;
    }

    private View toggleRow(String name, boolean checked, OnToggle on) {
        Switch sw = new Switch(mActivity);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((v, c) -> on.on(c));
        return row(name, null, sw);
    }

    private interface OnToggle {
        void on(boolean checked);
    }

    private int dp(float v) {
        Resources r = mActivity.getResources();
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, r.getDisplayMetrics()));
    }
}
