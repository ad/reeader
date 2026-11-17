package com.github.ad.reeder;

import android.content.ContentValues;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Feedbin extends Service {
    private static final String JSON = "application/json; charset=utf-8";

    Feedbin() {
        super("FEEDBIN", "Feedbin", "feedbin.com", "https://api.feedbin.com", "Use your Feedbin email and password.");
    }

    String userHint() {
        return "Email";
    }

    String login(String base, String user, String pass) throws IOException {
        String auth = Base64.encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        Api.call("GET", base + "/v2/authentication.json", null, null, "Authorization", "Basic " + auth);
        return auth;
    }

    Backend backend(String base, String auth) {
        return new Session(base + "/v2/", "Basic " + auth);
    }

    private static final class Session implements Backend {
        private final String base, auth;

        Session(String base, String auth) {
            this.base = base;
            this.auth = auth;
        }

        private String get(String path) throws IOException {
            return Api.call("GET", base + path, null, null, "Authorization", auth);
        }

        public List<Db.Feed> subscriptions() throws IOException, JSONException {
            Map<String, String> folders = new HashMap<>();
            JSONArray tags = new JSONArray(get("taggings.json"));
            for (int i = 0; i < tags.length(); i++) {
                JSONObject t = tags.getJSONObject(i);
                folders.put(t.getString("feed_id"), t.getString("name"));
            }
            Map<String, String> icons = new HashMap<>();
            JSONArray ic = new JSONArray(get("icons.json"));
            for (int i = 0; i < ic.length(); i++) icons.put(ic.getJSONObject(i).getString("host"), ic.getJSONObject(i).getString("url"));
            JSONArray subs = new JSONArray(get("subscriptions.json"));
            List<Db.Feed> out = new ArrayList<>();
            for (int i = 0; i < subs.length(); i++) {
                JSONObject s = subs.getJSONObject(i);
                Db.Feed f = new Db.Feed();
                f.id = s.getString("feed_id");
                f.title = s.optString("title");
                f.category = folders.containsKey(f.id) ? folders.get(f.id) : "Feeds";
                f.siteUrl = Backend.str(s, "site_url");
                f.iconUrl = f.siteUrl == null ? null : icons.get(Uri.parse(f.siteUrl).getHost());
                out.add(f);
            }
            return out;
        }

        public List<Long> unreadIds() throws IOException, JSONException {
            return ids("unread_entries.json");
        }

        public List<Long> starredIds() throws IOException, JSONException {
            return ids("starred_entries.json");
        }

        public List<Long> recentIds(long sinceMs) throws IOException, JSONException {
            List<Long> out = new ArrayList<>();
            String since = Api.enc(java.time.Instant.ofEpochMilli(sinceMs).toString());
            for (int page = 1; ; page++) {
                JSONArray a;
                try {
                    a = new JSONArray(get("entries.json?per_page=100&since=" + since + "&page=" + page));
                } catch (Api.HttpException e) {
                    if (e.code == 404) break;
                    throw e;
                }
                for (int i = 0; i < a.length(); i++) out.add(a.getJSONObject(i).getLong("id"));
                if (a.length() < 100) break;
            }
            return out;
        }

        private List<Long> ids(String path) throws IOException, JSONException {
            JSONArray a = new JSONArray(get(path));
            List<Long> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) out.add(a.getLong(i));
            return out;
        }

        public List<ContentValues> items(List<Long> ids) throws IOException, JSONException {
            StringBuilder q = new StringBuilder();
            for (long id : ids) q.append(q.length() == 0 ? "" : ",").append(id);
            JSONArray a = new JSONArray(get("entries.json?ids=" + q));
            List<ContentValues> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject e = a.getJSONObject(i);
                String content = e.isNull("content") ? Backend.str(e, "summary") : e.getString("content");
                out.add(Backend.row(e.getLong("id"), null, e.getString("feed_id"), e.isNull("title") ? "" : e.getString("title"),
                        content, Backend.str(e, "url"), Logic.isoMillis(e.optString("published", null)), false, false));
            }
            return out;
        }

        public void mark(List<Long> ids, String tag, boolean on) throws IOException {
            String key = Db.READ.equals(tag) ? "unread_entries" : "starred_entries";
            boolean delete = Db.READ.equals(tag) == on;
            String body;
            try {
                body = new JSONObject().put(key, new JSONArray(ids)).toString();
            } catch (JSONException e) {
                throw new IOException(e);
            }
            Api.call("POST", base + key + (delete ? "/delete.json" : ".json"), JSON, body, "Authorization", auth);
        }
    }
}
