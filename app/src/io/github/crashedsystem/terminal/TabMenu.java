package io.github.crashedsystem.terminal;

import java.util.ArrayList;
import java.util.List;

/**
 * The menu a long-pressed tab opens.
 *
 * <p>This was a method in the activity, and it pushed {@link MainActivity} past the line
 * budget the structure test enforces. The split is not cosmetic: the menu is a list of things
 * a tab can do, and every entry is a call back into the activity - so the activity keeps the
 * behaviour and this class owns only which entries exist for a given tab.
 *
 * <p>Rows are {@link Chrome.Row}, the same shape the dropdown uses, so the two menus read as
 * one surface. That is also why this does not build its own dialog: a second visual language
 * for the same kind of list is the thing a menu must not have.
 */
final class TabMenu {

    private TabMenu() {
    }

    /**
     * Reorder a tab within the strip.
     *
     * <p>The strip is a LinearLayout, so the view has to move with the model: reordering only
     * the list would leave the screen disagreeing with it, and the next repaint would move the
     * tab back. Kept here rather than in the activity because it is tab-set bookkeeping, not
     * coordination - and the activity is over its line budget as it is.
     *
     * @return true when the tab actually moved
     */
    static boolean move(List<Tab> tabs, android.view.ViewGroup strip, Tab tab, int delta,
                        Runnable repaint) {
        int from = tabs.indexOf(tab);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= tabs.size()) return false;
        tabs.remove(from);
        tabs.add(to, tab);

        if (tab.plate != null && strip != null) {
            strip.removeView(tab.plate);
            strip.addView(tab.plate, to);
        }
        repaint.run();
        return true;
    }

    /** What a menu entry does; the activity maps these back to its own methods. */
    interface Actions {
        void rename();

        void chooseColor();

        void duplicate();

        /** @param delta -1 left, +1 right */
        void move(int delta);

        void close();
    }

    /**
     * The entries that apply to this tab, in order.
     *
     * <p>Move entries are only listed when there is somewhere to move to. A greyed-out row is
     * something the user has to read and reject; a list that offers only what is possible is
     * shorter and never lies.
     */
    /**
     * Pick a fill for one tab.
     *
     * <p>Presets rather than a colour wheel: a tab mark is a mnemonic - this one is "the
     * build", that one is "the server" - and eight named swatches pick a mnemonic faster than
     * a wheel does. Zero is offered first so the choice can be undone.
     *
     * @param apply called after the colour changes, to repaint that tab
     */
    static void chooseColor(android.app.Activity activity, Tab tab, Runnable apply) {
        final String[] names = {
                AppStrings.s(R.string.i18n_0387),
                "Blue", "Green", "Purple", "Orange", "Red", "Teal", "Gray",
        };
        final int[] values = {0, 0xff1f4e79, 0xff1f5c3a, 0xff4b3a7a, 0xff7a4a1f,
                0xff6e2424, 0xff1f5f5f, 0xff3a3f45};
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == tab.color) checked = i;
        new android.app.AlertDialog.Builder(activity)
                .setTitle(AppStrings.s(R.string.i18n_0381))
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    tab.color = values[which];
                    apply.run();
                    d.dismiss();
                })
                .setNegativeButton(AppStrings.s(R.string.i18n_0058), null)
                .show();
    }

    static Chrome.Row[] rowsFor(int index, int count, Actions actions) {
        List<Chrome.Row> rows = new ArrayList<>();
        boolean many = count > 1;

        rows.add(Chrome.Row.of("pencil", AppStrings.s(R.string.i18n_0380), null,
                actions::rename));
        rows.add(Chrome.Row.of("swatch", AppStrings.s(R.string.i18n_0381), null,
                actions::chooseColor));
        rows.add(Chrome.Row.of("terminal", AppStrings.s(R.string.i18n_0382), null,
                actions::duplicate));
        if (many && index > 0) {
            rows.add(Chrome.Row.of("left", AppStrings.s(R.string.i18n_0383), null,
                    () -> actions.move(-1)));
        }
        if (many && index >= 0 && index < count - 1) {
            rows.add(Chrome.Row.of("right", AppStrings.s(R.string.i18n_0384), null,
                    () -> actions.move(1)));
        }
        rows.add(Chrome.Row.of("close", AppStrings.s(R.string.i18n_0385), null,
                actions::close));

        return rows.toArray(new Chrome.Row[0]);
    }
}
