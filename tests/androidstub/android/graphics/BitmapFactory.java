package android.graphics;

/** Test stub: decoding is never used in the text tests. */
public class BitmapFactory {
    public static class Options {
        public boolean inJustDecodeBounds;
        public int inSampleSize;
        public int outWidth;
        public int outHeight;
    }

    public static Bitmap decodeByteArray(byte[] data, int off, int len) { return null; }
    public static Bitmap decodeByteArray(byte[] data, int off, int len, Options opts) { return null; }
}
