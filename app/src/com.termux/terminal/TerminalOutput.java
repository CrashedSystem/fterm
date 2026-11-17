package com.termux.terminal;

import java.nio.charset.StandardCharsets;

/** A client which receives callbacks from events triggered by feeding input to a {@link TerminalEmulator}. */
public abstract class TerminalOutput {

    /** Write a string using the UTF-8 encoding to the terminal client. */
    public final void write(String data) {
        if (data == null) return;
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        write(bytes, 0, bytes.length);
    }

    /** Write bytes to the terminal client. */
    public abstract void write(byte[] data, int offset, int count);

    /** Notify the terminal client that the terminal title has changed. */
    public abstract void titleChanged(String oldTitle, String newTitle);

    /** Notify the terminal client that text should be copied to clipboard. */
    public abstract void onCopyTextToClipboard(String text);

    /** Notify the terminal client that text should be pasted from clipboard. */
    public abstract void onPasteTextFromClipboard();

    /**
     * The current clipboard text, for OSC 52 read requests (`ESC ] 52 ; c ; ?`).
     *
     * Return null to decline the read: that is the safe default, because a program can
     * silently harvest the clipboard. Callers that opt in (fterm's session) return the
     * clipboard, which is what makes opencode's paste work at all - its TUI cannot read
     * the Android clipboard itself and asks through this escape.
     */
    public String getClipboardText() {
        return null;
    }

    /**
     * The current clipboard IMAGE as PNG bytes, for OSC 52 read requests that want an
     * image (opentui asks with preferredTypes ["image/png", ...]). Return null when the
     * clipboard holds no image or the caller declines.
     */
    public byte[] getClipboardImage() {
        return null;
    }

    /** Notify the terminal client that a bell character (ASCII 7, bell, BEL, \a, ^G)) has been received. */
    public abstract void onBell();

    public abstract void onColorsChanged();

    /**
     * A desktop notification requested by the program in the shell.
     *
     * Terminals have no standard for this; the two de-facto ones are OSC 9 (iTerm2, and what
     * most CLI tools emit) and OSC 777 (rxvt-unicode). Both are handled here so that a tool
     * which already notifies on a desktop also notifies on the phone - no plugin, no
     * termux-api, no helper binary.
     *
     * @param title the title, or an empty string when the sequence carried only a body
     * @param body  the message text
     */
    public void onNotification(String title, String body) {}
}
