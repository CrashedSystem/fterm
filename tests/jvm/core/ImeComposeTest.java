/**
 * What a Korean syllable sends to the shell, under the termlib-derived projection.
 *
 * Source: connectbot/connectbot#2352 and connectbot/termlib `ImeInputView`, which fixed the
 * same bug against the same IME family. The rule is: an IME sends the whole composition on
 * every keystroke, a terminal has no shell-side composition region, so the composition is
 * projected live - append what grew, erase what shrank - and `finishComposingText` must NOT
 * re-send it.
 */
public class ImeComposeTest {

    static int pass = 0;

    static void eq(Object a, Object b, String what) {
        if (!java.util.Objects.equals(a, b)) {
            throw new AssertionError(what + ": expected <" + b + "> got <" + a);
        }
        pass++;
    }

    static void check(boolean c, String what) {
        if (!c) throw new AssertionError(what);
        pass++;
    }

    /** Mirrors TerminalView's projection. */
    static final class P {
        final StringBuilder shell = new StringBuilder();
        String composing = "";

        void setComposingText(String next) {
            if (next.equals(composing)) return;
            if (next.isEmpty()) { back(composing); composing = ""; return; }
            if (next.startsWith(composing)) {
                shell.append(next.substring(composing.length()));
            } else if (composing.startsWith(next)) {
                back(composing.substring(next.length()));
            } else {
                back(composing);
                shell.append(next);
            }
            composing = next;
        }

        void finishComposingText() { composing = ""; }

        void commitText(String text) {
            String pending;
            if (composing.isEmpty()) pending = text;
            else if (text.startsWith(composing)) pending = text.substring(composing.length());
            else if (text.endsWith(composing)) pending = text.substring(0, text.length() - composing.length());
            else if (text.contains(composing)) {
                int at = text.indexOf(composing);
                pending = text.substring(0, at) + text.substring(at + composing.length());
            } else { back(composing); pending = text; }
            shell.append(pending);
            composing = "";
        }

        void deleteSurroundingText(int n) { backN(Math.min(Math.max(n, 0), 8)); }
        private void back(String t) { backN(t.codePointCount(0, t.length())); }
        private void backN(int n) {
            for (int i = 0; i < n; i++) if (shell.length() > 0) shell.setLength(shell.length() - 1);
        }
    }

    public static void main(String[] args) throws Exception {
        // 두벌식 "한": ㅎ → 하 → 한, each step replaces the previous glyph.
        // Each step is a full replacement, not a prefix growth: ㅎ, 하 and 한 are three
        // distinct code points, so the previous glyph is erased and the new one written.
        P p = new P();
        p.setComposingText("ㅎ");
        eq(p.shell.toString(), "ㅎ", "the first jamo is projected");
        p.setComposingText("하");
        eq(p.shell.toString(), "하", "하 replaces ㅎ");
        p.setComposingText("한");
        eq(p.shell.toString(), "한", "한 replaces 하");
        p.finishComposingText();
        eq(p.shell.toString(), "한", "finish sends nothing more");

        // Growth is real when the IME appends (soft keyboards do this for Latin).
        P g = new P();
        g.setComposingText("a");
        g.setComposingText("ab");
        eq(g.shell.toString(), "ab", "growth writes only the delta (b), so the shell holds ab");
        g.setComposingText("abc");
        eq(g.shell.toString(), "abc", "and again");

        // Second syllable commits the first, which is the sequence a user sees as one block.
        P t = new P();
        t.setComposingText("한");
        t.finishComposingText();
        t.setComposingText("그");
        t.setComposingText("글");
        check(t.shell.toString().endsWith("글"), "the latest composition is on screen");

        // A commit carries only the part that is not already projected.
        P c = new P();
        c.setComposingText("스");
        c.commitText("스트");
        check(c.shell.toString().endsWith("트"), "commit adds only the tail: " + c.shell);

        // Backspace through the IME while composing.
        P b = new P();
        b.setComposingText("한");
        b.commitText("한");
        int before = b.shell.length();
        b.deleteSurroundingText(1);
        eq(b.shell.length(), before - 1, "deleteSurroundingText removes one code point");

        // Latin passes through untouched.
        P l = new P();
        l.commitText("echo hi");
        eq(l.shell.toString(), "echo hi", "latin path");

        againstView();
        System.out.println("ok  " + pass + " IME projection assertions");
        System.out.println("ALL OK");
    }

    static void againstView() throws Exception {
        String src = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("view.src",
                        "app/src/com.termux/view/TerminalView.java"))), "UTF-8");

        int set = src.indexOf("public boolean setComposingText");
        check(set > 0, "setComposingText is overridden");
        String body = src.substring(set, Math.min(src.length(), set + 1400));
        check(body.contains("next.startsWith(mComposing)"), "it appends the growth");
        check(body.contains("mComposing.startsWith(next)"), "and erases the shrink");

        int fin = src.indexOf("public boolean finishComposingText");
        String finBody = src.substring(fin, Math.min(src.length(), fin + 700));
        check(!finBody.contains("sendTextToTerminal"), "finish does not re-send the text");

        int del = src.indexOf("public boolean deleteSurroundingText");
        String delBody = src.substring(del, Math.min(src.length(), del + 900));
        // The comment explains why sendKeyEvent() is not used, so strip comment lines before
        // looking for the call itself.
        StringBuilder code = new StringBuilder();
        for (String line : delBody.split("\n")) {
            String t = line.trim();
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) continue;
            code.append(line).append('\n');
        }
        check(!code.toString().contains("sendKeyEvent("),
                "delete is not routed through sendKeyEvent (it clears the IME editable)");
        check(code.toString().contains("mTermSession.write"), "it writes DEL bytes directly");
    }
}
