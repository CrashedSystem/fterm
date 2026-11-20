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

        card.addView(ui.valueRow(AppStrings.s(R.string.i18n_0308), AppStrings.s(R.string.i18n_0309),
                Profiles.DEFAULT_NAME, null, false));
        card.addView(ui.valueRow(AppStrings.s(R.string.i18n_0310), AppStrings.s(R.string.i18n_0311),
                host.startDir().isEmpty() ? AppStrings.s(R.string.i18n_0312) : host.startDir(),
                this::editStartDir, false));
        card.addView(ui.valueRow(AppStrings.s(R.string.i18n_0313), AppStrings.s(R.string.i18n_0314),
                host.startShell().isEmpty() ? AppStrings.s(R.string.i18n_0315) : host.startShell(),
                this::editStartShell, false));
        card.addView(ui.toggleCardRow(AppStrings.s(R.string.i18n_0316), AppStrings.s(R.string.i18n_0317),
                host.keyboardOnStart(), host::onKeyboardOnStart, true));

        ui.hint(col, AppStrings.s(R.string.i18n_0318)
                + AppStrings.s(R.string.i18n_0319));
    }

    private void editStartDir() {
        ui.prompt(AppStrings.s(R.string.i18n_0310), host.startDir(), "/data/data/com.termux/files/home",
                AppStrings.s(R.string.i18n_0320)
                        + AppStrings.s(R.string.i18n_0321),
                v -> {
                    host.onStartDir(v);
                    render();
                });
    }

    private void editStartShell() {
        ui.prompt(AppStrings.s(R.string.i18n_0313), host.startShell(), "/data/data/com.termux/files/usr/bin/bash",
                AppStrings.s(R.string.i18n_0322),
                v -> {
                    host.onStartShell(v);
                    render();
                });
    }
}
