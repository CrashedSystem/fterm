package io.github.crashedsystem.fterm;

import android.widget.LinearLayout;

/**
 * 정보: what is connected, and what this app is.
 *
 * <p>The session row is the host's own sentence, because the count and the address are the
 * activity's truth and not something a page should recompute.
 */
final class SettingsAboutPage extends SettingsPage {

    SettingsAboutPage(SettingsSheet sheet) {
        super(sheet);
    }

    @Override
    void build(LinearLayout col) {
        LinearLayout card = ui.card(col);
        card.addView(ui.valueRow("세션", host.statusLine(), null, null, false));
        card.addView(ui.valueRow("데몬", "127.0.0.1 의 PTY 서버, 토큰 인증", null, null, false));
        card.addView(ui.valueRow("렌더링", "xterm-256color · 한글 2칸 폭", null, null, true));

        ui.hint(col, "fterm — Android 위의 Windows Terminal 모양을 목표로 하는 터미널. "
                + "셸은 Termux의 데몬이 소유하고, 앱은 화면과 입력만 맡습니다.");
    }
}
