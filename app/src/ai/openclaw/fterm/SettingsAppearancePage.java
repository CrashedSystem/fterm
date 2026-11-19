package ai.openclaw.fterm;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * 모양: the same rows as WT's appearance page, restricted to what this app has.
 *
 * <p>The font rows hand off to {@link SettingsFontPicker}, because choosing a face is three
 * dialogs deep and none of them is a row on this page. The background picture's four rows
 * live in {@link SettingsBackgroundPicker} for the same reason: the file picker result has to
 * come back to an object that outlives a render.
 */
final class SettingsAppearancePage extends SettingsPage {

    private static final String[] CURSOR_NAMES = {"블록", "밑줄", "세로 막대"};

    SettingsAppearancePage(SettingsSheet sheet) {
        super(sheet);
    }

    @Override
    void build(LinearLayout col) {
        LinearLayout card = ui.card(col);

        LinearLayout sizeRow = new LinearLayout(activity);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        sizeRow.setPadding(0, ui.dp(10), 0, ui.dp(10));

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(activity);
        t.setText("글자 크기");
        t.setTextColor(ui.TEXT);
        t.setTextSize(14);
        UiFont.apply(t, false);
        text.addView(t);
        TextView sub = new TextView(activity);
        sub.setText(host.fontSizeDp() + "dp");
        sub.setTextColor(ui.HINT);
        sub.setTextSize(11);
        text.addView(sub);
        sizeRow.addView(text);

        SeekBar size = new SeekBar(activity);
        size.setMax(FontZoom.MAX_DP - FontZoom.MIN_DP);
        size.setProgress(Math.max(0, host.fontSizeDp() - FontZoom.MIN_DP));
        size.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(150),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) {
                    host.onFontSize(p + FontZoom.MIN_DP);
                    sub.setText((p + FontZoom.MIN_DP) + "dp");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        sizeRow.addView(size);
        card.addView(ui.cardRow(sizeRow, false));

        card.addView(ui.valueRow("글꼴", "라틴 문자에 쓰는 고정폭 글꼴",
                Fonts.labelOf(activity, host.fontFamily()),
                () -> sheet.fonts().chooseFont(), false));
        card.addView(ui.valueRow("한글 글꼴", "한 글자가 정확히 두 칸을 차지하게 맞춤",
                Fonts.labelOfHangul(activity, host.hangulFamily()),
                () -> sheet.fonts().chooseHangulFont(), false));
        card.addView(ui.valueRow("색 구성표", "미리보기와 16색 팔레트",
                host.schemeName(), () -> open("schemes"), false));
        card.addView(ui.toggleCardRow("키 바 표시", "화면 아래 두 줄짜리 키 바",
                host.keyBarVisible(), host::onKeyBarVisible, false));

        // WT's cursor page, folded into this one: three shapes and a blink switch.
        card.addView(ui.valueRow("커서 모양", "블록 · 밑줄 · 세로 막대",
                CURSOR_NAMES[Math.max(0, Math.min(CURSOR_NAMES.length - 1, host.cursorStyle()))],
                this::chooseCursor, false));
        card.addView(ui.toggleCardRow("커서 깜빡임", "끄면 항상 보입니다",
                host.cursorBlink(), host::onCursorBlink, true));
        card.addView(ui.valueRow("커서 색", "구성표를 따르지 않고 직접 지정",
                host.cursorColor() == 0 ? "구성표 따름"
                        : String.format("#%06X", host.cursorColor() & 0xffffff),
                this::chooseCursorColor, false));
        card.addView(ui.valueRow("탭 아이콘", "탭 제목 앞의 작은 표시",
                TabIcons.isGlyph(host.tabIcon())
                        ? TabIcons.GLYPH_NAMES[SettingsIconPicker.glyphIndexOf(host.tabIcon())]
                        : host.tabIcon(),
                this::chooseTabIcon, false));
        card.addView(ui.toggleCardRow("레트로 효과", "형광 글로우와 주사선 (Windows Terminal)",
                host.retroEffect(), host::onRetroEffect, true));

        LinearLayout bg = ui.card(col);
        bg.addView(ui.cardTitle("배경 이미지"));
        sheet.background().rows(bg);
        ui.hint(col, "그림은 구성표와 따로 적용됩니다. 기기에서 고르거나, 앱의 bg 폴더에 "
                + "파일을 넣으면 최신 이미지가 그려집니다.");
    }

    private void chooseCursor() {
        new android.app.AlertDialog.Builder(activity)
                .setTitle("커서 모양")
                .setSingleChoiceItems(CURSOR_NAMES, host.cursorStyle(), (d, which) -> {
                    host.onCursorStyle(which);
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
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0);

        final int current = host.cursorColor();
        int seed = current != 0 ? current : host.customSlots()[18];

        final View chip = new View(activity);
        chip.setBackground(Chrome.plate(seed, 4));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(40));
        clp.bottomMargin = ui.dp(10);
        chip.setLayoutParams(clp);
        box.addView(chip);

        final android.widget.EditText hex = new android.widget.EditText(activity);
        hex.setText(current == 0 ? "" : String.format("#%06X", current & 0xffffff));
        hex.setHint("비우면 구성표를 따릅니다");
        hex.setHintTextColor(ui.HINT);
        hex.setSingleLine(true);
        hex.setTextSize(15);
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setTextColor(ui.TEXT);
        box.addView(hex);

        hex.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable e) {
                Integer c = SettingsUi.parseHex(e.toString());
                chip.setBackground(Chrome.plate(c != null ? c : seed, 4));
            }
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
        });

        new android.app.AlertDialog.Builder(activity)
                .setTitle("커서 색")
                .setView(box)
                .setPositiveButton("저장", (d, w) -> {
                    String t = hex.getText().toString().trim();
                    Integer c = SettingsUi.parseHex(t);
                    if (t.isEmpty() || t.equals("#")) {
                        host.onCursorColor(0);   // empty means "follow the scheme"
                    } else if (c == null) {
                        // A typo must not silently do nothing; keeping the old value while
                        // the sheet reopens is exactly the "the setting is broken" report.
                        android.widget.Toast.makeText(activity, "색 형식이 아닙니다: " + t,
                                android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        host.onCursorColor(c);
                    }
                    render();
                })
                .setNegativeButton("취소", null)
                .setNeutralButton("구성표 따름", (d, w) -> {
                    host.onCursorColor(0);
                    render();
                })
                .show();
    }

    private void chooseTabIcon() {
        SettingsIconPicker.pick(activity, ui, "탭 아이콘", host.tabIcon(), null, id -> {
            host.onTabIcon(id);
            render();
        });
    }
}
