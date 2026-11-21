package io.github.crashedsystem.terminal;

import com.termux.terminal.TextStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a Windows Terminal colour scheme, the JSON people share as a block in a gist or paste
 * into {@code settings.json}:
 *
 * <pre>
 * {"name": "Campbell", "black": "#0C0C0C", ..., "brightWhite": "#F2F2F2",
 *  "background": "#0C0C0C", "foreground": "#CCCCCC", "cursorColor": "#F2F2F2",
 *  "selectionBackground": "#FFFFFF"}
 * </pre>
 *
 * <p>The nineteen slots this app stores are the sixteen ANSI colours followed by foreground,
 * background and cursor; {@code selectionBackground} is accepted and dropped, because the
 * terminal view paints selections from the palette rather than from a scheme slot.
 *
 * <p>Hand-written rather than {@code org.json}: the app has no dependency on it in the JVM
 * suite either, and a scheme block is a flat object of strings - a full JSON library would be
 * more code than this and would still need the same colour parser. The reader is deliberately
 * tolerant of what people actually paste: line and block comments, trailing commas, and
 * extra keys such as {@code "experimental.retroTerminalEffect"}.
 *
 * <p>Pure Java, so {@code tests/jvm/core/SchemeJsonTest} runs it outside Android.
 */
public final class TerminalSchemeJson {

    public static final class Scheme {
        public final String name;
        /** 16 ANSI colours, then foreground, background, cursor. */
        public final int[] slots;

        public Scheme(String name, int[] slots) {
            this.name = name;
            this.slots = slots;
        }
    }

    /** Windows Terminal's names, in slot order. */
    private static final String[] ANSI_KEYS = {
            "black", "red", "green", "yellow", "blue", "purple", "cyan", "white",
            "brightBlack", "brightRed", "brightGreen", "brightYellow",
            "brightBlue", "brightPurple", "brightCyan", "brightWhite",
    };

    private TerminalSchemeJson() {}

