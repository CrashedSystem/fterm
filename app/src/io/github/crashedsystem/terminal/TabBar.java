package io.github.crashedsystem.terminal;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/**
 * The bar above the terminal: the tab strip, then the split button.
 *
 * <p>Windows Terminal's tab row is a ContentPresenter for the tabs and the NewTabButton, which
 * is a SplitButton - a plus that opens a tab and a chevron that opens the dropdown. There is
 * no hamburger and no gear anywhere in that row; settings is a row inside the dropdown. This
 * app had invented both, which cost the strip two buttons' worth of width.
 *
 * <p>Split out of {@link MainActivity#buildUi()} because the row is a self-contained layout
 * and the activity was over its line budget. The activity still owns what the buttons do.
 */
final class TabBar {

    /** The pieces the activity needs to keep, and the two gestures the buttons carry. */
    static final class Parts {
        LinearLayout top;
        HorizontalScrollView scroll;
        LinearLayout strip;
    }

    private TabBar() {
    }

    /**
     * Build the row.
     *
     * @param onNewTab  the plus; the primary half of the split button
     * @param onMenu    the chevron, and the plus's long press
     */
    static Parts build(Activity activity, Runnable onNewTab, View.OnClickListener onMenu) {
        Parts parts = new Parts();

        parts.strip = new LinearLayout(activity);
        parts.strip.setOrientation(LinearLayout.HORIZONTAL);
        // Centre, not bottom: the plates are 34dp in a 38dp bar, and the buttons beside them
        // are shorter still - bottom-gravity left the two split-button halves 3dp and 6dp
        // below the tab's centre line, which read as a misaligned pair.
        parts.strip.setGravity(Gravity.CENTER_VERTICAL);
        parts.strip.setBackgroundColor(0xff2b2b2b);
        parts.strip.setPadding(dp(activity, 4), dp(activity, 2), dp(activity, 4), 0);

        parts.scroll = new HorizontalScrollView(activity);
        parts.scroll.setHorizontalScrollBarEnabled(false);
        parts.scroll.setFillViewport(false);
        // Weight 1, not wrap_content: the strip must be the thing that gives way when the tabs
        // do not fit. With wrap_content the plates and the two buttons together were wider than
        // the bar at three tabs, and the chevron - the last child - was what got cut off.
        // Weighted, the tabs scroll inside and the buttons always stay on screen.
        parts.scroll.setLayoutParams(new LinearLayout.LayoutParams(0, dp(activity, 38), 1f));
        parts.scroll.addView(parts.strip);

        View plus = Chrome.icon(activity, "plus", 28, false, v -> onNewTab.run());
        View drop = Chrome.icon(activity, "chevron", 22, true, onMenu);
        // Long-press "+" opens the dropdown too: it is the gesture that was here before, and
        // it costs nothing to keep for a hand that has already learned it.
        plus.setOnLongClickListener(v -> {
            onMenu.onClick(v);
            return true;
        });

        parts.top = new LinearLayout(activity);
        parts.top.setOrientation(LinearLayout.HORIZONTAL);
        parts.top.setGravity(Gravity.CENTER_VERTICAL);
        parts.top.setBackgroundColor(0xff2b2b2b);
        parts.top.setPadding(dp(activity, 2), 0, dp(activity, 2), 0);
        parts.top.addView(parts.scroll);
        parts.top.addView(plus);
        // No gap between the two: they read as the one split button they are.
        parts.top.addView(drop);
        parts.top.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 38)));
        return parts;
    }

    private static int dp(Activity activity, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                activity.getResources().getDisplayMetrics()));
    }
}
