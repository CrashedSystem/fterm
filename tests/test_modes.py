"""Checks for the daemon's terminal mode tracker, over the wire.

  python3 test_modes.py
      Talks to a daemon that is already running (FTERM_PORT / FTERM_RUNDIR, the same
      environment `termux/test_client.py` uses) and makes the tracker's claims through the
      protocol: it asks a real shell to print the declarations, splits them across reads,
      buries them past the ring, and checks what a reattaching client is told. It does not
      read the daemon's source, so it judges any implementation of the protocol - which is
      the point. The unit-level claims live next to the implementation now
      (termux/ftermd-rs/src/modes.rs, `cargo test`).

Prints PASS/FAIL lines and exits non-zero if anything failed.
"""
import os
import re
import struct
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Declarations to look for, as they travel in both directions.
MOUSE = b"\x1b[?1000h"
SGR = b"\x1b[?1006h"
ALT = b"\x1b[?1049h"
PASTE = b"\x1b[?2004h"
FOCUS = b"\x1b[?1004h"


class Checks:
    def __init__(self):
        self.fails = []

    def check(self, name, cond, detail=""):
        print(("PASS " if cond else "FAIL ") + name + ("" if cond else "  <- " + detail))
        if not cond:
            self.fails.append(name)

    def skip(self, name, why):
        print("SKIP " + name + "  (" + why + ")")


# ---------------------------------------------------------------- wire mode

def run_wire():
    """The same claims as the unit mode, made through a live daemon.

    A declaration that a program prints once has to survive three things a unit test cannot
    model: the shell's own chunking of its output, the daemon's read boundaries on a real
    pty, and the ring eviction that happens before the app reconnects. So each case prints
    the declaration from a shell, then reattaches and inspects what a new client is told.
    """
    c = Checks()
    check = c.check
    tc = _tc()

    # W0: the daemon is there and the token works. Everything below would otherwise report a
    # misleading failure deep inside a case.
    c0 = tc.Conn()
    hello = c0.hello()
    if not any(t == tc.S_HELLO_OK for t, _ in hello):
        raise SystemExit("wire: daemon did not accept the token: %r" % ([t for t, _ in hello],))
    c0.close()

    # W1: a declaration split across two reads still survives. The shell prints the first
    # half, waits (so the daemon reads it as one chunk), then prints the rest. `set +m` keeps
    # bash from announcing finished background jobs between the halves: this device's .bashrc
    # launches one, and its plain-text "[1]- Done ..." between the halves is a third read that
    # kills the sequence - on any implementation, because a tracker that held a partial
    # sequence across unrelated prose would wrongly replay fragments of prose as modes.
    sid = 601
    conn = tc.Conn()
    conn.hello()
    _open(tc, conn, sid)
    _type(tc, conn, sid, "set +m; printf '\\033[?10'; sleep 0.3; printf '00h'\n", 1.5)
    conn.close()
    time.sleep(0.3)
    c2, _, pre, rep = _attach(tc, sid)
    check("wire: a declaration split across two reads is restored", MOUSE in pre, repr(pre[:120]))
    # The two halves were one declaration to the program but two reads to the daemon. A carry
    # that re-fed bytes (or a tracker that parsed each half alone) would show up here as a
    # repeat or as nothing at all.
    check("wire: and it is restored exactly once, not once per read",
          pre.count(MOUSE) == 1, "%d copies in %r" % (pre.count(MOUSE), pre[:200]))
    _close_quietly(tc, c2, sid)

    # W2: a combined declaration sets every parameter, and the alt screen is restored last so
    # nothing after it can clear the screen the ring redraws.
    sid = 602
    conn = tc.Conn()
    conn.hello()
    _open(tc, conn, sid)
    _type(tc, conn, sid, "printf '\\033[?1000;1006;1049h'\n", 1.2)
    conn.close()
    time.sleep(0.3)
    c2, _, pre, rep = _attach(tc, sid)
    check("wire: a combined DECSET restores every parameter",
          MOUSE in pre and SGR in pre and ALT in pre, repr(pre[:120]))
    check("wire: the alt screen is the last thing restored",
          pre.rfind(b"1049h") > max(pre.rfind(b"1000h"), pre.rfind(b"1006h")) if pre else False,
          repr(pre[:120]))
    _close_quietly(tc, c2, sid)

    # W3: a private sequence the tracker does not know is not echoed back as a mode.
    sid = 603
    conn = tc.Conn()
    conn.hello()
    _open(tc, conn, sid)
    _type(tc, conn, sid, "printf '\\033[?1234h\\033[?2026h'\n", 1.2)
    conn.close()
    time.sleep(0.3)
    c2, _, pre, rep = _attach(tc, sid)
    check("wire: an untracked private sequence is not restored",
          b"1234h" not in pre and b"2026h" not in pre, repr(pre[:120]))
    _close_quietly(tc, c2, sid)

    # W4: bracketed paste survives a reconnect. Without it a reattached client pastes a shell
    # command without the guards that keep the shell from running it line by line.
    sid = 604
    conn = tc.Conn()
    conn.hello()
    _open(tc, conn, sid)
    _type(tc, conn, sid, "printf '\\033[?2004h'\n", 1.2)
    conn.close()
    time.sleep(0.3)
    c2, _, pre, _ = _attach(tc, sid)
    check("wire: 2004 (bracketed paste) is restored", PASTE in pre, repr(pre[:120]))
    _close_quietly(tc, c2, sid)

    # W5: a mode the program cleared is not restored. Mouse reporting off is a decision, not
    # a gap in the tracker's knowledge.
    sid = 605
    conn = tc.Conn()
    conn.hello()
    _open(tc, conn, sid)
    _type(tc, conn, sid, "printf '\\033[?1000h\\033[?1006h'\n", 1.0)
    _type(tc, conn, sid, "printf '\\033[?1000l'\n", 1.0)
    conn.close()
    time.sleep(0.3)
    c2, _, pre, _ = _attach(tc, sid)
    check("wire: a cleared mode stays cleared", MOUSE not in pre, repr(pre[:120]))
    check("wire: but the mode the program kept is still restored", SGR in pre, repr(pre[:120]))
    _close_quietly(tc, c2, sid)

    # W6: the case the whole tracker exists for - the declaration has scrolled out of the ring
    # by the time the app's process is recreated.
    sid = 606
    conn = tc.Conn()
    conn.hello()
    _open(tc, conn, sid)
    _type(tc, conn, sid, "printf '\\033[?1000h\\033[?1006h'\n", 1.0)
    _type(tc, conn, sid, "head -c 320000 /dev/zero | tr '\\\\0' x\n", 6.0)
    conn.close()
    time.sleep(0.3)
    c2, _, pre, rep = _attach(tc, sid, seconds=3.0)
    check("wire: the ring has lost the declaration", MOUSE not in rep,
          "replayed %d bytes" % len(rep))
    check("wire: the modes still arrive ahead of the ring", MOUSE in pre and SGR in pre,
          repr(pre[:120]))
    # A full screen program is running, so the daemon also asks it to repaint; the frames for
    # that come after the ring, and the daemon must still be answering frames afterwards.
    check("wire: the daemon still answers after a redraw", tc.sync_ids(c2) is not None)
    _close_quietly(tc, c2, sid)

    # W7: broken output with an unterminated parameter run must not poison the tracker. The
    # daemon holds at most MAX_PENDING bytes of a partial sequence, so a program that prints
    # 20 KB of digits after ESC[? must not stop the *next* declaration from being tracked.
    sid = 607
    conn = tc.Conn()
    conn.hello()
    _open(tc, conn, sid)
    _type(tc, conn, sid,
          "python3 -c \"print('\\\\033[?' + '1'*20000, end='')\"\n", 2.0)
    _type(tc, conn, sid, "printf '\\033[?1004h'\n", 1.2)
    conn.close()
    time.sleep(0.3)
    c2, _, pre, _ = _attach(tc, sid)
    check("wire: an unterminated parameter run does not eat the next declaration",
          FOCUS in pre, repr(pre[:120]))
    _close_quietly(tc, c2, sid)

    # W8: nothing here was typed by the client, so any mode a reattached client receives was
    # declared by the shell on its own. bash turns on bracketed paste itself, and that is the
    # point: the tracker follows the stream, not the client's requests.
    sid = 608
    conn = tc.Conn()
    conn.hello()
    _open(tc, conn, sid)
    _type(tc, conn, sid, "echo plain\n", 1.0)
    conn.close()
    time.sleep(0.3)
    c2, _, pre, rep = _attach(tc, sid)
    check("wire: a session's output still replays", b"plain" in rep, repr(rep[-80:]))
    if PASTE in rep:
        check("wire: a mode the shell declared itself is restored ahead of the ring",
              PASTE in pre, repr(pre[:120]))
    else:
        c.skip("wire: a mode the shell declared itself is restored ahead of the ring",
               "this shell turns on no mode of its own")
    _close_quietly(tc, c2, sid)

    print("test_modes: %d failed" % len(c.fails))
    return c.fails


