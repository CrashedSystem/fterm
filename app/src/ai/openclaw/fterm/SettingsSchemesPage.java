package ai.openclaw.fterm;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.terminal.TextStyle;

/**
 * 색 구성표, laid out like the reference: a terminal preview first, then the 16 colours as
 * eight rows of two swatches, then the list of schemes with the active one marked.
 *
 * <p>The preview is not decoration - it is the only place the foreground, background and
 * all eight normal/bright pairs are shown together, which is what makes a scheme's
 * legibility judgeable before it is applied.
 *
 * <p>The list is the six compiled-in schemes followed by the user's own, up to
 * {@link Schemes#MAX_CUSTOM_SCHEMES}. A custom row carries a pencil: that is where a saved
 * scheme is renamed or deleted, because the row itself already means "use this one".
 */
final class SettingsSchemesPage extends SettingsPage {

    SettingsSchemesPage(SettingsSheet sheet) {
        super(sheet);
    }

    @Override
    void build(LinearLayout col) {
        final int scheme = host.scheme();
        final int[] slots = host.customSlots();
        final int customCount = host.customSchemeCount();
        // Only a saved scheme has slots to edit; a built-in's are compiled in.
        final boolean editable = Schemes.isCustom(scheme) && customCount > 0;
        col.addView(preview(scheme, slots));

        LinearLayout colours = ui.card(col);
        colours.addView(ui.cardTitle("색"));
        int[] table = Schemes.colors(scheme, slots);
        String[] names = {"검정", "빨강", "녹색", "노랑", "파랑", "보라색", "녹청색", "흰색"};
        for (int i = 0; i < names.length; i++) {
            colours.addView(swatchRow(names[i], table[i], table[i + 8], i == names.length - 1,
                    editable, i, slots));
        }

        LinearLayout list = ui.card(col);
        list.addView(ui.cardTitle("구성표"));
        final int rows = Schemes.SCHEME_NAMES.length + customCount;
        int shown = 0;
        for (int i = 0; i < Schemes.SCHEME_NAMES.length; i++) {
            final int idx = i;
            shown++;
            list.addView(schemeRow(Schemes.SCHEME_NAMES[i], scheme == idx, shown == rows,
                    false, () -> {
                        host.onScheme(idx);
                        render();          // so the preview and the palette follow the pick
                    }, null));
        }
        for (int i = 0; i < customCount; i++) {
            final int idx = Schemes.CUSTOM_INDEX + i;
            final int at = i;
            shown++;
            list.addView(schemeRow(host.customSchemeName(i), scheme == idx, shown == rows,
                    true, () -> {
                        host.onScheme(idx);
                        render();
                    }, () -> sheet.schemeEditor().edit(at)));
        }

        ui.hint(col, editable
                ? "색을 누르면 그 자리의 값을 바꿀 수 있습니다. 왼쪽이 보통색, "
                        + "오른쪽이 밝은색입니다. 구성표 옆 연필로 이름을 바꾸거나 삭제합니다."
                : "내장 구성표의 색은 앱에 들어 있습니다. 아래에서 사용자 지정 구성표를 "
                        + "고르거나 새로 만들면 16색을 직접 정할 수 있습니다.");

        LinearLayout add = ui.card(col);
        add.addView(ui.cardTitle("사용자 지정  " + customCount + "/"
                + Schemes.MAX_CUSTOM_SCHEMES));
        add.addView(ui.valueRow("현재 구성표 복제", "지금 보이는 색으로 새 구성표를 만듭니다",
                "복제", () -> addCustom(host.schemeName() + " 복제",
                        Schemes.slotsOf(scheme, slots)), false));
        add.addView(ui.valueRow("새 구성표", "Campbell 색에서 시작합니다",
                "만들기", () -> addCustom("새 구성표", Schemes.campbellSlots()), true));

        LinearLayout importCard = ui.card(col);
        importCard.addView(ui.cardTitle("가져오기"));
        importCard.addView(ui.valueRow("Windows Terminal JSON",
                "16색과 전경·배경·커서를 읽어 새 구성표로 추가하고 바로 적용",
                "가져오기", () -> sheet.schemes().open(), true));
    }

    /**
     * Adds a scheme and selects it.
     *
     * <p>The cap is refused here, with a sentence that says what to do about it, because the
     * alternative - silently replacing the oldest scheme - would delete something the user
     * made without ever being asked.
     */
    private void addCustom(String name, int[] slots) {
        if (host.addCustomScheme(name, slots) < 0) {
            Toast.makeText(activity, "사용자 지정 구성표가 " + Schemes.MAX_CUSTOM_SCHEMES
                    + "개입니다. 구성표 옆 연필에서 하나를 삭제한 뒤 다시 추가하세요.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        render();
    }

    /**
     * A colour editor for one slot: a hex field, a live chip, and a preview of the result.
     *
     * <p>A full HSV picker would be another screen of custom drawing; the terminal's own
     * vocabulary is the hex code, and every colour a user would paste from a scheme file is
     * written that way. The chip exists so a typo is visible before it is committed.
     */
    private void editSlot(final int slot, int current, final int[] slots) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0);

        final View chip = new View(activity);
        chip.setBackground(Chrome.plate(current | 0xff000000, 4));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(40));
        clp.bottomMargin = ui.dp(10);
        chip.setLayoutParams(clp);
        box.addView(chip);

