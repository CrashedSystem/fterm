/**
 * The user's own colour schemes: the sixteen cap, naming, deletion, and the migration from
 * the single palette the app used to store.
 *
 * <p>The list is stored as one string and read back on a phone that may have been upgraded
 * from an older build, so the round trip and the refusals are what matter here. Runs on the
 * real classes without Android - {@code Prefs} only hands the string over.
 */
public class CustomSchemesTest {

    static int pass = 0;

    static void eq(Object actual, Object expected, String what) {
        if (!java.util.Objects.equals(actual, expected)) {
            throw new AssertionError(what + ": expected <" + expected + "> got <" + actual);
        }
        pass++;
    }

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
        pass++;
    }

    static int rgb(int r, int g, int b) {
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    /** Nineteen distinct slots, so a mix-up between palettes is visible. */
    static int[] slots(int seed) {
        int[] out = new int[ai.openclaw.fterm.Schemes.CUSTOM_SLOTS];
        for (int i = 0; i < 16; i++) out[i] = rgb((seed * 13 + i) & 0xff, i * 8, seed);
        out[16] = rgb(0xee, 0xee, seed & 0xff);
        out[17] = rgb(0x10, 0x10, seed & 0xff);
        out[18] = rgb(0x00, 0xdd, seed & 0xff);
        return out;
    }

    public static void main(String[] args) throws Exception {
        // --- the index space ---------------------------------------------------
        eq(ai.openclaw.fterm.Schemes.CUSTOM_INDEX, 6, "the first custom index is where it was");
        eq(ai.openclaw.fterm.Schemes.MAX_CUSTOM_SCHEMES, 16, "sixteen can be saved");
        eq(ai.openclaw.fterm.Schemes.SCHEME_NAMES.length, 6, "six schemes are compiled in");
        eq(ai.openclaw.fterm.Schemes.SCHEME_NAMES.length,
                ai.openclaw.fterm.Schemes.CUSTOM_INDEX,
                "the built-ins occupy exactly 0..5, so old stored indices still mean the same");
        check(ai.openclaw.fterm.Schemes.isCustom(6), "6 is custom");
        check(ai.openclaw.fterm.Schemes.isCustom(21), "21 is the sixteenth custom");
        check(!ai.openclaw.fterm.Schemes.isCustom(5), "5 is built-in");
        check(!ai.openclaw.fterm.Schemes.isCustom(22), "22 is past the cap");

        // A scheme's nineteen slots, for "duplicate what I am looking at": a built-in is
        // copied from its own table, not from whichever custom palette happens to be first.
        int[] oneDark = ai.openclaw.fterm.Schemes.slotsOf(1, null);
        int[] oneDarkTable = ai.openclaw.fterm.Schemes.colors(1);
        eq(oneDark.length, ai.openclaw.fterm.Schemes.CUSTOM_SLOTS, "nineteen slots from a built-in");
        eq(oneDark[3], oneDarkTable[3], "a built-in's ANSI colour is copied");
        eq(oneDark[17], oneDarkTable[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND],
                "and its background");
        eq(ai.openclaw.fterm.Schemes.campbellSlots()[0],
                ai.openclaw.fterm.Schemes.colors(0)[0], "Campbell's slots come from Campbell");

        // --- migration from the single palette --------------------------------
        int[] legacy = slots(1);
        ai.openclaw.fterm.CustomSchemes store =
                ai.openclaw.fterm.CustomSchemes.fromLegacy(
                        ai.openclaw.fterm.CustomSchemes.DEFAULT_NAME, legacy);
        eq(store.size(), 1, "the old palette becomes one scheme");
        eq(store.nameAt(0), "사용자 지정", "under the name the single slot carried");
        for (int i = 0; i < legacy.length; i++) {
            eq(store.slotsAt(0)[i], legacy[i], "slot " + i + " survives the migration");
        }
        eq(store.slotsForScheme(ai.openclaw.fterm.Schemes.CUSTOM_INDEX).length, legacy.length,
                "a stored scheme=6 still names nineteen slots");
        check(java.util.Arrays.equals(
                store.slotsForScheme(ai.openclaw.fterm.Schemes.CUSTOM_INDEX), legacy),
                "the migrated palette is what the terminal draws");

        // --- sixteen, then a refusal ------------------------------------------
        for (int i = 1; i < ai.openclaw.fterm.Schemes.MAX_CUSTOM_SCHEMES; i++) {
            eq(store.add("S" + i, slots(i)), i, "scheme " + i + " is appended");
        }
        eq(store.size(), 16, "sixteen saved");
        check(store.isFull(), "and the store knows it");
        eq(store.add("S17", slots(17)), -1, "the seventeenth is refused");
        eq(store.size(), 16, "and nothing was written");
        eq(store.nameAt(16), "", "a position past the end has no name");
        eq(store.slotsAt(16), null, "and no slots");

        // --- names -------------------------------------------------------------
        ai.openclaw.fterm.CustomSchemes names = ai.openclaw.fterm.CustomSchemes.empty();
        names.add("Night", slots(2));
        names.add("Night", slots(3));
        names.add("night", slots(4));
        eq(names.nameAt(0), "Night", "the first keeps its name");
        eq(names.nameAt(1), "Night (2)", "a duplicate gets a number");
        eq(names.nameAt(2), "night (3)", "and the comparison ignores case");
        names.add("   ", slots(5));
        eq(names.nameAt(3), "사용자 지정", "a nameless scheme gets the default name");

        eq(ai.openclaw.fterm.CustomSchemes.uniqueName(names.names(), "Night"), "Night (4)",
                "uniqueName counts every taken variant");
        eq(ai.openclaw.fterm.CustomSchemes.uniqueName(names.names(), "Dusk"), "Dusk",
                "an unused name is used as is");

        // --- rename, edit, delete ---------------------------------------------
        check(store.rename(0, "Dusk"), "renaming works");
        eq(store.nameAt(0), "Dusk", "and the new name is what the list reads");
        check(store.rename(1, "Dusk"), "renaming onto a taken name is allowed");
        eq(store.nameAt(1), "Dusk (2)", "and is made distinct");
        check(!store.rename(0, "  "), "a blank name is refused");
        check(!store.rename(99, "x"), "an index past the end is refused");

        int[] black = store.slotsAt(0);
        black[17] = rgb(0, 0, 0);
        check(store.setSlots(0, black), "a palette's colours can be replaced");
        check(ai.openclaw.fterm.Schemes.isOled(
                ai.openclaw.fterm.Schemes.CUSTOM_INDEX, store.slotsAt(0)),
                "a black custom background counts as OLED");
        check(!ai.openclaw.fterm.Schemes.isOled(
                ai.openclaw.fterm.Schemes.CUSTOM_INDEX + 1, store.slotsAt(1)),
                "a custom index that is not black does not");
        check(!store.setSlots(0, new int[3]), "slots of the wrong length are refused");

        // --- storage round trip ------------------------------------------------
        String encoded = store.encode();
        ai.openclaw.fterm.CustomSchemes back = ai.openclaw.fterm.CustomSchemes.decode(encoded);
        eq(back.size(), 16, "every scheme comes back");
        for (int i = 0; i < 16; i++) {
            eq(back.nameAt(i), store.nameAt(i), "name " + i + " round trips");
            check(java.util.Arrays.equals(back.slotsAt(i), store.slotsAt(i)),
                    "slots " + i + " round trip");
        }
        eq(ai.openclaw.fterm.CustomSchemes.decode(null).size(), 0, "null is an empty list");
        eq(ai.openclaw.fterm.CustomSchemes.decode("").size(), 0, "empty is an empty list");
        eq(ai.openclaw.fterm.CustomSchemes.decode(encoded + "\u001ebroken\u001fxx").size(), 16,
                "a malformed record is dropped, not fatal");
        eq(ai.openclaw.fterm.CustomSchemes.decode("\u001e\u001e\u001e").size(), 0,
                "empty records are skipped");
        ai.openclaw.fterm.CustomSchemes odd = ai.openclaw.fterm.CustomSchemes.empty();
        odd.add("a\u001eb\u001fc", slots(6));
        check(odd.nameAt(0).indexOf('\u001e') < 0 && odd.nameAt(0).indexOf('\u001f') < 0,
                "the separators cannot appear in a stored name");

        // --- deletion shifts the selection ------------------------------------
        ai.openclaw.fterm.CustomSchemes three = ai.openclaw.fterm.CustomSchemes.empty();
        three.add("A", slots(1));
        three.add("B", slots(2));
        three.add("C", slots(3));
        eq(ai.openclaw.fterm.CustomSchemes.schemeIndexOf(2), 8, "the third scheme is index 8");
        eq(ai.openclaw.fterm.CustomSchemes.themeIndexOf(8), 2, "and index 8 is the third");
        eq(ai.openclaw.fterm.CustomSchemes.themeIndexOf(3), -1, "a built-in has no position");
        check(three.remove(1), "the middle scheme is removed");
        eq(three.size(), 2, "two left");
        eq(three.nameAt(1), "C", "C moved down into the hole");
        eq(three.slotsForScheme(7)[17], slots(3)[17], "so index 7 now draws C");
        check(!three.remove(5), "removing past the end is refused");

        ai.openclaw.fterm.CustomSchemes empty = ai.openclaw.fterm.CustomSchemes.empty();
        eq(empty.slotsForScheme(ai.openclaw.fterm.Schemes.CUSTOM_INDEX)[17],
                ai.openclaw.fterm.Schemes.campbellSlots()[17],
                "with no scheme saved, the editor still has a real palette to show");

        // --- which index a stored preference should become ---------------------
        eq(ai.openclaw.fterm.Schemes.clampScheme(2, 0), 2, "a built-in is left alone");
        eq(ai.openclaw.fterm.Schemes.clampScheme(8, 3), 8, "a custom index inside the list");
        eq(ai.openclaw.fterm.Schemes.clampScheme(9, 3), 6,
                "the one just past the third falls back to the first");
        eq(ai.openclaw.fterm.Schemes.clampScheme(9, 1), 6, "one past the end falls back to the first");
        eq(ai.openclaw.fterm.Schemes.clampScheme(21, 16), 21, "the last of sixteen is valid");
        eq(ai.openclaw.fterm.Schemes.clampScheme(9, 0), 0, "no scheme saved falls back to Campbell");
        eq(ai.openclaw.fterm.Schemes.clampScheme(40, 16), 6, "and something absurd is not drawn");

        // --- names the settings row prints ------------------------------------
        eq(ai.openclaw.fterm.Schemes.nameOf(0, store), "Campbell", "a built-in keeps its name");
        eq(ai.openclaw.fterm.Schemes.nameOf(ai.openclaw.fterm.Schemes.CUSTOM_INDEX, store),
                "Dusk", "a custom scheme is named by the user");
        eq(ai.openclaw.fterm.Schemes.nameOf(ai.openclaw.fterm.Schemes.CUSTOM_INDEX + 9, store),
                "S9", "and so is every one of the sixteen");
        eq(ai.openclaw.fterm.Schemes.nameOf(3, null), "Dracula", "no list is not a crash");

        // The palette the emulator receives is the stored one, at any custom index.
        int[] table = ai.openclaw.fterm.Schemes.colors(
                ai.openclaw.fterm.Schemes.CUSTOM_INDEX + 9, store.slotsAt(9));
        eq(table[3], store.slotsAt(9)[3], "index 15 draws its own ANSI colours");
        eq(table[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND], store.slotsAt(9)[17],
                "and its own background");

        System.out.println("ok  " + pass + " custom scheme assertions");
        System.out.println("ALL OK");
    }
}
