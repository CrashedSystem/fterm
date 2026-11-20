package io.github.crashedsystem.fterm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The 모양 page's picture rows: choose, remove, and how it is drawn.
 *
 * <p>Four rows rather than a page of their own. A background is one picture and three
 * numbers; giving it a breadcrumb would put the setting two taps further away than the
 * scheme it sits under.
 *
 * <p>The file picker result comes back through the activity, so one instance is kept for the
 * sheet's life - the same reason {@link SettingsFontPicker} is.
 */
final class SettingsBackgroundPicker {

    private static final int REQ_PICK = 0x4247;

    private interface Pct {
        void on(int percent);
    }

    private final SettingsSheet mSheet;
    private final SettingsSheet.Host mHost;
    private final Activity mActivity;
    private final SettingsUi mUi;

    SettingsBackgroundPicker(SettingsSheet sheet) {
        mSheet = sheet;
        mHost = sheet.host();
        mActivity = sheet.activity();
        mUi = sheet.ui();
    }

    /** Appends the four rows to the appearance card. */
    void rows(LinearLayout card) {
        String name = mHost.backgroundName();
        card.addView(mUi.valueRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0051), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0149),
                name == null ? io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0094) : name, this::choose, false));

        card.addView(sliderRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0150), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0151),
                mHost.backgroundOpacity(), 100, false, mHost::onBackgroundOpacity));

        card.addView(mUi.valueRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0152), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0153),
                BackgroundSpec.fitLabel(mHost.backgroundFit()), this::chooseFit, false));

        card.addView(mUi.valueRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0154), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0155),
                BackgroundSpec.alignLabel(mHost.backgroundAlign()), this::chooseAlign, false));

        card.addView(sliderRow(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0156), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0157),
                mHost.backgroundDim(), BackgroundSpec.MAX_DIM, true, mHost::onBackgroundDim));
    }

    // ------------------------------------------------------------------ rows

    /**
     * A labelled slider row.
     *
     * <p>The percentage is written next to the name and follows the thumb, because a slider
     * without a number cannot be put back where it was.
     */
    private View sliderRow(String title, String sub, int value, int max,
                           boolean last, final Pct onChange) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, mUi.dp(8), 0, mUi.dp(8));

        LinearLayout text = new LinearLayout(mActivity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(mActivity);
        t.setText(title);
        t.setTextColor(mUi.TEXT);
        t.setTextSize(14);
        UiFont.apply(t, false);
        text.addView(t);
        final TextView hint = new TextView(mActivity);
        hint.setText(sub + " · " + value + "%");
        hint.setTextColor(mUi.HINT);
        hint.setTextSize(11);
        text.addView(hint);
        row.addView(text);

        SeekBar bar = new SeekBar(mActivity);
        bar.setMax(max);
        bar.setProgress(Math.max(0, Math.min(max, value)));
        bar.setLayoutParams(new LinearLayout.LayoutParams(mUi.dp(140),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (!fromUser) return;
                hint.setText(sub + " · " + p + "%");
                onChange.on(p);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        row.addView(bar);
        return mUi.cardRow(row, last);
    }

    // ------------------------------------------------------------------ choose

    private void choose() {
        final boolean has = mHost.backgroundSet();
        String[] items = has
                ? new String[]{io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0158), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0159), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0160)}
                : new String[]{io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0158), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0159)};
        new AlertDialog.Builder(mActivity)
                .setTitle(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0051))
                .setItems(items, (d, which) -> {
                    if (which == 0) pick();
                    else if (which == 1) showScriptHelp();
                    else clear();
                })
                .setNegativeButton(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0029), null)
                .show();
    }

    /**
     * {@code ACTION_OPEN_DOCUMENT}, not {@code GET_CONTENT}: the document grant is what lets the
     * app read the file, and the copy happens immediately, so a revoked grant later cannot
     * leave the terminal pointing at a picture it can no longer open.
     */
    private void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            mActivity.startActivityForResult(i, REQ_PICK);
        } catch (Throwable t) {
            Toast.makeText(mActivity, io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0161) + t.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Called by {@link SettingsSheet} from the activity's result. */
    void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_PICK) return;
        if (resultCode != Activity.RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        boolean ok = BackgroundStore.install(mActivity, uri);
        Toast.makeText(mActivity, ok ? io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0162)
                : io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0163),
                Toast.LENGTH_SHORT).show();
        if (ok) {
            mHost.onBackgroundChanged();
            mSheet.render();
        }
    }

    private void clear() {
        BackgroundStore.clear(mActivity);
        mHost.onBackgroundChanged();
        Toast.makeText(mActivity, io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0164), Toast.LENGTH_SHORT).show();
        mSheet.render();
    }

    private void chooseFit() {
        final String[] values = BackgroundSpec.FIT_NAMES;
        String[] labels = {io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0165), io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0166)};
        int current = BackgroundSpec.FIT_COVER.equals(BackgroundSpec.fit(mHost.backgroundFit()))
                ? 1 : 0;
        new AlertDialog.Builder(mActivity)
                .setTitle(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0152))
                .setSingleChoiceItems(labels, current, (d, which) -> {
                    mHost.onBackgroundFit(values[which]);
                    d.dismiss();
                    mSheet.render();
                })
                .setNegativeButton(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0058), null)
                .show();
    }

    /**
     * The nine placements, as Windows Terminal names them ({@code topLeft} … {@code bottomRight}).
     *
     * <p>A theme that ships {@code "backgroundImageAlignment": "bottomRight"} should arrive
     * looking like the theme, which is why these are the same nine words and not a pair of
     * sliders: "move the picture" has a small number of answers people actually want.
     */
    private void chooseAlign() {
        final String[] values = BackgroundSpec.ALIGN_NAMES;
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = BackgroundSpec.alignLabel(values[i]) + " (" + values[i] + ")";
        }
        String current = BackgroundSpec.align(mHost.backgroundAlign());
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) selected = i;
        }
        new AlertDialog.Builder(mActivity)
                .setTitle(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0167))
                .setSingleChoiceItems(labels, selected, (d, which) -> {
                    mHost.onBackgroundAlign(values[which]);
                    d.dismiss();
                    mSheet.render();
                })
                .setNegativeButton(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0058), null)
                .show();
    }

    /**
     * How a script installs a picture.
     *
     * <p>Shown in the app because it is the one path that cannot be discovered from the UI:
     * the folder is under {@code Android/data}, which some file managers hide, and the
     * broadcast action is only documented in the repository.
     */
    private void showScriptHelp() {
        String dir = BackgroundStore.dir(mActivity).getAbsolutePath();
        String text = io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0168) + dir
                + io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0169)
                + "adb push miku-dusk.png " + dir + "/\n"
                + "adb shell am broadcast -a io.github.crashedsystem.fterm.action.APPLY_BACKGROUND"
                + " -p io.github.crashedsystem.fterm --es path " + dir + "/miku-dusk.png"
                + " --ei opacity 100 --es fit contain --ei dim 25"
                + io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0170)
                + "adb shell am broadcast -a io.github.crashedsystem.fterm.action.CLEAR_BACKGROUND"
                + " -p io.github.crashedsystem.fterm";
        TextView body = new TextView(mActivity);
        body.setText(text);
        body.setTextColor(mUi.TEXT);
        body.setTextSize(11.5f);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setPadding(mUi.dp(20), mUi.dp(12), mUi.dp(20), mUi.dp(4));
        new AlertDialog.Builder(mActivity)
                .setTitle(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0171))
                .setView(body)
                .setPositiveButton(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0172), null)
                .show();
    }
}
