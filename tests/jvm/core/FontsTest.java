/**
 * The font stack's routing rules, checked against the app's own constants.
 *
 * mterm's terminal type is three faces: Iosevka for Latin, a Nerd symbols face for icons,
 * Pretendard for Hangul, with a size-adjust on the Hangul face so one syllable is exactly
 * two Latin cells. The *metrics* are asserted by `tests/fonts_check.py` (fontTools can read
 * a TTF; this JVM cannot), and this test asserts the half that lives in the app: which face
 * answers which code point, and that the size-adjust constant is the one mterm measured.
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
        // 1. The constant is mterm's measured value.
        near(ai.openclaw.fterm.Fonts.HANGUL_SIZE_ADJUST_PCT, 115.85, 0.001,
                "HANGUL_SIZE_ADJUST_PCT");

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

        // 5. Only Hangul gets a size multiplier; everything else is 1.0.
        check(ai.openclaw.fterm.Fonts.isHangul(0xD55C), "한 is scaled");
        check(!ai.openclaw.fterm.Fonts.isHangul('x'), "x is not scaled");

        // 5b. The Latin face is chosen by id, and an unknown id means the default rather
        // than a crash: a prefs file written by a future build must not break this one.
        check(ai.openclaw.fterm.Fonts.latinIndexOf("iosevka") == 0, "iosevka is slot 0");
        check(ai.openclaw.fterm.Fonts.latinIndexOf("droid") == 1, "droid is slot 1");
        check(ai.openclaw.fterm.Fonts.latinIndexOf("cutive") == 2, "cutive is slot 2");
        check(ai.openclaw.fterm.Fonts.latinIndexOf("comic-sans") == 0,
                "an unknown id falls back to the default");
        check(ai.openclaw.fterm.Fonts.latinIndexOf(null) == 0, "and null does too");
        check(ai.openclaw.fterm.Fonts.LATIN_NAMES.length
                == ai.openclaw.fterm.Fonts.LATIN_IDS.length, "names and ids stay in step");
        check(ai.openclaw.fterm.Fonts.LATIN_PATHS.length
                == ai.openclaw.fterm.Fonts.LATIN_IDS.length, "and paths too");
        check(ai.openclaw.fterm.Fonts.LATIN_PATHS[0] == null,
                "slot 0 is the bundled asset, not a system file");

        // 6. The app must install the selector, or the stack never reaches the renderer.
        String main = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("main.src",
                        "app/src/ai/openclaw/fterm/MainActivity.java"))), "UTF-8");
        check(main.contains("setFaceSelector"), "MainActivity installs the face selector");
        check(main.contains("Fonts.load("), "and builds it from Fonts");
        // The chosen face has to reach Fonts.load(), or the setting is decorative.
        check(main.contains("mPrefs.fontFamily()"), "MainActivity passes the chosen face");
        check(main.contains("mFonts = Fonts.load("), "and rebuilds the stack on change");

        // The real Fonts and the stub must agree on the table, or the test above proves
        // nothing about the shipped code.
        String fonts = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("fonts.src",
                        "app/src/ai/openclaw/fterm/Fonts.java"))), "UTF-8");
        for (String name : ai.openclaw.fterm.Fonts.LATIN_NAMES) {
            check(fonts.contains("\"" + name + "\""), "real Fonts names " + name);
        }
        for (String id : ai.openclaw.fterm.Fonts.LATIN_IDS) {
            check(fonts.contains("\"" + id + "\""), "real Fonts knows id " + id);
        }

        System.out.println("ok  " + pass + " font routing assertions");
        System.out.println("ALL OK");
    }
}
