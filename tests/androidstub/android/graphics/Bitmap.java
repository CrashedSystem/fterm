package android.graphics;

/** Test stub: images are not exercised by the text tests, so allocation is inert. */
public class Bitmap {
    public static class Config { public static final Config ARGB_8888 = new Config(); }

    private final int w, h;
    private final int[] px;

    private Bitmap(int w, int h) { this.w = w; this.h = h; this.px = new int[w * h]; }

    public static Bitmap createBitmap(int w, int h, Config c) { return new Bitmap(w, h); }
    public static Bitmap createBitmap(Bitmap src, int x, int y, int w, int h) { return new Bitmap(w, h); }
    public static Bitmap createScaledBitmap(Bitmap src, int w, int h, boolean filter) { return new Bitmap(w, h); }
    public int getWidth() { return w; }
    public int getHeight() { return h; }
    public void getPixels(int[] out, int off, int stride, int x, int y, int w, int h) {
        System.arraycopy(px, 0, out, off, Math.min(px.length, out.length - off));
    }
    public void setPixels(int[] in, int off, int stride, int x, int y, int w, int h) {
        System.arraycopy(in, off, px, 0, Math.min(px.length, in.length - off));
    }
    public void eraseColor(int c) { java.util.Arrays.fill(px, c); }
    public void setPixel(int x, int y, int c) {}
    public int getAllocationByteCount() { return px.length * 4; }
}
