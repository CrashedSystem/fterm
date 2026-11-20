package io.github.crashedsystem.fterm;

import android.content.Context;
import java.util.Map;
import java.util.HashMap;

public final class AppStrings {
    private static Context ctx = null;
    private static final Map<Integer, String> FALLBACK = new HashMap<>();
    static {
        FALLBACK.put(R.string.app_name, "터미널");
        FALLBACK.put(R.string.copy_text, "복사");
        FALLBACK.put(R.string.paste_text, "붙여넣기");
        FALLBACK.put(R.string.text_selection_more, "더 보기");
        FALLBACK.put(R.string.notify_channel_session, "터미널 세션");
        FALLBACK.put(R.string.notify_channel_shell, "셸 알림");
    }

    public static void init(Context c) { ctx = c.getApplicationContext(); }
    public static String s(int id) {
        if (ctx != null) return ctx.getString(id);
        String v = FALLBACK.get(id);
        return v != null ? v : "";
    }
}
