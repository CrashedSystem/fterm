package com.termux.view;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.termux.terminal.TerminalSession;

/**
 * The interface for communication between {@link TerminalView} and its client. It allows for getting
 * various  configuration options from the client and for sending back data to the client like logs,
 * key events, both hardware and IME (which makes it different from that available with
 * {@link View#setOnKeyListener(View.OnKeyListener)}, etc. It must be set for the
 * {@link TerminalView} through {@link TerminalView#setTerminalViewClient(TerminalViewClient)}.
 */
public interface TerminalViewClient {

    /**
     * Callback function on scale events according to {@link ScaleGestureDetector#getScaleFactor()}.
     */
    float onScale(float scale);



    /**
     * On a single tap on the terminal if terminal mouse reporting not enabled.
     */
    void onSingleTapUp(MotionEvent e);

    /**
     * Ctrl+tap on the terminal: open the link under the pointer, if there is one.
     *
     * <p>Asked instead of the view opening the browser itself because the link's text and the
     * app it opens are the host's business, and because the view cannot tell a URL from a
     * word without the emulator's rows.
     *
     * @param row    the row under the pointer, in transcript coordinates (negative is history)
     * @param column the display column under the pointer
     * @return true when a link was found and opened; the view then swallows the gesture
     */
    boolean openUrlAt(int row, int column);

    boolean shouldBackButtonBeMappedToEscape();

    boolean shouldEnforceCharBasedInput();

    boolean shouldUseCtrlSpaceWorkaround();

    boolean isTerminalViewSelected();



    void copyModeChanged(boolean copyMode);



    boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session);

    boolean onKeyUp(int keyCode, KeyEvent e);

    boolean onLongPress(MotionEvent event);



    boolean readControlKey();

    boolean readAltKey();

    boolean readShiftKey();

    boolean readFnKey();



    boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session);

    /**
     * An image the IME handed over through {@code commitContent()} - a tap on the clipboard
     * thumbnail in Samsung keyboard's clipboard panel, or Gboard's clipboard row.
     *
     * <p>Rich content is the only way a keyboard can deliver a picture: it is not text, so
     * {@code commitText()} cannot carry it, and a terminal has no editable field for an
     * editor-style paste. The client is responsible for reading {@code uri} (it may need to
     * request the temporary read grant, which this view does not hand out) and for calling
     * {@code afterRead} when it is done, so the grant can be released.
     *
     * @param uri       the content URI of the committed image, readable for this call
     * @param description the IME's human-readable description, may be null
     * @param afterRead run once the content has been read (releases the URI permission)
     */
    void onCommitImage(android.net.Uri uri, String description, Runnable afterRead);


    void onEmulatorSet();


    void logError(String tag, String message);

    void logWarn(String tag, String message);

    void logInfo(String tag, String message);

    void logDebug(String tag, String message);

    void logVerbose(String tag, String message);

    void logStackTraceWithMessage(String tag, String message, Exception e);

    void logStackTrace(String tag, Exception e);

}
