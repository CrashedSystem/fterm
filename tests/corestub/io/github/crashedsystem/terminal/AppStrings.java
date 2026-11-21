package io.github.crashedsystem.terminal;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test stub: {@link AppStrings} with no Android on the classpath.
 *
 * <p>The real class calls {@code Context.getString()}, which a JVM test cannot do. This stub
 * reads res/values-ko/strings.xml once and answers with the Korean text, so a test that asserts
 * on a label a user would read actually checks the shipped word instead of an opaque id.
 *
 * <p>The XML is found through the {@code main.src} system property the test script sets, the
 * same way the paste test is pointed at MainActivity.java; with no property the stub falls
 * back to the id, which keeps every other test running.
 */
public final class AppStrings {

    private static final Map<Integer, String> VALUES = load();

    private AppStrings() {
    }

    /**
     * No-op: the real init() takes a Context, which this classpath does not have.
     *
     * <p>Declared with Object so the stub still compiles without android.jar; nothing calls it
     * from a JVM test.
     */
    public static void init(Object context) {
        // Nothing to remember without an Android context.
    }

    public static String s(int id) {
        String v = VALUES.get(id);
        if (v != null) return v;
        String key = R.name(id);
        return key == null ? "" : key;
    }

    private static Map<Integer, String> load() {
        Map<Integer, String> out = new HashMap<>();
        File xml = stringsXml();
        if (xml == null) return out;
        String text;
        try {
            text = new String(Files.readAllBytes(xml.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return out;
        }
        Matcher m = Pattern.compile("<string name=\"([^\"]+)\">(.*?)</string>", Pattern.DOTALL)
                .matcher(text);
        while (m.find()) {
            int id = fieldId(m.group(1));
            if (id != 0) out.put(id, unescape(m.group(2)));
        }
        return out;
    }

    /** The generated constant for a key, read reflectively so the two stubs stay decoupled. */
    private static int fieldId(String key) {
        try {
            return R.string.class.getField(key).getInt(null);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * XML text to the string Android would hand back at runtime.
     *
     * <p>Only the escapes this app actually writes: the entity for an apostrophe-free quote
     * pair, the two angle brackets, and the newline escape. Android also strips the surrounding
     * quotes that preserve edge whitespace, which several labels rely on - those quotes are
     * meaningful in the XML and not part of the text.
     */
    private static String unescape(String v) {
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v.replace("\\n", "\n")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("\\'", "'");
    }

    private static File stringsXml() {
        String src = System.getProperty("main.src");
        if (src == null) return null;
        // .../app/src/io/github/crashedsystem/terminal/MainActivity.java -> .../app/src
        File dir = new File(src).getParentFile();
        for (int i = 0; i < 5 && dir != null; i++) dir = dir.getParentFile();
        File root = dir == null ? null : dir.getParentFile();
        File xml = root == null ? null : new File(root, "res/values-ko/strings.xml");
        return xml != null && xml.isFile() ? xml : null;
    }
}
