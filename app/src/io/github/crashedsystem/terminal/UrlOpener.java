package io.github.crashedsystem.terminal;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.view.TerminalView;

/**
 * Ctrl+click on a link: which characters were under the pointer, and opening what they spell.
 *
 * <p>The rule itself - is there a URL at this character index, where does it end, does it need
 * a scheme - is in {@link TextOps}, because a terminal line is a string and a test can read
 * one. This file is the half that needs the emulator: turning the pointer's row and column
 * into a character index in the <em>logical</em> line (a wrapped line is one line, and a wide
 * Hangul glyph is one character over two cells), then handing the result to the browser.
 */
final class UrlOpener {

    private UrlOpener() {}

    /**
     * Open the link at {@code row}/{@code column} of the emulator, if there is one.
     *
     * @param row    the row the pointer was on, in transcript coordinates (negative is history)
     * @param column the display column the pointer was on
     * @return true when a link was found and the browser was asked for it
     */
    static boolean open(Activity activity, TerminalView view, int row, int column) {
        TerminalEmulator emulator = view == null ? null : view.mEmulator;
        if (emulator == null) return false;
        TerminalBuffer screen = emulator.getScreen();
        if (screen == null) return false;

        int columns = emulator.mColumns;
        int top = -screen.getActiveTranscriptRows();
        int bottom = emulator.mRows - 1;
        if (row < top || row > bottom || column < 0 || column >= columns) return false;

        int first = lineStart(screen, row, columns, top);
        int last = lineEnd(screen, row, columns, bottom);
        String line = screen.getSelectedText(0, first, columns, last, true, true);
        int offset = Math.max(0,
                screen.getSelectedText(0, first, column, row, true, true).length() - 1);
        String url = TextOps.urlAt(line, offset);
        if (url == null) return false;

        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return true;
        } catch (ActivityNotFoundException e) {
            // A device with no browser at all: say so rather than leave the tap doing nothing.
            Toast.makeText(activity, AppStrings.s(R.string.link_no_browser), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * The first row of the logical line {@code row} belongs to.
     *
     * <p>Wrapping is what makes a long URL clickable at all: the shell breaks it across rows
     * and the emulator marks the rows it continued, so the whole link is still one string.
     * Reading the rows above with trailing blanks kept ({@code joinFullLines}) and looking
     * for a newline is how the buffer itself says whether the break was a wrap or a line end.
     */
    private static int lineStart(TerminalBuffer screen, int row, int columns, int top) {
        int first = row;
        while (first > top && !screen.getSelectedText(0, first - 1, columns, row, true, true)
                .contains("\n")) {
            first--;
        }
        return first;
    }

    /** The last row of that logical line. */
    private static int lineEnd(TerminalBuffer screen, int row, int columns, int bottom) {
        int last = row;
        while (last < bottom && !screen.getSelectedText(0, row, columns, last + 1, true, true)
                .contains("\n")) {
            last++;
        }
        return last;
    }
}
