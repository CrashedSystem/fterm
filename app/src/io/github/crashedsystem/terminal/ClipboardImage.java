package io.github.crashedsystem.terminal;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Clipboard image handling: a screenshot on the clipboard has no text, so the paste path
 * saves it as a PNG and pastes the path instead - opencode attaches file:// URIs in the
 * prompt, which turns "I pasted a screenshot" into an image the model can see.
 */
final class ClipboardImage {

    private static final String TAG = "fterm";

    private ClipboardImage() {}

    /** What the activity does when an image is pasted. Kept out of the watcher so it stays testable. */
    interface Paste {
        /** False while there is no live session to insert into. Focus is the caller's job. */
        boolean ready();

        /** Insert the saved path into the active session's prompt. */
        void insert(String path);
    }

    /**
     * Device check for the save path alone: encode a red 2x2 PNG the same way a pasted
     * screenshot is encoded and log where it landed. Started with
     * {@code am start -n io.github.crashedsystem.terminal/.MainActivity --ez clipSelfTest true}.
     */
    static void selfTestIfRequested(Context context) {
        if (!(context instanceof android.app.Activity)) return;
        if (!((android.app.Activity) context).getIntent().getBooleanExtra("clipSelfTest", false)) return;
        selfTest(context);
    }

    private static void selfTest(Context context) {
        android.content.Intent intent = ((android.app.Activity) context).getIntent();
        // A real gallery image, if one was named: the encoder is the same, but a PNG
        // straight from MediaStore also proves the content resolver path.
        String given = intent.getStringExtra("clipSelfTestUri");
        if (given != null) {
            String saved = save(context, Uri.parse(given));
            Log.i(TAG, "selfTest: uri=" + given + " -> " + saved
                    + (saved == null ? "" : " (" + new File(saved).length() + " bytes)"));
            return;
        }
        Bitmap bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(0xFFFF0000);
        String path = writeFile(bmp);
        Log.i(TAG, "selfTest: file=" + path);
        if (path == null) Log.i(TAG, "selfTest: mediaStore=" + writeMediaStore(context, bmp));
    }

