package ai.openclaw.fterm;

import java.util.ArrayList;
import java.util.List;

/**
 * Text search over the terminal's screen and scrollback.
 *
 * <p>Deliberately pure: it takes the lines as strings plus the row number the first line sits
 * on, and returns matches in <em>columns</em>, not character indices. That conversion is the
 * whole reason this is a separate, testable class - the terminal stores wide characters as two
 * columns, so a Korean match's character offset and its column offset differ, and highlighting
 * by character offset would paint over the wrong cells.
 *
 * <p>Matching is case-insensitive by default because a phone has no comfortable way to type
 * capital letters mid-command, and a search that misses because of case reads as a bug.
 */
public final class Search {

    /** One match: the external row it sits on, and its half-open column range. */
    public static final class Hit {
        public final int row;
        public final int startCol;
        public final int endCol;

        public Hit(int row, int startCol, int endCol) {
            this.row = row;
            this.startCol = startCol;
            this.endCol = endCol;
        }

        public boolean sameAs(Hit other) {
            return other != null && row == other.row
                    && startCol == other.startCol && endCol == other.endCol;
        }
    }

    private Search() {}

    /**
     * Every occurrence, top to bottom, left to right.
     *
     * <p>Overlaps are not reported: searching "aa" in "aaaa" finds two matches, not three,
     * which is what a user paging through results expects.
     */
    public static List<Hit> find(String[] lines, int firstRow, String needle, boolean caseSensitive) {
        List<Hit> out = new ArrayList<>();
        if (lines == null || needle == null || needle.isEmpty()) return out;

        String n = caseSensitive ? needle : needle.toLowerCase();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isEmpty()) continue;
            String hay = caseSensitive ? line : line.toLowerCase();
            // toLowerCase() can change a string's length in a few locales, in which case the
            // indices would not map back to the original text; skip the line rather than
            // highlight the wrong cells.
            if (hay.length() != line.length()) continue;

            int at = hay.indexOf(n);
            while (at >= 0) {
                int startCol = columnOf(line, at);
                int endCol = columnOf(line, at + needle.length());
                out.add(new Hit(firstRow + i, startCol, endCol));
                at = hay.indexOf(n, at + needle.length());
            }
        }
        return out;
    }

    /**
     * The column a character index sits at, counting wide characters as two.
     *
     * <p>{@code index} may be the string's length, which is the exclusive end of a match.
     */
    public static int columnOf(String line, int index) {
        int col = 0;
        int i = 0;
        while (i < index && i < line.length()) {
            int cp = line.codePointAt(i);
            int step = Character.charCount(cp);
            int w = com.termux.terminal.WcWidth.width(cp);
            col += Math.max(w, 1);
            i += step;
        }
        return col;
    }
}
