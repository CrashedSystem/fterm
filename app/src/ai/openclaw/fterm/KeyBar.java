package ai.openclaw.fterm;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The row of keys above the soft keyboard.
 *
 * <p>Two docked rows of six cells, the mterm shape: Esc Tab Home ↑ End PgUp over
 * Ctrl Alt ← ↓ → PgDn. Nothing scrolls and nothing hides - a phone cannot afford a gesture to
 * reach a key it uses ten times a minute.
 *
 * <p>The modifiers are sticky rather than held: a phone cannot press two keys at once, so CTRL
 * latches and the next keystroke releases it. That release lives in MainActivity's onCodePoint,
 * not here - this only renders the latched state.
 *
 * <p>Function keys live on a second layer. The edge tabs that switch to it are drawn at almost
 * no contrast, because they are a once-an-hour affordance sitting next to keys that are used
 * constantly; a permanently visible arrow there would compete with PgUp and PgDn for the two
 * corners a thumb reaches most naturally.
 */
final class KeyBar {

    /** What a key press means to the terminal. */
    interface Action {
        /** A named special key: tab, esc, left, right, up, down. */
        void special(String name);

        /** Raw bytes to write (e.g. ^C). */
        void raw(String bytes);

        void toggleKeyboard();

        /** The terminal's own paste (clipboard image detection included). */
        void paste();

        /** A function key: 1..12. */
        void function(int number);
    }

    // The bar sits directly on the keyboard, so it needs its own hairline or the two grays
    // merge. Keys get a lighter fill than the bar and a 1dp gap, which is what makes them
    // read as buttons rather than as a single strip.
    private static final int BG = 0xff1b1d21;       // bar: a shade above pure black
    private static final int KEY = 0xff2a2d33;      // key: one step up from the bar
    private static final int KEY_PRESSED = 0xff3a3f47;
    private static final int KEY_ON = 0xff2f5a9e;   // latched: muted accent, not neon
    private static final int KEY_TEXT = 0xffd7d9dd;
    /** The edge tabs: two shades from the bar, i.e. findable but never in the way. */
    private static final int EDGE_TAB = 0xff26292f;

    /**
     * Width reserved on each side for an edge tab.
     *
     * <p>The tabs were overlaying the bar, so the right one sat on top of PgUp and swallowed
     * taps meant for it. Reserving the space symmetrically (rather than only on the side that
     * currently shows a tab) keeps the two rows from jumping sideways every time the layer
     * changes, which would be worse than the few dp it costs.
     */
    private static final int EDGE_W_DP = 16;

    private final Context mContext;
    private final Action mAction;
    private final LinearLayout mRow;
    private final LinearLayout mUserRow;
    private final LinearLayout mRoot;
    private final FrameLayout mFrame;
    private final TextView mLeftTab;
    private final TextView mRightTab;

    /** True while the bar shows F1..F12 instead of the main layer. */
    private boolean mFnLayer;

    /** One repeat chain for the whole bar: two fingers cannot hold two keys here. */
    private final KeyRepeat mRepeat = new KeyRepeat();
    /** Set when the current touch turned into a repeat, so release does not add one more. */
    private boolean mRepeated;

    private boolean mCtrl, mAlt, mShift;

