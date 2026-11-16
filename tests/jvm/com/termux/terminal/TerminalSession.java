package com.termux.terminal;

/**
 * Test stub for the Android-bound {@link TerminalSession}: only the surface that
 * {@link SocketTransport} touches, plus recording of what arrived.
 */
public class TerminalSession {

    public static final int MIN = Integer.MIN_VALUE;

    public volatile int mShellPid = 0;
    public volatile int exitCode = MIN;
    public final StringBuffer delivered = new StringBuffer();

    private final Emu mEmu = new Emu();
    private SocketTransport mTransport;

    public static final class Emu {
        public int mColumns = 80;
        public int mRows = 24;
    }

    public Emu getEmulator() {
        return mEmu;
    }

    public void setTransport(SessionTransport transport) {
        mTransport = (SocketTransport) transport;
    }

    public SocketTransport transport() {
        return mTransport;
    }

    void transportDeliver(byte[] data, int count) {
        delivered.append(new String(data, 0, count, java.nio.charset.StandardCharsets.UTF_8));
    }

    void transportExit(int code) {
        exitCode = code;
    }

    void notifyScreenUpdate() {
    }
}
