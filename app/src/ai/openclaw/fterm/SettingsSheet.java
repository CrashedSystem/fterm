package ai.openclaw.fterm;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * The settings sheet.
 *
 * Extracted from MainActivity so the activity is about sessions and input, and so the list of
 * settings can be read in one place. It owns no state: every control reports through
 * {@link Host}, and the host decides what to persist and repaint.
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

        java.util.ArrayList<String> keybarKeys();

        void onKeybarKeys(java.util.ArrayList<String> keys);

        /** e.g. "3 tabs · 127.0.0.1:8791" */
        String statusLine();
    }

    private final Activity mActivity;
    private final Host mHost;

    SettingsSheet(Activity activity, Host host) {
        mActivity = activity;
        mHost = host;
    }

    void show() {
        ScrollView sv = new ScrollView(mActivity);
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(12));
        sv.addView(box);

        box.addView(label("글자 크기"));

        SeekBar size = new SeekBar(mActivity);
        size.setMax(22);
        size.setProgress(mHost.fontSizeDp() - 6);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) mHost.onFontSize(p + 6);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        box.addView(size);

        TextView schemeLabel = label("색상 구성");
        schemeLabel.setPadding(0, dp(12), 0, dp(4));
        box.addView(schemeLabel);

        for (int i = 0; i < Prefs.SCHEME_NAMES.length; i++) {
            final int idx = i;
            Button b = new Button(mActivity);
            b.setText((mHost.scheme() == idx ? "\u2713 " : "") + Prefs.SCHEME_NAMES[i]);
            b.setAllCaps(false);
            b.setOnClickListener(v -> {
                mHost.onScheme(idx);
                show();                 // repaint with the new tick
            });
            box.addView(b);
        }

        // ---- key bar editor: chips in one wrap row ----------------------------
        TextView keyEditLabel = label("키 바 편집");
        keyEditLabel.setPadding(0, dp(12), 0, dp(2));
        box.addView(keyEditLabel);
        TextView keyEditHint = new TextView(mActivity);
        keyEditHint.setText("←→: 순서 변경 · 제거: 키를 목록에서 뺌 · + : 키 추가");
        keyEditHint.setTextColor(0xff8a8a8a);
        keyEditHint.setTextSize(11);
        keyEditHint.setPadding(0, 0, 0, dp(4));
        box.addView(keyEditHint);

        final LinearLayout chipRow = new LinearLayout(mActivity);
        chipRow.setOrientation(LinearLayout.VERTICAL);
        // Wrap: chips flow onto as many lines as needed.
        final java.util.ArrayList<String> chips = new java.util.ArrayList<>(mHost.keybarKeys());

        final Runnable[] redrawHolder = new Runnable[1];
        Runnable redraw = () -> {
            chipRow.removeAllViews();
            for (int i = 0; i < chips.size(); i++) {
                final int pos = i;
                String spec = chips.get(pos);
                chipRow.addView(chip(KeyBar.labelOf(spec), () -> {
                    chips.remove(pos);
                    mHost.onKeybarKeys(chips);
                    redrawHolder[0].run();
                }, pos > 0 ? () -> {
                    chips.add(pos - 1, chips.remove(pos));
                    mHost.onKeybarKeys(chips);
                    redrawHolder[0].run();
                } : null, pos < chips.size() - 1 ? () -> {
                    chips.add(pos + 1, chips.remove(pos));
                    mHost.onKeybarKeys(chips);
                    redrawHolder[0].run();
                } : null));
            }
            Button addAll = new Button(mActivity);
            addAll.setText("+ 키 추가");
            addAll.setAllCaps(false);
            addAll.setOnClickListener(v -> showAddDialog(chips, redrawHolder[0]));
            chipRow.addView(addAll);
        };
        redrawHolder[0] = redraw;
        redraw.run();
        box.addView(chipRow);

        TextView bellLabel = label("터미널 벨 (BEL)");
        bellLabel.setPadding(0, dp(12), 0, dp(4));
        box.addView(bellLabel);

        CheckBox vibrate = new CheckBox(mActivity);
        vibrate.setText("진동");
        vibrate.setChecked(mHost.bellVibrate());
        box.addView(vibrate);

        CheckBox sound = new CheckBox(mActivity);
        sound.setText("소리 (무음 모드에서는 나지 않음)");
        sound.setChecked(mHost.bellSound());
        box.addView(sound);

        vibrate.setOnCheckedChangeListener((v, on) -> mHost.onBell(on, sound.isChecked()));
        sound.setOnCheckedChangeListener((v, on) -> mHost.onBell(vibrate.isChecked(), on));

        CheckBox keys = new CheckBox(mActivity);
        keys.setText("키 바 표시");
        keys.setChecked(mHost.keyBarVisible());
        keys.setOnCheckedChangeListener((v, on) -> mHost.onKeyBarVisible(on));
        keys.setPadding(0, dp(12), 0, 0);
        box.addView(keys);

        TextView about = new TextView(mActivity);
        about.setText("\n" + mHost.statusLine()
                + "\n길게 누르기: 텍스트 선택 · 탭 길게: 닫기");
        about.setTextColor(0xff8a8a8a);
        about.setTextSize(12);
        box.addView(about);

        new AlertDialog.Builder(mActivity)
                .setTitle("fterm 설정")
                .setView(sv)
                .setPositiveButton("닫기", null)
                .show();
    }
    /**
     * One key editor row: the key name on the left, then order arrows and remove - all
     * full-height touch targets (44dp buttons in a 52dp row). Vertical list, not chips:
     * the chip row was too small to hit reliably on a phone.
     */
    private android.view.View chip(String label, Runnable remove,
                                   Runnable moveLeft, Runnable moveRight) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(2);
        row.setLayoutParams(rowLp);

        TextView t = new TextView(mActivity);
        t.setText(label);
        t.setTextSize(15);
        t.setTextColor(0xffe6e6e6);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);

        Button l = new Button(mActivity);
        l.setText("\u2190");
        l.setAllCaps(false);
        l.setPadding(dp(14), 0, dp(14), 0);
        l.setEnabled(moveLeft != null);
        l.setOnClickListener(v -> { if (moveLeft != null) moveLeft.run(); });
        row.addView(l);

        Button r = new Button(mActivity);
        r.setText("\u2192");
        r.setAllCaps(false);
        r.setPadding(dp(14), 0, dp(14), 0);
        r.setEnabled(moveRight != null);
        r.setOnClickListener(v -> { if (moveRight != null) moveRight.run(); });
        row.addView(r);

        Button x = new Button(mActivity);
        x.setText("제거");
        x.setAllCaps(false);
        x.setOnClickListener(v -> remove.run());
        row.addView(x);
        return row;
    }

    /** The catalogue as a picker: one dialog, tap a key to add it, stays open. */
    private void showAddDialog(java.util.ArrayList<String> chips, Runnable redraw) {
        java.util.ArrayList<String> available = new java.util.ArrayList<>();
        // Modifiers are configurable keys too - they belong in the same picker.
        for (String[] c : KeyBar.MODIFIERS) {
            if (!chips.contains(c[1])) available.add(c[1]);
        }
        for (String[] c : KeyBar.CATALOGUE) {
            if (!chips.contains(c[1])) available.add(c[1]);
        }
        if (available.isEmpty()) {
            android.widget.Toast.makeText(mActivity, "추가할 수 있는 키가 없습니다", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        // A 3-column grid: all remaining keys visible at once, no scrolling list.
        android.widget.GridLayout grid = new android.widget.GridLayout(mActivity);
        grid.setColumnCount(3);
        grid.setPadding(dp(8), dp(8), dp(8), dp(8));
        final java.util.List<android.app.Dialog> holder =
                new java.util.ArrayList<>(1);
        for (final String spec : available) {
            Button add = new Button(mActivity);
            add.setText(KeyBar.labelOf(spec));
            add.setAllCaps(false);
            add.setPadding(dp(2), dp(2), dp(2), dp(2));
            add.setOnClickListener(v -> {
                chips.add(spec);
                mHost.onKeybarKeys(chips);
                redraw.run();
                if (!holder.isEmpty()) holder.get(0).dismiss();
            });
            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
            lp.width = 0;
            lp.columnSpec = android.widget.GridLayout.spec(
                    android.widget.GridLayout.UNDEFINED, 1f);
            add.setLayoutParams(lp);
            grid.addView(add);
        }
        android.app.Dialog dialog = new AlertDialog.Builder(mActivity)
                .setTitle("키 추가")
                .setView(grid)
                .setNegativeButton("닫기", null)
                .create();
        holder.add(dialog);
        dialog.show();
    }

    private TextView label(String text) {
        TextView t = new TextView(mActivity);
        t.setText(text);
        t.setTextColor(0xffcfcfcf);
        t.setTextSize(13);
        return t;
    }

    private int dp(float v) {
        Resources r = mActivity.getResources();
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, r.getDisplayMetrics()));
    }
}
