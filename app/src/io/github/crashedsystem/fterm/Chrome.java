package io.github.crashedsystem.fterm;

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

        /** The user's profiles, in menu order. Never null. */
        java.util.List<Profiles.Profile> profiles();

        /** Open a tab from a profile. */
        void openProfile(Profiles.Profile profile);

        /** The settings page that edits profiles. */
        void manageProfiles();

        /** Open the find bar over the terminal. */
        void find();
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

    /** The palette while it is on screen, so a rotation can re-size it; null when closed. */
    private android.app.Dialog mPalette;

    Chrome(Activity activity, Commands commands) {
        mActivity = activity;
        mCommands = commands;
    }

    /** What a rename dialog hands back once the user accepts a name. */
    interface NameInput {
        void onName(String name);
    }

    /**
     * WT's "Rename tab" dialog.
     *
     * <p>A dialog rather than an in-place editor because a tab's title is the shell's until
     * the user overrides it, and an inline field would have to fight the OSC title for the
     * same row. What the new name means to the tab is the caller's business.
     */
    static void renameTab(Activity activity, String current, final NameInput apply) {
        final EditText input = new EditText(activity);
        input.setText(current);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        new android.app.AlertDialog.Builder(activity)
                .setTitle(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0056))
                .setView(input)
                .setPositiveButton(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0057), (d, w) ->
                        apply.onName(input.getText().toString().trim()))
                .setNegativeButton(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0058), null)
                .show();
    }

    /** Re-apply the palette's window size after the window changed (rotation, DeX resize). */
    void relayout() {
        if (mPalette != null && mPalette.isShowing()) DialogSize.palette(mPalette, mActivity);
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
                new Entry(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0059), mCommands::newTab),
                new Entry(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0060), mCommands::closeTab),
                new Entry(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0061), mCommands::renameTab),
                new Entry(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0062), mCommands::nextTab),
                new Entry(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0032), mCommands::paste),
                new Entry(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0031), mCommands::copy),
                new Entry(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0063), mCommands::find),
                new Entry(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0064), mCommands::biggerFont),
                new Entry(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0065), mCommands::smallerFont),
                new Entry(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0066), mCommands::settings),
                new Entry(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0067), mCommands::toggleKeyboard),
                new Entry(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0024), mCommands::about),
        };

        final android.widget.ArrayAdapter<Entry> adapter =
                new android.widget.ArrayAdapter<>(mActivity,
                        android.R.layout.simple_list_item_1, entries);
        final android.widget.ListView list = new android.widget.ListView(mActivity);
        list.setAdapter(adapter);
        list.setDividerHeight(0);
        list.setPadding(0, dp(mActivity, 2), 0, dp(mActivity, 2));

        final EditText filter = new EditText(mActivity);
        filter.setHint(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0068));
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
        mPalette = dialog;
        // The window is what gives the list its height; without this it collapses to a stub in
        // the middle of the screen and the rows at the bottom are unreachable. The width is
        // capped as well, so a 12" screen gets a column of commands instead of a list whose
        // labels sit at the far left of a metre of empty space.
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            // Translucent, so the palette's own rounded plate is the surface on screen.
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
        }
        DialogSize.palette(dialog, mActivity);
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
    static final class Row {
        final String glyph;
        final String label;
        final String shortcut;
        final Runnable action;
        /** A rule instead of a row: the divider between groups. */
        final boolean divider;

        Row(String glyph, String label, String shortcut, Runnable action, boolean divider) {
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
    /**
     * The menu a long-pressed tab opens.
     *
     * <p>Drawn with the same rows, plate and divider as {@link #menu(View)}: a tab menu that
     * used the platform's AlertDialog list was a second visual language in the same app, and
     * the one thing a menu must not do is look like it came from somewhere else.
     *
     * @param title  the tab's own title, used as the plate's heading
     * @param rows   the entries, already filtered by the caller to those that apply
     */
    void tabMenu(String title, final Row[] rows) {
        LinearLayout list = new LinearLayout(mActivity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(mActivity, 4), 0, dp(mActivity, 4));
        list.setBackground(plate(SHEET_BG, 10));

        final android.app.Dialog dialog = new android.app.Dialog(mActivity);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        TextView heading = new TextView(mActivity);
        heading.setText(title);
        heading.setTextSize(11);
        heading.setTextColor(HINT);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.setPadding(dp(mActivity, 12), dp(mActivity, 8), dp(mActivity, 12),
                dp(mActivity, 6));
        UiFont.apply(heading, true);
        list.addView(heading);
        list.addView(rule());

        for (Row r : rows) list.addView(menuRow(r, dialog));

        dialog.setContentView(list);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        // Centred, not hung off the tab: the tab is somewhere along a scrolling strip, and a
        // dropdown anchored to it would open off-screen for the first and last tabs.
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout(dp(mActivity, 240), ViewGroup.LayoutParams.WRAP_CONTENT);
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.gravity = Gravity.CENTER;
            w.setAttributes(lp);
        }
    }

    /** A divider, the same one the dropdown uses between groups. */
    private View rule() {
        View line = new View(mActivity);
        line.setBackgroundColor(HAIRLINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(mActivity, 0.8f)));
        lp.leftMargin = dp(mActivity, 10);
        lp.rightMargin = dp(mActivity, 10);
        lp.topMargin = dp(mActivity, 4);
        lp.bottomMargin = dp(mActivity, 4);
        line.setLayoutParams(lp);
        return line;
    }

    void menu(View anchor) {
        final Row[] rows = {
                Row.of("terminal", "새 탭", "Ctrl+Shift+T", mCommands::newTab),
                Row.of("pencil", "탭 이름 바꾸기", null, mCommands::renameTab),
                Row.of("close", "탭 닫기", "Ctrl+Shift+W", mCommands::closeTab),
                Row.rule(),
                Row.of("clipboard", "붙여넣기", "Ctrl+Shift+V", mCommands::paste),
                Row.of("copy", "복사", "Ctrl+Shift+C", mCommands::copy),
                Row.of("search", "찾기", "Ctrl+Shift+F", mCommands::find),
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
        // tab bar is the thing a tab IS. The default profile is always first; the user's own
        // follow, which is what makes an ssh profile one tap from the tab bar.
        list.addView(profileRow(dialog, null));
        java.util.List<Profiles.Profile> profiles = mCommands.profiles();
        for (Profiles.Profile p : profiles) list.addView(profileRow(dialog, p));
        list.addView(profileRow(dialog, MANAGE));

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
            // Just under the chevron that opened it, right-aligned like a dropdown hangs off
            // its button. WT's flyout opens below the split button's arrow, not at the screen
            // edge, so the sheet stays where the finger already is.
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = dp(mActivity, 4);
            lp.y = dp(mActivity, 42);
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
    /** A sentinel profile that opens the editor instead of a tab. */
    private static final Profiles.Profile MANAGE =
            new Profiles.Profile("프로필 관리", "", "", "");

    private View profileRow(final android.app.Dialog dialog, final Profiles.Profile profile) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(mActivity, 12), dp(mActivity, 8), dp(mActivity, 12),
                dp(mActivity, 8));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            dialog.dismiss();
            if (profile == MANAGE) mCommands.manageProfiles();
            else mCommands.openProfile(profile);
        });

        android.widget.ImageView plate = new android.widget.ImageView(mActivity);
        // The profile's own mark when it has one: a profile is exactly the thing worth
        // marking, and an ssh glyph on an opencode profile is the wrong signal. "프로필 관리"
        // and the default profile have no stored icon, so they keep their glyphs.
        String mark = profile == null ? "terminal"
                : profile == MANAGE ? "pencil"
                : profile.icon == null || profile.icon.isEmpty() ? "ssh" : profile.icon;
        plate.setImageDrawable(TabIcons.isGlyph(mark) ? new Glyph(mark, ICON)
                : new Glyph("terminal", ICON));
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
        name.setText(profile == null ? Profiles.DEFAULT_NAME
                : profile.name.isEmpty() ? "(이름 없음)" : profile.name);
        name.setTextColor(TEXT);
        name.setTextSize(13.5f);
        UiFont.apply(name, true);
        text.addView(name);
        TextView sub = new TextView(mActivity);
        sub.setText(profile == null ? "Termux · bash -l"
                : profile == MANAGE ? "탭을 열 때 쓰는 프로필 편집"
                : profile.subtitle().isEmpty() ? "기본 셸" : profile.subtitle());
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

    /**
     * A chrome icon: a filled Fluent path, tinted, scaled from a 24-unit grid.
     *
     * <p>The paths live in {@link GlyphPaths}, generated from microsoft/fluentui-system-icons -
     * the family WT's own toolbar uses. They are filled outlines rather than strokes, which is
     * the difference between an icon set that belongs next to Windows Terminal and one that
     * merely resembles it.
     */
    static final class Glyph extends android.graphics.drawable.Drawable {
        private final android.graphics.Path p = new android.graphics.Path();
        private final android.graphics.Path accent = new android.graphics.Path();
        private final android.graphics.Paint paint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint accentPaint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);

        /** Package-private so the tab chip can draw the same glyph set at its own tint. */
        Glyph(String name, int color) {
            paint.setColor(color);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            GlyphPaths.build(name, p);
            if (p.isEmpty()) p.addCircle(12, 12, 7, android.graphics.Path.Direction.CW);
            // A two-tone source mark: the second path is the darker half of the same shape,
            // so it is the tint mixed toward the background rather than a second colour. A
            // separate hue would be a brand colour the caller never asked for, and this
            // drawable is used at several tints.
            GlyphPaths.buildAccent(name, accent);
            if (!accent.isEmpty()) {
                accentPaint.setColor(color);
                accentPaint.setStyle(android.graphics.Paint.Style.FILL);
                accentPaint.setAlpha(0x55);
            }
        }

        @Override public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            // Centre a square: an icon in a wider box must not be stretched.
            float s = Math.min(b.width(), b.height()) / GlyphPaths.GRID;
            float dx = b.left + (b.width() - GlyphPaths.GRID * s) / 2f;
            float dy = b.top + (b.height() - GlyphPaths.GRID * s) / 2f;
            c.save();
            c.translate(dx, dy);
            c.scale(s, s);
            c.drawPath(p, paint);
            if (!accent.isEmpty()) c.drawPath(accent, accentPaint);
            c.restore();
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            if (!accent.isEmpty()) accentPaint.setAlpha(alpha * 0x55 / 0xff);
        }
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
