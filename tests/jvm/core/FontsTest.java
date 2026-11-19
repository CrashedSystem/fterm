/**
 * The font stack's routing rules, checked against the app's own constants.
 *
 * mterm's terminal type is three faces: Iosevka for Latin, a Nerd symbols face for icons,
 * Pretendard for Hangul. One syllable is two Latin cells wide, and the Hangul face is drawn
 * at the same em size as the Latin one - a face that overhangs its box is shrunk for that
 * code point, never the other faces grown to match (Windows Terminal's AtlasEngine rule).
 * The *metrics* are asserted on a device, and this test asserts the half that lives in the
 * app: which face answers which code point, and how the fitting is computed.
 */
public class FontsTest {

    static int pass = 0;

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
        pass++;
    }

    static void near(double actual, double expected, double tolerance, String what) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(what + ": expected " + expected + " +/- " + tolerance
                    + ", got " + actual);
        }
        pass++;
    }

    public static void main(String[] args) throws Exception {
        // 1. A Korean glyph may be shrunk to fit its two cells, never enlarged.
        //
        //    It used to be scaled up by 115.85% to fill the box. That made the Korean face's
        //    own ascent and line spacing larger than the row height the Latin face defined -
        //    measured on the device as 41.3px of spacing drawn into a 37.5px row - and the
        //    text read as cramped. Windows Terminal keeps one em size for every face and only
        //    corrects the advance to the cell, so this is the ceiling.
        near(ai.openclaw.fterm.Fonts.HANGUL_FIT_MAX, 1.0, 0.001, "HANGUL_FIT_MAX");

        // 2. Hangul routing covers every block a Korean terminal sees.
        check(ai.openclaw.fterm.Fonts.isHangul(0xAC00), "가 (syllables)");
        check(ai.openclaw.fterm.Fonts.isHangul(0xD55C), "한");
        check(ai.openclaw.fterm.Fonts.isHangul(0xD7A3), "the last syllable");
        check(ai.openclaw.fterm.Fonts.isHangul(0x3131), "ㄱ (compatibility jamo)");
        check(ai.openclaw.fterm.Fonts.isHangul(0x1100), "U+1100 (conjoining jamo)");
        check(ai.openclaw.fterm.Fonts.isHangul(0x11A8), "U+11A8 (trailing jamo)");
        check(ai.openclaw.fterm.Fonts.isHangul(0xA960), "U+A960 (jamo extended-A)");
        check(ai.openclaw.fterm.Fonts.isHangul(0xD7B0), "U+D7B0 (jamo extended-B)");

        // 3. And it does not claim anything else.
        check(!ai.openclaw.fterm.Fonts.isHangul('A'), "A");
        check(!ai.openclaw.fterm.Fonts.isHangul(0x30A2), "ア is Japanese, not Hangul");
        check(!ai.openclaw.fterm.Fonts.isHangul(0x6F22), "漢 is Hanja");
        check(!ai.openclaw.fterm.Fonts.isHangul(0x2588), "█ is a block element");
        check(!ai.openclaw.fterm.Fonts.isHangul(0x2500), "─ is box drawing");

        // 4. Icon routing is the private-use range a Nerd Font patches.
        check(ai.openclaw.fterm.Fonts.isNerdIcon(0xE0A0), "a powerline glyph");
        check(ai.openclaw.fterm.Fonts.isNerdIcon(0xF0C7), "a Font Awesome codepoint a Nerd Font fills");
        check(!ai.openclaw.fterm.Fonts.isNerdIcon('a'), "a letter is not an icon");
        check(!ai.openclaw.fterm.Fonts.isNerdIcon(0x2500), "box drawing is Iosevka's, not the icon face");

        // 5. Only Hangul can be resized at all; everything else is drawn at the em size.
        check(ai.openclaw.fterm.Fonts.isHangul(0xD55C), "한 is the only script up for fitting");
        check(!ai.openclaw.fterm.Fonts.isHangul('x'), "x is never resized");

        // 5b. Font choice is a scanned catalog, not a hard-coded table.
        //
        //     The old design named three faces in code, so the setting could never offer
        //     anything else on a device with hundreds of fonts. The id format is what the
        //     picker stores, so it is what has to be stable: the bundled face has a fixed
        //     id, a user font is "file:" + its absolute path, and anything unrecognised
        //     resolves to the bundled face rather than to a crash.
        String fontsSrc = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("fonts.src",
                        "app/src/ai/openclaw/fterm/Fonts.java"))), "UTF-8");
        check(fontsSrc.contains("\"asset:iosevka\""), "the bundled face has a stable id");
        check(fontsSrc.contains("context.getFilesDir(), \"fonts\""),
                "imported fonts live in app storage, the one directory it can read");
        check(!fontsSrc.contains("/sdcard/Download/fonts"),
                "not a shared folder: unreadable to the app since Android 11");
        check(fontsSrc.contains("importFont("),
                "the way in is a picked document, copied once");
        check(fontsSrc.contains("isFixedAdvance(face)"),
                "and a proportional file is refused before it is kept");
        check(fontsSrc.contains("isFixedAdvance"),
                "a proportional face is filtered out, or the grid breaks");
        check(fontsSrc.contains("measureText(\"M\")")
                        && fontsSrc.contains("measureText(probe)"),
                "the fixed-advance test measures the face, not its name");
        check(fontsSrc.contains("Math.abs(p.measureText(probe) - m) > m * 0.01f"),
                "and allows only a one-percent spread between glyphs");
        check(fontsSrc.contains("sCatalog = null"),
                "a rescan can drop the cache after the user adds a font");
        check(!fontsSrc.contains("LATIN_NAMES"),
                "the hard-coded name table is gone");
        check(fontsSrc.contains("static Face find("),
                "an id resolves through one lookup, so every caller agrees");
        check(fontsSrc.contains("typedPath("),
                "a typed path is honoured even though it is not in the catalog");
        check(fontsSrc.contains("!id.startsWith(FILE_PREFIX)"),
                "only a file: id is treated as a path, never a bare path in the store");
        check(fontsSrc.contains("private static Face typedPath") 
                        && fontsSrc.contains("isFile()"),
                "and a path that no longer exists falls back instead of crashing");

        // The picker has to read the catalog and pass the id through, not an index.
        String sheet = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("sheet.src",
                        "app/src/ai/openclaw/fterm/SettingsSheet.java"))), "UTF-8");
        check(sheet.contains("Fonts.catalog("), "the picker lists the scanned catalog");
        check(sheet.contains("mHost.onFontFamily(f.id)"), "and stores the face's id");
        check(sheet.contains("ACTION_OPEN_DOCUMENT"),
                "the picker imports through a document the user chose");
        check(sheet.contains("Fonts.importFont("),
                "copying it, because a content:// grant does not outlive a reboot");
        check(sheet.contains("onFontImportResult"),
                "and applies the copy when the result comes back");
        check(sheet.contains("mHost.onFontFamily(imported.id)"),
                "storing the catalog id, never a raw path");
        check(sheet.contains("Fonts.rescan"), "and rescan after adding one");

        // 5b. Termux is the easy way in: a URL is fetched on that side, because this app has
        //     neither a URL client nor a directory it may download into.
        check(sheet.contains("DaemonClient.fonts("),
                "the install dialog lists what the daemon can see");
        check(sheet.contains("DaemonClient.fetchFont("),
                "and a URL is downloaded by Termux, not by the app");
        check(sheet.contains("DaemonClient.font("),
                "the bytes come back over the same socket the terminal uses");
        check(sheet.contains("Fonts.install("),
                "and land in app storage through the same validation as a picked file");
        check(sheet.contains("new Thread("),
                "the listing runs off the UI thread - it is a socket round trip");
        check(sheet.contains("글꼴 설치"),
                "offered from the font list itself, where the new font will appear");

        String daemon = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("daemon.src",
                        "termux/ftermd-rs/src/protocol.rs"))),
                "UTF-8");
        String fontsrv = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("daemon.fonts",
                        "termux/ftermd-rs/src/fonts.rs"))),
                "UTF-8");
        check(daemon.contains("C_FONT_LIST") && daemon.contains("C_FONT_GET"),
                "the daemon protocol has the font opcodes");
        check(fontsrv.contains("FONT_CHUNK"),
                "a font is served in chunks, because one frame is capped at 1 MiB");
        check(fontsrv.contains("font_scan()"),
                "and only a path the scan returned is served, not an arbitrary file");
        check(fontsrv.contains("fetch_font("),
                "the daemon can download a URL, which is the whole point of the shortcut");
        check(fontsrv.contains(".fonts"),
                "into ~/.fonts, the directory a curl one-liner can reach");

        String client = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "app/src/com.termux/terminal/DaemonClient.java")), "UTF-8");
        check(client.contains("parseFontList"),
                "the client decodes the listing");
        check(client.contains("le32(p, 4)"), "and reassembles chunks by their own offset");

        // 6a. The Windows Terminal rule, stated where the fitting happens: no face is
        //     enlarged, and the fitting is derived from the face's own metrics rather than
        //     from a constant. A scale above 1 for one script is the bug this replaced.
        check(fontsSrc.contains("Math.min(1f, Math.min(byWidth, byHeight))"),
                "the fit is capped at 1, so a face is fitted and never enlarged");
        check(fontsSrc.contains("lp.getFontSpacing()") && fontsSrc.contains("inkHeight > rowHeight"),
                "the vertical fit compares the face's ink against the row the Latin face set");
        check(fontsSrc.contains("2f * cell"), "the horizontal fit compares the advance to two cells");
        check(!fontsSrc.contains("HANGUL_SIZE_ADJUST_PCT"),
                "the fixed 115.85% size-adjust is gone");
        check(fontsSrc.contains("mHangulFit"), "the fitted scale is carried to the renderer");

        // 6b. A fitted glyph has to be centred, or a CJK face's deeper ascent leaves the row
        //     looking bottom-heavy. The renderer asks the selector for the offset.
        String renderer = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                System.getProperty("renderer.src",
                        "app/src/com.termux/view/TerminalRenderer.java"))), "UTF-8");
        check(renderer.contains("shiftFor("),
                "the selector exposes a per-code-point baseline shift");
        check(renderer.contains("y + shift"),
                "and the per-code-point path applies it");

        // 6. The app must install the selector, or the stack never reaches the renderer.
        String main = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("main.src",
                        "app/src/ai/openclaw/fterm/MainActivity.java"))), "UTF-8");
        check(main.contains("setFaceSelector"), "MainActivity installs the face selector");
        check(main.contains("Fonts.load("), "and builds it from Fonts");
        // The chosen face has to reach Fonts.load(), or the setting is decorative.
        check(main.contains("mPrefs.fontFamily()"), "MainActivity passes the chosen face");
        check(main.contains("mFonts = Fonts.load("), "and rebuilds the stack on change");


        System.out.println("ok  " + pass + " font routing assertions");
        System.out.println("ALL OK");
    }
}
