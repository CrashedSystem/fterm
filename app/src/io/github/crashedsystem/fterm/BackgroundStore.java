package io.github.crashedsystem.fterm;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Where the background picture lives.
 *
 * <p>One folder, not one preference pointing at an arbitrary path: the app's own external
 * files directory, {@code /sdcard/Android/data/io.github.crashedsystem.fterm/files/bg/}. That is readable
 * by the app without a storage permission, by {@code adb push} without a debug build, and by
 * a personal script on the phone, which is what replaced the old "build the APK with an asset
 * overlay" workflow. The newest image file in it is the one that is drawn, so dropping a file
 * in is the whole installation.
 *
 * <p>A picked picture is decoded, scaled to at most {@value #MAX_SIDE} pixels on its long
 * side, and re-encoded here. The re-encode is not cosmetic: a 12-megapixel photograph decoded
 * at full size is 48 MB of ARGB, which is how a terminal process dies in the background. The
 * cap keeps it around 12 MB.
 */
final class BackgroundStore {

    /** Any app with this action can hand the app a picture; see {@link BackgroundReceiver}. */
    static final String ACTION_APPLY = "io.github.crashedsystem.fterm.action.APPLY_BACKGROUND";
    static final String ACTION_CLEAR = "io.github.crashedsystem.fterm.action.CLEAR_BACKGROUND";

    /** Sent after the store changed, so a running terminal repaints without being reopened. */
    static final String ACTION_CHANGED = "io.github.crashedsystem.fterm.action.BACKGROUND_CHANGED";

    static final String EXTRA_PATH = "path";
    static final String EXTRA_OPACITY = "opacity";
    static final String EXTRA_FIT = "fit";
    static final String EXTRA_ALIGN = "align";
    static final String EXTRA_DIM = "dim";

    static final int MAX_SIDE = 2048;

    /** Refuse absurd inputs rather than reading them into memory first. */
    private static final long MAX_BYTES = 40L * 1024 * 1024;

    private BackgroundStore() {}

    /** {@code /sdcard/Android/data/io.github.crashedsystem.fterm/files/bg} in practice. */
    static File dir(Context context) {
        File external = context.getExternalFilesDir("bg");
        return external != null ? external : new File(context.getFilesDir(), "bg");
    }

    /** The image to draw: the most recently written one, or null when the folder is empty. */
    static File current(Context context) {
        File[] files = dir(context).listFiles();
        if (files == null) return null;
        File best = null;
        for (File f : files) {
            if (!f.isFile() || !BackgroundSpec.isImageName(f.getName())) continue;
            if (best == null || f.lastModified() > best.lastModified()
                    || (f.lastModified() == best.lastModified()
                            && f.getName().compareTo(best.getName()) > 0)) {
                best = f;
            }
        }
        return best;
    }

    /** What the appearance page shows on the io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0051) row. */
    static String describe(Context context) {
        File f = current(context);
        if (f == null) return null;
        Prefs p = new Prefs(context);
        return f.getName() + " " + p.backgroundFit() + " " + p.backgroundAlign()
                + " " + p.backgroundOpacity() + "%";
    }

    /**
     * The current file's identity, as one string.
     *
     * <p>The activity re-decodes only when this changes: moving the opacity or dim slider
     * rebuilds the drawable around the bitmap it already has, and resuming the app does not
     * decode a photograph again. Dropping a file in while the app is open still takes effect,
     * because its name or timestamp differs.
     */
    static String fileSignature(Context context) {
        File f = current(context);
        return f == null ? "-" : f.getAbsolutePath() + ":" + f.lastModified() + ":" + f.length();
    }

    /** The decoded picture, or null when there is none or it cannot be decoded. */
    static Bitmap loadBitmap(Context context) {
        File f = current(context);
        if (f == null) return null;
        try {
            return decode(read(f));
        } catch (Throwable t) {
            // A corrupt or oversized file must not take the terminal down on resume.
            android.util.Log.w("fterm", io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0052) + f, t);
            return null;
        }
    }

    /** Copies the picked document into the folder. @return false with a reason in logcat. */
    static boolean install(Context context, Uri uri) {
        if (uri == null) return false;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return false;
            byte[] bytes = read(in);
            return install(context, bytes);
        } catch (Throwable t) {
            android.util.Log.w("fterm", io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0052) + uri, t);
            return false;
        }
    }

    /**
     * Takes an image already on disk - this is the path a script uses, and it does not need a
     * storage permission when the file sits in this app's own external files directory.
     */
    static boolean installFile(Context context, File file) {
        if (file == null || !file.isFile()) return false;
        try {
            return install(context, read(file));
        } catch (Throwable t) {
            android.util.Log.w("fterm", io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0052) + file, t);
            return false;
        }
    }

    /** @return false when the bytes are not a picture that could be decoded. */
    private static boolean install(Context context, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return false;
        Bitmap bitmap = decode(bytes);
        if (bitmap == null) {
            android.util.Log.w("fterm", io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0053));
            return false;
        }
        try {
            File dir = dir(context);
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            File tmp = new File(dir, "background.tmp");
            // Keep PNG as PNG even when it has no alpha: a screenshot or line art saved as
            // JPEG grows ringing around every glyph edge, and the picture sits directly behind
            // text. A photograph becomes JPEG, where the artifacts are invisible and the file
            // is ten times smaller.
            boolean alpha = bitmap.hasAlpha() || isPng(bytes);
            File target = new File(dir, alpha ? "background.png" : "background.jpg");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                if (!bitmap.compress(alpha ? Bitmap.CompressFormat.PNG
                        : Bitmap.CompressFormat.JPEG, 92, out)) {
                    return false;
                }
            }
            // Remove the previous picture only once the new one is fully on disk, so a failed
            // write leaves the user with the background they had rather than none.
            File[] old = dir.listFiles();
            if (old != null) {
                for (File f : old) {
                    if (f.isFile() && BackgroundSpec.isImageName(f.getName())
                            && !f.getName().equals(target.getName())) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    }
                }
            }
            if (target.exists() && !target.delete()) return false;
            if (!tmp.renameTo(target)) return false;
            return true;
        } catch (Throwable t) {
            android.util.Log.w("fterm", io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0054), t);
            return false;
        } finally {
            bitmap.recycle();
        }
    }

    static boolean clear(Context context) {
        File[] files = dir(context).listFiles();
        boolean any = false;
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && BackgroundSpec.isImageName(f.getName())) {
                    any |= f.delete();
                }
            }
        }
        return any;
    }

    /** Tell a running activity that the folder or the appearance changed. */
    static void notifyChanged(Context context) {
        Intent i = new Intent(ACTION_CHANGED);
        i.setPackage(context.getPackageName());
        context.sendBroadcast(i);
    }

    // ------------------------------------------------------------------ pixels

    private static boolean isPng(byte[] bytes) {
        return bytes.length > 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G';
    }

    private static byte[] read(File file) {
        if (file.length() > MAX_BYTES) throw new IllegalArgumentException(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0055));
        try (InputStream in = new FileInputStream(file)) {
            return read(in);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        long total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_BYTES) throw new java.io.IOException(io.github.crashedsystem.fterm.AppStrings.s(io.github.crashedsystem.fterm.R.string.i18n_0055));
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static Bitmap decode(byte[] data) {
        if (data == null || data.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        while (bounds.outWidth / sample > MAX_SIDE * 2
                || bounds.outHeight / sample > MAX_SIDE * 2) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (b == null) return null;

        int longSide = Math.max(b.getWidth(), b.getHeight());
        if (longSide > MAX_SIDE) {
            float s = MAX_SIDE / (float) longSide;
            Bitmap scaled = Bitmap.createScaledBitmap(b,
                    Math.max(1, Math.round(b.getWidth() * s)),
                    Math.max(1, Math.round(b.getHeight() * s)), true);
            if (scaled != b) b.recycle();
            b = scaled;
        }
        return b;
    }
}
