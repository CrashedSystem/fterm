package io.github.crashedsystem.terminal;

import android.widget.LinearLayout;

/**
 * 상호 작용: the two bell toggles and the back-key choice.
 *
 * <p>Three rows, and the notes below them list the gestures that have no setting because they
 * are not meant to be configurable - a long press selects text, a long press on a tab closes
 * it, and the key bar's edge tabs reach F1..F12.
 */
final class SettingsInteractionPage extends SettingsPage {

    SettingsInteractionPage(SettingsSheet sheet) {
        super(sheet);
    }

    @Override
    void build(LinearLayout col) {
        LinearLayout card = ui.card(col);
        card.addView(ui.toggleCardRow(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0203), io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0204),
                host.bellVibrate(), on -> host.onBell(on, host.bellSound()), false));
        card.addView(ui.toggleCardRow(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0205), io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0206),
                host.bellSound(), on -> host.onBell(host.bellVibrate(), on), false));
        card.addView(ui.toggleCardRow(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0207), io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0208),
                host.backIsEscape(), host::onBackIsEscape, true));

        ui.hint(col, io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0209));
    }
}
