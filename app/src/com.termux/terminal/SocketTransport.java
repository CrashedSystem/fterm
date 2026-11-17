package com.termux.terminal;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A {@link SessionTransport} that talks to the fterm daemon over loopback TCP.
 *
 * Frame format is defined by termux/ftermd: a one byte type, a little endian 32 bit payload
 * length, then the payload. Output is pushed to the session on a reader thread; input is
 * buffered on a writer thread so the UI thread never blocks on a socket.
 *
 * <h3>One link at a time, and the generation that enforces it</h3>
 *
 * <p>Reconnecting used to be a spawn: the reader hit an error, {@code fail()} started a new
 * reconnect thread which called {@code run()} again, and the writer and keep-alive threads of
 * the <em>previous</em> connection were never stopped. Two of them then drained the same
 * outgoing queue onto whatever socket {@code mOut} happened to point at, and the keep-alive
 * pings of both generations kept the daemon thinking two clients were attached. The daemon log
 * showed the symptom plainly: one session, three {@code open} lines, {@code clients=3}, and the
 * same 14 byte input frame written three times. Interleaved frames desynchronise the reader, the
 * reader stops matching frame boundaries, and the emulator receives nothing usable: the screen
 * goes blank until the tab is switched.
 *
 * <p>The structure is now inverted. {@link #supervise} is the only thread that ever decides to
 * reconnect, it lives for the whole session, and each connection is a numbered generation.
 * Writer and keep-alive threads capture their generation and exit the moment it stops matching,
 * so a dead link cannot leave anything behind to write into the next one. Nothing outside
 * {@code supervise} starts a connection.
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

    /**
     * Consecutive failed attempts before the session is declared unreachable.
     *
     * <p>Counted only for links that never became stable - a connect that refused, or a link
     * that died within {@link #STABLE_MS}. A link that carried traffic for longer resets the
     * count, which matters because the most common drop is the opposite of a failure: Android
     * freezes a backgrounded app, the reader times out after 20 s, and the shell on the daemon
     * is perfectly healthy. Counting that toward a ceiling killed a live shell after three
     * doze cycles, so it does not count.
     */
    private static final int MAX_ATTEMPTS = 6;

    /** Pause between attempts, so a daemon that is still starting gets a chance. */
    private static final long RETRY_DELAY_MS = 1000;

    /**
     * A link that lived this long resets the failure count. Without it, a flapping connection
     * would exhaust its three attempts over minutes of otherwise healthy use.
     */
    private static final long STABLE_MS = 5000;

    private final String mHost;
    private final int mPort;
    private final String mToken;
    private final int mSessionId;

    private TerminalSession mSession;
    private volatile int mColumns;
    private volatile int mRows;

    /** True from {@link #start} until {@link #close}: input is accepted and links are wanted. */
    private volatile boolean mRunning = true;

    /** True once {@link #close} ran or the attempts were exhausted; nothing may reconnect. */
    private volatile boolean mClosed;

    /** Set when the daemon reported the shell gone, so a reconnect would be pointless. */
    private volatile boolean mSessionEnded;

    /**
     * The current link's number. Bumped by {@link #supervise} and nowhere else, so comparing
     * against it is how a writer or keep-alive thread learns that its socket is history.
     */
    private volatile int mGeneration;

    /**
     * The per-link threads, held so {@link #endLink} can interrupt them.
     *
     * <p>The generation check alone is correct, but a keep-alive thread sits in a five second
     * sleep, so without an interrupt it stays alive for up to five seconds after its link is
     * gone - long enough to outlive several reconnects and look exactly like a leak.
     */
    private volatile Thread mWriter;
    private volatile Thread mKeepAlive;

    private volatile Socket mSocket;

    /**
     * Outgoing frames, already framed. Every write happens on the current writer thread: a
     * socket write on the UI thread throws NetworkOnMainThreadException, and a resize arrives
     * on the UI thread from the layout pass. Surviving a reconnect is deliberate - a resize or a
     * keystroke queued just before the drop belongs to the same shell that is still running.
     */
    private final LinkedBlockingQueue<byte[]> mOutQueue = new LinkedBlockingQueue<>();

    /** A null in the queue tells the writer to flush and close. */
    private static final byte[] POISON = new byte[0];

    /** Human readable reason the last connection attempt failed, for the UI to show. */
    private volatile String mLastError;

    private volatile int mPid;

    /** When the daemon last said anything; drives the keep-alive ping. */
    private volatile long mLastRecv = System.currentTimeMillis();

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
        Thread t = new Thread(this::supervise, "fterm-transport[" + mSessionId + "]");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ supervision

    /**
     * Own the session's link for its whole life: connect, read, notice the drop, reconnect.
     *
     * <p>This is the only place that creates a connection, which is what makes "one link at a
     * time" a property of the code rather than a hope.
     */
    private void supervise() {
        int failures = 0;
        while (!mClosed && mRunning) {
            final Socket socket;
            final OutputStream out;
            final DataInputStream in;
            try {
                socket = connect();
                out = socket.getOutputStream();
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 16384));
            } catch (IOException e) {
                failures++;
                mLastError = "연결 실패: " + e.getMessage();
                announce(mLastError);
                if (giveUpAfter(failures)) return;
                if (!sleepBeforeRetry()) return;
                continue;
            }

            final int gen = ++mGeneration;
            mSocket = socket;
            mLastRecv = System.currentTimeMillis();
            startWriter(gen, out);
            startKeepAlive(gen);
            final long connectedAt = System.currentTimeMillis();

            // Blocks until this link ends, one way or another.
            String reason = readLoop(gen, in);
            endLink(gen);

            if (mClosed || mSessionEnded || !mRunning) return;
            if (reason != null) {
                mLastError = reason;
                announce(reason);
            }
            // A link that carried traffic for a while is not a failed attempt; only rapid
            // drops count toward the ceiling, or a long session would eventually give up.
            if (System.currentTimeMillis() - connectedAt >= STABLE_MS) failures = 0;
            failures++;
            if (giveUpAfter(failures)) return;
            if (!sleepBeforeRetry()) return;
        }
    }

    /** @return false when the session should stop for good, after the last attempt. */
    private boolean giveUpAfter(int failures) {
        if (failures < MAX_ATTEMPTS) return false;
        mRunning = false;
        TerminalSession s = mSession;
        if (s != null) s.transportExit(-1);
        return true;
    }

    /** @return false if the transport was closed while waiting. */
    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            return false;
        }
        return !mClosed && mRunning;
    }

    private Socket connect() throws IOException {
        Socket s = new Socket();
        boolean ok = false;
        try {
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(mHost, mPort), 2500);
            // A read that times out is how a frozen/dozed app notices its connection died.
            // Without this the reader blocks in read() indefinitely and the terminal looks
            // frozen even after the process comes back.
            s.setSoTimeout(SO_TIMEOUT_MS);

            byte[] tok = mToken.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream hello = new ByteArrayOutputStream();
            hello.write(1);
            hello.write(tok.length & 0xff);
            hello.write((tok.length >> 8) & 0xff);
            hello.write(tok, 0, tok.length);
            // HELLO must be the first frame on the connection, so it is written here before
            // the writer thread exists. Everything after it goes through the queue.
            OutputStream out = s.getOutputStream();
            out.write(frameOf(C_HELLO, hello.toByteArray()));
            out.flush();
            ok = true;
            return s;
        } finally {
            if (!ok) {
                try {
                    s.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Retire a link: invalidate its threads, interrupt them, then close the socket under them. */
    private void endLink(int gen) {
        if (gen == mGeneration) mGeneration = gen + 1;
        Thread w = mWriter, k = mKeepAlive;
        mWriter = null;
        mKeepAlive = null;
        if (w != null) w.interrupt();
        if (k != null) k.interrupt();
        Socket s = mSocket;
        mSocket = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Read frames until this link ends.
     *
     * @return null when the link ended cleanly (closed, or the shell exited), otherwise the
     *         reason to show in the terminal before the next attempt.
     */
    private String readLoop(int gen, DataInputStream in) {
        try {
            while (gen == mGeneration && !mClosed) {
                mLastRecv = System.currentTimeMillis();
                int type = in.readUnsignedByte();
                int len = readIntLE(in);
                if (len < 0 || len > MAX_FRAME) return "프레임 길이 오류";
                byte[] payload = new byte[len];
                in.readFully(payload);
                if (!handle(type, payload)) return null;
            }
            return null;
        } catch (EOFException e) {
            return mSessionEnded ? null : "데몬이 연결을 닫았습니다";
        } catch (SocketTimeoutException e) {
            // The app was frozen or dozed. The daemon has been silent for longer than the
            // timeout, so the link is dead even though the socket does not know it yet.
            return "연결이 끊겼습니다 (응답 없음)";
        } catch (IOException e) {
            return mClosed ? null : "수신 오류: " + e.getMessage();
        }
    }

    /** @return false when the session is finished and the link must not be re-established. */
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
                mRunning = false;
                mSessionEnded = true;
                TerminalSession s = mSession;
                if (s != null) s.transportExit(code);
                return false;
            }
            case S_ERROR: {
                int code = p.length >= 1 ? (p[0] & 0xff) : 0;
                int msgLen = p.length >= 3 ? readShortLE(p, 1) : 0;
                String msg = msgLen > 0 && 3 + msgLen <= p.length
                        ? new String(p, 3, msgLen, StandardCharsets.UTF_8) : "";
                mLastError = errorText(code, msg);
                announce(mLastError);
                // A wrong token or protocol will fail identically three times in a row; there
                // is nothing to retry, and the message is more useful left on screen.
                mRunning = false;
                mSessionEnded = true;
                TerminalSession s = mSession;
                if (s != null) s.transportExit(-1);
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

    private void announce(String reason) {
        TerminalSession s = mSession;
        if (s == null || reason == null) return;
        byte[] text = ("\r\n[fterm] " + reason + "\r\n").getBytes(StandardCharsets.UTF_8);
        s.transportDeliver(text, text.length);
    }

    // ------------------------------------------------------------------ per-link threads

    /**
     * Drain the queue onto this link's socket, and stop the moment the link is retired.
     *
     * @param out captured per link: the field version was the bug, because the next connection
     *            overwrote it while the old writer was still running.
     */
    private void startWriter(int gen, OutputStream out) {
        Thread t = new Thread(() -> {
            try {
                while (gen == mGeneration) {
                    byte[] framed = mOutQueue.poll(200, TimeUnit.MILLISECONDS);
                    if (framed == null) continue;
                    if (framed == POISON) {
                        // CLOSE has already been written by now; closing the stream is what
                        // makes the reader notice and lets supervise retire the link.
                        break;
                    }
                    if (gen != mGeneration) break;
                    out.write(framed);
                    out.flush();
                }
            } catch (InterruptedException ignored) {
            } catch (IOException e) {
                // Not a reconnect trigger on its own: closing the socket forces the reader to
                // fail, and supervise is the single place that decides what happens next.
                if (gen == mGeneration) mLastError = "송신 오류: " + e.getMessage();
            } finally {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
                Socket s = mSocket;
                if (s != null) {
                    try {
                        s.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }, "fterm-writer[" + mSessionId + "]");
        t.setDaemon(true);
        mWriter = t;
        t.start();
    }

    /**
     * Ping while this link is quiet.
     *
     * Android freezes cached apps; the socket then looks open but nothing flows. A ping every
     * few seconds makes the daemon's idle watchdog see a live client, keeps the socket state
     * alive, and gives the reader a deadline to fail on instead of blocking forever.
     */
    private void startKeepAlive(int gen) {
        Thread t = new Thread(() -> {
            while (gen == mGeneration && !mClosed) {
                try {
                    Thread.sleep(IDLE_PING_MS);
                } catch (InterruptedException e) {
                    return;
                }
                if (gen != mGeneration || mClosed) return;
                // Unconditional: the daemon answers every ping, and a missed answer is the
                // signal that the socket died while the app was frozen.
                enqueue(C_PING, new byte[0]);
            }
        }, "fterm-keepalive[" + mSessionId + "]");
        t.setDaemon(true);
        mKeepAlive = t;
        t.start();
    }

    // ------------------------------------------------------------------ SessionTransport

    /** Enqueue a frame. Safe from the UI thread; never touches the socket. */
    private void enqueue(int type, byte[] payload) {
        if (mClosed) return;
        mOutQueue.offer(frameOf(type, payload));
    }

    @Override
    public void write(byte[] data, int offset, int count) {
        if (mClosed || !mRunning) return;
        byte[] b = new byte[6 + count];
        putShort(b, 0, mSessionId);
        putInt(b, 2, count);
        System.arraycopy(data, offset, b, 6, count);
        enqueue(C_INPUT, b);
    }

    @Override
    public void resize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        // Called from the layout pass on the UI thread, so this only records the size and
        // queues the frame. If no link is up yet the value is used by OPEN instead.
        mColumns = columns;
        mRows = rows;
        if (mClosed || !mRunning) return;
        byte[] b = new byte[6];
        putShort(b, 0, mSessionId);
        putShort(b, 2, columns);
        putShort(b, 4, rows);
        enqueue(C_RESIZE, b);
    }

    @Override
    public void close() {
        if (mClosed) return;
        mClosed = true;
        mRunning = false;
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

    // ------------------------------------------------------------------ framing

    private byte[] openPayload(int cols, int rows) {
        byte[] b = new byte[6];
        putShort(b, 0, mSessionId);
        putShort(b, 2, cols);
        putShort(b, 4, rows);
        return b;
    }

    private static byte[] frameOf(int type, byte[] payload) {
        byte[] head = new byte[5 + payload.length];
        head[0] = (byte) type;
        putInt(head, 1, payload.length);
        System.arraycopy(payload, 0, head, 5, payload.length);
        return head;
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
