import com.termux.terminal.SocketTransport;
import com.termux.terminal.TerminalSession;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reconnecting must replace a link, not accumulate one.
 *
 * <p>The bug this pins down was invisible from the inside: the daemon log showed one session
 * with three {@code open} lines, {@code clients=3}, and the same 14 byte input frame written
 * three times. {@code SocketTransport.fail()} closed its socket and started a new reconnect
 * thread, but the writer, keep-alive and reader threads of the previous connection kept
 * running - and because {@code mSocket}/{@code mOut} were fields, those stale threads wrote
 * into whatever socket the newest generation had installed. Interleaved frames desynchronise
 * the reader, and the emulator stops receiving anything: the blank screen.
 *
 * <p>A live ftermd cannot be made to drop a link on demand, so this test runs its own daemon:
 * a {@link ServerSocket} speaking just enough of the protocol to accept, open, and then close
 * abruptly. Two invariants are asserted, both of which the old code violates:
 *
 * <ol>
 *   <li>Never more than one open connection per session, at any instant.</li>
 *   <li>One keystroke reaches the daemon exactly once, even across reconnects.</li>
 * </ol>
 */
public class ReconnectTest {

    static int pass = 0;

    static void check(boolean c, String m) {
        if (!c) throw new AssertionError(m);
        pass++;
    }

