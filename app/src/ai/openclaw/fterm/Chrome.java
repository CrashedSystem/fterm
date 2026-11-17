package ai.openclaw.fterm;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Windows Terminal-shaped chrome: the command palette, the icon strip, and the row/plate
 * primitives the settings sheet shares.
 *
 * <p>Windows Terminal is the reference not because of its colours but because of three
 * decisions it makes that a phone terminal usually gets wrong: commands are a searchable list
 * rather than a nest of menus, chrome is monochrome line art rather than coloured emoji, and
 * settings are a flat sheet of labelled rows rather than a second app.
 *
 * <p>Extracted from MainActivity for the same reason KeyBar and SettingsSheet were: the
 * activity is a coordinator and this is presentation.
 */
final class Chrome {

    /** What a palette entry does. Kept out of the palette so the list stays data. */
    interface Commands {
        void newTab();
        void closeTab();
        void renameTab();
        void nextTab();
        void paste();
        void copy();
        void biggerFont();
        void smallerFont();
        void settings();
        void toggleKeyboard();
        void palette();
        void about();
    }

    /** One palette entry: the label shown, and the action it carries. */
    private static final class Entry {
        final String label;
        final Runnable action;
        Entry(String label, Runnable action) { this.label = label; this.action = action; }
        @Override public String toString() { return label; }
    }

    private final Activity mActivity;
    private final Commands mCommands;

    Chrome(Activity activity, Commands commands) {
        mActivity = activity;
        mCommands = commands;
    }

