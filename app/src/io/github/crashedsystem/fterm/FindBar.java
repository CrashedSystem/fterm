package io.github.crashedsystem.fterm;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.view.TerminalView;

import java.util.ArrayList;
import java.util.List;

/**
 * WT's find bar: one field, a match count, and next/previous.
 *
 * <p>WT puts this in a side pane with options for case and regex. A phone has no room for a
 * pane, so it is a bar above the terminal - the terminal stays visible while searching, which
 * is the only reason to search at all.
 *
 * <p>The search runs over the emulator's own rows rather than over a copy of the output: the
 * scrollback is already there, it is already the thing on screen, and a copy would drift the
 * moment a build printed another line.
 */
final class FindBar {

    private final Activity mActivity;
    private final TerminalView mView;
    private final LinearLayout mBar;
    private final EditText mField;
    private final TextView mCount;

    private List<Search.Hit> mHits = new ArrayList<>();
    private int mIndex = -1;
    private boolean mCaseSensitive;

    FindBar(Activity activity, TerminalView view) {
        mActivity = activity;
        mView = view;

        mBar = new LinearLayout(activity);
        mBar.setOrientation(LinearLayout.HORIZONTAL);
        mBar.setGravity(Gravity.CENTER_VERTICAL);
        mBar.setBackgroundColor(0xff26292f);
        mBar.setPadding(dp(10), 0, dp(6), 0);
        mBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        mBar.setVisibility(View.GONE);

        mField = new EditText(activity);
        mField.setSingleLine(true);
        mField.setTextSize(13);
        mField.setTextColor(0xffe6e6e6);
        mField.setHintTextColor(0xff8a8a8a);
        mField.setHint("찾기");
        mField.setBackground(null);
        mField.setPadding(0, 0, 0, 0);
        mField.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        mField.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        mField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { research(); }
        });
        mField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { step(1); return true; }
            return false;
        });
        mBar.addView(mField, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        mCount = new TextView(activity);
        mCount.setTextSize(11);
        mCount.setTextColor(0xff9a9a9a);
        mCount.setGravity(Gravity.CENTER);
        mCount.setSingleLine(true);
        mCount.setPadding(dp(6), 0, dp(6), 0);
        mBar.addView(mCount);

        // Aa toggles case, the way every other find bar does it.
        TextView aa = label("Aa");
        aa.setOnClickListener(v -> {
            mCaseSensitive = !mCaseSensitive;
            aa.setTextColor(mCaseSensitive ? 0xffffffff : 0xff8a8a8a);
            research();
        });
        aa.setTextColor(0xff8a8a8a);
        mBar.addView(aa);

        TextView prev = label("\u2039");
        prev.setOnClickListener(v -> step(-1));
        mBar.addView(prev);

        TextView next = label("\u203a");
        next.setOnClickListener(v -> step(1));
        mBar.addView(next);

        TextView close = label("\u00d7");
        close.setOnClickListener(v -> hide());
        mBar.addView(close);
    }

    private TextView label(String text) {
        TextView t = new TextView(mActivity);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(0xffcfcfcf);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setPadding(dp(12), 0, dp(12), 0);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        t.setClickable(true);
        return t;
    }

    View view() { return mBar; }

    boolean isOpen() { return mBar.getVisibility() == View.VISIBLE; }

    /** Show the bar and put the cursor in the field, keeping whatever was typed last time. */
    void show() {
        mBar.setVisibility(View.VISIBLE);
        mField.requestFocus();
        mField.setSelection(mField.getText().length());
        android.view.inputmethod.InputMethodManager ime =
                (android.view.inputmethod.InputMethodManager)
                        mActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (ime != null) ime.showSoftInput(mField, 0);
        research();
    }

    void hide() {
        mBar.setVisibility(View.GONE);
        mHits.clear();
        mIndex = -1;
        mView.clearFindHighlights();
        mView.requestFocus();
    }

    /** Re-run the query and jump to the first match at or after the current scroll position. */
    private void research() {
        String query = mField.getText().toString();
        mHits.clear();
        mIndex = -1;
        if (query.isEmpty()) {
            mCount.setText("");
            mView.clearFindHighlights();
            return;
        }

        String[] lines = lines();
        if (lines == null) return;
        mHits = Search.find(lines, mFirstRow, query, mCaseSensitive);
        if (mHits.isEmpty()) {
            mCount.setText("없음");
            mView.clearFindHighlights();
            return;
        }
        mIndex = 0;
        showCurrent();
    }

    private void step(int delta) {
        if (mHits.isEmpty()) return;
        mIndex = (mIndex + delta + mHits.size()) % mHits.size();
        showCurrent();
    }

    private void showCurrent() {
        Search.Hit hit = mHits.get(mIndex);
        mCount.setText((mIndex + 1) + "/" + mHits.size());

        TerminalEmulator em = mView.mEmulator;
        if (em != null) {
            // Centre the row instead of scrolling it to the top: a match you cannot see the
            // context of is barely better than no match.
            int top = hit.row - em.mRows / 2;
            int minTop = -em.getScreen().getActiveTranscriptRows();
            mView.setTopRow(Math.max(minTop, Math.min(0, top)));
        }
        int[][] rects = new int[mHits.size()][];
        for (int i = 0; i < mHits.size(); i++) {
            Search.Hit h = mHits.get(i);
            rects[i] = new int[]{h.row, h.startCol, h.endCol};
        }
        mView.setFindHighlights(rects, mIndex);
    }

    /** The text of every row from the top of the scrollback to the bottom of the screen. */
    private int mFirstRow;

    private String[] lines() {
        TerminalEmulator em = mView.mEmulator;
        if (em == null) return null;
        TerminalBuffer screen = em.getScreen();
        mFirstRow = -screen.getActiveTranscriptRows();
        int last = em.mRows - 1;
        String[] out = new String[last - mFirstRow + 1];
        for (int row = mFirstRow; row <= last; row++) {
            out[row - mFirstRow] = screen.getSelectedText(0, row, em.mColumns, row, false);
        }
        return out;
    }

    private int dp(float v) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, v,
                mActivity.getResources().getDisplayMetrics());
    }
}
