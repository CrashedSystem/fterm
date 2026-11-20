package io.github.crashedsystem.fterm;

import android.widget.LinearLayout;

/**
 * WT's 시작 page: rows that mostly name a default and show its value on the right.
 *
 * <p>The two rows that hold a value open a one-line prompt instead of a picker, because the
 * value is a path and the path is already the thing the user thinks in. See
 * {@link SettingsUi#prompt}.
 */
final class SettingsStartPage extends SettingsPage {

    SettingsStartPage(SettingsSheet sheet) {
        super(sheet);
    }

    @Override
    void build(LinearLayout col) {
        LinearLayout card = ui.card(col);

        card.addView(ui.valueRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0308), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0309),
                Profiles.DEFAULT_NAME, null, false));
        card.addView(ui.valueRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0310), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0311),
                host.startDir().isEmpty() ? io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0312) : host.startDir(),
                this::editStartDir, false));
        card.addView(ui.valueRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0313), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0314),
                host.startShell().isEmpty() ? io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0315) : host.startShell(),
                this::editStartShell, false));
        card.addView(ui.toggleCardRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0316), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0317),
                host.keyboardOnStart(), host::onKeyboardOnStart, true));

        ui.hint(col, io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0318)
                + io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0319));
    }

    private void editStartDir() {
        ui.prompt(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0310), host.startDir(), "/data/data/com.termux/files/home",
                io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0320)
                        + io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0321),
                v -> {
                    host.onStartDir(v);
                    render();
                });
    }

    private void editStartShell() {
        ui.prompt(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0313), host.startShell(), "/data/data/com.termux/files/usr/bin/bash",
                io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0322),
                v -> {
                    host.onStartShell(v);
                    render();
                });
    }
}
