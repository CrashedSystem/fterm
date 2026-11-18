"""Unit checks for the daemon's mode tracker, run without a daemon or a pty.

`Modes` is the only part of the reconnect fix that reasons about a stream split at arbitrary
byte boundaries, so it is worth pinning down on its own: the failures it can have are silent
(a declaration swallowed because it arrived in two reads, or a private sequence misread as a
mode) and would look exactly like the bug it is meant to fix.

  python3 test_modes.py
"""
import re
import sys
import types

import os
SRC = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "termux", "ftermd")
fails = []


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else "  <- " + detail))
    if not cond:
        fails.append(name)


def load_modes():
    """Pull the Modes class out of the daemon without running its main()."""
    src = open(SRC).read()
    m = re.search(r"^class Modes:\n(?:.*\n)*?(?=^class )", src, re.M)
    if not m:
        raise SystemExit("Modes not found - did it get renamed?")
    g = {"__name__": "modes_under_test"}
    exec(compile(m.group(0), "ftermd:Modes", "exec"), g)
    return g["Modes"]


Modes = load_modes()

# 1. A plain declaration is remembered and replayed.
m = Modes()
m.feed(b"\x1b[?1000h\x1b[?1006h")
check("a DECSET is replayed", m.restore() == b"\x1b[?1000h\x1b[?1006h", repr(m.restore()))
check("and it reads as interactive", m.interactive())

# 2. A reset is honoured and not replayed: the program said it wants no events.
m.feed(b"\x1b[?1000l")
check("a DECRST is not replayed", m.restore() == b"\x1b[?1006h", repr(m.restore()))

# 3. Split anywhere: every prefix must produce the same answer as the whole at once.
seq = b"\x1b[?1000;1006;1049h"
for cut in range(1, len(seq)):
    a = Modes()
    a.feed(seq)
    b = Modes()
    b.feed(seq[:cut])
    b.feed(seq[cut:])
    check("split at %d matches whole" % cut, a.restore() == b.restore(),
          "%r vs %r" % (a.restore(), b.restore()))

# 4. Combined parameters are all applied.
m = Modes()
m.feed(seq)
check("a combined DECSET sets every parameter",
      set(m.state) == {1000, 1006, 1049}, repr(m.state))

# 5. Unrelated private sequences are ignored rather than guessed at: DECSET 2004 (bracketed
#    paste) is restored, but a private sequence the daemon does not track must not appear.
m = Modes()
m.feed(b"\x1b[?1234h\x1b[?2026h")
check("an untracked mode stays out of the state", not m.state, repr(m.state))
check("and out of the replay", m.restore() == b"", repr(m.restore()))

# 6. Bracketed paste is worth keeping: a reattached client must not paste a shell command
#    without the guards the program turned on.
m = Modes()
m.feed(b"\x1b[?2004h")
check("2004 is restored", m.restore() == b"\x1b[?2004h", repr(m.restore()))

# 7. The alt screen goes last, so it cannot be followed by something that clears it.
m = Modes()
m.feed(b"\x1b[?1049h\x1b[?1000h\x1b[?1006h")
r = m.restore()
check("the alt screen is the final item", r.endswith(b"\x1b[?1049h"), repr(r))
check("and the mouse modes precede it", r.index(b"1000") < r.index(b"1049"), repr(r))

# 8. Splitting mid-escape between two reads must not lose the sequence. This is the case the
#    old ring-only design could not survive at all - a declaration cut by the read boundary
#    would have been discarded as well as pushed out.
for cut in (1, 2, 3, 4):
    b = Modes()
    b.feed(b"\x1b[?1000h"[:cut])
    b.feed(b"\x1b[?1000h"[cut:])
    check("a sequence cut after %d bytes survives" % cut, b.restore() == b"\x1b[?1000h",
          repr(b.restore()))

# 9. Ordinary text containing a question mark is not a mode.
m = Modes()
m.feed(b"what? 1000h is fine\n")
check("prose is not parsed as a mode", m.restore() == b"", repr(m.restore()))

# 10. Non-private CSI is untouched: the tracker only claims ESC[?.
m = Modes()
m.feed(b"\x1b[1;31m\x1b[2J\x1b[?1000h")
check("colour and clear are ignored", m.restore() == b"\x1b[?1000h", repr(m.restore()))

# 11. A late 1000, arriving after everything else, still wins: last state, not first.
m = Modes()
m.feed(b"\x1b[?1000h\x1b[?1006h")
m.feed(b"\x1b[?1000l\x1b[?1000h")
check("the last declaration wins", m.restore() == b"\x1b[?1000h\x1b[?1006h", repr(m.restore()))

# 12. Large output with no modes at all must not accumulate the carry forever.
m = Modes()
m.feed(b"x" * 100000)
check("no carry from plain output", m.carry == b"", repr(m.carry[:40]))
check("and no state", not m.state, repr(m.state))

# 12b. A parameter run that never ends is broken output, not a mode: the carry must stay
#     bounded instead of growing with the stream (measured: 600 bytes and climbing).
m = Modes()
m.feed(b"\x1b[?" + b"1" * 99)
for _ in range(20):
    m.feed(b"1" * 500)
check("an unterminated parameter run does not grow the carry",
      len(m.carry) <= Modes.MAX_PENDING, "%d bytes held" % len(m.carry))
check("and it does not become a mode", not m.state, repr(m.state))

# 12c. But a genuinely split sequence is still held: MAX_PENDING is far longer than any
#     real declaration.
m = Modes()
m.feed(b"\x1b[?1000;10")
check("a real partial sequence is kept", m.carry == b"\x1b[?1000;10", repr(m.carry))
m.feed(b"06h")
check("and completes across the split", m.restore() == b"\x1b[?1000h\x1b[?1006h", repr(m.restore()))

# 13. The interactive set is exactly the modes that make a program own the screen.
m = Modes()
m.feed(b"\x1b[?2004h\x1b[?25l")
check("2004 alone is not interactive", not m.interactive(), repr(m.state))
m.feed(b"\x1b[?1049h")
check("the alt screen is", m.interactive())

print("test_modes: %d failed" % len(fails))
sys.exit(1 if fails else 0)
