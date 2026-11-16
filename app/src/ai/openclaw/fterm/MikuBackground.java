package ai.openclaw.fterm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * mterm's Miku illustration behind the terminal.
 *
 * mterm composites the picture with {@code background-blend-mode: multiply} against the
 * scheme's background, and its `app.css` explains why: an alpha composite puts
 * {@code #dcdfe4} text on whatever the picture happens to be at that pixel - the figure's
 * pale hair is {@code #e8e2e0}, about 1.9:1 - whereas multiplying lets the palette decide
 * contrast and only darkens the picture toward it.
 *
 * **The multiply is baked into the asset, not done at draw time.** Android's
 * {@code BlendMode.MULTIPLY} on a Drawable needs a hardware layer the view does not always
 * have, and when it silently falls back the picture draws at full strength - a bright figure
 * behind text. So `miku-dusk.png` is the source picture already multiplied against
 * {@code #282c34} at full opacity, and this class only draws it.
 *
 * Full opacity is what mterm does. `THEMES.miku.backgroundOpacity` is 0.55 there, but nothing
 * reads it: the only consumer would be a CSS alpha, and the rule in `app.css` is
 * {@code background-image: var(--term-bg-image), linear-gradient(var(--term-bg), var(--term-bg))}
 * with {@code background-blend-mode: multiply}. The picture keeps its own alpha channel and is
 * multiplied at 100%. An earlier revision here applied 0.55 on top, which is why the theme did
 * not look like mterm's.
 *
 * Measured on the baked asset: maximum luminance 43.7, exactly the background's own luminance
 * (43.7). No pixel can be brighter than the scheme's background, so the foreground keeps its
 * palette contrast of 11.4:1 wherever it is drawn.
 */
final class MikuBackground extends Drawable {

    private final Bitmap mBitmap;
    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect mSrc = new Rect();
    private final Rect mDst = new Rect();

    private MikuBackground(Bitmap bitmap) {
        mBitmap = bitmap;
    }

    /** @return null when the asset is missing, so a theme can never break startup. */
    static MikuBackground load(Context context) {
        try {
            Bitmap b = BitmapFactory.decodeStream(context.getAssets().open("img/miku-dusk.png"));
            return b == null ? null : new MikuBackground(b);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (mBitmap == null || bounds.isEmpty()) return;

        // `background-size: contain` - the whole picture fits, centred. Cropping would cut
        // the figure the theme is named after, which is the one thing mterm insists on.
        float scale = Math.min(
                bounds.width() / (float) mBitmap.getWidth(),
                bounds.height() / (float) mBitmap.getHeight());
        int w = Math.round(mBitmap.getWidth() * scale);
        int h = Math.round(mBitmap.getHeight() * scale);
        int left = bounds.left + (bounds.width() - w) / 2;
        int top = bounds.top + (bounds.height() - h) / 2;

        mSrc.set(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        mDst.set(left, top, left + w, top + h);
        canvas.drawBitmap(mBitmap, mSrc, mDst, mPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        // The canvas below is already painted with the scheme's background, so the picture
        // only darkens it.
        return PixelFormat.TRANSLUCENT;
    }
}
