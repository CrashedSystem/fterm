package io.github.crashedsystem.terminal;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * A picture behind the terminal text.
 *
 * <p>The general replacement for the old hard-coded theme image: any picture the user picked
 * or dropped into the app's {@code bg} folder, drawn under the text with the fit, opacity and
 * dimming the appearance page asks for.
 *
 * <p>Why dim rather than multiply at draw time: {@code BlendMode.MULTIPLY} on a Drawable needs
 * a hardware layer the terminal view does not always have, and when it silently falls back the
 * picture draws at full strength - a bright photograph behind {@code #dcdfe4} text, which is
 * unreadable. A black scrim is one extra drawRect that behaves identically on every path, and
 * it is the control the user actually wants (io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0358)). Picture contrast is then
 * the scheme's business again, exactly as the previous baked-in asset did by being pre-multiplied.
 */
final class TerminalBackground extends Drawable {

    private final Bitmap mBitmap;
    private final String mFit;
    private final String mAlign;
    private final int mOpacity;
    private final int mDim;
    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint mScrim = new Paint();
    private final Rect mSrc = new Rect();
    private final Rect mDst = new Rect();

    TerminalBackground(Bitmap bitmap, String fit, String align, int opacity, int dim) {
        mBitmap = bitmap;
        mFit = BackgroundSpec.fit(fit);
        mAlign = BackgroundSpec.align(align);
        mOpacity = BackgroundSpec.opacity(opacity);
        mDim = BackgroundSpec.dim(dim);
        mPaint.setAlpha(mOpacity * 255 / 100);
        mScrim.setColor(0xff000000);
        mScrim.setAlpha(mDim * 255 / 100);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (mBitmap == null || bounds.isEmpty()) return;
        int[] place = BackgroundSpec.place(bounds.width(), bounds.height(),
                mBitmap.getWidth(), mBitmap.getHeight(), mFit, mAlign);
        if (place[2] <= 0 || place[3] <= 0) return;

        mSrc.set(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        mDst.set(bounds.left + place[0], bounds.top + place[1],
                bounds.left + place[0] + place[2], bounds.top + place[1] + place[3]);

        // cover overflows the bounds on one axis; contain does not, but clipping either is
        // free and keeps a rounding error from painting over the tab strip.
        canvas.save();
        canvas.clipRect(bounds);
        canvas.drawBitmap(mBitmap, mSrc, mDst, mPaint);
        if (mDim > 0) canvas.drawRect(bounds, mScrim);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(Math.max(0, Math.min(255, alpha)) * mOpacity / 100);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        // The view has already painted the scheme's background underneath.
        return PixelFormat.TRANSLUCENT;
    }
}
