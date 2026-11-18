package ai.openclaw.fterm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;

/**
 * Windows Terminal's retro effect, applied to the finished frame.
 *
 * <p>Two back ends, chosen once:
 *
 * <ul>
 *   <li>API 33+: a {@link RuntimeShader} running {@link RetroShader#SOURCE}. This is the real
 *       port - a glow and a scanline multiply computed from the rendered pixels, exactly the
 *       two operations WT's {@code Retro.hlsl} performs. The shader reads the frame as a
 *       texture, so the glow spills from text onto the background the way a phosphor does.
 *   <li>API 26-32: a cached scanline bitmap only. The glow is the expensive half and cannot
 *       be done on a software Canvas without a second full-size buffer, so it is dropped
 *       rather than faked. The lines are the dark-row multiply WT uses, not the pale added
 *       line an earlier version of this class drew: a bright line over a dark background is
 *       not what a scanline is, and it was measurable (11 vs 29 where WT's is 31 vs 16).
 * </ul>
 *
 * <p>The shader is built once per size and reused; only the resolution uniform changes, and
 * {@code RenderEffect} is cached on the view until the size does.
 */
public final class RetroOverlay {

    private Bitmap mScanlines;
    private float mScanlineStrength;

    /**
     * Build (or reuse) whatever this device can do, and hand the view its effect.
     *
     * @param overImage true when a background picture sits behind the text. The strength
     *                  uniforms drop in that case: see RetroShader's ON_IMAGE constants for
     *                  why a photograph changes what the same multiply looks like.
     */
    public RenderEffect effectFor(int width, int height, float scale, float sigma,
                                 boolean overImage) {
        if (width <= 0 || height <= 0) return null;
        if (android.os.Build.VERSION.SDK_INT < 33) return null;   // bitmap path, see draw()
        try {
            RuntimeShader shader = new RuntimeShader(RetroShader.SOURCE);
            shader.setFloatUniform("resolution", (float) width, (float) height);
            shader.setFloatUniform("scale", Math.max(1f, scale));
            shader.setFloatUniform("sigma", Math.max(0.5f, sigma));
            shader.setFloatUniform("scanlineFactor", overImage
                    ? RetroShader.SCANLINE_FACTOR_ON_IMAGE : RetroShader.SCANLINE_FACTOR);
            shader.setFloatUniform("glowStrength", overImage
                    ? RetroShader.GLOW_STRENGTH_ON_IMAGE : RetroShader.GLOW_STRENGTH);
            return RenderEffect.createRuntimeShaderEffect(shader, "content");
        } catch (Throwable t) {
            // A shader that fails to compile must not take the terminal with it; the bitmap
            // path still gives the scanlines.
            return null;
        }
    }

    /** The API 26-32 fallback: draw the cached dark-row scanlines over the frame. */
    public void draw(Canvas canvas, int width, int height, float scale, boolean overImage) {
        if (width <= 0 || height <= 0) return;
        if (android.os.Build.VERSION.SDK_INT >= 33) return;   // the RenderEffect already did it
        float strength = overImage
                ? RetroShader.SCANLINE_FACTOR_ON_IMAGE : RetroShader.SCANLINE_FACTOR;
        if (mScanlines == null || mScanlines.getWidth() != width
                || mScanlines.getHeight() != Math.max(1, (int) scale)
                || mScanlineStrength != strength) {
            if (mScanlines != null) mScanlines.recycle();
            mScanlines = buildScanlineTile(width, Math.max(1, (int) scale), strength);
            mScanlineStrength = strength;
        }
        if (mScanlines == null) return;
        canvas.drawBitmap(mScanlines, null,
                new android.graphics.RectF(0, 0, width, height), null);
    }

    /** Drop the cache; the next draw rebuilds it. */
    public void invalidate() {
        if (mScanlines != null) mScanlines.recycle();
        mScanlines = null;
        mScanlineStrength = 0f;
    }

    /**
     * One scanline period, tiled by the blitter.
     *
     * <p>Black at {@code SCANLINE_FACTOR} alpha over the frame is the multiply WT performs,
     * done in the one way a software Canvas can: a translucent black row leaves the lit row
     * untouched and halves the dim one.
     */
    private static Bitmap buildScanlineTile(int width, int period, float strength) {
        try {
            Bitmap tile = Bitmap.createBitmap(width, period, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(tile);
            c.drawColor(0x00000000);
            Paint p = new Paint();
            p.setColor(0xff000000);
            p.setAlpha(Math.round(255 * strength));
            c.drawRect(0, period - 1, width, period, p);
            return tile;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }
}
