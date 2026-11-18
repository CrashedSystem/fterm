package com.termux.terminal;

import static com.termux.terminal.Protocol.C_CLOSE;
import static com.termux.terminal.Protocol.C_HELLO;
import static com.termux.terminal.Protocol.C_INPUT;
import static com.termux.terminal.Protocol.C_OPEN;
import static com.termux.terminal.Protocol.C_PING;
import static com.termux.terminal.Protocol.C_RESIZE;
import static com.termux.terminal.Protocol.E_BAD_FRAME;
import static com.termux.terminal.Protocol.MAX_FRAME;
import static com.termux.terminal.Protocol.S_ERROR;
import static com.termux.terminal.Protocol.S_EXIT;
import static com.termux.terminal.Protocol.S_HELLO_OK;
import static com.termux.terminal.Protocol.S_OPENED;
import static com.termux.terminal.Protocol.S_OUTPUT;
import static com.termux.terminal.Protocol.S_REPLAY;
import static com.termux.terminal.Protocol.S_SYNCED;

import java.io.BufferedInputStream;
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
     * True once the daemon answered OPENED: from then on a shell exists on the daemon side, and
     * C_CLOSE is the only thing that kills it.
     */
    private volatile boolean mOpened;

    /**
     * True once C_CLOSE was sent for this session id.
     *
     * <p>Exactly one of the paths that end a session may send it - a tab close, an S_ERROR, the
     * attempt ceiling - and the flag is what makes "exactly one" true rather than likely.
     */
    private volatile boolean mCloseSent;

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

    /** The shell and directory a session opens with; both may be empty for the defaults. */
    private final String mShell;
    private final String mCwd;

    public SocketTransport(String host, int port, String token, int sessionId) {
        this(host, port, token, sessionId, null, null);
    }

    /**
     * @param shell an absolute path the daemon may run, or null/"" for its own default
     * @param cwd   the directory to start in, or null/"" for the daemon's home
     */
    public SocketTransport(String host, int port, String token, int sessionId,
                           String shell, String cwd) {
        mHost = host;
        mPort = port;
        mToken = token;
        mSessionId = sessionId;
        mShell = shell;
        mCwd = cwd;
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
        // No link is coming back, but the daemon may still be holding this session's shell.
        tellDaemonToClose(false);
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

            // HELLO must be the first frame on the connection, so it is written here before
            // the writer thread exists. Everything after it goes through the queue.
            OutputStream out = s.getOutputStream();
            out.write(Protocol.frame(C_HELLO, Protocol.hello(mToken)));
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
                int len = Protocol.readInt(in);
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
                mOpened = true;
                if (p.length >= 6) {
                    mPid = Protocol.readInt(p, 2);
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
                    int n = Protocol.readInt(p, 2);
                    if (n > 0 && 6 + n <= p.length) deliver(p, 6, n);
                }
                return true;
            }
            case S_EXIT: {
                int code = p.length >= 6 ? Protocol.readInt(p, 2) : -1;
                // The daemon removed the session itself; a C_CLOSE here would only be a line in
                // its log. Marking it sent is what keeps a later path from opening a connection
                // just to say "already gone".
                mCloseSent = true;
                mRunning = false;
                mSessionEnded = true;
                TerminalSession s = mSession;
                if (s != null) s.transportExit(code);
                return false;
            }
            case S_ERROR: {
                int code = p.length >= 1 ? (p[0] & 0xff) : 0;
                int msgLen = p.length >= 3 ? Protocol.readShort(p, 1) : 0;
                String msg = msgLen > 0 && 3 + msgLen <= p.length
                        ? new String(p, 3, msgLen, StandardCharsets.UTF_8) : "";
                mLastError = errorText(code, msg);
                announce(mLastError);
                // A wrong token or protocol will fail identically three times in a row; there
                // is nothing to retry, and the message is more useful left on screen.
                mRunning = false;
                mSessionEnded = true;
                // The daemon drops the connection after some of these errors but keeps the pty
                // and the shell. Nothing below will reconnect for this session, so this is the
                // last chance to close it.
                tellDaemonToClose(false);
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
        mOutQueue.offer(Protocol.frame(type, payload));
    }

    @Override
    public void write(byte[] data, int offset, int count) {
        if (mClosed || !mRunning) return;
        byte[] b = new byte[6 + count];
        Protocol.putShort(b, 0, mSessionId);
        Protocol.putInt(b, 2, count);
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
        Protocol.putShort(b, 0, mSessionId);
        Protocol.putShort(b, 2, columns);
        Protocol.putShort(b, 4, rows);
        enqueue(C_RESIZE, b);
    }

    @Override
    public void close() {
        if (mClosed) return;
        mClosed = true;
        mRunning = false;
        // CLOSE must reach the daemon before the socket dies, so the writer performs the
        // shutdown when it reaches the poison pill. Interrupting it here would drop CLOSE
        // and leave the shell alive on the daemon side. A tab closed while the app is offline
        // has no writer to hand it to, which is why this goes through the same helper as the
        // error paths instead of queueing directly.
        tellDaemonToClose(true);
        mOutQueue.offer(POISON);
        TerminalSession s = mSession;
        if (s != null) s.transportExit(0);
    }

    // ------------------------------------------------------------------ closing the shell

    /**
     * Kill the shell this session owns, once.
     *
     * <p>A C_CLOSE queued for the writer only reaches the daemon while a link is up, and a
     * missing link is exactly the situation that used to leak the shell: an {@link #S_ERROR}
     * the daemon answers by dropping the connection, {@link #MAX_ATTEMPTS} failed reconnects,
     * or a tab closed while the app is offline. The daemon keeps the pty and the shell, and
     * sixteen sessions are its limit, so a handful of leaks makes every new tab fail with
     * "동시 세션 한도를 넘었습니다".
     *
     * <p>When no live link can carry it, this opens a short-lived one of its own and says it
     * plainly. It never throws: this runs on the way out of a session that is already over.
     *
     * @param useLiveLink true when the current socket is still healthy (an ordinary tab close)
     */
    private void tellDaemonToClose(boolean useLiveLink) {
        if (!mOpened || mCloseSent) return;
        mCloseSent = true;
        byte[] b = new byte[2];
        Protocol.putShort(b, 0, mSessionId);
        Socket live = mSocket;
        if (useLiveLink && live != null && live.isConnected() && !live.isClosed()) {
            // Queued rather than written: every socket write belongs to the writer thread.
            mOutQueue.offer(Protocol.frame(C_CLOSE, b));
            return;
        }
        Socket tmp = null;
        try {
            tmp = new Socket();
            tmp.connect(new InetSocketAddress(mHost, mPort), 800);
            tmp.setSoTimeout(800);
            OutputStream out = tmp.getOutputStream();
            out.write(Protocol.frame(C_HELLO, Protocol.hello(mToken)));
            out.write(Protocol.frame(C_CLOSE, b));
            out.flush();
            // Read the daemon's first frame: that is the acknowledgement that it has read
            // HELLO, and CLOSE is in the same segment behind it.
            tmp.getInputStream().read();
        } catch (IOException | RuntimeException ignored) {
            // Best effort. If even this fails, the shell is still bounded by the daemon's idle
            // watchdog and by the next successful connection's C_CLOSE.
        } finally {
            if (tmp != null) {
                try {
                    tmp.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------------------ framing

    /**
     * The OPEN frame: id and size, then the shell and directory as length-prefixed UTF-8.
     *
     * <p>The tail is optional by protocol, so an older daemon reads the first six bytes and
     * ignores the rest, and a request with no shell and no directory is still six bytes -
     * which is what keeps the two builds speaking to each other.
     */
    private byte[] openPayload(int cols, int rows) {
        byte[] shell = mShell == null ? new byte[0] : mShell.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] cwd = mCwd == null ? new byte[0] : mCwd.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = new byte[6 + 2 + shell.length + 2 + cwd.length];
        Protocol.putShort(b, 0, mSessionId);
        Protocol.putShort(b, 2, cols);
        Protocol.putShort(b, 4, rows);
        Protocol.putShort(b, 6, shell.length);
        System.arraycopy(shell, 0, b, 8, shell.length);
        int off = 8 + shell.length;
        Protocol.putShort(b, off, cwd.length);
        System.arraycopy(cwd, 0, b, off + 2, cwd.length);
        return b;
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


}
