package ai.openclaw.fterm;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.graphics.Paint;

/**
 * The terminal typeface, built the way mterm builds it.
 *
 * mterm's stack is three faces with different jobs: Iosevka answers Latin (every glyph
 * 500/1000 em), a Nerd Font symbols face answers the private-use icon range, and Pretendard
 * answers Hangul. That split is not taste - Iosevka has no CJK coverage at all, and Pretendard's
 * syllable advance (1770/2048 em) is *not* two Iosevka cells (1000/1000 em), so a Hangul
 * face has to be scaled before it can sit in a two-cell box.
 *
 * Android has no `size-adjust` and no per-face unicode-range on a `Typeface`, so two things
 * replace them:
 *
 *   1. A single `Typeface` is built with {@link Typeface#createFromAssets} for each face and
 *      selected per code point. {@link #typefaceFor(int)} returns Pretendard for Hangul and
 *      Iosevka for everything else, which is exactly the stack's decision.
 *   2. Hangul is scaled by {@link #HANGUL_SCALE}, which is mterm's 115.85% size-adjust
 *      expressed the way a {@link Paint} can express it - as a text size multiplier - so a
 *      syllable's advance lands on two Iosevka cells.
 *
 * The terminal view keeps one `Paint`, so the per-code-point switch has to happen in the
 * renderer. {@link #typefaceFor} is the single place that decision lives.
 */
final class Fonts {

    /** mterm: iosevka 500/1000 em, recommendation for the Hangul face's size-adjust. */
    static final float HANGUL_SIZE_ADJUST_PCT = 115.85f;

    /**
     * The Latin faces a user may pick, in menu order.
     *
     * <p>All three are monospaced with a whole-pixel cell, which is the only property the
     * renderer actually requires: a proportional face would break the grid, and a face whose
     * advance is fractional would make columns drift apart over eighty cells. Two of them
     * ship with the system, so the list costs nothing to the APK.
     */
    static final String[] LATIN_NAMES = {"Iosevka", "Droid Sans Mono", "Cutive Mono"};
    static final String[] LATIN_IDS = {"iosevka", "droid", "cutive"};
    /** The bundled fallback face, named once so the settings preview loads the same file. */
    static final String ASSET_PATH = "fonts/Iosevka.ttf";

    static final String[] LATIN_PATHS = {
            null,                                   // asset, handled separately
            "/system/fonts/DroidSansMono.ttf",
            "/system/fonts/CutiveMono.ttf"
    };

    /** The index for a stored id, or 0 (Iosevka) when the id is unknown. */
    static int latinIndexOf(String id) {
        for (int i = 0; i < LATIN_IDS.length; i++) {
            if (LATIN_IDS[i].equals(id)) return i;
        }
        return 0;
    }

    private final Typeface mIosevka;
    private final Typeface mHangul;
    private final Typeface mSymbols;
    private final float mHangulAdjust;

    private Fonts(Typeface iosevka, Typeface hangul, Typeface symbols, float hangulAdjust) {
        mIosevka = iosevka;
        mHangul = hangul;
        mSymbols = symbols;
        mHangulAdjust = hangulAdjust;
    }

    /**
     * Load the three faces and work out the Hangul adjustment for this cell width.
     *
     * @param latinCellPx the width of one Iosevka cell at the requested text size, measured
     *                    by the caller from the renderer's own Paint so the two cannot drift.
     */
    static Fonts load(Context context, float latinCellPx, float textSizePx) {
        return load(context, latinCellPx, textSizePx, 0);
    }

