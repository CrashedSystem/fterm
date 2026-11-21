package io.github.crashedsystem.terminal;

import android.app.LocaleManager;
import android.os.Build;
import android.os.LocaleList;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * 언어: which language this app runs in.
 *
 * <p>Two mechanisms, because the platform has two. From Android 13 the system keeps a
 * per-app locale ({@link LocaleManager}), which survives a restart, applies to this app only,
 * and is the same list the system's own "앱 언어" screen edits - so the two never disagree.
 * Before that there is no per-app locale at all and the value is stored here and pushed
 * through {@code Resources}, which is the closest equivalent the older platform offers.
 *
 * <p>Only languages with a {@code values-XX} directory are listed. Offering a language the app
 * has no strings for would do nothing visible and read as a broken setting.
 */
final class SettingsLanguagePage extends SettingsPage {

    /**
     * The languages that ship with the app; every one has a values-XX directory.
     *
     * <p>Chinese is listed by region because that is what a values directory can express
     * (values-zh-rCN, values-zh-rTW) and what LocaleManager round-trips: asking for "zh"
     * alone would leave the system picking a script, and the tick would land on neither row.
     */
    private static final String[] TAGS = {
            "en", "ko", "de", "es", "fr", "it", "ja", "pt", "ru", "sr", "uk", "zh-CN", "zh-TW"};

    SettingsLanguagePage(SettingsSheet sheet) {
        super(sheet);
    }

    @Override
    void build(LinearLayout col) {
        LinearLayout card = ui.card(col);
        String current = currentTag();
        for (int i = 0; i < TAGS.length; i++) {
            final String tag = TAGS[i];
            card.addView(ui.valueRow(nameOf(tag), tag,
                    tag.equals(current) ? "✓" : null,
                    () -> apply(tag), i == TAGS.length - 1));
        }

        ui.hint(col, AppStrings.s(R.string.i18n_0388));
        if (Build.VERSION.SDK_INT < 33) {
            ui.hint(col, AppStrings.s(R.string.i18n_0389));
        }
    }

    /** The language name in that language, which is how a picker should read. */
    private static String nameOf(String tag) {
        java.util.Locale locale = java.util.Locale.forLanguageTag(tag);
        String name = locale.getDisplayName(locale);
        return name.isEmpty() ? tag : name;
    }

    /**
     * The app's current language.
     *
     * <p>Asked of the platform rather than of our own preference: from API 33 the system owns
     * the value, and a stale copy here would show the wrong tick after the user changed it in
     * the system's screen.
     */
    private String currentTag() {
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager lm = activity.getSystemService(LocaleManager.class);
            if (lm != null) {
                LocaleList list = lm.getApplicationLocales();
                if (!list.isEmpty()) return matchTag(list.get(0));
            }
        }
        return matchTag(activity.getResources().getConfiguration()
                .getLocales().get(0));
    }

    /**
     * The row that represents this locale.
     *
     * <p>Compared by language and region rather than by {@code getLanguage()} alone: Chinese
     * is two rows (zh-CN and zh-TW) with the same language code, so the region is what tells
     * them apart. A system locale the app does not ship falls back to the row it resolves to,
     * which is English, so the tick never disappears from the list.
     */
    private static String matchTag(java.util.Locale locale) {
        String language = locale.getLanguage();
        String region = locale.getCountry();
        for (String tag : TAGS) {
            java.util.Locale candidate = java.util.Locale.forLanguageTag(tag);
            if (!candidate.getLanguage().equals(language)) continue;
            if (!candidate.getCountry().isEmpty() && !candidate.getCountry().equals(region)) continue;
            return tag;
        }
        return "en";
    }

    private void apply(String tag) {
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager lm = activity.getSystemService(LocaleManager.class);
            if (lm != null) {
                lm.setApplicationLocales(LocaleList.forLanguageTags(tag));
                // The activity is recreated by the platform with the new configuration, and
                // the settings sheet with it - which is what makes the change visible at once.
                return;
            }
        }
        Toast.makeText(activity, AppStrings.s(R.string.i18n_0390),
                Toast.LENGTH_LONG).show();
    }
}
