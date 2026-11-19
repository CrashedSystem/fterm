"""The protocol constant tables must be identical in all the places they exist.

The wire format was implemented three times in the app's own code (SocketTransport,
DaemonClient, and now a shared Protocol class) plus once in the daemon (Rust; the Python
implementation is gone - the Rust port proved protocol-identical on the wire, so keeping a
second implementation of one protocol was drift waiting to happen).
They agree today because they were copied carefully, and that is exactly the kind of agreement
that survives until someone adds a frame type in one place. A mismatch is not a crash: the
daemon answers "unknown type" and the app sees a tab that does nothing.

So this reads the numbers out of the sources and compares them. It does not run a daemon; it is
a spec check, and it runs from `tests/run_daemon.sh` because that script is what CI calls.

  python3 tests/test_protocol.py
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

RS_DAEMON = os.path.join(ROOT, "termux", "ftermd-rs", "src", "protocol.rs")
JAVA = os.path.join(ROOT, "app", "src", "com.termux", "terminal", "Protocol.java")

fails = []


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else "  <- " + detail))
    if not cond:
        fails.append(name)


# Only the wire vocabulary is compared: a daemon also has tuning constants (IDLE_TIMEOUT) that
# are its own business and would otherwise look like drift.
PROTOCOL_NAME = re.compile(r"^(C_|S_|E_|FONT_|PROTO_VERSION$|MAX_FRAME$|MAX_SESSIONS$)")


def wanted(name):
    return bool(PROTOCOL_NAME.match(name))


def value(text):
    text = text.strip().rstrip(";")
    return int(text, 0)


def read_rust():
    """C_HELLO = 0x01 and friends, from the module-level `pub const` items."""
    out = {}
    with open(RS_DAEMON) as f:
        for line in f:
            m = re.match(r"^pub const ([A-Z][A-Z0-9_]*)\s*:\s*(u8|u32|usize)\s*=\s*([^;]+);", line)
            if m and wanted(m.group(1)):
                raw = m.group(3).strip()
                try:
                    out[m.group(1)] = value(raw)
                except ValueError:
                    # `1 << 20` and similar: evaluate the two simple forms that are used.
                    mm = re.match(r"1\s*<<\s*(\d+)", raw)
                    if mm:
                        out[m.group(1)] = 1 << int(mm.group(1))
    return out


def read_java():
    out = {}
    with open(JAVA) as f:
        for line in f:
            m = re.match(r"\s*public static final int ([A-Z][A-Z0-9_]*)\s*=\s*([^;]+);", line)
            if m and wanted(m.group(1)):
                raw = m.group(2).strip()
                mm = re.match(r"1\s*<<\s*(\d+)", raw)
                out[m.group(1)] = (1 << int(mm.group(1))) if mm else value(raw)
    return out


def main():
    rs, java = read_rust(), read_java()
    check("the rust daemon has its protocol table", len(rs) >= 20, "%d constants" % len(rs))
    check("the app has its protocol table", len(java) >= 20, "%d constants" % len(java))

    # Names differ in spelling only where the language does: the app's VERSION is the daemon's
    # PROTO_VERSION.
    java_names = dict(java)
    if "VERSION" in java_names:
        java_names["PROTO_VERSION"] = java_names.pop("VERSION")

    shared = sorted(set(rs) & set(java_names))
    check("both tables define the frame types", len(shared) >= 20, "%d shared" % len(shared))

    for name in shared:
        check("%s is the same everywhere" % name, rs[name] == java_names[name],
              "rust=0x%02x java=0x%02x" % (rs[name], java_names[name]))

    # Anything one side knows and the other does not is drift waiting to happen, and it is
    # cheaper to notice here than as a tab that does nothing.
    only_rs = sorted(set(rs) - set(java_names))
    only_java = sorted(set(java_names) - set(rs))
    # The daemon sizes its session table and the app does not, and the daemon's PROTO_VERSION
    # is the app's VERSION; both are expected.
    only_rs = [n for n in only_rs if n not in ("MAX_SESSIONS", "PROTO_VERSION")]
    check("the rust daemon adds nothing the app lacks", not only_rs, repr(only_rs))
    check("the app adds nothing the daemon lacks", not only_java, repr(only_java))

    # The frame envelope itself: five bytes, little-endian length.
    try:
        src = open(JAVA).read()
        check("the app frames with a 5-byte header", "new byte[5 + payload.length]" in src)
        check("and writes the length little-endian", "putInt(framed, 1, payload.length)" in src)
    except OSError as e:
        check("the app's framing is readable", False, str(e))

    print("test_protocol: %d failed" % len(fails))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
