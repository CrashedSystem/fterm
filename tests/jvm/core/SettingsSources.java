import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * The settings surface, whole.
 *
 * <p>The sheet is split across several files - one per page, plus the palette and row shapes,
 * the font pickers and the icon grid - because 1,985 lines in one class is not something a
 * person can hold. These tests assert things about the surface ("the cursor colour is
 * reachable by name", "the picker stores the face id"), not about one file, so they read all of
 * it. An override that points at a single file still works: a directory with no other Settings
 * source in it yields just that file.
 */
public final class SettingsSources {

    private SettingsSources() {
    }

    static String all(String sheetPath) throws Exception {
        File sheet = new File(sheetPath);
        if (!sheet.isFile()) throw new java.io.FileNotFoundException(sheetPath);
        File dir = sheet.getParentFile();
        File[] parts = dir == null ? null : dir.listFiles((d, name) ->
                name.startsWith("Settings") && name.endsWith(".java"));
        if (parts == null || parts.length == 0) return read(sheet);
        Arrays.sort(parts);
        StringBuilder sb = new StringBuilder();
        for (File f : parts) sb.append(read(f));
        return sb.toString();
    }

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
