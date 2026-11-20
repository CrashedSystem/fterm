package io.github.crashedsystem.fterm;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Choosing and installing a face: the Latin picker, the Hangul picker, and the three ways a
 * font gets in.
 *
 * <p>These are dialogs rather than rows, and there are five of them chained - pick, install
 * from Termux, install from the device, install from a URL, and the error box - so they are a
 * class of their own. The 모양 page only names the two rows that open the first two.
 *
 * <p>One instance per sheet, because a file picked through {@code ACTION_OPEN_DOCUMENT} comes
 * back to the activity, and the activity hands it to the sheet, which hands it here: the object
 * that started the picker has to be the one that receives the result.
 */
final class SettingsFontPicker {

    private final SettingsSheet mSheet;
    private final SettingsSheet.Host mHost;
    private final Activity mActivity;
    private final SettingsUi mUi;

    SettingsFontPicker(SettingsSheet sheet) {
        mSheet = sheet;
        mHost = sheet.host();
        mActivity = sheet.activity();
        mUi = sheet.ui();
    }

    /**
     * The face picker: every fixed-advance font on the device, each rendered in itself.
     *
     * <p>Rendered in itself is the whole point - the only honest way to pick a monospace face
     * is to look at it, and a name tells you nothing about whether "M" is legible at 12dp.
     *
     * <p>The list is scanned, not enumerated, and it is long: this device has hundreds of
     * fonts and a handful are fixed-advance. So the scroll is capped, the current face is
     * pinned at the top, and there is a way to rescan after the user copies a file in.
     */
    void chooseFont() {
        final java.util.List<Fonts.Face> faces = Fonts.catalog(mActivity);
        String current = mHost.fontFamily();

        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(mUi.dp(8), mUi.dp(4), mUi.dp(8), 0);

        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];

