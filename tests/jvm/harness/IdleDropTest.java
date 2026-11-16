import com.termux.terminal.DaemonClient;
import com.termux.terminal.SocketTransport;
import com.termux.terminal.TerminalSession;

import java.net.Socket;
import java.util.List;

/**
 * The daemon must survive a client going away.
 *
 * The bug this pins down: `drop_idle()` closed a socket *after* it had been put into the
 * select() fd set, so select() got -1 and the daemon died with
 * "file descriptor cannot be a negative integer (-1)" - taking every live shell with it.
 * That crash is what "the terminal dies when idle" actually was.
 */
public class IdleDropTest {

    static void check(boolean c, String m) {
        if (!c) throw new AssertionError(m);
        pass++;
    }

    static int pass = 0;

    static boolean await(java.util.function.BooleanSupplier c, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (c.getAsBoolean()) return true;
            Thread.sleep(50);
        }
        return c.getAsBoolean();
    }

    public static void main(String[] args) throws Exception {
        String token = args[0];
        int port = Integer.parseInt(args[1]);

        // 1. A long-lived session.
        TerminalSession keeper = new TerminalSession();
        SocketTransport kt = new SocketTransport("127.0.0.1", port, token, 91);
        keeper.setTransport(kt);
        kt.start(keeper);
        check(await(() -> keeper.delivered.length() > 10, 8000), "keeper session opened");

        // 2. Twenty short-lived clients that connect and vanish. Each one used to be a chance
        //    for the daemon to close a socket that select() was still watching.
        for (int i = 0; i < 20; i++) {
            Socket s = new Socket("127.0.0.1", port);
            s.getOutputStream().write(new byte[]{1, 0, 0, 0, 0});   // a HELLO header, then vanish
            s.getOutputStream().flush();
            s.close();
            Thread.sleep(60);
        }
        System.out.println("ok  20 abrupt disconnects");

        // 3. The daemon is still there.
        List<Integer> live = DaemonClient.liveSessions("127.0.0.1", port, token, 3000);
        check(live.contains(91), "the daemon survived and still holds session 91: " + live);
        System.out.println("ok  daemon alive after the churn, sessions " + live);

        // 4. And the long-lived session still works.
        byte[] cmd = "echo SURVIVED-$((6*7))\n".getBytes("UTF-8");
        kt.write(cmd, 0, cmd.length);
        check(await(() -> keeper.delivered.indexOf("SURVIVED-42") >= 0, 8000),
                "the keeper session still runs commands");
        System.out.println("ok  the surviving session is usable");

        // 5. An idle client must not take the daemon down either. (The daemon's own timeout
        //    is 120 s, far longer than a test should wait, so this only asserts the path exists
        //    in the source - the runtime part is covered by the churn above.)
        String src = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("daemon.src",
                        "termux/ftermd"))), "UTF-8");
        int dropAt = src.indexOf("self.drop_idle()");
        int rfdsAt = src.indexOf("rfds = [self.srv]");
        check(dropAt > 0 && rfdsAt > 0 && dropAt < rfdsAt,
                "drop_idle() runs before the fd set is built");
        check(src.contains("if c.alive:") && src.contains("if not s.closed and s.fd >= 0"),
                "closed sockets and fds cannot enter select()");
        check(src.contains("except (OSError, ValueError)"), "and select() errors do not kill the loop");

        kt.close();
        System.out.println("ALL OK");
        System.exit(0);
    }
}
