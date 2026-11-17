package com.termux.terminal;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link SessionTransport} that talks to the fterm daemon over loopback TCP.
 *
 * Frame format is defined by termux/ftermd: a one byte type, a little endian 32 bit payload
 * length, then the payload. Output is pushed to the session on a reader thread; input is
 * buffered on a writer thread so the UI thread never blocks on a socket.
 */
public final class SocketTransport implements SessionTransport {

    private static final String LOG_TAG = "SocketTransport";

    private static final int C_HELLO = 0x01;
    private static final int C_SYNC = 0x09;
    private static final int C_OPEN = 0x10;
    private static final int C_CLOSE = 0x11;
    private static final int C_INPUT = 0x12;
    private static final int C_RESIZE = 0x13;
    private static final int C_PING = 0x18;

    private static final int S_HELLO_OK = 0x80;
    private static final int S_OPENED = 0x90;
    private static final int S_OUTPUT = 0x91;
    private static final int S_EXIT = 0x92;
    private static final int S_REPLAY = 0x93;
    private static final int S_SYNCED = 0x9E;
    private static final int S_ERROR = 0x9F;

    private static final int MAX_FRAME = 1 << 20;

    /**
     * Read timeout, and it must be comfortably larger than the ping interval.
     *
     * The first version had a 15s timeout and pinged "if idle for 8s" checked after an 8s
     * sleep - the check landed exactly on the boundary, so the ping was skipped and the next
     * opportunity was at 16s, one second after the read had already given up. That is the
     * measured 15s disconnect. A ping is now sent unconditionally every 5s against a 20s
     * timeout, so two pings can be lost without declaring the link dead.
     */
    private static final int SO_TIMEOUT_MS = 20000;

    /** Interval between keep-alive pings; always sent, not only when "idle enough". */
    private static final long IDLE_PING_MS = 5000;

    private final String mHost;
    private final int mPort;
    private final String mToken;
    private final int mSessionId;

    private Socket mSocket;
    private DataInputStream mIn;
    private OutputStream mOut;
    private TerminalSession mSession;
    private volatile int mColumns;
    private volatile int mRows;
    private final AtomicBoolean mRunning = new AtomicBoolean(true);
    /**
     * Outgoing frames, already framed. Every write happens on the writer thread: a socket
     * write on the UI thread throws NetworkOnMainThreadException, and a resize arrives on
     * the UI thread from the layout pass.
     */
    private final LinkedBlockingQueue<byte[]> mOutQueue = new LinkedBlockingQueue<>();
    private volatile Thread mWriter;
    private volatile int mPid;

    /** When the daemon last said anything; drives the keep-alive ping. */
    private volatile long mLastRecv = System.currentTimeMillis();

    /** A null in the queue tells the writer to flush and close. */
    private static final byte[] POISON = new byte[0];

    /** Human readable reason the last connection attempt failed, for the UI to show. */
    private volatile String mLastError;

    public SocketTransport(String host, int port, String token, int sessionId) {
        mHost = host;
        mPort = port;
        mToken = token;
        mSessionId = sessionId;
    }

    public String getLastError() {
        return mLastError;
    }

    public int getPid() {
        return mPid;
    }

    @Override
    public void start(TerminalSession session) {
        mSession = session;
        mColumns = session.getEmulator().mColumns;
        mRows = session.getEmulator().mRows;
        // A non-zero pid keeps isRunning() true so input is not dropped; the real pid
        // arrives with the OPENED frame and is reported to the client from there.
        session.mShellPid = -2;
        Thread t = new Thread(this::run, "fterm-transport[" + mSessionId + "]");
        t.setDaemon(true);
        t.start();
    }

