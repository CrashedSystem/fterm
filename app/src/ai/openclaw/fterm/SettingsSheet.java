package ai.openclaw.fterm;

import android.app.Activity;
import android.app.AlertDialog;
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