        // One row for every way a font arrives, above the list rather than as a fourth dialog
        // button. An AlertDialog has three buttons and they are already spent on rescan/close;
        // more importantly, "how do I get a font in here" is the question the list itself
        // should answer, in the place the new font will appear.
        LinearLayout install = new LinearLayout(mActivity);
        install.setOrientation(LinearLayout.HORIZONTAL);
        install.setGravity(Gravity.CENTER_VERTICAL);
        install.setPadding(mUi.dp(12), mUi.dp(12), mUi.dp(12), mUi.dp(12));
        install.setBackground(mUi.touchable());
        install.setClickable(true);
        install.addView(Chrome.icon(mActivity, "folder", 18, false, null));
        TextView installLabel = new TextView(mActivity);
        installLabel.setText(AppStrings.s(R.string.i18n_0173));
        installLabel.setTextColor(mUi.ACCENT);
        installLabel.setTextSize(12.5f);
        installLabel.setPadding(mUi.dp(10), 0, 0, 0);
        install.addView(installLabel);
        install.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            installFont();
        });
        box.addView(install);
        box.addView(mUi.hairline(mUi.dp(12), mUi.dp(0)));

        for (int i = 0; i < faces.size(); i++) {
            final Fonts.Face f = faces.get(i);
            LinearLayout row = new LinearLayout(mActivity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(mUi.dp(12), mUi.dp(11), mUi.dp(12), mUi.dp(11));
            row.setBackground(mUi.touchable());
            row.setClickable(true);

            TextView sample = new TextView(mActivity);
            sample.setText(AppStrings.s(R.string.i18n_0174));
            sample.setTextSize(15);
            sample.setTextColor(mUi.TEXT);
            sample.setTypeface(faceFor(f));
            sample.setSingleLine(true);
            sample.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(sample);

            TextView name = new TextView(mActivity);
            name.setText(f.label);
            name.setTextSize(11);
            name.setTextColor(f.id.equals(current) ? mUi.ACCENT : mUi.HINT);
            name.setPadding(0, 0, mUi.dp(10), 0);
            row.addView(name);

            if (f.id.equals(current)) row.addView(Chrome.icon(mActivity, "check", 16, false, null));

            row.setOnClickListener(v -> {
                mHost.onFontFamily(f.id);
                if (holder[0] != null) holder[0].dismiss();
                mSheet.render();
            });
            box.addView(row);
            if (i < faces.size() - 1) box.addView(mUi.hairline(mUi.dp(12), mUi.dp(0)));
        }

        ScrollView scroll = new ScrollView(mActivity);
        scroll.setFillViewport(true);
        scroll.addView(box);
        // A cap, not a full-screen list: the dialog keeps its title and buttons visible while
        // the user scrolls, and a device with four hundred fonts does not become four hundred
        // rows of chrome.
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, mUi.dp(340)));

        holder[0] = new android.app.AlertDialog.Builder(mActivity)
                .setTitle(AppStrings.s(R.string.i18n_0124))
                .setView(scroll)
                .setPositiveButton(AppStrings.s(R.string.i18n_0175), (d, w) -> {
                    Fonts.rescan();
                    mSheet.render();
                    chooseFont();
                })
                .setNegativeButton(AppStrings.s(R.string.i18n_0029), null)
                .create();
        holder[0].show();
    }

    /**
     * The Korean face, from its own list.
     *
     * <p>A separate picker from {@link #chooseFont} because it is a separate question with a
     * separate filter. The Latin list is fixed-advance only; every Korean font on a real
     * device is proportional, so that list was structurally unable to contain one. Here the
     * only requirement is that the face answers Hangul, and the sample is a Korean word plus
     * conjoining jamo - the jamo line is what tells the user whether the face can follow an
     * IME, which the bundled Pretendard cannot.
     */
    void chooseHangulFont() {
        final java.util.List<Fonts.Face> faces = Fonts.hangulCatalog(mActivity);
        String current = mHost.hangulFamily();

        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(mUi.dp(8), mUi.dp(4), mUi.dp(8), 0);

        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];

        for (int i = 0; i < faces.size(); i++) {
            final Fonts.Face f = faces.get(i);
            LinearLayout row = new LinearLayout(mActivity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(mUi.dp(12), mUi.dp(11), mUi.dp(12), mUi.dp(11));
            row.setBackground(mUi.touchable());
            row.setClickable(true);

            TextView sample = new TextView(mActivity);
            sample.setText(i == 0 ? AppStrings.s(R.string.i18n_0176) : AppStrings.s(R.string.i18n_0177));
            sample.setTextSize(15);
            sample.setTextColor(mUi.TEXT);
            sample.setTypeface(faceForHangul(f));
            sample.setSingleLine(true);
            sample.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(sample);

            TextView name = new TextView(mActivity);
            // AppStrings.s(R.string.i18n_0178) marks a face whose own cmap has conjoining jamo. The bundled Pretendard
            // does not - syllables only - and the platform fills the gap with a fallback face,
            // so the mark is about which font draws the text, not about whether it appears.
            name.setText(Fonts.faceHasJamo(mActivity, f) ? f.label + AppStrings.s(R.string.i18n_0179) : f.label);
            name.setTextSize(11);
            name.setTextColor(f.id.equals(current) ? mUi.ACCENT : mUi.HINT);
            name.setPadding(0, 0, mUi.dp(10), 0);
            row.addView(name);

            if (f.id.equals(current)) row.addView(Chrome.icon(mActivity, "check", 16, false, null));

            row.setOnClickListener(v -> {
                mHost.onHangulFamily(f.id);
                if (holder[0] != null) holder[0].dismiss();
                mSheet.render();
            });
            box.addView(row);
            if (i < faces.size() - 1) box.addView(mUi.hairline(mUi.dp(12), mUi.dp(0)));
        }

        ScrollView scroll = new ScrollView(mActivity);
        scroll.setFillViewport(true);
        scroll.addView(box);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, mUi.dp(340)));

        holder[0] = new android.app.AlertDialog.Builder(mActivity)
                .setTitle(AppStrings.s(R.string.i18n_0126))
                .setView(scroll)
                .setPositiveButton(AppStrings.s(R.string.i18n_0175), (d, w) -> {
                    Fonts.rescan();
                    mSheet.render();
                    chooseHangulFont();
                })
                .setNeutralButton(AppStrings.s(R.string.i18n_0180), (d, w) -> installFont())
                .setNegativeButton(AppStrings.s(R.string.i18n_0029), null)
                .create();
        holder[0].show();
    }

    /**
     * Every way a font gets in, in one dialog.
     *
     * <p>There are two real answers and they are not equally easy. The device picker works
     * but requires the file to already be on the phone, which means the user has to have
     * downloaded it in a browser first. Termux is the easier one: the daemon already runs
     * there, it has `curl`, and it can see {@code ~/.fonts} - so a URL typed here is fetched
     * on that side and handed over, which is one step instead of three apps.
     *
     * <p>The list is asked for on a background thread because it is a socket round trip and
     * a directory scan on the far end; doing it on the UI thread is the freeze that makes a
     * settings screen feel broken.
     */
    private void installFont() {
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(mUi.dp(8), mUi.dp(6), mUi.dp(8), 0);

        LinearLayout status = new LinearLayout(mActivity);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(mUi.dp(12), mUi.dp(4), mUi.dp(12), mUi.dp(8));
        box.addView(status);

        TextView loading = new TextView(mActivity);
        loading.setText(AppStrings.s(R.string.i18n_0181));
        loading.setTextColor(mUi.HINT);
        loading.setTextSize(12);
        status.addView(loading);

        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];

        LinearLayout device = new LinearLayout(mActivity);
        device.setOrientation(LinearLayout.HORIZONTAL);
        device.setGravity(Gravity.CENTER_VERTICAL);
        device.setPadding(mUi.dp(12), mUi.dp(12), mUi.dp(12), mUi.dp(12));
        device.setBackground(mUi.touchable());
        device.setClickable(true);
        device.addView(Chrome.icon(mActivity, "folder", 16, false, null));
        TextView deviceLabel = new TextView(mActivity);
        deviceLabel.setText(AppStrings.s(R.string.i18n_0182));
        deviceLabel.setTextColor(mUi.TEXT);
        deviceLabel.setTextSize(13);
        deviceLabel.setPadding(mUi.dp(10), 0, 0, 0);
        device.addView(deviceLabel);
        device.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            fontImport();
        });
        box.addView(device);
        box.addView(mUi.hairline(mUi.dp(12), mUi.dp(0)));

        LinearLayout url = new LinearLayout(mActivity);
        url.setOrientation(LinearLayout.HORIZONTAL);
        url.setGravity(Gravity.CENTER_VERTICAL);
        url.setPadding(mUi.dp(12), mUi.dp(12), mUi.dp(12), mUi.dp(12));
        url.setBackground(mUi.touchable());
        url.setClickable(true);
        url.addView(Chrome.icon(mActivity, "cloud", 16, false, null));
        TextView urlLabel = new TextView(mActivity);
        urlLabel.setText(AppStrings.s(R.string.i18n_0183));
        urlLabel.setTextColor(mUi.TEXT);
        urlLabel.setTextSize(13);
        urlLabel.setPadding(mUi.dp(10), 0, 0, 0);
        url.addView(urlLabel);
        url.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            fontFromUrl();
        });
        box.addView(url);
        box.addView(mUi.hairline(mUi.dp(12), mUi.dp(0)));

        ScrollView scroll = new ScrollView(mActivity);
        scroll.addView(box);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, mUi.dp(360)));

        holder[0] = new android.app.AlertDialog.Builder(mActivity)
                .setTitle(AppStrings.s(R.string.i18n_0184))
                .setView(scroll)
                .setNegativeButton(AppStrings.s(R.string.i18n_0029), null)
                .create();
        holder[0].show();

        new Thread(() -> {
            final java.util.List<com.termux.terminal.DaemonClient.RemoteFont> remote;
            final String error;
            try {
                remote = com.termux.terminal.DaemonClient.fonts(
                        mHost.daemonHost(), mHost.daemonPort(), mHost.daemonToken(), 4000);
                error = null;
            } catch (com.termux.terminal.DaemonClient.Failure f) {
                mActivity.runOnUiThread(() -> reportFonts(status, loading, null, f.getMessage()));
                return;
            }
            mActivity.runOnUiThread(() -> reportFonts(status, loading, remote, error));
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
            TextView t = new TextView(mActivity);
            t.setText(AppStrings.s(R.string.i18n_0185) + error);
            t.setTextColor(mUi.HINT);
            t.setTextSize(11);
            status.addView(t);
            return;
        }
        if (fonts == null || fonts.isEmpty()) {
            TextView t = new TextView(mActivity);
            t.setText(AppStrings.s(R.string.i18n_0186)
                    + AppStrings.s(R.string.i18n_0187));
            t.setTextColor(mUi.HINT);
            t.setTextSize(11);
            status.addView(t);
            return;
        }
        TextView head = new TextView(mActivity);
        head.setText(AppStrings.s(R.string.i18n_0188) + fonts.size() + AppStrings.s(R.string.i18n_0189));
        head.setTextColor(mUi.HINT);
        head.setTextSize(11);
        status.addView(head);
        for (com.termux.terminal.DaemonClient.RemoteFont f : fonts) {
            LinearLayout row = new LinearLayout(mActivity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(mUi.dp(12), mUi.dp(10), mUi.dp(12), mUi.dp(10));
            row.setBackground(mUi.touchable());
            row.setClickable(true);

            TextView name = new TextView(mActivity);
            name.setText(f.name());
            name.setTextColor(mUi.TEXT);
            name.setTextSize(12.5f);
            name.setSingleLine(true);
            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(name);

            TextView size = new TextView(mActivity);
            size.setText((f.size / 1024) + " KB");
            size.setTextColor(mUi.HINT);
            size.setTextSize(10.5f);
            row.addView(size);

            row.setOnClickListener(v -> pullFromTermux(f));
            status.addView(row);
        }
    }

    /** Fetch one Termux-side font and install it as if it had been picked locally. */
    private void pullFromTermux(com.termux.terminal.DaemonClient.RemoteFont f) {
        android.widget.Toast.makeText(mActivity, f.name() + AppStrings.s(R.string.i18n_0190),
                android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final Fonts.Import result;
            try {
                byte[] data = com.termux.terminal.DaemonClient.font(
                        mHost.daemonHost(), mHost.daemonPort(), mHost.daemonToken(),
                        f.path, 8000);
                result = Fonts.install(mActivity, f.name(), data);
            } catch (com.termux.terminal.DaemonClient.Failure e) {
                mActivity.runOnUiThread(() -> showFontError(e.getMessage()));
                return;
            }
            mActivity.runOnUiThread(() -> applyImported(result));
        }, "fterm-font-get").start();
    }

    /** A URL for the daemon to download, because this app has no way to fetch one itself. */
    private void fontFromUrl() {
        mUi.prompt(AppStrings.s(R.string.i18n_0191), "", "https://example.com/MyFont.ttf",
                AppStrings.s(R.string.i18n_0192)
                        + AppStrings.s(R.string.i18n_0193),
                v -> {
                    if (v.isEmpty()) return;
                    if (!v.startsWith("http://") && !v.startsWith("https://")) {
                        showFontError(AppStrings.s(R.string.i18n_0194));
                        return;
                    }
                    android.widget.Toast.makeText(mActivity, AppStrings.s(R.string.i18n_0195),
                            android.widget.Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        final Fonts.Import result;
                        try {
                            String path = com.termux.terminal.DaemonClient.fetchFont(
                                    mHost.daemonHost(), mHost.daemonPort(), mHost.daemonToken(),
                                    v, 12000);
                            byte[] data = com.termux.terminal.DaemonClient.font(
                                    mHost.daemonHost(), mHost.daemonPort(), mHost.daemonToken(),
                                    path, 8000);
                            String name = path.substring(path.lastIndexOf('/') + 1);
                            result = Fonts.install(mActivity, name, data);
                        } catch (com.termux.terminal.DaemonClient.Failure e) {
                            mActivity.runOnUiThread(() -> showFontError(e.getMessage()));
                            return;
                        }
                        mActivity.runOnUiThread(() -> applyImported(result));
                    }, "fterm-font-url").start();
                });
    }

    private void showFontError(String message) {
        new android.app.AlertDialog.Builder(mActivity)
                .setTitle(AppStrings.s(R.string.i18n_0124))
                .setMessage(message)
                .setPositiveButton(AppStrings.s(R.string.i18n_0172), null)
                .show();
    }

    /**
     * Install a font, select it, and reopen the picker it belongs in.
     *
     * <p>A Korean font is proportional, so the Latin list can never contain it - reopening
     * that list after installing one would show a list the new row is not in and read as a
     * failed install. The rule is the same one {@link Fonts#adopt} uses to accept the file.
     */
    private void applyImported(Fonts.Import imported) {
        if (imported.id == null) {
            showFontError(imported.reason);
            return;
        }
        boolean latin = Fonts.isLatinFace(imported.id);
        if (latin) mHost.onFontFamily(imported.id);
        else mHost.onHangulFamily(imported.id);
        mSheet.render();
        if (latin) chooseFont();
        else chooseHangulFont();
    }

    /**
     * Import a font file the user picked, by copying it into app storage.
     *
     * <p>A system file picker, not a text field. The previous version asked for an absolute
     * path, which reads as the more powerful option and was the less capable one: since
     * Android 11 this app cannot open an arbitrary {@code /sdcard} path, so the field accepted
     * paths it could never load. The picker hands back a {@code content://} document that the
     * platform has already granted, and {@link Fonts#importFont} copies it somewhere the
     * terminal can read forever.
     *
     * <p>No separate result plumbing: {@link Activity#startActivityForResult} is deprecated but
     * the sheet is a Dialog, not a fragment host, and a contract launcher would need the same
     * hop back through the activity. The activity forwards the result here.
     */
    private static final int REQ_IMPORT_FONT = 0x4f4e;

    private void fontImport() {
        android.content.Intent i = new android.content.Intent(
                android.content.Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        // Any file, not font/*. The system picker resolves a filter against the MIME type
        // MediaStore recorded for each file, and a .ttf that arrived by adb or a file manager
        // often has none - filtered to font/*, the folder the user just filled shows as empty.
        // So the picker shows everything and the font check happens after the copy, where a
        // wrong pick is refused with a reason instead of never being offered.
        i.setType("*/*");
        try {
            mActivity.startActivityForResult(i, REQ_IMPORT_FONT);
        } catch (Throwable t) {
            new android.app.AlertDialog.Builder(mActivity)
                    .setTitle(AppStrings.s(R.string.i18n_0124))
                    .setMessage(AppStrings.s(R.string.i18n_0196) + t)
                    .setPositiveButton(AppStrings.s(R.string.i18n_0172), null)
                    .show();
        }
    }

    /**
     * The result of {@link #fontImport()}: copy the pick, then apply it.
     *
     * <p>Called by the activity. A refused file says why - "nothing happened" after picking a
     * file is the response that makes a user think the feature is broken.
     */
    void onFontImportResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode != REQ_IMPORT_FONT) return;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        // The reason, not a shrug: a wrong file and a broken picker need different responses
        // from the user, and "could not read that" reads as the latter.
        applyImported(Fonts.importFont(mActivity, data.getData()));
    }

    /** The actual face for one catalog entry, so the sample is the font and not a lookalike. */
    private Typeface faceFor(Fonts.Face f) {
        if (f.path == null) {
            try {
                return Typeface.createFromAsset(mActivity.getAssets(), Fonts.ASSET_PATH);
            } catch (Throwable ignored) {
            }
        } else {
            try {
                return Typeface.createFromFile(f.path);
            } catch (Throwable ignored) {
            }
        }
        return Typeface.MONOSPACE;
    }

    /** The actual Korean face for a catalog entry; the first row is the bundled one. */
    private Typeface faceForHangul(Fonts.Face f) {
        if (f.path == null) {
            try {
                return Typeface.createFromAsset(mActivity.getAssets(), Fonts.HANGUL_PATH);
            } catch (Throwable ignored) {
            }
        } else {
            try {
                return Typeface.createFromFile(f.path);
            } catch (Throwable ignored) {
            }
        }
        return Typeface.DEFAULT;
    }
}
