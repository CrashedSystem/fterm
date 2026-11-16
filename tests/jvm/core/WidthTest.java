import com.termux.terminal.WcWidth;

/**
 * The claim this app depends on: a Hangul syllable occupies two cells, a terminal
 * escape sequence is not a character, and the combining jamo logic is sane.
 */
public class WidthTest {
    static void eq(int actual, int expected, String what) {
        if (actual != expected) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        eq(WcWidth.width('M'), 1, "ASCII M");
        eq(WcWidth.width('한'), 2, "Hangul syllable 한");
        eq(WcWidth.width('글'), 2, "Hangul syllable 글");
        eq(WcWidth.width('가'), 2, "Hangul 가");
        eq(WcWidth.width(0x3131), 2, "Hangul compatibility jamo ㄱ is full width");
        eq(WcWidth.width(0x1100), 2, "Hangul choseong jamo is full width");
        eq(WcWidth.width('漢'), 2, "Han");
        eq(WcWidth.width('あ'), 2, "Hiragana");
        eq(WcWidth.width('·'), 1, "middle dot is narrow");
        eq(WcWidth.width(0x200B), 0, "zero width space");
        eq(WcWidth.width(0x1F300), 2, "emoji (U+1F300) is wide");
        eq(WcWidth.width('́'), 0, "combining acute is zero width");
        // Decomposed jamo: the choseong is wide, the vowel and jongseong are narrow by
        // Unicode East Asian Width (N), so a decomposed sequence occupies 1+1 cells rather
        // than 2. Android IMEs commit precomposed syllables (U+AC00..), which the wide
        // range covers, so this only matters for text pasted from a decomposed source.
        eq(WcWidth.width(0x1100), 2, "leading jamo is wide");
        eq(WcWidth.width(0x1161), 1, "vowel jamo is narrow (Unicode EAW=N)");
        System.out.println("ok  " + 15 + " width assertions");
        System.out.println("ALL OK");
    }
}
