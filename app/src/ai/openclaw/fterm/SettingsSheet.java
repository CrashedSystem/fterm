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

        TextView keyEditLabel = label("키 바 편집");
        keyEditLabel.setPadding(0, dp(12), 0, dp(4));
        box.addView(keyEditLabel);
        TextView keyEditHint = new TextView(mActivity);
        keyEditHint.setText("아래 목록에서 탭: 추가/삭제. 화살표: 순서 변경. 변경은 즉시 저장됩니다.");
        keyEditHint.setTextColor(0xff8a8a8a);
        keyEditHint.setTextSize(11);
        box.addView(keyEditHint);

        // The catalogue: every key the bar can have. Tapped keys toggle in/out of the bar;
        // the arrows beside each selected key move it left/right in the bar's order.
        java.util.LinkedHashMap<String, String> cat = new java.util.LinkedHashMap<>();
        for (String[] c : KeyBar.CATALOGUE) cat.put(c[1], c[0]);
        final java.util.ArrayList<String> current = new java.util.ArrayList<>(mHost.keybarKeys());

        box.addView(orderRow(current, cat, null));
        for (int i = 0; i < current.size(); i++) {
            final int pos = i;
            box.addView(orderRow(current, cat, pos));
        }

        TextView addLabel = new TextView(mActivity);
        addLabel.setText("추가 가능한 키 (탭하여 추가):");
        addLabel.setTextColor(0xffaaaaaa);
        addLabel.setTextSize(12);
        addLabel.setPadding(0, dp(10), 0, dp(2));
        box.addView(addLabel);
        boolean any = false;
        for (String spec : cat.keySet()) {
            if (!current.contains(spec)) {
                box.addView(addRow(current, cat, spec));
                any = true;
            }
        }
        if (!any) {
            TextView none = new TextView(mActivity);
            none.setText("모두 추가됨");
            none.setTextColor(0xff666666);
            none.setTextSize(12);
            box.addView(none);
        }

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
    /** One row of the key editor: the key label, order arrows, and a delete button. */
    private android.view.View orderRow(java.util.ArrayList<String> current,
                                       java.util.LinkedHashMap<String, String> cat,
                                       Integer position) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));

        if (position == null) {
            TextView head = new TextView(mActivity);
            head.setText("키 바에 있는 키:");
            head.setTextColor(0xffaaaaaa);
            head.setTextSize(12);
            head.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(head);
            return row;
        }

        final String spec = current.get(position);
        final String label = cat.containsKey(spec) ? cat.get(spec) : KeyBar.labelOf(spec);

        TextView name = new TextView(mActivity);
        name.setText(label);
        name.setTextSize(14);
        name.setTextColor(0xffe6e6e6);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);

        Button left = new Button(mActivity);
        left.setText("\u2190");
        left.setAllCaps(false);
        left.setPadding(dp(8), 0, dp(8), 0);
        left.setEnabled(position > 0);
        left.setOnClickListener(v -> {
            if (position > 0) {
                String moved = current.remove((int) position);
                current.add(position - 1, moved);
                mHost.onKeybarKeys(current);
                show();
            }
        });
        row.addView(left);

        Button right = new Button(mActivity);
        right.setText("\u2192");
        right.setAllCaps(false);
        right.setPadding(dp(8), 0, dp(8), 0);
        right.setEnabled(position < current.size() - 1);
        right.setOnClickListener(v -> {
            if (position < current.size() - 1) {
                String moved = current.remove((int) position);
                current.add(position + 1, moved);
                mHost.onKeybarKeys(current);
                show();
            }
        });
        row.addView(right);

        Button del = new Button(mActivity);
        del.setText("삭제");
        del.setAllCaps(false);
        del.setOnClickListener(v -> {
            current.remove(position);
            mHost.onKeybarKeys(current);
            show();
        });
        row.addView(del);
        return row;
    }

    /** A selectable row for a catalogue key not currently on the bar: tap to add. */
    private android.view.View addRow(java.util.ArrayList<String> current,
                                     java.util.LinkedHashMap<String, String> cat,
                                     String spec) {
        Button add = new Button(mActivity);
        add.setText("+ " + (cat.containsKey(spec) ? cat.get(spec) : KeyBar.labelOf(spec)));
        add.setAllCaps(false);
        add.setOnClickListener(v -> {
            current.add(spec);
            mHost.onKeybarKeys(current);
            show();
        });
        return add;
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
