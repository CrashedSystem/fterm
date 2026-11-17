package ai.openclaw.fterm;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
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

        /** The terminal's own paste (clipboard image detection included). */
        void paste();
    }

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
    /** Modifiers are also just keys: the editor treats them like the rest. */
    static final String[][] MODIFIERS = {
            {"CTRL", "mod:ctrl"}, {"ALT", "mod:alt"}, {"SHIFT", "mod:shift"},
    };

    static final String[][] CATALOGUE = {
            {"\ud074\ub9bd\ubcf5\uc0ac", "action:paste"},
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
        for (String[] c : MODIFIERS) if (c[1].equals(spec)) return c[0];
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
        } else if (spec.equals("action:paste")) {
            action.paste();
        }
    }

    // The bar sits directly on the keyboard, so it needs its own hairline or the two grays
    // merge. Keys get a lighter fill than the bar and a 1dp gap, which is what makes them
    // read as buttons rather than as a single strip.
    private static final int BG = 0xff1b1d21;       // bar: a shade above pure black
    private static final int KEY = 0xff2a2d33;      // key: one step up from the bar
    private static final int KEY_PRESSED = 0xff3a3f47;
    private static final int KEY_ON = 0xff2f5a9e;   // latched: muted accent, not neon
    private static final int KEY_TEXT = 0xffd7d9dd;
    private static final int DIVIDER = 0xff101114;

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
        mRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        mRow.setBackgroundColor(BG);
        mRow.setPadding(dp(4), dp(6), dp(4), dp(6));

        // Docked layout, no swipe: row 1 is the fixed core (modifiers + the keys you
        // always need), row 2 is the user's configured keys. A swipe to reach a key was
        // a mis-tap factory - both rows are always fully visible.
        mRow.addView(chip2());
        // The scroll view keeps its API for MainActivity; with fillViewport and no
        // scrolling content it behaves like a static container.
        HorizontalScrollView hsv = new HorizontalScrollView(mContext);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(BG);
        hsv.setForeground(null);
        hsv.setFillViewport(true);
        hsv.setClipToPadding(false);
        hsv.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                new android.graphics.drawable.ColorDrawable(DIVIDER),
                new android.graphics.drawable.ColorDrawable(BG),
        }));
        hsv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hsv.addView(mRow);
        return hsv;
    }

    /** The fixed rows: modifiers + core keys (row 1), arrows + paste (row 2). */
    private View chip2() {
        LinearLayout fixed = new LinearLayout(mContext);
        fixed.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row1 = new LinearLayout(mContext);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(android.view.Gravity.CENTER);
        row1.setPadding(dp(4), dp(4), dp(4), dp(2));
        row1.addView(modifier("CTRL", "ctrl"));
        row1.addView(modifier("ALT", "alt"));
        row1.addView(modifier("SHIFT", "shift"));
        row1.addView(special("TAB", () -> mAction.special("tab")));
        row1.addView(special("ESC", () -> mAction.special("esc")));
        row1.addView(special("^C", () -> mAction.raw("\u0003")));
        mUserRow = new LinearLayout(mContext);
        mUserRow.setOrientation(LinearLayout.HORIZONTAL);
        mUserRow.setGravity(android.view.Gravity.CENTER);
        mUserRow.setPadding(dp(4), dp(2), dp(4), dp(4));
        mUserRow.addView(special("\u2190", () -> mAction.special("left")));
        mUserRow.addView(special("\u2193", () -> mAction.special("down")));
        mUserRow.addView(special("\u2191", () -> mAction.special("up")));
        mUserRow.addView(special("\u2192", () -> mAction.special("right")));
        mUserRow.addView(special("PASTE", mAction::paste));
        fixed.addView(row1);
        fixed.addView(mUserRow);
        return fixed;
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
    private int mUserKeyCount = 0;
    private LinearLayout mUserRow;

    private void addKeyboardToggle() {
        // No tag: refresh() only recolours views tagged with a modifier kind. The toggle
        // sits AFTER the user-key segment; setKeys() inserts at MODIFIERS.length so the
        // toggle stays at the end regardless of how many user keys there are.
        mRow.addView(special("\u2328", mAction::toggleKeyboard));
    }

    void setKeys(java.util.List<String> specs) {
        // Docked layout: the configured keys render into their own row, appended after
        // the fixed arrows/paste keys. Removing and re-adding keeps the order stable and
        // never touches the fixed rows.
        if (mUserRow == null) return;
        // Keep the fixed arrow/paste keys: they are the first five children.
        int fixed = 5;
        while (mUserRow.getChildCount() > fixed) {
            mUserRow.removeViewAt(fixed);
        }
        for (String spec : specs) {
            if (spec.startsWith("mod:")) continue;   // modifiers are always in row 1
            TextView b = special(KeyBar.labelOf(spec), () -> KeyBar.apply(spec, mAction));
            b.setTag(spec);
            b.setLongClickable(true);
            mUserRow.addView(b);
        }
    }

    private TextView special(String label, Runnable action) {
        TextView b = pill(label, KEY);
        // Hold to repeat, entirely in the touch listener. Two cancellable pieces: a
        // 350ms starter and the 60ms chain it spawns - BOTH stored under one tag so a
        // short press cancels everything. The previous version cancelled only the chain,
        // the starter then fired it forever: "one press, endless repeat".
        b.setOnTouchListener((v, ev) -> {
            int actionId = ev.getAction();
            if (actionId == android.view.MotionEvent.ACTION_DOWN) {
                final Handler h = new Handler();
                final Runnable[] starter = new Runnable[1];
                final Runnable[] chain = new Runnable[1];
                chain[0] = () -> {
                    action.run();
                    h.postDelayed(chain[0], 60);
                };
                starter[0] = () -> {
                    action.run();
                    h.postDelayed(chain[0], 60);
                };
                v.setTag(R.id.repeat_handler, h);
                v.setTag(R.id.repeat_tag, new Runnable[]{starter[0], chain[0]});
                action.run();   // tap = one keypress, immediately
                h.postDelayed(starter[0], 350);
                // Keep the touch even if the finger wanders - without this the
                // HorizontalScrollView cancels the press on the first pixel of movement.
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (actionId == android.view.MotionEvent.ACTION_UP
                    || actionId == android.view.MotionEvent.ACTION_CANCEL) {
                Handler h = (Handler) v.getTag(R.id.repeat_handler);
                Object tag = v.getTag(R.id.repeat_tag);
                if (h != null && tag instanceof Runnable[]) {
                    // Cancel BOTH pieces: the 350ms starter and the 60ms chain it spawns.
                    // The chain re-posts itself, so removing only the starter let it run
                    // forever - the "one press, endless repeat" bug.
                    for (Runnable r : (Runnable[]) tag) h.removeCallbacks(r);
                    v.setTag(R.id.repeat_tag, null);
                } else if (h != null && tag instanceof Runnable) {
                    h.removeCallbacks((Runnable) tag);
                }
            }
            return false;
        });
        return b;
    }

    private TextView pill(String label, int bg) {
        TextView t = new TextView(mContext);
        t.setText(label);
        t.setTextSize(12);
        t.setTextColor(KEY_TEXT);
        t.setGravity(Gravity.CENTER);
        UiFont.apply(t, false);
        // A raised key: top highlight + bottom shade make it sit above the bar instead of
        // being a flat pill painted onto it.
        t.setBackground(pillBackground());
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
        // Modifiers live in row 1 (inside the fixed vertical container), so scan the
        // whole hierarchy of the docked layout rather than one row.
        java.util.ArrayList<View> all = new java.util.ArrayList<>();
        collectViews(mRow, all);
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
            } else if (!(v.getBackground() instanceof android.graphics.drawable.StateListDrawable)) {
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
        android.graphics.drawable.GradientDrawable resting =
                new android.graphics.drawable.GradientDrawable();
        resting.setColor(KEY);
        resting.setCornerRadius(dp(10));

        android.graphics.drawable.GradientDrawable pressed =
                new android.graphics.drawable.GradientDrawable();
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
