package io.github.crashedsystem.fterm;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The navigation column: WT's list of pages, as glyph + label + hint.
 *
 * <p>WT draws each entry as glyph + label, and so does this; "프로필" is not a real page on the
 * desktop build, so unlike WT it is listed here as one. Every row leads somewhere, which is why
 * every row carries the right-pointing chevron WT puts on the same rows.
 */
final class SettingsRootPage extends SettingsPage {

    SettingsRootPage(SettingsSheet sheet) {
        super(sheet);
    }

    @Override
    void build(LinearLayout col) {
        col.addView(navRow("terminal", "시작", "기본 프로필, 셸, 시작 동작", "start"));
        col.addView(navRow("ssh", "프로필", "ssh처럼 명령으로 시작하는 탭", "profiles"));
        col.addView(navRow("pencil", "모양", "글자 크기, 키 바", "appearance"));
        col.addView(navRow("swatch", "색 구성표", "16색 팔레트와 미리보기", "schemes"));
        col.addView(navRow("keyboard", "상호 작용", "벨, 뒤로 가기 키", "interaction"));
        col.addView(navRow("info", "정보", "버전과 연결 상태", "about"));
    }

    private View navRow(String glyph, String label, String hint, final String page) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(10), ui.dp(9), ui.dp(8), ui.dp(9));
        row.setBackground(ui.touchable());
        row.setClickable(true);
        row.setOnClickListener(v -> open(page));

        View ic = Chrome.icon(activity, glyph, 20, false, null);
        // The gap is a margin, not padding: the glyph fills its ImageView's content box, so
        // padding here would shrink the icon instead of spacing it (that is why the sidebar
        // icons came out at a third of their size).
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20));
        ilp.rightMargin = ui.dp(14);
        ic.setLayoutParams(ilp);
        row.addView(ic);

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(activity);
        t.setText(label);
        t.setTextColor(ui.TEXT);
        t.setTextSize(14.5f);
        UiFont.apply(t, false);
        text.addView(t);
        TextView h = new TextView(activity);
        h.setText(hint);
        h.setTextColor(ui.HINT);
        h.setTextSize(11);
        text.addView(h);
        row.addView(text);

        // A right-pointing chevron: WT uses it on every row that leads somewhere else.
        View more = Chrome.icon(activity, "right", 16, true, null);
        row.addView(more);

        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row);
        wrap.addView(ui.hairline(ui.dp(10), ui.dp(0)));
        return wrap;
    }
}