    KeyBar(Context context, Action action) {
        mContext = context;
        mAction = action;

        mRoot = new LinearLayout(context);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mRoot.setBackgroundColor(BG);
        // Air above and below the keys, the mterm proportion: the bar reads as a surface the
        // keys sit on, not as a frame they are squeezed into.
        // Air above and below the keys, the mterm proportion: the bar reads as a surface the
        // keys sit on, not as a frame they are squeezed into. The left and right insets are
        // wider because the edge tabs are overlaid in the same FrameLayout and would otherwise
        // sit on top of the first and last cells, eating their taps.
        mRoot.setPadding(dp(3) + dp(EDGE_W_DP), dp(6), dp(3) + dp(EDGE_W_DP), dp(6));

        mRow = new LinearLayout(context);
        mUserRow = new LinearLayout(context);
        for (LinearLayout row : new LinearLayout[]{mRow, mUserRow}) {
            row.setOrientation(LinearLayout.HORIZONTAL);
        }

        // "‹" goes back to the main layer, "›" goes forward to the function layer. The
        // actions were the other way round, which made both tabs dead: on the main layer the
        // only visible tab was the one that returned to the main layer, and the tab that
        // opened F1..F12 was hidden. The edge tabs are the only way to reach the F keys, so
        // this is the whole feature, not a detail of it.
        mLeftTab = edgeTab("\u2039", () -> setFnLayer(false));
        mRightTab = edgeTab("\u203a", () -> setFnLayer(true));

        mFrame = new FrameLayout(context);
        mFrame.setBackgroundColor(BG);
        mFrame.addView(mRoot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(dp(EDGE_W_DP),
                ViewGroup.LayoutParams.MATCH_PARENT);
        leftLp.gravity = Gravity.START;
        mFrame.addView(mLeftTab, leftLp);
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(dp(EDGE_W_DP),
                ViewGroup.LayoutParams.MATCH_PARENT);
        rightLp.gravity = Gravity.END;
        mFrame.addView(mRightTab, rightLp);

        buildLayer(false);
    }

    private void buildLayer(boolean fnLayer) {
        mFnLayer = fnLayer;
        mRow.removeAllViews();
        mUserRow.removeAllViews();
        mRoot.removeAllViews();

        if (fnLayer) {
            for (int i = 1; i <= 6; i++) {
                final int n = i;
                addGridCell(mRow, "F" + i, () -> mAction.function(n), "f" + i);
            }
            for (int i = 7; i <= 12; i++) {
                final int n = i;
                addGridCell(mUserRow, "F" + i, () -> mAction.function(n), "f" + i);
            }
        } else {
            addGridCell(mRow, "Esc",    () -> mAction.special("esc"),  "esc");
            addGridCell(mRow, "Tab",    () -> mAction.special("tab"),  "tab");
            addGridCell(mRow, "Home",   () -> mAction.special("home"), "home");
            addGridCell(mRow, "\u2191", () -> mAction.special("up"),   "up");
            addGridCell(mRow, "End",    () -> mAction.special("end"),  "end");
            addGridCell(mRow, "PgUp",   () -> mAction.special("pgup"), "pgup");

            addGridCell(mUserRow, "Ctrl",  () -> toggleMod("ctrl"), "ctrl");
            addGridCell(mUserRow, "Alt",   () -> toggleMod("alt"),  "alt");
            addGridCell(mUserRow, "\u2190", () -> mAction.special("left"),  "left");
            addGridCell(mUserRow, "\u2193", () -> mAction.special("down"),  "down");
            addGridCell(mUserRow, "\u2192", () -> mAction.special("right"), "right");
            addGridCell(mUserRow, "PgDn",  () -> mAction.special("pgdn"),  "pgdn");
        }

        mRoot.addView(mRow, rowParams());
        mRoot.addView(mUserRow, rowParams());

        // The tab that would take you back is the one that is worth seeing; the other one is
        // the hint that a layer exists at all.
        mLeftTab.setVisibility(fnLayer ? View.VISIBLE : View.INVISIBLE);
        mRightTab.setVisibility(fnLayer ? View.INVISIBLE : View.VISIBLE);
        refresh();
    }

    /** Switch between the main keys and F1..F12. */
    private void setFnLayer(boolean fnLayer) {
        if (mFnLayer == fnLayer) return;
        mRepeat.stop();
        buildLayer(fnLayer);
    }

    /** The view to add to the layout. */
    View view() {
        return mFrame;
    }

    boolean ctrl() { return mCtrl; }
    boolean alt() { return mAlt; }
    boolean shift() { return mShift; }

    /** Release every latched modifier; called after a keystroke has used them. */
    void clear() {
        mCtrl = mAlt = mShift = false;
        refresh();
    }

    void setVisible(boolean visible) {
        mFrame.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) mRepeat.stop();
    }

    /** Stop any hold in progress; called when the window loses focus. */
    void cancelRepeat() {
        mRepeat.stop();
    }

    /** Recolour the bar for a scheme (OLED is true black). */
    void applyTheme(boolean oled) {
        int bg = oled ? 0xff0d0d0d : BG;
        mFrame.setBackgroundColor(bg);
        mRoot.setBackgroundColor(bg);
        mRow.setBackgroundColor(bg);
        mUserRow.setBackgroundColor(bg);
    }

