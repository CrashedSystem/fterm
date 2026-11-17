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

    /**
     * A configurable key: the label shown on the pill and an action spec, saved to prefs as
     * text so the bar survives restarts. Spec grammar:
     *
     *   special:tab | special:esc | special:left | special:right | special:up
     *   special:down | special:home | special:end | special:pgup | special:pgdn
     *   raw:&lt;characters&gt;        e.g. raw:\u0003 is ^C
     *
     * The default set is the classic Termux row; everything the user adds comes from the
     * catalogue below so the editor cannot invent a broken spec.
     */
    static final String[][] CATALOGUE = {
            {"TAB", "special:tab"}, {"ESC", "special:esc"},
            {"^C", "raw:\u0003"}, {"^Z", "raw:\u001a"}, {"^D", "raw:\u0004"},
            {"^L", "raw:\u000c"}, {"^W", "raw:\u0017"}, {"^U", "raw:\u0015"},
            {"^R", "raw:\u0012"}, {"|", "raw:|"}, {"~", "raw:~"},
            {"/", "raw:/"}, {"-", "raw:-"},
            {"\u2190", "special:left"}, {"\u2192", "special:right"},
            {"\u2191", "special:up"}, {"\u2193", "special:down"},
            {"HOME", "special:home"}, {"END", "special:end"},
            {"PGUP", "special:pgup"}, {"PGDN", "special:pgdn"},
    };

    static String labelOf(String spec) {
        for (String[] c : CATALOGUE) if (c[1].equals(spec)) return c[0];
        if (spec.startsWith("raw:")) {
            String r = spec.substring(4);
            // A control char reads as ^X; anything else shows as itself.
            if (r.length() == 1 && r.charAt(0) < 32) {
                return "^" + (char) ('@' + r.charAt(0));
            }
            return r;
        }
        return spec;
    }

    static void apply(String spec, Action action) {
        if (spec.startsWith("special:")) {
            action.special(spec.substring(8));
        } else if (spec.startsWith("raw:")) {
            action.raw(spec.substring(4));
        }
    }

    // The bar sits directly on the keyboard, so it needs its own hairline or the two grays
    // merge. Keys get a lighter fill than the bar and a 1dp gap, which is what makes them
    // read as buttons rather than as a single strip.
    private static final int BG = 0xff202124;
    private static final int KEY = 0xff33363b;
    private static final int KEY_PRESSED = 0xff44484f;
    private static final int KEY_ON = 0xff4a7fc1;
    private static final int DIVIDER = 0xff15161a;

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
        mRow.setPadding(dp(4), dp(6), dp(4), dp(6));

        for (String[] m : MODIFIERS) {
            mRow.addView(modifier(m[0], m[1]));
        }
        mRow.addView(special("\u2328", mAction::toggleKeyboard));

        HorizontalScrollView hsv = new HorizontalScrollView(mContext);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(BG);
        hsv.setForeground(null);
        // A 1dp divider on top: the bar and the keyboard are both light greys otherwise.
        hsv.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                new android.graphics.drawable.ColorDrawable(DIVIDER),
                new android.graphics.drawable.ColorDrawable(BG),
        }));
        hsv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
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

    /** The configured user keys, between the modifiers and the keyboard toggle. */
    void setKeys(java.util.List<String> specs) {
        // Remove everything after the modifiers and before the keyboard toggle.
        while (mRow.getChildCount() > MODIFIERS.length + 1) {
            mRow.removeViewAt(MODIFIERS.length);
        }
        for (String spec : specs) {
            TextView b = special(KeyBar.labelOf(spec), () -> KeyBar.apply(spec, mAction));
            b.setTag(spec);          // keep the spec on the view for the editor
            b.setLongClickable(true);
            mRow.addView(b, MODIFIERS.length);
        }
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
        // 36dp tall inside a 50dp bar: a finger target the platform would accept, with the
        // visual weight of a key rather than a label.
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        lp.rightMargin = dp(5);
        t.setLayoutParams(lp);
        t.setPadding(dp(10), 0, dp(10), 0);
        t.setMinWidth(dp(38));
        t.setClickable(true);
        t.setFocusable(true);
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
