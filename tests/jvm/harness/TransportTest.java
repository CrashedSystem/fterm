import com.termux.terminal.SocketTransport;
import com.termux.terminal.TerminalSession;

/** Drives the real SocketTransport against a live ftermd. */
public class TransportTest {

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    static boolean await(java.util.function.BooleanSupplier c, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (c.getAsBoolean()) return true;
            Thread.sleep(20);
        }
        return c.getAsBoolean();
    }

    /** setTransport() + start() is exactly what initializeEmulator() does. */
    static TerminalSession open(String token, int port, int id) {
        TerminalSession s = new TerminalSession();
        SocketTransport t = new SocketTransport("127.0.0.1", port, token, id);
        s.setTransport(t);
        t.start(s);
        return s;
    }

    /** As {@link #open}, with an explicit shell and starting directory. */
    static TerminalSession openDir(String token, int port, int id, String shell, String cwd) {
        TerminalSession s = new TerminalSession();
        SocketTransport t = new SocketTransport("127.0.0.1", port, token, id, shell, cwd);
        s.setTransport(t);
        t.start(s);
        return s;
    }

    static SocketTransport transportOf(TerminalSession s) {
        return s.transport();
    }

    public static void main(String[] args) throws Exception {
        String token = args[0];
        int port = Integer.parseInt(args[1]);

        // --- bad token is reported, and no output is delivered
        TerminalSession bad = open("wrong", port, 61);
        check(await(() -> bad.exitCode != Integer.MIN_VALUE, 4000), "bad token: exit not reported");
        String err = transportOf(bad).getLastError();
        check(err != null && err.contains("토큰"),
                "bad token: expected a token error, got " + err);
        check(bad.delivered.indexOf("$") < 0, "bad token: leaked shell output");
        System.out.println("ok  bad token rejected: " + err);

        // --- good token
        TerminalSession s = open(token, port, 62);
        check(await(() -> s.delivered.indexOf("$") >= 0 || s.delivered.indexOf("#") >= 0, 8000),
                "open: no prompt, got: " + s.delivered);
        System.out.println("ok  open -> prompt (" + s.delivered.length() + " chars)");

        SocketTransport t = transportOf(s);
        byte[] cmd = "echo JAVA-$((6*7))\n".getBytes("UTF-8");
        t.write(cmd, 0, cmd.length);
        check(await(() -> s.delivered.indexOf("JAVA-42") >= 0, 6000),
                "echo: no JAVA-42, got: " + s.delivered);
        System.out.println("ok  input round-trip through the Java transport");

        byte[] hb = "echo 한글테스트\n".getBytes("UTF-8");
        t.write(hb, 0, hb.length);
        check(await(() -> s.delivered.indexOf("한글테스트") >= 0, 6000), "hangul missing");
        System.out.println("ok  hangul survives the framing");

        t.resize(41, 13, 10, 20);
        byte[] sz = "stty size\n".getBytes("UTF-8");
        t.write(sz, 0, sz.length);
        check(await(() -> s.delivered.indexOf("13 41") >= 0, 6000),
                "resize not applied: " + s.delivered);
        System.out.println("ok  resize reaches the pty");

        check(await(() -> t.getPid() > 0, 3000), "pid not received");
        System.out.println("ok  pid reported: " + t.getPid());

        // --- a second client on the same session id gets the ring replay
        TerminalSession s2 = open(token, port, 62);
        check(await(() -> s2.delivered.indexOf("JAVA-42") >= 0, 8000),
                "replay missing on reconnect: " + s2.delivered);
        System.out.println("ok  reconnect replays scrollback (" + s2.delivered.length() + " chars)");

        // --- close ends the session
        SocketTransport t2 = transportOf(s2);
        t2.close();
        check(await(() -> s2.exitCode != Integer.MIN_VALUE, 4000), "close: no exit");
        System.out.println("ok  close finishes the session");

        // --- two independent sessions do not cross bytes
        TerminalSession a = open(token, port, 63);
        TerminalSession b = open(token, port, 64);
        SocketTransport ta = transportOf(a);
        SocketTransport tb = transportOf(b);
        await(() -> a.delivered.length() > 20 && b.delivered.length() > 20, 8000);
        byte[] ma = "echo AAA-$((1+1))\n".getBytes("UTF-8");
        byte[] mb = "echo BBB-$((2+2))\n".getBytes("UTF-8");
        ta.write(ma, 0, ma.length);
        tb.write(mb, 0, mb.length);
        check(await(() -> a.delivered.indexOf("AAA-2") >= 0, 6000), "session A missing its echo");
        check(await(() -> b.delivered.indexOf("BBB-4") >= 0, 6000), "session B missing its echo");
        check(a.delivered.indexOf("BBB-4") < 0 && b.delivered.indexOf("AAA-2") < 0,
                "sessions crossed bytes");
        System.out.println("ok  two sessions stay separate");
        ta.close();
        tb.close();

        // --- the OPEN frame's optional tail: a directory and a shell
        TerminalSession d = openDir(token, port, 65, "", "/data/data/com.termux/files/usr");
        SocketTransport td = transportOf(d);
        check(await(() -> d.delivered.indexOf("$") >= 0 || d.delivered.indexOf("#") >= 0, 8000),
                "cwd session: no prompt, got: " + d.delivered);
        byte[] pw = "echo PWD-$(pwd)\n".getBytes("UTF-8");
        td.write(pw, 0, pw.length);
        check(await(() -> d.delivered.indexOf("PWD-/data/data/com.termux/files/usr") >= 0, 6000),
                "start directory not applied: " + d.delivered);
        System.out.println("ok  OPEN carries a starting directory");
        td.close();

        TerminalSession sh = openDir(token, port, 66, "/data/data/com.termux/files/usr/bin/sh", "");
        SocketTransport tsh = transportOf(sh);
        check(await(() -> sh.delivered.indexOf("$") >= 0 || sh.delivered.indexOf("#") >= 0, 8000),
                "shell session: no prompt, got: " + sh.delivered);
        byte[] zero = "echo ZERO-$0\n".getBytes("UTF-8");
        tsh.write(zero, 0, zero.length);
        check(await(() -> sh.delivered.indexOf("ZERO-") >= 0, 6000),
                "shell prompt missing: " + sh.delivered);
        check(sh.delivered.indexOf("ZERO-/data/data/com.termux/files/usr/bin/bash") < 0,
                "requested shell was ignored: " + sh.delivered);
        System.out.println("ok  OPEN carries the shell");
        tsh.close();

        System.out.println("ALL OK");
        System.exit(0);
    }
}
