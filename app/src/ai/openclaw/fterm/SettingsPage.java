package ai.openclaw.fterm;

import android.app.Activity;
import android.widget.LinearLayout;

/**
 * One page of the settings surface.
 *
 * <p>A page owns its rows and nothing else. It reads through {@link SettingsSheet.Host}, draws
 * with {@link SettingsUi}, and asks the sheet to re-render or to push another page. Which page
 * is showing is the sheet's state, not the page's, so a page is built fresh on every render
 * and every edit ends in {@link #render()} rather than in a field.
 *
 * <p>The split into one file per page is what keeps the sheet a router: adding a page means
 * adding a file and one line in the sheet's dispatch, not growing the file every other page
 * also lives in.
 */
abstract class SettingsPage {

    final SettingsSheet sheet;
    final SettingsSheet.Host host;
    final SettingsUi ui;
    final Activity activity;

    SettingsPage(SettingsSheet sheet) {
        this.sheet = sheet;
        this.host = sheet.host();
        this.ui = sheet.ui();
        this.activity = sheet.activity();
    }

    /** Appends this page's cards and notes to the sheet's scrolling column. */
    abstract void build(LinearLayout col);

    /** Redraw the sheet, which is the only way a change becomes visible. */
    final void render() {
        sheet.render();
    }

    /** Push another page over this one, as WT's breadcrumb does. */
    final void open(String page) {
        sheet.open(page);
    }
}