    private void connect() throws IOException {
        Socket s = new Socket();
        s.setTcpNoDelay(true);
        s.connect(new InetSocketAddress(mHost, mPort), 2500);
        mSocket = s;
        // A read that times out is how a frozen/dozed app notices its connection died. Without
        // this the reader thread blocks in read() indefinitely and the terminal looks frozen
        // even after the process comes back.
        s.setSoTimeout(SO_TIMEOUT_MS);
        mIn = new DataInputStream(new BufferedInputStream(s.getInputStream(), 16384));
        mOut = s.getOutputStream();

        byte[] tok = mToken.getBytes(StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream hello = new java.io.ByteArrayOutputStream();
        hello.write(1);
        hello.write(tok.length & 0xff);
        hello.write((tok.length >> 8) & 0xff);
        hello.write(tok, 0, tok.length);
        // Still on this thread: nothing else has the socket yet, and HELLO must be the
        // first frame. Everything after this goes through the queue.
        writeNow(frameOf(C_HELLO, hello.toByteArray()));
    }

    private void run() {
        try {
            connect();
        } catch (IOException e) {
            fail("연결 실패: " + e.getMessage());
            return;
        }

        startWriter();
        startKeepAlive();

        try {
            while (mRunning.get()) {
                mLastRecv = System.currentTimeMillis();
                int type = mIn.readUnsignedByte();
                int len = readIntLE(mIn);
                if (len < 0 || len > MAX_FRAME) {
                    fail("프레임 길이 오류");
                    return;
                }
                byte[] payload = new byte[len];
                mIn.readFully(payload);
                if (!handle(type, payload)) return;
            }
        } catch (EOFException e) {
            fail("데몬이 연결을 닫았습니다");
        } catch (java.net.SocketTimeoutException e) {
            // The app was frozen or dozed. The daemon has been silent for longer than the
            // timeout, so the link is dead even though the socket does not know it yet.
            fail("연결이 끊겼습니다 (응답 없음)");
        } catch (IOException e) {
            if (mRunning.get()) fail("수신 오류: " + e.getMessage());
        }
    }

    /** @return false when the session is finished and the loop must stop. */
    private boolean handle(int type, byte[] p) throws IOException {
        switch (type) {
            case S_HELLO_OK:
                // The size gathered during layout goes out with OPEN, so the daemon spawns
                // the shell at the right width instead of the 80x24 default.
                enqueue(C_OPEN, openPayload(mColumns, mRows));
                return true;
            case S_OPENED:
                if (p.length >= 6) {
                    mPid = readIntLE(p, 2);
                    TerminalSession s = mSession;
                    if (s != null) {
                        s.mShellPid = mPid > 0 ? mPid : -2;
                        s.notifyScreenUpdate();
                    }
                }
                return true;
            case S_REPLAY: {
                // Ring of what the shell printed while nobody was attached. Feeding it into the
                // emulator reconstructs the visible screen at the current size.
                int n = p.length - 2;
                if (n > 0) deliver(p, 2, n);
                return true;
            }
            case S_OUTPUT: {
                if (p.length >= 6) {
                    int n = readIntLE(p, 2);
                    if (n > 0 && 6 + n <= p.length) deliver(p, 6, n);
                }
                return true;
            }
            case S_EXIT: {
                int code = p.length >= 6 ? readIntLE(p, 2) : -1;
                mRunning.set(false);
                TerminalSession s = mSession;
                if (s != null) s.transportExit(code);
                closeQuietly();
                return false;
            }
            case S_ERROR: {
                int code = p.length >= 1 ? (p[0] & 0xff) : 0;
                int msgLen = p.length >= 3 ? readShortLE(p, 1) : 0;
                String msg = msgLen > 0 && 3 + msgLen <= p.length
                        ? new String(p, 3, msgLen, StandardCharsets.UTF_8) : "";
                fail(errorText(code, msg));
                return false;
            }
            case S_SYNCED:
            case 0x98: // S_PONG
                mLastRecv = System.currentTimeMillis();
                return true;
            default:
                return true;
        }
    }

    private static String errorText(int code, String msg) {
        switch (code) {
            case 2: return "데몬 프로토콜 버전이 다릅니다";
            case 3: return "토큰이 틀렸습니다 — Termux에서 ftermd token 을 확인하세요";
            case 5: return "세션이 없습니다";
            case 6: return "데몬이 셸을 띄우지 못했습니다";
            case 7: return "동시 세션 한도를 넘었습니다";
            default: return "데몬 오류: " + msg;
        }
    }

    private void deliver(byte[] p, int offset, int count) {
        TerminalSession s = mSession;
        if (s == null) return;
        byte[] copy = new byte[count];
        System.arraycopy(p, offset, copy, 0, count);
        s.transportDeliver(copy, count);
    }

    private void fail(String reason) {
        mLastError = reason;
        mRunning.set(false);
        TerminalSession s = mSession;
        if (s != null) {
            byte[] text = ("\r\n[fterm] " + reason + "\r\n").getBytes(StandardCharsets.UTF_8);
            s.transportDeliver(text, text.length);
        }
        // Try to reconnect instead of declaring the shell dead. A backgrounded app loses
        // its socket to a doze kill; the shell itself is alive on the daemon, so killing
        // the session here is what made "back out of the app" kill the shell. Three quick
        // attempts; if they all fail the transport reports dead and the UI's Enter-to-close
        // path takes over as before.
        closeQuietly();
        new Thread(() -> {
            for (int attempt = 1; attempt <= 3; attempt++) {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { return; }
                TerminalSession sess = mSession;
                if (sess == null) return;
                mRunning.set(true);
                try {
                    run();   // reconnects, re-opens the session, feeds the ring replay
                    return;  // connected and running again
                } catch (Throwable t) {
                    mRunning.set(false);
                }
            }
            // All attempts failed: the shell is effectively unreachable for this session.
            TerminalSession sess = mSession;
            if (sess != null) sess.transportExit(-1);
        }, "fterm-reconnect[" + mSessionId + "]").start();
    }

    private void closeQuietly() {
        mRunning.set(false);
        try {
            if (mSocket != null) mSocket.close();
        } catch (IOException ignored) {
        }
    }

    /**
     * Send a PING whenever the link has been quiet for a while.
     *
     * Android freezes cached apps; the socket then looks open but nothing flows. A ping every
     * few seconds makes the daemon's idle watchdog see a live client, keeps the NAT/socket
     * state alive, and gives the reader a deadline to fail on instead of blocking forever.
     */
    private void startKeepAlive() {
        Thread t = new Thread(() -> {
            while (mRunning.get()) {
                try {
                    Thread.sleep(IDLE_PING_MS);
                } catch (InterruptedException e) {
                    return;
                }
                if (!mRunning.get()) return;
                // Unconditional: the daemon answers every ping, and a missed answer is the
                // signal that the socket died while the app was frozen.
                enqueue(C_PING, new byte[0]);
            }
        }, "fterm-keepalive[" + mSessionId + "]");
        t.setDaemon(true);
        t.start();
    }

    private void startWriter() {
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    byte[] framed = mOutQueue.take();
                    if (framed == POISON) break;
                    writeNow(framed);
                }
            } catch (InterruptedException ignored) {
            } catch (IOException e) {
                // A closed socket while draining the queue after close() is expected.
                if (mRunning.get()) fail("송신 오류: " + e.getMessage());
            } finally {
                try {
                    if (mSocket != null) mSocket.close();
                } catch (IOException ignored) {
                }
            }
        }, "fterm-writer[" + mSessionId + "]");
        t.setDaemon(true);
        mWriter = t;
        t.start();
    }

    /** Enqueue a frame. Safe from the UI thread; never touches the socket. */
    private void enqueue(int type, byte[] payload) {
        if (!mRunning.get()) return;
        mOutQueue.offer(frameOf(type, payload));
    }

    private static byte[] frameOf(int type, byte[] payload) {
        byte[] head = new byte[5 + payload.length];
        head[0] = (byte) type;
        putInt(head, 1, payload.length);
        System.arraycopy(payload, 0, head, 5, payload.length);
        return head;
    }

    /** The only place a frame reaches the socket. Writer thread or the connecting thread. */
    private void writeNow(byte[] framed) throws IOException {
        OutputStream out = mOut;
        if (out == null) throw new IOException("not connected");
        out.write(framed);
        out.flush();
    }

    private byte[] openPayload(int cols, int rows) {
        byte[] b = new byte[6];
        putShort(b, 0, mSessionId);
        putShort(b, 2, cols);
        putShort(b, 4, rows);
        return b;
    }

    @Override
    public void write(byte[] data, int offset, int count) {
        if (!mRunning.get()) return;
        byte[] b = new byte[6 + count];
        putShort(b, 0, mSessionId);
        putInt(b, 2, count);
        System.arraycopy(data, offset, b, 6, count);
        enqueue(C_INPUT, b);
    }

    @Override
    public void resize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        // Called from the layout pass on the UI thread, so this only records the size and
        // queues the frame. If the socket is not up yet the value is used by OPEN instead.
        mColumns = columns;
        mRows = rows;
        if (!mRunning.get()) return;
        byte[] b = new byte[6];
        putShort(b, 0, mSessionId);
        putShort(b, 2, columns);
        putShort(b, 4, rows);
        enqueue(C_RESIZE, b);
    }

    @Override
    public void close() {
        if (!mRunning.getAndSet(false)) return;
        // CLOSE must reach the daemon before the socket dies, so the writer performs the
        // shutdown when it reaches the poison pill. Interrupting it here would drop CLOSE
        // and leave the shell alive on the daemon side.
        byte[] b = new byte[2];
        putShort(b, 0, mSessionId);
        mOutQueue.offer(frameOf(C_CLOSE, b));
        mOutQueue.offer(POISON);
        TerminalSession s = mSession;
        if (s != null) s.transportExit(0);
    }

    private static int readIntLE(DataInputStream in) throws IOException {
        int b0 = in.readUnsignedByte();
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        int b3 = in.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static int readIntLE(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    private static int readShortLE(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    private static void putShort(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
    }

    private static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
        b[off + 2] = (byte) ((v >> 16) & 0xff);
        b[off + 3] = (byte) ((v >> 24) & 0xff);
    }
}
