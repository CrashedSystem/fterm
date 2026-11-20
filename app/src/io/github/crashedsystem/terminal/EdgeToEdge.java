package io.github.crashedsystem.terminal;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.LinearLayout;

/**
 * Edge-to-edge layout, the way Android 15+ expects it.
 *
 * From targetSdk 35 the system ignores {@code setStatusBarColor()} and
 * {@code setNavigationBarColor()} and draws the app behind the system bars whether it wants
 * to or not. So the window must fit itself: the tab strip is inset from the status bar and
 * the key bar from the navigation bar and the keyboard.
 *
 * The insets grow the rows rather than just padding them. A fixed-height row with a bottom
 * inset would squeeze its children to zero height, which is exactly what happened to the
 * key bar on the first attempt.
 */
final class EdgeToEdge {

    private EdgeToEdge() {}

    /** Let the window draw under the system bars. */
    static void enable(Activity activity) {
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        if (Build.VERSION.SDK_INT < 35) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(0xff2b2b2b);
            window.setNavigationBarColor(0xff000000);
        }
    }

    /**
     * Fit three rows between the system bars.
     *
     * <p>The base heights are dp, not px: they are multiplied out on every inset pass, so a
     * density change (DeX on an external monitor) is picked up without rebuilding the layout.
     *
     * <p>The bottom inset lives on the key bar while the bar is showing. When it is not - the
     * user turned it off, or a hardware keyboard hid it - it moves to {@code frame} instead,
     * because otherwise the last rows of the terminal would sit behind the navigation bar.
     *
     * @param root       the vertical column holding the three rows
     * @param topBar     the tab strip row
     * @param keyBar     the key row
     * @param frame      the terminal's frame, which takes the inset the key bar is not using
     * @param topBaseDp  the tab strip height without insets, in dp
     * @param keyBaseDp  the key row height without insets, in dp
     */
    static void applyInsets(final View root, final View topBar, final View keyBar,
                            final View frame, final float topBaseDp, final float keyBaseDp) {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                // Read the density here rather than closing over it: this listener outlives
                // any configuration change the manifest keeps the activity alive for.
                float density = v.getResources().getDisplayMetrics().density;
                int topBase = Math.round(topBaseDp * density);
                int keyBase = Math.round(keyBaseDp * density);
                // 8dp of slack under the key row (see the padding computation below).
                int slack = Math.round(8 * density);
                int top, bottom;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars =
                            insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                    top = bars.top;
                    // While the keyboard is up its inset replaces the navigation bar inset,
                    // because the nav bar is drawn over the keyboard.
                    bottom = Math.max(bars.bottom, ime.bottom);
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }

                if (topBar != null) {
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) topBar.getLayoutParams();
                    int want = topBase + top;
                    if (lp.height != want) {
                        lp.height = want;
                        topBar.setLayoutParams(lp);
                    }
                    topBar.setPadding(topBar.getPaddingLeft(), top,
                            topBar.getPaddingRight(), topBar.getPaddingBottom());
                }

                if (keyBar != null) {
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) keyBar.getLayoutParams();
                    int want = keyBase + bottom;
                    boolean changed = lp.height != want;
                    if (changed) {
                        lp.height = want;
                        keyBar.setLayoutParams(lp);
                    }
                    // +8dp of slack: on some OneUI builds the reported IME inset is a few
                    // pixels short of the real keyboard top, which visually sinks the
                    // bottom edge of the key row into the keyboard.
                    int pad = bottom + slack;
                    if (keyBar.getPaddingBottom() != pad) {
                        keyBar.setPadding(keyBar.getPaddingLeft(), keyBar.getPaddingTop(),
                                keyBar.getPaddingRight(), pad);
                    }
                }

                if (frame != null) {
                    // The key bar is what normally absorbs the navigation bar; when it is not
                    // on screen the terminal has to.
                    boolean barShowing = keyBar != null && keyBar.getVisibility() == View.VISIBLE;
                    int pad = barShowing ? 0 : bottom;
                    if (frame.getPaddingBottom() != pad) {
                        frame.setPadding(frame.getPaddingLeft(), frame.getPaddingTop(),
                                frame.getPaddingRight(), pad);
                    }
                }
                return insets;
            }
        });
        root.requestApplyInsets();
    }
}
