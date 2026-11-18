package com.github.ad.reeder;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

final class App {
    static final int STARRED = 0, UNREAD = 1, ALL = 2;

    static Db db;
    static SharedPreferences prefs;
    static Sync sync;
    static boolean inBrowser;

    static void init(Context c) {
        if (db != null) return;
        Context app = c.getApplicationContext();
        db = new Db(app);
        prefs = app.getSharedPreferences("reeder", Context.MODE_PRIVATE);
        sync = new Sync();
        ((Application) app).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            int started;
            boolean changing;

            public void onActivityStarted(Activity a) {
                // a rotation stops and restarts the activity; it is not a return to the app
                if (started++ == 0 && !changing && !inBrowser && !"manual".equals(prefs.getString("sync_mode", "start"))) sync.start();
                changing = false;
                inBrowser = false;
            }

            public void onActivityStopped(Activity a) {
                started--;
                changing = a.isChangingConfigurations();
            }

            public void onActivityCreated(Activity a, Bundle b) {}
            public void onActivityResumed(Activity a) {}
            public void onActivityPaused(Activity a) {}
            public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            public void onActivityDestroyed(Activity a) {}
        });
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
