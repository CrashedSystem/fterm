package ai.openclaw.fterm;

import android.widget.LinearLayout;

/**
 * WT's 시작 page: rows that mostly name a default and show its value on the right.
 *
 * <p>The two rows that hold a value open a one-line prompt instead of a picker, because the
 * value is a path and the path is already the thing the user thinks in. See
 * {@link SettingsUi#prompt}.
 */
final class SettingsStartPage extends SettingsPage {

    SettingsStartPage(SettingsSheet sheet) {
        super(sheet);
    }

    @Override
    void build(LinearLayout col) {
        LinearLayout card = ui.card(col);

        card.addView(ui.valueRow("기본 프로필", "새 탭을 열 때 쓰는 프로필",
                Profiles.DEFAULT_NAME, null, false));
        card.addView(ui.valueRow("시작 디렉터리", "새 세션이 시작하는 위치",
                host.startDir().isEmpty() ? "홈" : host.startDir(),
                this::editStartDir, false));
        card.addView(ui.valueRow("시작 셸", "새 세션이 실행하는 프로그램",
                host.startShell().isEmpty() ? "기본 셸" : host.startShell(),
                this::editStartShell, false));
        card.addView(ui.toggleCardRow("앱을 열 때 키보드", "시작하자마자 입력할 수 있습니다",
                host.keyboardOnStart(), host::onKeyboardOnStart, true));

        ui.hint(col, "디렉터리와 셸은 다음에 여는 탭부터 적용됩니다. "
                + "이미 열린 탭은 그대로입니다. 셸은 데몬이 허용한 경로만 실행됩니다.");
    }

    private void editStartDir() {
        ui.prompt("시작 디렉터리", host.startDir(), "/data/data/com.termux/files/home",
                "비우면 데몬의 홈 디렉터리에서 시작합니다. 존재하지 않는 경로를 적으면 "
                        + "홈으로 대체됩니다.",
                v -> {
                    host.onStartDir(v);
                    render();
                });
    }

    private void editStartShell() {
        ui.prompt("시작 셸", host.startShell(), "/data/data/com.termux/files/usr/bin/bash",
                "데몬이 허용한 경로만 실행됩니다. 비우면 데몬 기본 셸을 씁니다.",
                v -> {
                    host.onStartShell(v);
                    render();
                });
    }
}
