package io.github.crashedsystem.terminal;

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

    final SettingsSheet mSheet;
    final SettingsSheet.Host mHost;
    final Activity mActivity;
    final SettingsUi mUi;

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
        installLabel.setText(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0173));
        installLabel.setTextColor(mUi.ACCENT);
        installLabel.setTextSize(12.5f);
        installLabel.setPadding(mUi.dp(10), 0, 0, 0);
        install.addView(installLabel);
        install.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            new SettingsFontInstall(this).show();
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
            sample.setText(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0174));
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
                ViewGroup.LayoutParams.MATCH_PARENT, mUi.listHeight(340, 0.6f)));

        holder[0] = new android.app.AlertDialog.Builder(mActivity)
                .setTitle(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0124))
                .setView(scroll)
                .setPositiveButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0175), (d, w) -> {
                    Fonts.rescan();
                    mSheet.render();
                    chooseFont();
                })
                .setNegativeButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0029), null)
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
    /** Korean syllables: jamo coverage is marked, because the bundled face lacks it. */
    void chooseHangulFont() {
        chooseScript(new Script(
                R.string.i18n_0126,
                () -> Fonts.hangulCatalog(mActivity),
                mHost::hangulFamily,
                mHost::onHangulFamily,
                bundled -> AppStrings.s(bundled ? R.string.i18n_0176 : R.string.i18n_0177),
                f -> Fonts.faceHasJamo(mActivity, f)
                        ? f.label + AppStrings.s(R.string.i18n_0179) : f.label,
                (v, f) -> v.setTypeface(faceForHangul(f)),
                Fonts::rescan));
    }

    /** Japanese and Chinese: kana and Han together, so a face answering one is not offered. */
    void chooseCjkFont() {
        chooseScript(new Script(
                R.string.i18n_0397,
                () -> Fonts.cjkCatalog(mActivity),
                mHost::cjkFamily,
                mHost::onCjkFamily,
                bundled -> "\u3042\u30a2\u4e00\u6f22",
                f -> f.label,
                (v, f) -> {
                    if (f.path == null) return;
                    try {
                        v.setTypeface(android.graphics.Typeface.createFromFile(f.path));
                    } catch (Throwable ignored) {
                    }
                },
                Fonts::rescanCjk));
    }

    /**
     * Pick the face for one script.
     *
     * <p>Korean and CJK are the same dialog three times over - a row per face, a sample in
     * that face, the current one marked - so they share this. Only the sample text, the label
     * and the setter differ, and those are the descriptor.
     *
     * <p>The filter for both lists is glyph coverage rather than a fixed advance: they are
     * drawn per code point at a scale derived from the cell, so a proportional face is correct
     * here and would be wrong as the Latin face.
     */
    private void chooseScript(Script script) {
        final java.util.List<Fonts.Face> faces = script.faces.get();
        final String current = script.current.get();

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
            // The first row is the bundled face, which answers syllables but not jamo; the
            // sample spells that difference out instead of leaving it to the label.
            sample.setText(script.sample.apply(i == 0));
            sample.setTextSize(15);
            sample.setTextColor(mUi.TEXT);
            script.face.accept(sample, f);
            sample.setSingleLine(true);
            sample.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(sample);

            TextView name = new TextView(mActivity);
            name.setText(script.label.apply(f));
            name.setTextSize(11);
            name.setTextColor(f.id.equals(current) ? mUi.ACCENT : mUi.HINT);
            name.setPadding(0, 0, mUi.dp(10), 0);
            row.addView(name);

            if (f.id.equals(current)) row.addView(Chrome.icon(mActivity, "check", 16, false, null));

            row.setOnClickListener(v -> {
                script.set.accept(f.id);
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
                ViewGroup.LayoutParams.MATCH_PARENT, mUi.listHeight(340, 0.6f)));

        holder[0] = new android.app.AlertDialog.Builder(mActivity)
                .setTitle(AppStrings.s(script.title))
                .setView(scroll)
                .setPositiveButton(AppStrings.s(R.string.i18n_0175), (d, w) -> {
                    script.rescan.run();
                    mSheet.render();
                    chooseScript(script);
                })
                .setNeutralButton(AppStrings.s(R.string.i18n_0180),
                        (d, w) -> new SettingsFontInstall(this).show())
                .setNegativeButton(AppStrings.s(R.string.i18n_0029), null)
                .create();
        holder[0].show();
    }

    /** What differs between the Korean and CJK pickers: the list, the sample, the setter. */
    private static final class Script {
        final int title;
        final java.util.function.Supplier<java.util.List<Fonts.Face>> faces;
        final java.util.function.Supplier<String> current;
        final java.util.function.Consumer<String> set;
        final java.util.function.Function<Boolean, String> sample;
        final java.util.function.Function<Fonts.Face, String> label;
        final java.util.function.BiConsumer<TextView, Fonts.Face> face;
        final Runnable rescan;

        Script(int title, java.util.function.Supplier<java.util.List<Fonts.Face>> faces,
               java.util.function.Supplier<String> current,
               java.util.function.Consumer<String> set,
               java.util.function.Function<Boolean, String> sample,
               java.util.function.Function<Fonts.Face, String> label,
               java.util.function.BiConsumer<TextView, Fonts.Face> face,
               Runnable rescan) {
            this.title = title;
            this.faces = faces;
            this.current = current;
            this.set = set;
            this.sample = sample;
            this.label = label;
            this.face = face;
            this.rescan = rescan;
        }
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

    void fontImport() {
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
                    .setTitle(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0124))
                    .setMessage(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0196) + t)
                    .setPositiveButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0172), null)
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
        new SettingsFontInstall(this).onImported(Fonts.importFont(mActivity, data.getData()));
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
