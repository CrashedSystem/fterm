#!/data/data/com.termux/files/usr/bin/env python3
"""ftermd 프로토콜 검증 클라이언트.

  python3 test_client.py            기본 시나리오 전부
  python3 test_client.py shell      대화형

검증 항목
1. 토큰 없이 붙으면 ERROR+연결 종료, 바이트 유출 없음
2. HELLO 후 OPEN, 셸 배너 수신
3. INPUT 에코 왕복, 한글 UTF-8 왕복
4. RESIZE 후 `stty size`가 새 크기 보고
5. 재접속 REPLAY에 이전 출력이 들어 있음
6. CLOSE 후 세션 소멸, SYNC 목록에서 빠짐
"""
import os
import socket
import struct
import sys
import tempfile
import time

HOST = "127.0.0.1"
PORT = int(os.environ.get("FTERM_PORT", "8791"))
TOKEN = os.path.expanduser(os.environ.get("FTERM_RUNDIR", "~/.fterm") + "/token")

C_HELLO, C_SYNC, C_OPEN, C_CLOSE, C_INPUT, C_RESIZE = 0x01, 0x09, 0x10, 0x11, 0x12, 0x13
S_HELLO_OK, S_OPENED, S_OUTPUT, S_EXIT, S_REPLAY, S_SYNCED, S_ERROR = (
    0x80, 0x90, 0x91, 0x92, 0x93, 0x9E, 0x9F)


def token():
    with open(TOKEN) as f:
        return f.read().strip()


class Conn:
    def __init__(self):
        self.s = socket.create_connection((HOST, PORT), 3.0)
        self.s.settimeout(3.0)
        self.buf = b""

    def frame(self, typ, payload=b""):
        self.s.sendall(struct.pack("<BI", typ, len(payload)) + payload)

    def recv_frame(self, timeout=3.0):
        self.s.settimeout(timeout)
        while True:
            if len(self.buf) >= 5:
                typ, ln = struct.unpack_from("<BI", self.buf, 0)
                if len(self.buf) >= 5 + ln:
                    pay = self.buf[5:5 + ln]
                    self.buf = self.buf[5 + ln:]
                    return typ, pay
            d = self.s.recv(65536)
            if not d:
                return None, None
            self.buf += d

    def collect(self, seconds=0.6):
        """주어진 시간 동안 오는 프레임을 모아 (typ, payload) 리스트로."""
        out = []
        end = time.time() + seconds
        while time.time() < end:
            try:
                typ, pay = self.recv_frame(max(0.05, end - time.time()))
            except socket.timeout:
                break
            if typ is None:
                break
            out.append((typ, pay))
        return out

    def hello(self, tok=None):
        tok = (tok if tok is not None else token()).encode()
        self.frame(C_HELLO, bytes([1]) + struct.pack("<H", len(tok)) + tok)
        return self.collect(1.0)

    def close(self):
        try:
            self.s.close()
        except OSError:
            pass


def err_code(frames):
    for typ, pay in frames:
        if typ == S_ERROR:
            return pay[0]
    return None


def test_auth():
    c = Conn()
    frames = c.hello("wrong-token")
    code = err_code(frames)
    got = [t for t, _ in frames]
    c.close()
    assert code == 3, ("bad token must be rejected with E_BAD_TOKEN(3)", frames)
    assert S_OUTPUT not in got, ("no output before auth", got)
    print("ok  auth rejects bad token, no bytes leaked")


def test_open_and_echo():
    c = Conn()
    f = c.hello()
    assert err_code(f) is None, f
    assert any(t == S_HELLO_OK for t, _ in f), f
    sid = free_sid(c)
    c.frame(C_OPEN, struct.pack("<HHH", sid, 80, 24))
    f = c.collect(1.5)
    opened = [(t, p) for t, p in f if t == S_OPENED]
    assert opened, ("no OPENED", [t for t, _ in f], err_code(f))
    got_sid, pid = struct.unpack_from("<Hi", opened[0][1], 0)
    assert got_sid == sid and pid > 0, (got_sid, pid)

    c.frame(C_INPUT, struct.pack("<H", sid) + struct.pack("<I", 22) +
            b"echo MARKER-$((40+2))\n")
    text = b""
    for typ, pay in c.collect(2.0):
        if typ == S_OUTPUT:
            s, ln = struct.unpack_from("<HI", pay, 0)
            text += pay[6:6 + ln]
    assert b"MARKER-42" in text, text[-400:]
    print("ok  open -> shell, echo round-trip (pid %d)" % pid)
    return c, sid


