package io.github.crashedsystem.terminal;

/**
 * The text rules behind three small conveniences: what a copied selection keeps, which link
 * was under the finger, and how big a paste is.
 *
 * <p>None of the three needs the emulator or a {@code Context} to answer, so none of them
 * lives in the view or the activity. That is on purpose: the emulator tests in
 * {@code tests/jvm} can drive these directly, and the questions they answer - "does this
 * line end in spaces", "is there a URL at this column" - are exactly the ones that break
 * quietly when a regex is tuned by eye.
 */
public final class TextOps {

    /**
     * A paste at or above this many UTF-8 bytes is confirmed before it is sent.
     *
     * <p>5 KB is roughly a screenful of code. Below it, a paste is the user's own action
     * arriving where they aimed it; above it, a stray long clip from the clipboard manager
     * (a log, a whole file) can push a TUI prompt somewhere the user did not intend, and
     * undo in a terminal is not a thing.
     */
    public static final int PASTE_WARN_BYTES = 5120;

    /** Characters that end a URL even though they are printable: markup and shell syntax. */
    private static final String URL_STOP = "<>\"`{|}\\^";

    private TextOps() {}

    // ------------------------------------------------------------------ copy

    /**
     * The selection with each line's trailing spaces and tabs removed.
     *
     * <p>A TUI draws its boxes by padding every line to the same width, so a copy of a
     * rendered table carried a wall of spaces on the right that survives into a chat
     * message, an issue, or a diff. Leading indentation and the line breaks themselves are
     * kept: they are the part that meant something.
     */
    public static String stripTrailingBlanks(String text) {
        if (text == null) return null;
        StringBuilder out = new StringBuilder(text.length());
        int start = 0;
        while (true) {
            int nl = text.indexOf('\n', start);
            int end = nl < 0 ? text.length() : nl;
            int keep = end;
            while (keep > start && (text.charAt(keep - 1) == ' ' || text.charAt(keep - 1) == '\t')) {
                keep--;
            }
            out.append(text, start, keep);
            if (nl < 0) return out.toString();
            out.append('\n');
            start = nl + 1;
        }
    }

    // ------------------------------------------------------------------ paste

    /** The UTF-8 size of the text, which is what the daemon and the PTY actually carry. */
    public static int utf8Length(String text) {
        if (text == null) return 0;
        int bytes = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            bytes += cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
            i += Character.charCount(cp);
        }
        return bytes;
    }

    /** How many lines the paste will become; an empty string is one (empty) line. */
    public static int lineCount(String text) {
        if (text == null || text.isEmpty()) return 1;
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lines++;
        }
        return lines;
    }

    /** Whether this paste is big enough to ask about before sending it. */
    public static boolean needsPasteWarning(String text) {
        return utf8Length(text) >= PASTE_WARN_BYTES;
    }

    // ------------------------------------------------------------------ links

    /**
     * The URL at {@code column} of {@code line}, or null when the tap was not on one.
     *
     * <p>{@code line} is the whole logical line - wrapped rows already joined - and
     * {@code column} is a character index into it, not a display column; the caller gets
     * that index from the terminal buffer, which is where wide characters are known.
     */
    public static String urlAt(String line, int column) {
        if (line == null || line.isEmpty() || column < 0 || column >= line.length()) return null;
        int i = 0;
        while (i < line.length()) {
            int start = findScheme(line, i);
            if (start < 0) return null;
            int end = scanToEnd(line, start);
            String raw = line.substring(start, end);
            String url = trimTrailing(raw);
            if (looksLikeUrl(url) && column >= start && column < start + url.length()) {
                return normalize(url);
            }
            i = Math.max(end, start + 1);
        }
        return null;
    }

    /** {@code www.example.com} is a link a browser understands; give it the scheme it means. */
    public static String normalize(String url) {
        if (url == null) return null;
        return startsWithIgnoreCase(url, "www.") ? "https://" + url : url;
    }

    /** True when the text is shaped like something a browser can open. */
    public static boolean looksLikeUrl(String text) {
        if (text == null) return false;
        if (startsWithIgnoreCase(text, "http://")) return text.length() > 7;
        if (startsWithIgnoreCase(text, "https://")) return text.length() > 8;
        if (startsWithIgnoreCase(text, "www.")) return text.length() > 4;
        return false;
    }

    /**
     * The index of the next scheme marker at or after {@code from}, or -1.
     *
     * <p>A marker in the middle of a word is not a scheme: {@code xhttps://} is a string
     * that happens to contain one, and opening it would be a surprise.
     */
    private static int findScheme(String line, int from) {
        for (int i = from; i < line.length(); i++) {
            char c = line.charAt(i);
            if (i > 0) {
                char prev = line.charAt(i - 1);
                if (Character.isLetterOrDigit(prev) || prev == '_' || prev == '-' || prev == '/'
                        || prev == '.') {
                    continue;
                }
            }
            if (matchesAt(line, i, "https://") || matchesAt(line, i, "http://")
                    || matchesAt(line, i, "www.")) {
                return i;
            }
        }
        return -1;
    }

    /** Where the token that started at {@code start} ends: the first byte a URL cannot hold. */
    private static int scanToEnd(String line, int start) {
        int i = start;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c <= ' ' || c >= 0x7f || URL_STOP.indexOf(c) >= 0) break;
            i++;
        }
        return i;
    }

    /**
     * Drops the punctuation a sentence or a table puts after a link.
     *
     * <p>Periods and commas are never part of a URL that ends there. Brackets are: a
     * Wikipedia link can contain its own {@code (bar)}, so a closing bracket is only
     * dropped when the link has more of them than opening ones.
     */
    static String trimTrailing(String url) {
        String s = url;
        while (!s.isEmpty()) {
            char c = s.charAt(s.length() - 1);
            if (". , ; : ! ? ' \"".indexOf(c) >= 0) {
                s = s.substring(0, s.length() - 1);
            } else if ((c == ')' || c == ']' || c == '}') && closers(s, c) > openers(s, c)) {
                s = s.substring(0, s.length() - 1);
            } else {
                break;
            }
        }
        return s;
    }

    private static int openers(String s, char close) {
        char open = close == ')' ? '(' : close == ']' ? '[' : '{';
        return count(s, open);
    }

    private static int closers(String s, char close) {
        return count(s, close);
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) n++;
        }
        return n;
    }

    private static boolean matchesAt(String s, int at, String what) {
        return at + what.length() <= s.length()
                && s.regionMatches(true, at, what, 0, what.length());
    }

    private static boolean startsWithIgnoreCase(String s, String prefix) {
        return s.length() >= prefix.length() && s.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
