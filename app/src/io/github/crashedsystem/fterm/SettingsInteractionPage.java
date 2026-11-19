package io.github.crashedsystem.fterm;

import android.widget.LinearLayout;

/**
 * 상호 작용: the two bell toggles and the back-key choice.
 *
 * <p>Three rows, and the notes below them list the gestures that have no setting because they
 * are not meant to be configurable - a long press selects text, a long press on a tab closes
 * it, and the key bar's edge tabs reach F1..F12.
 */
final class SettingsInteractionPage extends SettingsPage {

    SettingsInteractionPage(SettingsSheet sheet) {
        super(sheet);
    }

    @Override
    void build(LinearLayout col) {
        LinearLayout card = ui.card(col);
        card.addView(ui.toggleCardRow("터미널 벨 · 진동", "BEL 문자를 받으면 짧게 진동합니다",
                host.bellVibrate(), on -> host.onBell(on, host.bellSound()), false));
        card.addView(ui.toggleCardRow("터미널 벨 · 소리", "무음 모드가 아닐 때만 울립니다",
                host.bellSound(), on -> host.onBell(host.bellVibrate(), on), false));
        card.addView(ui.toggleCardRow("뒤로 가기 = Esc", "vim·less에서 뒤로 가기가 취소로 동작",
                host.backIsEscape(), host::onBackIsEscape, true));

        ui.hint(col, "길게 누르기: 텍스트 선택 · 탭 길게 누르기: 탭 닫기 · 키 바 가장자리: F1~F12");
    }
}