    /**
     * Save a committed image and hand its path to {@code paste}.
     *
     * <p>The gesture this serves is one tap: the picture is copied in the gallery, the
     * keyboard's clipboard panel shows its thumbnail, and tapping the thumbnail makes the
     * IME call {@code commitContent()} (see {@code TerminalView.commitContent}). Nothing
     * happens on focus or on a timer - an image attaches exactly when the user taps it,
     * which is the difference between "it pastes itself" and "I paste it".
     *
     * <p>Why a file path rather than the image itself: opencode's paste command asks
     * OpenTUI for the clipboard, and OpenTUI reads the <em>host</em> clipboard
     * ({@code createClipboard().read()} delegates only to the native X11/Wayland backend).
     * Android has neither, so a TUI inside this terminal can never see the Android
     * clipboard - OSC 52/5522 do not change that path. What it can do is open a file: its
     * paste handler resolves a local filepath into a {@code data:} URI attachment. Hence
     * the path, and hence {@link #CLIP_DIR} must be readable by the Termux uid.
     */
    static void saveAsync(final Context context, final Uri uri, final Handler postToUi,
                          final Paste paste, final Runnable afterRead) {
        if (uri == null) {
            if (afterRead != null) afterRead.run();
            return;
        }
        if (!paste.ready()) {
            if (afterRead != null) afterRead.run();
            return;
        }
        // Decoding a screenshot on the UI thread would freeze the terminal. No in-flight
        // guard is needed: a tap is one call, and the IME will not send a second image
        // until this one returns.
        Thread worker = new Thread(() -> {
            final String path = save(context, uri);
            if (afterRead != null) afterRead.run();
            postToUi.post(() -> {
                if (path == null) {
                    Log.w(TAG, "clip save failed: " + uri);
                    Toast.makeText(context, io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0069), Toast.LENGTH_SHORT).show();
                    return;
                }
                Log.i(TAG, "clip saved: " + path);
                paste.insert(path);
            });
        }, "clip-save");
        worker.start();
    }

    /**
     * The paste-key path: if the clipboard holds an image, save it off the UI thread and insert
     * its path. Returns false when the clip is not an image, so the caller can paste text.
     *
     * <p>This is the same work {@link #saveAsync} does for the keyboard's tap; the paste key
     * used to call the blocking {@link #save} instead, which decodes a full-resolution
     * screenshot on the UI thread - the terminal and the keyboard stop responding for as long as
     * the decode takes, which on a 108 MP photo is not a frame drop but a visible freeze.
     */
    static boolean pasteIfImage(final Context context, final Handler postToUi,
                                final ClipData clip, final Paste paste) {
        if (!isImage(context, clip)) return false;
        saveAsync(context, uriOf(clip), postToUi, paste, null);
        return true;
    }

    /** True when the primary clip is an image (any image/* MIME, or a URI that resolves to one). */
    static boolean isImage(Context context, ClipData clip) {
        ClipDescription description = clip.getDescription();
        if (description != null) {
            for (String mime : description.filterMimeTypes("image/*")) {
                if (mime.startsWith("image/")) return true;
            }
        }
        // OneUI's gallery copies a picture as a URI, and some apps hand it over as
        // text/uri-list rather than a typed item. The URI is the truth either way.
        Uri uri = uriOf(clip);
        if (uri == null) return false;
        String[] types = context.getContentResolver().getStreamTypes(uri, "image/*");
        return types != null && types.length > 0;
    }

    /**
     * The item's URI, or the one written into its text.
     *
     * <p>A copy from a gallery usually arrives as a typed item, but "copy link/image" style
     * clips arrive as {@code text/uri-list} with the URI only in the text. Reading both
     * costs nothing and the alternative is a copied picture that silently does nothing.
     */
    static Uri uriOf(ClipData clip) {
        if (clip == null || clip.getItemCount() == 0) return null;
        ClipData.Item item = clip.getItemAt(0);
        if (item == null) return null;
        Uri direct = item.getUri();
        if (direct != null) return direct;
        CharSequence text = item.getText();
        if (text == null) return null;
        // text/uri-list allows comment lines and may hold several URIs; the first is ours.
        for (String line : text.toString().split("\\s+")) {
            if (line.startsWith("content://") || line.startsWith("file://")) {
                return Uri.parse(line);
            }
        }
        return null;
    }

    /**
     * Where a pasted image has to live.
     *
     * NOT the app cache. The path is inserted into the prompt as text and the reading
     * program is opencode, which runs as the Termux uid - it gets EACCES on
     * /data/data/io.github.crashedsystem.terminal/cache/clip, so "image pasted" silently became
     * "attachment does not exist". /sdcard/Download is the one place both uids can see,
     * and it is already the daemon's handshake directory, so it is known to work here.
     */
    private static final String CLIP_DIR = "/sdcard/Download/fterm-clip";

    /** Keep a few so a read that races the next paste still finds its file. */
    private static final int KEEP = 5;

    /**
     * Decode the image URI into a PNG that the terminal's programs can actually open.
     * Returns the absolute path, or null when the URI is unreadable.
     */
    static String save(Context context, Uri uri) {
        try {
            Bitmap bmp = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri));
            if (bmp == null) return null;
            String path = writeFile(bmp);
            return path != null ? path : writeMediaStore(context, bmp);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Direct file write. This is what the daemon handshake already does (/sdcard/Download
     * /fterm-up), so on a device where that works this is the shortest path and yields a
     * plain filesystem path with no media database involved.
     */
    private static String writeFile(Bitmap bmp) {
        try {
            File dir = new File(CLIP_DIR);
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            File out = new File(dir, "clip-" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fo = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fo);
            }
            // World-readable: nothing else grants the Termux uid a read here.
            out.setReadable(true, false);
            prune(dir);
            return out.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Scoped-storage fallback (API 29+): an app without all-files access cannot create a
     * file by path, but MediaStore can, and the result is still an ordinary
     * /sdcard/Download path that the Termux uid reads like any other download.
     */
    private static String writeMediaStore(Context context, Bitmap bmp) {
        if (Build.VERSION.SDK_INT < 29) return null;
        String name = "clip-" + System.currentTimeMillis() + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/fterm-clip");
        try {
            Uri target = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (target == null) return null;
            try (OutputStream os = context.getContentResolver().openOutputStream(target)) {
                if (os == null || !bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) return null;
            }
            return CLIP_DIR + "/" + name;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Delete all but the newest {@link #KEEP} clips, so a screenshot spree cannot fill the phone. */
    private static void prune(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= KEEP) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = KEEP; i < files.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }
}
