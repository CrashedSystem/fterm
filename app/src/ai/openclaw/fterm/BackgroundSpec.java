package ai.openclaw.fterm;

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
     * <p>Centred in both modes. With {@code cover} the rectangle is larger than the bounds on
     * one axis, which is what the drawing code clips; the arithmetic stays here so the test can
     * see both the crop and the centring without a Canvas.
     */
    public static int[] place(int bw, int bh, int iw, int ih, String fit) {
        if (bw <= 0 || bh <= 0 || iw <= 0 || ih <= 0) return new int[]{0, 0, 0, 0};
        float sx = bw / (float) iw;
        float sy = bh / (float) ih;
        float scale = FIT_COVER.equals(fit(fit)) ? Math.max(sx, sy) : Math.min(sx, sy);
        int w = Math.max(1, Math.round(iw * scale));
        int h = Math.max(1, Math.round(ih * scale));
        // Integer division rounds toward zero, which for an odd difference puts the extra
        // pixel on the right/bottom - still centred to within one pixel.
        return new int[]{(bw - w) / 2, (bh - h) / 2, w, h};
    }
}
