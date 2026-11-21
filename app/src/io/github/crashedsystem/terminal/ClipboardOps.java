package io.github.crashedsystem.terminal;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

/**
 * Everything the activity does with the clipboard: copy out (without a TUI's padding), paste
 * in (with a confirmation when it is large), and the two OSC 52 reads a program can ask for.
 *
 * <p>It lives outside {@link MainActivity} because none of it is window plumbing: the activity
 * routes a menu item, a key binding or an IME commit to one of these calls and stays a
 * coordinator. {@link TextOps} holds the rules that need no {@code Context}.
 */
final class ClipboardOps {

    /** The clip the app itself writes is labelled, so its owner is visible to other apps. */
    private static final String LABEL = "fterm";

    private ClipboardOps() {}

    // ------------------------------------------------------------------ copy

    /**
     * The current selection, or the one the selection toolbar stored, with each line's
     * trailing blanks removed.
     */
    static String selectedText(MainActivity activity) {
        if (activity.mTermView.getCurrentSession() == null || activity.mTermView.mEmulator == null) {
            return null;
        }
        // The live selection first - that is what the highlight on screen is - and the text the
        // selection toolbar stored before closing as the fallback (the old Ctrl+Shift+C path).
        String live = activity.mTermView.getSelectedText();
        String text = live != null ? live : activity.mTermView.getStoredSelectedText();
        return TextOps.stripTrailingBlanks(text);
    }

    static void copySelection(MainActivity activity) {
        String selection = selectedText(activity);
        if (selection == null || selection.isEmpty()) {
            Toast.makeText(activity, AppStrings.s(R.string.copy_nothing_selected),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        copy(activity, selection);
        Toast.makeText(activity, AppStrings.s(R.string.copy_done), Toast.LENGTH_SHORT).show();
    }

    /**
     * Put text on the clipboard.
     *
     * <p>The scrub happens here rather than in the caller so every way of copying - the
     * toolbar, Ctrl+Shift+C, the selection handles, a program's OSC 52 - carries the same
     * rule. A TUI pads its lines to the frame width and none of that padding was typed.
     */
    static void copy(Context context, String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(LABEL, TextOps.stripTrailingBlanks(text)));
    }

    // ------------------------------------------------------------------ paste

    static void paste(MainActivity activity) {
        ClipboardManager cm =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return;
        ClipData clip = cm.getPrimaryClip();
        ClipData.Item item = clip.getItemAt(0);
        if (item == null) return;

        // An image clip has no text at all. Save it and paste the PATH: opencode resolves a
        // local filepath into a data: URI attachment, which is how a screenshot gets in.
        // Saving happens on the worker: decoding a screenshot on the UI thread freezes the
        // terminal while it runs.
        if (ClipboardImage.pasteIfImage(activity, ui(), clip, new ClipboardImage.Paste() {
            @Override public boolean ready() {
                Tab tab = activity.activeTab();
                return tab != null && tab.session != null;
            }
            @Override public void insert(String path) {
                pasteText(activity, path);
                Toast.makeText(activity, AppStrings.s(R.string.paste_image_path) + path,
                        Toast.LENGTH_LONG).show();
            }
        })) {
            return;
        }

        CharSequence text = item.coerceToText(activity);
        if (text == null || text.length() == 0) {
            // A clip that is neither text nor a detected image: tell the user instead of
            // doing nothing, which read as "image paste is broken".
            Toast.makeText(activity, AppStrings.s(R.string.paste_nothing), Toast.LENGTH_SHORT).show();
            return;
        }
        pasteText(activity, text.toString());
    }

    /**
     * Send text through the emulator as a paste, after asking when it is large.
     *
     * <p>5 KB is not a limit, it is a question. Below it the paste is the user's own action
     * arriving where they aimed it; above it a stray clip (a log, a whole file) can push a
     * TUI prompt somewhere they did not intend, and a terminal has no undo.
     */
    static void pasteText(MainActivity activity, String text) {
        if (TextOps.needsPasteWarning(text)) {
            askBeforePasting(activity, text);
            return;
        }
        pasteNow(activity, text);
    }

    private static void askBeforePasting(final MainActivity activity, final String text) {
        int bytes = TextOps.utf8Length(text);
        String kb = String.format(java.util.Locale.ROOT, "%.1f", bytes / 1024.0);
        String message = activity.getString(R.string.paste_large_message, kb, TextOps.lineCount(text));
        new AlertDialog.Builder(activity)
                .setTitle(R.string.paste_large_title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.paste_large_confirm,
                        (dialog, which) -> pasteNow(activity, text))
                .show();
    }

    private static void pasteNow(MainActivity activity, String text) {
        // Through the emulator, not the session: Emulator.paste() strips escapes, turns
        // newlines into CR, and - the part that matters here - wraps the text in
        // \e[200~ .. \e[201~ when the running program asked for bracketed paste
        // (DECSET 2004). A multi-line paste sent raw looks like a series of Return presses,
        // which is why "paste five lines" arrived as five separate submissions instead of
        // one "[5 lines pasted]" block.
        Tab tab = activity.activeTab();
        TerminalEmulator emulator = tab == null || tab.session == null
                ? null : tab.session.getEmulator();
        if (emulator != null) {
            emulator.paste(text);
        } else if (tab != null && tab.session != null) {
            tab.session.write(text);
        }
    }

    // ------------------------------------------------------------------ OSC 52

    /** OSC 52 read: the program asked for the clipboard (opencode's TUI pastes this way). */
    static String clipboardText(Context context) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return null;
        ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
        if (item == null) return null;
        CharSequence text = item.coerceToText(context);
        return text == null ? null : text.toString();
    }

    /**
     * OSC 52 image read: opentui asks with preferredTypes ["image/png", "text/plain"], so an
     * image clipboard must answer with PNG bytes or the paste silently fails. This is the
     * only path a screenshot has into opencode - the bracketed-paste channel is text only.
     */
    static byte[] clipboardImage(Context context) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return null;
        ClipData clip = cm.getPrimaryClip();
        if (clip.getItemAt(0) == null) return null;
        if (!ClipboardImage.isImage(context, clip)) return null;
        Uri uri = ClipboardImage.uriOf(clip);
        try {
            android.graphics.Bitmap bmp =
                    android.graphics.BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri));
            if (bmp == null) return null;
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bo);
            return bo.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    /** The IME's clipboard-thumbnail tap: read the image, then put its path in the prompt. */
    static void commitImage(final MainActivity activity, final Uri uri, final Runnable afterRead) {
        ClipboardImage.saveAsync(activity, uri, ui(), new ClipboardImage.Paste() {
            @Override public boolean ready() {
                Tab tab = activity.activeTab();
                return tab != null && tab.session != null;
            }
            @Override public void insert(String path) {
                pasteText(activity, path);
                Toast.makeText(activity, AppStrings.s(R.string.paste_image_attached) + path,
                        Toast.LENGTH_SHORT).show();
            }
        }, afterRead);
    }

    /**
     * A handler on the main looper for the worker's result.
     *
     * <p>{@link ClipboardImage} only needs somewhere to post to; handing it the activity's
     * own handler would make this class reach into the activity's field, and a second one
     * costs a few bytes.
     */
    private static Handler ui() {
        return new Handler(Looper.getMainLooper());
    }
}
