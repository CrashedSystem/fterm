package io.github.crashedsystem.fterm;

import android.content.Context;
import java.util.Map;
import java.util.HashMap;

public final class AppStrings {
    private static Context ctx = null;
    private static final Map<Integer, String> FALLBACK = new HashMap<>();
    static {
        FALLBACK.put(R.string.app_name, AppStrings.s(R.string.i18n_0030));
        FALLBACK.put(R.string.copy_text, AppStrings.s(R.string.i18n_0031));
        FALLBACK.put(R.string.paste_text, AppStrings.s(R.string.i18n_0032));
        FALLBACK.put(R.string.text_selection_more, AppStrings.s(R.string.i18n_0033));
        FALLBACK.put(R.string.notify_channel_session, AppStrings.s(R.string.i18n_0034));
        FALLBACK.put(R.string.notify_channel_shell, AppStrings.s(R.string.i18n_0035));
        FALLBACK.put(R.string.i18n_0001, "ë°ëª¬ íë ì ì¤ë¥");
        FALLBACK.put(R.string.i18n_0002, "í í°ì´ íë ¸ìµëë¤ â Termuxìì ftermd token ì íì¸íì¸ì");
        FALLBACK.put(R.string.i18n_0003, "ë°ëª¬ ì¤ë¥ (code ");
        FALLBACK.put(R.string.i18n_0004, "Termux ë°ëª¬ì ì°ê²°í  ì ììµëë¤: ");
        FALLBACK.put(R.string.i18n_0005, "ê¸ê¼´ íë ì ì¤ë¥");
        FALLBACK.put(R.string.i18n_0006, "ê¸ê¼´ì ë°ì§ ëª»íìµëë¤: ");
        FALLBACK.put(R.string.i18n_0007, "ì£¼ìê° .ttf/.otfë¡ ëëì§ ììµëë¤");
        FALLBACK.put(R.string.i18n_0008, "ê¸ê¼´ ìëµ ì¤ë¥");
        FALLBACK.put(R.string.i18n_0009, "ê¸ê¼´ ë¤ì´ë¡ë ì¤í¨");
        FALLBACK.put(R.string.i18n_0010, "ë°ëª¬ ìëµ ì¤ë¥ (type ");
        FALLBACK.put(R.string.i18n_0011, "ì°ê²° ì¤í¨: ");
        FALLBACK.put(R.string.i18n_0012, "íë ì ê¸¸ì´ ì¤ë¥");
        FALLBACK.put(R.string.i18n_0013, "ë°ëª¬ì´ ì°ê²°ì ë«ììµëë¤");
        FALLBACK.put(R.string.i18n_0014, "ì°ê²°ì´ ëê²¼ìµëë¤ (ìëµ ìì)");
        FALLBACK.put(R.string.i18n_0015, "ìì  ì¤ë¥: ");
        FALLBACK.put(R.string.i18n_0016, "ë°ëª¬ íë¡í ì½ ë²ì ì´ ë¤ë¦ëë¤");
        FALLBACK.put(R.string.i18n_0017, "ì¸ìì´ ììµëë¤");
        FALLBACK.put(R.string.i18n_0018, "ë°ëª¬ì´ ì¸ì ëì°ì§ ëª»íìµëë¤");
        FALLBACK.put(R.string.i18n_0019, "ëì ì¸ì íëë¥¼ ëììµëë¤");
        FALLBACK.put(R.string.i18n_0020, "ë°ëª¬ ì¤ë¥: ");
        FALLBACK.put(R.string.i18n_0021, "ì¡ì  ì¤ë¥: ");
        FALLBACK.put(R.string.i18n_0022, "í");
        FALLBACK.put(R.string.i18n_0023, "í");
        FALLBACK.put(R.string.i18n_0024, "ì ë³´");
        FALLBACK.put(R.string.i18n_0025, "ë²ì  ");
        FALLBACK.put(R.string.i18n_0026, "ê¸¸ê² ëë¥´ê¸°: íì¤í¸ ì í
");
        FALLBACK.put(R.string.i18n_0027, "í­ ê¸¸ê² ëë¥´ê¸°: í­ ë«ê¸°
");
        FALLBACK.put(R.string.i18n_0028, "í¤ ë° ì¢ì° ê°ì¥ìë¦¬: F1~F12");
        FALLBACK.put(R.string.i18n_0029, "ë«ê¸°");
        FALLBACK.put(R.string.i18n_0030, "í°ë¯¸ë");
        FALLBACK.put(R.string.i18n_0031, "ë³µì¬");
        FALLBACK.put(R.string.i18n_0032, "ë¶ì¬ë£ê¸°");
        FALLBACK.put(R.string.i18n_0033, "ë ë³´ê¸°");
        FALLBACK.put(R.string.i18n_0034, "í°ë¯¸ë ì¸ì");
        FALLBACK.put(R.string.i18n_0035, "ì¸ ìë¦¼");
        FALLBACK.put(R.string.i18n_0036, "error: ê·¸ ê²½ë¡ë ì½ì ì ììµëë¤: ");
        FALLBACK.put(R.string.i18n_0037, "error: --es path <íì¼> ëë content:// URI ê° íìí©ëë¤");
        FALLBACK.put(R.string.i18n_0038, "error: ì´ë¯¸ì§ë¥¼ ì½ê±°ë ì ì¥íì§ ëª»íìµëë¤");
        FALLBACK.put(R.string.i18n_0039, "ok: ë°°ê²½ ì´ë¯¸ì§ë¥¼ ì§ì ìµëë¤");
        FALLBACK.put(R.string.i18n_0040, "ì±ì°ê¸° (cover)");
        FALLBACK.put(R.string.i18n_0041, "ë§ì¶¤ (contain)");
        FALLBACK.put(R.string.i18n_0042, "ì¼ìª½ ì");
        FALLBACK.put(R.string.i18n_0043, "ì");
        FALLBACK.put(R.string.i18n_0044, "ì¤ë¥¸ìª½ ì");
        FALLBACK.put(R.string.i18n_0045, "ì¼ìª½");
        FALLBACK.put(R.string.i18n_0046, "ê°ì´ë°");
        FALLBACK.put(R.string.i18n_0047, "ì¤ë¥¸ìª½");
        FALLBACK.put(R.string.i18n_0048, "ì¼ìª½ ìë");
        FALLBACK.put(R.string.i18n_0049, "ìë");
        FALLBACK.put(R.string.i18n_0050, "ì¤ë¥¸ìª½ ìë");
        FALLBACK.put(R.string.i18n_0051, "ë°°ê²½ ì´ë¯¸ì§");
        FALLBACK.put(R.string.i18n_0052, "background: ì½ì§ ëª»í¨ ");
        FALLBACK.put(R.string.i18n_0053, "background: ì´ë¯¸ì§ë¡ ëì½ëí  ì ìì");
        FALLBACK.put(R.string.i18n_0054, "background: ì ì¥íì§ ëª»í¨");
        FALLBACK.put(R.string.i18n_0055, "íì¼ì´ ëë¬´ í½ëë¤");
        FALLBACK.put(R.string.i18n_0056, "í­ ì´ë¦");
        FALLBACK.put(R.string.i18n_0057, "ì ì¥");
        FALLBACK.put(R.string.i18n_0058, "ì·¨ì");
        FALLBACK.put(R.string.i18n_0059, "ì í­ ì´ê¸°");
        FALLBACK.put(R.string.i18n_0060, "í­ ë«ê¸°");
        FALLBACK.put(R.string.i18n_0061, "í­ ì´ë¦ ë°ê¾¸ê¸°");
        FALLBACK.put(R.string.i18n_0062, "ë¤ì í­");
        FALLBACK.put(R.string.i18n_0063, "ì°¾ê¸°");
        FALLBACK.put(R.string.i18n_0064, "ê¸ì í¬ê¸° í¤ì°ê¸°");
        FALLBACK.put(R.string.i18n_0065, "ê¸ì í¬ê¸° ì¤ì´ê¸°");
        FALLBACK.put(R.string.i18n_0066, "ì¤ì  ì´ê¸°");
        FALLBACK.put(R.string.i18n_0067, "í¤ë³´ë ë³´ì´ê¸°/ì¨ê¸°ê¸°");
        FALLBACK.put(R.string.i18n_0068, "ëªë ¹ ê²ì");
        FALLBACK.put(R.string.i18n_0069, "ì´ë¯¸ì§ë¥¼ ì½ì§ ëª»íìµëë¤");
        FALLBACK.put(R.string.i18n_0070, "ì¬ì©ì ì§ì ");
        FALLBACK.put(R.string.i18n_0071, "Termux ë°ëª¬ ì¤ë¹ ì¤â¦");
        FALLBACK.put(R.string.i18n_0072, "Termuxìì `ftermd token` ì¶ë ¥ê°");
        FALLBACK.put(R.string.i18n_0073, "Termux ì°ê²°");
        FALLBACK.put(R.string.i18n_0074, "Termuxìì ë¤ìì í ë² ì¤ííì¸ì:

");
        FALLBACK.put(R.string.i18n_0075, "ì¶ë ¥ë í í°ì ë¶ì¬ë£ì¼ì¸ì.");
        FALLBACK.put(R.string.i18n_0076, "Termux ëªë ¹ ë³µì¬");
        FALLBACK.put(R.string.i18n_0077, "ë³µì¬ë¨ â Termuxì ë¶ì¬ë£ê³  ì¤ííì¸ì");
        FALLBACK.put(R.string.i18n_0078, "Termux ì¤ë¹ê° íìí©ëë¤");
        FALLBACK.put(R.string.i18n_0079, "ëªë ¹ ë³µì¬");
        FALLBACK.put(R.string.i18n_0080, "ì ì¥ìì ìì²­ íì¼ì ì¸ ì ììµëë¤");
        FALLBACK.put(R.string.i18n_0081, "ftermì /sdcard/Download/fterm-up íì¼ë¡ Termuxì keeperìê² ë°ëª¬ì
");
        FALLBACK.put(R.string.i18n_0082, "ìì²­í©ëë¤. ê·¸ íì¼ì ì°ì§ ëª»íë©´ ì±ì´ ì¤ì¤ë¡ ë°ëª¬ì ëì¸ ì ììµëë¤.

");
        FALLBACK.put(R.string.i18n_0083, "íì¸í  ê²:
");
        FALLBACK.put(R.string.i18n_0084, "  Â· ì ì¥ì ê¶íì´ íì©ëì´ ìëì§
");
        FALLBACK.put(R.string.i18n_0085, "  Â· /sdcard/Download ê° ì°ê¸° ê°ë¥íì§

");
        FALLBACK.put(R.string.i18n_0086, "Termuxìì í ë² ì¤ííë©´ í í°ì ì§ì  ë°ì ìë ììµëë¤:
");
        FALLBACK.put(R.string.i18n_0087, "ë°ëª¬ì´ ìëµíì§ ììµëë¤");
        FALLBACK.put(R.string.i18n_0088, "keeperë ì´ììì§ë§ ë°ëª¬ì´ ë¨ì§ ìììµëë¤.

");
        FALLBACK.put(R.string.i18n_0089, "Termuxìì íì¸:
");
        FALLBACK.put(R.string.i18n_0090, "ë¤ì ìë");
        FALLBACK.put(R.string.i18n_0091, "Termuxìì í ë²ë§ ì¤ííì¸ì:

");
        FALLBACK.put(R.string.i18n_0092, "install.sh ê° ë°ëª¬ê³¼ keeperë¥¼ ì¤ì¹íê³  keeperë¥¼ ëìëë¤.
");
        FALLBACK.put(R.string.i18n_0093, "ê·¸ ë¤ì ì´ ì±ì¼ë¡ ëìì¤ë©´ ìëì¼ë¡ ì°ê²°ë©ëë¤.");
        FALLBACK.put(R.string.i18n_0094, "ìì");
        FALLBACK.put(R.string.i18n_0095, "íì¼ì ë°ì§ ëª»íìµëë¤.");
        FALLBACK.put(R.string.i18n_0096, "TTF ëë OTF íì¼ì´ ìëëë¤: ");
        FALLBACK.put(R.string.i18n_0097, "íì¼ì ì´ ì ììµëë¤: ");
        FALLBACK.put(R.string.i18n_0098, "íì¼ì ë³µì¬íì§ ëª»íìµëë¤: ");
        FALLBACK.put(R.string.i18n_0099, "ë¹ íì¼ìëë¤: ");
        FALLBACK.put(R.string.i18n_0100, "ë¹ íì¼ìëë¤.");
        FALLBACK.put(R.string.i18n_0101, "íì¼ì ì ì¥íì§ ëª»íìµëë¤: ");
        FALLBACK.put(R.string.i18n_0102, "ê¸ê¼´ íì¼ì ì´ ì ììµëë¤: ");
        FALLBACK.put(R.string.i18n_0103, "ê¸ì í­ì´ ì¼ì íì§ ìì ê¸ê¼´ìëë¤: ");
        FALLBACK.put(R.string.i18n_0104, "

í°ë¯¸ëì í ì¹¸ì´ í ê¸ìì¸ ê²©ìë¼, ë¹ë¡ ê¸ê¼´ì ì°ë©´ ì´ì´ ì´ê¸ë©ëë¤.");
        FALLBACK.put(R.string.i18n_0105, "
íê¸ ê¸ê¼´ì´ë©´ ê¸ê¼´ ëª©ë¡ì ãíê¸ ê¸ê¼´ãìì ê³ ë¥¼ ì ììµëë¤.");
        FALLBACK.put(R.string.i18n_0106, "Iosevka (ê¸°ë³¸)");
        FALLBACK.put(R.string.i18n_0107, " (ë´ ê¸ê¼´)");
        FALLBACK.put(R.string.i18n_0108, "Pretendard (ê¸°ë³¸)");
        FALLBACK.put(R.string.i18n_0109, " (ì§ì  ì§ì )");
        FALLBACK.put(R.string.i18n_0110, "ì¸ì ì°ê²°íë ì¤â¦");
        FALLBACK.put(R.string.i18n_0111, " is the user's switch, and off means off. On top of it, a keyboard that
     * can type hides a bar holding the keys that keyboard already has; the ëª¨ì page's
     * auto-hide row is what lets a tablet that is sometimes a tablet keep the bar.
     */
    void applyKeyBarVisibility() {
        if (mKeyBar == null) return;
        mKeyBar.setVisible(mPrefs.keyBarVisible()
                && !(mPrefs.keyBarAutoHide() && HardwareKeyboard.present(this)));
        // The inset that the bar is not using moves to the terminal; ask for them again so
        // the move happens now rather than at the next rotation.
        if (mRoot != null) mRoot.requestApplyInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBackdrop != null) mBackdrop.resume();
        if (mShowKeyboardOnce && mPrefs.showKeyboardOnStart() && !HardwareKeyboard.present(this)) {
            mShowKeyboardOnce = false;
            showKeyboardSoon();
        }
    }

    /**
     * With {@code enableOnBackInvokedCallback=");
        FALLBACK.put(R.string.i18n_0112, "OLED (ìì ê²ì )");
        FALLBACK.put(R.string.i18n_0113, "ì¸ì");
        FALLBACK.put(R.string.i18n_0114, "ë°ëª¬");
        FALLBACK.put(R.string.i18n_0115, "127.0.0.1 ì PTY ìë², í í° ì¸ì¦");
        FALLBACK.put(R.string.i18n_0116, "ë ëë§");
        FALLBACK.put(R.string.i18n_0117, "xterm-256color Â· íê¸ 2ì¹¸ í­");
        FALLBACK.put(R.string.i18n_0118, "fterm â Android ìì Windows Terminal ëª¨ìì ëª©íë¡ íë í°ë¯¸ë. ");
        FALLBACK.put(R.string.i18n_0119, "ì¸ì Termuxì ë°ëª¬ì´ ìì íê³ , ì±ì íë©´ê³¼ ìë ¥ë§ ë§¡ìµëë¤.");
        FALLBACK.put(R.string.i18n_0120, "ë¸ë¡");
        FALLBACK.put(R.string.i18n_0121, "ë°ì¤");
        FALLBACK.put(R.string.i18n_0122, "ì¸ë¡ ë§ë");
        FALLBACK.put(R.string.i18n_0123, "ê¸ì í¬ê¸°");
        FALLBACK.put(R.string.i18n_0124, "ê¸ê¼´");
        FALLBACK.put(R.string.i18n_0125, "ë¼í´ ë¬¸ìì ì°ë ê³ ì í­ ê¸ê¼´");
        FALLBACK.put(R.string.i18n_0126, "íê¸ ê¸ê¼´");
        FALLBACK.put(R.string.i18n_0127, "í ê¸ìê° ì íí ë ì¹¸ì ì°¨ì§íê² ë§ì¶¤");
        FALLBACK.put(R.string.i18n_0128, "ì êµ¬ì±í");
        FALLBACK.put(R.string.i18n_0129, "ë¯¸ë¦¬ë³´ê¸°ì 16ì íë í¸");
        FALLBACK.put(R.string.i18n_0130, "í¤ ë° íì");
        FALLBACK.put(R.string.i18n_0131, "íë©´ ìë ë ì¤ì§ë¦¬ í¤ ë°");
        FALLBACK.put(R.string.i18n_0132, "íëì¨ì´ í¤ë³´ëìì í¤ ë° ì¨ê¹");
        FALLBACK.put(R.string.i18n_0133, "DeXÂ·ë¶ì»¤ë² í¤ë³´ëê° ë¶ì´ ìì¼ë©´ í¤ ë°ë¥¼ ìëì¼ë¡ ì¨ê¹ëë¤");
        FALLBACK.put(R.string.i18n_0134, "ì»¤ì ëª¨ì");
        FALLBACK.put(R.string.i18n_0135, "ë¸ë¡ Â· ë°ì¤ Â· ì¸ë¡ ë§ë");
        FALLBACK.put(R.string.i18n_0136, "ì»¤ì ê¹ë¹¡ì");
        FALLBACK.put(R.string.i18n_0137, "ëë©´ í­ì ë³´ìëë¤");
        FALLBACK.put(R.string.i18n_0138, "ì»¤ì ì");
        FALLBACK.put(R.string.i18n_0139, "êµ¬ì±íë¥¼ ë°ë¥´ì§ ìê³  ì§ì  ì§ì ");
        FALLBACK.put(R.string.i18n_0140, "êµ¬ì±í ë°ë¦");
        FALLBACK.put(R.string.i18n_0141, "í­ ìì´ì½");
        FALLBACK.put(R.string.i18n_0142, "í­ ì ëª© ìì ìì íì");
        FALLBACK.put(R.string.i18n_0143, "ë í¸ë¡ í¨ê³¼");
        FALLBACK.put(R.string.i18n_0144, "íê´ ê¸ë¡ì°ì ì£¼ì¬ì  (Windows Terminal)");
        FALLBACK.put(R.string.i18n_0145, "ê·¸ë¦¼ì êµ¬ì±íì ë°ë¡ ì ì©ë©ëë¤. ê¸°ê¸°ìì ê³ ë¥´ê±°ë, ì±ì bg í´ëì ");
        FALLBACK.put(R.string.i18n_0146, "íì¼ì ë£ì¼ë©´ ìµì  ì´ë¯¸ì§ê° ê·¸ë ¤ì§ëë¤.");
        FALLBACK.put(R.string.i18n_0147, "ë¹ì°ë©´ êµ¬ì±íë¥¼ ë°ë¦ëë¤");
        FALLBACK.put(R.string.i18n_0148, "ì íìì´ ìëëë¤: ");
        FALLBACK.put(R.string.i18n_0149, "í°ë¯¸ë ê¸ì ë¤ì ê·¸ë¦´ ê°ì¸ ì´ë¯¸ì§");
        FALLBACK.put(R.string.i18n_0150, "í¬ëªë");
        FALLBACK.put(R.string.i18n_0151, "ê·¸ë¦¼ì ì¼ë§ë ì§íê² ê·¸ë¦´ì§");
        FALLBACK.put(R.string.i18n_0152, "ë§ì¶¤");
        FALLBACK.put(R.string.i18n_0153, "íë©´ì ë§ì¶ë ë°©ì");
        FALLBACK.put(R.string.i18n_0154, "ìì¹");
        FALLBACK.put(R.string.i18n_0155, "ì¬ë°±ì´ ìê¸¸ ë ê·¸ë¦¼ì ë¶ì¼ ê³³");
        FALLBACK.put(R.string.i18n_0156, "ì´ë¡ê²");
        FALLBACK.put(R.string.i18n_0157, "ê¸ìê° ë¬»íë©´ ì¬ë¦¬ì¸ì");
        FALLBACK.put(R.string.i18n_0158, "ê¸°ê¸°ìì ê³ ë¥´ê¸°");
        FALLBACK.put(R.string.i18n_0159, "Termux Â· adb ê²½ë¡ ìë´");
        FALLBACK.put(R.string.i18n_0160, "ë°°ê²½ ìì ê¸°");
        FALLBACK.put(R.string.i18n_0161, "íì¼ ì íê¸°ë¥¼ ì´ ì ììµëë¤: ");
        FALLBACK.put(R.string.i18n_0162, "ë°°ê²½ ì´ë¯¸ì§ë¥¼ ì ì©íìµëë¤");
        FALLBACK.put(R.string.i18n_0163, "ì´ë¯¸ì§ë¥¼ ì½ì§ ëª»íìµëë¤ (ì§ìíì§ ìë íìì´ê±°ë ëë¬´ í½ëë¤)");
        FALLBACK.put(R.string.i18n_0164, "ë°°ê²½ ì´ë¯¸ì§ë¥¼ ì§ì ìµëë¤");
        FALLBACK.put(R.string.i18n_0165, "ë§ì¶¤ (contain) â ì ì²´ê° ë³´ì´ê³  ì¬ë°±");
        FALLBACK.put(R.string.i18n_0166, "ì±ì°ê¸° (cover) â ê½ ì±ì°ê³  ìë¦¼");
        FALLBACK.put(R.string.i18n_0167, "ë°°ê²½ ìì¹");
        FALLBACK.put(R.string.i18n_0168, "í´ë (ìµì  ì´ë¯¸ì§ê° ë°°ê²½ì´ ë©ëë¤)
");
        FALLBACK.put(R.string.i18n_0169, "

adbë¡ ë£ê¸°
");
        FALLBACK.put(R.string.i18n_0170, "

ì§ì°ê¸°
");
        FALLBACK.put(R.string.i18n_0171, "ê²½ë¡ë¡ ì ì©");
        FALLBACK.put(R.string.i18n_0172, "íì¸");
        FALLBACK.put(R.string.i18n_0173, "ê¸ê¼´ ì¤ì¹ â Termuxìì ë°ê¸°, ê¸°ê¸°ìì ê³ ë¥´ê¸°");
        FALLBACK.put(R.string.i18n_0174, "Mmm 0123 í");
        FALLBACK.put(R.string.i18n_0175, "ë¤ì ì°¾ê¸°");
        FALLBACK.put(R.string.i18n_0176, "íê¸ ê°ëë¤ 12");
        FALLBACK.put(R.string.i18n_0177, "íê¸ ê°ëë¤ â²");
        FALLBACK.put(R.string.i18n_0178, "ìëª¨");
        FALLBACK.put(R.string.i18n_0179, " Â· ìëª¨");
        FALLBACK.put(R.string.i18n_0180, "ì¤ì¹");
        FALLBACK.put(R.string.i18n_0181, "Termuxìì ê¸ê¼´ì ì°¾ë ì¤â¦");
        FALLBACK.put(R.string.i18n_0182, "ì´ ê¸°ê¸°ì íì¼ìì ê³ ë¥´ê¸°");
        FALLBACK.put(R.string.i18n_0183, "URLë¡ ë°ê¸° (Termuxê° ëì  ë´ë ¤ë°ì)");
        FALLBACK.put(R.string.i18n_0184, "ê¸ê¼´ ì¤ì¹");
        FALLBACK.put(R.string.i18n_0185, "Termux ê¸ê¼´ ëª©ë¡ì ê°ì ¸ì¤ì§ ëª»íìµëë¤.
");
        FALLBACK.put(R.string.i18n_0186, "Termux ìª½ì ê¸ê¼´ì´ ììµëë¤. ìë URLë¡ ë°ê±°ë, Termuxìì ");
        FALLBACK.put(R.string.i18n_0187, "~/.fonts ì .ttfë¥¼ ë£ì¼ì¸ì.");
        FALLBACK.put(R.string.i18n_0188, "Termuxìì ì°¾ì ê¸ê¼´ ");
        FALLBACK.put(R.string.i18n_0189, "ê°");
        FALLBACK.put(R.string.i18n_0190, " ë°ë ì¤â¦");
        FALLBACK.put(R.string.i18n_0191, "ê¸ê¼´ URL");
        FALLBACK.put(R.string.i18n_0192, "Termuxê° ëì  ë´ë ¤ë°ì ~/.fonts ì ì ì¥í ë¤ ì±ì¼ë¡ ëê¹ëë¤. ");
        FALLBACK.put(R.string.i18n_0193, ".ttf/.otfë¡ ëëë http(s) ì£¼ìì¬ì¼ í©ëë¤.");
        FALLBACK.put(R.string.i18n_0194, "http:// ëë https:// ì£¼ìê° ìëëë¤.");
        FALLBACK.put(R.string.i18n_0195, "ë´ë ¤ë°ë ì¤â¦");
        FALLBACK.put(R.string.i18n_0196, "íì¼ ì íê¸°ë¥¼ ì´ ì ììµëë¤.

");
        FALLBACK.put(R.string.i18n_0197, "ê° Â· ");
        FALLBACK.put(R.string.i18n_0198, "ë°ì´í°ë² ì´ì¤");
        FALLBACK.put(R.string.i18n_0199, "ê¸°ë³¸");
        FALLBACK.put(R.string.i18n_0200, "ë°ì´í°ë² â¦");
        FALLBACK.put(R.string.i18n_0201, "ì§ì  ìë ¥");
        FALLBACK.put(R.string.i18n_0202, "íë ê¸ìê° ê°ì¥ ì ë§ìµëë¤. ê¸¸ë©´ ìê² ê·¸ë ¤ì§ëë¤.");
        FALLBACK.put(R.string.i18n_0203, "í°ë¯¸ë ë²¨ Â· ì§ë");
        FALLBACK.put(R.string.i18n_0204, "BEL ë¬¸ìë¥¼ ë°ì¼ë©´ ì§§ê² ì§ëí©ëë¤");
        FALLBACK.put(R.string.i18n_0205, "í°ë¯¸ë ë²¨ Â· ìë¦¬");
        FALLBACK.put(R.string.i18n_0206, "ë¬´ì ëª¨ëê° ìë ëë§ ì¸ë¦½ëë¤");
        FALLBACK.put(R.string.i18n_0207, "ë¤ë¡ ê°ê¸° = Esc");
        FALLBACK.put(R.string.i18n_0208, "vimÂ·lessìì ë¤ë¡ ê°ê¸°ê° ì·¨ìë¡ ëì");
        FALLBACK.put(R.string.i18n_0209, "ê¸¸ê² ëë¥´ê¸°: íì¤í¸ ì í Â· í­ ê¸¸ê² ëë¥´ê¸°: í­ ë«ê¸° Â· í¤ ë° ê°ì¥ìë¦¬: F1~F12");
        FALLBACK.put(R.string.i18n_0210, "íë¡íì´ ììµëë¤");
        FALLBACK.put(R.string.i18n_0211, "ìëìì ì¶ê°íë©´ ë©ë´ ì²« ë¸ë¡ì ëíë©ëë¤.");
        FALLBACK.put(R.string.i18n_0212, "íë¡í ì¶ê°");
        FALLBACK.put(R.string.i18n_0213, "ì: ssh ìë² ì´ë¦ê³¼ ëªë ¹");
        FALLBACK.put(R.string.i18n_0214, "ëªë ¹ì opencodeë¥¼ ë£ì¼ì¸ì");
        FALLBACK.put(R.string.i18n_0215, "ìì : opencode");
        FALLBACK.put(R.string.i18n_0216, "OpenCodeë¥¼ ëì°ë íë¡íì ì¶ê°í©ëë¤");
        FALLBACK.put(R.string.i18n_0217, "ì¤ì ëë¥´ë©´ í¸ì§í©ëë¤. í­ì ì´ ëë ì¼ìª½ ì ë©ë´ ì²« ë¸ë¡ìì ");
        FALLBACK.put(R.string.i18n_0218, "íë¡íì ê³ ë¥´ì¸ì. ëªë ¹ì ì¸ì´ ë¬ ë¤ì ìë ¥ëë¯ë¡ sshê° ë¡ê·¸ì¸ ì¸ì í¤ì ");
        FALLBACK.put(R.string.i18n_0219, "ì¤ì ì ê·¸ëë¡ ìëë¤.");
        FALLBACK.put(R.string.i18n_0220, "opencode íë¡íì´ ì´ë¯¸ ììµëë¤. íë¡í ëª©ë¡ìì ëë¬ í¸ì§íì¸ì.");
        FALLBACK.put(R.string.i18n_0221, "ì´ë¦ ã");
        FALLBACK.put(R.string.i18n_0222, "ã, ìì ëªë ¹ ã");
        FALLBACK.put(R.string.i18n_0223, "ã, ìì´ì½ OpenCodeë¡ íë¡íì ì¶ê°í©ëë¤.

");
        FALLBACK.put(R.string.i18n_0224, "í­ì ì´ë©´ ì¸ ììì opencodeê° ì¤íë©ëë¤. ");
        FALLBACK.put(R.string.i18n_0225, "ì¤ì¹ ê²½ë¡ê° PATHì ìì´ì¼ í©ëë¤.");
        FALLBACK.put(R.string.i18n_0226, "ì¶ê°");
        FALLBACK.put(R.string.i18n_0227, "ìë²");
        FALLBACK.put(R.string.i18n_0228, "(ì´ë¦ ìì)");
        FALLBACK.put(R.string.i18n_0229, "ì: ìë²");
        FALLBACK.put(R.string.i18n_0230, "ì´ë¦");
        FALLBACK.put(R.string.i18n_0231, "ìì´ì½");
        FALLBACK.put(R.string.i18n_0232, "í­ ìì´ì½ê³¼ ê°ì");
        FALLBACK.put(R.string.i18n_0233, "íë¡í ìì´ì½");
        FALLBACK.put(R.string.i18n_0234, "ìì ëë í°ë¦¬ (ì í)");
        FALLBACK.put(R.string.i18n_0235, "ì¸ (ì í)");
        FALLBACK.put(R.string.i18n_0236, "ìì ëªë ¹ (ì í)");
        FALLBACK.put(R.string.i18n_0237, "ëªë ¹ì í ì¤ì´ì´ì¼ í©ëë¤. ë¹ì°ë©´ ê·¸ë¥ ì¸ì´ ë¹ëë¤.");
        FALLBACK.put(R.string.i18n_0238, "íë¡í í¸ì§");
        FALLBACK.put(R.string.i18n_0239, "ì­ì ");
        FALLBACK.put(R.string.i18n_0240, "íë¡í");
        FALLBACK.put(R.string.i18n_0241, "ìì");
        FALLBACK.put(R.string.i18n_0242, "ê¸°ë³¸ íë¡í, ì¸, ìì ëì");
        FALLBACK.put(R.string.i18n_0243, "sshì²ë¼ ëªë ¹ì¼ë¡ ììíë í­");
        FALLBACK.put(R.string.i18n_0244, "ëª¨ì");
        FALLBACK.put(R.string.i18n_0245, "ê¸ì í¬ê¸°, í¤ ë°");
        FALLBACK.put(R.string.i18n_0246, "16ì íë í¸ì ë¯¸ë¦¬ë³´ê¸°");
        FALLBACK.put(R.string.i18n_0247, "ìí¸ ìì©");
        FALLBACK.put(R.string.i18n_0248, "ë²¨, ë¤ë¡ ê°ê¸° í¤");
        FALLBACK.put(R.string.i18n_0249, "ë²ì ê³¼ ì°ê²° ìí");
        FALLBACK.put(R.string.i18n_0250, "ì´ë¦ ë³ê²½");
        FALLBACK.put(R.string.i18n_0251, "êµ¬ì±í ì´ë¦");
        FALLBACK.put(R.string.i18n_0252, "ê°ì ì´ë¦ì´ ì´ë¯¸ ìì¼ë©´ ë¤ì ë²í¸ê° ë¶ìµëë¤.");
        FALLBACK.put(R.string.i18n_0253, "êµ¬ì±í ì­ì ");
        FALLBACK.put(R.string.i18n_0254, "' êµ¬ì±íë¥¼ ì­ì í ê¹ì? ìì ëëë¦´ ì ììµëë¤.");
        FALLBACK.put(R.string.i18n_0255, "íì¼ìì ì´ê¸° (.json)");
        FALLBACK.put(R.string.i18n_0256, "Windows Terminalì ì êµ¬ì±í JSONìëë¤. 16ìê³¼ ì ê²½Â·ë°°ê²½Â·ì»¤ìë¥¼ ì½ê³  ");
        FALLBACK.put(R.string.i18n_0257, "ì ì¬ì©ì ì§ì  êµ¬ì±íë¡ ì¶ê°í´ ë°ë¡ ì ì©í©ëë¤. selectionBackgroundë ì´ ì±ì ");
        FALLBACK.put(R.string.i18n_0258, "ê·¸ ìë¦¬ê° ìì´ ë¬´ìí©ëë¤.");
        FALLBACK.put(R.string.i18n_0259, "ì êµ¬ì±í ê°ì ¸ì¤ê¸°");
        FALLBACK.put(R.string.i18n_0260, "ê°ì ¸ì¤ê¸°");
        FALLBACK.put(R.string.i18n_0261, "íì¼ì ì´ ì ììµëë¤");
        FALLBACK.put(R.string.i18n_0262, "íì¼ì ì½ì§ ëª»íìµëë¤: ");
        FALLBACK.put(R.string.i18n_0263, "ê°ì ¸ì¤ì§ ëª»íìµëë¤: ");
        FALLBACK.put(R.string.i18n_0264, "ì¬ì©ì ì§ì  êµ¬ì±íê° ");
        FALLBACK.put(R.string.i18n_0265, "ê°ìëë¤. íëë¥¼ ì­ì í ë¤ ë¤ì ê°ì ¸ì¤ì¸ì.");
        FALLBACK.put(R.string.i18n_0266, "' êµ¬ì±íë¥¼ ì¶ê°íìµëë¤");
        FALLBACK.put(R.string.i18n_0267, "êµ¬ì±í ");
        FALLBACK.put(R.string.i18n_0268, "ê°ë¥¼ ì¶ê°íìµëë¤");
        FALLBACK.put(R.string.i18n_0269, "ê°ë ìë¦¬ê° ìì´ ê±´ëë)");
        FALLBACK.put(R.string.i18n_0270, "ì");
        FALLBACK.put(R.string.i18n_0271, "ê²ì ");
        FALLBACK.put(R.string.i18n_0272, "ë¹¨ê°");
        FALLBACK.put(R.string.i18n_0273, "ë¹ì");
        FALLBACK.put(R.string.i18n_0274, "ë¸ë");
        FALLBACK.put(R.string.i18n_0275, "íë");
        FALLBACK.put(R.string.i18n_0276, "ë³´ë¼ì");
        FALLBACK.put(R.string.i18n_0277, "ë¹ì²­ì");
        FALLBACK.put(R.string.i18n_0278, "í°ì");
        FALLBACK.put(R.string.i18n_0279, "êµ¬ì±í");
        FALLBACK.put(R.string.i18n_0280, "ìì ëë¥´ë©´ ê·¸ ìë¦¬ì ê°ì ë°ê¿ ì ììµëë¤. ì¼ìª½ì´ ë³´íµì, ");
        FALLBACK.put(R.string.i18n_0281, "ì¤ë¥¸ìª½ì´ ë°ìììëë¤. êµ¬ì±í ì ì°íë¡ ì´ë¦ì ë°ê¾¸ê±°ë ì­ì í©ëë¤.");
        FALLBACK.put(R.string.i18n_0282, "ë´ì¥ êµ¬ì±íì ìì ì±ì ë¤ì´ ììµëë¤. ìëìì ì¬ì©ì ì§ì  êµ¬ì±íë¥¼ ");
        FALLBACK.put(R.string.i18n_0283, "ê³ ë¥´ê±°ë ìë¡ ë§ë¤ë©´ 16ìì ì§ì  ì í  ì ììµëë¤.");
        FALLBACK.put(R.string.i18n_0284, "ì¬ì©ì ì§ì   ");
        FALLBACK.put(R.string.i18n_0285, "íì¬ êµ¬ì±í ë³µì ");
        FALLBACK.put(R.string.i18n_0286, "ì§ê¸ ë³´ì´ë ìì¼ë¡ ì êµ¬ì±íë¥¼ ë§ë­ëë¤");
        FALLBACK.put(R.string.i18n_0287, "ë³µì ");
        FALLBACK.put(R.string.i18n_0288, " ë³µì ");
        FALLBACK.put(R.string.i18n_0289, "ì êµ¬ì±í");
        FALLBACK.put(R.string.i18n_0290, "Campbell ììì ììí©ëë¤");
        FALLBACK.put(R.string.i18n_0291, "ë§ë¤ê¸°");
        FALLBACK.put(R.string.i18n_0292, "16ìê³¼ ì ê²½Â·ë°°ê²½Â·ì»¤ìë¥¼ ì½ì´ ì êµ¬ì±íë¡ ì¶ê°íê³  ë°ë¡ ì ì©");
        FALLBACK.put(R.string.i18n_0293, "ê°ìëë¤. êµ¬ì±í ì ì°íìì íëë¥¼ ì­ì í ë¤ ë¤ì ì¶ê°íì¸ì.");
        FALLBACK.put(R.string.i18n_0294, "ë³´íµì ");
        FALLBACK.put(R.string.i18n_0295, "ë°ìì ");
        FALLBACK.put(R.string.i18n_0296, "ë²");
        FALLBACK.put(R.string.i18n_0297, "ì ê²½");
        FALLBACK.put(R.string.i18n_0298, "ë°°ê²½");
        FALLBACK.put(R.string.i18n_0299, "ì»¤ì");
        FALLBACK.put(R.string.i18n_0300, "ì ì§ì ");
        FALLBACK.put(R.string.i18n_0301, "ì ê²½ì¼ë¡");
        FALLBACK.put(R.string.i18n_0302, "ë°ì ");
        FALLBACK.put(R.string.i18n_0303, "ì¸ì: 3 Â· ìë² 127.0.0.1:8791");
        FALLBACK.put(R.string.i18n_0304, "íë¡í ê´ë¦¬");
        FALLBACK.put(R.string.i18n_0305, "ì êµ¬ì±í âº Campbell");
        FALLBACK.put(R.string.i18n_0306, "ì¤ì ");
        FALLBACK.put(R.string.i18n_0307, "ì¤ì   âº  ");
        FALLBACK.put(R.string.i18n_0308, "ê¸°ë³¸ íë¡í");
        FALLBACK.put(R.string.i18n_0309, "ì í­ì ì´ ë ì°ë íë¡í");
        FALLBACK.put(R.string.i18n_0310, "ìì ëë í°ë¦¬");
        FALLBACK.put(R.string.i18n_0311, "ì ì¸ìì´ ììíë ìì¹");
        FALLBACK.put(R.string.i18n_0312, "í");
        FALLBACK.put(R.string.i18n_0313, "ìì ì¸");
        FALLBACK.put(R.string.i18n_0314, "ì ì¸ìì´ ì¤ííë íë¡ê·¸ë¨");
        FALLBACK.put(R.string.i18n_0315, "ê¸°ë³¸ ì¸");
        FALLBACK.put(R.string.i18n_0316, "ì±ì ì´ ë í¤ë³´ë");
        FALLBACK.put(R.string.i18n_0317, "ììíìë§ì ìë ¥í  ì ììµëë¤");
        FALLBACK.put(R.string.i18n_0318, "ëë í°ë¦¬ì ì¸ì ë¤ìì ì¬ë í­ë¶í° ì ì©ë©ëë¤. ");
        FALLBACK.put(R.string.i18n_0319, "ì´ë¯¸ ì´ë¦° í­ì ê·¸ëë¡ìëë¤. ì¸ì ë°ëª¬ì´ íì©í ê²½ë¡ë§ ì¤íë©ëë¤.");
        FALLBACK.put(R.string.i18n_0320, "ë¹ì°ë©´ ë°ëª¬ì í ëë í°ë¦¬ìì ììí©ëë¤. ì¡´ì¬íì§ ìë ê²½ë¡ë¥¼ ì ì¼ë©´ ");
        FALLBACK.put(R.string.i18n_0321, "íì¼ë¡ ëì²´ë©ëë¤.");
        FALLBACK.put(R.string.i18n_0322, "ë°ëª¬ì´ íì©í ê²½ë¡ë§ ì¤íë©ëë¤. ë¹ì°ë©´ ë°ëª¬ ê¸°ë³¸ ì¸ì ìëë¤.");
        FALLBACK.put(R.string.i18n_0323, "ê¸°ë³¸ê°");
        FALLBACK.put(R.string.i18n_0324, "í°ë¯¸ë ì°½");
        FALLBACK.put(R.string.i18n_0325, "íë¡¬íí¸");
        FALLBACK.put(R.string.i18n_0326, "ì½ë");
        FALLBACK.put(R.string.i18n_0327, "íì´í");
        FALLBACK.put(R.string.i18n_0328, "ë¶í ");
        FALLBACK.put(R.string.i18n_0329, "í´ë¼ì°ë");
        FALLBACK.put(R.string.i18n_0330, "ì§êµ¬");
        FALLBACK.put(R.string.i18n_0331, "í´ë");
        FALLBACK.put(R.string.i18n_0332, "ë¬¸ì");
        FALLBACK.put(R.string.i18n_0333, "í¤ë³´ë");
        FALLBACK.put(R.string.i18n_0334, "ë²ê·¸");
        FALLBACK.put(R.string.i18n_0335, "ë¹ì»¤");
        FALLBACK.put(R.string.i18n_0336, "ë ì¹");
        FALLBACK.put(R.string.i18n_0337, "ë²ê°");
        FALLBACK.put(R.string.i18n_0338, "ë¡ì¼");
        FALLBACK.put(R.string.i18n_0339, "ë°©í¨");
        FALLBACK.put(R.string.i18n_0340, "ë");
        FALLBACK.put(R.string.i18n_0341, "ì¤ìº");
        FALLBACK.put(R.string.i18n_0342, "ë³");
        FALLBACK.put(R.string.i18n_0343, "íí¸");
        FALLBACK.put(R.string.i18n_0344, "íë¸");
        FALLBACK.put(R.string.i18n_0345, "í¼ì¦");
        FALLBACK.put(R.string.i18n_0346, "ë¶ê½");
        FALLBACK.put(R.string.i18n_0347, "ì¹´ë©ë¼");
        FALLBACK.put(R.string.i18n_0348, "ì ë¬¼");
        FALLBACK.put(R.string.i18n_0349, "ë¶");
        FALLBACK.put(R.string.i18n_0350, "ìì ë±");
        FALLBACK.put(R.string.i18n_0351, "ì§");
        FALLBACK.put(R.string.i18n_0352, "ë§íì ");
        FALLBACK.put(R.string.i18n_0353, "ë©ì¼");
        FALLBACK.put(R.string.i18n_0354, "ì í");
        FALLBACK.put(R.string.i18n_0355, "íë¦°í°");
        FALLBACK.put(R.string.i18n_0356, "ì");
        FALLBACK.put(R.string.i18n_0357, "ê¸ìê° ìë¡ ë¶ìë¤");
        FALLBACK.put(R.string.i18n_0358, "ê¸ìê° ë¬»íë©´ ì´ë¡ê²");
        FALLBACK.put(R.string.i18n_0359, "ì ì²´ ì í");
        FALLBACK.put(R.string.i18n_0360, "JSONì´ ë¹ì´ ììµëë¤");
        FALLBACK.put(R.string.i18n_0361, "JSONì ì½ì§ ëª»íìµëë¤: ");
        FALLBACK.put(R.string.i18n_0362, "êµ¬ì±íë¥¼ ì°¾ì§ ëª»íìµëë¤ â \"schemes\" ëª©ë¡ì´ë ì í­ëª©ì´ ìë ê°ì²´ê° íìí©ëë¤");
        FALLBACK.put(R.string.i18n_0363, "êµ¬ì±íë { â¦ } íëì´ê±°ë ê·¸ ë°°ì´ì´ì´ì¼ í©ëë¤");
        FALLBACK.put(R.string.i18n_0364, "ê°ì ¸ì¬ êµ¬ì±íê° ììµëë¤");
        FALLBACK.put(R.string.i18n_0365, "êµ¬ì±íë { â¦ } íëì¬ì¼ í©ëë¤");
        FALLBACK.put(R.string.i18n_0366, "ì í­ëª©ì´ ììµëë¤ (black, foreground â¦)");
        FALLBACK.put(R.string.i18n_0367, "ê°ì ¸ì¨ êµ¬ì±í");
        FALLBACK.put(R.string.i18n_0368, " ê°ì´ ë¬¸ìì´ì´ ìëëë¤");
        FALLBACK.put(R.string.i18n_0369, " ì íìì´ ìëëë¤: ");
        FALLBACK.put(R.string.i18n_0370, "ê° ë¤ì ë¨ì ê¸ì");
        FALLBACK.put(R.string.i18n_0371, "ê°ì´ íìí©ëë¤");
        FALLBACK.put(R.string.i18n_0372, "í¤ë ë¬¸ìì´ì´ì´ì¼ í©ëë¤");
        FALLBACK.put(R.string.i18n_0373, ": ê° ììµëë¤");
        FALLBACK.put(R.string.i18n_0374, ", ëë } ê° íìí©ëë¤");
        FALLBACK.put(R.string.i18n_0375, "] ê° ììµëë¤");
        FALLBACK.put(R.string.i18n_0376, ", ëë ] ê° íìí©ëë¤");
        FALLBACK.put(R.string.i18n_0377, "í°ë¯¸ë ì¤ë¹ ì¤");
    }

    public static void init(Context c) { ctx = c.getApplicationContext(); }
    public static String s(int id) {
        if (ctx != null) return ctx.getString(id);
        String v = FALLBACK.get(id);
        return v != null ? v : "";
    }
}
