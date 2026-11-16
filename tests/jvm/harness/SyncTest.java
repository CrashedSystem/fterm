import com.termux.terminal.DaemonClient;
import com.termux.terminal.SocketTransport;
import com.termux.terminal.TerminalSession;

import java.util.List;

/** DaemonClient against a live ftermd: what is alive, and what is not. */
public class SyncTest {

    static void check(boolean c, String m) {
        if (!c) throw new AssertionError(m);
    }

    static TerminalSession open(String token, int port, int id) {
        TerminalSession s = new TerminalSession();
        SocketTransport t = new SocketTransport("127.0.0.1", port, token, id);
        s.setTransport(t);
        t.start(s);
        return s;
    }

    public static void main(String[] args) throws Exception {
        String token = args[0];
        int port = Integer.parseInt(args[1]);

        // Whatever the daemon has right now is the baseline. On a shared daemon it may
        // already hold shells, so the test must not assume an empty daemon.
        List<Integer> before = DaemonClient.liveSessions("127.0.0.1", port, token, 2500);
        System.out.println("ok  baseline sessions: " + before);

        // One session, then SYNC must list it. This is the path that lets a relaunched
        // app reattach instead of orphaning the shell.
        TerminalSession a = open(token, port, 71);
        TerminalSession b = open(token, port, 72);
        long end = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < end
                && (a.delivered.length() == 0 || b.delivered.length() == 0)) {
            Thread.sleep(50);
        }
        List<Integer> during = DaemonClient.liveSessions("127.0.0.1", port, token, 2500);
        check(during.contains(71) && during.contains(72),
                "SYNC should list 71 and 72, got " + during);
        System.out.println("ok  SYNC lists the live sessions: " + during);

        // Wrong token fails loudly.
        try {
            DaemonClient.liveSessions("127.0.0.1", port, "nope", 2500);
            throw new AssertionError("bad token must throw");
        } catch (DaemonClient.Failure f) {
            check(f.getMessage().contains("토큰"), "unexpected message: " + f.getMessage());
            System.out.println("ok  bad token surfaces: " + f.getMessage());
        }

        // Dead port fails without hanging.
        long t0 = System.currentTimeMillis();
        try {
            DaemonClient.liveSessions("127.0.0.1", 1, token, 800);
            throw new AssertionError("closed port must throw");
        } catch (DaemonClient.Failure f) {
            long ms = System.currentTimeMillis() - t0;
            check(ms < 3000, "connect attempt took " + ms + "ms");
            System.out.println("ok  refused port fails in " + ms + "ms: " + f.getMessage());
        }

        // After closing, SYNC must forget them.
        a.transport().close();
        b.transport().close();
        Thread.sleep(400);
        List<Integer> after = DaemonClient.liveSessions("127.0.0.1", port, token, 2500);
        check(!after.contains(71) && !after.contains(72),
                "closed sessions still listed: " + after);
        for (int id : before) check(after.contains(id), "baseline session " + id + " was disturbed");
        System.out.println("ok  closed sessions leave SYNC: " + after);

        System.out.println("ALL OK");
        System.exit(0);
    }
}