        final android.widget.EditText hex = new android.widget.EditText(activity);
        hex.setText(String.format("#%06X", current & 0xffffff));
        hex.setSingleLine(true);
        hex.setTextSize(15);
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setTextColor(ui.TEXT);
        hex.setHintTextColor(ui.HINT);
        box.addView(hex);

        TextView label = new TextView(activity);
        label.setText(slot < 16 ? (slot < 8 ? "보통색 " : "밝은색 ") + (slot % 8 + 1) + "번"
                : slot == 16 ? "전경" : slot == 17 ? "배경" : "커서");
        label.setTextColor(ui.HINT);
        label.setTextSize(11);
        label.setPadding(0, ui.dp(6), 0, 0);
        box.addView(label);

        hex.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable e) {
                Integer c = SettingsUi.parseHex(e.toString());
                if (c != null) chip.setBackground(Chrome.plate(c, 4));
            }
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
        });

        new android.app.AlertDialog.Builder(activity)
                .setTitle("색 지정")
                .setView(box)
                .setPositiveButton("저장", (d, w) -> {
                    Integer c = SettingsUi.parseHex(hex.getText().toString());
                    if (c != null) {
                        host.onCustomSlot(slot, c);
                        slots[slot] = c;
                        render();
                    }
                })
                .setNegativeButton("취소", null)
                .setNeutralButton("전경으로", (d, w) -> {
                    host.onCustomSlot(slot, slots[16]);
                    slots[slot] = slots[16];
                    render();
                })
                .show();
    }

    /**
     * A miniature terminal: the scheme's own background, and the eight colour names written in
     * the colours they name, normal then bright, one row each.
     */
    private View preview(int schemeIndex, int[] slots) {
        int[] table = Schemes.colors(schemeIndex, slots);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        box.setBackground(Chrome.plate(Schemes.background(schemeIndex, slots), 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ui.dp(10);
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

            TextView line = new TextView(activity);
            line.setText(sb);
            line.setTextSize(11);
            line.setTypeface(Typeface.MONOSPACE);
            line.setIncludeFontPadding(false);
            line.setLineSpacing(ui.dp(1), 1f);
            box.addView(line);
        }
        return box;
    }

    /**
     * One palette entry: name, its normal swatch, its bright swatch.
     *
     * <p>When a saved scheme is active the two swatches become tappable, because that is
     * where editing lives - WT's colour page is the same idea with a picker instead of a hex
     * field. The row itself stays a row: a value you can change, not a form control.
     */
    private View swatchRow(String name, int normal, int bright, boolean last,
                           boolean editable, final int base, final int[] slots) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(7), 0, ui.dp(7));

        TextView t = new TextView(activity);
        t.setText(name);
        t.setTextColor(ui.TEXT);
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
        return ui.cardRow(row, last);
    }

    /**
     * A colour chip. Black needs a hairline or it disappears into the card, and a stroke on
     * every chip is what WT draws anyway - the border is part of the swatch, not a fallback.
     */
    private View swatch(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(ui.dp(3));
        d.setStroke(Math.max(1, ui.dp(0.7f)), ui.CARD_HI);
        View v = new View(activity);
        v.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(34), ui.dp(20));
        lp.leftMargin = ui.dp(8);
        v.setLayoutParams(lp);
        return v;
    }

    /**
     * A scheme as a selectable row: accent bar when active, name, check glyph, and for a
     * saved scheme a pencil that opens rename/delete.
     */
    private View schemeRow(String name, boolean active, boolean last, boolean custom,
                           Runnable onPick, Runnable onEdit) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(2), 0, 0, 0);
        row.setClickable(true);
        row.setOnClickListener(v -> onPick.run());
        if (!active) row.setBackground(ui.touchable());

        View bar = new View(activity);
        bar.setBackgroundColor(active ? ui.ACCENT : 0x00000000);
        bar.setLayoutParams(new LinearLayout.LayoutParams(Math.max(2, ui.dp(2)), ui.dp(30)));
        row.addView(bar);

        TextView t = new TextView(activity);
        t.setText(name);
        t.setTextColor(active ? ui.TEXT : 0xffc8ccd4);
        t.setTextSize(13.5f);
        t.setPadding(ui.dp(10), ui.dp(9), 0, ui.dp(9));
        UiFont.apply(t, active);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);

        if (active) row.addView(Chrome.icon(activity, "check", 18, false, null));
        if (custom && onEdit != null) {
            View pencil = Chrome.icon(activity, "pencil", 17, true, v -> onEdit.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(17), ui.dp(17));
            lp.leftMargin = ui.dp(10);
            lp.rightMargin = ui.dp(2);
            pencil.setLayoutParams(lp);
            row.addView(pencil);
        }
        return ui.cardRow(row, last);
    }
}