def test_hangul(c, sid):
    msg = "한글출력".encode()
    c.frame(C_INPUT, struct.pack("<H", sid) + struct.pack("<I", len(msg) + 6) +
            msg)
    time.sleep(0.2)
    c.frame(C_INPUT, struct.pack("<H", sid) + struct.pack("<I", 1) + b"\r")
    text = b""
    for typ, pay in c.collect(1.5):
        if typ == S_OUTPUT:
            s, ln = struct.unpack_from("<HI", pay, 0)
            text += pay[6:6 + ln]
    assert "한글출력".encode() in text, text[-300:]
    print("ok  hangul utf-8 round-trip")


def test_resize(c, sid):
    c.frame(C_RESIZE, struct.pack("<HHH", sid, 41, 13))
    time.sleep(0.2)
    cmd = b"stty size\n"
    c.frame(C_INPUT, struct.pack("<H", sid) + struct.pack("<I", len(cmd) + 6) + cmd)
    text = b""
    for typ, pay in c.collect(1.5):
        if typ == S_OUTPUT:
            s, ln = struct.unpack_from("<HI", pay, 0)
            text += pay[6:6 + ln]
    assert b"13 41" in text, text[-300:]
    print("ok  resize reaches the pty (stty says 13 41)")


def test_replay(c, sid):
    c.frame(C_INPUT, struct.pack("<H", sid) + struct.pack("<I", 6 + 12) +
            b"echo RPLY-99\n")
    c.collect(0.8)
    c.close()
    time.sleep(0.3)
    c2 = Conn()
    c2.hello()
    c2.frame(C_OPEN, struct.pack("<HHH", sid, 41, 13))
    frames = c2.collect(1.5)
    replays = [(t, p) for t, p in frames if t == S_REPLAY]
    assert replays, [t for t, _ in frames]
    blob = b"".join(p[2:] for _, p in replays)
    assert b"RPLY-99" in blob, blob[-300:]
    print("ok  reconnect replays scrollback (%d bytes)" % len(blob))
    return c2


def free_sid(c):
    """An unused session id.

    OPEN on an id that already exists attaches to that session instead of spawning one, so
    a test that hardcodes "1" types its commands into whatever the app is running when the
    app happens to own id 1. That is exactly the flake this replaces: the tests below run
    against the same daemon the phone's tabs use.
    """
    live = set(sync_ids(c) or [])
    sid = 200
    while sid in live:
        sid += 1
    return sid


def sync_ids(c):
    c.frame(C_SYNC)
    for typ, pay in c.collect(1.0):
        if typ == S_SYNCED:
            n = struct.unpack_from("<H", pay, 0)[0]
            return [struct.unpack_from("<H", pay, 2 + i * 2)[0] for i in range(n)]
    return None


def test_close_and_sync(c, sid):
    # A daemon is shared with the app's tabs, so the check is "this id is gone",
    # not "the daemon is empty".
    before = sync_ids(c)
    c.frame(C_CLOSE, struct.pack("<H", sid))
    time.sleep(0.4)
    after = sync_ids(c)
    assert after is not None, "no SYNCED reply"
    assert sid not in after, ("session %d must be gone after CLOSE" % sid, after)
    print("ok  close kills the session (SYNC before %s, after %s)" % (before, after))


def interactive():
    c = Conn()
    print("hello:", [t for t, _ in c.hello()])
    c.frame(C_OPEN, struct.pack("<HHH", 7, 80, 24))
    import threading
    def pump():
        while True:
            typ, pay = c.recv_frame(60)
            if typ is None:
                print("[closed]")
                return
            if typ == S_OUTPUT:
                s, ln = struct.unpack_from("<HI", pay, 0)
                sys.stdout.write(pay[6:6 + ln].decode("utf-8", "replace"))
                sys.stdout.flush()
            elif typ == S_ERROR:
                print("[ERROR]", pay)
    threading.Thread(target=pump, daemon=True).start()
    while True:
        line = sys.stdin.readline()
        if not line:
            break
        b = line.encode()
        c.frame(C_INPUT, struct.pack("<H", 7) + struct.pack("<I", len(b) + 6) + b)


def read_output(c, seconds=1.5):
    text = b""
    for typ, pay in c.collect(seconds):
        if typ == S_OUTPUT:
            _, ln = struct.unpack_from("<HI", pay, 0)
            text += pay[6:6 + ln]
    return text