    /**
     * Count live threads whose name starts with {@code prefix}.
     *
     * <p>This is the invariant the old code broke outright: {@code fail()} started a reader
     * without stopping the writers of the link it had just abandoned, so every reconnect left
     * one more writer and one more keep-alive behind. They never exit - the writer loops on
     * {@code take()} forever - and their {@code finally} block closed {@code mSocket}, which by
     * then was the newest link's socket. Each stale thread therefore killed the link that
     * replaced it, which is a reconnect storm rather than a reconnect. Counting threads makes
     * that visible; connection counts do not, because a leaked writer does not open one.
     */
    static int liveThreads(String prefix) {
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive() && t.getName().startsWith(prefix)) n++;
        }
        return n;
    }

    static boolean await(java.util.function.BooleanSupplier c, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (c.getAsBoolean()) return true;
            Thread.sleep(20);
        }
        return c.getAsBoolean();
    }

    /** Enough of ftermd to keep a transport happy: accept, OPEN, echo nothing. */
    static final class FakeDaemon extends Thread {
        final ServerSocket server;
        final AtomicInteger live = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final AtomicInteger totalConnections = new AtomicInteger();
        /** Every C_INPUT payload ever seen, so duplicates stay visible across links. */
        final List<String> input = new CopyOnWriteArrayList<>();
        volatile Socket current;
        volatile boolean running = true;

        FakeDaemon() throws IOException {
            server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            setDaemon(true);
        }

        int port() {
            return server.getLocalPort();
        }

        @Override public void run() {
            while (running) {
                final Socket s;
                try {
                    s = server.accept();
                } catch (IOException e) {
                    return;
                }
                current = s;
                totalConnections.incrementAndGet();
                int now = live.incrementAndGet();
                // Record the high-water mark rather than just the final count: two links that
                // overlap briefly is exactly the old failure, and it would be gone by the end.
                peak.accumulateAndGet(now, Math::max);
                new Thread(() -> serve(s), "fake-daemon-conn").start();
            }
        }

        private void serve(Socket s) {
            try {
                DataInputStream in = new DataInputStream(s.getInputStream());
                OutputStream out = s.getOutputStream();
                while (true) {
                    int type = in.readUnsignedByte();
                    int len = readIntLE(in);
                    if (len < 0 || len > (1 << 20)) return;
                    byte[] p = new byte[len];
                    in.readFully(p);
                    if (type == 0x01) {                 // C_HELLO
                        out.write(frame(0x80, new byte[0]));      // S_HELLO_OK
                        out.flush();
                    } else if (type == 0x10) {          // C_OPEN
                        int sid = (p[0] & 0xff) | ((p[1] & 0xff) << 8);
                        byte[] opened = new byte[6];
                        opened[0] = (byte) sid;
                        opened[2] = 0x22;               // pid 0x2222, just needs to be > 0
                        opened[3] = 0x22;
                        out.write(frame(0x90, opened));           // S_OPENED
                        // A little output on every link, so "reconnected" is observable.
                        byte[] hello = ("\r\nlink-" + totalConnections.get() + "\r\n")
                                .getBytes(StandardCharsets.UTF_8);
                        out.write(frame(0x91, outputFrame(sid, hello)));
                        out.flush();
                    } else if (type == 0x12) {          // C_INPUT
                        int n = (p[2] & 0xff) | ((p[3] & 0xff) << 8)
                                | ((p[4] & 0xff) << 16) | ((p[5] & 0xff) << 24);
                        if (n > 0 && 6 + n <= p.length) {
                            input.add(new String(p, 6, n, StandardCharsets.UTF_8));
                        }
                    } else if (type == 0x18) {          // C_PING
                        out.write(frame(0x98, new byte[0]));      // S_PONG
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
                // The client closed, or the server dropped it on purpose.
            } finally {
                live.decrementAndGet();
                try {
                    s.close();
                } catch (IOException ignored) {
                }
            }
        }

        /** Kill the current link the way a doze freeze looks from the app's side. */
        void dropLink() {
            Socket s = current;
            if (s != null) {
                try {
                    s.close();
                } catch (IOException ignored) {
                }
            }
        }

        void stopAll() {
            running = false;
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }
    }

    static int readIntLE(DataInputStream in) throws IOException {
        int b0 = in.readUnsignedByte();
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        int b3 = in.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    static byte[] frame(int type, byte[] payload) {
        byte[] b = new byte[5 + payload.length];
        b[0] = (byte) type;
        b[1] = (byte) (payload.length & 0xff);
        b[2] = (byte) ((payload.length >> 8) & 0xff);
        b[3] = (byte) ((payload.length >> 16) & 0xff);
        b[4] = (byte) ((payload.length >> 24) & 0xff);
        System.arraycopy(payload, 0, b, 5, payload.length);
        return b;
    }

    static byte[] outputFrame(int sid, byte[] data) {
        byte[] b = new byte[6 + data.length];
        b[0] = (byte) (sid & 0xff);
        b[1] = (byte) ((sid >> 8) & 0xff);
        b[2] = (byte) (data.length & 0xff);
        b[3] = (byte) ((data.length >> 8) & 0xff);
        b[4] = (byte) ((data.length >> 16) & 0xff);
        b[5] = (byte) ((data.length >> 24) & 0xff);
        System.arraycopy(data, 0, b, 6, data.length);
        return b;
    }

    public static void main(String[] args) throws Exception {
        FakeDaemon daemon = new FakeDaemon();
        daemon.start();

        TerminalSession s = new TerminalSession();
        SocketTransport t = new SocketTransport("127.0.0.1", daemon.port(), "tok", 41);
        s.setTransport(t);
        t.start(s);

        check(await(() -> s.delivered.indexOf("link-1") >= 0, 5000),
                "first link opened, got: " + s.delivered);
        System.out.println("ok  first link opened");

        // Three forced drops. Each one must produce a new link and must not leave the old
        // one's threads behind.
        for (int i = 2; i <= 4; i++) {
            daemon.dropLink();
            final int n = i;
            check(await(() -> s.delivered.indexOf("link-" + n) >= 0, 6000),
                    "link-" + n + " after a drop, got: " + s.delivered);
        }
        System.out.println("ok  reconnected three times: " + daemon.totalConnections.get() + " links");

        // Invariant 1: the links replaced each other, never overlapped.
        check(daemon.peak.get() == 1,
                "never more than one live connection (peak " + daemon.peak.get() + ")");
        System.out.println("ok  peak concurrent links: " + daemon.peak.get());

        // Invariant 2: no thread outlived its link. One supervisor, one writer, one keep-alive,
        // because every generation is retired before the next one is created.
        int writers = liveThreads("fterm-writer[41]");
        int keepalives = liveThreads("fterm-keepalive[41]");
        int supervisors = liveThreads("fterm-transport[41]");
        check(writers == 1, "one writer thread after 4 links, got " + writers);
        check(keepalives == 1, "one keep-alive thread after 4 links, got " + keepalives);
        check(supervisors == 1, "one supervisor thread after 4 links, got " + supervisors);
        System.out.println("ok  threads after 4 links: " + supervisors + " supervisor, "
                + writers + " writer, " + keepalives + " keep-alive");

        // Invariant 2: one keystroke, one frame - on the current link, not on all of them.
        byte[] cmd = "echo ONCE\n".getBytes(StandardCharsets.UTF_8);
        t.write(cmd, 0, cmd.length);
        check(await(() -> !daemon.input.isEmpty(), 4000), "the keystroke reached the daemon");
        long copies = daemon.input.stream().filter(x -> x.equals("echo ONCE\n")).count();
        check(copies == 1, "the keystroke was delivered exactly once, got " + copies
                + " in " + daemon.input);
        System.out.println("ok  one keystroke, one frame across " + daemon.totalConnections.get()
                + " links");

        t.close();
        daemon.stopAll();
        System.out.println("ReconnectTest: " + pass + " assertions OK");
        System.exit(0);
    }
}