    /**
     * As {@link #load(Context, float, float)}, with the Latin face chosen by index into
     * {@link #LATIN_IDS}. An index whose font is missing falls back to the asset face, so a
     * system image without Cutive Mono still opens a terminal.
     */
    static Fonts load(Context context, float latinCellPx, float textSizePx, int latinIndex) {
        Typeface mono = null;
        Typeface hangul = null;
        Typeface symbols = null;
        if (latinIndex > 0 && latinIndex < LATIN_PATHS.length && LATIN_PATHS[latinIndex] != null) {
            try {
                mono = Typeface.createFromFile(LATIN_PATHS[latinIndex]);
            } catch (Throwable ignored) {
            }
        }
        if (mono == null) {
            try {
                mono = Typeface.createFromAsset(context.getAssets(), ASSET_PATH);
            } catch (Throwable ignored) {
            }
        }
        if (mono == null) mono = Typeface.MONOSPACE;
        try {
            hangul = Typeface.createFromAsset(context.getAssets(), "fonts/PretendardHangul.ttf");
        } catch (Throwable ignored) {
        }
        try {
            symbols = Typeface.createFromAsset(context.getAssets(), "fonts/NerdSymbols.ttf");
        } catch (Throwable ignored) {
        }

        // Measure the cell from the chosen face rather than trusting the caller's number:
        // the caller's is the previous face's width, and switching faces is exactly when it
        // is wrong. A face's own "M" is the cell the renderer will use anyway.
        float cell = latinCellPx;
        Paint latinPaint = new Paint();
        latinPaint.setTypeface(mono);
        latinPaint.setTextSize(textSizePx);
        float measured = latinPaint.measureText("M");
        if (measured > 0f) cell = measured;

        // Measure the Hangul advance at the same text size and derive the multiplier that
        // makes one syllable exactly two Latin cells. mterm's 115.85% is what that
        // arithmetic produced for a 6 px Iosevka cell; deriving it keeps a different face
        // and size honest instead of 1.5% off.
        float adjust = HANGUL_SIZE_ADJUST_PCT / 100f;
        if (hangul != null && cell > 0f) {
            Paint p = new Paint();
            p.setTypeface(hangul);
            p.setTextSize(textSizePx);
            float syllable = p.measureText("\uD55C");
            if (syllable > 0f) {
                float target = 2f * cell;
                adjust = target / syllable;
                // A face that is already two cells wide must not be scaled at all; a value
                // far from mterm's means the measurement failed and the constant is safer.
                if (adjust < 0.5f || adjust > 2.5f) adjust = HANGUL_SIZE_ADJUST_PCT / 100f;
            }
        }
        return new Fonts(mono, hangul != null ? hangul : mono,
                symbols != null ? symbols : mono, adjust);
    }

    Typeface latin() {
        return mIosevka;
    }

    /** The multiplier a Hangul code point's text size needs, to land on two cells. */
    float hangulAdjust() {
        return mHangulAdjust;
    }

    /** True when the code point is answered by the Korean face. */
    static boolean isHangul(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x11FF)      // conjoining jamo
                || (codePoint >= 0x3130 && codePoint <= 0x318F)  // compatibility jamo
                || (codePoint >= 0xA960 && codePoint <= 0xA97F)  // jamo extended-A
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)  // precomposed syllables
                || (codePoint >= 0xD7B0 && codePoint <= 0xD7FF); // jamo extended-B
    }

    /** Private-use range a Nerd Font uses for icons. */
    static boolean isNerdIcon(int codePoint) {
        return (codePoint >= 0xE0A0 && codePoint <= 0xE0B3)
                || (codePoint >= 0xE0B4 && codePoint <= 0xE0C8)
                || (codePoint >= 0xF000 && codePoint <= 0xF2FF);
    }

    /**
     * The face for one code point: Hangul -> Pretendard, icons -> Nerd Symbols, else Iosevka.
     *
     * This is mterm's stack order expressed as a function, which is what an Android
     * `Typeface` can do instead of a `font-family` list.
     */
    Typeface typefaceFor(int codePoint) {
        if (isHangul(codePoint)) return mHangul;
        if (isNerdIcon(codePoint)) return mSymbols;
        return mIosevka;
    }

    /** The text-size multiplier for one code point (Hangul needs its size-adjust). */
    float scaleFor(int codePoint) {
        return isHangul(codePoint) ? mHangulAdjust : 1f;
    }

    static float dpToPx(Context context, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }
}
