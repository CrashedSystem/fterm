package io.github.crashedsystem.fterm;

import android.widget.LinearLayout;

/**
 * 정보: what is connected, and what this app is.
 *
 * <p>The session row is the host's own sentence, because the count and the address are the
 * activity's truth and not something a page should recompute.
 */
final class SettingsAboutPage extends SettingsPage {

    SettingsAboutPage(SettingsSheet sheet) {
        super(sheet);
    }

    @Override
    void build(LinearLayout col) {
        LinearLayout card = ui.card(col);
        card.addView(ui.valueRow(AppStrings.s(R.string.i18n_0113), host.statusLine(), null, null, false));
        card.addView(ui.valueRow(AppStrings.s(R.string.i18n_0114), AppStrings.s(R.string.i18n_0115), null, null, false));
        card.addView(ui.valueRow(AppStrings.s(R.string.i18n_0116), AppStrings.s(R.string.i18n_0117), null, null, true));

        ui.hint(col, AppStrings.s(R.string.i18n_0118)
                + AppStrings.s(R.string.i18n_0119));
    }
}
