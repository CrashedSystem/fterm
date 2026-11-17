package ai.openclaw.fterm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Windows Terminal-shaped chrome: the command palette, the icon strip, and the row/plate
 * primitives the settings sheet shares.
 *
 * <p>Windows Terminal is the reference not because of its colours but because of three
 * decisions it makes that a phone terminal usually gets wrong: commands are a searchable list
 * rather than a nest of menus, chrome is monochrome line art rather than coloured emoji, and
 * settings are a flat sheet of labelled rows rather than a second app.
 *
 * <p>Extracted from MainActivity for the same reason KeyBar and SettingsSheet were: the
 * activity is a coordinator and this is presentation.
 */
final class Chrome {

    /** What a palette entry does. Kept out of the palette so the list stays data. */
    interface Commands {
        void newTab();
        void closeTab();
        void renameTab();
        void nextTab();
        void paste();
        void copy();
        void biggerFont();
        void smallerFont();
        void settings();
        void toggleKeyboard();
        void palette();
    }

    /** One palette entry: the label shown, and the action it carries. */
    private static final class Entry {
        final String label;
        final Runnable action;
        Entry(String label, Runnable action) { this.label = label; this.action = action; }
        @Override public String toString() { return label; }
    }

    private final Activity mActivity;
    private final Commands mCommands;

    Chrome(Activity activity, Commands commands) {
        mActivity = activity;
        mCommands = commands;
    }

