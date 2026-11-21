package io.github.crashedsystem.terminal;

import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.termux.view.TerminalView;

/**
 * The terminal's right-click menu.
 *
 * <p>Right-click was wired all the way down and did nothing: {@code TerminalView} calls
 * {@code showContextMenu()} on a secondary button, and no {@code onCreateContextMenu} listener
 * exists anywhere in the app, so the platform had nothing to show. On DeX and on a tablet with
 * a trackpad that left a mouse user with the long-press-and-drag-the-handles route to copy.
 *
 * <p>This is the Windows Terminal menu for the same surface - Copy, Paste, Select All, then
 * Find and Settings - drawn as a small plate at the pointer. It is a Dialog rather than the
 * platform context menu because the platform's one needs the selection handles' ActionMode
 * plumbing, which is what Termux's own menu is built on.
 */
final class TerminalMenu {

    private static final int BG = 0xff1f2126;
    private static final int TEXT = 0xffe8eaee;
    private static final int HAIRLINE = 0xff2c2f36;
    private static final int ROW_H = 40;
    private static final int WIDTH = 208;

    private final MainActivity mActivity;
    private final TerminalView mView;

    private TerminalMenu(MainActivity activity, TerminalView view) {
        mActivity = activity;
        mView = view;
    }

    /**
     * Listen for right-clicks on the terminal.
     *
     * <p>Nothing is registered on the Activity: the view hands the event over directly, so the
     * menu belongs to the terminal and not to whichever window happens to hold it.
     */
    static void attach(final MainActivity activity, TerminalView view) {
        view.setContextMenuRequest(event -> new TerminalMenu(activity, view).show(event));
    }

    private void show(MotionEvent event) {
        final android.app.Dialog dialog = new android.app.Dialog(mActivity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout list = new LinearLayout(mActivity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(4));
        list.setBackground(Chrome.plate(BG, 10));

        addRow(list, dialog, io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0031), mActivity::copySelection);
        addRow(list, dialog, io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0032), mActivity::pasteClipboard);
        addRow(list, dialog, io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0359), () -> mView.selectAll());
        addRule(list);
        addRow(list, dialog, io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0063), mActivity::find);
        addRow(list, dialog, io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0306), mActivity::showSettings);

        dialog.setContentView(list);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        Window w = dialog.getWindow();
        if (w == null) return;
        w.setBackgroundDrawable(new ColorDrawable(0x00000000));
        int width = dp(WIDTH);
        w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);

        // At the pointer, nudged back inside the screen when the click is in the last few
        // hundred pixels of it - a menu that hangs off the right edge of a 12" screen puts
        // its labels out of reach of the cursor that opened it.
        DisplayMetrics dm = mActivity.getResources().getDisplayMetrics();
        int[] at = new int[2];
        mView.getLocationOnScreen(at);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = clamp((int) (at[0] + event.getX()), dp(8), dm.widthPixels - width - dp(8));
        // The lower bound is a guess at the menu height, so on a short window - a phone in
        // landscape, or a split-screen tablet - it can exceed the screen. Clamping to a
        // negative maximum would put the menu off the bottom, so the ceiling is floored.
        lp.y = clamp((int) (at[1] + event.getY()), dp(8),
                Math.max(dp(8), dm.heightPixels - dp(280)));
        w.setAttributes(lp);
    }

    private void addRow(LinearLayout list, final android.app.Dialog dialog, String label,
                        final Runnable action) {
        TextView row = new TextView(mActivity);
        row.setText(label);
        row.setTextSize(13.5f);
        row.setTextColor(TEXT);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ROW_H)));
        android.util.TypedValue tv = new android.util.TypedValue();
        if (mActivity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, tv, true)) {
            row.setBackgroundResource(tv.resourceId);
        }
        row.setClickable(true);
        row.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        list.addView(row);
    }

    private void addRule(LinearLayout list) {
        View line = new View(mActivity);
        line.setBackgroundColor(HAIRLINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.8f)));
        lp.leftMargin = dp(10);
        lp.rightMargin = dp(10);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        line.setLayoutParams(lp);
        list.addView(line);
    }

    private static int clamp(int value, int min, int max) {
        // A caller that computed an inverted range (min > max) wants the minimum, not the
        // maximum: Math.min would return max, which is the off-screen edge.
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, mActivity.getResources().getDisplayMetrics()));
    }
}
