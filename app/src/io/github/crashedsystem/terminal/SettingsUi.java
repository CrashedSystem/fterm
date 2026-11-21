package io.github.crashedsystem.terminal;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The palette and the row shapes every settings page is built from.
 *
 * <p>WT's settings are cards of labelled rows: the value sits at the right of its own row, and
 * the row itself is the control. That shape is the same on every page, so it is written
 * once here instead of being copied into each page file. Nothing in this class knows what a
 * setting is - it draws, it measures, and it asks the activity for the platform's ripple.
 *
 * <p>The colours are WT's settings window: a dark surface, cards a step lighter, one accent.
 * They are instance fields rather than constants of the sheet because a page reaches them
 * through the one {@code ui} it was handed, and a colour change should be one edit in one file.
 */
final class SettingsUi {

    // WT's settings window: a dark surface, cards a step lighter, one accent colour.
    final int WINDOW = 0xff1f2126;
    final int CARD = 0xff2a2d33;
    final int CARD_HI = 0xff34383f;
    final int TEXT = 0xffe8eaee;
    final int HINT = 0xff8a8f99;
    final int HAIRLINE = 0xff34383f;
    final int ACCENT = 0xff4a9eff;

    private final Activity mActivity;

    SettingsUi(Activity activity) {
        mActivity = activity;
    }

    // ------------------------------------------------------------------ cards

