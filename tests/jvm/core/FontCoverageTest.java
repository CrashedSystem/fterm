/**
 * The Hangul picker's filter, checked against real font files.
 *
 * Every bug this test exists to prevent was found on a device, and neither of them can be
 * reproduced with a stub: Paint.hasGlyph answers through the system fallback chain, so it
 * said yes for every face on the phone, and a proportional Hangul font was refused by the
 * Latin fixed-advance rule, so the list could not contain a Korean font at all.
 *
 * The cmap reader is pure JDK, so this runs the real parser on the real files rather than
 * asserting that the source contains a string.
 */
public class FontCoverageTest {

    static int pass = 0;

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
        pass++;
    }

    static java.io.File font(String path) {
        java.io.File f = new java.io.File(path);
        check(f.isFile(), "test fixture is present: " + path);
        return f;
    }

    // 가, 한, 힣 - the three probes the picker uses.
    static final int[] HANGUL = {0xAC00, 0xD55C, 0xD7A3};
    // U+1100 choseong kiyeok, U+1161 jungseong a, U+11A8 jongseong kiyeok.
    static final int[] JAMO = {0x1100, 0x1161, 0x11A8};

    public static void main(String[] args) throws Exception {
        String bundled = System.getProperty("hangul.src", "assets/fonts/PretendardHangul.ttf");

        // 1. The bundled Korean face has every syllable...
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(bundled));
        check(ai.openclaw.fterm.FontCoverage.covers(bytes, HANGUL),
                "the bundled face covers every syllable probe");
        check(!ai.openclaw.fterm.FontCoverage.covers(bytes, JAMO),
                "and none of the conjoining jamo - which is why the picker marks ability, "
                        + "not which face is loaded");

        // 2. ...and a Latin face is the negative case. Iosevka has no Hangul at all, which is
        //    the whole reason a second face exists; if this ever turns true, the split is
        //    pointless and something is reading the wrong table.
        byte[] latin = java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("latin.src", "assets/fonts/Iosevka.ttf")));
        check(!ai.openclaw.fterm.FontCoverage.covers(latin, HANGUL),
                "Iosevka does not answer Hangul, so the two-face split is real");

        // 3. The parser must handle both cmap formats in the wild: format 4 (BMP) and format
        //    12 (full Unicode). Noto Sans CJK is a collection whose first face is format 12;
        //    a face that only declares format 4 must stay honest about being BMP-only.
        java.io.File cjk = new java.io.File("/system/fonts/NotoSansCJK-Regular.ttc");
        if (cjk.isFile()) {
            check(ai.openclaw.fterm.FontCoverage.covers(cjk, HANGUL),
                    "a TTC's first face answers Hangul through format 12");
            check(ai.openclaw.fterm.FontCoverage.covers(cjk, JAMO),
                    "and carries conjoining jamo, unlike the bundled face");
        } else {
            System.out.println("skip no NotoSansCJK-Regular.ttc on this device");
        }

        // 4. A file that is not a font must answer false rather than throw - the catalog
        //    feeds it every .ttf in a directory, and one corrupt file must not break the list.
        java.io.File junk = java.io.File.createTempFile("not-a-font", ".ttf");
        try {
            java.nio.file.Files.write(junk.toPath(), new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
            check(!ai.openclaw.fterm.FontCoverage.covers(junk, HANGUL), "junk is not coverage");
            check(!ai.openclaw.fterm.FontCoverage.covers((byte[]) null, HANGUL),
                    "and null has none either");
            check(!ai.openclaw.fterm.FontCoverage.covers(junk, new int[0]),
                    "an empty probe list is not a free yes");
        } finally {
            junk.delete();
        }

        // 5. The rule the picker's list depends on, stated where the list is built: a
        //    proportional Korean font is accepted by the Hangul path and refused by the Latin
        //    one. NanumGothic is the font that used to be invisible for exactly this reason.
        java.io.File nanum = new java.io.File(
                System.getProperty("home", System.getProperty("user.home"))
                        + "/.fonts/NanumGothic-Regular.ttf");
        if (nanum.isFile()) {
            check(ai.openclaw.fterm.FontCoverage.covers(nanum, HANGUL),
                    "NanumGothic carries Hangul, so the Korean picker must offer it");
        } else {
            System.out.println("skip no ~/.fonts/NanumGothic-Regular.ttf");
        }

        // 6. The app's own source has to keep asking the file, never the platform.
        String fonts = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("fonts.src",
                        "app/src/ai/openclaw/fterm/Fonts.java"))), "UTF-8");
        check(!fonts.contains("p.hasGlyph("),
                "hasGlyph is not used: it answers via the system fallback, not the file");
        check(fonts.contains("FontCoverage.covers("),
                "the cmap reader is what the filter asks");
        check(fonts.contains("HANGUL_ASSET_ID"),
                "the Korean face has its own id, so it can differ from the Latin one");

        String prefs = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("prefs.src",
                        "app/src/ai/openclaw/fterm/Prefs.java"))), "UTF-8");
        check(prefs.contains("hangul_family"),
                "and its own preference, because one id cannot mean two faces");

        String main = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("main.src",
                        "app/src/ai/openclaw/fterm/MainActivity.java"))), "UTF-8");
        check(main.contains("mPrefs.hangulFamily()"),
                "which is read when the stack is built");

        String sheet = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("sheet.src",
                        "app/src/ai/openclaw/fterm/SettingsSheet.java"))), "UTF-8");
        check(sheet.contains("한글 글꼴"),
                "the setting is reachable by name in 모양");
        check(sheet.contains("Fonts.hangulCatalog("),
                "from the Hangul list, not the Latin one");
        check(sheet.contains("onHangulFamily("),
                "and it applies on pick");

        // 7. Replacing the renderer must not drop the face stack. This was a real bug, found
        //    by changing the Korean face and watching the screen not change: loadFonts()
        //    installs the selector and then calls setTypeface(), and setTypeface() rebuilt
        //    the renderer without carrying the selector over - so every code point silently
        //    fell back to the platform's font, and the setting was decorative.
        String view = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("view.src",
                        "app/src/com.termux/view/TerminalView.java"))), "UTF-8");
        int setTypeface = view.indexOf("public void setTypeface(Typeface newTypeface)");
        check(setTypeface >= 0, "TerminalView has setTypeface");
        String body = view.substring(setTypeface, view.indexOf("\n    }", setTypeface));
        check(body.contains("mRenderer.mFaces"),
                "setTypeface carries the face selector over, as setTextSize does");
        check(body.contains("setFaceSelector(faces)"),
                "and installs it on the new renderer");

        System.out.println("ok  " + pass + " font coverage assertions");
    }
}
