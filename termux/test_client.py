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
    c.frame(C_OPEN, struct.pack("<HHH", 1, 80, 24))
    f = c.collect(1.5)
    opened = [(t, p) for t, p in f if t == S_OPENED]
    assert opened, ("no OPENED", [t for t, _ in f], err_code(f))
    sid, pid = struct.unpack_from("<Hi", opened[0][1], 0)
    assert sid == 1 and pid > 0, (sid, pid)

    c.frame(C_INPUT, struct.pack("<H", 1) + struct.pack("<I", 22) +
            b"echo MARKER-$((40+2))\n")
    text = b""
    for typ, pay in c.collect(2.0):
        if typ == S_OUTPUT:
            s, ln = struct.unpack_from("<HI", pay, 0)
            text += pay[6:6 + ln]
    assert b"MARKER-42" in text, text[-400:]
    print("ok  open -> shell, echo round-trip (pid %d)" % pid)
    return c


def test_hangul(c):
    msg = "한글출력".encode()
    c.frame(C_INPUT, struct.pack("<H", 1) + struct.pack("<I", len(msg) + 6) +
            msg)
    time.sleep(0.2)
    c.frame(C_INPUT, struct.pack("<H", 1) + struct.pack("<I", 1) + b"\r")
    text = b""
    for typ, pay in c.collect(1.5):
        if typ == S_OUTPUT:
            s, ln = struct.unpack_from("<HI", pay, 0)
            text += pay[6:6 + ln]
    assert "한글출력".encode() in text, text[-300:]
    print("ok  hangul utf-8 round-trip")


def test_resize(c):
    c.frame(C_RESIZE, struct.pack("<HHH", 1, 41, 13))
    time.sleep(0.2)
    cmd = b"stty size\n"
    c.frame(C_INPUT, struct.pack("<H", 1) + struct.pack("<I", len(cmd) + 6) + cmd)
    text = b""
    for typ, pay in c.collect(1.5):
        if typ == S_OUTPUT:
            s, ln = struct.unpack_from("<HI", pay, 0)
            text += pay[6:6 + ln]
    assert b"13 41" in text, text[-300:]
    print("ok  resize reaches the pty (stty says 13 41)")


def test_replay(c):
    c.frame(C_INPUT, struct.pack("<H", 1) + struct.pack("<I", 6 + 12) +
            b"echo RPLY-99\n")
    c.collect(0.8)
    c.close()
    time.sleep(0.3)
    c2 = Conn()
    c2.hello()
    c2.frame(C_OPEN, struct.pack("<HHH", 1, 41, 13))
    frames = c2.collect(1.5)
    replays = [(t, p) for t, p in frames if t == S_REPLAY]
    assert replays, [t for t, _ in frames]
    blob = b"".join(p[2:] for _, p in replays)
    assert b"RPLY-99" in blob, blob[-300:]
    print("ok  reconnect replays scrollback (%d bytes)" % len(blob))
    return c2


def sync_ids(c):
    c.frame(C_SYNC)
    for typ, pay in c.collect(1.0):
        if typ == S_SYNCED:
            n = struct.unpack_from("<H", pay, 0)[0]
            return [struct.unpack_from("<H", pay, 2 + i * 2)[0] for i in range(n)]
    return None


def test_close_and_sync(c):
    # A daemon may be shared with other clients, so the check is "id 1 is gone",
    # not "the daemon is empty".
    before = sync_ids(c)
    c.frame(C_CLOSE, struct.pack("<H", 1))
    time.sleep(0.4)
    after = sync_ids(c)
    assert after is not None, "no SYNCED reply"
    assert 1 not in after, ("session 1 must be gone after CLOSE", after)
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


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "shell":
        interactive()
        sys.exit(0)
    test_auth()
    c = test_open_and_echo()
    test_hangul(c)
    test_resize(c)
    c = test_replay(c)
    test_close_and_sync(c)
    c.close()
    print("ALL OK")
