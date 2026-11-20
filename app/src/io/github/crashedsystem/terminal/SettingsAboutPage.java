package io.github.crashedsystem.terminal;

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
        card.addView(ui.valueRow(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0113), host.statusLine(), null, null, false));
        card.addView(ui.valueRow(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0114), io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0115), null, null, false));
        card.addView(ui.valueRow(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0116), io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0117), null, null, true));

        ui.hint(col, io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0118)
                + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0119));
    }
}
