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
        java.util.List<io.github.crashedsystem.terminal.Profiles.Profile> list = new java.util.ArrayList<>();
        list.add(new io.github.crashedsystem.terminal.Profiles.Profile(
                "서버", "/data/data/com.termux/files/home", "/usr/bin/bash", "ssh host"));
        list.add(new io.github.crashedsystem.terminal.Profiles.Profile("빈", "", "", ""));
        String encoded = io.github.crashedsystem.terminal.Profiles.encode(list);
        java.util.List<io.github.crashedsystem.terminal.Profiles.Profile> back =
                io.github.crashedsystem.terminal.Profiles.decode(encoded);
        check(back.size() == 2, "two profiles survive the round trip");
        check(back.get(0).name.equals("서버"), "name survives");
        check(back.get(0).dir.equals("/data/data/com.termux/files/home"), "directory survives");
        check(back.get(0).shell.equals("/usr/bin/bash"), "shell survives");
        check(back.get(0).command.equals("ssh host"), "command survives");
        check(back.get(1).command.isEmpty(), "an empty command stays empty");

        // 2. Every malformed store decodes to something, never to a throw.
        check(io.github.crashedsystem.terminal.Profiles.decode(null).isEmpty(), "null decodes to empty");
        check(io.github.crashedsystem.terminal.Profiles.decode("").isEmpty(), "empty decodes to empty");
        check(io.github.crashedsystem.terminal.Profiles.decode("garbage").isEmpty(),
                "a record without four fields is dropped");
        check(io.github.crashedsystem.terminal.Profiles.decode("a\u001fb\u001ec\u001fd").isEmpty(),
                "a truncated record is dropped, not half-read");
        check(io.github.crashedsystem.terminal.Profiles.decode("\u001e\u001e").isEmpty(),
                "empty records are skipped");

        // 3. A separator typed into a name must not be able to forge a second profile.
        io.github.crashedsystem.terminal.Profiles.Profile nasty = new io.github.crashedsystem.terminal.Profiles.Profile(
                "a\u001eb\u001fc\u001fd\u001fe", "", "", "");
        String one = io.github.crashedsystem.terminal.Profiles.encode(java.util.Collections.singletonList(nasty));
        check(io.github.crashedsystem.terminal.Profiles.decode(one).size() == 1,
                "separators in a name cannot create a second profile");

        // 4. A newline in the command would run more than the subtitle shows.
        check(io.github.crashedsystem.terminal.Profiles.commandIsOneLine("ssh host"), "one line is fine");
        check(!io.github.crashedsystem.terminal.Profiles.commandIsOneLine("ssh host\nrm -rf ~"),
                "a newline is rejected");
        check(!io.github.crashedsystem.terminal.Profiles.commandIsOneLine("ssh host\rfoo"),
                "a carriage return is rejected too");
        check(!io.github.crashedsystem.terminal.Profiles.commandIsOneLine(null), "null is not a command");

        // 4b. The icon is a fifth field, and its absence is not an error.
        //
        //     Profiles shipped with four fields. Adding the icon in the middle would have
        //     made every stored profile undecodable; appending it means an old store reads
        //     back with an empty icon, which already means "use the default". Both widths
        //     therefore have to decode, and the four-field one has to keep its data.
        java.util.List<io.github.crashedsystem.terminal.Profiles.Profile> iconList = new java.util.ArrayList<>();
        iconList.add(new io.github.crashedsystem.terminal.Profiles.Profile("oc", "", "", "", "opencode"));
        String iconEncoded = io.github.crashedsystem.terminal.Profiles.encode(iconList);
        java.util.List<io.github.crashedsystem.terminal.Profiles.Profile> iconBack =
                io.github.crashedsystem.terminal.Profiles.decode(iconEncoded);
        check(iconBack.size() == 1, "a profile with an icon round trips");
        check("opencode".equals(iconBack.get(0).icon), "the icon survives");

        java.util.List<io.github.crashedsystem.terminal.Profiles.Profile> old =
                io.github.crashedsystem.terminal.Profiles.decode("서버\u001f/home\u001f/bin/bash\u001fssh h");
        check(old.size() == 1, "a four-field record from the old format still decodes");
        check("서버".equals(old.get(0).name), "with its name intact");
        check("ssh h".equals(old.get(0).command), "and its command");
        check(old.get(0).icon.isEmpty(), "and an empty icon, which means the default");

        check(new io.github.crashedsystem.terminal.Profiles.Profile("a", "", "", "", null).icon.isEmpty(),
                "a null icon is stored as empty, not as the string null");

        // 5. The subtitle is what the menu prints: the command if there is one.
        check(new io.github.crashedsystem.terminal.Profiles.Profile("x", "/sdcard", "", "ssh h").subtitle()
                .equals("ssh h"), "the command is the subtitle");
        check(new io.github.crashedsystem.terminal.Profiles.Profile("x", "/sdcard", "/bin/sh", "").subtitle()
                .equals("/bin/sh"), "then the shell");
        check(new io.github.crashedsystem.terminal.Profiles.Profile("x", "/sdcard", "", "").subtitle()
                .equals("/sdcard"), "then the directory");

        // 6. The default profile is named after the shell that actually starts.
        check(io.github.crashedsystem.terminal.Profiles.DEFAULT_NAME.equals("bash"),
                "the unnamed profile is bash, because that is what the daemon starts");

        // 7. A list handled by index survives an edit and a delete.
        //
        //    This is the bug the settings sheet had: profiles() decodes the stored string
        //    into fresh objects each call, so a dialog that held one of those objects and
        //    asked "is this the same instance?" never matched, and the save or the delete
        //    quietly did nothing. Reference equality across a decode boundary is a trap, so
        //    the index is the handle - and here is the round trip that proves it works.
        java.util.List<io.github.crashedsystem.terminal.Profiles.Profile> work = new java.util.ArrayList<>();
        work.add(new io.github.crashedsystem.terminal.Profiles.Profile("a", "", "", ""));
        work.add(new io.github.crashedsystem.terminal.Profiles.Profile("b", "", "", ""));
        work.add(new io.github.crashedsystem.terminal.Profiles.Profile("c", "", "", ""));

        // Delete the middle one by index, as the sheet now does.
        work.remove(1);
        java.util.List<io.github.crashedsystem.terminal.Profiles.Profile> afterDelete =
                io.github.crashedsystem.terminal.Profiles.decode(io.github.crashedsystem.terminal.Profiles.encode(work));
        check(afterDelete.size() == 2, "a delete by index actually removes one");
        check(afterDelete.get(1).name.equals("c"), "and removes the right one");

        // Replace by index, which is how an edit is stored.
        afterDelete.set(0, new io.github.crashedsystem.terminal.Profiles.Profile("A", "/tmp", "", ""));
        java.util.List<io.github.crashedsystem.terminal.Profiles.Profile> afterEdit =
                io.github.crashedsystem.terminal.Profiles.decode(io.github.crashedsystem.terminal.Profiles.encode(afterDelete));
        check(afterEdit.get(0).name.equals("A"), "an edit by index replaces in place");
        check(afterEdit.get(0).dir.equals("/tmp"), "and keeps the other fields it was given");
        check(afterEdit.get(1).name.equals("c"), "without disturbing the neighbours");

        System.out.println("ok  " + pass + " profile assertions");
        System.out.println("ALL OK");
    }
}
