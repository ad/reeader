package com.github.ad.reeder;

import android.content.ContentValues;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class Feedly extends Service {
    Feedly() {
        super("FEEDLY", "Feedly", "feedly.com", "https://cloud.feedly.com",
                "Paste an API access token from feedly.com/i/team/api. Feedly issues them to Enterprise plans only.");
    }

    String userHint() {
        return null;
    }

    String passwordHint() {
        return "Access token";
    }

    String login(String base, String user, String pass) throws IOException {
        Api.call("GET", base + "/v3/profile", null, null, "Authorization", "Bearer " + pass.trim());
        return pass.trim();
    }

    Backend backend(String base, String auth) {
        return new Session(base + "/v3/", "Bearer " + auth);
    }

    private static final class Session implements Backend {
        private final String base, auth;
        private final Sids sids = new Sids();
        private String user;

        Session(String base, String auth) {
            this.base = base;
            this.auth = auth;
        }

        private String call(String method, String path, String json) throws IOException {
            return Api.call(method, base + path, json == null ? null : Api.JSON, json, "Authorization", auth);
        }

        private String stream(String name) throws IOException, JSONException {
            if (user == null) user = new JSONObject(call("GET", "profile", null)).getString("id");
            return "user/" + user + "/" + name;
        }

        public List<Db.Feed> subscriptions() throws IOException, JSONException {
            JSONArray subs = new JSONArray(call("GET", "subscriptions", null));
            List<Db.Feed> out = new ArrayList<>();
            for (int i = 0; i < subs.length(); i++) {
                JSONObject f = subs.getJSONObject(i);
                JSONArray cats = f.optJSONArray("categories");
                Db.Feed d = new Db.Feed();
                d.id = f.getString("id");
                d.title = f.optString("title");
                d.category = cats != null && cats.length() > 0 ? cats.getJSONObject(0).optString("label", "Feeds") : "Feeds";
                d.siteUrl = Backend.str(f, "website");
                d.iconUrl = f.isNull("iconUrl") ? Backend.str(f, "visualUrl") : f.getString("iconUrl");
                out.add(d);
            }
            return out;
        }

        public List<Long> unreadIds() throws IOException, JSONException {
            return ids(stream("category/global.all"), "&unreadOnly=true");
        }

        public List<Long> starredIds() throws IOException, JSONException {
            return ids(stream("tag/global.saved"), "");
        }

        private List<Long> ids(String streamId, String extra) throws IOException, JSONException {
            List<Long> out = new ArrayList<>();
            String cont = null;
            do {
                JSONObject o = new JSONObject(call("GET", "streams/ids?count=250&streamId=" + Api.enc(streamId) + extra
                        + (cont != null ? "&continuation=" + Api.enc(cont) : ""), null));
                JSONArray ids = o.optJSONArray("ids");
                if (ids != null) for (int i = 0; i < ids.length(); i++) out.add(sids.id(ids.getString(i)));
                cont = Backend.str(o, "continuation");
            } while (cont != null && !cont.isEmpty());
            return out;
        }

        public List<ContentValues> items(List<Long> ids) throws IOException, JSONException {
            JSONArray entries = new JSONArray(call("POST", "entries/.mget", sidArray(ids).toString()));
            List<ContentValues> out = new ArrayList<>();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject e = entries.getJSONObject(i);
                JSONObject body = e.optJSONObject("content");
                if (body == null) body = e.optJSONObject("summary");
                JSONArray alt = e.optJSONArray("alternate");
                String url = alt != null && alt.length() > 0 ? Backend.str(alt.getJSONObject(0), "href") : Backend.str(e, "canonicalUrl");
                JSONObject origin = e.optJSONObject("origin");
                String sid = e.getString("id");
                out.add(Backend.row(sids.id(sid), sid, origin != null ? origin.optString("streamId") : "", e.optString("title"),
                        body != null ? body.optString("content") : "", url, e.optLong("published"), false, false));
            }
            return out;
        }

        public void mark(List<Long> ids, String tag, boolean on) throws IOException {
            JSONArray entryIds = sidArray(ids);
            if (entryIds.length() == 0) return;
            String action = Db.READ.equals(tag) ? (on ? "markAsRead" : "keepUnread") : (on ? "markAsSaved" : "markAsUnsaved");
            try {
                call("POST", "markers", new JSONObject().put("action", action).put("type", "entries").put("entryIds", entryIds).toString());
            } catch (JSONException e) {
                throw new IOException(e);
            }
        }

        private JSONArray sidArray(List<Long> ids) {
            JSONArray a = new JSONArray();
            for (long id : ids) {
                String sid = sids.sid(id);
                if (sid != null) a.put(sid);
            }
            return a;
        }
    }
}
