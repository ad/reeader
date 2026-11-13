package com.github.ad.reeder;

import android.content.Context;
import android.content.SharedPreferences;

final class App {
    static final int STARRED = 0, UNREAD = 1, ALL = 2;

    static Db db;
    static SharedPreferences prefs;
    static Sync sync;

    static void init(Context c) {
        if (db != null) return;
        Context app = c.getApplicationContext();
        db = new Db(app);
        prefs = app.getSharedPreferences("reeder", Context.MODE_PRIVATE);
        sync = new Sync();
    }

    static boolean loggedIn() {
        return prefs.getString("auth", null) != null;
    }

    static int filter() {
        return prefs.getInt("filter", UNREAD);
    }

    static void setFilter(int f) {
        prefs.edit().putInt("filter", f).apply();
    }

    static boolean readerFor(String feedId) {
        return prefs.getBoolean("reader:" + feedId, prefs.getBoolean("reader_default", false));
    }
}
