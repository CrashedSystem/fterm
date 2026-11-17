package ai.openclaw.fterm;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Clipboard image handling: a screenshot on the clipboard has no text, so the paste path
 * saves it as a PNG and pastes the path instead - opencode attaches file:// URIs in the
 * prompt, which turns "I pasted a screenshot" into an image the model can see.
 */
final class ClipboardImage {

    private ClipboardImage() {}

    /** True when the primary clip is an image (any image/ MIME). */
    static boolean isImage(Context context, ClipData clip) {
        if (clip.getDescription() != null && clip.getDescription().hasMimeType("image/")) return true;
        if (clip.getItemAt(0) == null || clip.getItemAt(0).getUri() == null) return false;
        String mime = context.getContentResolver().getType(clip.getItemAt(0).getUri());
        return mime != null && mime.startsWith("image/");
    }

    static android.net.Uri uriOf(ClipData clip) {
        return clip.getItemAt(0).getUri();
    }

    /**
     * Decode the image URI into a PNG under the app's cache. Returns the absolute path,
     * or null when the URI is unreadable.
     */
    static String save(Context context, Uri uri) {
        try {
            Bitmap bmp = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri));
            if (bmp == null) return null;
            File dir = new File(context.getCacheDir(), "clip");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "clip-" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fo = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fo);
            }
            return out.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }
}
