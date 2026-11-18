/**
 * Search's two hard parts: columns instead of character indices, and being total.
 *
 * The column mapping is the reason this is a class instead of an inline indexOf: a Korean
 * character is one character and two columns, so highlighting by character offset paints over
 * the wrong cells on exactly the lines a Korean user searches most.
 */
public class SearchTest {

    static int pass = 0;

    static void check(boolean c, String what) {
        if (!c) throw new AssertionError(what);
        pass++;
    }

    public static void main(String[] args) {
        String[] lines = {
                "hello world",
                "hello again",
                "",
                null,
        };

        // 1. Every occurrence across lines, with the row it sits on.
        java.util.List<ai.openclaw.fterm.Search.Hit> hits =
                ai.openclaw.fterm.Search.find(lines, -2, "hello", true);
        check(hits.size() == 2, "two matches");
        check(hits.get(0).row == -2, "the first is on the first row");
        check(hits.get(0).startCol == 0 && hits.get(0).endCol == 5, "and spans columns 0..5");
        check(hits.get(1).row == -1, "the second is one row down");

        // 2. Case-insensitive by default, and the case-sensitive mode still works.
        check(ai.openclaw.fterm.Search.find(lines, 0, "HELLO", false).size() == 2,
                "case-insensitive finds both");
        check(ai.openclaw.fterm.Search.find(lines, 0, "HELLO", true).isEmpty(),
                "case-sensitive finds none");

        // 3. Wide characters: one character, two columns.
        String[] korean = {"가나다"};
        java.util.List<ai.openclaw.fterm.Search.Hit> k =
                ai.openclaw.fterm.Search.find(korean, 0, "나", true);
        check(k.size() == 1, "a Korean match is found");
        check(k.get(0).startCol == 2 && k.get(0).endCol == 4,
                "and it spans two columns, not one (" + k.get(0).startCol + ".." + k.get(0).endCol + ")");

        // 4. Mixed: a wide char before an ASCII match must shift the ASCII column by two.
        String[] mixed = {"한a"};
        java.util.List<ai.openclaw.fterm.Search.Hit> m =
                ai.openclaw.fterm.Search.find(mixed, 0, "a", true);
        check(m.size() == 1 && m.get(0).startCol == 2,
                "ASCII after a wide char starts at column 2 (" + m.get(0).startCol + ")");

        // 5. Overlaps are not reported: "aa" in "aaaa" is two matches, not three.
        check(ai.openclaw.fterm.Search.find(new String[]{"aaaa"}, 0, "aa", true).size() == 2,
                "overlapping matches are counted once each");

        // 6. Total: nothing here throws.
        check(ai.openclaw.fterm.Search.find(null, 0, "x", true).isEmpty(), "null lines");
        check(ai.openclaw.fterm.Search.find(lines, 0, "", true).isEmpty(), "empty needle");
        check(ai.openclaw.fterm.Search.find(lines, 0, null, true).isEmpty(), "null needle");
        check(ai.openclaw.fterm.Search.find(new String[0], 0, "x", true).isEmpty(), "no lines");

        // 7. Empty and null lines are skipped, not matched at column 0.
        check(ai.openclaw.fterm.Search.find(new String[]{"", null, "x"}, 0, "x", true).size() == 1,
                "blank lines cannot match");

        // 8. columnOf is measured against the emulator's own width rule.
        check(ai.openclaw.fterm.Search.columnOf("abc", 0) == 0, "index 0 is column 0");
        check(ai.openclaw.fterm.Search.columnOf("abc", 3) == 3, "the end index is the end column");
        check(ai.openclaw.fterm.Search.columnOf("한글", 1) == 2, "one wide char is two columns");

        System.out.println("ok  " + pass + " search assertions");
        System.out.println("ALL OK");
    }
}
