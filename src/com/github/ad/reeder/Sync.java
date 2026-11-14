package com.github.ad.reeder;

import android.content.ContentValues;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
                flushOps(api());
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

    private Api api() {
        return new Api(App.prefs.getString("base", ""), App.prefs.getString("auth", ""));
    }

    private void run() throws IOException, JSONException {
        Api api = api();
        flushOps(api);
        List<Db.Feed> feeds = subscriptions(api);
        alive(api);
        App.db.replaceFeeds(feeds);
        List<Long> unread = ids(api, "user/-/state/com.google/reading-list", Db.READ);
        List<Long> starred = ids(api, Db.STARRED, null);
        java.util.Set<Long> all = new java.util.LinkedHashSet<>(unread);
        all.addAll(starred);
        List<Long> missing = App.db.missing(all);
        for (int i = 0; i < missing.size(); i += 100) {
            String json = api.post("stream/items/contents?output=json", Api.idsForm(missing.subList(i, Math.min(missing.size(), i + 100))));
            alive(api);
            App.db.insertItems(parseItems(json));
        }
        alive(api);
        App.db.applyServerState(unread, starred);
        int keepDays = App.prefs.getInt("keep_days", 30);
        if (keepDays > 0) App.db.prune(System.currentTimeMillis() - keepDays * 86400_000L);
    }

    private static void alive(Api api) throws IOException {
        if (!api.auth.equals(App.prefs.getString("auth", null))) throw new IOException("Logged out");
    }

    private void flushOps(Api api) throws IOException {
        List<Db.Op> ops = App.db.ops();
        Map<String, List<Db.Op>> groups = new HashMap<>();
        for (Db.Op o : ops) {
            String key = (o.add ? "a=" : "r=") + Api.enc(o.tag);
            List<Db.Op> g = groups.get(key);
            if (g == null) groups.put(key, g = new ArrayList<>());
            g.add(o);
        }
        for (Map.Entry<String, List<Db.Op>> e : groups.entrySet()) {
            List<Db.Op> g = e.getValue();
            for (int i = 0; i < g.size(); i += 200) {
                List<Db.Op> part = g.subList(i, Math.min(g.size(), i + 200));
                List<Long> ids = new ArrayList<>();
                for (Db.Op o : part) ids.add(o.itemId);
                api.post("edit-tag", Api.idsForm(ids) + "&" + e.getKey());
                App.db.deleteOps(part);
            }
        }
    }

    private static List<Db.Feed> subscriptions(Api api) throws IOException, JSONException {
        JSONArray subs = new JSONObject(api.get("subscription/list?output=json")).getJSONArray("subscriptions");
        List<Db.Feed> out = new ArrayList<>();
        for (int i = 0; i < subs.length(); i++) {
            JSONObject s = subs.getJSONObject(i);
            Db.Feed f = new Db.Feed();
            f.id = s.getString("id");
            f.title = Html.fromHtml(s.optString("title"), 0).toString();
            JSONArray cats = s.optJSONArray("categories");
            f.category = cats != null && cats.length() > 0 ? cats.getJSONObject(0).optString("label", "Feeds") : "Feeds";
            f.iconUrl = s.optString("iconUrl", null);
            f.siteUrl = s.optString("htmlUrl", null);
            out.add(f);
        }
        return out;
    }

    private static List<Long> ids(Api api, String stream, String exclude) throws IOException, JSONException {
        List<Long> out = new ArrayList<>();
        String cont = null;
        do {
            String q = "stream/items/ids?output=json&n=10000&s=" + Api.enc(stream)
                    + (exclude != null ? "&xt=" + Api.enc(exclude) : "")
                    + (cont != null ? "&c=" + Api.enc(cont) : "");
            JSONObject o = new JSONObject(api.get(q));
            JSONArray refs = o.optJSONArray("itemRefs");
            if (refs != null) for (int i = 0; i < refs.length(); i++) out.add(Long.parseLong(refs.getJSONObject(i).getString("id")));
            cont = o.has("continuation") && !o.isNull("continuation") ? o.getString("continuation") : null;
        } while (cont != null && !cont.isEmpty());
        return out;
    }

    private static List<ContentValues> parseItems(String json) throws JSONException {
        JSONArray items = new JSONObject(json).getJSONArray("items");
        List<ContentValues> out = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.getJSONObject(i);
            JSONObject body = o.optJSONObject("content");
            if (body == null) body = o.optJSONObject("summary");
            String content = body != null ? body.optString("content") : "";
            String url = href(o.optJSONArray("canonical"));
            if (url == null) url = href(o.optJSONArray("alternate"));
            boolean read = false, starred = false;
            JSONArray cats = o.optJSONArray("categories");
            if (cats != null) for (int k = 0; k < cats.length(); k++) {
                read |= Db.READ.equals(cats.getString(k));
                starred |= Db.STARRED.equals(cats.getString(k));
            }
            ContentValues v = new ContentValues();
            v.put("id", Logic.parseItemId(o.getString("id")));
            v.put("feed_id", o.getJSONObject("origin").getString("streamId"));
            v.put("title", Html.fromHtml(o.optString("title"), 0).toString());
            v.put("summary", Logic.stripHtml(content, 300));
            v.put("content", content);
            v.put("image", Logic.firstImage(content));
            v.put("url", url);
            v.put("published", o.optLong("published") * 1000);
            v.put("read", read ? 1 : 0);
            v.put("starred", starred ? 1 : 0);
            out.add(v);
        }
        return out;
    }

    private static String href(JSONArray a) throws JSONException {
        return a != null && a.length() > 0 ? a.getJSONObject(0).optString("href", null) : null;
    }
}