    /**
     * The command palette.
     *
     * <p>Usable with no keyboard at all, which is the only state a phone terminal spends most of
     * its time in: one full-height list of tall rows, always visible, and the search box is
     * optional - it does not take focus when the palette opens, so ignoring it runs the whole
     * palette by thumb. The earlier version put a focused {@link EditText} on top, so opening
     * the palette always raised the IME over the list it was meant to filter.
     */
    void palette() {
        final Entry[] entries = {
                new Entry("새 탭 열기", mCommands::newTab),
                new Entry("탭 닫기", mCommands::closeTab),
                new Entry("탭 이름 바꾸기", mCommands::renameTab),
                new Entry("다음 탭", mCommands::nextTab),
                new Entry("붙여넣기", mCommands::paste),
                new Entry("복사", mCommands::copy),
                new Entry("글자 크기 키우기", mCommands::biggerFont),
                new Entry("글자 크기 줄이기", mCommands::smallerFont),
                new Entry("설정 열기", mCommands::settings),
                new Entry("키보드 보이기/숨기기", mCommands::toggleKeyboard),
        };

        final android.widget.ArrayAdapter<Entry> adapter =
                new android.widget.ArrayAdapter<>(mActivity,
                        android.R.layout.simple_list_item_1, entries);
        final android.widget.ListView list = new android.widget.ListView(mActivity);
        list.setAdapter(adapter);
        list.setDividerHeight(0);

        final EditText filter = new EditText(mActivity);
        filter.setHint("검색 (선택)");
        filter.setSingleLine(true);
        // Focusable by touch, not by default: the list is the primary surface.
        filter.setFocusableInTouchMode(true);
        filter.setFocusable(false);
        filter.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable e) {
                adapter.getFilter().filter(e.toString());
            }
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
        });

        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(SHEET_BG);
        box.addView(filter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final AlertDialog dialog = new AlertDialog.Builder(mActivity)
                .setTitle("명령 팔레트")
                .setView(box)
                .setNegativeButton("닫기", null)
                .create();

        list.setOnItemClickListener((parent, view, position, id) -> {
            Entry e = adapter.getItem(position);
            dialog.dismiss();
            if (e != null) e.action.run();
        });
        dialog.show();
        // The window is what gives the list its height; without this it collapses to a stub in
        // the middle of the screen and the rows at the bottom are unreachable.
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(plate(SHEET_BG, 8));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (mActivity.getResources().getDisplayMetrics().heightPixels * 0.72f));
        }
    }

    // ------------------------------------------------------------------ icon strip

    private static final int ICON = 0xffc8ccd4;
    private static final int ICON_DIM = 0xff8a8f99;
    private static final int SHEET_BG = 0xff1f2126;

    /**
     * A chrome glyph drawn as line art.
     *
     * <p>Windows Terminal's title bar is a row of thin strokes at one weight, so the eye reads
     * position rather than decoration. The previous strip was text ("＋", "▾", "⚙"), which on
     * this device rendered as a mix of full-width CJK plus, a black triangle and a coloured
     * emoji gear - three different weights and one of them in colour. These are paths, so they
     * are the same weight at every density and never fall back to an emoji font.
     *
     * @param path one of {"plus", "chevron", "gear", "terminal", "keyboard", "split", "close"}
     */
    static View icon(Context context, String path, int sizeDp, boolean dim,
                     View.OnClickListener onClick) {
        android.widget.ImageView v = new android.widget.ImageView(context);
        v.setImageDrawable(new Glyph(path, dim ? ICON_DIM : ICON));
        v.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        int s = dp(context, sizeDp);
        v.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        v.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));
        v.setClickable(onClick != null);
        if (onClick != null) v.setOnClickListener(onClick);
        return v;
    }

    /** The path set. One stroke width, one cap style, 24x24 viewport, like WT's icon font. */
    private static final class Glyph extends android.graphics.drawable.Drawable {
        private final android.graphics.Path p = new android.graphics.Path();
        private final android.graphics.Paint paint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);

        Glyph(String name, int color) {
            paint.setColor(color);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(1.9f);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            build(name);
        }

        private void build(String name) {
            switch (name) {
                case "plus":
                    p.moveTo(12, 5); p.lineTo(12, 19);
                    p.moveTo(5, 12); p.lineTo(19, 12);
                    break;
                case "chevron":
                    p.moveTo(8, 10); p.lineTo(12, 14); p.lineTo(16, 10);
                    break;
                case "close":
                    p.moveTo(7, 7); p.lineTo(17, 17);
                    p.moveTo(17, 7); p.lineTo(7, 17);
                    break;
                case "gear":
                    // A real gear outline: eight trapezoidal teeth around a ring, as one closed
                    // path. The previous version was a small circle with eight spokes, which
                    // at phone size read as a sun - and "gear" is the one glyph whose whole job
                    // is to be recognised without a label.
                    buildGear(8, 9.2f, 6.5f, 12f, 12f);
                    p.addCircle(12, 12, 2.6f, android.graphics.Path.Direction.CW);
                    break;
                case "terminal":
                    // ">_" - the prompt, the one glyph every terminal shares.
                    p.moveTo(5, 7); p.lineTo(10, 12); p.lineTo(5, 17);
                    p.moveTo(12, 17); p.lineTo(19, 17);
                    break;
                case "keyboard":
                    p.addRoundRect(new android.graphics.RectF(3, 7, 21, 17), 2.2f, 2.2f,
                            android.graphics.Path.Direction.CW);
                    p.moveTo(7, 11); p.lineTo(7, 11.01f);
                    p.moveTo(11, 11); p.lineTo(11, 11.01f);
                    p.moveTo(15, 11); p.lineTo(15, 11.01f);
                    p.moveTo(17, 11); p.lineTo(17, 11.01f);
                    p.moveTo(8, 14); p.lineTo(16, 14);
                    break;
                case "split":
                    p.addRoundRect(new android.graphics.RectF(3, 5, 21, 19), 2f, 2f,
                            android.graphics.Path.Direction.CW);
                    p.moveTo(12, 5); p.lineTo(12, 19);
                    break;
                default:
                    p.addCircle(12, 12, 7, android.graphics.Path.Direction.CW);
                    break;
            }
        }

        /**
         * A cog outline as one closed path: a ring of trapezoidal teeth.
         *
         * <p>Drawn as an outline rather than a circle plus spokes because the spokes were what
         * made it look like a sun. Each tooth is a quad whose inner edge is narrower than its
         * outer edge, which is what gives a cog its shape; a rectangle on a ring reads as a
         * sprocket, and lines out from a circle read as rays.
         */
        private void buildGear(int teeth, float outerR, float innerR, float cx, float cy) {
            final float halfOuter = 0.30f;   // angular half-width at the tip, radians
            final float halfInner = 0.18f;   // and at the root: the taper is the tooth
            for (int i = 0; i < teeth; i++) {
                float mid = (float) (2 * Math.PI * i / teeth - Math.PI / 2);
                float a0 = mid - halfInner, a1 = mid - halfOuter;
                float a2 = mid + halfOuter, a3 = mid + halfInner;
                float x0 = cx + (float) Math.cos(a0) * innerR, y0 = cy + (float) Math.sin(a0) * innerR;
                float x1 = cx + (float) Math.cos(a1) * outerR, y1 = cy + (float) Math.sin(a1) * outerR;
                float x2 = cx + (float) Math.cos(a2) * outerR, y2 = cy + (float) Math.sin(a2) * outerR;
                float x3 = cx + (float) Math.cos(a3) * innerR, y3 = cy + (float) Math.sin(a3) * innerR;
                if (i == 0) p.moveTo(x0, y0);
                else p.lineTo(x0, y0);
                p.lineTo(x1, y1);
                p.lineTo(x2, y2);
                p.lineTo(x3, y3);
                // Connect to the next tooth's root along the inner circle, so the outline is
                // continuous instead of eight floating shapes.
                float next = (float) (2 * Math.PI * (i + 1) / teeth - Math.PI / 2) - halfInner;
                p.lineTo(cx + (float) Math.cos(next) * innerR,
                        cy + (float) Math.sin(next) * innerR);
            }
            p.close();
        }

        @Override public void draw(android.graphics.Canvas c) {
            // Scale the 24-unit viewport onto whatever the ImageView gives us.
            int w = getBounds().width(), h = getBounds().height();
            float s = Math.min(w, h) / 24f;
            c.save();
            c.translate(getBounds().left + (w - 24 * s) / 2f,
                    getBounds().top + (h - 24 * s) / 2f);
            c.scale(s, s);
            c.drawPath(p, paint);
            c.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) {
            paint.setColorFilter(cf);
        }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    // ------------------------------------------------------------------ shared looks

    /** The flat rounded plate WT uses for tabs and dialogs. */
    static android.graphics.drawable.Drawable plate(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp);
        return d;
    }

    /** A labelled row of the settings sheet: title line, optional hint, optional control. */
    static View row(Context context, String title, String hint) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(context, 10), 0, dp(context, 6));

        TextView t = new TextView(context);
        t.setText(title);
        t.setTextColor(0xffe8eaee);
        t.setTextSize(14);
        UiFont.apply(t, false);
        col.addView(t);

        if (hint != null) {
            TextView h = new TextView(context);
            h.setText(hint);
            h.setTextColor(0xff8a8f99);
            h.setTextSize(11);
            h.setGravity(Gravity.START);
            col.addView(h);
        }
        return col;
    }

    static int dp(Context context, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, context.getResources().getDisplayMetrics()));
    }
}
