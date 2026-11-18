package ai.openclaw.fterm;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One tab's plate: the icon slot, the title, and the close box.
 *
 * <p>WT's tab is a rounded-top plate whose fill matches the terminal it belongs to, so the
 * active tab reads as continuous with the pane below it and the inactive ones recede into the
 * bar. Inside it: a 16dp icon slot (the shell glyph), the title, and a close box that only
 * appears on the active tab - the same "the tab you are looking at is the one you can close"
 * rule WT uses, and the reason a stray X cannot kill a background build.
 *
 * <p>This is presentation, so it lives outside the activity; the activity owns which tab is
 * active and what closing one means.
 */
final class TabStrip {

    /** The three views a tab is made of, kept together so painting one is one call. */
    static final class Chip {
        LinearLayout plate;
        TextView title;
        TextView close;
    }

    private final Activity mActivity;

    TabStrip(Activity activity) {
        mActivity = activity;
    }

    /**
     * Build a tab.
     *
     * <p>The title's four marquee settings are all required and each fails silently on its
     * own: MARQUEE as the ellipsizer, horizontal scrolling on, the view selected (the
     * platform's marquee is driven by the selected state, not by focus), and a bounded width -
     * a wrap_content TextView always has room for its text, so it has nothing to scroll.
     */
    Chip create(String title, Runnable onActivate, Runnable onClose) {
        Chip chip = new Chip();

        LinearLayout plate = new LinearLayout(mActivity);
        plate.setOrientation(LinearLayout.HORIZONTAL);
        plate.setGravity(Gravity.CENTER_VERTICAL);
        plate.setPadding(dp(8), 0, dp(4), 0);
        plate.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        plate.setOnClickListener(v -> onActivate.run());
        plate.setOnLongClickListener(v -> {
            onClose.run();
            return true;
        });

        TextView icon = new TextView(mActivity);
        icon.setText(">_");
        icon.setTextSize(9);
        icon.setTextColor(0xff8a8a8a);
        UiFont.apply(icon, false);
        icon.setGravity(Gravity.CENTER);
        icon.setIncludeFontPadding(false);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(34)));
        plate.addView(icon);

        TextView label = new TextView(mActivity);
        label.setTextSize(12);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        label.setHorizontallyScrolling(true);
        label.setMarqueeRepeatLimit(-1);
        label.setFocusable(false);
        // The cap is what gives the marquee something to scroll: with wrap_content and no
        // limit the view is always exactly as wide as the text.
        label.setMaxWidth(dp(150));
        // A TextView inside a taller row draws its text at the TOP unless told otherwise.
        // The tab title was doing exactly that - the row is 34dp and the text sat on its
        // upper edge, which reads as "글자가 위로 붙었다".
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setIncludeFontPadding(false);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        label.setPadding(dp(2), 0, dp(6), 0);
        plate.addView(label);

        TextView close = new TextView(mActivity);
        close.setText("\u00d7");
        close.setTextSize(15);
        close.setGravity(Gravity.CENTER);
        close.setIncludeFontPadding(false);
        // Right next to the title, not at the far edge: the X belongs to the tab, and a wide
        // gap makes it look like it belongs to the next one.
        close.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(34)));
        close.setOnClickListener(v -> onClose.run());
        plate.addView(close);

        chip.plate = plate;
        chip.title = label;
        chip.close = close;
        paint(chip, title, false, 0, false);
        return chip;
    }

    /**
     * Active tab: the terminal's own background, so the plate and the pane are one surface.
     * Inactive: the bar's colour, dimmed text, and no close box.
     */
    void paint(Chip chip, String title, boolean active, int termBg, boolean oled) {
        if (chip == null || chip.title == null || chip.plate == null) return;
        chip.title.setText(title);
        // The marquee runs only on the active tab. The platform drives a marquee from the
        // selected state, and leaving it set on inactive tabs would have every long title in
        // the strip scrolling at once, which is noise rather than information.
        //
        // Cleared first so a title change restarts the scroll at column zero. setSelected(true)
        // on a view that is already selected is a no-op, so without the toggle a shell that
        // updates its title mid-scroll leaves the new text halfway across the tab.
        chip.title.setSelected(false);
        chip.title.setSelected(active);

        GradientDrawable d = new GradientDrawable();
        // Inactive tabs are a slightly lighter gray than the bar so they read as tabs; the
        // active one takes the terminal's own background and grows into the pane.
        d.setColor(active ? termBg : (oled ? 0xff0a0a0a : 0xff303338));
        d.setCornerRadii(new float[]{dp(7), dp(7), dp(7), dp(7), 0, 0, 0, 0});
        // A 2dp accent at the bottom of the active tab is the cue that survives a dark theme,
        // where the tab fill and the pane are the same colour by design.
        if (active) d.setStroke(dp(2), 0xff4a7fc1);
        chip.plate.setBackground(d);

        chip.title.setTextColor(active ? 0xffffffff : 0xff9a9a9a);
        chip.close.setTextColor(active ? 0xffcfcfcf : 0x00000000);
        chip.close.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        chip.plate.setPadding(dp(8), 0, dp(4), 0);
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                mActivity.getResources().getDisplayMetrics());
    }
}
