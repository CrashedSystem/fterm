/**
 * The three text rules the terminal now leans on: what a copy keeps, which link a tap found,
 * and when a paste is big enough to ask about.
 *
 * <p>They are pure functions in {@code TextOps} precisely so they can be checked here, one
 * case at a time - a URL at the end of a sentence, a TUI's box padding, a Korean word glued
 * to a link - instead of by tapping a phone and squinting.
 */
public class TextOpsTest {

    static int pass = 0;

    static void eq(Object actual, Object expected, String what) {
        if (!java.util.Objects.equals(actual, expected)) {
            throw new AssertionError(what + ": expected <" + expected + "> got <" + actual + ">");
        }
        pass++;
    }

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
        pass++;
    }

    public static void main(String[] args) {
        strip();
        sizes();
        links();
        System.out.println("ok  " + pass + " text assertions");
        System.out.println("ALL OK");
    }

    // ------------------------------------------------------------------ copy

    static void strip() {
        // A TUI pads every line of a box to the same width; the copy should not carry it.
        eq(io.github.crashedsystem.terminal.TextOps.stripTrailingBlanks(
                        "┌────┐   \n│ hi │   \n└────┘   "),
                "┌────┐\n│ hi │\n└────┘",
                "the box's padding is gone, the box is not");
        // Leading indentation and the line breaks are the part that meant something.
        eq(io.github.crashedsystem.terminal.TextOps.stripTrailingBlanks("    indented  \n\tone tab\t\n"),
                "    indented\n\tone tab\n",
                "indentation stays, the break at the end stays");
        eq(io.github.crashedsystem.terminal.TextOps.stripTrailingBlanks("a \t \n \t\nb"),
                "a\n\nb", "a whitespace-only line becomes empty rather than disappearing");
        eq(io.github.crashedsystem.terminal.TextOps.stripTrailingBlanks(""),
                "", "an empty selection stays empty");
        eq(io.github.crashedsystem.terminal.TextOps.stripTrailingBlanks("no trailing blanks"),
                "no trailing blanks", "ordinary text is untouched");
        // \r is not a blank to remove: it is a character the program sent.
        eq(io.github.crashedsystem.terminal.TextOps.stripTrailingBlanks("carriage\r\n"),
                "carriage\r\n", "CRLF keeps its CR");
    }

    // ------------------------------------------------------------------ paste

    static void sizes() {
        eq(io.github.crashedsystem.terminal.TextOps.utf8Length("abc"), 3, "ASCII is one byte");
        eq(io.github.crashedsystem.terminal.TextOps.utf8Length("한글"), 6, "Hangul is three bytes");
        eq(io.github.crashedsystem.terminal.TextOps.utf8Length("é"), 2, "accented latin is two");
        eq(io.github.crashedsystem.terminal.TextOps.utf8Length("\uD83D\uDE00"), 4, "an emoji is four");
        eq(io.github.crashedsystem.terminal.TextOps.utf8Length("a\nb"), 3, "the newline counts");

        eq(io.github.crashedsystem.terminal.TextOps.lineCount("a\nb\nc"), 3, "three lines");
        eq(io.github.crashedsystem.terminal.TextOps.lineCount("a\n"), 2, "a trailing newline starts one");
        eq(io.github.crashedsystem.terminal.TextOps.lineCount(""), 1, "an empty paste is one line");

        // 5119 bytes is a big paste and still goes straight through; 5120 asks.
        StringBuilder under = new StringBuilder();
        for (int i = 0; i < 5119; i++) under.append('x');
        check(!io.github.crashedsystem.terminal.TextOps.needsPasteWarning(under.toString()),
                "5119 bytes does not warn");
        under.append('x');
        check(io.github.crashedsystem.terminal.TextOps.needsPasteWarning(under.toString()),
                "5120 bytes warns");
        check(io.github.crashedsystem.terminal.TextOps.needsPasteWarning("한".repeat(1707)),
                "5121 bytes of Hangul warn through their UTF-8 size, not their length");
        check(!io.github.crashedsystem.terminal.TextOps.needsPasteWarning("ls -la"),
                "a short paste never warns");
    }

    // ------------------------------------------------------------------ links

    static void links() {
        String line = "see https://example.com/docs for more";
        eq(io.github.crashedsystem.terminal.TextOps.urlAt(line, 10), "https://example.com/docs",
                "a URL in the middle of a sentence is found");
        eq(io.github.crashedsystem.terminal.TextOps.urlAt(line, 1), null,
                "tapping the word before it finds nothing");
        eq(io.github.crashedsystem.terminal.TextOps.urlAt(line, 32), null,
                "tapping the word after it finds nothing");
        eq(io.github.crashedsystem.terminal.TextOps.urlAt(line, line.length() - 1), null,
                "tapping past the URL finds nothing");

        // A sentence's punctuation is not part of the link.
        eq(io.github.crashedsystem.terminal.TextOps.urlAt("(https://example.com/a).", 5),
                "https://example.com/a", "a trailing period and bracket are dropped");
        eq(io.github.crashedsystem.terminal.TextOps.urlAt("\"https://example.com/q\"", 3),
                "https://example.com/q", "a trailing quote is dropped");
        // ...but a bracket the link itself opened is.
        eq(io.github.crashedsystem.terminal.TextOps.urlAt(
                        "https://en.wikipedia.org/wiki/Foo_(bar)", 10),
                "https://en.wikipedia.org/wiki/Foo_(bar)", "balanced brackets survive");
        eq(io.github.crashedsystem.terminal.TextOps.urlAt("https://x.com]", 2),
                "https://x.com", "an unmatched closing bracket is dropped");

        // www. is a link too, and the scheme it means is https.
        eq(io.github.crashedsystem.terminal.TextOps.urlAt("visit www.example.com now", 8),
                "https://www.example.com", "www. gets https://");
        eq(io.github.crashedsystem.terminal.TextOps.urlAt("www.example.com", 0),
                "https://www.example.com", "a line that starts with the link works");

        // A TUI draws boxes around its text; the border is not a URL character.
        eq(io.github.crashedsystem.terminal.TextOps.urlAt("│ https://a.com/x │", 4),
                "https://a.com/x", "box drawing characters stop the token");
        eq(io.github.crashedsystem.terminal.TextOps.urlAt("https://a.com입니다", 2),
                "https://a.com", "non-ASCII text glued to a link is not part of it");

        // Not links.
        eq(io.github.crashedsystem.terminal.TextOps.urlAt("no links here", 3), null,
                "plain text has none");
        eq(io.github.crashedsystem.terminal.TextOps.urlAt("https://", 3), null,
                "a bare scheme is not a link");
        eq(io.github.crashedsystem.terminal.TextOps.urlAt("xhttps://a.com", 4), null,
                "a marker inside a word is not a scheme");

        // Two links on one line: the tap decides which.
        String two = "https://a.com and https://b.com";
        eq(io.github.crashedsystem.terminal.TextOps.urlAt(two, 2), "https://a.com",
                "the first link answers near the start");
        eq(io.github.crashedsystem.terminal.TextOps.urlAt(two, 20), "https://b.com",
                "and the second after the space");

        // Query strings and ports are part of the URL.
        eq(io.github.crashedsystem.terminal.TextOps.urlAt(
                        "http://127.0.0.1:8791/x?y=1&z=2", 8),
                "http://127.0.0.1:8791/x?y=1&z=2", "port, path and query survive");
        // A scheme in capitals is still a scheme; the URL is returned as printed.
        eq(io.github.crashedsystem.terminal.TextOps.urlAt("HTTPS://EXAMPLE.COM/A", 3),
                "HTTPS://EXAMPLE.COM/A", "case is preserved");

        eq(io.github.crashedsystem.terminal.TextOps.normalize("www.a.com"), "https://www.a.com",
                "normalize adds the scheme");
        eq(io.github.crashedsystem.terminal.TextOps.normalize("http://a.com"), "http://a.com",
                "and leaves an explicit one alone");
    }
}
