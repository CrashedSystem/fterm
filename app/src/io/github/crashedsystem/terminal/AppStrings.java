package io.github.crashedsystem.terminal;

import android.content.Context;
import java.util.Map;
import java.util.HashMap;

public final class AppStrings {
    private static Context ctx = null;
    private static final Map<Integer, String> FALLBACK = new HashMap<>();

    public static void init(Context c) { ctx = c.getApplicationContext(); }

    public static String s(int id) {
        if (ctx != null) return ctx.getString(id);
        String v = FALLBACK.get(id);
        return v != null ? v : "";
    }
}
