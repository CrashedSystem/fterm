#!/usr/bin/env python3
"""Generate app/src/ai/openclaw/fterm/GlyphPaths.java from Fluent UI System Icons.

WT draws its chrome with Segoe Fluent Icons, a real icon font. The hand-drawn stroked
paths this project used before were the reason the toolbar looked wrong: a constant-width
1.9dp stroke is not how an icon font draws, and the eye notices even when it cannot say
why. So the glyphs come from the same source Microsoft ships, and this script converts
them when the source is authored rather than at runtime - android.util.PathParser is a
hidden API, and approximating curves by hand is how a set ends up almost-right.

Fluent's regular paths only use M/L/H/V/C/Z, so the conversion is exact.

Usage:  python3 tests/tools/icons.py
Writes: app/src/ai/openclaw/fterm/GlyphPaths.java
"""
import os
import re
import urllib.parse
import urllib.request

# semantic name in this app -> (Fluent icon folder, Fluent grid size)
ICONS = {
    "plus":      ("Add", 24),
    "more":      ("More Horizontal", 24),
    "menu":      ("Navigation", 24),
    "chevron":   ("Chevron Down", 24),
    "left":      ("Chevron Left", 24),
    "right":     ("Chevron Right", 24),
    "close":     ("Dismiss", 24),
    "check":     ("Checkmark", 24),
    "pencil":    ("Edit", 24),
    "clipboard": ("Clipboard", 24),
    "copy":      ("Copy", 24),
    "search":    ("Search", 24),
    "settings":  ("Settings", 24),
    "gear":      ("Settings", 24),
    "info":      ("Info", 24),
    "palette":   ("Color", 24),
    "terminal":  ("Window Console", 20),
    "ssh":       ("Server", 24),
    "keyboard":  ("Keyboard", 24),
    "split":     ("Arrow Split", 24),
    "code":      ("Code", 24),
}

BASE = ("https://raw.githubusercontent.com/microsoft/fluentui-system-icons/main/"
        "assets/%s/SVG/ic_fluent_%s_%d_regular.svg")

TOKEN = re.compile(r'([MmLlHhVvCcZz])|(-?\d*\.?\d+(?:e-?\d+)?)')


def fetch(folder, size):
    url = BASE % (urllib.parse.quote(folder), folder.lower().replace(" ", "_"), size)
    with urllib.request.urlopen(url, timeout=30) as r:
        return r.read().decode("utf-8")


def path_data(svg):
    ds = re.findall(r'd="([^"]+)"', svg)
    if not ds:
        raise SystemExit("no path data")
    return " ".join(ds)


def parse(d):
    """Yield (command, [floats]) with implicit command repeats made explicit."""
    cmd, nums = None, []
    for m in TOKEN.finditer(d):
        if m.group(1):
            if cmd is not None:
                yield cmd, nums
            cmd, nums = m.group(1), []
        else:
            nums.append(float(m.group(2)))
    if cmd is not None:
        yield cmd, nums


def fmt(v):
    s = ("%.4f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("", "-0") else s


def emit(d, pad):
    """Path calls for one Fluent path, relative commands folded into absolutes."""
    out, cur, start = [], (0.0, 0.0), (0.0, 0.0)
    for cmd, nums in parse(d):
        rel, c = cmd.islower(), cmd.upper()
        if c in ("M", "L"):
            for i in range(0, len(nums) - 1, 2):
                x, y = nums[i], nums[i + 1]
                if rel:
                    x, y = x + cur[0], y + cur[1]
                if c == "M" and i == 0:
                    start = (x, y)
                    out.append("%sp.moveTo(%sf, %sf);" % (pad, fmt(x), fmt(y)))
                else:
                    out.append("%sp.lineTo(%sf, %sf);" % (pad, fmt(x), fmt(y)))
                cur = (x, y)
        elif c == "H":
            for v in nums:
                x = cur[0] + v if rel else v
                cur = (x, cur[1])
                out.append("%sp.lineTo(%sf, %sf);" % (pad, fmt(x), fmt(cur[1])))
        elif c == "V":
            for v in nums:
                y = cur[1] + v if rel else v
                cur = (cur[0], y)
                out.append("%sp.lineTo(%sf, %sf);" % (pad, fmt(cur[0]), fmt(y)))
        elif c == "C":
            for i in range(0, len(nums) - 5, 6):
                x1, y1, x2, y2, x, y = nums[i:i + 6]
                if rel:
                    x1, y1 = x1 + cur[0], y1 + cur[1]
                    x2, y2 = x2 + cur[0], y2 + cur[1]
                    x, y = x + cur[0], y + cur[1]
                out.append("%sp.cubicTo(%sf, %sf, %sf, %sf, %sf, %sf);"
                           % (pad, fmt(x1), fmt(y1), fmt(x2), fmt(y2), fmt(x), fmt(y)))
                cur = (x, y)
        elif c == "Z":
            cur = start
            out.append("%sp.close();" % pad)
        else:
            raise SystemExit("unsupported SVG command %r" % cmd)
    return out


HEADER = '''package ai.openclaw.fterm;

import android.graphics.Matrix;
import android.graphics.Path;

/**
 * The chrome icon set, generated from Fluent UI System Icons - the same glyph family WT
 * draws its toolbar with.
 *
 * <p>Source: github.com/microsoft/fluentui-system-icons (MIT), fetched by
 * {@code tests/tools/icons.py}; do not edit by hand.
 *
 * <p>These are filled outlines, not strokes. That is what gives an icon font its uneven
 * weight, and a constant-width stroke is what made the previous hand-drawn set look foreign
 * next to the rest of the UI.
 *
 * <p>The source paths only use M/L/H/V/C/Z, so this translation is exact - no curve is
 * approximated, and no runtime SVG parser is needed.
 */
final class GlyphPaths {

    /** Every icon is drawn on this grid; the view scales it to whatever box it is given. */
    static final float GRID = 24f;

    private GlyphPaths() {}

    /** Append the path for {@code name} into {@code p}; leaves {@code p} empty if unknown. */
    static void build(String name, Path p) {
        switch (name) {
'''

FOOTER = '''            default:
                // Unknown names draw nothing. A visible wrong icon is worse than a gap:
                // it claims the toolbar works when it does not.
                break;
        }
    }
}
'''


def main():
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    out = root + "/app/src/ai/openclaw/fterm/GlyphPaths.java"
    body = []
    for name in sorted(ICONS):
        folder, size = ICONS[name]
        d = path_data(fetch(folder, size))
        body.append('            case "%s":' % name)
        if size == 24:
            body.extend(emit(d, "                "))
        else:
            # Window Console is only published on a 20-grid. Draw it into a scratch path and
            # scale that grid up to 24 so it sits optically level with the rest of the set.
            body.append("                {")
            body.append("                    Path t = new Path();")
            body.extend(emit(d, "                    "))
            body.append("                    Matrix m = new Matrix();")
            body.append("                    m.setScale(GRID / %df, GRID / %df);" % (size, size))
            body.append("                    t.transform(m);")
            body.append("                    p.addPath(t);")
            body.append("                }")
        body.append("                break;")
    with open(out, "w") as f:
        f.write(HEADER)
        f.write("\n".join(body) + "\n")
        f.write(FOOTER)
    print("wrote", out)


if __name__ == "__main__":
    main()
