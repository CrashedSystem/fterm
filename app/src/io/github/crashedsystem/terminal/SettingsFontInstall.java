package io.github.crashedsystem.terminal;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The import dialog: device picker, a URL Termux fetches, and what Termux already has.
 *
 * <p>See {@link #show()}. State lives in the picker this borrows; this class is the flow.
 */
final class SettingsFontInstall {

    private final SettingsFontPicker picker;

    SettingsFontInstall(SettingsFontPicker picker) {
        this.picker = picker;
    }

    /**
     * Every way a font gets in, in one dialog.
     *
     * <p>Three answers, and they are not equally easy. A file already on the phone goes
     * through the system picker. A URL is fetched by Termux, which already has curl and can
     * see {@code ~/.fonts} - one step instead of three apps. The Termux list is a socket
     * round trip, so it is asked for on a worker thread; doing it here is the freeze that
     * makes a settings screen feel broken.
     *
     * <p>Split out of {@link SettingsFontPicker} so that class holds the pickers and this one
     * holds the import flow - the structure test enforces the size, but the split is real:
     * these are different jobs.
     */
    void show() {
        LinearLayout box = new LinearLayout(picker.mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(picker.mUi.dp(8), picker.mUi.dp(6), picker.mUi.dp(8), 0);

        LinearLayout status = new LinearLayout(picker.mActivity);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(picker.mUi.dp(12), picker.mUi.dp(4), picker.mUi.dp(12), picker.mUi.dp(8));
        box.addView(status);

        TextView loading = new TextView(picker.mActivity);
        loading.setText(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0181));
        loading.setTextColor(picker.mUi.HINT);
        loading.setTextSize(12);
        status.addView(loading);

        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];

        LinearLayout device = new LinearLayout(picker.mActivity);
        device.setOrientation(LinearLayout.HORIZONTAL);
        device.setGravity(Gravity.CENTER_VERTICAL);
        device.setPadding(picker.mUi.dp(12), picker.mUi.dp(12), picker.mUi.dp(12), picker.mUi.dp(12));
        device.setBackground(picker.mUi.touchable());
        device.setClickable(true);
        device.addView(Chrome.icon(picker.mActivity, "folder", 16, false, null));
        TextView deviceLabel = new TextView(picker.mActivity);
        deviceLabel.setText(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0182));
        deviceLabel.setTextColor(picker.mUi.TEXT);
        deviceLabel.setTextSize(13);
        deviceLabel.setPadding(picker.mUi.dp(10), 0, 0, 0);
        device.addView(deviceLabel);
        device.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            picker.fontImport();
        });
        box.addView(device);
        box.addView(picker.mUi.hairline(picker.mUi.dp(12), picker.mUi.dp(0)));

        LinearLayout url = new LinearLayout(picker.mActivity);
        url.setOrientation(LinearLayout.HORIZONTAL);
        url.setGravity(Gravity.CENTER_VERTICAL);
        url.setPadding(picker.mUi.dp(12), picker.mUi.dp(12), picker.mUi.dp(12), picker.mUi.dp(12));
        url.setBackground(picker.mUi.touchable());
        url.setClickable(true);
        url.addView(Chrome.icon(picker.mActivity, "cloud", 16, false, null));
        TextView urlLabel = new TextView(picker.mActivity);
        urlLabel.setText(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0183));
        urlLabel.setTextColor(picker.mUi.TEXT);
        urlLabel.setTextSize(13);
        urlLabel.setPadding(picker.mUi.dp(10), 0, 0, 0);
        url.addView(urlLabel);
        url.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            fontFromUrl();
        });
        box.addView(url);
        box.addView(picker.mUi.hairline(picker.mUi.dp(12), picker.mUi.dp(0)));

        ScrollView scroll = new ScrollView(picker.mActivity);
        scroll.addView(box);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, picker.mUi.listHeight(360, 0.62f)));

        holder[0] = new android.app.AlertDialog.Builder(picker.mActivity)
                .setTitle(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0184))
                .setView(scroll)
                .setNegativeButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0029), null)
                .create();
        holder[0].show();

        new Thread(() -> {
            final java.util.List<com.termux.terminal.DaemonClient.RemoteFont> remote;
            final String error;
            try {
                remote = com.termux.terminal.DaemonClient.fonts(
                        picker.mHost.daemonHost(), picker.mHost.daemonPort(), picker.mHost.daemonToken(), 4000);
                error = null;
            } catch (com.termux.terminal.DaemonClient.Failure f) {
                picker.mActivity.runOnUiThread(() -> reportFonts(status, loading, null, f.getMessage()));
                return;
            }
            picker.mActivity.runOnUiThread(() -> reportFonts(status, loading, remote, error));
        }, "fterm-font-list").start();
    }

    /**
     * Replace the "looking…" line with what Termux has, or with why it could not be asked.
     *
     * <p>A daemon that cannot be reached is not an error worth a modal: the other two ways in
     * still work, so the message says so and stays out of the way.
     */
    private void reportFonts(LinearLayout status, TextView loading,
                             java.util.List<com.termux.terminal.DaemonClient.RemoteFont> fonts,
                             String error) {
        status.removeAllViews();
        if (error != null) {
            TextView t = new TextView(picker.mActivity);
            t.setText(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0185) + error);
            t.setTextColor(picker.mUi.HINT);
            t.setTextSize(11);
            status.addView(t);
            return;
        }
        if (fonts == null || fonts.isEmpty()) {
            TextView t = new TextView(picker.mActivity);
            t.setText(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0186)
                    + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0187));
            t.setTextColor(picker.mUi.HINT);
            t.setTextSize(11);
            status.addView(t);
            return;
        }
        TextView head = new TextView(picker.mActivity);
        head.setText(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0188) + fonts.size() + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0189));
        head.setTextColor(picker.mUi.HINT);
        head.setTextSize(11);
        status.addView(head);
        for (com.termux.terminal.DaemonClient.RemoteFont f : fonts) {
            LinearLayout row = new LinearLayout(picker.mActivity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(picker.mUi.dp(12), picker.mUi.dp(10), picker.mUi.dp(12), picker.mUi.dp(10));
            row.setBackground(picker.mUi.touchable());
            row.setClickable(true);

            TextView name = new TextView(picker.mActivity);
            name.setText(f.name());
            name.setTextColor(picker.mUi.TEXT);
            name.setTextSize(12.5f);
            name.setSingleLine(true);
            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(name);

            TextView size = new TextView(picker.mActivity);
            size.setText((f.size / 1024) + " KB");
            size.setTextColor(picker.mUi.HINT);
            size.setTextSize(10.5f);
            row.addView(size);

            row.setOnClickListener(v -> pullFromTermux(f));
            status.addView(row);
        }
    }

    /** Fetch one Termux-side font and install it as if it had been picked locally. */
    private void pullFromTermux(com.termux.terminal.DaemonClient.RemoteFont f) {
        android.widget.Toast.makeText(picker.mActivity, f.name() + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0190),
                android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final Fonts.Import result;
            try {
                byte[] data = com.termux.terminal.DaemonClient.font(
                        picker.mHost.daemonHost(), picker.mHost.daemonPort(), picker.mHost.daemonToken(),
                        f.path, 8000);
                result = Fonts.install(picker.mActivity, f.name(), data);
            } catch (com.termux.terminal.DaemonClient.Failure e) {
                picker.mActivity.runOnUiThread(() -> showFontError(e.getMessage()));
                return;
            }
            picker.mActivity.runOnUiThread(() -> applyImported(result));
        }, "fterm-font-get").start();
    }

    /** A URL for the daemon to download, because this app has no way to fetch one itself. */
    private void fontFromUrl() {
        picker.mUi.prompt(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0191), "", "https://example.com/MyFont.ttf",
                io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0192)
                        + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0193),
                v -> {
                    if (v.isEmpty()) return;
                    if (!v.startsWith("http://") && !v.startsWith("https://")) {
                        showFontError(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0194));
                        return;
                    }
                    android.widget.Toast.makeText(picker.mActivity, io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0195),
                            android.widget.Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        final Fonts.Import result;
                        try {
                            String path = com.termux.terminal.DaemonClient.fetchFont(
                                    picker.mHost.daemonHost(), picker.mHost.daemonPort(), picker.mHost.daemonToken(),
                                    v, 12000);
                            byte[] data = com.termux.terminal.DaemonClient.font(
                                    picker.mHost.daemonHost(), picker.mHost.daemonPort(), picker.mHost.daemonToken(),
                                    path, 8000);
                            String name = path.substring(path.lastIndexOf('/') + 1);
                            result = Fonts.install(picker.mActivity, name, data);
                        } catch (com.termux.terminal.DaemonClient.Failure e) {
                            picker.mActivity.runOnUiThread(() -> showFontError(e.getMessage()));
                            return;
                        }
                        picker.mActivity.runOnUiThread(() -> applyImported(result));
                    }, "fterm-font-url").start();
                });
    }

    private void showFontError(String message) {
        new android.app.AlertDialog.Builder(picker.mActivity)
                .setTitle(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0124))
                .setMessage(message)
                .setPositiveButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0172), null)
                .show();
    }

    /**
     * Install a font, select it, and reopen the picker it belongs in.
     *
     * <p>A Korean font is proportional, so the Latin list can never contain it - reopening
     * that list after installing one would show a list the new row is not in and read as a
     * failed install. The rule is the same one {@link Fonts#adopt} uses to accept the file.
     */
    /** Entry point for the activity's picker result; see the picker's onFontImportResult. */
    void onImported(Fonts.Import imported) {
        applyImported(imported);
    }

    private void applyImported(Fonts.Import imported) {
        if (imported.id == null) {
            showFontError(imported.reason);
            return;
        }
        // Three destinations, not two. A CJK face is proportional like a Korean one, so the
        // old "latin or hangul" split filed a Japanese font as the Korean face - which then
        // rendered nothing, because it has no Hangul glyphs. The file is asked what it
        // actually covers, in the order that matches the pickers.
        boolean latin = Fonts.isLatinFace(imported.id);
        if (latin) {
            picker.mHost.onFontFamily(imported.id);
            picker.mSheet.render();
            picker.chooseFont();
            return;
        }
        String path = imported.id.startsWith(Fonts.FILE_PREFIX)
                ? imported.id.substring(Fonts.FILE_PREFIX.length()) : null;
        java.io.File file = path == null ? null : new java.io.File(path);
        if (file != null && Fonts.hasHangul(file) && !Fonts.hasCjk(file)) {
            picker.mHost.onHangulFamily(imported.id);
            picker.mSheet.render();
            picker.chooseHangulFont();
            return;
        }
        if (file != null && Fonts.hasCjk(file)) {
            picker.mHost.onCjkFamily(imported.id);
            picker.mSheet.render();
            picker.chooseCjkFont();
            return;
        }
        // Neither: treat it as the Korean face, which is where a proportional font with no
        // CJK coverage belongs on this app.
        picker.mHost.onHangulFamily(imported.id);
        picker.mSheet.render();
        picker.chooseHangulFont();
    }

}
