package io.github.crashedsystem.fterm;

import java.util.ArrayList;
import java.util.List;

/**
 * The user's own colour schemes: up to {@link Schemes#MAX_CUSTOM_SCHEMES} named palettes of
 * nineteen slots each, in the order the settings list shows them.
 *
 * <p>This used to be one palette in nineteen shared-preference keys. It is a list now, so the
 * list is the unit of storage: one string, records separated by the record separator and
 * fields by the unit separator, exactly like {@link Profiles}. Names and colours cannot
 * contain those two characters (a name has them stripped, colours are decimal), so the format
 * needs no quoting, and a record that does not parse is dropped rather than breaking the
 * others - a colour scheme is a convenience and must never stop the terminal from opening.
 *
 * <p>A record is the name followed by the nineteen slots in the app's order: the sixteen ANSI
 * colours, then foreground, background and cursor. Decimal, because the values are ints with
 * an opaque alpha of {@code 0xff} and therefore negative in Java; hex would need its own
 * unsigned parse for no gain.
 *
 * <p>Pure Java, so {@code tests/jvm/core/CustomSchemesTest} runs it without Android.
 */
public final class CustomSchemes {

    /** What a palette is called when it has no name: the single slot's old label. */
    public static final String DEFAULT_NAME = AppStrings.s(R.string.i18n_0070);

    private static final char UNIT = '\u001f';
    private static final char RECORD = '\u001e';

    /** A saved palette: the name the list prints, and its nineteen colour slots. */
    public static final class Theme {
        public final String name;
        public final int[] slots;

        public Theme(String name, int[] slots) {
            this.name = clean(name).isEmpty() ? DEFAULT_NAME : clean(name);
            this.slots = slots == null || slots.length != Schemes.CUSTOM_SLOTS
                    ? Schemes.campbellSlots() : slots.clone();
        }
    }

    private final List<Theme> mThemes = new ArrayList<>();

    private CustomSchemes() {}

    public static CustomSchemes empty() {
        return new CustomSchemes();
    }

    /**
     * The store a single-slot installation becomes: one palette, under the name that slot
     * carried in the settings list.
     *
     * <p>Called once, by {@link Prefs}, when the list key is absent. Keeping it here rather
     * than in {@code Prefs} is what lets the JVM suite test the migration without Android.
     */
    public static CustomSchemes fromLegacy(String name, int[] slots) {
        CustomSchemes out = new CustomSchemes();
        out.add(name, slots);
        return out;
    }

    public int size() {
        return mThemes.size();
    }

    public boolean isEmpty() {
        return mThemes.isEmpty();
    }

    public boolean isFull() {
        return mThemes.size() >= Schemes.MAX_CUSTOM_SCHEMES;
    }

    /** The stored name at a position, or "" when the position is past the end. */
    public String nameAt(int index) {
        return index >= 0 && index < mThemes.size() ? mThemes.get(index).name : "";
    }