    /**
     * @throws IllegalArgumentException with a message meant for the user: the dialog shows it
     *         instead of a shrug, because "nothing happened" after a paste reads as a bug.
     */
    /**
     * Every scheme in a document: one object, an array of them, or a whole Windows Terminal
     * {@code settings.json} with a {@code schemes} list.
     *
     * <p>A settings.json is what people actually share - the Miku theme this app's palette came
     * from ships eight schemes in one file and a background picture beside it - so refusing the
     * file and asking for a hand-extracted block would be the tool making work for the user.
     *
     * @throws IllegalArgumentException with a user-facing message when nothing could be read.
     */
    public static List<Scheme> parseAll(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0360));
        }
        Object root;
        try {
            root = new Parser(text).document();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0361) + e.getMessage());
        }
        List<Object> blocks = new ArrayList<>();
        if (root instanceof Map) {
            Map<?, ?> object = (Map<?, ?>) root;
            Object schemes = null;
            for (Map.Entry<?, ?> e : object.entrySet()) {
                if ("schemes".equalsIgnoreCase(String.valueOf(e.getKey()))) {
                    schemes = e.getValue();
                    break;
                }
            }
            if (schemes instanceof List) {
                blocks.addAll((List<?>) schemes);
            } else if (containsColour(object)) {
                // A single scheme block, which is also how a settings.json with no "schemes" key
                // and a profile-level colour set looks. Nothing to import there, but a block
                // pasted from a gist is exactly this.
                blocks.add(object);
            } else {
                throw new IllegalArgumentException(
                        io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0362));
            }
        } else if (root instanceof List) {
            blocks.addAll((List<?>) root);
        } else {
            throw new IllegalArgumentException(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0363));
        }

        List<Scheme> out = new ArrayList<>();
        String firstError = null;
        for (Object block : blocks) {
            if (!(block instanceof Map)) continue;
            try {
                out.add(fromObject((Map<?, ?>) block));
            } catch (IllegalArgumentException e) {
                // One broken entry must not throw away the other seven: report it only if every
                // entry failed, which is when the message is the useful part.
                if (firstError == null) firstError = e.getMessage();
            }
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException(firstError != null
                    ? firstError : io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0364));
        }
        return out;
    }

    private static boolean containsColour(Map<?, ?> object) {
        for (Object k : object.keySet()) {
            String name = String.valueOf(k).toLowerCase(java.util.Locale.ROOT);
            if ("foreground".equals(name) || "background".equals(name)
                    || "cursorcolor".equals(name)) {
                return true;
            }
            for (String ansi : ANSI_KEYS) {
                if (ansi.equalsIgnoreCase(name)) return true;
            }
        }
        return false;
    }

    /**
     * @throws IllegalArgumentException with a message meant for the user: the dialog shows it
     *         instead of a shrug, because "nothing happened" after a paste reads as a bug.
     */
    public static Scheme parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0360));
        }
        Object root;
        try {
            root = new Parser(text).document();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0361) + e.getMessage());
        }
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0365));
        }
        return fromObject((Map<?, ?>) root);
    }

    private static Scheme fromObject(Map<?, ?> object) {
        Map<String, Object> lower = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : object.entrySet()) {
            lower.put(String.valueOf(e.getKey()).toLowerCase(java.util.Locale.ROOT), e.getValue());
        }

        int[] base = Schemes.campbell();
        int[] slots = new int[Schemes.CUSTOM_SLOTS];
        System.arraycopy(base, 0, slots, 0, 16);
        slots[16] = base[TextStyle.COLOR_INDEX_FOREGROUND];
        slots[17] = base[TextStyle.COLOR_INDEX_BACKGROUND];
        slots[18] = base[TextStyle.COLOR_INDEX_CURSOR];

        int found = 0;
        for (int i = 0; i < ANSI_KEYS.length; i++) {
            Object v = lower.get(ANSI_KEYS[i].toLowerCase(java.util.Locale.ROOT));
            if (v == null) continue;
            slots[i] = colour(ANSI_KEYS[i], v);
            found++;
        }
        Object fg = lower.get("foreground");
        if (fg != null) { slots[16] = colour("foreground", fg); found++; }
        Object bg = lower.get("background");
        if (bg != null) { slots[17] = colour("background", bg); found++; }
        Object cursor = lower.get("cursorcolor");
        if (cursor != null) { slots[18] = colour("cursorColor", cursor); found++; }

        if (found == 0) {
            throw new IllegalArgumentException(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0366));
        }
        Object name = lower.get("name");
        String label = name instanceof String && !((String) name).trim().isEmpty()
                ? ((String) name).trim() : io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0367);
        return new Scheme(label, slots);
    }

    private static int colour(String key, Object value) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(key + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0368));
        }
        Integer c = parseColour((String) value);
        if (c == null) {
            throw new IllegalArgumentException(key + io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0369) + value);
        }
        return c;
    }

    /**
     * {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}, with or without the hash.
     *
     * @return null when the text is not a colour, so the caller can name the key it came from.
     */
    public static Integer parseColour(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (!s.matches("[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) return null;
        if (s.length() == 3) {
            int r = Integer.parseInt(s.substring(0, 1) + s.substring(0, 1), 16);
            int g = Integer.parseInt(s.substring(1, 2) + s.substring(1, 2), 16);
            int b = Integer.parseInt(s.substring(2, 3) + s.substring(2, 3), 16);
            return 0xff000000 | (r << 16) | (g << 8) | b;
        }
        long v = Long.parseLong(s, 16);
        if (s.length() == 6) return (int) (0xff000000L | v);
        return (int) v;
    }

    // ------------------------------------------------------------------ reader

    /** Objects become {@link Map}, arrays {@link List}, strings String, numbers Double. */
    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        Object document() {
            Object v = value();
            skip();
            if (i < s.length()) throw error(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0370));
            return v;
        }

        private Object value() {
            skip();
            if (i >= s.length()) throw error(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0371));
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            if (c == '-' || (c >= '0' && c <= '9')) return number();
            throw error(AppStrings.s(R.string.json_unknown_char) + c + "'");
        }

        private Map<String, Object> object() {
            Map<String, Object> out = new LinkedHashMap<>();
            i++;                      // {
            while (true) {
                skip();
                if (i >= s.length()) throw error(AppStrings.s(R.string.json_missing_close_brace));
                if (s.charAt(i) == '}') { i++; return out; }
                if (s.charAt(i) != '"') throw error(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0372));
                String key = string();
                skip();
                if (i >= s.length() || s.charAt(i) != ':') throw error(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0373));
                i++;
                out.put(key, value());
                skip();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                if (i < s.length() && s.charAt(i) == '}') { i++; return out; }
                throw error(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0374));
            }
        }

        private List<Object> array() {
            List<Object> out = new ArrayList<>();
            i++;                      // [
            while (true) {
                skip();
                if (i >= s.length()) throw error(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0375));
                if (s.charAt(i) == ']') { i++; return out; }
                out.add(value());
                skip();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                if (i < s.length() && s.charAt(i) == ']') { i++; return out; }
                throw error(io.github.crashedsystem.terminal.AppStrings.s(io.github.crashedsystem.terminal.R.string.i18n_0376));
            }
        }

        private String string() {
            StringBuilder b = new StringBuilder();
            i++;                      // opening quote
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case '"': b.append('"'); break;
                    case '\\': b.append('\\'); break;
                    case '/': b.append('/'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw error(AppStrings.s(R.string.json_short_unicode));
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: throw error(AppStrings.s(R.string.json_unknown_escape) + e);
                }
            }
            throw error(AppStrings.s(R.string.json_unterminated));
        }

        private Double number() {
            int start = i;
            if (s.charAt(i) == '-') i++;
            while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
            try {
                return Double.valueOf(s.substring(start, i));
            } catch (NumberFormatException e) {
                throw error(AppStrings.s(R.string.json_not_a_number));
            }
        }

        /** Whitespace, line comments and block comments - the parts people paste with. */
        private void skip() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { i++; continue; }
                if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                    while (i < s.length() && s.charAt(i) != '\n') i++;
                    continue;
                }
                if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                    int end = s.indexOf("*/", i + 2);
                    i = end < 0 ? s.length() : end + 2;
                    continue;
                }
                return;
            }
        }

        private IllegalArgumentException error(String what) {
            return new IllegalArgumentException(what + AppStrings.s(R.string.json_at_position) + i + ")");
        }
    }
}
