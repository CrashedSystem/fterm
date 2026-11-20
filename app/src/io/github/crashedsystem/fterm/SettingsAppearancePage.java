package io.github.crashedsystem.fterm;

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

    SettingsAppearancePage(SettingsSheet sheet) {
        super(sheet);
    }

    /** Built per render: the names are localized, so they cannot be a static snapshot. */
    private static String[] cursorNames() {
        return new String[]{
                io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0120),
                io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0121),
                io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0122)};
    }

    @Override
    void build(LinearLayout col) {
        LinearLayout card = ui.card(col);

        // A number, not a slider: the size is a value people quote and type ("12dp"), and a
        // slider cannot land on a particular number on purpose. It is also not a unit anyone
        // should have to think about - dp is the same apparent size on every screen density -
        // so the field is labelled with it and otherwise left alone.
        LinearLayout sizeRow = new LinearLayout(activity);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        sizeRow.setPadding(0, ui.dp(10), 0, ui.dp(10));

        LinearLayout sizeText = new LinearLayout(activity);
        sizeText.setOrientation(LinearLayout.VERTICAL);
        sizeText.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView sizeTitle = new TextView(activity);
        sizeTitle.setText(AppStrings.s(R.string.i18n_0123));
        sizeTitle.setTextColor(ui.TEXT);
        sizeTitle.setTextSize(14);
        UiFont.apply(sizeTitle, false);
        sizeText.addView(sizeTitle);
        TextView sizeHint = new TextView(activity);
        sizeHint.setText(FontZoom.MIN_DP + "\u2013" + FontZoom.MAX_DP + " dp");
        sizeHint.setTextColor(ui.HINT);
        sizeHint.setTextSize(11);
        sizeText.addView(sizeHint);
        sizeRow.addView(sizeText);

        final android.widget.EditText sizeField = new android.widget.EditText(activity);
        sizeField.setText(String.valueOf(host.fontSizeDp()));
        sizeField.setHint(String.valueOf(FontZoom.DEFAULT_DP));
        sizeField.setHintTextColor(ui.HINT);
        sizeField.setTextColor(ui.TEXT);
        sizeField.setTextSize(14);
        sizeField.setSingleLine(true);
        sizeField.setGravity(Gravity.END);
        sizeField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        sizeField.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(80),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        sizeField.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) applySizeField(sizeField);
        });
        sizeField.setOnEditorActionListener((v, actionId, event) -> {
            applySizeField(sizeField);
            return true;
        });
        sizeRow.addView(sizeField);

        TextView sizeUnit = new TextView(activity);
        sizeUnit.setText("dp");
        sizeUnit.setTextColor(ui.HINT);
        sizeUnit.setTextSize(12);
        sizeUnit.setPadding(ui.dp(6), 0, ui.dp(8), 0);
        sizeRow.addView(sizeUnit);

        // A stepper beside the field: the common change is "one bigger", and reaching for the
        // keyboard to type 13 is more work than tapping an arrow. The field stays editable for
        // the uncommon case.
        sizeRow.addView(stepper("\u2212", () -> {
            int next = host.fontSizeDp() - 1;
            if (next < FontZoom.MIN_DP) return;
            host.onFontSize(next);
            sizeField.setText(String.valueOf(next));
        }));
        sizeRow.addView(stepper("+", () -> {
            int next = host.fontSizeDp() + 1;
            if (next > FontZoom.MAX_DP) return;
            host.onFontSize(next);
            sizeField.setText(String.valueOf(next));
        }));

        card.addView(ui.cardRow(sizeRow, false));

        card.addView(ui.valueRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0124), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0125),
                Fonts.labelOf(activity, host.fontFamily()),
                () -> sheet.fonts().chooseFont(), false));
        card.addView(ui.valueRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0126), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0127),
                Fonts.labelOfHangul(activity, host.hangulFamily()),
                () -> sheet.fonts().chooseHangulFont(), false));
        card.addView(ui.valueRow(AppStrings.s(R.string.i18n_0397), AppStrings.s(R.string.i18n_0398),
                cjkLabel(), () -> sheet.fonts().chooseCjkFont(), false));
        card.addView(ui.valueRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0128), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0129),
                host.schemeName(), () -> open("schemes"), false));
        card.addView(ui.toggleCardRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0130), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0131),
                host.keyBarVisible(), host::onKeyBarVisible, false));
        card.addView(ui.toggleCardRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0132),
                io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0133),
                host.keyBarAutoHide(), host::onKeyBarAutoHide, false));

        // WT's cursor page, folded into this one: three shapes and a blink switch.
        card.addView(ui.valueRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0134), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0135),
                cursorNames()[Math.max(0, Math.min(2, host.cursorStyle()))],
                this::chooseCursor, false));
        card.addView(ui.toggleCardRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0136), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0137),
                host.cursorBlink(), host::onCursorBlink, true));
        card.addView(cursorBlinkSpeedRow());
        card.addView(ui.valueRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0138), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0139),
                host.cursorColor() == 0 ? io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0140)
                        : String.format("#%06X", host.cursorColor() & 0xffffff),
                this::chooseCursorColor, false));
        card.addView(ui.valueRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0141), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0142),
                TabIcons.isGlyph(host.tabIcon())
                        ? TabIcons.GLYPH_NAMES[SettingsIconPicker.glyphIndexOf(host.tabIcon())]
                        : host.tabIcon(),
                this::chooseTabIcon, false));
        card.addView(ui.toggleCardRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0143), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0144),
                host.retroEffect(), host::onRetroEffect, true));

        LinearLayout bg = ui.card(col);
        bg.addView(ui.cardTitle(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0051)));
        sheet.background().rows(bg);
        ui.hint(col, io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0145)
                + io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0146));
    }

    /**
     * One arrow of the stepper: a square, the glyph centred, the bar's own hint colour.
     *
     * <p>Not {@link Chrome#icon}, because those glyphs are Fluent paths for actions and the
     * stepper needs the two characters every numeric field on every platform uses.
     */
    private View stepper(String glyph, Runnable action) {
        TextView v = new TextView(activity);
        v.setText(glyph);
        v.setTextSize(17);
        v.setTextColor(ui.TEXT);
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(false);
        v.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));
        v.setBackground(ui.touchable());
        v.setClickable(true);
        v.setOnClickListener(x -> action.run());
        return v;
    }

    /**
     * Commit the typed font size, or put the old one back.
     *
     * <p>The range is stated in the hint rather than enforced by clamping: a size outside it
     * would be quietly changed to something the user did not ask for, and "I typed 40 and got
     * 28" reads as a bug. Refusing and restoring is the honest answer.
     */
    private void applySizeField(android.widget.EditText field) {
        String raw = field.getText().toString().trim();
        int dp;
        try {
            dp = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            dp = -1;
        }
        if (dp < FontZoom.MIN_DP || dp > FontZoom.MAX_DP) {
            field.setText(String.valueOf(host.fontSizeDp()));
            android.widget.Toast.makeText(activity,
                    AppStrings.s(R.string.i18n_0399)
                            + FontZoom.MIN_DP + "\u2013" + FontZoom.MAX_DP + " dp",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        field.setText(String.valueOf(dp));
        host.onFontSize(dp);
    }

    /**
     * The blink speed as a number, not a slider.
     *
     * <p>A rate is a value people quote and compare ("500ms", "1s"), so it has to be exact:
     * a 20-step slider cannot land on 600 or 1000 and the stored value drifts from what
     * anyone meant. The field takes milliseconds directly and rejects out-of-range input
     * instead of silently clamping it, so a typo never becomes a mystery setting.
     */
    private View cursorBlinkSpeedRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(10), 0, ui.dp(10));

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(activity);
        t.setText(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0378));
        t.setTextColor(ui.TEXT);
        t.setTextSize(14);
        UiFont.apply(t, false);
        text.addView(t);
        row.addView(text);

        final android.widget.EditText field = new android.widget.EditText(activity);
        field.setText(String.valueOf(host.cursorBlinkMs()));
        field.setHint("500");
        field.setHintTextColor(ui.HINT);
        field.setTextColor(ui.TEXT);
        field.setTextSize(14);
        field.setSingleLine(true);
        field.setGravity(Gravity.END);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        field.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(90),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) return;
            applyBlinkField(field);
        });
        field.setOnEditorActionListener((v, actionId, event) -> {
            applyBlinkField(field);
            return true;
        });
        row.addView(field);

        TextView unit = new TextView(activity);
        unit.setText("ms");
        unit.setTextColor(ui.HINT);
        unit.setTextSize(12);
        unit.setPadding(ui.dp(6), 0, 0, 0);
        row.addView(unit);

        return ui.cardRow(row, false);
    }

    /**
     * Commit the typed rate, or put the old one back.
     *
     * <p>Out-of-range values are refused rather than clamped: TerminalView disables the
     * blinker entirely for a rate it does not accept, so clamping here would look like it
     * worked while the cursor froze.
     */
    private void applyBlinkField(android.widget.EditText field) {
        String raw = field.getText().toString().trim();
        int ms;
        try {
            ms = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            ms = -1;
        }
        if (ms < BLINK_MIN_MS || ms > BLINK_MAX_MS) {
            field.setText(String.valueOf(host.cursorBlinkMs()));
            android.widget.Toast.makeText(activity,
                    io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0379)
                            + BLINK_MIN_MS + "-" + BLINK_MAX_MS,
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        field.setText(String.valueOf(ms));
        host.onCursorBlinkMs(ms);
    }

    /** The range TerminalView accepts; a rate outside it turns blinking off. */
    private static final int BLINK_MIN_MS = 100;
    private static final int BLINK_MAX_MS = 2000;

    /** What the CJK row prints: the chosen face, or that the platform is choosing. */
    private String cjkLabel() {
        String id = host.cjkFamily();
        if (id == null || id.isEmpty()) return AppStrings.s(R.string.i18n_0396);
        for (Fonts.Face f : Fonts.cjkCatalog(activity)) {
            if (f.id.equals(id)) return f.label;
        }
        return AppStrings.s(R.string.i18n_0396);
    }

    private void chooseCursor() {        new android.app.AlertDialog.Builder(activity)
                .setTitle(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0134))
                .setSingleChoiceItems(cursorNames(), host.cursorStyle(), (d, which) -> {
                    host.onCursorStyle(which);
                    d.dismiss();
                    render();
                })
                .setNegativeButton(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0058), null)
                .show();
    }

    /**
     * The cursor colour: a hex field plus a chip, and io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0140) to go back.
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
        hex.setHint(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0147));
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
                .setTitle(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0138))
                .setView(box)
                .setPositiveButton(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0057), (d, w) -> {
                    String t = hex.getText().toString().trim();
                    Integer c = SettingsUi.parseHex(t);
                    if (t.isEmpty() || t.equals("#")) {
                        host.onCursorColor(0);   // empty means "follow the scheme"
                    } else if (c == null) {
                        // A typo must not silently do nothing; keeping the old value while
                        // the sheet reopens is exactly the "the setting is broken" report.
                        android.widget.Toast.makeText(activity, io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0148) + t,
                                android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        host.onCursorColor(c);
                    }
                    render();
                })
                .setNegativeButton(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0058), null)
                .setNeutralButton(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0140), (d, w) -> {
                    host.onCursorColor(0);
                    render();
                })
                .show();
    }

    private void chooseTabIcon() {
        SettingsIconPicker.pick(activity, ui, io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0141), host.tabIcon(), null, id -> {
            host.onTabIcon(id);
            render();
        });
    }
}