    /**
     * The command palette.
     *
     * <p>Usable with no keyboard at all, which is the only state a phone terminal spends most of
     * its time in: one full-height list of tall rows, always visible, and the search box is
     * optional - it does not take focus when the palette opens, so ignoring it runs the whole
     * palette by thumb. The earlier version put a focused {@link EditText} on top, so opening
     * the palette always raised the IME over the list it was meant to filter.
     */
    void palette() {
        final Entry[] entries = {
                new Entry("새 탭 열기", mCommands::newTab),
                new Entry("탭 닫기", mCommands::closeTab),
                new Entry("탭 이름 바꾸기", mCommands::renameTab),
                new Entry("다음 탭", mCommands::nextTab),
                new Entry("붙여넣기", mCommands::paste),
                new Entry("복사", mCommands::copy),
                new Entry("글자 크기 키우기", mCommands::biggerFont),
                new Entry("글자 크기 줄이기", mCommands::smallerFont),
                new Entry("설정 열기", mCommands::settings),
                new Entry("키보드 보이기/숨기기", mCommands::toggleKeyboard),
                new Entry("정보", mCommands::about),
        };

        final android.widget.ArrayAdapter<Entry> adapter =
                new android.widget.ArrayAdapter<>(mActivity,
                        android.R.layout.simple_list_item_1, entries);
        final android.widget.ListView list = new android.widget.ListView(mActivity);
        list.setAdapter(adapter);
        list.setDividerHeight(0);
        list.setPadding(0, dp(mActivity, 2), 0, dp(mActivity, 2));

        final EditText filter = new EditText(mActivity);
        filter.setHint("명령 검색");
        filter.setSingleLine(true);
        filter.setTextSize(13.5f);
        filter.setTextColor(TEXT);
        filter.setHintTextColor(HINT);
        filter.setBackground(null);
        filter.setPadding(dp(mActivity, 14), dp(mActivity, 10), dp(mActivity, 14),
                dp(mActivity, 10));
        // Focusable by touch, not by default: the list is the primary surface. WT's palette
        // does focus its box, but WT always has a keyboard; here focusing it would raise the
        // IME over the list.
        filter.setFocusableInTouchMode(true);
        filter.setFocusable(false);
        filter.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable e) {
                adapter.getFilter().filter(e.toString());
            }
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
        });

        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(plate(SHEET_BG, 10));
        box.setPadding(0, dp(mActivity, 6), 0, 0);
        box.addView(filter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View filterRule = new View(mActivity);
        filterRule.setBackgroundColor(HAIRLINE);
        box.addView(filterRule, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(mActivity, 0.8f))));

        box.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final android.app.Dialog dialog = new android.app.Dialog(mActivity);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(box);
        dialog.setCanceledOnTouchOutside(true);

        list.setOnItemClickListener((parent, view, position, id) -> {
            Entry e = adapter.getItem(position);
            dialog.dismiss();
            if (e != null) e.action.run();
        });

        dialog.show();
        // The window is what gives the list its height; without this it collapses to a stub in
        // the middle of the screen and the rows at the bottom are unreachable.
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (mActivity.getResources().getDisplayMetrics().heightPixels * 0.72f));
        }
    }

    // ------------------------------------------------------------------ dropdown

    /**
     * One row of the dropdown: glyph, name, and the shortcut right-aligned.
     *
     * <p>This is the piece of Windows Terminal the app was imitating badly. The old menu was a
     * platform {@link android.widget.PopupMenu} filled with strings that padded the shortcut
     * into the label with spaces - so the "column" was however many spaces happened to fit,
     * nothing was aligned, the glyphs were missing, and the section separators were dropped
     * outright (they appeared in the array and were skipped when building the menu). What makes
     * the WT menu read as one surface is exactly the three things that were lost: a glyph per
     * row, a shortcut column that really lines up, and hairlines between groups.
     */
    private static final class Row {
        final String glyph;
        final String label;
        final String shortcut;
        final Runnable action;
        /** A rule instead of a row: the divider between groups. */
        final boolean divider;

        private Row(String glyph, String label, String shortcut, Runnable action, boolean divider) {
            this.glyph = glyph;
            this.label = label;
            this.shortcut = shortcut;
            this.action = action;
            this.divider = divider;
        }

        static Row of(String glyph, String label, String shortcut, Runnable action) {
            return new Row(glyph, label, shortcut, action, false);
        }

        static Row rule() {
            return new Row(null, null, null, null, true);
        }
    }

    /**
     * The tab bar's dropdown, shaped like WT's: a profile block, then commands, then settings.
     *
     * <p>Shortcut labels are shown even though a phone has no control key for most of them, and
     * that is deliberate - they are the vocabulary of the desktop terminal, and a phone terminal
     * that never mentions them cannot teach its own gestures. Tapping a row is the primary path;
     * the hints are secondary.
     */
    void menu(View anchor) {
        final Row[] rows = {
                Row.of("terminal", "새 탭", "Ctrl+Shift+T", mCommands::newTab),
                Row.of("pencil", "탭 이름 바꾸기", null, mCommands::renameTab),
                Row.of("close", "탭 닫기", "Ctrl+Shift+W", mCommands::closeTab),
                Row.rule(),
                Row.of("clipboard", "붙여넣기", "Ctrl+Shift+V", mCommands::paste),
                Row.of("copy", "복사", "Ctrl+Shift+C", mCommands::copy),
                Row.rule(),
                Row.of("gear", "설정", "Ctrl+,", mCommands::settings),
                Row.of("terminal", "명령 팔레트", "Ctrl+Shift+P", mCommands::palette),
                Row.of("info", "정보", null, mCommands::about),
        };

        LinearLayout list = new LinearLayout(mActivity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(mActivity, 4), 0, dp(mActivity, 4));
        list.setBackground(plate(SHEET_BG, 10));

        final android.app.Dialog dialog = new android.app.Dialog(mActivity);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        // WT's dropdown starts with the profile, not with commands: the first thing under the
        // tab bar is the thing a tab IS. On the phone build there is one profile, so it is one
        // row - the block is still there because it is what makes the menu read as WT's.
        list.addView(profileRow(dialog));
        View firstRule = new View(mActivity);
        firstRule.setBackgroundColor(HAIRLINE);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(mActivity, 0.8f)));
        flp.topMargin = dp(mActivity, 3);
        flp.bottomMargin = dp(mActivity, 3);
        firstRule.setLayoutParams(flp);
        list.addView(firstRule);

        for (Row r : rows) {
            if (r.divider) {
                View line = new View(mActivity);
                line.setBackgroundColor(HAIRLINE);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(mActivity, 0.8f)));
                lp.leftMargin = dp(mActivity, 10);
                lp.rightMargin = dp(mActivity, 10);
                lp.topMargin = dp(mActivity, 4);
                lp.bottomMargin = dp(mActivity, 4);
                line.setLayoutParams(lp);
                list.addView(line);
                continue;
            }
            list.addView(menuRow(r, dialog));
        }

        dialog.setContentView(list);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        android.view.Window w = dialog.getWindow();
        if (w != null) {
            // Translucent so the sheet's own rounded plate is the visible surface, and the
            // terminal stays readable behind it.
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout(dp(mActivity, 260), ViewGroup.LayoutParams.WRAP_CONTENT);
            // Just under the bar, right-aligned with the glyph that opened it, like a dropdown
            // hangs off its button rather than appearing in the middle of the screen.
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = dp(mActivity, 8);
            lp.y = dp(mActivity, 44);
            w.setAttributes(lp);
        }
    }

    /**
     * WT's profile block: a glyph plate, the profile's name, and the shell it runs.
     *
     * <p>It is not a command. It is the header of the dropdown - the answer to "which profile am
     * I tabbing into" - so it is drawn with a filled plate rather than a row ripple, and it
     * closes the menu without doing anything.
     */
    private View profileRow(final android.app.Dialog dialog) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(mActivity, 12), dp(mActivity, 8), dp(mActivity, 12),
                dp(mActivity, 8));
        row.setClickable(true);
        row.setOnClickListener(v -> dialog.dismiss());

        android.widget.ImageView plate = new android.widget.ImageView(mActivity);
        plate.setImageDrawable(new Glyph("terminal", ICON));
        plate.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        plate.setBackground(plate(CARD_HI, 6));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                dp(mActivity, 30), dp(mActivity, 30));
        plp.rightMargin = dp(mActivity, 10);
        plate.setPadding(dp(mActivity, 5), dp(mActivity, 5), dp(mActivity, 5),
                dp(mActivity, 5));
        plate.setLayoutParams(plp);
        row.addView(plate);

        LinearLayout text = new LinearLayout(mActivity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(mActivity);
        name.setText("shell");
        name.setTextColor(TEXT);
        name.setTextSize(13.5f);
        UiFont.apply(name, true);
        text.addView(name);
        TextView sub = new TextView(mActivity);
        sub.setText("Termux · bash -l");
        sub.setTextColor(HINT);
        sub.setTextSize(10.5f);
        text.addView(sub);
        row.addView(text);

        return row;
    }

    /** Glyph, name, and the shortcut in its own right-aligned column. */    private View menuRow(Row r, android.app.Dialog dialog) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(mActivity, 12), 0, dp(mActivity, 12), 0);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(mActivity, 42)));
        // Platform ripple, so a tap is acknowledged; the sheet keeps its own surface colour.
        android.util.TypedValue tv = new android.util.TypedValue();
        if (mActivity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, tv, true)) {
            row.setBackgroundResource(tv.resourceId);
        }
        row.setClickable(true);
        row.setOnClickListener(v -> {
            dialog.dismiss();
            r.action.run();
        });

        View glyph = icon(mActivity, r.glyph, 18, true, null);
        glyph.setPadding(0, 0, 0, 0);
        row.addView(glyph);

        TextView label = new TextView(mActivity);
        label.setText(r.label);
        label.setTextSize(13.5f);
        label.setTextColor(TEXT);
        label.setSingleLine(true);
        UiFont.apply(label, false);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.leftMargin = dp(mActivity, 12);
        label.setLayoutParams(labelLp);
        row.addView(label);

        TextView hint = new TextView(mActivity);
        hint.setText(r.shortcut == null ? "" : r.shortcut);
        hint.setTextSize(11.5f);
        hint.setTextColor(HINT);
        hint.setSingleLine(true);
        UiFont.apply(hint, false);
        hint.setGravity(Gravity.END);
        row.addView(hint);

        return row;
    }

    // ------------------------------------------------------------------ controls

    /**
     * WT's toggle: a small track with a sliding thumb, accent-filled when on.
     *
     * <p>The platform {@link android.widget.Switch} is a OneUI-styled control: its off state
     * draws a dark grey track on a dark grey sheet, which on this device rendered as a black
     * dot - three invisible toggles in the settings list. WT's own toggle is deliberately
     * low-contrast when off and clearly accent-coloured when on, and it is small, so it reads
     * as a value rather than as a button competing with the row.
     */
    static View toggle(Context context, boolean checked, final OnToggle on) {
        final int TRACK_W = 36, TRACK_H = 20, THUMB = 16;
        final boolean[] state = {checked};

        android.widget.ImageView v = new android.widget.ImageView(context);
        final android.graphics.drawable.GradientDrawable track =
                new android.graphics.drawable.GradientDrawable();
        final android.graphics.drawable.GradientDrawable thumb =
                new android.graphics.drawable.GradientDrawable();
        track.setCornerRadius(TRACK_H / 2f * context.getResources().getDisplayMetrics().density);
        thumb.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        thumb.setColor(0xffffffff);

        final int w = dp(context, TRACK_W), h = dp(context, TRACK_H);
        final int pad = dp(context, 2);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        v.setPadding(0, 0, 0, 0);
        v.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        v.setClickable(true);
        v.setFocusable(false);

        final Runnable paint = new Runnable() {
            @Override public void run() {
                boolean onState = state[0];
                track.setColor(onState ? ACCENT : 0xff3a3f47);
                // A layer-list is what puts the thumb on the track; a GradientDrawable alone
                // cannot hold a child.
                android.graphics.drawable.LayerDrawable layers =
                        new android.graphics.drawable.LayerDrawable(
                                new android.graphics.drawable.Drawable[]{track, thumb});
                int thumbSize = dp(context, THUMB);
                int top = (h - thumbSize) / 2;
                int left = onState ? w - thumbSize - pad : pad;
                layers.setLayerInset(1, left, top, w - left - thumbSize, top);
                v.setImageDrawable(layers);
            }
        };
        paint.run();

        v.setOnClickListener(x -> {
            state[0] = !state[0];
            paint.run();
            on.on(state[0]);
        });
        return v;
    }

    interface OnToggle {
        void on(boolean checked);
    }

    /**
     * The sheet's title row: name on the left, a close glyph on the right.
     *
     * <p>WT's settings page is a page, not a dialog: it has no OK/Cancel footer, and the way
     * out is closing the surface. The previous sheet ended in a full-width "닫기" button, which
     * is the single most Android-dialog thing about it and the reason it read as a form.
     */
    static View titleRow(Context context, String title, final Runnable onClose) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 10), 0, dp(context, 8));

        TextView t = new TextView(context);
        t.setText(title);
        t.setTextSize(17);
        t.setTextColor(TEXT);
        UiFont.apply(t, true);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);

        View close = icon(context, "close", 20, true, v -> onClose.run());
        row.addView(close);
        return row;
    }

    // ------------------------------------------------------------------ icon strip
    private static final int ICON = 0xffc8ccd4;
    private static final int ICON_DIM = 0xff8a8f99;
    private static final int SHEET_BG = 0xff1f2126;
    private static final int CARD_HI = 0xff34383f;
    private static final int TEXT = 0xffe8eaee;
    private static final int HINT = 0xff8a8f99;
    private static final int HAIRLINE = 0xff2c2f36;
    /** WT's accent: the selection colour, and the only saturated colour in the chrome. */
    static final int ACCENT = 0xff4a9eff;

    /**
     * A chrome glyph drawn as line art.
     *
     * <p>Windows Terminal's title bar is a row of thin strokes at one weight, so the eye reads
     * position rather than decoration. The previous strip was text ("＋", "▾", "⚙"), which on
     * this device rendered as a mix of full-width CJK plus, a black triangle and a coloured
     * emoji gear - three different weights and one of them in colour. These are paths, so they
     * are the same weight at every density and never fall back to an emoji font.
     *
     * @param path one of {"plus", "chevron", "gear", "terminal", "keyboard", "split", "close"}
     */
    static View icon(Context context, String path, int sizeDp, boolean dim,
                     View.OnClickListener onClick) {
        android.widget.ImageView v = new android.widget.ImageView(context);
        v.setImageDrawable(new Glyph(path, dim ? ICON_DIM : ICON));
        v.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        int s = dp(context, sizeDp);
        v.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        // The inset scales with the glyph: a fixed 4dp on a 16dp icon left 8dp of ink in a
        // 16dp box, which is why the sidebar glyphs rendered at half the size of their labels.
        int inset = Math.round(sizeDp * 0.10f);
        v.setPadding(dp(context, inset), dp(context, inset),
                dp(context, inset), dp(context, inset));
        v.setClickable(onClick != null);
        if (onClick != null) v.setOnClickListener(onClick);
        return v;
    }

    /** The path set. One stroke width, one cap style, 24x24 viewport, like WT's icon font. */
    private static final class Glyph extends android.graphics.drawable.Drawable {
        private final android.graphics.Path p = new android.graphics.Path();
        private final android.graphics.Paint paint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);

        Glyph(String name, int color) {
            paint.setColor(color);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(1.9f);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            build(name);
        }

        private void build(String name) {
            switch (name) {
                case "plus":
                    p.moveTo(12, 5); p.lineTo(12, 19);
                    p.moveTo(5, 12); p.lineTo(19, 12);
                    break;
                case "chevron":
                    p.moveTo(8, 10); p.lineTo(12, 14); p.lineTo(16, 10);
                    break;
                case "left":
                    p.moveTo(14, 7); p.lineTo(9, 12); p.lineTo(14, 17);
                    break;
                case "menu":
                    // WT's hamburger: three rules, widening downward, at the same weight as
                    // every other glyph so it does not read as a different icon set.
                    p.moveTo(4, 7); p.lineTo(20, 7);
                    p.moveTo(4, 12); p.lineTo(20, 12);
                    p.moveTo(4, 17); p.lineTo(20, 17);
                    break;
                case "swatch":
                    // The colour-scheme icon in WT's sidebar: a wheel split into quadrants.
                    p.addCircle(12, 12, 8.2f, android.graphics.Path.Direction.CW);
                    p.moveTo(12, 3.8f); p.lineTo(12, 20.2f);
                    p.moveTo(3.8f, 12); p.lineTo(20.2f, 12);
                    break;
                case "right":
                    p.moveTo(10, 7); p.lineTo(15, 12); p.lineTo(10, 17);
                    break;
                case "check":
                    p.moveTo(6, 12.5f); p.lineTo(10, 16.5f); p.lineTo(18, 7.5f);
                    break;
                case "palette":
                    // Three overlapping wells: a colour wheel reduced to what survives at
                    // 20dp. WT's sidebar icon for colour schemes is a wheel; a full wheel is
                    // mud at this size, three circles still read as "colours".
                    p.addCircle(9.2f, 9.8f, 4.6f, android.graphics.Path.Direction.CW);
                    p.addCircle(14.8f, 9.8f, 4.6f, android.graphics.Path.Direction.CW);
                    p.addCircle(12f, 14.9f, 4.6f, android.graphics.Path.Direction.CW);
                    break;
                case "close":
                    p.moveTo(7, 7); p.lineTo(17, 17);
                    p.moveTo(17, 7); p.lineTo(7, 17);
                    break;
                case "gear":
                    // A real gear outline: eight trapezoidal teeth around a ring, as one closed
                    // path. The previous version was a small circle with eight spokes, which
                    // at phone size read as a sun - and "gear" is the one glyph whose whole job
                    // is to be recognised without a label.
                    buildGear(8, 9.2f, 6.5f, 12f, 12f);
                    p.addCircle(12, 12, 2.6f, android.graphics.Path.Direction.CW);
                    break;
                case "terminal":
                    // ">_" - the prompt, the one glyph every terminal shares.
                    p.moveTo(5, 7); p.lineTo(10, 12); p.lineTo(5, 17);
                    p.moveTo(12, 17); p.lineTo(19, 17);
                    break;
                case "keyboard":
                    p.addRoundRect(new android.graphics.RectF(3, 7, 21, 17), 2.2f, 2.2f,
                            android.graphics.Path.Direction.CW);
                    p.moveTo(7, 11); p.lineTo(7, 11.01f);
                    p.moveTo(11, 11); p.lineTo(11, 11.01f);
                    p.moveTo(15, 11); p.lineTo(15, 11.01f);
                    p.moveTo(17, 11); p.lineTo(17, 11.01f);
                    p.moveTo(8, 14); p.lineTo(16, 14);
                    break;
                case "split":
                    p.addRoundRect(new android.graphics.RectF(3, 5, 21, 19), 2f, 2f,
                            android.graphics.Path.Direction.CW);
                    p.moveTo(12, 5); p.lineTo(12, 19);
                    break;
                case "pencil":
                    // A pen at 45°: barrel, tip, and the nib line.
                    p.moveTo(4, 20); p.lineTo(4.6f, 16.2f);
                    p.lineTo(15.6f, 5.2f); p.lineTo(18.8f, 8.4f);
                    p.lineTo(7.8f, 19.4f); p.close();
                    p.moveTo(14.2f, 6.6f); p.lineTo(17.4f, 9.8f);
                    break;
                case "clipboard":
                    // Paste: a board with the tab that a clipboard hangs from.
                    p.addRoundRect(new android.graphics.RectF(5, 4, 19, 20), 2f, 2f,
                            android.graphics.Path.Direction.CW);
                    p.addRoundRect(new android.graphics.RectF(9, 2.4f, 15, 6), 1.2f, 1.2f,
                            android.graphics.Path.Direction.CW);
                    break;
                case "copy":
                    // Two offset sheets: the back one open, the front one closed.
                    p.addRoundRect(new android.graphics.RectF(8, 3, 19, 14), 1.8f, 1.8f,
                            android.graphics.Path.Direction.CW);
                    p.addRoundRect(new android.graphics.RectF(4, 8, 15, 21), 1.8f, 1.8f,
                            android.graphics.Path.Direction.CW);
                    break;
                case "info":
                    p.addCircle(12, 12, 8.4f, android.graphics.Path.Direction.CW);
                    p.moveTo(12, 11); p.lineTo(12, 16.4f);
                    p.moveTo(12, 7.6f); p.lineTo(12, 7.61f);
                    break;
                default:
                    p.addCircle(12, 12, 7, android.graphics.Path.Direction.CW);
                    break;
            }
        }

        /**
         * A cog outline as one closed path: a ring of trapezoidal teeth.
         *
         * <p>Drawn as an outline rather than a circle plus spokes because the spokes were what
         * made it look like a sun. Each tooth is a quad whose inner edge is narrower than its
         * outer edge, which is what gives a cog its shape; a rectangle on a ring reads as a
         * sprocket, and lines out from a circle read as rays.
         */
        private void buildGear(int teeth, float outerR, float innerR, float cx, float cy) {
            final float halfOuter = 0.30f;   // angular half-width at the tip, radians
            final float halfInner = 0.18f;   // and at the root: the taper is the tooth
            for (int i = 0; i < teeth; i++) {
                float mid = (float) (2 * Math.PI * i / teeth - Math.PI / 2);
                float a0 = mid - halfInner, a1 = mid - halfOuter;
                float a2 = mid + halfOuter, a3 = mid + halfInner;
                float x0 = cx + (float) Math.cos(a0) * innerR, y0 = cy + (float) Math.sin(a0) * innerR;
                float x1 = cx + (float) Math.cos(a1) * outerR, y1 = cy + (float) Math.sin(a1) * outerR;
                float x2 = cx + (float) Math.cos(a2) * outerR, y2 = cy + (float) Math.sin(a2) * outerR;
                float x3 = cx + (float) Math.cos(a3) * innerR, y3 = cy + (float) Math.sin(a3) * innerR;
                if (i == 0) p.moveTo(x0, y0);
                else p.lineTo(x0, y0);
                p.lineTo(x1, y1);
                p.lineTo(x2, y2);
                p.lineTo(x3, y3);
                // Connect to the next tooth's root along the inner circle, so the outline is
                // continuous instead of eight floating shapes.
                float next = (float) (2 * Math.PI * (i + 1) / teeth - Math.PI / 2) - halfInner;
                p.lineTo(cx + (float) Math.cos(next) * innerR,
                        cy + (float) Math.sin(next) * innerR);
            }
            p.close();
        }

        @Override public void draw(android.graphics.Canvas c) {
            // Scale the 24-unit viewport onto whatever the ImageView gives us.
            int w = getBounds().width(), h = getBounds().height();
            float s = Math.min(w, h) / 24f;
            c.save();
            c.translate(getBounds().left + (w - 24 * s) / 2f,
                    getBounds().top + (h - 24 * s) / 2f);
            c.scale(s, s);
            c.drawPath(p, paint);
            c.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) {
            paint.setColorFilter(cf);
        }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    // ------------------------------------------------------------------ shared looks

    /** The flat rounded plate WT uses for tabs and dialogs. */
    static android.graphics.drawable.Drawable plate(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp);
        return d;
    }

    /** A labelled row of the settings sheet: title line, optional hint, optional control. */
    static View row(Context context, String title, String hint) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(context, 10), 0, dp(context, 6));

        TextView t = new TextView(context);
        t.setText(title);
        t.setTextColor(0xffe8eaee);
        t.setTextSize(14);
        UiFont.apply(t, false);
        col.addView(t);

        if (hint != null) {
            TextView h = new TextView(context);
            h.setText(hint);
            h.setTextColor(0xff8a8f99);
            h.setTextSize(11);
            h.setGravity(Gravity.START);
            col.addView(h);
        }
        return col;
    }

    static int dp(Context context, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, context.getResources().getDisplayMetrics()));
    }
}
