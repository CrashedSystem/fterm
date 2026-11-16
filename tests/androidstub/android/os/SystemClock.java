package android.os;

/** Test stub: monotonic millis. */
public class SystemClock {
    public static long uptimeMillis() {
        return System.nanoTime() / 1000000L;
    }
}
