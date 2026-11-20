/**
 * The background picture's pure rules: where it is drawn, and how its sliders clamp.
 *
 * <p>The drawable itself needs a Canvas, but the geometry is where a bug looks like "the
 * picture is cut off" or "it sits off centre", and that is checkable here. The structural
 * half pins the thing that motivated the change: no copyrighted asset and no per-scheme
 * picture hard-coding left in the public repository.
 */
public class BackgroundTest {

    static int pass = 0;

    static void eq(Object actual, Object expected, String what) {
        if (!java.util.Objects.equals(actual, expected)) {
            throw new AssertionError(what + ": expected <" + expected + "> got <" + actual);
        }
        pass++;
    }

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
        pass++;
    }

    static String read(String path) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                "UTF-8");
    }

    public static void main(String[] args) throws Exception {
        // --- fit geometry ------------------------------------------------------
        // 100x100 picture in a 100x200 terminal: contain fits the width, cover fills the
        // height and crops the sides.
        int[] contain = io.github.crashedsystem.terminal.BackgroundSpec.place(100, 200, 100, 100, "contain");
        eq(contain[2], 100, "contain keeps the picture's width");
        eq(contain[3], 100, "contain keeps its aspect");
        eq(contain[0], 0, "contain centres horizontally");
        eq(contain[1], 50, "contain centres vertically");

        int[] cover = io.github.crashedsystem.terminal.BackgroundSpec.place(100, 200, 100, 100, "cover");
        eq(cover[2], 200, "cover scales to the tall side");
        eq(cover[3], 200, "cover keeps aspect while covering");
        eq(cover[0], -50, "cover centres by overflowing both sides");
        eq(cover[1], 0, "cover does not overflow the axis it fills");

        int[] wide = io.github.crashedsystem.terminal.BackgroundSpec.place(200, 100, 100, 100, "contain");
        eq(wide[0], 50, "a wide terminal letterboxes on the left");
        eq(wide[1], 0, "and not vertically");
        eq(wide[2], 100, "at the picture's size");

        int[] empty = io.github.crashedsystem.terminal.BackgroundSpec.place(0, 0, 100, 100, "cover");
        eq(empty[2], 0, "a zero-sized terminal draws nothing");
        eq(empty[3], 0, "and asks for no rectangle");

        // A 3x2 picture in a 3x3 terminal: contain rounds to 3x2, centred with the extra pixel
        // at the bottom.
        int[] odd = io.github.crashedsystem.terminal.BackgroundSpec.place(3, 3, 3, 2, "contain");
        eq(odd[3], 2, "odd rounding keeps the picture's height");
        eq(odd[1], 0, "and centres it as closely as integers allow");

        // --- alignment ---------------------------------------------------------
        // Windows Terminal's nine names. A theme that ships "bottomRight" has to land in the
        // bottom-right corner here too, or importing the theme means moving the picture by hand.
        eq(io.github.crashedsystem.terminal.BackgroundSpec.align("bottomRight"), "bottomRight",
                "a WT alignment name passes through");
        eq(io.github.crashedsystem.terminal.BackgroundSpec.align("BOTTOMRIGHT"), "bottomRight",
                "case does not matter");
        eq(io.github.crashedsystem.terminal.BackgroundSpec.align(null),
                io.github.crashedsystem.terminal.BackgroundSpec.DEFAULT_ALIGN, "absent means centre");
        eq(io.github.crashedsystem.terminal.BackgroundSpec.align("middle-ish"), "center",
                "an unknown name falls back to centre rather than throwing");
        eq(io.github.crashedsystem.terminal.BackgroundSpec.ALIGN_NAMES.length, 9, "nine placements");

        // A 100x100 picture in a 100x200 terminal, contain: 100x100 free vertically.
        int[] tl = io.github.crashedsystem.terminal.BackgroundSpec.place(100, 200, 100, 100, "contain", "topLeft");
        eq(tl[0], 0, "topLeft is flush left");
        eq(tl[1], 0, "and flush to the top");
        int[] br = io.github.crashedsystem.terminal.BackgroundSpec.place(
                100, 200, 100, 100, "contain", "bottomRight");
        eq(br[0], 0, "bottomRight does not move horizontally when the width already fits");
        eq(br[1], 100, "and sits at the bottom");
        int[] bc = io.github.crashedsystem.terminal.BackgroundSpec.place(100, 200, 100, 100, "contain", "bottom");
        eq(bc[0], 0, "bottom is horizontally centred");
        eq(bc[1], 100, "and at the bottom");
        int[] top = io.github.crashedsystem.terminal.BackgroundSpec.place(200, 100, 100, 100, "contain", "top");
        eq(top[0], 50, "top is horizontally centred");
        eq(top[1], 0, "and at the top");
        int[] lf = io.github.crashedsystem.terminal.BackgroundSpec.place(200, 100, 100, 100, "contain", "left");
        eq(lf[0], 0, "left letterboxes on the left");
        eq(lf[1], 0, "and centres vertically");
        int[] rt = io.github.crashedsystem.terminal.BackgroundSpec.place(200, 100, 100, 100, "contain", "right");
        eq(rt[0], 100, "right pushes the picture to the right edge");

        // With cover the rectangle overflows; bottomRight keeps the overflow on the left/top so
        // the bottom-right corner of the picture is the corner the user sees.
        int[] cbr = io.github.crashedsystem.terminal.BackgroundSpec.place(
                100, 200, 100, 100, "cover", "bottomRight");
        eq(cbr[2], 200, "cover still scales to the tall side");
        eq(cbr[0], -100, "and the overflow moves off the right edge");

        // The five-argument form is the pre-alignment behaviour and must not have changed.
        int[] noArg = io.github.crashedsystem.terminal.BackgroundSpec.place(100, 200, 100, 100, "contain");
        int[] centred = io.github.crashedsystem.terminal.BackgroundSpec.place(
                100, 200, 100, 100, "contain", "center");
        eq(noArg[0], centred[0], "no alignment argument is the same rect as centre (x)");
        eq(noArg[1], centred[1], "no alignment argument is the same rect as centre (y)");

        // --- clamps ------------------------------------------------------------
        eq(io.github.crashedsystem.terminal.BackgroundSpec.opacity(-5), 0, "opacity floor");
        eq(io.github.crashedsystem.terminal.BackgroundSpec.opacity(150), 100, "opacity ceiling");
        eq(io.github.crashedsystem.terminal.BackgroundSpec.opacity(70), 70, "opacity passes through");
        eq(io.github.crashedsystem.terminal.BackgroundSpec.dim(-1), 0, "dim floor");
        eq(io.github.crashedsystem.terminal.BackgroundSpec.dim(200), io.github.crashedsystem.terminal.BackgroundSpec.MAX_DIM,
                "dim ceiling is below fully black");
        eq(io.github.crashedsystem.terminal.BackgroundSpec.dim(25), 25, "dim passes through");

        // --- fit names ---------------------------------------------------------
        eq(io.github.crashedsystem.terminal.BackgroundSpec.fit(null),
                io.github.crashedsystem.terminal.BackgroundSpec.FIT_CONTAIN, "absent fit is contain");
        eq(io.github.crashedsystem.terminal.BackgroundSpec.fit("COVER"),
                io.github.crashedsystem.terminal.BackgroundSpec.FIT_COVER, "fit is case-insensitive");
        eq(io.github.crashedsystem.terminal.BackgroundSpec.fit("stretch"),
                io.github.crashedsystem.terminal.BackgroundSpec.FIT_CONTAIN, "unknown fit falls back safely");
        check(io.github.crashedsystem.terminal.BackgroundSpec.fitLabel("cover").contains("cover"),
                "the row names the mode the file will use");

        // --- which files count -------------------------------------------------
        check(io.github.crashedsystem.terminal.BackgroundSpec.isImageName("miku-dusk.png"), "png");
        check(io.github.crashedsystem.terminal.BackgroundSpec.isImageName("PHOTO.JPG"), "jpg, any case");
        check(io.github.crashedsystem.terminal.BackgroundSpec.isImageName("a.webp"), "webp");
        check(io.github.crashedsystem.terminal.BackgroundSpec.isImageName("scan.bmp"), "bmp");
        check(!io.github.crashedsystem.terminal.BackgroundSpec.isImageName("background.tmp"),
                "the half-written temp file is not a picture");
        check(!io.github.crashedsystem.terminal.BackgroundSpec.isImageName("README.txt"), "text is not");
        check(!io.github.crashedsystem.terminal.BackgroundSpec.isImageName(null), "null is not");

        // --- structure: the overlay workflow is gone ---------------------------
        String manifest = read("AndroidManifest.xml");
        check(manifest.contains("io.github.crashedsystem.terminal.BackgroundReceiver"),
                "the script door is declared, not implied");
        check(manifest.contains("io.github.crashedsystem.terminal.action.APPLY_BACKGROUND"),
                "with the action a script sends");
        check(manifest.contains("io.github.crashedsystem.terminal.action.CLEAR_BACKGROUND"),
                "and the one that removes the picture");

        String build = read("build.sh");
        check(!build.contains("miku-dusk"), "the build no longer copies a theme asset");
        check(!build.contains("assets/img"), "and no longer looks in the asset folder");

        String main = read("app/src/io/github/crashedsystem/terminal/MainActivity.java");
        check(!main.toLowerCase().contains("miku"), "no hard-coded picture is named");
        check(main.contains("new BackgroundLayer("), "the picture lives in its own layer");
        check(!main.contains("setBackgroundImage("), "not wired inline in the activity");

        String appearance = read("app/src/io/github/crashedsystem/terminal/SettingsAppearancePage.java");
        // The heading was extracted into resources for translation, so the page now references
        // it by key. The text itself is still asserted - in the XML, where it lives.
        check(appearance.contains("i18n_0051"), "the appearance page offers the setting");
        check(read("res/values/strings.xml").contains(">배경 이미지<"),
                "and the heading text is the one a user reads");
        check(appearance.contains("sheet.background().rows("),
                "and its rows are a picker class, not inlined");

        String store = read("app/src/io/github/crashedsystem/terminal/BackgroundStore.java");
        check(store.contains("ACTION_APPLY"), "the store names the script action");
        check(store.contains("getExternalFilesDir"), "the folder is reachable by adb");
        check(store.contains("MAX_SIDE"), "and a large photo is downscaled before drawing");

        // The old drawable and its asset path must not survive anywhere in app/src.
        java.nio.file.Path src = java.nio.file.Paths.get("app/src");
        java.util.List<String> offenders = new java.util.ArrayList<>();
        java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(src);
        walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
            try {
                String text = new String(java.nio.file.Files.readAllBytes(p), "UTF-8");
                if (text.contains("MikuBackground") || text.contains("img/miku-dusk")
                        || text.contains("hasBackgroundImage")) {
                    offenders.add(p.toString());
                }
            } catch (Exception ignored) {
                // A file that cannot be read cannot be an offender here.
            }
        });
        walk.close();
        check(offenders.isEmpty(), "no source still points at the theme asset: " + offenders);

        System.out.println("BackgroundTest ALL OK (" + pass + " assertions)");
    }
}