def _tc():
    """The protocol client from termux/test_client.py, reused rather than re-written."""
    sys.path.insert(0, os.path.join(ROOT, "termux"))
    import test_client
    return test_client


def _split_pre_replay(tc, frames):
    """(output before the replayed ring, the replayed ring).

    The modes are a property of the running program, so the daemon sends them ahead of the
    ring: anything a reattaching client sees before REPLAY is the restore, and nothing in the
    ring can be mistaken for it.
    """
    pre, rep = b"", b""
    in_replay = False
    for typ, pay in frames:
        if typ == tc.S_REPLAY:
            in_replay = True
            rep += pay[2:]
        elif typ == tc.S_OUTPUT and not in_replay:
            _, ln = struct.unpack_from("<HI", pay, 0)
            pre += pay[6:6 + ln]
    return pre, rep


def _open(tc, conn, sid, cols=100, rows=30):
    conn.frame(tc.C_OPEN, struct.pack("<HHH", sid, cols, rows))
    frames = conn.collect(1.5)
    if not any(t == tc.S_OPENED for t, _ in frames):
        raise SystemExit("wire: no OPENED for sid %d: %r" % (sid, [t for t, _ in frames]))


def _attach(tc, sid, cols=100, rows=30, seconds=2.0):
    """A fresh connection attaching to an existing session: (frames, pre-replay, ring)."""
    c = tc.Conn()
    c.hello()
    c.frame(tc.C_OPEN, struct.pack("<HHH", sid, cols, rows))
    frames = c.collect(seconds)
    pre, rep = _split_pre_replay(tc, frames)
    return c, frames, pre, rep


def _type(tc, conn, sid, command, seconds=1.0):
    return tc.run(conn, sid, command, seconds)


def _close_quietly(tc, conn, sid):
    try:
        conn.frame(tc.C_CLOSE, struct.pack("<H", sid))
        time.sleep(0.25)
    except OSError:
        pass
    conn.close()


def main(argv):
    fails = run_wire()
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
