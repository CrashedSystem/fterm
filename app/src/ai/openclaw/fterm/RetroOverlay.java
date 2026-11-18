package ai.openclaw.fterm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * The "retro terminal" overlay: scanlines and a vignette, drawn over the finished frame.
 *
 * <p>Windows Terminal does this with a pixel shader, which needs a GPU surface and a full
 * screen redraw. This renderer is a software Canvas and redraws every time the cursor blinks,
 * so a per-frame effect has to cost almost nothing - hence an overlay bitmap built once per
 * size and blitted, rather than anything evaluated per pixel.
 *
 * <p>Two of WT's three effects survive that trade. Scanlines and the vignette are functions
 * of position only, so they cache perfectly. The third, chromatic aberration, needs the
 * rendered text itself to split into channels; there is no cheap way to do that on a Canvas,
 * so it is left out rather than faked with a coloured overlay that would only look dirty.
 */
public final class RetroOverlay {

    /** Scanline pitch in px. 3 is the smallest period that survives a 2x screen. */
    private static final int LINE_PITCH = 3;
    /**
     * How bright a scanline is, in alpha.
     *
     * <p>WT dims alternate rows, which is invisible here: the dark schemes sit at RGB 9, so
     * a black line over them measures 9 vs 8 - measured on device, and the effect did
     * nothing. A pale line adds light instead, which registers on a dark background and is
     * faint enough over text to read as glow rather than haze.
     */
    private static final int LINE_ALPHA = 0x14;

    private Bitmap mBitmap;

    /** Build (or reuse) the overlay for a view of this size, then draw it. */
    public void draw(Canvas canvas, int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (mBitmap == null || mBitmap.getWidth() != width || mBitmap.getHeight() != height) {
            mBitmap = build(width, height);
        }
        if (mBitmap != null) canvas.drawBitmap(mBitmap, 0, 0, null);
    }

    /** Drop the cache; the next draw rebuilds it. */
    public void invalidate() {
        if (mBitmap != null) mBitmap.recycle();
        mBitmap = null;
    }

    private static Bitmap build(int width, int height) {
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            // A 1440x3120 ARGB overlay is 18 MB. If that is not available the effect is
            // skipped rather than crashing the terminal it decorates.
            return null;
        }
        Canvas c = new Canvas(bitmap);

        Paint line = new Paint();
        line.setColor(Color.WHITE);
        line.setAlpha(LINE_ALPHA);
        for (int y = 0; y < height; y += LINE_PITCH) c.drawRect(0, y, width, y + 1, line);

        // Vignette: transparent until 55% of the radius, then black at the corners.
        Paint vignette = new Paint();
        vignette.setShader(new RadialGradient(width / 2f, height / 2f,
                Math.max(width, height) * 0.72f,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT, 0xB3000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, width, height, vignette);
        return bitmap;
    }
}
