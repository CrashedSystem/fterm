/**
 * The profile list's stored format, checked as a total function.
 *
 * A profile list is a convenience: losing it is annoying, but a profile list that throws on
 * open would keep a terminal from starting. So the tests that matter are the malformed ones -
 * a record split in the wrong place, a separator inside a name, an empty store - all of which
 * have to decode to "the profiles we could understand" rather than to an exception.
 */
public class ProfilesTest {

    static int pass = 0;

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
        pass++;
    }

    public static void main(String[] args) {
        // 1. Round trip, including the fields that contain the characters a path has.
        java.util.List<ai.openclaw.fterm.Profiles.Profile> list = new java.util.ArrayList<>();
        list.add(new ai.openclaw.fterm.Profiles.Profile(
                "서버", "/data/data/com.termux/files/home", "/usr/bin/bash", "ssh host"));
        list.add(new ai.openclaw.fterm.Profiles.Profile("빈", "", "", ""));
        String encoded = ai.openclaw.fterm.Profiles.encode(list);
        java.util.List<ai.openclaw.fterm.Profiles.Profile> back =
                ai.openclaw.fterm.Profiles.decode(encoded);
        check(back.size() == 2, "two profiles survive the round trip");
        check(back.get(0).name.equals("서버"), "name survives");
        check(back.get(0).dir.equals("/data/data/com.termux/files/home"), "directory survives");
        check(back.get(0).shell.equals("/usr/bin/bash"), "shell survives");
        check(back.get(0).command.equals("ssh host"), "command survives");
        check(back.get(1).command.isEmpty(), "an empty command stays empty");

        // 2. Every malformed store decodes to something, never to a throw.
        check(ai.openclaw.fterm.Profiles.decode(null).isEmpty(), "null decodes to empty");
        check(ai.openclaw.fterm.Profiles.decode("").isEmpty(), "empty decodes to empty");
        check(ai.openclaw.fterm.Profiles.decode("garbage").isEmpty(),
                "a record without four fields is dropped");
        check(ai.openclaw.fterm.Profiles.decode("a\u001fb\u001ec\u001fd").isEmpty(),
                "a truncated record is dropped, not half-read");
        check(ai.openclaw.fterm.Profiles.decode("\u001e\u001e").isEmpty(),
                "empty records are skipped");

        // 3. A separator typed into a name must not be able to forge a second profile.
        ai.openclaw.fterm.Profiles.Profile nasty = new ai.openclaw.fterm.Profiles.Profile(
                "a\u001eb\u001fc\u001fd\u001fe", "", "", "");
        String one = ai.openclaw.fterm.Profiles.encode(java.util.Collections.singletonList(nasty));
        check(ai.openclaw.fterm.Profiles.decode(one).size() == 1,
                "separators in a name cannot create a second profile");

        // 4. A newline in the command would run more than the subtitle shows.
        check(ai.openclaw.fterm.Profiles.commandIsOneLine("ssh host"), "one line is fine");
        check(!ai.openclaw.fterm.Profiles.commandIsOneLine("ssh host\nrm -rf ~"),
                "a newline is rejected");
        check(!ai.openclaw.fterm.Profiles.commandIsOneLine("ssh host\rfoo"),
                "a carriage return is rejected too");
        check(!ai.openclaw.fterm.Profiles.commandIsOneLine(null), "null is not a command");

        // 5. The subtitle is what the menu prints: the command if there is one.
        check(new ai.openclaw.fterm.Profiles.Profile("x", "/sdcard", "", "ssh h").subtitle()
                .equals("ssh h"), "the command is the subtitle");
        check(new ai.openclaw.fterm.Profiles.Profile("x", "/sdcard", "/bin/sh", "").subtitle()
                .equals("/bin/sh"), "then the shell");
        check(new ai.openclaw.fterm.Profiles.Profile("x", "/sdcard", "", "").subtitle()
                .equals("/sdcard"), "then the directory");

        System.out.println("ok  " + pass + " profile assertions");
        System.out.println("ALL OK");
    }
}
