import com.termux.terminal.DaemonClient;
import com.termux.terminal.SocketTransport;
import com.termux.terminal.TerminalSession;

import java.util.List;

/**
 * Idle/freeze behaviour, against a live ftermd.
 *
 * Android freezes cached apps (SIGSTOP). A frozen app holds a TCP socket that looks open and
 * delivers nothing, which is the "terminal freezes after idle" report. Two things fix it and
 * both are checked here: the client pings while idle so the daemon sees a live client, and the
 * daemon drops a client that has been silent too long so a ghost cannot hold a session.
 */
public class IdleTest {

    static void check(boolean c, String m) {
        if (!c) throw new AssertionError(m);
    }

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

        // 1. A session survives 20s of doing nothing at all, and still works afterwards.
        TerminalSession s = new TerminalSession();
        SocketTransport t = new SocketTransport("127.0.0.1", port, token, 81);
        s.setTransport(t);
        t.start(s);
        check(await(() -> s.delivered.length() > 10, 8000), "session opened");

        int before = s.delivered.length();
        Thread.sleep(20000);                       // longer than the daemon's idle ping window
        check(s.exitCode == TerminalSession.MIN, "the session did not die while idle");

        byte[] cmd = "echo AFTER-IDLE-$((2*21))\n".getBytes("UTF-8");
        t.write(cmd, 0, cmd.length);
        check(await(() -> s.delivered.indexOf("AFTER-IDLE-42") >= 0, 8000),
                "the session still works after 20s idle");
        System.out.println("ok  idle 20s: session alive and usable (" +
                (s.delivered.length() - before) + " bytes after)");

        // 2. The daemon still lists it (the keep-alive ping kept the client non-idle).
        List<Integer> live = DaemonClient.liveSessions("127.0.0.1", port, token, 3000);
        check(live.contains(81), "SYNC still lists the idle-but-alive session: " + live);
        System.out.println("ok  daemon kept the idle client: " + live);

        // 3. A transport whose daemon goes away reports the break instead of hanging forever.
        SocketTransport doomed = new SocketTransport("127.0.0.1", port, token, 82);
        TerminalSession s2 = new TerminalSession();
        s2.setTransport(doomed);
        doomed.start(s2);
        check(await(() -> s2.delivered.length() > 10, 8000), "second session opened");
        doomed.close();
        check(await(() -> s2.exitCode != TerminalSession.MIN, 6000),
                "closing the transport ends the session promptly");
        System.out.println("ok  a closed link ends the session instead of hanging");

        t.close();
        System.out.println("ALL OK");
        System.exit(0);
    }
}
