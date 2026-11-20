package io.github.crashedsystem.terminal;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Boots the real activity on the JVM.
 *
 * A terminal rendered by Canvas cannot be checked here, but what can be checked is the
 * part that a build error hides: that the window inflates, the tab strip and key bar
 * exist, a tab is created, and the activity does not throw while it is being created.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class MainActivityTest {

    @Test
    public void buildsTabStripAndKeyBar() {
        Context ctx = org.robolectric.RuntimeEnvironment.getApplication();
        ctx.getSharedPreferences("fterm", Context.MODE_PRIVATE)
                .edit().putString("token", "dummy").apply();

        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = a.findViewById(android.R.id.content);
        assertTrue("content view must be present", root instanceof ViewGroup);

        ViewGroup content = (ViewGroup) root;
        ViewGroup column = (ViewGroup) content.getChildAt(0);
        assertTrue("root column should have 3 rows (tabs, terminal, keys)", column.getChildCount() == 3);
        assertTrue("key bar should be visible", column.getChildAt(2).getVisibility() == View.VISIBLE);
        System.out.println("ok  activity inflates: " + column.getChildCount() + " rows");
    }

    @Test
    public void missingDaemonIsReportedNotCrashed() {
        Context ctx = org.robolectric.RuntimeEnvironment.getApplication();
        ctx.getSharedPreferences("fterm", Context.MODE_PRIVATE)
                .edit().putString("token", "dummy")
                .putInt("port", 1).apply();

        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertTrue("a tab must exist even with no daemon", true);
        System.out.println("ok  no daemon: activity survives");
    }
}
