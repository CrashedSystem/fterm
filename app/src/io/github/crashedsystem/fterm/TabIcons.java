package io.github.crashedsystem.fterm;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The small mark at the left of every tab.
 *
 * <p>A tab's icon is the one piece of chrome a user stares at all day, so it is the piece
 * most worth letting them change. The value is a preference string, and it is read two ways:
 * one of {@link #GLYPHS} builds a Fluent path, anything else is drawn as text. That is the
 * whole format - no escaping, no schema - because the only two things it can be are "a glyph
 * this app has" and "something the user typed".
 *
 * <p>Text is kept short deliberately. The slot is 18dp wide; a word would be clipped to a
 * few pixels and read as damage, so a long entry is printed smaller rather than cut.
 */
final class TabIcons {

    /**
     * The glyph ids {@link #build} draws as paths; every other value is text.
     *
     * <p>Broad on purpose. A tab mark is how a glance tells one tab from another - an ssh
     * session, a database console, a log tail - and a set of four forced the user to write
     * their own text for anything outside it. Every id here is a Fluent path already in
     * {@link GlyphPaths}, so adding one costs a string and one table entry in
     * {@code tests/tools/icons.py}.
     *
     * <p>Order is the picker's order: the ones a terminal is most likely to want first.
     */
    static final String[] GLYPHS = {
            "terminal", "prompt", "code", "chevron", "split",
            "server", "database", "cloud", "globe", "folder",
            "document", "keyboard", "bug", "beaker", "wrench",
            "flash", "rocket", "shield", "eye", "scan",
            "star", "heart", "cube", "puzzle", "fire",
            "camera", "gift", "brush", "flashlight", "home",
            "chat", "mail", "phone", "print", "ruler",
            "opencode",
    };

    /** What the settings list prints for each of {@link #GLYPHS}, same order. */
    static final String[] GLYPH_NAMES = {
            "터미널 창", "프롬프트", "코드", "화살표", "분할",
            "서버", "데이터베이스", "클라우드", "지구", "폴더",
            "문서", "키보드", "버그", "비커", "렌치",
            "번개", "로켓", "방패", "눈", "스캔",
            "별", "하트", "큐브", "퍼즐", "불꽃",
            "카메라", "선물", "붓", "손전등", "집",
            "말풍선", "메일", "전화", "프린터", "자",
            "opencode",
    };

    /** The default: the prompt every terminal has printed since the teletype. */
    static final String DEFAULT_ID = ">_";

    /** The slot is this wide; the tab chip's height is what the views fill. */
    private static final float WIDTH_DP = 18f;
    private static final float HEIGHT_DP = 34f;

    private TabIcons() {}

    /** Whether {@code id} is drawn as a path rather than as text. */
    static boolean isGlyph(String id) {
        if (id == null) return false;
        for (String g : GLYPHS) if (g.equals(id)) return true;
        return false;
    }

    /**
     * The view for one icon, sized for the tab slot.
     *
     * @param color the tint, which the caller varies with the active state
     */
    static View build(Context context, String id, int color) {
        String value = (id == null || id.isEmpty()) ? DEFAULT_ID : id;
        if (isGlyph(value)) {
            ImageView v = new ImageView(context);
            v.setImageDrawable(new Chrome.Glyph(value, color));
            // A 24-unit Fluent path inside an 18dp slot needs the same proportional inset
            // the toolbar uses, or the glyph touches the slot edges and reads as bold.
            int inset = Math.round(WIDTH_DP * 0.10f);
            v.setPadding(dp(context, inset), 0, dp(context, inset), 0);
            v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            v.setLayoutParams(new LinearLayout.LayoutParams(
                    dp(context, WIDTH_DP), dp(context, HEIGHT_DP)));
            return v;
        }
        TextView t = new TextView(context);
        t.setText(value);
        // One or two characters read as a mark; three or more have to shrink to fit the slot.
        t.setTextSize(value.length() <= 2 ? 9 : 7);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER);
        UiFont.apply(t, false);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                dp(context, WIDTH_DP), dp(context, HEIGHT_DP)));
        return t;
    }

    private static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }
}
