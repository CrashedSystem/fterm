package com.termux.terminal;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The wire protocol between the app and ftermd, in one place.
 *
 * <p>Before this file the constants lived four times: in {@link SocketTransport}, in
 * {@link DaemonClient}, in the first (Python) daemon and in {@code termux/ftermd-rs}. They
 * agreed, but only by having been copied carefully, and a frame type added on one side and not
 * the others is a silent misread rather than an error - the daemon answers "unknown type" and
 * the app sees a tab that does nothing. The Python implementation has since been removed; the
 * Rust one is the daemon.
 *
 * <p>Java can only share with Java, so the agreement is checked by a test instead:
 * {@code tests/test_protocol.py} reads the constant tables out of the Rust
 * daemon and this class and fails when any value differs. That check is the point; this file is
 * where the Java half of it lives.
 *
 * <h3>Frames</h3>
 * <pre>
 *   &lt;u8 type&gt;&lt;u32 little-endian payload length&gt;&lt;payload&gt;
 * </pre>
 * A payload over {@link #MAX_FRAME} is refused rather than buffered, on both sides.
 */
public final class Protocol {

    /** Bumped only for an incompatible change; both sides refuse anything else. */
    public static final int VERSION = 1;

    /** Largest payload either side will accept. A font is sent in chunks under this. */
    public static final int MAX_FRAME = 1 << 20;

    // ---------------------------------------------------------------- client -> daemon
    public static final int C_HELLO = 0x01;
    public static final int C_SYNC = 0x09;
    public static final int C_OPEN = 0x10;
    public static final int C_CLOSE = 0x11;
    public static final int C_INPUT = 0x12;
    public static final int C_RESIZE = 0x13;
    public static final int C_PING = 0x18;
    public static final int C_FONT_LIST = 0x1A;
    public static final int C_FONT_GET = 0x1B;
    public static final int C_FONT_FETCH = 0x1C;

    // ---------------------------------------------------------------- daemon -> client
    public static final int S_HELLO_OK = 0x80;
    public static final int S_OPENED = 0x90;
    public static final int S_OUTPUT = 0x91;
    public static final int S_EXIT = 0x92;
    public static final int S_REPLAY = 0x93;
    public static final int S_PONG = 0x98;
    public static final int S_FONT_LIST = 0x9A;
    public static final int S_FONT_DATA = 0x9B;
    public static final int S_FONT_FETCHED = 0x9C;
    public static final int S_SYNCED = 0x9E;
    public static final int S_ERROR = 0x9F;

    // ---------------------------------------------------------------- error codes
    public static final int E_BAD_MAGIC = 1;
    public static final int E_BAD_PROTO = 2;
    public static final int E_BAD_TOKEN = 3;
    public static final int E_BAD_FRAME = 4;
    public static final int E_NO_SESSION = 5;
    public static final int E_SPAWN = 6;
    public static final int E_TOO_MANY = 7;

    private Protocol() {}

    /** One frame, header included. */
    public static byte[] frame(int type, byte[] payload) {
        byte[] framed = new byte[5 + payload.length];
        framed[0] = (byte) type;
        putInt(framed, 1, payload.length);
        System.arraycopy(payload, 0, framed, 5, payload.length);
        return framed;
    }

    /**
     * HELLO: the protocol byte, then the token as a length-prefixed UTF-8 string.
     *
     * <p>It must be the first frame on a connection. It is built the same way here, in
     * {@link DaemonClient} and in the CLOSE-on-error path of {@link SocketTransport}, which is
     * why it is one method rather than three copies of three writes.
     */
    public static byte[] hello(String token) {
        byte[] tok = (token == null ? "" : token).getBytes(StandardCharsets.UTF_8);
        byte[] hello = new byte[3 + tok.length];
        hello[0] = (byte) VERSION;
        putShort(hello, 1, tok.length);
        System.arraycopy(tok, 0, hello, 3, tok.length);
        return hello;
    }

    /** C_CLOSE: the session id, two bytes. */
    public static byte[] close(int sessionId) {
        byte[] b = new byte[2];
        putShort(b, 0, sessionId);
        return b;
    }

    /** C_RESIZE: session id then columns and rows, six bytes. */
    public static byte[] resize(int sessionId, int columns, int rows) {
        byte[] b = new byte[6];
        putShort(b, 0, sessionId);
        putShort(b, 2, columns);
        putShort(b, 4, rows);
        return b;
    }

    public static void putShort(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
    }

    public static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
        b[off + 2] = (byte) ((v >> 16) & 0xff);
        b[off + 3] = (byte) ((v >> 24) & 0xff);
    }

    public static int readInt(DataInputStream in) throws IOException {
        int b0 = in.readUnsignedByte();
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        int b3 = in.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    public static int readInt(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    public static int readShort(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }
}
