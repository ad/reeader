package com.github.ad.reeder;

import android.content.ContentValues;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class NewsBlur extends Service {
    private static final String COOKIE = "newsblur_sessionid";

    NewsBlur() {
        super("NEWSBLUR", "NewsBlur", "newsblur.com", "https://www.newsblur.com", "Use your NewsBlur username and password.");
    }

    String login(String base, String user, String pass) throws IOException {
        HttpURLConnection c = Api.connect("POST", base + "/api/login", Api.FORM,
                "username=" + Api.enc(user) + "&password=" + Api.enc(pass), "User-Agent", "Reeader");
        try {
            String body = Api.read(c.getInputStream());
            JSONObject o = new JSONObject(body);
            if (o.optInt("code") != 1) throw new IOException(o.optString("errors", "Login failed"));
            List<String> cookies = c.getHeaderFields().get("Set-Cookie");
            if (cookies != null) for (String k : cookies) if (k.startsWith(COOKIE + "=")) return k.split(";", 2)[0];
            throw new IOException("No session cookie");
        } catch (JSONException e) {
            throw new IOException(e);
        } finally {
            c.disconnect();
        }
    }

    Backend backend(String base, String auth) {
        return new Session(base, auth);
    }

    private static final class Session implements Backend {
        private final String base, cookie;
        private final Sids sids = new Sids();

        Session(String base, String cookie) {
            this.base = base;
            this.cookie = cookie;
        }

        private JSONObject call(String method, String path, String form) throws IOException, JSONException {
            return new JSONObject(Api.call(method, base + path, form == null ? null : Api.FORM, form, "Cookie", cookie, "User-Agent", "Reeader"));
        }

        public List<Db.Feed> subscriptions() throws IOException, JSONException {
            JSONObject o = call("GET", "/reader/feeds?flat=true&update_counts=false", null);
            // An expired session silently gets the demo user's feeds instead of an error.
            if (!o.optBoolean("authenticated")) throw new IOException("NewsBlur session expired, sign in again");
            Map<String, String> folders = new HashMap<>();
            JSONObject flat = o.optJSONObject("flat_folders");
            if (flat != null) for (Iterator<String> it = flat.keys(); it.hasNext(); ) {
                String name = it.next();
                JSONArray ids = flat.getJSONArray(name);
                for (int i = 0; i < ids.length(); i++) folders.put(ids.getString(i), name.trim().isEmpty() ? "Feeds" : name);
            }
            JSONObject feeds = o.getJSONObject("feeds");
            List<Db.Feed> out = new ArrayList<>();
            for (Iterator<String> it = feeds.keys(); it.hasNext(); ) {
                JSONObject f = feeds.getJSONObject(it.next());
                Db.Feed d = new Db.Feed();
                d.id = f.getString("id");
                d.title = Html.fromHtml(f.optString("feed_title"), 0).toString();
                d.category = folders.containsKey(d.id) ? folders.get(d.id) : "Feeds";
                d.siteUrl = Backend.str(f, "feed_link");
                d.iconUrl = Backend.str(f, "favicon_url");
                out.add(d);
            }
            return out;
        }

        public List<Long> unreadIds() throws IOException, JSONException {
            JSONObject byFeed = call("GET", "/reader/unread_story_hashes", null).getJSONObject("unread_feed_story_hashes");
            List<Long> out = new ArrayList<>();
            for (Iterator<String> it = byFeed.keys(); it.hasNext(); ) add(out, byFeed.getJSONArray(it.next()));
            return out;
        }

        public List<Long> starredIds() throws IOException, JSONException {
            List<Long> out = new ArrayList<>();
            add(out, call("GET", "/reader/starred_story_hashes", null).getJSONArray("starred_story_hashes"));
            return out;
        }

        private void add(List<Long> out, JSONArray hashes) throws JSONException {
            for (int i = 0; i < hashes.length(); i++) out.add(sids.id(hashes.getString(i)));
        }

        public List<ContentValues> items(List<Long> ids) throws IOException, JSONException {
            JSONArray stories = call("POST", "/reader/river_stories", form("h", ids) + "&include_hidden=true").getJSONArray("stories");
            List<ContentValues> out = new ArrayList<>();
            for (int i = 0; i < stories.length(); i++) {
                JSONObject s = stories.getJSONObject(i);
                String hash = s.getString("story_hash");
                out.add(Backend.row(sids.id(hash), hash, s.getString("story_feed_id"), Html.fromHtml(s.optString("story_title"), 0).toString(),
                        s.optString("story_content"), Backend.str(s, "story_permalink"), s.optLong("story_timestamp") * 1000, false, false));
            }
            return out;
        }

        public void mark(List<Long> ids, String tag, boolean on) throws IOException {
            String path = Db.READ.equals(tag)
                    ? (on ? "/reader/mark_story_hashes_as_read" : "/reader/mark_story_hash_as_unread")
                    : (on ? "/reader/mark_story_hash_as_starred" : "/reader/mark_story_hash_as_unstarred");
            // Only mark_story_hashes_as_read is documented to take several hashes.
            List<List<Long>> parts = new ArrayList<>();
            if (path.contains("hashes")) parts.add(ids);
            else for (long id : ids) parts.add(java.util.Collections.singletonList(id));
            for (List<Long> part : parts) {
                String form = form("story_hash", part);
                if (!form.isEmpty()) Api.call("POST", base + path, Api.FORM, form, "Cookie", cookie, "User-Agent", "Reeader");
            }
        }

        private String form(String param, List<Long> ids) {
            StringBuilder sb = new StringBuilder();
            for (long id : ids) {
                String hash = sids.sid(id);
                if (hash != null) sb.append(sb.length() == 0 ? "" : "&").append(param).append('=').append(Api.enc(hash));
            }
            return sb.toString();
        }
    }
}
