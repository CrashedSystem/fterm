/**
 * The terminal bell and the permission it needs.
 *
 * A bell whose vibration silently fails because the permission is missing is worse than no
 * bell: it looks implemented. This asserts the manifest declares VIBRATE, that the class
 * actually vibrates and only beeps when the ringer is normal, and that MainActivity rings for
 * the active tab only.
 */
public class BellTest {

    static int pass = 0;

    static void check(boolean c, String what) {
        if (!c) throw new AssertionError(what);
        pass++;
    }

    static String read(String p) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(p)), "UTF-8");
    }

    public static void main(String[] args) throws Exception {
        String root = System.getProperty("root", ".");
        String manifest = read(root + "/AndroidManifest.xml");
        check(manifest.contains("android.permission.VIBRATE"),
                "VIBRATE is declared (without it the bell's default is a silent no-op)");
        check(manifest.contains("android.permission.POST_NOTIFICATIONS"),
                "POST_NOTIFICATIONS is declared for the service notification");
        // specialUse, not dataSync: Android 15 stops a dataSync service after six hours in any
        // 24, and the service exists to outlive a shell session.
        check(manifest.contains("FOREGROUND_SERVICE_SPECIAL_USE"),
                "the foreground service type is declared");
        check(manifest.contains("foregroundServiceType=\"specialUse\""),
                "and it is specialUse, which has no daily clock");
        check(manifest.contains("io.github.crashedsystem.terminal.TerminalService"),
                "the service is registered");

        String bell = read(root + "/app/src/io/github/crashedsystem/terminal/Bell.java");
        check(bell.contains("VibrationEffect.createWaveform"), "the bell vibrates");
        check(bell.contains("RINGER_MODE_NORMAL"), "and beeps only when the ringer is normal");
        check(bell.contains("hasVibrator()"), "and checks the device has one");
        check(bell.contains("ToneGenerator.TONE_PROP_BEEP"), "the beep is a short tone");
        check(bell.contains("release()"), "the tone generator is releasable");

        String main = read(root + "/app/src/io/github/crashedsystem/terminal/MainActivity.java");
        check(main.contains("mBell.ring(mPrefs.bellVibrate(), mPrefs.bellSound())"),
                "MainActivity rings with the user's settings");
        check(main.contains("mActive.session != session"), "and only for the active tab");

        // The toggles must be reachable, or the bell cannot be turned off.
        String settings = SettingsSources.all(
                root + "/app/src/io/github/crashedsystem/terminal/SettingsSheet.java");
        // The labels are resource keys now; the words live in strings.xml. Both are asserted
        // so a wrong key or a missing translation still fails.
        check(settings.contains("i18n_0203") && settings.contains("i18n_0205"),
                "both toggles are in settings");
        String strings = read(root + "/res/values/strings.xml");
        check(strings.contains(">터미널 벨 · 진동<") && strings.contains(">터미널 벨 · 소리<"),
                "and they say 진동 and 소리");
        check(settings.contains("bellVibrate()") && settings.contains("bellSound()"),
                "and are bound to the host");

        System.out.println("ok  " + pass + " bell/notification assertions");
        System.out.println("ALL OK");
    }
}