    /** A WT card: rounded, one step lighter than the window, with a small inset. */
    LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(mActivity);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Chrome.plate(CARD, 8));
        c.setPadding(dp(12), dp(2), dp(12), dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        parent.addView(c, lp);
        return c;
    }

    View cardTitle(String title) {
        TextView t = new TextView(mActivity);
        t.setText(title);
        t.setTextColor(HINT);
        t.setTextSize(11);
        t.setLetterSpacing(0.06f);
        t.setPadding(0, dp(9), 0, dp(7));
        UiFont.apply(t, true);
        return t;
    }

    /** Appends a row plus its hairline; the last row of a card has no trailing rule. */
    View cardRow(View row, boolean last) {
        LinearLayout wrap = new LinearLayout(mActivity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (!last) wrap.addView(hairline(0, 0));
        return wrap;
    }

    // ------------------------------------------------------------------ rows

    /**
     * A row that names a value: title and hint on the left, the value on the right.
     *
     * <p>This is the shape of every row on WT's 시작 page, and the reason it does not read as
     * a form: the value carries its own affordance (a chevron or a toggle) instead of sitting
     * in a separate button column.
     */
    View valueRow(String name, String sub, String value, Runnable onClick, boolean last) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        if (onClick != null) {
            row.setClickable(true);
            row.setBackground(touchable());
            row.setOnClickListener(v -> onClick.run());
        }

        LinearLayout text = new LinearLayout(mActivity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(mActivity);
        t.setText(name);
        t.setTextColor(TEXT);
        t.setTextSize(13.5f);
        UiFont.apply(t, false);
        text.addView(t);

        if (sub != null) {
            TextView h = new TextView(mActivity);
            h.setText(sub);
            h.setTextColor(HINT);
            h.setTextSize(10.5f);
            h.setLineSpacing(dp(1), 1f);
            text.addView(h);
        }
        row.addView(text);

        if (value != null) {
            TextView v = new TextView(mActivity);
            v.setText(value);
            v.setTextColor(onClick == null ? HINT : TEXT);
            v.setTextSize(12);
            v.setSingleLine(true);
            v.setGravity(Gravity.END);
            v.setPadding(dp(10), 0, 0, 0);
            UiFont.apply(v, false);
            row.addView(v);
        }
        if (onClick != null) {
            View more = Chrome.icon(mActivity, "right", 15, true, null);
            row.addView(more);
        }
        return cardRow(row, last);
    }

    /** A card row whose control is the sheet's own toggle, right-aligned. */
    View toggleCardRow(String name, String sub, boolean checked, Chrome.OnToggle on,
                       boolean last) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout text = new LinearLayout(mActivity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(mActivity);
        t.setText(name);
        t.setTextColor(TEXT);
        t.setTextSize(13.5f);
        UiFont.apply(t, false);
        text.addView(t);
        if (sub != null) {
            TextView h = new TextView(mActivity);
            h.setText(sub);
            h.setTextColor(HINT);
            h.setTextSize(10.5f);
            text.addView(h);
        }
        row.addView(text);
        row.addView(Chrome.toggle(mActivity, checked, on));
        return cardRow(row, last);
    }

    /** A paragraph of gray text under the cards, where WT puts its explanatory notes. */
    void hint(LinearLayout parent, String text) {
        TextView t = new TextView(mActivity);
        t.setText(text);
        t.setTextColor(HINT);
        t.setTextSize(10.5f);
        t.setLineSpacing(dp(2), 1f);
        t.setPadding(dp(4), dp(2), dp(4), 0);
        parent.addView(t);
    }

    TextView label(String text) {
        TextView t = new TextView(mActivity);
        t.setText(text);
        t.setTextColor(HINT);
        t.setTextSize(10.5f);
        t.setPadding(0, dp(8), 0, dp(2));
        return t;
    }

    EditText field(String value, String placeholder) {
        EditText f = new EditText(mActivity);
        f.setText(value);
        f.setSingleLine(true);
        f.setTextSize(13);
        f.setTypeface(Typeface.MONOSPACE);
        f.setTextColor(TEXT);
        f.setHintTextColor(HINT);
        f.setHint(placeholder);
        return f;
    }

    // ------------------------------------------------------------------ dialogs

    /**
     * A one-line text prompt, used by the rows that hold a path or an address.
     *
     * <p>WT opens a file picker for the directory; a phone has no such picker that reaches
     * the Termux home, and the path is already the thing the user thinks in, so the field is
     * the path itself. Empty means "the daemon's default", which is why the hint says so.
     */
    void prompt(String title, String value, String placeholder, String noteText,
                java.util.function.Consumer<String> onOk) {
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);

        final EditText field = new EditText(mActivity);
        field.setText(value);
        field.setSingleLine(true);
        field.setTextSize(14);
        field.setTypeface(Typeface.MONOSPACE);
        field.setTextColor(TEXT);
        field.setHintTextColor(HINT);
        field.setHint(placeholder);
        box.addView(field);

        TextView note = new TextView(mActivity);
        note.setText(noteText);
        note.setTextColor(HINT);
        note.setTextSize(10.5f);
        note.setPadding(0, dp(6), 0, 0);
        box.addView(note);

        new android.app.AlertDialog.Builder(mActivity)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0057), (d, w) -> onOk.accept(field.getText().toString().trim()))
                .setNegativeButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0058), null)
                .setNeutralButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0323), (d, w) -> onOk.accept(""))
                .show();
    }

    // ------------------------------------------------------------------ helpers

    /** Accepts "#rrggbb", "rrggbb" and "#rgb"; returns null when it is not a colour. */
    static Integer parseHex(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 3) {
            t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1)
                    + t.charAt(2) + t.charAt(2);
        }
        if (t.length() != 6) return null;
        try {
            return 0xff000000 | Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    View hairline(int leftInset, int rightInset) {
        View line = new View(mActivity);
        line.setBackgroundColor(HAIRLINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f)));
        lp.leftMargin = leftInset;
        lp.rightMargin = rightInset;
        line.setLayoutParams(lp);
        return line;
    }

    /** The platform ripple, so a tap is acknowledged without repainting the card. */
    Drawable touchable() {
        TypedValue tv = new TypedValue();
        if (mActivity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, tv, true)) {
            return mActivity.getDrawable(tv.resourceId);
        }
        return null;
    }

    /** The ripple, plus a quiet fill so the chosen cell is visible before it is touched. */
    Drawable touchableSelected() {
        Drawable base = touchable();
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0x224a9eff);
        fill.setCornerRadius(dp(8));
        if (base == null) return fill;
        return new LayerDrawable(new Drawable[]{fill, base});
    }

    int dp(float v) {
        Resources r = mActivity.getResources();
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, r.getDisplayMetrics()));
    }

    /**
     * A list height that fits the screen it is on.
     *
     * <p>A fixed 340dp is right on a phone held upright and wrong twice elsewhere: on a tablet
     * it leaves two thirds of the dialog empty, and in landscape - where the window is often
     * half the height - it runs past the bottom and the buttons are off screen. The cap is the
     * smaller of the preferred height and most of what is actually available.
     */
    int listHeight(float preferredDp, float fraction) {
        int available = mActivity.getResources().getDisplayMetrics().heightPixels;
        return Math.min(dp(preferredDp), Math.round(available * fraction));
    }
}
