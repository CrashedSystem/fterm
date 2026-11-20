package io.github.crashedsystem.terminal;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * WT's profiles page: a list of them, and an editor per profile.
 *
 * <p>A profile here is the four things a tab can differ in. The command is the field that
 * makes an "ssh profile" possible, and it is typed into the shell rather than replacing
 * it - see {@link Profiles}.
 */
final class SettingsProfilesPage extends SettingsPage {

    SettingsProfilesPage(SettingsSheet sheet) {
        super(sheet);
    }

    @Override
    void build(LinearLayout col) {
        LinearLayout card = ui.card(col);
        java.util.List<Profiles.Profile> list = host.profiles();
        if (list.isEmpty()) {
            LinearLayout empty = new LinearLayout(activity);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
            TextView t = new TextView(activity);
            t.setText(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0210));
            t.setTextColor(ui.TEXT);
            t.setTextSize(13);
            empty.addView(t);
            TextView sub = new TextView(activity);
            sub.setText(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0211));
            sub.setTextColor(ui.HINT);
            sub.setTextSize(10.5f);
            sub.setPadding(0, ui.dp(3), 0, 0);
            empty.addView(sub);
            card.addView(empty);
        } else {
            for (int i = 0; i < list.size(); i++) {
                final int index = i;
                final Profiles.Profile p = list.get(i);
                card.addView(profileRow(p, () -> editProfile(index, false),
                        i == list.size() - 1));
            }
        }

        card.addView(ui.valueRow(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0212), io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0213), "+",
                () -> editProfile(-1, true), false));

        // An example, not documentation. io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0214) is a sentence the user
        // has to translate into four fields and an icon; a row that installs the finished
        // profile is the same information in one tap, and it doubles as the only place the
        // OpenCode mark is discoverable without opening the icon grid.
        card.addView(ui.valueRow(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0215), io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0216), "",
                this::addOpencodeExample, true));

        ui.hint(col, io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0217)
                + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0218)
                + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0219));
    }

    /**
     * The example profile: opencode, with its own mark.
     *
     * <p>Four fields are already filled in by {@link Profiles#opencodeExample()}, so this is
     * only the confirmation step - the row says what it will do, the dialog names the command,
     * and a second opencode profile is refused rather than piled up.
     */
    private void addOpencodeExample() {
        Profiles.Profile example = Profiles.opencodeExample();
        boolean present = false;
        for (Profiles.Profile p : host.profiles()) {
            if (example.name.equals(p.name) && example.command.equals(p.command)) present = true;
        }
        if (present) {
            new android.app.AlertDialog.Builder(activity)
                    .setTitle(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0215))
                    .setMessage(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0220))
                    .setPositiveButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0172), null)
                    .show();
            return;
        }
        new android.app.AlertDialog.Builder(activity)
                .setTitle(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0215))
                .setMessage(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0221) + example.name + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0222) + example.command
                        + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0223)
                        + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0224)
                        + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0225))
                .setPositiveButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0226), (d, w) -> {
                    java.util.List<Profiles.Profile> list = host.profiles();
                    list.add(example);
                    host.onProfiles(list);
                    render();
                })
                .setNegativeButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0058), null)
                .show();
    }

    /**
     * One row of the profile list: the mark the tab will carry, the name, the subtitle.
     *
     * <p>The mark is shown here rather than only in the editor because the list is where the
     * user decides which profile is which. A name alone makes two profiles called io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0227)
     * indistinguishable; the icon is the thing that would have told them apart in the tab
     * strip, so it belongs in the list that configures the strip.
     */
    private View profileRow(Profiles.Profile p, Runnable onClick, boolean last) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(9), 0, ui.dp(9));
        row.setClickable(true);
        row.setBackground(ui.touchable());
        row.setOnClickListener(v -> onClick.run());

        LinearLayout slot = new LinearLayout(activity);
        slot.setGravity(Gravity.CENTER);
        slot.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)));
        String icon = p.icon == null || p.icon.isEmpty() ? "" : p.icon;
        slot.addView(icon.isEmpty()
                ? Chrome.icon(activity, "terminal", 16, true, null)
                : TabIcons.build(activity, icon, ui.HINT));
        LinearLayout.LayoutParams slp = (LinearLayout.LayoutParams) slot.getLayoutParams();
        slp.rightMargin = ui.dp(10);
        slot.setLayoutParams(slp);
        row.addView(slot);

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(activity);
        name.setText(p.name.isEmpty() ? io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0228) : p.name);
        name.setTextColor(ui.TEXT);
        name.setTextSize(13.5f);
        UiFont.apply(name, false);
        text.addView(name);
        String sub = p.subtitle();
        if (!sub.isEmpty()) {
            TextView s = new TextView(activity);
            s.setText(sub);
            s.setTextColor(ui.HINT);
            s.setTextSize(10.5f);
            text.addView(s);
        }
        row.addView(text);
        row.addView(Chrome.icon(activity, "right", 16, true, null));
        return ui.cardRow(row, last);
    }

    /**
     * Edit or create the profile at {@code index}; {@code -1} means a new one.
     *
     * <p>The index, not the object, is the handle. {@link SettingsHost#profiles()} decodes the
     * stored string afresh on every call, so the instance this dialog was built from is never
     * the instance the save path sees - identity comparison there silently did nothing, and a
     * deleted profile reappeared on the next render.
     */
    private void editProfile(final int index, final boolean isNew) {
        java.util.List<Profiles.Profile> current = host.profiles();
        if (!isNew && (index < 0 || index >= current.size())) return;
        final Profiles.Profile existing = isNew ? new Profiles.Profile() : current.get(index);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), 0);

        final EditText name = ui.field(existing.name, io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0229));
        final EditText dir = ui.field(existing.dir, "/data/data/com.termux/files/home");
        final EditText shell = ui.field(existing.shell, "/data/data/com.termux/files/usr/bin/bash");
        final EditText command = ui.field(existing.command, "ssh myhost");

        // The icon is picked, not typed, so it lives in a mutable holder rather than in an
        // EditText: the dialog reads it when Save is pressed, exactly like the fields.
        final String[] icon = {existing.icon == null ? "" : existing.icon};

        box.addView(ui.label(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0230)));
        box.addView(name);

        LinearLayout iconRow = new LinearLayout(activity);
        iconRow.setOrientation(LinearLayout.HORIZONTAL);
        iconRow.setGravity(Gravity.CENTER_VERTICAL);
        iconRow.setPadding(0, ui.dp(8), 0, 0);
        iconRow.setClickable(true);
        iconRow.setBackground(ui.touchable());
        LinearLayout iconText = new LinearLayout(activity);
        iconText.setOrientation(LinearLayout.VERTICAL);
        iconText.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView iconTitle = new TextView(activity);
        iconTitle.setText(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0231));
        iconTitle.setTextColor(ui.TEXT);
        iconTitle.setTextSize(13.5f);
        UiFont.apply(iconTitle, false);
        iconText.addView(iconTitle);
        TextView iconValue = new TextView(activity);
        iconValue.setTextColor(ui.HINT);
        iconValue.setTextSize(11);
        iconText.addView(iconValue);
        iconRow.addView(iconText);
        // A live preview of the mark beside its name, drawn by the same builder the tab uses.
        LinearLayout iconPreview = new LinearLayout(activity);
        iconPreview.setGravity(Gravity.CENTER);
        iconPreview.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(30), ui.dp(30)));
        iconRow.addView(iconPreview);
        box.addView(iconRow);

        final Runnable repaintIcon = () -> {
            String id = icon[0];
            String shown = id.isEmpty() ? io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0232)
                    : TabIcons.isGlyph(id)
                            ? TabIcons.GLYPH_NAMES[SettingsIconPicker.glyphIndexOf(id)]
                            : id;
            iconValue.setText(shown);
            iconPreview.removeAllViews();
            iconPreview.addView(TabIcons.build(activity, id.isEmpty()
                    ? TabIcons.DEFAULT_ID : id, ui.TEXT));
        };
        repaintIcon.run();
        iconRow.setOnClickListener(v -> SettingsIconPicker.pick(activity, ui,
                io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0233), icon[0], io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0094), id -> {
                    icon[0] = id;
                    repaintIcon.run();
                }));

        box.addView(ui.label(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0234)));
        box.addView(dir);
        box.addView(ui.label(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0235)));
        box.addView(shell);
        box.addView(ui.label(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0236)));
        box.addView(command);
        TextView note = new TextView(activity);
        note.setText(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0237));
        note.setTextColor(ui.HINT);
        note.setTextSize(10.5f);
        note.setPadding(0, ui.dp(6), 0, 0);
        box.addView(note);

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(activity)
                .setTitle(isNew ? io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0212) : io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0238))
                .setView(box)
                .setPositiveButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0057), (d, w) -> {
                    Profiles.Profile p = new Profiles.Profile(
                            name.getText().toString().trim(),
                            dir.getText().toString().trim(),
                            shell.getText().toString().trim(),
                            command.getText().toString().trim(),
                            icon[0]);
                    java.util.List<Profiles.Profile> list = host.profiles();
                    if (isNew || index >= list.size()) {
                        list.add(p);
                    } else {
                        // Editing in place keeps the menu order the user built.
                        list.set(index, p);
                    }
                    host.onProfiles(list);
                    render();
                })
                .setNegativeButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0058), null);
        if (!isNew) {
            b.setNeutralButton(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0239), (d, w) -> {
                java.util.List<Profiles.Profile> list = host.profiles();
                if (index < list.size()) list.remove(index);
                host.onProfiles(list);
                render();
            });
        }
        b.show();
    }
}
