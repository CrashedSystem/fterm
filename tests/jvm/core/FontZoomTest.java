/**
 * Pinch-to-zoom, run as a whole gesture rather than read off the source.
 *
 * The bug: onScale() rounded the font size to a whole dp on every gesture event, and a
 * pinch reports a couple of percent per event. At 9dp, a 1.02 factor is 9.18 - which
 * truncates to 9, and then to 9 again on every event after it. Zooming out was unaffected
 * because 8.82 truncates to 8, so the size moved on the first event. One direction worked
 * and the other silently did nothing, which is exactly what was reported.
 */
public class FontZoomTest {

    static int pass = 0;

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
        pass++;
    }

    public static void main(String[] args) throws Exception {
        // Sanity: the range the settings slider and the pinch share.
        check(io.github.crashedsystem.fterm.FontZoom.MIN_DP == 4, "the smallest size is 4dp");
        check(io.github.crashedsystem.fterm.FontZoom.MAX_DP == 28, "the largest size is 28dp");

        // 1. The reported bug, stated as the smallest failing case: thirty small zoom-out
        //    style factors in the "bigger" direction must add up to more than nothing.
        float dp = 9f;
        for (int i = 0; i < 30; i++) dp = io.github.crashedsystem.fterm.FontZoom.next(dp, 1.02f);
        check(dp > 16f, "thirty 1.02 events grow 9dp to " + dp + ", not to 9");

        // 2. And the old arithmetic is the control: doing it the old way makes no progress
        //    at all, so the test would have caught the bug.
        int old = 9;
        for (int i = 0; i < 30; i++) old = (int) (old * 1.02f);
        check(old == 9, "the old per-event truncation stays at 9dp forever, which was the bug");

        // 3. Zooming out still works, so the fix did not trade one direction for the other.
        dp = 9f;
        for (int i = 0; i < 30; i++) dp = io.github.crashedsystem.fterm.FontZoom.next(dp, 0.98f);
        check(dp < 9f, "thirty 0.98 events shrink 9dp, got " + dp);
        for (int i = 0; i < 80; i++) dp = io.github.crashedsystem.fterm.FontZoom.next(dp, 0.98f);
        check(dp == 4f, "and enough of them stop at the 4dp floor, got " + dp);

        // 4. A pinch that grows and shrinks back by the same factor returns to where it was:
        //    the fraction is remembered, not re-derived from a rounded size.
        dp = 11f;
        for (int i = 0; i < 10; i++) dp = io.github.crashedsystem.fterm.FontZoom.next(dp, 1.03f);
        for (int i = 0; i < 10; i++) dp = io.github.crashedsystem.fterm.FontZoom.next(dp, 1f / 1.03f);
        check(Math.abs(dp - 11f) < 0.01f, "a gesture there and back is a no-op, got " + dp);

        // 5. The clamp is a clamp, not an overflow: past the ends the value stays at the
        //    limit, and the next opposite event moves immediately instead of unwinding a
        //    long way first.
        dp = 28f;
        dp = io.github.crashedsystem.fterm.FontZoom.next(dp, 1.5f);
        check(dp == 28f, "and no larger than 28dp");
        dp = io.github.crashedsystem.fterm.FontZoom.next(dp, 0.98f);
        check(dp < 28f, "zooming out after hitting the ceiling moves at once, got " + dp);

        // 6. Junk from the detector must not wipe the size. A zero, negative, or NaN factor
        //    would otherwise collapse the font or poison it with NaN for the rest of the
        //    session, since every later event multiplies by it.
        check(io.github.crashedsystem.fterm.FontZoom.next(12f, 0f) == 12f, "a zero factor is ignored");
        check(io.github.crashedsystem.fterm.FontZoom.next(12f, -1.5f) == 12f, "a negative factor is ignored");
        check(io.github.crashedsystem.fterm.FontZoom.next(12f, Float.NaN) == 12f, "NaN is ignored");
        check(io.github.crashedsystem.fterm.FontZoom.next(12f, Float.POSITIVE_INFINITY) == 12f,
                "infinity is ignored");

        // 7. Rounding for the renderer: 9.6dp must render at 10, not 9. Truncation is what
        //    made the fraction worthless in the first place.
        check(io.github.crashedsystem.fterm.FontZoom.dp(9.6f) == 10, "9.6dp renders as 10dp");
        check(io.github.crashedsystem.fterm.FontZoom.dp(9.4f) == 9, "9.4dp renders as 9dp");
        check(io.github.crashedsystem.fterm.FontZoom.dp(2f) == 4, "dp() clamps low too");
        check(io.github.crashedsystem.fterm.FontZoom.dp(99f) == 28, "dp() clamps high too");

        // 8. The activity has to keep the fraction. If mFontDp is what onScale() multiplies,
        //    the fix is gone: the very act of storing a whole number loses it.
        String main = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                System.getProperty("main.src", "app/src/io/github/crashedsystem/fterm/MainActivity.java"))),
                java.nio.charset.StandardCharsets.UTF_8);
        int onScale = main.indexOf("public float onScale(float scale)");
        check(onScale >= 0, "MainActivity implements onScale");
        String body = main.substring(onScale, main.indexOf("\n    }", onScale));
        check(body.contains("FontZoom.next("),
                "onScale goes through the accumulating arithmetic");
        check(!body.contains("(int)"),
                "and does not cast to int, which is what discarded the fraction");

        System.out.println("ok  " + pass + " font zoom assertions");
    }
}
