package com.termux.terminal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Focus reporting (DECSET 1004). opencode - and any full-screen TUI - learns the user left
 * or came back through CSI I / CSI O. It also GATES ITS NOTIFICATIONS on that signal: with
 * focus unknown it stays silent forever (measured in the opencode binary's attention code),
 * so a terminal that answers DECRQM for 1004 but never reports is worse than one that
 * refuses the mode.
 */
public class FocusTest {
  static int fails = 0;
  static void eq(Object a, Object b, String m) {
    boolean ok = a == null ? b == null : a.equals(b);
    if (!ok) { fails++; System.out.println("FAIL " + m + " got=" + a + " want=" + b); }
    else System.out.println("ok " + m);
  }
  static TerminalSessionClient client() {
    return new TerminalSessionClient() {
      public void onTextChanged(com.termux.terminal.TerminalSession s) {}
      public void onTitleChanged(com.termux.terminal.TerminalSession s) {}
      public void onBell(com.termux.terminal.TerminalSession s) {}
      public void onColorsChanged(com.termux.terminal.TerminalSession s) {}
      public void onNotification(com.termux.terminal.TerminalSession s, String t, String b) {}
      public void onSessionFinished(com.termux.terminal.TerminalSession s) {}
      public void onCopyTextToClipboard(com.termux.terminal.TerminalSession s, String t) {}
      public void onPasteTextFromClipboard(com.termux.terminal.TerminalSession s) {}
      public void onTerminalCursorStateChange(boolean state) {}
      public void setTerminalShellPid(com.termux.terminal.TerminalSession s, int pid) {}
      public Integer getTerminalCursorStyle() { return 0; }
      public void logError(String t, String m) {}
      public void logWarn(String t, String m) {}
      public void logInfo(String t, String m) {}
      public void logDebug(String t, String m) {}
      public void logVerbose(String t, String m) {}
      public void logStackTraceWithMessage(String t, String m, Exception e) {}
      public void logStackTrace(String t, Exception e) {}
    };
  }
  /** TerminalOutput that records what the emulator writes back, without a PTY. */
  static class RecSession extends TerminalOutput {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    @Override public void write(byte[] data, int offset, int count) {
      out.write(data, offset, count);
    }
    @Override public void titleChanged(String o, String n) {}
    @Override public void onCopyTextToClipboard(String t) {}
    @Override public void onPasteTextFromClipboard() {}
    @Override public void onBell() {}
    @Override public void onColorsChanged() {}
    String drained() {
      String s = out.toString();
      out.reset();
      return s;
    }
  }
  static void feed(TerminalEmulator e, String text) {
    byte[] b = text.getBytes(StandardCharsets.UTF_8);
    e.append(b, b.length);
  }
  public static void main(String[] args) throws Exception {
    RecSession s = new RecSession();
    TerminalEmulator e = new TerminalEmulator(s, 80, 24, 10, 20, 200, client());
    // 1. Without DECSET 1004 nothing is emitted.
    e.reportFocus(true);
    eq(s.drained(), "", "no focus report when 1004 is off");
    // 2. Enable 1004: CSI ? 1004 h
    feed(e, "\033[?1004h");
    eq(s.drained(), "", "enabling 1004 itself writes nothing");
    // 3. Focus changes now emit CSI I / CSI O.
    e.reportFocus(true);
    eq(s.drained(), "\033[I", "focus gained emits CSI I");
    e.reportFocus(false);
    eq(s.drained(), "\033[O", "focus lost emits CSI O");
    // 4. Reset 1004: emissions stop.
    feed(e, "\033[?1004l");
    e.reportFocus(true);
    eq(s.drained(), "", "no report after 1004 reset");
    // 5. DECRQM answers 1 (set) while enabled - what opencode probes with `CSI ?1004$p`.
    feed(e, "\033[?1004h");
    feed(e, "\033[?1004$p");
    eq(s.drained(), "\033[?1004;1$y", "DECRQM reports 1004 as set");
    System.out.println(fails == 0 ? "FocusTest ALL OK" : "FocusTest FAILURES: " + fails);
    if (fails > 0) System.exit(1);
  }
}
