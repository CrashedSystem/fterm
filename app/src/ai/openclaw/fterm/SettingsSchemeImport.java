package ai.openclaw.fterm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

/**
 * Imports a Windows Terminal colour scheme.
 *
 * <p>Two ways in, because both are how people actually have one: paste the block they copied
 * from a gist or a {@code settings.json}, or open the {@code .json} file they downloaded.
 *
 * <p>An import is added as a new custom scheme under the name the JSON carries, and selected.
 * It used to overwrite the single 사용자 지정 slot, which was the honest behaviour when there
 * was one palette; with sixteen there is nothing to overwrite, and the scheme the user was
 * using stays where it was. A name already in the list gets a number appended, so importing
 * the same gist twice gives two distinguishable rows rather than one that looks unchanged.
 */
final class SettingsSchemeImport {

    private static final int REQ_OPEN = 0x4a53;
    private static final int MAX_BYTES = 1024 * 1024;

    private final SettingsSheet mSheet;
    private final SettingsSheet.Host mHost;
    private final Activity mActivity;
    private final SettingsUi mUi;

    SettingsSchemeImport(SettingsSheet sheet) {
        mSheet = sheet;
        mHost = sheet.host();
        mActivity = sheet.activity();
        mUi = sheet.ui();
    }

    void open() {
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(mUi.dp(20), mUi.dp(8), mUi.dp(20), 0);

        final EditText field = new EditText(mActivity);
        field.setHint("{\n  \"name\": \"Campbell\",\n  \"background\": \"#0C0C0C\",\n"
                + "  \"foreground\": \"#CCCCCC\",\n  \"black\": \"#0C0C0C\" …\n}");
        field.setHintTextColor(mUi.HINT);
        field.setTextColor(mUi.TEXT);
        field.setTextSize(12);
        field.setTypeface(Typeface.MONOSPACE);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setMinLines(5);
        field.setMaxLines(9);
        box.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView fromFile = new TextView(mActivity);
        fromFile.setText("파일에서 열기 (.json)");
        fromFile.setTextColor(mUi.ACCENT);
        fromFile.setTextSize(12.5f);
        fromFile.setPadding(0, mUi.dp(12), 0, mUi.dp(12));
        fromFile.setBackground(mUi.touchable());
        fromFile.setClickable(true);
        fromFile.setOnClickListener(v -> pickFile());
        box.addView(fromFile);

        TextView note = new TextView(mActivity);
        note.setText("Windows Terminal의 색 구성표 JSON입니다. 16색과 전경·배경·커서를 읽고 "
                + "새 사용자 지정 구성표로 추가해 바로 적용합니다. selectionBackground는 이 앱에 "
                + "그 자리가 없어 무시합니다.");
        note.setTextColor(mUi.HINT);
        note.setTextSize(10.5f);
        note.setPadding(0, mUi.dp(6), 0, 0);
        box.addView(note);

        final AlertDialog dialog = new AlertDialog.Builder(mActivity)
                .setTitle("색 구성표 가져오기")
                .setView(box)
                .setPositiveButton("가져오기", null)
                .setNegativeButton("취소", null)
                .create();
        dialog.show();
        // The listener is set after show() so a bad paste keeps the dialog open with the text
        // still there, which is the only way the user can see what was wrong and fix it.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (apply(field.getText().toString())) dialog.dismiss();
        });
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        // A scheme is usually saved as .json, but some file providers label it
        // application/octet-stream; showing everything and parsing after the pick is more
        // forgiving than a filter that hides the file the user just saved.
        i.setType("*/*");
        try {
            mActivity.startActivityForResult(i, REQ_OPEN);
        } catch (Throwable t) {
            Toast.makeText(mActivity, "파일 선택기를 열 수 없습니다: " + t.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_OPEN) return;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String text;
        try (InputStream in = mActivity.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("파일을 열 수 없습니다");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new IllegalStateException("파일이 너무 큽니다");
            }
            text = out.toString("UTF-8");
        } catch (Throwable t) {
            Toast.makeText(mActivity, "파일을 읽지 못했습니다: " + t.getMessage(),
                    Toast.LENGTH_LONG).show();
            return;
        }
        apply(text);
    }

    /**
     * @return true when the scheme was added and selected, so a pasted dialog can close on
     *         success only.
     */
    private boolean apply(String text) {
        try {
            TerminalSchemeJson.Scheme scheme = TerminalSchemeJson.parse(text);
            int added = mHost.addCustomScheme(scheme.name, scheme.slots);
            if (added < 0) {
                Toast.makeText(mActivity, "사용자 지정 구성표가 " + Schemes.MAX_CUSTOM_SCHEMES
                        + "개입니다. 하나를 삭제한 뒤 다시 가져오세요.",
                        Toast.LENGTH_LONG).show();
                return false;
            }
            Toast.makeText(mActivity, "'" + mHost.customSchemeName(added) + "' 구성표를 추가했습니다",
                    Toast.LENGTH_SHORT).show();
            mSheet.render();
            return true;
        } catch (IllegalArgumentException e) {
            Toast.makeText(mActivity, "가져오지 못했습니다: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
