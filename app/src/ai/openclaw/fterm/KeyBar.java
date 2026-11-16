package ai.openclaw.fterm;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The row of keys above the soft keyboard.
 *
 * Extracted from MainActivity: it is a self-contained widget with its own state (the sticky
 * modifiers) and one way to talk to the world ({@link Action}). Nothing in it knows what a
 * tab is, which is what makes it testable and what keeps MainActivity from growing.
 *
 * The modifiers are sticky rather than held: a phone cannot press two keys at once, so CTRL
 * latches and the next keystroke releases it. That release lives in MainActivity's
 * onCodePoint, not here - this only renders the latched state.
 */
final class KeyBar {

    /** What a key press means to the terminal. */
    interface Action {
        /** A named special key: tab, esc, left, right, up, down. */
        void special(String name);

        /** Raw bytes to write (e.g. ^C). */
        void raw(String bytes);

        void toggleKeyboard();
    }

    private static final String[][] MODIFIERS = {
            {"CTRL", "ctrl"}, {"ALT", "alt"}, {"SHIFT", "shift"},
    };

    private static final int BG = 0xff252526;
    private static final int KEY = 0xff3a3a3a;
    private static final int KEY_ON = 0xff5a8dd6;

    private final Context mContext;
    private final Action mAction;
    private final LinearLayout mRow;
    private final HorizontalScrollView mScroll;

    private boolean mCtrl, mAlt, mShift;

    KeyBar(Context context, Action action) {
        mContext = context;
        mAction = action;
        mRow = new LinearLayout(context);
        mScroll = build();
    }

    /** The view to add to the layout. */
    View view() {
        return mScroll;
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
        mScroll.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Recolour the bar for a scheme (OLED is true black). */
    void applyTheme(boolean oled) {
        int bg = oled ? 0xff0d0d0d : BG;
        mScroll.setBackgroundColor(bg);
        mRow.setBackgroundColor(bg);
    }

    private HorizontalScrollView build() {
        mRow.setOrientation(LinearLayout.HORIZONTAL);
        mRow.setBackgroundColor(BG);
        mRow.setPadding(dp(3), dp(2), dp(3), dp(2));

        for (String[] m : MODIFIERS) {
            mRow.addView(modifier(m[0], m[1]));
        }
        mRow.addView(special("TAB", () -> mAction.special("tab")));
        mRow.addView(special("ESC", () -> mAction.special("esc")));
        mRow.addView(special("^C", () -> mAction.raw("\u0003")));
        mRow.addView(special("\u2190", () -> mAction.special("left")));
        mRow.addView(special("\u2192", () -> mAction.special("right")));
        mRow.addView(special("\u2191", () -> mAction.special("up")));
        mRow.addView(special("\u2193", () -> mAction.special("down")));
        mRow.addView(special("\u2328", mAction::toggleKeyboard));

        HorizontalScrollView hsv = new HorizontalScrollView(mContext);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(BG);
        hsv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        hsv.addView(mRow);
        return hsv;
    }

    private TextView modifier(String label, String kind) {
        TextView b = pill(label, KEY);
        b.setTag(kind);
        b.setOnClickListener(v -> {
            switch (kind) {
                case "ctrl": mCtrl = !mCtrl; break;
                case "alt": mAlt = !mAlt; break;
                case "shift": mShift = !mShift; break;
                default: break;
            }
            refresh();
        });
        return b;
    }

    private TextView special(String label, Runnable action) {
        TextView b = pill(label, KEY);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private TextView pill(String label, int bg) {
        TextView t = new TextView(mContext);
        t.setText(label);
        t.setTextSize(11);
        t.setTextColor(0xffe6e6e6);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(6));
        t.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
        lp.rightMargin = dp(3);
        t.setLayoutParams(lp);
        t.setPadding(dp(6), 0, dp(6), 0);
        t.setMinWidth(dp(28));
        return t;
    }

    private void refresh() {
        for (int i = 0; i < mRow.getChildCount(); i++) {
            View v = mRow.getChildAt(i);
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
            ((GradientDrawable) v.getBackground()).setColor(on ? KEY_ON : KEY);
        }
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, mContext.getResources().getDisplayMetrics()));
    }
}
