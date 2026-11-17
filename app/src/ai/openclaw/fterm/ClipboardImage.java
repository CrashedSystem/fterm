package ai.openclaw.fterm;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Clipboard image handling: a screenshot on the clipboard has no text, so the paste path
 * saves it as a PNG and pastes the path instead - opencode attaches file:// URIs in the
 * prompt, which turns "I pasted a screenshot" into an image the model can see.
 */
final class ClipboardImage {

    private ClipboardImage() {}

    /** What the activity does when an image is pasted. Kept out of the watcher so it stays testable. */
    interface Paste {
        /** False while there is no live session to insert into. Focus is the caller's job. */
        boolean ready();

        /** Insert the saved path into the active session's prompt. */
        void insert(String path);
    }

    /**
     * Watch the clipboard and hand every NEW image to {@code paste}.
     *
     * <p>Copying an image in any app (a screenshot in the gallery, "copy" in a viewer) is
     * the whole gesture: nothing else triggers it and no paste button exists.
     *
     * <p>Why a file path rather than the image itself: opencode's paste command asks
     * OpenTUI for the clipboard, and OpenTUI reads the <em>host</em> clipboard
     * ({@code createClipboard().read()} delegates only to the native X11/Wayland backend).
     * Android has neither, so a TUI inside this terminal can never see the Android
     * clipboard - OSC 52/5522 do not change that path. What it can do is open a file: its
     * paste handler resolves a local filepath into a {@code data:} URI attachment. Hence
     * the path, and hence {@link #CLIP_DIR} must be readable by the Termux uid.
     */
    static Watcher watch(Context context, Handler postToUi, Paste paste) {
        Watcher watcher = new Watcher(context, postToUi, paste);
        watcher.listen();
        return watcher;
    }

    /**
     * The clipboard rule, kept in a object because a change listener alone is not enough.
     *
     * <p>Copying a screenshot happens in the gallery, where this app is in the background.
     * Android 10+ refuses clipboard access to a background app and the change listener does
     * not fire for one either, so the event that matters is lost by the time the user comes
     * back. {@link #poll()} is therefore called again whenever the window regains focus,
     * and a clip is only acted on once thanks to {@link ClipDescription#getTimestamp()}.
     */
    static final class Watcher {
        private final Context context;
        private final Handler postToUi;
        private final Paste paste;
        private String lastUri;
        private long lastStamp = Long.MIN_VALUE;

        private Watcher(Context context, Handler postToUi, Paste paste) {
            this.context = context;
            this.postToUi = postToUi;
            this.paste = paste;
        }

        private void listen() {
            try {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null) return;
                cm.addPrimaryClipChangedListener(() -> postToUi.post(this::poll));
            } catch (Throwable ignored) {
                // A device with no clipboard service still gets every other feature.
            }
        }

        /** Save the current clipboard image and paste its path, unless it was already done. */
        void poll() {
            try {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null || !paste.ready() || !cm.hasPrimaryClip()) return;
                ClipData clip = cm.getPrimaryClip();
                if (clip == null) return;
                ClipDescription description = clip.getDescription();
                long stamp = description == null ? Long.MIN_VALUE : description.getTimestamp();
                // Only a real timestamp is a safe "already handled" signal. A clip that
                // reports none must still get through, or its image is skipped forever.
                if (stamp != Long.MIN_VALUE && stamp == lastStamp) return;
                Uri uri = uriOf(clip);
                if (uri == null || !isImage(context, clip)) return;
                String path = save(context, uri);
                if (path == null) return;
                // Copying the same picture twice must not insert its path twice.
                if (uri.toString().equals(lastUri)) {
                    lastStamp = stamp;
                    return;
                }
                lastUri = uri.toString();
                lastStamp = stamp;
                paste.insert(path);
            } catch (Throwable ignored) {
                // A clipboard that cannot be read is not worth crashing over.
            }
        }
    }

    /** True when the primary clip is an image (any image/* MIME). */
    static boolean isImage(Context context, ClipData clip) {
        if (clip.getDescription() != null) {
            for (String mime : clip.getDescription().filterMimeTypes("image/*")) {
                if (mime.startsWith("image/")) return true;
            }
        }
        if (clip.getItemAt(0) == null || clip.getItemAt(0).getUri() == null) return false;
        String[] uris = context.getContentResolver().getStreamTypes(
                clip.getItemAt(0).getUri(), "image/*");
        return uris != null && uris.length > 0;
    }

    static android.net.Uri uriOf(ClipData clip) {
        return clip.getItemAt(0).getUri();
    }

    /**
     * Where a pasted image has to live.
     *
     * NOT the app cache. The path is inserted into the prompt as text and the reading
     * program is opencode, which runs as the Termux uid - it gets EACCES on
     * /data/data/ai.openclaw.fterm/cache/clip, so "image pasted" silently became
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