def run(c, sid, command, seconds=1.5):
    b = command.encode()
    c.frame(C_INPUT, struct.pack("<H", sid) + struct.pack("<I", len(b) + 6) + b)
    return read_output(c, seconds)


def open_strings_paths():
    """A shell and a starting directory to put in OPEN, on this machine.

    The daemon's allowlist is a property of Termux (PREFIX/bin) plus /bin and /system/bin/sh,
    so the same test runs on a phone and on a Linux runner by following what exists here.
    """
    prefix = os.environ.get("PREFIX", "/data/data/com.termux/files/usr")
    shell = None
    for cand in (prefix + "/bin/sh", "/bin/sh", "/bin/bash"):
        if os.path.isfile(cand) and os.access(cand, os.X_OK):
            shell = cand
            break
    assert shell, "no shell to ask for"
    if os.path.isdir(prefix):
        cwd = prefix
    else:
        cwd = tempfile.mkdtemp(prefix="fterm-cwd-")
    return shell, cwd


def disallowed_shell():
    """An executable whose realpath is outside every prefix the daemon will exec.

    `/usr/bin/env` used to serve here and no longer does: /usr/bin is on the allowlist now
    (Debian's /bin is a symlink into it), which is exactly the kind of drift this candidate
    list has to follow.
    """
    prefix = os.environ.get("PREFIX", "/data/data/com.termux/files/usr")
    allowed = (prefix + "/bin/", prefix + "/local/bin/", "/bin/", "/usr/bin/")
    for cand in ("/system/bin/toybox", "/usr/local/bin/python3", "/usr/local/bin/python3.12",
                 "/etc/hostname"):
        if not (os.path.isfile(cand) and os.access(cand, os.X_OK)):
            continue
        real = os.path.realpath(cand)
        if real == "/system/bin/sh" or any(real.startswith(a) for a in allowed):
            continue
        return cand
    return None


def test_open_strings():
    """OPEN may carry a shell and a directory; both are optional and both validated."""
    c = Conn()
    c.hello()
    s1, s2, s3 = free_sid(c), None, None
    shell, cwd = open_strings_paths()
    payload = struct.pack("<HHH", s1, 80, 24)
    for text in (shell, cwd):
        raw = text.encode()
        payload += struct.pack("<H", len(raw)) + raw
    c.frame(C_OPEN, payload)
    frames = c.collect(1.5)
    assert any(t == S_OPENED for t, _ in frames), [t for t, _ in frames]
    # $0 is the realpath of what was asked for - sh is a symlink to dash here - so the
    # assertion is the directory it runs from plus "not the daemon's own bash".
    out = run(c, s1, "echo CWD-$(pwd); echo ZERO-$0\n")
    assert ("CWD-" + cwd).encode() in out, out[-300:]
    zero = [ln for ln in out.replace(b"\r", b"").split(b"\n") if ln.startswith(b"ZERO-")]
    # $0 is the realpath of what was asked for - sh is a symlink to dash here - so this checks
    # the shell that actually ran, not just that something did.
    assert zero and zero[0] == ("ZERO-" + os.path.realpath(shell)).encode(), (zero, out[-300:])
    print("ok  OPEN carries a starting directory and a shell (%s in %s)" % (shell, cwd))

    # A shell outside the allowlist must fall back, not run. The candidate is picked so that
    # its realpath is outside every allowed prefix on this machine, which is not the same
    # path on a phone and on a Linux runner.
    bad = disallowed_shell()
    if bad is None:
        print("skip  no disallowed-but-executable shell to try on this machine")
    else:
        c2 = Conn()
        c2.hello()
        s2 = free_sid(c2)
        raw = bad.encode()
        payload = struct.pack("<HHH", s2, 80, 24) + struct.pack("<H", len(raw)) + raw
        payload += struct.pack("<H", 0)
        c2.frame(C_OPEN, payload)
        frames = c2.collect(1.5)
        assert any(t == S_OPENED for t, _ in frames), [t for t, _ in frames]
        out = run(c2, s2, "echo SH-$(basename $0)\n")
        forbidden = ("SH-" + os.path.basename(bad)).encode()
        assert forbidden not in out, ("disallowed shell must not run", out[-300:])
        # ... and the session it fell back to is a working shell, not a dead one.
        assert b"SH-" in out, ("the fallback shell must still run", out[-300:])
        print("ok  OPEN ignores a shell outside the allowlist (%s)" % bad)
        c2.frame(C_CLOSE, struct.pack("<H", s2))
        time.sleep(0.3)
        c2.close()

    # Six-byte OPEN (no strings) is what an older client sends and must still work.
    c.frame(C_CLOSE, struct.pack("<H", s1))
    time.sleep(0.3)
    s3 = free_sid(c)
    c.frame(C_OPEN, struct.pack("<HHH", s3, 80, 24))
    frames = c.collect(1.5)
    assert any(t == S_OPENED for t, _ in frames), [t for t, _ in frames]
    print("ok  six-byte OPEN still opens a session")
    c.frame(C_CLOSE, struct.pack("<H", s3))
    time.sleep(0.3)
    c.close()


