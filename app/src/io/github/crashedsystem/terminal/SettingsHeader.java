package io.github.crashedsystem.terminal;

import android.app.Activity;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The sheet's header: back arrow or app mark, the breadcrumb, and the close glyph.
 *
 * <p>WT's page header is a trail with the ancestors dimmed and the leaf bright. There is one
 * level here, so the trail is the page name alone unless we are at the root.
 *
 * <p>Split out of {@link SettingsSheet} because the sheet is a router and this is a drawing
 * problem - and because the router was over the line budget the structure test enforces.
 */
final class SettingsHeader {

    private SettingsHeader() {
    }

    /**
     * @param page     the current page key, or null at the root
     * @param onBack   the arrow at the root's left; unused when {@code page} is null
     * @param onClose  the glyph on the right
     */
    static View build(Activity activity, SettingsUi ui, String page,
                      Runnable onBack, Runnable onClose) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));

        if (page != null) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ui.dp(22), ui.dp(22));
            blp.leftMargin = ui.dp(4);
            blp.rightMargin = ui.dp(8);
            row.addView(Chrome.icon(activity, "left", 22, false, v -> onBack.run()), blp);
        } else {
            View mark = Chrome.icon(activity, "gear", 22, false, null);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22));
            mlp.rightMargin = ui.dp(6);
            mark.setLayoutParams(mlp);
            row.addView(mark);
        }

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView crumb = new TextView(activity);
        // One Spannable rather than two concatenated resources: the ancestor/leaf distinction
        // is a colour, and concatenating was also what made the trail depend on leading and
        // trailing spaces - which aapt2 trims, so "설정 ›" and the page name ran together.
        if (page == null) {
            crumb.setText(AppStrings.s(R.string.i18n_0306));
        } else {
            String ancestor = AppStrings.s(R.string.i18n_0306);
            String separator = "  \u203a  ";
            String leaf = titleOf(page);
            SpannableString trail = new SpannableString(ancestor + separator + leaf);
            trail.setSpan(new ForegroundColorSpan(ui.HINT), 0, ancestor.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            trail.setSpan(new ForegroundColorSpan(ui.TEXT),
                    ancestor.length() + separator.length(), trail.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            crumb.setText(trail);
        }
        crumb.setTextColor(ui.TEXT);
        crumb.setTextSize(page == null ? 18 : 13);
        crumb.setSingleLine(true);
        crumb.setEllipsize(TextUtils.TruncateAt.END);
        UiFont.apply(crumb, page == null);
        titles.addView(crumb);
        row.addView(titles);

        row.addView(Chrome.icon(activity, "close", 20, false, v -> onClose.run()));
        return row;
    }

    /** The page's name in the trail. */
    static String titleOf(String page) {
        switch (page) {
            case "start": return AppStrings.s(R.string.i18n_0241);
            case "profiles": return AppStrings.s(R.string.i18n_0240);
            case "appearance": return AppStrings.s(R.string.i18n_0244);
            case "schemes": return AppStrings.s(R.string.i18n_0128);
            case "interaction": return AppStrings.s(R.string.i18n_0247);
            case "language": return AppStrings.s(R.string.i18n_0388);
            default: return AppStrings.s(R.string.i18n_0024);
        }
    }
}
