package io.github.crashedsystem.terminal;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

/**
 * The single UI typeface: Pretendard, loaded once.
 *
 * Every surface the user reads - key bar labels, tab titles, settings, dialogs - used to
 * pick its own face (sans-serif, sans-serif-medium, monospace for one icon), which is why
 * the UI looked assembled from parts. The terminal grid keeps Iosevka/Pretendard's
 * code-point split; this is only for chrome.
 */
final class UiFont {

    private static Typeface sPretendard;

    private UiFont() {}

    /** The UI typeface, created on first use. */
    static Typeface get(Context context) {
        if (sPretendard == null) {
            try {
                sPretendard = Typeface.createFromAsset(context.getAssets(), "fonts/PretendardHangul.ttf");
            } catch (Throwable t) {
                sPretendard = Typeface.create("sans-serif", Typeface.NORMAL);
            }
        }
        return sPretendard;
    }

    /** Apply the UI typeface (and an optional style) to a view. */
    static void apply(TextView v, boolean medium) {
        Typeface base = get(v.getContext());
        v.setTypeface(medium ? Typeface.create(base, Typeface.BOLD) : base);
    }
}
