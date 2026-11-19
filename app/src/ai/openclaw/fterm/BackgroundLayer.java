package ai.openclaw.fterm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Build;

/**
 * Keeps the terminal's picture in step with the folder and the appearance settings.
 *
 * <p>The activity used to hold a single drawable loaded from an asset. Now the picture is a
 * user setting, so three things can change it: the settings page, a dropped file noticed on
 * resume, and the broadcast a script sends. All three end here, which is why this is a class
 * rather than four lines in the activity - and why {@code MainActivity} still fits under its
 * line cap.
 *
 * <p>The decoded bitmap is kept across restyles. Dragging the opacity slider fires dozens of
 * changes, and re-decoding a 12-megapixel photograph on each one would make the slider stutter;
 * only a different file forces a decode.
 */
final class BackgroundLayer {

    private final MainActivity mActivity;
    private final Prefs mPrefs;
    private TerminalBackground mImage;
    private Bitmap mBitmap;
    /** File identity and drawing parameters, compared separately so a slider move is cheap. */
    private String mFileKey;
    private String mStyleKey = "";
    private final BroadcastReceiver mReceiver;
    private boolean mRegistered;

    BackgroundLayer(MainActivity activity, Prefs prefs) {
        mActivity = activity;
        mPrefs = prefs;
        mReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                reload();
            }
        };
    }

    TerminalBackground image() {
        return mImage;
    }

    /** The file name on the appearance row, or null when there is no picture. */
    String describe() {
        return BackgroundStore.describe(mActivity);
    }

    /** Puts the current picture on the terminal view; called after every scheme change. */
    void attach() {
        if (mActivity.mTermView != null) {
            mActivity.mTermView.setBackgroundImage(mImage);
            mActivity.mTermView.invalidate();
        }
    }

    /**
     * Re-reads whatever changed: the file, or how it is drawn.
     *
     * <p>The new drawable is attached before the old bitmap is released, because the view may
     * still be holding the old one - recycling it first is the classic
     * "trying to use a recycled bitmap" crash.
     */
    void reload() {
        String fileKey = BackgroundStore.fileSignature(mActivity);
        String styleKey = mPrefs.backgroundFit() + "|" + mPrefs.backgroundAlign() + "|"
                + mPrefs.backgroundOpacity() + "|" + mPrefs.backgroundDim();
        if (fileKey.equals(mFileKey) && styleKey.equals(mStyleKey)) return;

        Bitmap old = mBitmap;
        if (!fileKey.equals(mFileKey)) {
            mBitmap = BackgroundStore.loadBitmap(mActivity);
            mFileKey = fileKey;
        }
        mStyleKey = styleKey;
        mImage = mBitmap == null ? null : new TerminalBackground(mBitmap,
                mPrefs.backgroundFit(), mPrefs.backgroundAlign(),
                mPrefs.backgroundOpacity(), mPrefs.backgroundDim());
        attach();
        if (old != null && old != mBitmap) old.recycle();
    }

    void resume() {
        reload();
        if (!mRegistered) {
            IntentFilter filter = new IntentFilter(BackgroundStore.ACTION_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                mActivity.registerReceiver(mReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                mActivity.registerReceiver(mReceiver, filter);
            }
            mRegistered = true;
        }
    }

    void pause() {
        if (mRegistered) {
            try {
                mActivity.unregisterReceiver(mReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already gone; unregistering twice is not an error the user can act on.
            }
            mRegistered = false;
        }
    }
}
