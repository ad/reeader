package com.github.ad.reeder;

import android.content.ContentValues;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Sync {
    interface Listener {
        void onSyncState(boolean running);
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean running;
    private volatile String error;

    String error() {
        return error;
    }

    boolean running() {
        return running;
    }

    void addListener(Listener l) {
        listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    void setFlag(Collection<Long> ids, String tag, boolean on) {
        App.db.setFlag(ids, tag, on);
        worker.execute(() -> {
            try {
                flushOps(backend());
            } catch (Exception ignored) {
                // retried on next sync
            }
        });
    }

    void start() {
        if (running || !App.loggedIn()) return;
        running = true;
        error = null;
        notifyListeners(true);
        worker.execute(() -> {
            String error = null;
            try {
                run();
                App.prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply();
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            this.error = error;
            running = false;
            notifyListeners(false);
        });
    }

    private void notifyListeners(boolean run) {
        main.post(() -> {
            for (Listener l : listeners) l.onSyncState(run);
        });
    }

    private Backend backend() {
        return Service.of(App.prefs.getString("type", null)).backend(App.prefs.getString("base", ""), App.prefs.getString("auth", ""));
    }

    private void run() throws IOException, JSONException {
        String auth = App.prefs.getString("auth", "");
        Backend b = backend();
        flushOps(b);
        List<Db.Feed> feeds = b.subscriptions();
        alive(auth);
        App.db.replaceFeeds(feeds);
        List<Long> unread = b.unreadIds();
        List<Long> starred = b.starredIds();
        java.util.Set<Long> all = new java.util.LinkedHashSet<>(unread);
        all.addAll(starred);
        List<Long> missing = App.db.missing(all);
        for (int i = 0; i < missing.size(); i += 100) {
            List<ContentValues> items = b.items(missing.subList(i, Math.min(missing.size(), i + 100)));
            alive(auth);
            App.db.insertItems(items);
        }
        alive(auth);
        App.db.applyServerState(unread, starred);
        int keepDays = App.prefs.getInt("keep_days", 30);
        if (keepDays > 0) App.db.prune(System.currentTimeMillis() - keepDays * 86400_000L);
    }

    private static void alive(String auth) throws IOException {
        if (!auth.equals(App.prefs.getString("auth", null))) throw new IOException("Logged out");
    }

    private void flushOps(Backend b) throws IOException {
        List<Db.Op> ops = App.db.ops();
        Map<String, List<Db.Op>> groups = new HashMap<>();
        for (Db.Op o : ops) {
            String key = (o.add ? "a" : "r") + o.tag;
            List<Db.Op> g = groups.get(key);
            if (g == null) groups.put(key, g = new ArrayList<>());
            g.add(o);
        }
        for (List<Db.Op> g : groups.values()) {
            for (int i = 0; i < g.size(); i += 200) {
                List<Db.Op> part = g.subList(i, Math.min(g.size(), i + 200));
                List<Long> ids = new ArrayList<>();
                for (Db.Op o : part) ids.add(o.itemId);
                b.mark(ids, part.get(0).tag, part.get(0).add);
                App.db.deleteOps(part);
            }
        }
    }
}
