package ai.openclaw.fterm;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.graphics.Paint;

/**
 * The terminal typeface, built the way mterm builds it.
 *
 * mterm's stack is three faces with different jobs: Iosevka answers Latin (every glyph
 * 500/1000 em), a Nerd Font symbols face answers the private-use icon range, and Pretendard
 * answers Hangul. That split is not taste - Iosevka has no CJK coverage at all, and Pretendard's
 * syllable advance (1770/2048 em) is *not* two Iosevka cells (1000/1000 em), so a Hangul
 * face needs its advance fitted to the two-cell box.
 *
 * Android has no `size-adjust` and no per-face unicode-range on a `Typeface`, so two things
 * replace them:
 *
 *   1. A single `Typeface` is built with {@link Typeface#createFromAssets} for each face and
 *      selected per code point. {@link #typefaceFor(int)} returns Pretendard for Hangul and
 *      Iosevka for everything else, which is exactly the stack's decision.
 *   2. Every face is drawn at the same em size. Windows Terminal's AtlasEngine does the same
 *      thing: it asks DirectWrite to place glyphs at `font->fontSize` and only corrects the
 *      cluster's advance to the cells the width rules gave it. Nothing is enlarged to fill a
 *      cell, because enlarging one script's face and not another's makes its ascent and line
 *      spacing disagree with the row height every other face agreed on.
 *
 * The terminal view keeps one `Paint`, so the per-code-point switch has to happen in the
 * renderer. {@link #typefaceFor} is the single place that decision lives.
 */
final class Fonts {

    /**
     * The widest a Hangul glyph may be drawn, as a multiple of its two-cell box.
     *
     * <p>Faces differ: Noto Serif CJK's syllable is wider than two Iosevka cells, One UI Sans
     * KR's is narrower. Windows Terminal answers this by leaving the em size alone and
     * shrinking only a glyph that overhangs its box, so the text keeps one baseline and one
     * line height. The same rule here: a face is never enlarged, only fitted.
     */
    static final float HANGUL_FIT_MAX = 1.0f;

    /** The bundled face, which is also the default and the one that always works. */
    static final String ASSET_PATH = "fonts/Iosevka.ttf";

    /** The bundled Korean face; the default, and the last resort when a chosen one fails. */
    static final String HANGUL_PATH = "fonts/PretendardHangul.ttf";
    static final String ASSET_ID = "asset:iosevka";

    /** The bundled Korean face's id, the default for the Hangul picker. */
    static final String HANGUL_ASSET_ID = "asset:pretendard";

