/**
 * A long tab title must be readable on the active tab, without widening the strip.
 *
 * <p>The title used to be truncated with an ellipsis, so a shell whose OSC title is a full
 * command line showed "vim ~/projects/fterm/app/src/com…" and the only way to read the rest was
 * to rename the tab by hand. The user asked for the text to flow like a billboard instead.
 *
 * <p>Android's marquee is easy to wire in a way that looks right and never moves, so this
 * checks each of the four conditions it actually needs - and, because the static half cannot see
 * a running TextView, it also asserts the two properties that make the marquee a feature rather
 * than a distraction: it is scoped to the active tab, and a title change restarts it.
 */
public class TabTitleTest {

    static int pass = 0;

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
        pass++;
    }

    static int countOccurrences(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) n++;
        return n;
    }

    public static void main(String[] args) throws Exception {
        String src = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("main.src",
                        "app/src/io/github/crashedsystem/fterm/MainActivity.java"))), "UTF-8");

        // The chip is built by TabStrip, so that is where the marquee settings live now.
        String strip = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("strip.src",
                        "app/src/io/github/crashedsystem/fterm/TabStrip.java"))), "UTF-8");
        String chip = strip;

        // 1. The four things a marquee needs, and it silently degrades if any is missing.
        check(chip.contains("TruncateAt.MARQUEE"), "the title uses the marquee ellipsizer");
        check(chip.contains("setHorizontallyScrolling(true)"),
                "and is allowed to scroll horizontally");
        check(chip.contains("setMarqueeRepeatLimit"), "and repeats instead of stopping once");
        check(chip.contains("setMaxWidth("),
                "and has a bounded width - an unbounded title has nothing to scroll");
        check(!chip.contains("TruncateAt.END"),
                "the old ellipsis truncation is gone, or it would clip before scrolling");

        // 2. Only the active tab scrolls. setSelected is what the platform's marquee watches,
        //    so this is the switch, not a decoration. Comments are stripped first: the source
        //    explains the API by name in prose, and matching that would pass regardless of code.
        String update = strip.substring(strip.indexOf("void paint(Chip chip"));
        update = update.substring(0, Math.min(update.length(), 1800));
        StringBuilder code = new StringBuilder();
        for (String line : update.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue;
            code.append(line).append('\n');
        }
        String updateCode = code.toString();
        check(updateCode.contains("setSelected(active)"),
                "the marquee is enabled for the active tab only");
        check(!updateCode.contains("setSelected(true)"),
                "and is not forced on for every tab");
        // 3. A new title must scroll from the start, which needs the toggle, not just true.
        check(countOccurrences(updateCode, "setSelected(false)") >= 1,
                "and the selection is cleared first so a new title starts at column zero");

        // 4. The user can still opt out by renaming: a user title must survive the shell's OSC.
        check(src.contains("if (t.userTitled) return;"),
                "a renamed tab still ignores the shell's title");

        // 5. The tab's mark is a preference, and it is not hard-coded in the chip.
        //
        //    ">_" was a literal in TabStrip, so there was no way to change it short of
        //    editing the source. It is a preference now, and the two things that make that
        //    real are: the value reaches the chip builder, and the chip knows how to rebuild
        //    the mark when the value changes (Glyph bakes its colour in at construction, so
        //    a changed tint is a new drawable, and a changed kind is a new view).
        check(!strip.contains("setText(\">_\")"),
                "the default mark is no longer a literal in the chip");
        check(src.contains("mPrefs.tabIcon()"),
                "the chip is built from the tab icon preference");
        check(src.contains("refreshTabIcons()"),
                "and existing tabs are repainted when it changes");
        check(strip.contains("chip.iconTag"),
                "the chip remembers what the mark was built from");
        check(strip.contains("TabIcons.build("),
                "and rebuilds it through the one place that knows both forms");

        String icons = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("app/src/io/github/crashedsystem/fterm/TabIcons.java")), "UTF-8");
        check(icons.contains("DEFAULT_ID = \">_\""), "the default is the prompt >_");
        check(icons.contains("isGlyph("), "a value is either a glyph id or text");
        // Unknown input must not be a blank slot: a tab with no mark reads as a bug.
        check(icons.contains("DEFAULT_ID : id"), "an empty preference falls back to the default");

        System.out.println("TabTitleTest: " + pass + " assertions OK");
    }
}