    private LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    }

    /** Modifier latch - CTRL/ALT/SHIFT share one state and one visual. */
    private void toggleMod(String kind) {
        switch (kind) {
            case "ctrl": mCtrl = !mCtrl; break;
            case "alt": mAlt = !mAlt; break;
            case "shift": mShift = !mShift; break;
            default: break;
        }
        refresh();
    }

    /**
     * One grid cell: the label centred in the cell, the cell is the whole touch target.
     *
     * <p>Press fires the key immediately, holding keeps firing it (see {@link KeyRepeat}).
     * The repeat is owned by the bar, not by the cell, so switching layers mid-hold or
     * hiding the bar cannot leave a chain behind: every one of those paths stops the same
     * chain, and the chain also caps itself.
     */
    private void addGridCell(LinearLayout row, String label, Runnable action, String tag) {
        TextView cell = new TextView(mContext);
        cell.setText(label);
        cell.setTextSize(13);
        cell.setTextColor(KEY_TEXT);
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(pillBackground());
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setTag(tag);
        // The gap between keys is the mterm cue: cells read as separate keys without needing
        // a border, and the 1dp divider between them stays out of the way.
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.rightMargin = dp(3);
        cell.setLayoutParams(lp);

        cell.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mRepeated = false;
                    mRepeat.start(() -> {
                        mRepeated = true;
                        action.run();
                    });
                    // false: the view still gets its click on release, which is the single
                    // press path. The repeat has already sent everything after the delay.
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mRepeat.stop();
                    return false;
                default:
                    return false;
            }
        });
        cell.setOnClickListener(v -> {
            if (mRepeated) {
                // The hold already sent this key a number of times; the release click is the
                // one duplicate worth suppressing.
                mRepeated = false;
                return;
            }
            action.run();
        });
        row.addView(cell);
    }

    /**
     * The barely-there tab at each edge that switches layers.
     *
     * <p>Contrast is deliberate: the key cells are 0xff2a2d33 on 0xff1b1d21, and this is
     * 0xff26292f - a shape you find when you look for it and do not read as a key when you
     * are aiming at the arrows.
     */
    private TextView edgeTab(String label, Runnable action) {
        TextView t = new TextView(mContext);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(0xff5c6270);
        t.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setColor(EDGE_TAB);
        d.setCornerRadius(dp(8));
        t.setBackground(d);
        t.setClickable(true);
        t.setFocusable(false);
        t.setPadding(0, 0, 0, 0);
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private void refresh() {
        java.util.ArrayList<View> all = new java.util.ArrayList<>();
        collectViews(mRoot, all);
        for (View v : all) {
            if (!(v instanceof TextView)) continue;
            Object tag = v.getTag();
            if (tag == null) continue;
            boolean on;
            switch (tag.toString()) {
                case "ctrl": on = mCtrl; break;
                case "alt": on = mAlt; break;
                case "shift": on = mShift; break;
                default: continue;
            }
            // Latched = a SOLID blue background replaces the state list entirely. Toggling
            // through a StateListDrawable's inner gradient was unreliable: getCurrent()
            // returns whichever state drawable is active, so the recolour sometimes landed
            // on the pressed copy and the latch looked like it did nothing.
            if (on) {
                GradientDrawable onBg = new GradientDrawable();
                onBg.setColor(KEY_ON);
                onBg.setCornerRadius(dp(10));
                v.setBackground(onBg);
                ((TextView) v).setTextColor(0xffffffff);
            } else {
                v.setBackground(pillBackground());
                ((TextView) v).setTextColor(KEY_TEXT);
            }
        }
    }

    /**
     * The resting key background. Flat, one step lighter than the bar, no gradient or
     * bevel - a modern terminal key is a quiet surface, not a plastic button.
     */
    private android.graphics.drawable.Drawable pillBackground() {
        GradientDrawable resting = new GradientDrawable();
        resting.setColor(KEY);
        resting.setCornerRadius(dp(10));

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(KEY_PRESSED);
        pressed.setCornerRadius(dp(10));

        android.graphics.drawable.StateListDrawable sl =
                new android.graphics.drawable.StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sl.addState(new int[]{}, resting);
        return sl;
    }

    private void collectViews(ViewGroup group, java.util.ArrayList<View> out) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View c = group.getChildAt(i);
            out.add(c);
            if (c instanceof ViewGroup) collectViews((ViewGroup) c, out);
        }
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, mContext.getResources().getDisplayMetrics()));
    }
}
