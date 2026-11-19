package io.github.crashedsystem.fterm;

import java.util.ArrayList;
import java.util.List;

/**
 * A profile is what a tab starts as: a name, where it starts, which shell runs, and a
 * command to run once that shell is up.
 *
 * <p>Windows Terminal's dropdown opens with a list of these, and an "ssh profile" is simply
 * a profile whose command is {@code ssh host}. The command is written into the PTY after the
 * shell has started rather than exec'd in its place - that keeps the login shell's
 * environment and startup files, which is what makes {@code ssh} find the keys it should,
 * and it does not widen what the daemon is willing to execute.
 *
 * <p>Storage is one string, not JSON: the fields contain paths, and the two separator
 * characters (record and unit separators) do not appear in paths, names, or shell commands,
 * so a hand-rolled format cannot be attacked by quoting. Parsing is total - a malformed
 * record is dropped, because a profile list is a convenience and must never stop a tab from
 * opening.
 *
 * <p>A record has five fields. It used to have four, and the fifth (the icon) was added at
 * the end rather than in the middle for exactly that reason: an old store still decodes, with
 * an empty icon, and no migration pass is needed for a value that means "the default".
 */
public final class Profiles {

    /**
     * The name of the default profile - the one that is not in the user's list because it
     * is what a tab is when nothing else was chosen.
     *
     * <p>It is the shell the daemon actually starts, not a generic word: the daemon's
     * {@code default_shell()} resolves to bash in Termux, and a dropdown that says "shell"
     * while the prompt says bash is a small lie told every time the menu opens.
     */
    public static final String DEFAULT_NAME = "bash";

    private static final char UNIT = '\u001f';
    private static final char RECORD = '\u001e';

    /** A tab's starting configuration. Every field may be empty, meaning "use the default". */
    public static final class Profile {
        public String name = "";
        public String dir = "";
        public String shell = "";
        public String command = "";
        /**
         * The tab mark for this profile: a {@link TabIcons} glyph id, literal text, or "" to
         * inherit the global tab icon.
         *
         * <p>Per profile rather than global because that is the whole point of having
         * profiles: one tab is an ssh session, one is a database console, one is opencode,
         * and the mark is how a glance tells them apart before the title is read.
         */
        public String icon = "";

        public Profile() {}

        public Profile(String name, String dir, String shell, String command) {
            this(name, dir, shell, command, "");
        }

        public Profile(String name, String dir, String shell, String command, String icon) {
            this.name = safe(name);
            this.dir = safe(dir);
            this.shell = safe(shell);
            this.command = safe(command);
            this.icon = safe(icon);
        }

        /** What the dropdown and the settings list print under the name. */
        public String subtitle() {
            if (!command.isEmpty()) return command;
            if (!shell.isEmpty()) return shell;
            return dir;
        }
    }

    private Profiles() {}

    /**
     * The example profile the settings page offers: opencode, started on top of the shell.
     *
     * <p>Here, beside {@link #DEFAULT_NAME}, because it is the app's own example and not a
     * user setting - a literal in the settings UI would be the same string in a place where
     * no test can reach it. The command is the bare name, not an absolute path: opencode is
     * on PATH through the shell's startup files, and a hard-coded
     * {@code /data/data/com.termux/files/home/.local/bin/opencode} would break the moment the
     * user moves it and would say nothing about <em>how</em> the profile runs it.
     */
    public static Profile opencodeExample() {
        return new Profile("opencode", "", "", "opencode", "opencode");
    }

    /** Drop the separators; a name with a record separator would otherwise split the list. */
    private static String safe(String s) {
        if (s == null) return "";
        return s.replace(UNIT, ' ').replace(RECORD, ' ');
    }

    public static String encode(List<Profile> list) {
        StringBuilder out = new StringBuilder();
        for (Profile p : list) {
            if (p == null) continue;
            if (out.length() > 0) out.append(RECORD);
            out.append(safe(p.name)).append(UNIT)
                    .append(safe(p.dir)).append(UNIT)
                    .append(safe(p.shell)).append(UNIT)
                    .append(safe(p.command)).append(UNIT)
                    .append(safe(p.icon));
        }
        return out.toString();
    }

    public static List<Profile> decode(String stored) {
        List<Profile> out = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return out;
        for (String record : stored.split(String.valueOf(RECORD), -1)) {
            if (record.isEmpty()) continue;
            // Four fields is a profile written before profiles had an icon, five is current.
            // Anything else is a leftover from an older format and is not worth guessing at.
            String[] f = record.split(String.valueOf(UNIT), -1);
            if (f.length != 4 && f.length != 5) continue;
            if (f[0].isEmpty() && f[1].isEmpty() && f[2].isEmpty() && f[3].isEmpty()) continue;
            out.add(new Profile(f[0], f[1], f[2], f[3], f.length == 5 ? f[4] : ""));
        }
        return out;
    }

    /**
     * Whether this command can be typed into a shell as a single line.
     *
     * <p>A newline would execute more than the user sees in the menu, so it is rejected
     * rather than silently split: the point of a profile is that opening it does what its
     * subtitle says.
     */
    public static boolean commandIsOneLine(String command) {
        return command != null && command.indexOf('\n') < 0 && command.indexOf('\r') < 0;
    }
}
