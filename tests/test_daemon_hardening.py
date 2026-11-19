"""The three daemon defects the Rust daemon exists to remove, tested through the protocol.

  python3 tests/test_daemon_hardening.py <daemon command...>

Each check is run against a daemon of its own (own rundir, own port), because each of them either
starts or kills things:

  A. A pidfile left by a crash must not stop the daemon from starting. The stale pid here is a
     live `sleep`, which is the case that used to print "already running" forever: `kill(pid, 0)`
     says alive, so the old daemon believed itself running and exited.
  B. A client that stops reading must lose its own connection, not take the daemon's memory -
     and with it every tab's shell - with it.
  C. One tab whose program stops reading its terminal must not stop another tab.

The wire-protocol suites judge any daemon; this one pins the hardening guarantees the shipped
implementation must have.
"""
import json
import os
import shutil
import socket
import struct
import subprocess
import sys
import tempfile
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
fails = []


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else "  <- " + detail))
    if not cond:
        fails.append(name)


def tc():
    sys.path.insert(0, os.path.join(ROOT, "termux"))
    import test_client
    return test_client


class Sandbox:
    """A daemon of its own: own FTERM_RUNDIR, own port, started and stopped by the test."""

    def __init__(self, cmd, port):
        self.cmd = cmd
        self.port = port
        self.dir = tempfile.mkdtemp(prefix="fterm-hard-")
        self.log = os.path.join(self.dir, "daemon.log")
        self.proc = None
        self.env = dict(os.environ)
        self.env.update(
            FTERM_RUNDIR=self.dir,
            FTERM_PORT=str(port),
            FTERM_SHELL="/bin/bash" if os.path.exists("/bin/bash") else "/bin/sh",
        )

    def run(self, args, timeout=15):
        return subprocess.run(
            self.cmd + args,
            env=self.env,
            capture_output=True,
            text=True,
            timeout=timeout,
        )

    def start(self, wait=8.0):
        self.proc = subprocess.Popen(
            self.cmd + ["start"],
            env=self.env,
            stdout=open(self.log, "w"),
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
        end = time.time() + wait
        while time.time() < end:
            if self.status().get("reachable"):
                return True
            time.sleep(0.1)
        return False

    def status(self):
        r = self.run(["status"])
        try:
            return json.loads(r.stdout.strip().splitlines()[-1])
        except Exception:
            return {}

    def stop(self):
        self.run(["stop"], timeout=15)
        if self.proc is not None:
            try:
                self.proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.proc.kill()
                self.proc.wait(timeout=5)
        shutil.rmtree(self.dir, ignore_errors=True)


def hello(tcmod, sandbox):
    """A connected, authenticated client on this sandbox's daemon."""
    tcmod.PORT = sandbox.port
    tcmod.TOKEN = os.path.join(sandbox.dir, "token")
    c = tcmod.Conn()
    frames = c.hello()
    assert any(t == tcmod.S_HELLO_OK for t, _ in frames), [t for t, _ in frames]
    return c


def wait_output(tcmod, conn, sid, command, marker, deadline=4.0):
    """Send a command and time how long the marker takes to arrive.

    `tcmod.run` collects for a fixed window, so it cannot answer "how long did this take"; this
    stops as soon as the marker is in the output, which is the property under test.
    """
    b = command.encode()
    conn.frame(tcmod.C_INPUT, struct.pack("<H", sid) + struct.pack("<I", len(b) + 6) + b)
    start = time.time()
    text = b""
    while True:
        left = deadline - (time.time() - start)
        if left <= 0:
            return None, text
        try:
            typ, pay = conn.recv_frame(left)
        except socket.timeout:
            return None, text
        if typ is None:
            return None, text
        if typ == tcmod.S_OUTPUT:
            _, ln = struct.unpack_from("<HI", pay, 0)
            text += pay[6:6 + ln]
            if marker in text:
                return time.time() - start, text


def rss_kb(pid):
    try:
        with open("/proc/%d/status" % pid) as f:
            for line in f:
                if line.startswith("VmRSS:"):
                    return int(line.split()[1])
    except OSError:
        pass
    return -1


# ---------------------------------------------------------------- A: stale pidfile

def check_stale_pidfile(cmd):
    print("\n=== A. a pidfile that names some other process")
    box = Sandbox(cmd, 8851)
    squatter = subprocess.Popen(["sleep", "120"])
    try:
        with open(os.path.join(box.dir, "ftermd.pid"), "w") as f:
            f.write(str(squatter.pid))
        ok = box.start(wait=8.0)
        st = box.status()
        check("the daemon starts despite a stale pidfile", ok,
              "status=%s log=%s" % (st, open(box.log).read()[-400:] if os.path.exists(box.log) else ""))
        check("and it is the pid in the pidfile now, not the squatter's",
              st.get("pid") not in (0, squatter.pid), repr(st))
        check("the squatter is untouched", squatter.poll() is None)

        # And the check still works the other way: a second daemon is refused while the first
        # is genuinely running.
        second = box.run(["start"], timeout=15)
        check("a second daemon is refused with \"already running\"",
              second.returncode != 0 and "already running" in second.stdout,
              "rc=%s out=%r" % (second.returncode, second.stdout[-200:]))
    finally:
        box.stop()
        squatter.kill()
        squatter.wait()


# ---------------------------------------------------------------- B: slow client

def check_slow_client(cmd):
    print("\n=== B. a client that stops reading")
    box = Sandbox(cmd, 8852)
    tcmod = tc()
    try:
        if not box.start():
            check("the daemon starts", False, open(box.log).read()[-400:])
            return
        slow = hello(tcmod, box)
        sid_slow = 921
        slow.frame(tcmod.C_OPEN, struct.pack("<HHH", sid_slow, 100, 30))
        assert any(t == tcmod.S_OPENED for t, _ in slow.collect(1.5)), "no OPENED"
        # Flood the session, then never read the socket again: this is the app going to the
        # background while `cat` is still printing.
        slow.frame(tcmod.C_INPUT,
                   struct.pack("<H", sid_slow) + struct.pack("<I", 6 + 6) + b"yes\n")
        time.sleep(0.3)

        # A second client must keep working while the first is being dropped.
        fast = hello(tcmod, box)
        sid_fast = 922
        fast.frame(tcmod.C_OPEN, struct.pack("<HHH", sid_fast, 80, 24))
        assert any(t == tcmod.S_OPENED for t, _ in fast.collect(1.5)), "no OPENED for the second client"
        elapsed, out = wait_output(tcmod, fast, sid_fast, "echo STILL-HERE\n", b"STILL-HERE")
        check("another client still gets answers while one is flooded",
              elapsed is not None, (elapsed, out[-200:]))

        # Give the daemon time to notice the cap and drop the slow one.
        time.sleep(4.0)
        pid = box.status().get("pid", 0)
        rss = rss_kb(pid)
        check("the daemon did not grow without bound (VmRSS %d kB)" % rss,
              0 < rss < 200 * 1024, "%d kB" % rss)

        # The flooded session itself must still exist: only the connection went.
        ids = tcmod.sync_ids(fast) or []
        check("the flooded session is still alive for a reconnect", sid_slow in ids, ids)

        # Stop the flood, then reattach: the ring is what a reconnecting app gets.
        slow.s.close()
        fast.frame(tcmod.C_INPUT,
                   struct.pack("<H", sid_slow) + struct.pack("<I", 6 + 1) + b"\x03")
        time.sleep(0.5)
        re = hello(tcmod, box)
        re.frame(tcmod.C_OPEN, struct.pack("<HHH", sid_slow, 100, 30))
        frames = re.collect(2.0)
        check("a reconnecting client is replayed into the flooded session",
              any(t == tcmod.S_REPLAY for t, _ in frames), [t for t, _ in frames])
        re.frame(tcmod.C_CLOSE, struct.pack("<H", sid_slow))
        fast.frame(tcmod.C_CLOSE, struct.pack("<H", sid_fast))
        time.sleep(0.3)
        re.close()
        fast.close()
    finally:
        box.stop()


# ---------------------------------------------------------------- C: one blocked tab

def check_blocked_tab(cmd):
    print("\n=== C. a tab whose program is not reading")
    box = Sandbox(cmd, 8853)
    tcmod = tc()
    try:
        if not box.start():
            check("the daemon starts", False, open(box.log).read()[-400:])
            return
        blocked = hello(tcmod, box)
        sid_blocked = 931
        blocked.frame(tcmod.C_OPEN, struct.pack("<HHH", sid_blocked, 100, 30))
        assert any(t == tcmod.S_OPENED for t, _ in blocked.collect(1.5)), "no OPENED"
        # `sleep` reads nothing, so the pty's input buffer fills and a plain write blocks.
        tcmod.run(blocked, sid_blocked, "sleep 120\n", 0.6)

        # Push far more than a pty input buffer holds. The daemon's write for *this* session is
        # now stuck inside the kernel.
        chunk = b"x" * (512 * 1024)
        for _ in range(6):
            blocked.frame(tcmod.C_INPUT,
                          struct.pack("<H", sid_blocked)
                          + struct.pack("<I", len(chunk) + 6) + chunk)
        time.sleep(1.0)

        # Another tab must still answer.
        other = hello(tcmod, box)
        sid_other = 932
        other.frame(tcmod.C_OPEN, struct.pack("<HHH", sid_other, 80, 24))
        assert any(t == tcmod.S_OPENED for t, _ in other.collect(1.5)), "no OPENED for the other tab"
        elapsed, out = wait_output(tcmod, other, sid_other, "echo OTHER-TAB-OK\n", b"OTHER-TAB-OK")
        check("a tab blocked on write does not stall another tab",
              elapsed is not None, (elapsed, out[-200:]))
        check("and it answered promptly rather than after a stall",
              elapsed is not None and elapsed < 2.0,
              "%s" % (("%.2fs" % elapsed) if elapsed else "no answer"))

        # Ctrl-C on the sleeping tab, then drop both.
        blocked.frame(tcmod.C_INPUT,
                      struct.pack("<H", sid_blocked) + struct.pack("<I", 6 + 1) + b"\x03")
        time.sleep(0.3)
        blocked.frame(tcmod.C_CLOSE, struct.pack("<H", sid_blocked))
        other.frame(tcmod.C_CLOSE, struct.pack("<H", sid_other))
        time.sleep(0.3)
        blocked.close()
        other.close()
        check("the daemon is still reachable at the end", bool(box.status().get("reachable")),
              repr(box.status()))
    finally:
        box.stop()


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    cmd = argv[1:]
    check_stale_pidfile(cmd)
    check_slow_client(cmd)
    check_blocked_tab(cmd)
    print("\ntest_daemon_hardening: %d failed" % len(fails))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
