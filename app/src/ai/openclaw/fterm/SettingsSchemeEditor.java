package ai.openclaw.fterm;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * What the pencil beside a saved scheme opens: rename it or delete it.
 *
 * <p>A dialog rather than a second settings page, because a scheme has exactly two things to
 * change and neither is a value you want to hunt for. Deleting asks first and names the
 * scheme: it is the only action here that destroys something the user made, and the palette
 * cannot be recovered from anywhere.
 */
final class SettingsSchemeEditor {

    private final SettingsSheet mSheet;
    private final SettingsSheet.Host mHost;
    private final Activity mActivity;
    private final SettingsUi mUi;

    SettingsSchemeEditor(SettingsSheet sheet) {
        mSheet = sheet;
        mHost = sheet.host();
        mActivity = sheet.activity();
        mUi = sheet.ui();
    }

    /** The action list for custom scheme {@code index}. */
    void edit(final int index) {
        final String name = mHost.customSchemeName(index);
        if (name.isEmpty()) return;
        new AlertDialog.Builder(mActivity)
                .setTitle(name)
                .setItems(new String[]{"이름 변경", "삭제"}, (d, which) -> {
                    if (which == 0) rename(index, name);
                    else confirmDelete(index, name);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void rename(final int index, String name) {
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(mUi.dp(20), mUi.dp(4), mUi.dp(20), 0);

        final EditText field = new EditText(mActivity);
        field.setText(name);
        field.setSingleLine(true);
        field.setTextSize(14);
        field.setTypeface(Typeface.MONOSPACE);
        field.setTextColor(mUi.TEXT);
        field.setHintTextColor(mUi.HINT);
        field.setHint("구성표 이름");
        box.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(mActivity);
        note.setText("같은 이름이 이미 있으면 뒤에 번호가 붙습니다.");
        note.setTextColor(mUi.HINT);
        note.setTextSize(10.5f);
        note.setPadding(0, mUi.dp(6), 0, 0);
        box.addView(note);

        new AlertDialog.Builder(mActivity)
                .setTitle("구성표 이름")
                .setView(box)
                .setPositiveButton("저장", (d, w) -> {
                    mHost.onRenameCustomScheme(index, field.getText().toString().trim());
                    mSheet.render();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void confirmDelete(final int index, String name) {
        new AlertDialog.Builder(mActivity)
                .setTitle("구성표 삭제")
                .setMessage("'" + name + "' 구성표를 삭제할까요? 색은 되돌릴 수 없습니다.")
                .setPositiveButton("삭제", (d, w) -> {
                    mHost.onDeleteCustomScheme(index);
                    mSheet.render();
                })
                .setNegativeButton("취소", null)
                .show();
    }
}
