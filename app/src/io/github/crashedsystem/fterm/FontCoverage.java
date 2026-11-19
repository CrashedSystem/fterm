package io.github.crashedsystem.fterm;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Whether a font file's own character map contains a set of code points.
 *
 * <p>This exists because {@link android.graphics.Paint#hasGlyph} is the wrong question. It
 * asks the platform, and the platform answers through its font fallback chain: on a real
 * device every face claimed to render Hangul, because the system would have borrowed a CJK
 * font to do it. The picker then listed every font on the phone, and worse, the sample line
 * in each row was drawn by the fallback - a preview of a font other than the one on offer.
 *
 * <p>So the cmap table is read directly. Only the tables a shaper would consider are
 * ranked, the best one wins, and the answer is "the file itself has this glyph".
 *
 * <p>Pure JDK on purpose: no Android types, so {@code tests/jvm/core/FontCoverageTest} can
 * run it against real system fonts instead of trusting a stub.
 */
public final class FontCoverage {

    /** Slow byte-at-a-time reads; a cmap is a few kilobytes and this runs once per file. */
    private interface Src {
        int u8(long off) throws IOException;
    }

    private static final class RafSource implements Src {
        private final RandomAccessFile mFile;

        RafSource(RandomAccessFile file) {
            mFile = file;
        }

        @Override public int u8(long off) throws IOException {
            mFile.seek(off);
            return mFile.read();
        }
    }

    private static final class ArraySource implements Src {
        private final byte[] mBytes;

        ArraySource(byte[] bytes) {
            mBytes = bytes;
        }

        @Override public int u8(long off) {
            if (off < 0 || off >= mBytes.length) return 0;
            return mBytes[(int) off] & 0xff;
        }
    }

    private FontCoverage() {}

    /** True when every code point is in the file's cmap. An unreadable file has none. */
    public static boolean covers(java.io.File file, int[] codePoints) {
        if (file == null || codePoints == null) return false;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 12) return false;
            return covers(new RafSource(raf), codePoints);
        } catch (Throwable t) {
            return false;
        }
    }

    /** As {@link #covers(java.io.File, int[])}, for a font already in memory. */
    public static boolean covers(byte[] bytes, int[] codePoints) {
        return bytes != null && codePoints != null && covers(new ArraySource(bytes), codePoints);
    }

    private static int u16(Src s, long off) throws IOException {
        return (s.u8(off) << 8) | s.u8(off + 1);
    }

    private static long u32(Src s, long off) throws IOException {
        return ((long) s.u8(off) << 24) | ((long) s.u8(off + 1) << 16)
                | ((long) s.u8(off + 2) << 8) | s.u8(off + 3);
    }

    private static boolean covers(Src s, int[] codePoints) {
        if (codePoints.length == 0) return false;
        try {
            long base = 0;
            // 'ttcf': a collection. The first face is the one a loader gives you, and it is
            // also the one whose name the picker shows, so it is the one to ask about.
            if (s.u8(0) == 't' && s.u8(1) == 't' && s.u8(2) == 'c' && s.u8(3) == 'f') {
                base = u32(s, 12);
            }
            long cmap = -1;
            int tables = u16(s, base + 4);
            for (int i = 0; i < tables; i++) {
                long rec = base + 12 + 16L * i;
                if (s.u8(rec) == 'c' && s.u8(rec + 1) == 'm' && s.u8(rec + 2) == 'a'
                        && s.u8(rec + 3) == 'p') {
                    cmap = u32(s, rec + 8);
                    break;
                }
            }
            if (cmap < 0) return false;

            // Rank the subtables the way a shaper does and use the best one. A format 4
            // table cannot answer for anything above the BMP, which is the honest answer for
            // a font that only declares one.
            long best = -1;
            int bestRank = 0;
            int subtables = u16(s, cmap + 2);
            for (int i = 0; i < subtables; i++) {
                long rec = cmap + 4 + 8L * i;
                int platform = u16(s, rec);
                int encoding = u16(s, rec + 2);
                int rank;
                if (platform == 3 && encoding == 10) rank = 5;      // Windows, full Unicode
                else if (platform == 3 && encoding == 1) rank = 4;  // Windows, BMP
                else if (platform == 0) rank = 3;                   // Unicode
                else if (platform == 3 && encoding == 0) rank = 1;  // symbol
                else rank = 0;
                if (rank > bestRank) {
                    bestRank = rank;
                    best = cmap + u32(s, rec + 4);
                }
            }
            if (best < 0) return false;

            int format = u16(s, best);
            for (int cp : codePoints) {
                boolean found;
                if (format == 12 || format == 13) found = format12Has(s, best, cp);
                else if (format == 4) found = format4Has(s, best, cp);
                else return false;
                if (!found) return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Segmented coverage: find the segment that starts at or before the code point. */
    private static boolean format4Has(Src s, long p, int cp) throws IOException {
        if (cp < 0 || cp > 0xffff) return false;
        int segX2 = u16(s, p + 6);
        int seg = segX2 / 2;
        long endCodes = p + 14;
        long startCodes = endCodes + segX2 + 2L;
        long idRangeOffsets = startCodes + segX2 + segX2;
        int lo = 0;
        int hi = seg - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int end = u16(s, endCodes + 2L * mid);
            if (cp > end) {
                lo = mid + 1;
                continue;
            }
            int start = u16(s, startCodes + 2L * mid);
            if (cp < start) {
                hi = mid - 1;
                continue;
            }
            // idRangeOffset is relative to its own slot, not to the table - the one detail
            // that makes format 4 easy to get wrong.
            int rangeOffset = u16(s, idRangeOffsets + 2L * mid);
            if (rangeOffset == 0) return true;
            long addr = idRangeOffsets + 2L * mid + rangeOffset + 2L * (cp - start);
            return u16(s, addr) != 0;
        }
        return false;
    }

    /** Grouped coverage: find the last group that starts at or before the code point. */
    private static boolean format12Has(Src s, long p, int cp) throws IOException {
        long groups = u32(s, p + 12);
        long lo = 0;
        long hi = groups - 1;
        while (lo <= hi) {
            long mid = (lo + hi) >>> 1;
            long g = p + 16 + 12 * mid;
            long start = u32(s, g);
            if (cp < start) {
                hi = mid - 1;
                continue;
            }
            long end = u32(s, g + 4);
            if (cp > end) {
                lo = mid + 1;
                continue;
            }
            return true;
        }
        return false;
    }
}