    /** A copy of the slots at a position, or null when the position is past the end. */
    public int[] slotsAt(int index) {
        return index >= 0 && index < mThemes.size() ? mThemes.get(index).slots.clone() : null;
    }

    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (Theme t : mThemes) out.add(t.name);
        return out;
    }

    /**
     * Appends a palette, with a name that is not already taken.
     *
     * @return the new position, or -1 when the sixteen are full or the slots are not
     *         nineteen long - a half-written palette is worse than a refused one.
     */
    public int add(String name, int[] slots) {
        if (isFull() || slots == null || slots.length != Schemes.CUSTOM_SLOTS) return -1;
        mThemes.add(new Theme(uniqueName(names(), name), slots));
        return mThemes.size() - 1;
    }

    /** Renames a palette; a name already used by another one gets a number appended. */
    public boolean rename(int index, String name) {
        if (index < 0 || index >= mThemes.size()) return false;
        if (clean(name).isEmpty()) return false;
        List<String> others = new ArrayList<>();
        for (int i = 0; i < mThemes.size(); i++) {
            if (i != index) others.add(mThemes.get(i).name);
        }
        Theme old = mThemes.get(index);
        mThemes.set(index, new Theme(uniqueName(others, name), old.slots));
        return true;
    }

    /** Replaces one palette's colours, which is what editing a swatch does. */
    public boolean setSlots(int index, int[] slots) {
        if (index < 0 || index >= mThemes.size()) return false;
        if (slots == null || slots.length != Schemes.CUSTOM_SLOTS) return false;
        mThemes.set(index, new Theme(mThemes.get(index).name, slots));
        return true;
    }

    public boolean remove(int index) {
        if (index < 0 || index >= mThemes.size()) return false;
        mThemes.remove(index);
        return true;
    }

    /**
     * The slots a scheme index should draw with.
     *
     * <p>For a custom index it is that palette; for a built-in, or for an index past the end,
     * the first palette, so the editor still has nineteen slots to show and
     * {@code schemes.colors(index, slots)} never falls back to Campbell's while the list is
     * non-empty.
     */
    public int[] slotsForScheme(int scheme) {
        int i = scheme - Schemes.CUSTOM_INDEX;
        if (i < 0 || i >= mThemes.size()) i = 0;
        return mThemes.isEmpty() ? Schemes.campbellSlots() : mThemes.get(i).slots.clone();
    }

    /** The scheme index that selects a position in this list. */
    public static int schemeIndexOf(int themeIndex) {
        return Schemes.CUSTOM_INDEX + themeIndex;
    }

    /** The position a scheme index selects, or -1 for a built-in index. */
    public static int themeIndexOf(int schemeIndex) {
        return Schemes.isCustom(schemeIndex) ? schemeIndex - Schemes.CUSTOM_INDEX : -1;
    }

    public String encode() {
        StringBuilder out = new StringBuilder();
        for (Theme t : mThemes) {
            if (out.length() > 0) out.append(RECORD);
            out.append(clean(t.name));
            for (int slot : t.slots) out.append(UNIT).append(slot);
        }
        return out.toString();
    }

    /** Reads a stored list; malformed records and extras past the cap are dropped. */
    public static CustomSchemes decode(String stored) {
        CustomSchemes out = new CustomSchemes();
        if (stored == null || stored.isEmpty()) return out;
        for (String record : stored.split(String.valueOf(RECORD), -1)) {
            if (record.isEmpty() || out.isFull()) continue;
            String[] f = record.split(String.valueOf(UNIT), -1);
            if (f.length != Schemes.CUSTOM_SLOTS + 1) continue;
            int[] slots = new int[Schemes.CUSTOM_SLOTS];
            boolean ok = true;
            for (int i = 0; i < slots.length; i++) {
                try {
                    slots[i] = Integer.parseInt(f[i + 1].trim());
                } catch (NumberFormatException e) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.mThemes.add(new Theme(f[0], slots));
        }
        return out;
    }

    /**
     * A name that does not collide with the ones taken: "Night" becomes "Night (2)".
     *
     * <p>Importing the same scheme twice is the ordinary case - the same gist, opened on a
     * second device - and two identical rows in the list would be indistinguishable.
     */
    public static String uniqueName(List<String> taken, String wanted) {
        String base = clean(wanted);
        if (base.isEmpty()) base = DEFAULT_NAME;
        if (base.length() > 40) base = base.substring(0, 40).trim();
        if (!has(taken, base)) return base;
        for (int n = 2; n < 1000; n++) {
            String candidate = base + " (" + n + ")";
            if (!has(taken, candidate)) return candidate;
        }
        return base;
    }

    private static boolean has(List<String> names, String candidate) {
        for (String n : names) {
            if (n.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    /** Drops the two separators, which would otherwise split a record or a field. */
    private static String clean(String s) {
        if (s == null) return "";
        return s.replace(UNIT, ' ').replace(RECORD, ' ').trim();
    }
}
