package io.github.crashedsystem.fterm;

/**
 * The decisions about a background picture that have nothing to do with Android: how it is
 * scaled into the terminal's rectangle, how the opacity and dim sliders clamp, and which
 * file names count as an image.
 *
 * <p>Pure on purpose. The drawable, the file store and the settings rows all need these
 * answers, and a JVM suite can check them without an emulator - the geometry is where an
 * off-by-one shows up as a picture shifted half a pixel off centre.
 */
public final class BackgroundSpec {

    /** Fit the whole picture inside the terminal, centred - no crop. */
    public static final String FIT_CONTAIN = "contain";

    /** Fill the terminal, cropping the overflow - no letterbox. */
    public static final String FIT_COVER = "cover";

    public static final String[] FIT_NAMES = {FIT_CONTAIN, FIT_COVER};

    /**
     * Where the picture sits when it does not fill the terminal.
     *
     * <p>Windows Terminal spells these {@code topLeft} … {@code bottomRight}, and a theme that
     * ships {@code "backgroundImageAlignment": "bottomRight"} should look the same here as it
     * does there - otherwise "import the theme" means "then move the picture by hand".
     */
    public static final String[] ALIGN_NAMES = {
            "topLeft", "top", "topRight",
            "left", "center", "right",
            "bottomLeft", "bottom", "bottomRight",
    };

    public static final String ALIGN_CENTER = "center";

    /** The alignment used when none was ever chosen, and for any name we do not know. */
    public static final String DEFAULT_ALIGN = ALIGN_CENTER;

    /** Percent. 100 is the picture as it was saved. */
    public static final int DEFAULT_OPACITY = 100;

    /**
     * Percent of black laid over the picture.
     *
     * <p>Capped below 100 because the last ten percent leaves the picture invisible while the
     * setting still claims there is one; "none" is the way to say that.
     */
    public static final int MAX_DIM = 90;

    /** Suffixes the loader will decode; anything else in the folder is left alone. */
    public static final String[] IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp", ".bmp"};

    private BackgroundSpec() {}

    /** An unknown or absent fit is {@code contain}, the safe one: nothing is cropped away. */
    public static String fit(String value) {
        return FIT_COVER.equalsIgnoreCase(value == null ? "" : value.trim())
                ? FIT_COVER : FIT_CONTAIN;
    }

    public static String fitLabel(String value) {
        return FIT_COVER.equals(fit(value)) ? "채우기 (cover)" : "맞춤 (contain)";
    }

    /**
     * A known alignment name, matched the way Windows Terminal writes them.
     *
     * <p>Case is ignored and the nine names are the whole vocabulary; anything else falls back
     * to centre rather than throwing, because the value comes from a stored preference that an
     * older build may have written.
     */
    public static String align(String value) {
        if (value == null) return DEFAULT_ALIGN;
        String v = value.trim();
        for (String name : ALIGN_NAMES) {
            if (name.equalsIgnoreCase(v)) return name;
        }
        return DEFAULT_ALIGN;
    }

    /** Korean labels for the nine cells of the alignment picker. */
    public static String alignLabel(String value) {
        String a = align(value);
        if ("topLeft".equals(a)) return "왼쪽 위";
        if ("top".equals(a)) return "위";
        if ("topRight".equals(a)) return "오른쪽 위";
        if ("left".equals(a)) return "왼쪽";
        if ("center".equals(a)) return "가운데";
        if ("right".equals(a)) return "오른쪽";
        if ("bottomLeft".equals(a)) return "왼쪽 아래";
        if ("bottom".equals(a)) return "아래";
        return "오른쪽 아래";
    }

    public static int opacity(int percent) {
        return percent < 0 ? 0 : Math.min(percent, 100);
    }

    public static int dim(int percent) {
        return percent < 0 ? 0 : Math.min(percent, MAX_DIM);
    }

    public static boolean isImageName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String suffix : IMAGE_SUFFIXES) {
            if (lower.endsWith(suffix)) return true;
        }
        return false;
    }

    /**
     * The destination rectangle for a picture of {@code iw}x{@code ih} inside one of
     * {@code bw}x{@code bh}, as {@code {left, top, width, height}} in the bounds' own
     * coordinates.
     *
     * <p>With {@code cover} the rectangle is larger than the bounds on one axis, which is what
     * the drawing code clips; the arithmetic stays here so the test can see both the crop and
     * the placement without a Canvas. The short axis follows {@code alignment}: centred when it
     * is null or unknown, which is what this did before alignment existed.
     */
    public static int[] place(int bw, int bh, int iw, int ih, String fit) {
        return place(bw, bh, iw, ih, fit, DEFAULT_ALIGN);
    }

    public static int[] place(int bw, int bh, int iw, int ih, String fit, String alignment) {
        if (bw <= 0 || bh <= 0 || iw <= 0 || ih <= 0) return new int[]{0, 0, 0, 0};
        float sx = bw / (float) iw;
        float sy = bh / (float) ih;
        float scale = FIT_COVER.equals(fit(fit)) ? Math.max(sx, sy) : Math.min(sx, sy);
        int w = Math.max(1, Math.round(iw * scale));
        int h = Math.max(1, Math.round(ih * scale));

        String a = align(alignment);
        boolean left = a.endsWith("Left") || "left".equals(a);
        boolean right = a.endsWith("Right") || "right".equals(a);
        boolean top = a.startsWith("top");
        boolean bottom = a.startsWith("bottom");

        // cover overflows on one axis: -50 centres a 200-wide picture in a 100-wide box. The
        // alignments keep that same overflow, just moved to one edge, which is what makes
        // "bottomRight" put the visible corner of a cropped picture in the corner.
        int x;
        if (left) x = 0;
        else if (right) x = bw - w;
        else x = (bw - w) / 2;

        int y;
        if (top) y = 0;
        else if (bottom) y = bh - h;
        else y = (bh - h) / 2;

        return new int[]{x, y, w, h};
    }
}
