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

    # Tab mark candidates. A tab icon is the one glyph a user looks at all day, so the
    # set is broad on purpose: a terminal is used for ssh, for code, for a database
    # console, for a log tail, and the mark is how a glance tells those tabs apart.
    "prompt":    ("Prompt", 24),
    "server":    ("Server", 24),
    "database":  ("Database", 24),
    "cloud":     ("Cloud", 24),
    "globe":     ("Globe", 24),
    "folder":    ("Folder", 24),
    "document":  ("Document", 24),
    "star":      ("Star", 24),
    "heart":     ("Heart", 24),
    "shield":    ("Shield", 24),
    "flash":     ("Flash", 24),
    "cube":      ("Cube", 24),
    "puzzle":    ("Puzzle Piece", 24),
    "wrench":    ("Wrench", 24),
    "brush":     ("Paint Brush", 24),
    "eye":       ("Eye", 24),
    "fire":      ("Fire", 24),
    "camera":    ("Camera", 24),
    "gift":      ("Gift", 24),
    "rocket":    ("Rocket", 24),
    "bug":       ("Bug", 24),
    "beaker":    ("Beaker", 24),
    "flashlight": ("Flashlight", 24),
    "home":      ("Home", 24),
    "chat":      ("Chat", 24),
    "mail":      ("Mail", 24),
    "phone":     ("Phone", 24),
    "print":     ("Print", 24),
    "ruler":     ("Ruler", 24),
    "scan":      ("Scan", 24),
    "keyboard2": ("Keyboard", 24),
}

BASE = ("https://raw.githubusercontent.com/microsoft/fluentui-system-icons/main/"
        "assets/%s/SVG/ic_fluent_%s_%d_regular.svg")

# Icons that are not Fluent. A profile mark for a specific program should be that program's
# own mark: an opencode tab with a generic terminal glyph is the wrong signal, and the app
# already owns the path-data pipeline, so the only cost is a source URL.
#
# Each entry is (viewBox size, path data) or (viewBox size, path data, (x, y, w, h)), where
# the third value is the bounding box of the ink inside the viewBox. The path is scaled onto
# the 24 grid, so the set stays optically level; an entry with a bounding box is cropped to it
# first, because a site favicon is drawn with dead margin around it and at 15dp that margin is
# half the slot.
#
# The box is width and height, not one span: a mark is not always square. The opencode mark is
# 256 wide and 320 tall, and fitting it to a square box squashed it against the short side.
RAW = {
    # github.com/anomalyco/opencode, packages/ui/src/assets/favicon/favicon.svg.
    #
    # The original is a white frame with an evenodd hole and a gray block filling the lower
    # half of that hole. Android's path fill is non-zero, so the hole is written the other
    # way round - the inner square is traversed counter-clockwise - and the gray block is a
    # normal clockwise subpath: frame and block both paint, the gap between them stays empty.
    # That is the monochrome reading of the mark, which is what a 15dp tab slot needs; the
    # original's two colours would collapse into a blob at this size.
    # Cropped to the frame: the favicon's frame sits at 128..384 of a 512 canvas, so an
    # uncropped glyph fills a quarter of its slot and reads as a smudge.
    #
    # Only the frame is here. The block inside it is the mark's second tone, and one filled
    # path cannot carry two tones: the block would merge with the frame into a slab, which is
    # exactly the shape this mark is not. It comes from RAW_ACCENT instead.
    "opencode": (512,
                 "M384 416H128V96H384V416Z"          # outer frame
                 "M320 160H192V352H320V160Z",        # hole: reversed, so it stays a hole
                 (128, 96, 256, 320))
}

# Marks that are two-tone in their source: the main path drawn in the tint, this one at
# partial alpha over it. Keyed and cropped like RAW.
RAW_ACCENT = {
    # The lower half of opencode's opening, gray in the favicon while the frame is white.
    "opencode": "M320 224V352H192V224H320Z",
}

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


