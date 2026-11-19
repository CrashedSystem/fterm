package ai.openclaw.fterm;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * An icon picker: a grid of every glyph, then an option to type one.
 *
 * <p>A grid, not a list. The set is thirty-six because a tab mark is how a glance tells an ssh
 * session from a database console, and a list that long is a scroll with no overview - the
 * whole point of choosing an icon is seeing the candidates at once. Each cell draws the real
 * mark with the same {@link TabIcons#build} the tab strip uses, so nothing here can promise a
 * size the tab will not deliver.
 *
 * <p>Shared by the global tab icon and the per-profile icon. The two differ only in what
 * "no icon" means, which is the {@code clearLabel} parameter: the global setting has a real
 * default to fall back to, a profile has "use the global one". A static utility because it
 * holds no state - the choice leaves through the callback before the dialog closes.
 */
final class SettingsIconPicker {

    private SettingsIconPicker() {
    }

    /**
     * Opens the grid.
     *
     * @param current    the value in force, for the tick
     * @param clearLabel the label for "no icon", or null when there is nothing to clear
     * @param onPick     called with a glyph id or literal text; the dialog has already closed
     */
    static void pick(Activity activity, SettingsUi ui, String title, String current,
                     String clearLabel, java.util.function.Consumer<String> onPick) {
        final int columns = 5;
        // 5 columns at 62dp: wide enough for "데이터베이스" on two lines, narrow enough that
        // the grid still fits a phone dialog without overflowing it.
        final int CELL_DP = 62;
        final java.util.List<String> ids = new java.util.ArrayList<>();
        final java.util.List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < TabIcons.GLYPHS.length; i++) {
            ids.add(TabIcons.GLYPHS[i]);
            names.add(TabIcons.GLYPH_NAMES[i]);
        }
        // The default is a cell like any other, at the end. Its label is not the mark itself -
        // the cell already draws ">_" above the label, and printing it twice reads as a
        // duplicated row rather than as a name.
        ids.add(TabIcons.DEFAULT_ID);
        names.add("기본");
        if (clearLabel != null) {
            // An empty id. TabIcons.build would draw the default for it, so the cell would
            // lie about what the choice means; the label carries the meaning instead.
            ids.add("");
            names.add(clearLabel);
        }

        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, ui.dp(6), 0, ui.dp(6));
        // MATCH_PARENT so each row is as wide as the dialog. The rows are centred, and a
        // wrap_content row has no width to centre inside: the final row holds one cell, so it
        // would sit against the left edge while every other row is centred.
        grid.setLayoutParams(new android.widget.ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];
        LinearLayout line = null;
        for (int i = 0; i < ids.size(); i++) {
            if (i % columns == 0) {
                line = new LinearLayout(activity);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER);
                line.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                grid.addView(line);
            }
            final String id = ids.get(i);
            // One comparison covers every case: a glyph id matches only its own cell, ">_"
            // matches the default cell, "" matches the "no icon" cell, and a user-typed mark
            // matches nothing - which is honest, since it is not one of the cells on screen.
            boolean chosen = id.equals(current);

            LinearLayout cell = new LinearLayout(activity);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(5));
            cell.setBackground(chosen ? ui.touchableSelected() : ui.touchable());
            // A fixed width, not weight. The dialog wraps this view in its own container, and
            // weight inside a container whose width is resolved later collapses: the last
            // row's lone cell came out half-size with its label hanging outside the fill.
            // A dp width is the same on every row and needs no filler views.
            cell.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(CELL_DP),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.setOnClickListener(v -> {
                holder[0].dismiss();
                onPick.accept(id);
            });

            // The mark sits in a tab-sized slot, so a cell is the tab at a glance.
            LinearLayout slot = new LinearLayout(activity);
            slot.setGravity(Gravity.CENTER);
            slot.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(34), ui.dp(34)));
            slot.addView(id.isEmpty()
                    ? absentMark(activity, ui, chosen)
                    : TabIcons.build(activity, id, chosen ? ui.TEXT : ui.HINT));
            cell.addView(slot);

            TextView label = new TextView(activity);
            label.setText(names.get(i));
            label.setTextColor(chosen ? ui.ACCENT : ui.HINT);
            label.setTextSize(9.5f);
            // Two lines: a name like "데이터베이스" is one word and cannot wrap, so a
            // single-line label truncates it to "데이터베…" and the name stops being a name.
            label.setSingleLine(false);
            label.setMaxLines(2);
            label.setGravity(Gravity.CENTER);
            label.setMaxWidth(ui.dp(CELL_DP - 4));
            cell.addView(label);

            line.addView(cell);
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(grid);

        holder[0] = new android.app.AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("직접 입력", (d, w) -> promptIconText(ui, title, current, onPick))
                .setNegativeButton("취소", null)
                .create();
        holder[0].show();
    }

    /**
     * The mark for "no icon": a dashed outline of the slot.
     *
     * <p>An empty cell would read as a rendering failure, and the default mark would read as
     * "this is the mark" - which is the opposite of what choosing it does.
     */
    private static View absentMark(Activity activity, SettingsUi ui, boolean bright) {
        View v = new View(activity);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x00000000);
        d.setStroke(Math.max(1, ui.dp(1)), bright ? ui.ACCENT : 0xff55595f);
        d.setCornerRadius(ui.dp(4));
        v.setBackground(d);
        v.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(16), ui.dp(20)));
        return v;
    }

    /** A one-line prompt for a custom mark; the slot clips long text, so the note says so. */
    private static void promptIconText(SettingsUi ui, String title, String current,
                                       java.util.function.Consumer<String> onPick) {
        ui.prompt(title, TabIcons.isGlyph(current) ? "" : current, ">_",
                "한두 글자가 가장 잘 맞습니다. 길면 작게 그려집니다.",
                v -> onPick.accept(v.trim()));
    }

    /** The index of a glyph id in {@link TabIcons#GLYPHS}; 0 when it is not one of them. */
    static int glyphIndexOf(String id) {
        for (int i = 0; i < TabIcons.GLYPHS.length; i++) {
            if (TabIcons.GLYPHS[i].equals(id)) return i;
        }
        return 0;
    }
}
