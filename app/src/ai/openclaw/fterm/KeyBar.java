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
    private LinearLayout mRow;
    private LinearLayout mRoot;
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
        // Docked, mterm-style: two fixed rows of six text keys on the bar background.
        // No pills, no bevels - a cell is its whole touch target, pressed cells get a
        // subtle fill, latched modifiers turn accent. Nothing scrolls, nothing hides.
        mRoot = new LinearLayout(mContext);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mRoot.setBackgroundColor(BG);
        mRoot.setPadding(dp(2), dp(4), dp(2), dp(4));

        mRow = new LinearLayout(mContext);
        mUserRow = new LinearLayout(mContext);
        for (LinearLayout row : new LinearLayout[]{mRow, mUserRow}) {
            row.setOrientation(LinearLayout.HORIZONTAL);
        }
        addGridCell(mRow, "Esc",      () -> mAction.special("esc"),   "esc");
        addGridCell(mRow, "Tab",      () -> mAction.special("tab"),   "tab");
        addGridCell(mRow, "Home",     () -> mAction.special("home"),  "home");
        addGridCell(mRow, "\u2191",   () -> mAction.special("up"),    "up");
        addGridCell(mRow, "End",      () -> mAction.special("end"),   "end");
        addGridCell(mRow, "PgUp",     () -> mAction.special("pgup"),  "pgup");

        addGridCell(mUserRow, "Ctrl", () -> toggleMod("ctrl"), "ctrl");
        addGridCell(mUserRow, "Alt",  () -> toggleMod("alt"),  "alt");
        addGridCell(mUserRow, "\u2190", () -> mAction.special("left"),  "left");
        addGridCell(mUserRow, "\u2193", () -> mAction.special("down"),  "down");
        addGridCell(mUserRow, "\u2192", () -> mAction.special("right"), "right");
        addGridCell(mUserRow, "PgDn", () -> mAction.special("pgdn"),  "pgdn");

        mRoot.addView(mRow, rowParams());
        mRoot.addView(mUserRow, rowParams());

        HorizontalScrollView hsv = new HorizontalScrollView(mContext);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(BG);
        hsv.setFillViewport(true);
        hsv.setClipToPadding(false);
        hsv.addView(mRoot);
        return hsv;
    }

    private LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    }

    /** Modifier latch - the editor's mod: specs and these built-ins share one state. */
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
     * One grid cell: the label centred in the cell, the cell is the touch target.
     * mterm-style - text on the bar, not buttons on the bar.
     *
     * <p>ONE PRESS = ONE KEY. There is deliberately no hold-to-repeat here. The repeat
     * implementation posted a 350 ms starter that re-posted a 60 ms chain, and every
     * attempt to cancel it left a path alive: the measured result was ~200 frames per
     * second for nine seconds straight (1,800 synthetic keypresses from one touch). A
     * phone key bar does not need auto-repeat badly enough to justify that failure mode,
     * so the whole mechanism is gone rather than patched again.
     */
    private void addGridCell(LinearLayout row, String label, Runnable action,
                             String tag) {
        TextView cell = new TextView(mContext);
        cell.setText(label);
        cell.setTextSize(13);
        cell.setTextColor(KEY_TEXT);
        cell.setGravity(android.view.Gravity.CENTER);
        cell.setBackground(pillBackground());
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setTag(tag.startsWith("mod:") ? tag.substring(4) : tag);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.rightMargin = dp(1);
        cell.setLayoutParams(lp);

        cell.setOnClickListener(v -> action.run());
        row.addView(cell);
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
        // Docked layout: the two fixed rows carry the core keys. Any configured key that
        // is not already drawn (e.g. ^D, HOME, a paste chip) is appended to row 2 as a
        // grid cell - the editor adds specs here, the bar renders them like built-ins.
        if (mUserRow == null) return;
        java.util.ArrayList<String> already = new java.util.ArrayList<>();
        already.add("special:esc"); already.add("special:tab");
        already.add("special:home"); already.add("special:end");
        already.add("special:pgup"); already.add("special:pgdn");
        already.add("special:left"); already.add("special:right");
        already.add("special:up"); already.add("special:down");
        already.add("action:paste");
        already.add("raw:\u0003");
        for (String[] m : MODIFIERS) already.add(m[1]);
        for (String spec : specs) {
            if (already.contains(spec)) continue;
            addGridCell(mUserRow, KeyBar.labelOf(spec),
                    () -> KeyBar.apply(spec, mAction), spec);
        }
    }

    /** True when the spec is one of the keys already drawn in the fixed rows. */
    private static boolean isFixed(String spec) {
        switch (spec) {
            case "special:tab": case "special:esc": case "raw:\u0003":
            case "special:left": case "special:right":
            case "special:up": case "special:down":
            case "action:paste":
                return true;
            default:
                return false;
        }
    }

    private TextView special(String label, Runnable action) {
        TextView b = pill(label, KEY);
        // One press, one action - same rule as addGridCell. This used to carry the same
        // 350ms-starter/60ms-chain repeat that flooded the PTY from a single arrow tap;
        // a keyboard toggle has even less use for auto-repeat than an arrow key does.
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private TextView pill(String label, int bg) {
        TextView t = new TextView(mContext);
        t.setText(label);
        boolean isGlyph = label.length() == 1 && !Character.isLetterOrDigit(label.charAt(0));
        t.setTextSize(isGlyph ? 16 : 13);
        t.setTextColor(KEY_TEXT);
        t.setGravity(Gravity.CENTER);
        UiFont.apply(t, false);
        // A raised key: top highlight + bottom shade make it sit above the bar instead of
        // being a flat pill painted onto it.
        t.setBackground(pillBackground());
        // 36dp tall inside a 50dp bar: a finger target the platform would accept, with the
        // visual weight of a key rather than a label.
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        lp.rightMargin = dp(6);
        t.setLayoutParams(lp);
        t.setPadding(dp(12), 0, dp(12), 0);
        t.setMinWidth(dp(44));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private void refresh() {
        // Modifiers live in row 1 (inside the fixed vertical container), so scan the
        // whole hierarchy of the docked layout rather than one row.
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