def emit(d, pad, var="p"):
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
                    out.append("%s%s.moveTo(%sf, %sf);" % (pad, var, fmt(x), fmt(y)))
                else:
                    out.append("%s%s.lineTo(%sf, %sf);" % (pad, var, fmt(x), fmt(y)))
                cur = (x, y)
        elif c == "H":
            for v in nums:
                x = cur[0] + v if rel else v
                cur = (x, cur[1])
                out.append("%s%s.lineTo(%sf, %sf);" % (pad, var, fmt(x), fmt(cur[1])))
        elif c == "V":
            for v in nums:
                y = cur[1] + v if rel else v
                cur = (cur[0], y)
                out.append("%s%s.lineTo(%sf, %sf);" % (pad, var, fmt(cur[0]), fmt(y)))
        elif c == "C":
            for i in range(0, len(nums) - 5, 6):
                x1, y1, x2, y2, x, y = nums[i:i + 6]
                if rel:
                    x1, y1 = x1 + cur[0], y1 + cur[1]
                    x2, y2 = x2 + cur[0], y2 + cur[1]
                    x, y = x + cur[0], y + cur[1]
                out.append("%s%s.cubicTo(%sf, %sf, %sf, %sf, %sf, %sf);"
                           % (pad, var, fmt(x1), fmt(y1), fmt(x2), fmt(y2), fmt(x), fmt(y)))
                cur = (x, y)
        elif c == "Z":
            cur = start
            out.append("%s%s.close();" % (pad, var))
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

    /**
     * Append the second tone for {@code name}, or leave {@code p} empty.
     *
     * <p>Almost every icon is one silhouette and returns nothing. The few that are not are
     * the ones whose source draws two colours to make one shape - a bright frame around a
     * gray block - and flattening them into a single fill loses the shape. The caller draws
     * this path over the first at reduced alpha.
     */
    static void buildAccent(String name, Path p) {
        switch (name) {
__ACCENT__
            default:
                break;
        }
    }
}
'''


def main():
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    out = root + "/app/src/ai/openclaw/fterm/GlyphPaths.java"
    body = []
    for name in sorted(list(ICONS) + list(RAW)):
        box = None
        if name in RAW:
            entry = RAW[name]
            size, d = entry[0], entry[1]
            if len(entry) > 2:
                box = entry[2]
        else:
            folder, size = ICONS[name]
            d = path_data(fetch(folder, size))
        body.append('            case "%s":' % name)
        if size == 24:
            body.extend(emit(d, "                "))
        else:
            # Window Console is only published on a 20-grid, and a favicon is published with
            # margin around its mark. Draw into a scratch path and scale that grid up to 24,
            # then shift a cropped entry so its ink starts at the origin - together these put
            # the mark on the same optical footing as the rest of the set.
            body.append("                {")
            body.append("                    Path t = new Path();")
            # Draw into t, not into p: an unscaled 512- or 20-grid path in p is off the
            # visible grid, which is why these icons rendered as nothing at all.
            body.extend(emit(d, "                    ", var="t"))
            body.append("                    Matrix m = new Matrix();")
            if box is None:
                body.append("                    m.setScale(GRID / %df, GRID / %df);"
                            % (size, size))
            else:
                x, y, w, h = box
                # Fit the larger side to the grid, then centre the other one: filling the
                # grid on both axes would stretch a mark that is not square.
                scale = 24.0 / max(w, h)
                body.append("                    m.setScale(%sf, %sf);" % (fmt(scale), fmt(scale)))
                body.append("                    m.postTranslate(%sf, %sf);"
                            % (fmt((24.0 - w * scale) / 2 - x * scale),
                               fmt((24.0 - h * scale) / 2 - y * scale)))
            body.append("                    t.transform(m);")
            body.append("                    p.addPath(t);")
            body.append("                }")
        body.append("                break;")
    accent = []
    for name in sorted(RAW_ACCENT):
        entry = RAW[name]
        size = entry[0]
        box = entry[2] if len(entry) > 2 else None
        accent.append('            case "%s":' % name)
        accent.append("                {")
        accent.append("                    Path t = new Path();")
        accent.extend(emit(RAW_ACCENT[name], "                    ", var="t"))
        accent.append("                    Matrix m = new Matrix();")
        if box is None:
            accent.append("                    m.setScale(GRID / %df, GRID / %df);"
                          % (size, size))
        else:
            x, y, w, h = box
            scale = 24.0 / max(w, h)
            accent.append("                    m.setScale(%sf, %sf);"
                          % (fmt(scale), fmt(scale)))
            accent.append("                    m.postTranslate(%sf, %sf);"
                          % (fmt((24.0 - w * scale) / 2 - x * scale),
                             fmt((24.0 - h * scale) / 2 - y * scale)))
        accent.append("                    t.transform(m);")
        accent.append("                    p.addPath(t);")
        accent.append("                }")
        accent.append("                break;")

    with open(out, "w") as f:
        f.write(HEADER)
        f.write("\n".join(body) + "\n")
        f.write(FOOTER.replace("__ACCENT__", "\n".join(accent)))
    print("wrote", out)


if __name__ == "__main__":
    main()