    /**
     * Where an imported font is kept: this app's own files directory.
     *
     * <p>Not {@code /sdcard/Download}. Since Android 11 an app cannot list or open an
     * arbitrary file under {@code /sdcard} - not even one it just wrote - so a shared folder
     * looked like a reasonable place to put a font and was readable by nobody. The app's
     * files directory needs no permission and no storage scope; the way in is
     * {@link #importFont(Context, android.net.Uri)}, which copies through a document the user
     * picked, and that picker is the only door the platform leaves open.
     */
    static java.io.File userDir(Context context) {
        java.io.File dir = new java.io.File(context.getFilesDir(), "fonts");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    private static final String SYSTEM_DIR = "/system/fonts";

    /** The prefix of a {@code file:} id; a stored preference is an id, never a bare path. */
    static final String FILE_PREFIX = "file:";

    /**
     * The outcome of an import: an id, or why there is none.
     *
     * <p>A reason, not a boolean. "Could not read that" is indistinguishable from a bug; the
     * actual failure - the document would not open, the file was not a font, the font was
     * proportional - tells the user whether to try another file or stop trying.
     */
    static final class Import {
        final String id;
        final String reason;

        private Import(String id, String reason) {
            this.id = id;
            this.reason = reason;
        }
    }

    /**
     * Copy a font the user picked into {@link #userDir} and return its catalog id.
     *
     * <p>Copied rather than referenced: a {@code content://} URI is a permission-scoped
     * handle that outlives neither a reboot nor a revocation, and the terminal reads the file
     * on every font change for the rest of the install's life.
     */
    static Import importFont(Context context, android.net.Uri uri) {
        if (uri == null) return new Import(null, "파일을 받지 못했습니다.");
        String name = null;
        try (android.database.Cursor c = context.getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Throwable ignored) {
        }
        if (name == null || name.isEmpty()) name = "font" + System.currentTimeMillis() + ".ttf";
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) {
            return new Import(null, "TTF 또는 OTF 파일이 아닙니다: " + name);
        }
        // The picker shows a display name and nothing stops one containing a path separator.
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        java.io.File out = new java.io.File(userDir(context), name);
        long bytes = 0;
        try (java.io.InputStream in = context.getContentResolver().openInputStream(uri);
             java.io.OutputStream os = new java.io.FileOutputStream(out)) {
            if (in == null) return new Import(null, "파일을 열 수 없습니다: " + uri);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                bytes += n;
            }
        } catch (Throwable t) {
            // A half-written file would show up in the catalog as a face that cannot load.
            out.delete();
            return new Import(null, "파일을 복사하지 못했습니다: " + t);
        }
        if (bytes == 0) {
            out.delete();
            return new Import(null, "빈 파일입니다: " + name);
        }
        return adopt(out);
    }

    /**
     * Install bytes that did not come from a document picker.
     *
     * <p>Same checks and same destination as {@link #importFont}: only the delivery differs.
     * The Termux daemon is the other delivery - it can download, and it can read directories
     * this app cannot - so a font fetched there lands here through this method rather than
     * through a second copy of the validation rules.
     */
    static Import install(Context context, String name, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return new Import(null, "빈 파일입니다.");
        if (name == null || name.isEmpty()) name = "font.ttf";
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) {
            return new Import(null, "TTF 또는 OTF 파일이 아닙니다: " + name);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        java.io.File out = new java.io.File(userDir(context), name);
        try (java.io.OutputStream os = new java.io.FileOutputStream(out)) {
            os.write(bytes);
        } catch (Throwable t) {
            out.delete();
            return new Import(null, "파일을 저장하지 못했습니다: " + t);
        }
        return adopt(out);
    }

    /**
     * Validate a file already in {@link #userDir} and turn it into a catalog id.
     *
     * <p>Two ways to be a valid font, because there are two pickers. A Latin face has to be
     * fixed-advance: it defines the cell, and a proportional face makes columns drift. A
     * Korean face only has to render Hangul, because it is drawn per code point at a scale
     * derived from the cell - which is why every Korean font on a real device is
     * proportional and every one of them was refused here before. The catalog filters decide
     * where an accepted file then appears; this method only rejects what neither list can use.
     */
    private static Import adopt(java.io.File out) {
        String name = out.getName();
        Typeface face = loadFile(out.getAbsolutePath());
        if (face == null) {
            out.delete();
            return new Import(null, "글꼴 파일을 열 수 없습니다: " + name);
        }
        boolean mono = isFixedAdvance(face);
        boolean korean = hasHangul(out);
        if (!mono && !korean) {
            out.delete();
            return new Import(null, "글자 폭이 일정하지 않은 글꼴입니다: " + name
                    + "\n\n터미널은 한 칸이 한 글자인 격자라, 비례 글꼴을 쓰면 열이 어긋납니다."
                    + "\n한글 글꼴이면 글꼴 목록의 「한글 글꼴」에서 고를 수 있습니다.");
        }
        rescan();
        return new Import(FILE_PREFIX + out.getAbsolutePath(), null);
    }

    /**
     * One selectable face.
     *
     * <p>The list is built by scanning, not by naming fonts in code. A hard-coded table is
     * why the setting offered three faces on a device with hundreds: it could only ever
     * offer what the app already knew about. The scan is filtered by the one property the
     * renderer actually requires - a fixed advance - so a proportional face is never
     * offered and never breaks the grid.
     */
    static final class Face {
        /** {@link #ASSET_ID} or "file:/abs/path". Unknown ids fall back to the asset. */
        final String id;
        /** What the picker prints. */
        final String label;
        /** Absolute path, or null for the bundled asset. */
        final String path;
        /** True for the bundled face. */
        final boolean bundled;

        Face(String id, String label, String path, boolean bundled) {
            this.id = id;
            this.label = label;
            this.path = path;
            this.bundled = bundled;
        }
    }

    /**
     * Code points that prove a face can draw Hangul: 가 (U+AC00), 한 (U+D55C), 힣 (U+D7A3).
     *
     * <p>The first, a common one, and the last - a subset that answers one and not the others
     * is exactly the case where a word renders and the next one is boxes.
     */
    private static final int[] HANGUL_PROBES = {0xAC00, 0xD55C, 0xD7A3};

    /** Conjoining jamo, which is what a decomposed paste and some IMEs send. */
    private static final int[] JAMO_PROBES = {0x1100, 0x1161, 0x11A8};

    /**
     * Whether a font file's own cmap contains every one of these code points.
     *
     * <p>Deliberately not {@link Paint#hasGlyph}: that asks the platform, which answers
     * through its font fallback chain, so every face on the device claimed to have Hangul and
     * the Korean picker listed all of them - a list whose sample line rendered from a
     * <em>different</em> font than the row was offering. The question this picker has to
     * answer is what the file itself can draw, and only the cmap table knows.
     */
    static boolean covers(java.io.File file, int[] codePoints) {
        return FontCoverage.covers(file, codePoints);
    }

    static boolean hasHangul(java.io.File file) {
        return covers(file, HANGUL_PROBES);
    }

    static boolean hasJamo(java.io.File file) {
        return covers(file, JAMO_PROBES);
    }

    /** Whether a picker row's face can follow an IME through conjoining jamo. */
    static boolean faceHasJamo(Context context, Face f) {
        if (f.path == null) {
            return FontCoverage.covers(readAsset(context, HANGUL_PATH), JAMO_PROBES);
        }
        return hasJamo(new java.io.File(f.path));
    }

    private static Typeface loadBundledHangul(Context context) {
        try {
            return Typeface.createFromAsset(context.getAssets(), HANGUL_PATH);
        } catch (Throwable t) {
            return null;
        }
    }

    static byte[] readAsset(Context context, String path) {
        try (java.io.InputStream in = context.getAssets().open(path)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static java.util.List<Face> sCatalog;

    /**
     * Every face the renderer can use, in picker order.
     *
     * <p>Cached because the scan opens every file on the device and the picker is opened more
     * than once per session. {@link #rescan()} clears it when the user has added a font.
     */
    static java.util.List<Face> catalog(Context context) {
        if (sCatalog != null) return sCatalog;
        java.util.List<Face> out = new java.util.ArrayList<>();
        out.add(new Face(ASSET_ID, "Iosevka (기본)", null, true));

        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        addDir(out, seen, new java.io.File(SYSTEM_DIR), "");
        addDir(out, seen, userDir(context), " (내 글꼴)");
        sCatalog = out;
        return out;
    }

    /**
     * Whether a catalog id can serve as the Latin face, i.e. it defines a cell.
     *
     * <p>Asked after an install so the picker that opens next is the one the file belongs in.
     * A Korean font is proportional by design and belongs in the other list; opening the
     * Latin picker on it would show a list the new row is not in.
     */
    static boolean isLatinFace(String id) {
        // Asked of the file, not of the catalog: ensureVisible() runs right after install()
        // and the id is a bare path at that moment, before either list has been rescanned.
        if (id == null || !id.startsWith(FILE_PREFIX)) return true;
        String path = id.substring(FILE_PREFIX.length());
        if (!new java.io.File(path).isFile()) return true;
        return isFixedAdvance(loadFile(path));
    }

    /** Drop the cached catalog so the next picker shows a font the user just copied in. */
    static void rescan() {
        sCatalog = null;
        sHangulCatalog = null;
    }

    private static java.util.List<Face> sHangulCatalog;

    /**
     * The faces that can render Hangul, for the Korean face picker.
     *
     * <p>A separate list from {@link #catalog} because the filter is the opposite one. The
     * Latin list requires a fixed advance - a proportional Latin face breaks the column grid -
     * but Hangul is drawn per code point at a derived scale so that one syllable lands on two
     * cells, which means the Hangul face's own advances do not matter. Applying the Latin
     * filter here is why every Korean font on the device was missing from the picker: Nanum
     * Gothic and Noto Sans KR are both proportional, so both were rejected, and the only
     * Korean face the app could offer was the one compiled in.
     */
    static java.util.List<Face> hangulCatalog(Context context) {
        if (sHangulCatalog != null) return sHangulCatalog;
        java.util.List<Face> out = new java.util.ArrayList<>();
        out.add(new Face(HANGUL_ASSET_ID, "Pretendard (기본)", null, true));

        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        addHangulDir(out, seen, new java.io.File(SYSTEM_DIR), "", bundledSize(context));
        addHangulDir(out, seen, userDir(context), " (내 글꼴)", bundledSize(context));
        sHangulCatalog = out;
        return out;
    }

    /** The bundled Korean face's byte length, or -1 when the asset cannot be opened. */
    private static long bundledSize(Context context) {
        try {
            return context.getAssets().openFd(HANGUL_PATH).getLength();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static void addHangulDir(java.util.List<Face> out, java.util.Set<String> seen,
                                     java.io.File dir, String suffix, long bundledSize) {
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (java.io.File f : files) {
            String name = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (!name.endsWith(".ttf") && !name.endsWith(".otf") && !name.endsWith(".ttc")) {
                continue;
            }
            if (!f.isFile() || f.length() == 0) continue;
            // A copy of the bundled face on disk is the same face under a second name. Two
            // rows that render identically is a row the user has to guess about, and the
            // check has to be on bytes: a Typeface loaded from an asset and one loaded from
            // a file never compare equal in memory.
            if (bundledSize > 0 && f.length() == bundledSize) continue;
            String path = f.getAbsolutePath();
            if (!seen.add(path)) continue;
            if (!hasHangul(f)) continue;
            out.add(new Face(FILE_PREFIX + path, stripExtension(f.getName()) + suffix, path, false));
        }
    }

    private static void addDir(java.util.List<Face> out, java.util.Set<String> seen,
                               java.io.File dir, String suffix) {
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        // Sorted so the list does not reshuffle between opens: a picker whose order changes
        // is a picker the user has to re-read every time.
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (java.io.File f : files) {
            String name = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (!name.endsWith(".ttf") && !name.endsWith(".otf")) continue;
            if (!f.isFile() || f.length() == 0) continue;
            String path = f.getAbsolutePath();
            if (!seen.add(path)) continue;
            Typeface face = loadFile(path);
            if (face == null || !isFixedAdvance(face)) continue;
            out.add(new Face(FILE_PREFIX + path, stripExtension(f.getName()) + suffix, path, false));
        }
    }

    /**
     * The catalog entry for an id, or the bundled face.
     *
     * <p>Total on purpose: a prefs file written by another build, or a font the user has
     * since deleted, must still open a terminal.
     */
    static Face find(Context context, String id) {
        java.util.List<Face> all = catalog(context);
        for (Face f : all) {
            if (f.id.equals(id)) return f;
        }
        // An id that names a file outside the scanned directories is still honoured, as long
        // as that file is a fixed-advance face right now. The user typed the path in
        // deliberately; refusing it because the file lives somewhere this app does not scan
        // would make the field a lie. A path that no longer loads falls through to the
        // bundled face, which is the same rule as everywhere else here.
        Face typed = typedPath(id);
        if (typed != null) return typed;
        return all.get(0);
    }

    /**
     * The Hangul catalog entry for an id, or the bundled Korean face.
     *
     * <p>Total for the same reason {@link #find} is: a stored id may name a font the user has
     * since deleted, and that must cost the Korean face, not the terminal. Note that this
     * falls back to the *bundled Hangul* face, not the bundled Latin one - Pretendard has no
     * Latin glyphs worth printing, and Iosevka has no Hangul at all.
     */
    static Face findHangul(Context context, String id) {
        java.util.List<Face> all = hangulCatalog(context);
        for (Face f : all) {
            if (f.id.equals(id)) return f;
        }
        if (id != null && id.startsWith(FILE_PREFIX)) {
            String path = id.substring(FILE_PREFIX.length());
            java.io.File f = new java.io.File(path);
            if (f.isFile() && f.length() > 0) {
                // The one property this picker requires, which is not the Latin one: the file
                // has to carry Hangul glyphs. Proportional is fine here.
                if (hasHangul(f)) {
                    return new Face(id, stripExtension(f.getName()) + " (직접 지정)", path, false);
                }
            }
        }
        return all.get(0);
    }

    /**
     * The catalog entry for a user-typed {@code file:} id, or null.
     *
     * <p>Not cached: the point of a typed path is that it is not in the catalog, so a cache
     * would only be able to hold stale answers about a file that may have moved.
     */
    private static Face typedPath(String id) {
        if (id == null || !id.startsWith(FILE_PREFIX)) return null;
        String path = id.substring(FILE_PREFIX.length());
        if (path.isEmpty()) return null;
        java.io.File f = new java.io.File(path);
        if (!f.isFile() || f.length() == 0) return null;
        Typeface face = loadFile(path);
        if (face == null || !isFixedAdvance(face)) return null;
        return new Face(id, stripExtension(f.getName()) + " (직접 지정)", path, false);
    }

    /** The picker's caption for an id. */
    static String labelOf(Context context, String id) {
        return find(context, id).label;
    }

    /** The Korean picker's caption for an id. */
    static String labelOfHangul(Context context, String id) {
        return findHangul(context, id).label;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static Typeface loadFile(String path) {
        try {
            return Typeface.createFromFile(path);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Whether every glyph occupies the same advance.
     *
     * <p>This is the only property the grid needs: with a fixed advance, one cell is the
     * character width, and a proportional face would make columns drift apart over a line.
     * Measured from the face itself rather than from its name, because a file called
     * "Mono" is not a promise.
     */
    static boolean isFixedAdvance(Typeface face) {
        if (face == null) return false;
        Paint p = new Paint();
        p.setTypeface(face);
        p.setTextSize(64f);
        float m = p.measureText("M");
        if (m <= 0f) return false;
        // Four extremes: two narrow, two wide. A face whose widest and narrowest differ by
        // more than a hundredth of a cell is proportional.
        for (String probe : new String[]{"i", "l", "W", "M", "0"}) {
            if (Math.abs(p.measureText(probe) - m) > m * 0.01f) return false;
        }
        return true;
    }

    private final Typeface mIosevka;
    private final Typeface mHangul;
    private final Typeface mSymbols;
    /** How much (at most 1) a Hangul glyph is shrunk to sit inside its two cells. */
    private final float mHangulFit;
    /** Baseline shift for Hangul, as a fraction of the em, to centre it in the row. */
    private final float mHangulShift;

    private Fonts(Typeface iosevka, Typeface hangul, Typeface symbols, float hangulFit,
                  float hangulShift) {
        mIosevka = iosevka;
        mHangul = hangul;
        mSymbols = symbols;
        mHangulFit = hangulFit;
        mHangulShift = hangulShift;
    }

    /**
     * Load the three faces and work out the Hangul adjustment for this cell width.
     *
     * @param latinCellPx the width of one Iosevka cell at the requested text size, measured
     *                    by the caller from the renderer's own Paint so the two cannot drift.
     */
    static Fonts load(Context context, float latinCellPx, float textSizePx) {
        return load(context, latinCellPx, textSizePx, ASSET_ID);
    }

    /**
     * As {@link #load(Context, float, float)}, with the Latin face chosen by catalog id.
     *
     * <p>A missing file falls back to the bundled asset: the user can delete a font from
     * {@link #userDir} and must not lose the ability to open a terminal because of it.
     */
    static Fonts load(Context context, float latinCellPx, float textSizePx, String id) {
        return load(context, latinCellPx, textSizePx, id, HANGUL_ASSET_ID);
    }

    /**
     * As {@link #load(Context, float, float, String)}, with both faces chosen by catalog id.
     *
     * <p>Two ids rather than one because the two pickers answer different questions. The
     * Latin face must be fixed-advance - it defines the cell - while the Korean face only has
     * to have the glyphs, because it is drawn per code point at a scale derived here. Sharing
     * one id meant every Korean font on the device was filtered out by the Latin rule and the
     * Korean face could not be changed at all.
     */
    static Fonts load(Context context, float latinCellPx, float textSizePx, String id,
                      String hangulId) {
        Typeface mono = null;
        Typeface hangul = null;
        Typeface symbols = null;
        Face chosen = find(context, id);
        if (chosen.path != null) mono = loadFile(chosen.path);
        if (mono == null) {
            try {
                mono = Typeface.createFromAsset(context.getAssets(), ASSET_PATH);
            } catch (Throwable ignored) {
            }
        }
        if (mono == null) mono = Typeface.MONOSPACE;
        Face hangulChosen = findHangul(context, hangulId);
        if (hangulChosen.path != null) hangul = loadFile(hangulChosen.path);
        if (hangul == null) {
            try {
                hangul = Typeface.createFromAsset(context.getAssets(), HANGUL_PATH);
            } catch (Throwable ignored) {
            }
        }
        try {
            symbols = Typeface.createFromAsset(context.getAssets(), "fonts/NerdSymbols.ttf");
        } catch (Throwable ignored) {
        }

        // Measure the cell from the chosen face rather than trusting the caller's number:
        // the caller's is the previous face's width, and switching faces is exactly when it
        // is wrong. A face's own "M" is the cell the renderer will use anyway.
        float cell = latinCellPx;
        Paint latinPaint = new Paint();
        latinPaint.setTypeface(mono);
        latinPaint.setTextSize(textSizePx);
        float measured = latinPaint.measureText("M");
        if (measured > 0f) cell = measured;

        // The Hangul face is measured, not resized. Its glyph is drawn at the Latin em size
        // and only shrunk when its own advance is wider than the two cells the width rules
        // reserved for it - Windows Terminal's rule, and the reason text keeps one baseline:
        // a face scaled up to fill its box brings its own, taller line metrics with it, and
        // then its ascent disagrees with the row height the Latin face defined.
        float fit = HANGUL_FIT_MAX;
        float shift = 0f;
        if (hangul != null && cell > 0f) {
            Paint lp = new Paint();
            lp.setTypeface(mono);
            lp.setTextSize(textSizePx);
            float rowHeight = lp.getFontSpacing();

            Paint hp = new Paint();
            hp.setTypeface(hangul);
            hp.setTextSize(textSizePx);
            float syllable = hp.measureText("\uD55C");
            Paint.FontMetrics fm = hp.getFontMetrics();
            float inkHeight = fm.descent - fm.ascent;

            // Two ways a glyph can overhang its box, and Windows Terminal handles both: the
            // advance can be wider than the two cells, and the face's own ascent and descent
            // can be taller than the row the Latin face defined. Either one is answered by
            // shrinking that glyph, never by growing the others to match.
            float byWidth = syllable > 0f && syllable > 2f * cell ? (2f * cell) / syllable : 1f;
            float byHeight = inkHeight > rowHeight ? rowHeight / inkHeight : 1f;
            fit = Math.min(1f, Math.min(byWidth, byHeight));
            // A measurement this far off means the face or the cell did not measure; drawing
            // at the natural size is better than shrinking text to nothing.
            if (fit < 0.5f) fit = HANGUL_FIT_MAX;

            // Centre what is left in the row. A CJK face usually has more ascent than a Latin
            // one, so without this its glyphs sit high and the row looks bottom-heavy. The
            // shift is a fraction of the em, so it follows the font size.
            Paint.FontMetrics hf = new Paint.FontMetrics();
            Paint scaled = new Paint();
            scaled.setTypeface(hangul);
            scaled.setTextSize(textSizePx * fit);
            scaled.getFontMetrics(hf);
            if (textSizePx > 0f) {
                float glyphCentre = (hf.ascent + hf.descent) / 2f;
                float rowCentre = (lp.ascent() + lp.descent()) / 2f;
                shift = (rowCentre - glyphCentre) / textSizePx;
            }
        }
        return new Fonts(mono, hangul != null ? hangul : mono,
                symbols != null ? symbols : mono, fit, shift);
    }

    Typeface latin() {
        return mIosevka;
    }

    /** The scale a Hangul glyph is drawn at; 1 unless the face overhangs its box. */
    float hangulFit() {
        return mHangulFit;
    }

    /** How far, in px at this size, to move a Hangul baseline down to centre it in the row. */
    float hangulShift(float textSizePx) {
        return mHangulShift * textSizePx;
    }

    /** True when the code point is answered by the Korean face. */
    static boolean isHangul(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x11FF)      // conjoining jamo
                || (codePoint >= 0x3130 && codePoint <= 0x318F)  // compatibility jamo
                || (codePoint >= 0xA960 && codePoint <= 0xA97F)  // jamo extended-A
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)  // precomposed syllables
                || (codePoint >= 0xD7B0 && codePoint <= 0xD7FF); // jamo extended-B
    }

    /** Private-use range a Nerd Font uses for icons. */
    static boolean isNerdIcon(int codePoint) {
        return (codePoint >= 0xE0A0 && codePoint <= 0xE0B3)
                || (codePoint >= 0xE0B4 && codePoint <= 0xE0C8)
                || (codePoint >= 0xF000 && codePoint <= 0xF2FF);
    }

    /**
     * The face for one code point: Hangul -> Pretendard, icons -> Nerd Symbols, else Iosevka.
     *
     * This is mterm's stack order expressed as a function, which is what an Android
     * `Typeface` can do instead of a `font-family` list.
     */
    Typeface typefaceFor(int codePoint) {
        if (isHangul(codePoint)) return mHangul;
        if (isNerdIcon(codePoint)) return mSymbols;
        return mIosevka;
    }

    /**
     * The scale for one code point. Only Hangul can differ, and only to fit its box.
     *
     * <p>Never above 1: a face is not enlarged to fill a cell. Enlarging one script's face
     * while the row height stays the Latin face's is what made Korean text sit too tall for
     * its line - measured on the device as 41.3px of line spacing drawn into a 37.5px row.
     */
    float scaleFor(int codePoint) {
        return isHangul(codePoint) ? mHangulFit : 1f;
    }

    static float dpToPx(Context context, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }
}
