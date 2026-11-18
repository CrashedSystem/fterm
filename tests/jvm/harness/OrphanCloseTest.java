import com.termux.terminal.SocketTransport;
import com.termux.terminal.TerminalSession;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A session that ends on an {@code S_ERROR} must still tell the daemon to kill its shell.
 *
 * <p>The leak this pins down: the daemon answers some errors by dropping the connection while
 * keeping the pty and the shell. The app treated that error as the end of the session and never
 * reconnected, so {@code C_CLOSE} was never sent - the shell stayed, counted against the
 * sixteen-session limit, until "동시 세션 한도를 넘었습니다" started refusing new tabs.
 *
 * <p>The fake daemon here speaks just enough of the protocol: it answers HELLO, takes OPEN, and
 * then answers {@code S_ERROR} and closes the socket the way the real one does. A second
 * connection is then expected to arrive carrying HELLO and CLOSE for the same session id. No
 * ftermd, no Android, no pty - which is what makes this runnable in CI.
 *
 *   java -cp <classes> OrphanCloseTest
 */
public class OrphanCloseTest {

    static final int C_HELLO = 0x01;
    static final int C_OPEN = 0x10;
    static final int C_CLOSE = 0x11;
    static final int S_HELLO_OK = 0x80;
    static final int S_OPENED = 0x90;
    static final int S_ERROR = 0x9F;
    static final int E_BAD_FRAME = 4;
    static final int SID = 42;

    static int failures = 0;

    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(0, 8, InetAddress.getLoopbackAddress())) {
            int port = server.getLocalPort();
            AtomicReference<String> closedSid = new AtomicReference<>();
            AtomicInteger connections = new AtomicInteger();
            AtomicReference<String> serverError = new AtomicReference<>();

            Thread fake = new Thread(() -> serve(server, closedSid, connections, serverError),
                    "fake-daemon");
            fake.setDaemon(true);
            fake.start();

            TerminalSession session = new TerminalSession();
            SocketTransport transport = new SocketTransport("127.0.0.1", port, "tok", SID);
            session.setTransport(transport);
            transport.start(session);

            long deadline = System.currentTimeMillis() + 6000;
            while (System.currentTimeMillis() < deadline && closedSid.get() == null) {
                Thread.sleep(20);
            }

            check(serverError.get() == null, "the fake daemon broke: " + serverError.get());
            check(closedSid.get() != null,
                    "the daemon was never told to close the session (S_ERROR path leaks a shell)");
            check("42".equals(closedSid.get()),
                    "CLOSE carried the wrong session id: " + closedSid.get());
            check(session.exitCode != TerminalSession.MIN,
                    "the app must see the session end, not a tab that looks alive");
            // The error is final for this session: no reconnect, and therefore no third link.
            Thread.sleep(400);
            check(connections.get() == 2,
                    "expected one follow-up connection carrying CLOSE, saw " + connections.get());

            System.out.println((failures == 0 ? "OrphanCloseTest: OK" : "OrphanCloseTest: FAILED")
                    + " (" + (failures == 0 ? "5" : String.valueOf(5 - failures)) + " assertions)");
            if (failures != 0) System.exit(1);
        }
    }

    static void check(boolean cond, String msg) {
        if (!cond) {
            failures++;
            System.out.println("FAIL " + msg);
        }
    }

    /**
     * Two connections: the first gets S_ERROR after OPEN, the second is expected to say CLOSE.
     */
    static void serve(ServerSocket server, AtomicReference<String> closedSid,
                      AtomicInteger connections, AtomicReference<String> error) {
        try {
            for (int i = 0; i < 2; i++) {
                Socket s = server.accept();
                connections.incrementAndGet();
                DataInputStream in = new DataInputStream(s.getInputStream());
                OutputStream out = s.getOutputStream();
                boolean done = false;
                while (!done) {
                    int type;
                    try {
                        type = in.readUnsignedByte();
                    } catch (EOFException eof) {
                        break;
                    }
                    int len = readIntLE(in);
                    if (len < 0 || len > (1 << 20)) {
                        throw new IOException("bad frame length " + len);
                    }
                    byte[] p = new byte[len];
                    in.readFully(p);
                    switch (type) {
                        case C_HELLO:
                            write(out, S_HELLO_OK, new byte[] {1});
                            break;
                        case C_OPEN: {
                            // The shell exists from here on: this is the OPENED the real daemon
                            // sends, and it is what makes the session something to close.
                            byte[] opened = new byte[6];
                            opened[0] = (byte) (SID & 0xff);
                            opened[1] = (byte) ((SID >> 8) & 0xff);
                            putIntLE(opened, 2, 12345);
                            write(out, S_OPENED, opened);
                            // Then the daemon hits something it will not accept: it says so and
                            // drops the link, and the shell it just spawned stays behind.
                            write(out, S_ERROR, errorPayload(E_BAD_FRAME, "frame too large"));
                            done = true;
                            break;
                        }
                        case C_CLOSE:
                            closedSid.set(String.valueOf(readShortLE(p, 0)));
                            done = true;
                            break;
                        default:
                            break;
                    }
                }
                s.close();
            }
        } catch (IOException e) {
            error.set(e.toString());
        }
    }

    static byte[] errorPayload(int code, String msg) {
        byte[] b = msg.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[3 + b.length];
        out[0] = (byte) code;
        out[1] = (byte) (b.length & 0xff);
        out[2] = (byte) ((b.length >> 8) & 0xff);
        System.arraycopy(b, 0, out, 3, b.length);
        return out;
    }

    static void write(OutputStream out, int type, byte[] payload) throws IOException {
        byte[] frame = new byte[5 + payload.length];
        frame[0] = (byte) type;
        putIntLE(frame, 1, payload.length);
        System.arraycopy(payload, 0, frame, 5, payload.length);
        out.write(frame);
        out.flush();
    }

    static int readIntLE(DataInputStream in) throws IOException {
        int b0 = in.readUnsignedByte(), b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte(), b3 = in.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    static int readShortLE(byte[] p, int off) {
        return (p[off] & 0xff) | ((p[off + 1] & 0xff) << 8);
    }

    static void putIntLE(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
        b[off + 2] = (byte) ((v >> 16) & 0xff);
        b[off + 3] = (byte) ((v >> 24) & 0xff);
    }
}