def test_mode_restore():
    """A reattached client must learn the terminal modes the program set long ago.

    A TUI declares DECSET 1000/1006 once at startup and never repeats it. If that
    declaration has scrolled out of the ring by the time the app's process is recreated,
    the new emulator starts with mouse reporting off while the shell still reports it on:
    swipes become history scrolling and taps never reach the program. The daemon therefore
    tracks the modes per session and replays them ahead of the ring.
    """
    # A session id of its own: the app may be running, and reusing one of its tab ids
    # would type this test's commands into whatever TUI that tab is hosting.
    sid = 77
    c = Conn()
    c.hello()
    c.frame(C_OPEN, struct.pack("<HHH", sid, 100, 30))
    assert any(t == S_OPENED for t, _ in c.collect(1.5)), "no OPENED"
    # Turn mouse reporting on, then bury it under more output than the ring holds.
    decl = b"printf '\\033[?1000h\\033[?1006h'\n"
    c.frame(C_INPUT, struct.pack("<H", sid) + struct.pack("<I", len(decl) + 6) + decl)
    c.collect(1.0)
    fill = 300 * 1024
    bury = ("head -c %d /dev/zero | tr '\\0' x\n" % fill).encode()
    c.frame(C_INPUT, struct.pack("<H", sid) + struct.pack("<I", len(bury) + 6) + bury)
    c.collect(6.0)
    c.close()
    time.sleep(0.5)

    c2 = Conn()
    c2.hello()
    c2.frame(C_OPEN, struct.pack("<HHH", sid, 100, 30))
    frames = c2.collect(3.0)
    replay = b"".join(p[2:] for t, p in frames if t == S_REPLAY)
    pre = b""
    for t, p in frames:
        if t == S_REPLAY:
            break
        if t == S_OUTPUT:
            _, ln = struct.unpack_from("<HI", p, 0)
            pre += p[6:6 + ln]
    # The ring must have lost the declaration, or the test proves nothing.
    assert b"\x1b[?1000h" not in replay, "ring still holds the mode sequence"
    assert b"\x1b[?1000h" in pre, ("modes must precede the ring", pre[:200])
    assert b"\x1b[?1006h" in pre, ("SGR must be restored too", pre[:200])
    print("ok  terminal modes survive a replay that outgrew the ring")

    # A mode the program turned off must not be restored.
    off = b"printf '\\033[?1000l'\n"
    c2.frame(C_INPUT, struct.pack("<H", sid) + struct.pack("<I", len(off) + 6) + off)
    c2.collect(0.8)
    c2.close()
    time.sleep(0.4)
    c3 = Conn()
    c3.hello()
    c3.frame(C_OPEN, struct.pack("<HHH", sid, 100, 30))
    frames = c3.collect(2.0)
    pre = b""
    for t, p in frames:
        if t in (S_REPLAY,):
            break
        if t == S_OUTPUT:
            _, ln = struct.unpack_from("<HI", p, 0)
            pre += p[6:6 + ln]
    assert b"\x1b[?1000h" not in pre, ("a reset mode came back", pre[:200])
    c3.frame(C_CLOSE, struct.pack("<H", sid))
    time.sleep(0.3)
    c3.close()
    print("ok  a mode the program cleared stays cleared")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "shell":
        interactive()
        sys.exit(0)
    test_auth()
    c, sid = test_open_and_echo()
    test_hangul(c, sid)
    test_resize(c, sid)
    c = test_replay(c, sid)
    test_close_and_sync(c, sid)
    c.close()
    test_open_strings()
    test_mode_restore()
    print("ALL OK")
