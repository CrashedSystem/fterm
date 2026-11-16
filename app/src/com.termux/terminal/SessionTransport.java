package com.termux.terminal;

import java.io.InputStream;

/**
 * A byte transport between a {@link TerminalSession} and a shell.
 *
 * The stock Termux implementation talks to a local PTY through JNI. fterm instead lets
 * the Termux-side daemon own the PTY, so the session talks to a socket and the JNI path
 * is only used when no transport is installed.
 */
public interface SessionTransport {

    /** Called once by {@link TerminalSession} after the emulator is created. */
    void start(TerminalSession session);

    /** Bytes typed by the user. */
    void write(byte[] data, int offset, int count);

    /** The pty window changed. */
    void resize(int columns, int rows, int cellWidthPixels, int cellHeightPixels);

    /** The session is being torn down. */
    void close();
}
