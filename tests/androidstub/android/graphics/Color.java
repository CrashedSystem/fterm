package android.graphics;

/** Test stub: argb channel extraction. */
public class Color {
    public static int red(int c) { return (c >> 16) & 0xff; }
    public static int green(int c) { return (c >> 8) & 0xff; }
    public static int blue(int c) { return c & 0xff; }
}
