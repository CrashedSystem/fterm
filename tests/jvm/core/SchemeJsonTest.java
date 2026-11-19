import java.util.Map;

/**
 * Windows Terminal colour scheme import, checked on the real parser.
 *
 * <p>The parser is hand-written, so the tests are the only thing standing between it and a
 * paste that is accepted but wrong. Each case below is a shape that actually occurs: a full
 * Campbell block as shared on the web, a block with the comments and trailing commas people
 * copy out of {@code settings.json}, a partial block, and the mistakes that must be refused
 * with a message naming the key.
 */
public class SchemeJsonTest {

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

    static void fails(String json, String mustMention) {
        try {
            ai.openclaw.fterm.TerminalSchemeJson.parse(json);
        } catch (IllegalArgumentException e) {
            check(e.getMessage() != null && e.getMessage().contains(mustMention),
                    "refusal names the problem: " + e.getMessage());
            return;
        }
        throw new AssertionError("accepted invalid input: " + json);
    }

    static int rgb(int r, int g, int b) {
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    static final String CAMPBELL = "{\n"
            + "    \"name\": \"Campbell\",\n"
            + "    \"black\": \"#0C0C0C\",\n"
            + "    \"red\": \"#C50F1F\",\n"
            + "    \"green\": \"#13A10E\",\n"
            + "    \"yellow\": \"#C19C00\",\n"
            + "    \"blue\": \"#0037DA\",\n"
            + "    \"purple\": \"#881798\",\n"
            + "    \"cyan\": \"#3A96DD\",\n"
            + "    \"white\": \"#CCCCCC\",\n"
            + "    \"brightBlack\": \"#767676\",\n"
            + "    \"brightRed\": \"#E74856\",\n"
            + "    \"brightGreen\": \"#16C60C\",\n"
            + "    \"brightYellow\": \"#F9F1A5\",\n"
            + "    \"brightBlue\": \"#3B78FF\",\n"
            + "    \"brightPurple\": \"#B4009E\",\n"
            + "    \"brightCyan\": \"#61D6D6\",\n"
            + "    \"brightWhite\": \"#F2F2F2\",\n"
            + "    \"background\": \"#0C0C0C\",\n"
            + "    \"foreground\": \"#CCCCCC\",\n"
            + "    \"cursorColor\": \"#F2F2F2\",\n"
            + "    \"selectionBackground\": \"#FFFFFF\"\n"
            + "}\n";

    public static void main(String[] args) throws Exception {
        // --- the shared Campbell block ----------------------------------------
        ai.openclaw.fterm.TerminalSchemeJson.Scheme s =
                ai.openclaw.fterm.TerminalSchemeJson.parse(CAMPBELL);
        eq(s.name, "Campbell", "reads the scheme's name");
        eq(s.slots.length, ai.openclaw.fterm.Schemes.CUSTOM_SLOTS, "nineteen slots");
        int[] campbell = ai.openclaw.fterm.Schemes.campbell();
        for (int i = 0; i < 16; i++) {
            eq(s.slots[i], campbell[i], "ANSI " + i + " is Campbell's");
        }
        eq(s.slots[13], rgb(0xB4, 0x00, 0x9E), "brightPurple lands in the bright purple slot");
        eq(s.slots[16], rgb(0xCC, 0xCC, 0xCC), "foreground");
        eq(s.slots[17], rgb(0x0C, 0x0C, 0x0C), "background");
        eq(s.slots[18], rgb(0xF2, 0xF2, 0xF2), "cursor");

        // The imported slots must build a palette the renderer can use unchanged.
        int[] palette = ai.openclaw.fterm.Schemes.fromSlots(s.slots);
        eq(palette[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND],
                rgb(0x0C, 0x0C, 0x0C), "the built palette uses the imported background");
        eq(palette[com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND],
                rgb(0xCC, 0xCC, 0xCC), "and foreground");

        // --- what settings.json actually looks like ---------------------------
        String commented = "{\n"
                + "  // my scheme\n"
                + "  \"name\": \"Night\",\n"
                + "  \"background\": \"#101418\", /* dark */\n"
                + "  \"foreground\": \"#E6E6E6\",\n"
                + "  \"brightCyan\": \"#00FFEE\",\n"
                + "}\n";
        ai.openclaw.fterm.TerminalSchemeJson.Scheme night =
                ai.openclaw.fterm.TerminalSchemeJson.parse(commented);
        eq(night.name, "Night", "comments do not stop the parse");
        eq(night.slots[17], rgb(0x10, 0x14, 0x18), "block comment after a value is skipped");
        eq(night.slots[14], rgb(0x00, 0xFF, 0xEE), "brightCyan");
        eq(night.slots[0], campbell[0], "an absent colour keeps Campbell's value");
        eq(night.slots[18], campbell[com.termux.terminal.TextStyle.COLOR_INDEX_CURSOR],
                "and an absent cursor keeps Campbell's");

        // Unknown keys (WT's own settings carry several) are ignored, not refused.
        ai.openclaw.fterm.TerminalSchemeJson.parse(
                "{\"background\":\"#000000\",\"experimental.retroTerminalEffect\":true,"
                        + "\"opacity\":85}");

        // Short hex form and bare hex are both accepted; case does not matter.
        eq(ai.openclaw.fterm.TerminalSchemeJson.parseColour("#abc"), rgb(0xAA, 0xBB, 0xCC),
                "#abc expands each nibble");
        eq(ai.openclaw.fterm.TerminalSchemeJson.parseColour("0c0c0c"), rgb(0x0C, 0x0C, 0x0C),
                "the hash is optional");
        eq(ai.openclaw.fterm.TerminalSchemeJson.parseColour("#80112233"), 0x80112233,
                "#AARRGGBB keeps its alpha");
        eq(ai.openclaw.fterm.TerminalSchemeJson.parseColour("#ff"), null, "two digits is not");
        eq(ai.openclaw.fterm.TerminalSchemeJson.parseColour("red"), null, "names are not");

        // --- refusals ---------------------------------------------------------
        fails("", "비어");
        fails("[1,2,3]", "구성표");
        fails("{\"name\": \"Empty\"}", "색 항목이 없습니다");
        fails("{\"background\": \"nope\"}", "background");
        fails("{\"black\": 12}", "black");
        fails("{background: \"#000000\"}", "키는 문자열");
        fails("{\"background\": \"#000000\"", "}");

        // A name-less block still imports; the toast needs something to say.
        eq(ai.openclaw.fterm.TerminalSchemeJson.parse("{\"background\":\"#123456\"}").name,
                "가져온 구성표", "an unnamed scheme gets a name");

        // The app's own scheme list is the target: import overwrites 사용자 지정.
        check(ai.openclaw.fterm.Schemes.CUSTOM_INDEX == 6, "import lands in 사용자 지정");

        System.out.println("SchemeJsonTest ALL OK (" + pass + " assertions)");
    }
}
