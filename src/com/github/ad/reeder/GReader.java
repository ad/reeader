package com.github.ad.reeder;

import android.content.ContentValues;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class GReader implements Backend {
    private final Api api;
    private final Sids sids = new Sids();

    GReader(Api api) {
        this.api = api;
    }

    public List<Db.Feed> subscriptions() throws IOException, JSONException {
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

    public List<Long> unreadIds() throws IOException, JSONException {
        return ids("user/-/state/com.google/reading-list", Db.READ);
    }

    public List<Long> starredIds() throws IOException, JSONException {
        return ids(Db.STARRED, null);
    }

    public List<ContentValues> items(List<Long> ids) throws IOException, JSONException {
        return parseItems(api.post("stream/items/contents?output=json", idsForm(ids)));
    }

    public void mark(List<Long> ids, String tag, boolean on) throws IOException {
        api.post("edit-tag", idsForm(ids) + (on ? "&a=" : "&r=") + Api.enc(tag));
    }

    private List<Long> ids(String stream, String exclude) throws IOException, JSONException {
        List<Long> out = new ArrayList<>();
        String cont = null;
        do {
            String q = "stream/items/ids?output=json&n=10000&s=" + Api.enc(stream)
                    + (exclude != null ? "&xt=" + Api.enc(exclude) : "")
                    + (cont != null ? "&c=" + Api.enc(cont) : "");
            JSONObject o = new JSONObject(api.get(q));
            JSONArray refs = o.optJSONArray("itemRefs");
            if (refs != null) for (int i = 0; i < refs.length(); i++) out.add(id(refs.getJSONObject(i).getString("id")));
            cont = o.has("continuation") && !o.isNull("continuation") ? o.getString("continuation") : null;
        } while (cont != null && !cont.isEmpty());
        return out;
    }

    private long id(String raw) {
        try {
            return Logic.parseItemId(raw);
        } catch (NumberFormatException e) {
            return sids.id(raw.startsWith(Logic.ITEM_PREFIX) ? raw.substring(Logic.ITEM_PREFIX.length()) : raw);
        }
    }

    private String idsForm(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (long id : ids) {
            String sid = sids.sid(id);
            sb.append("&i=").append(Api.enc(sid != null ? Logic.ITEM_PREFIX + sid : Logic.longItemId(id)));
        }
        return sb.substring(1);
    }

    private List<ContentValues> parseItems(String json) throws JSONException {
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
            long id = id(o.getString("id"));
            out.add(Backend.row(id, sids.seen(id), o.getJSONObject("origin").getString("streamId"),
                    Html.fromHtml(o.optString("title"), 0).toString(), content, url, o.optLong("published") * 1000, read, starred));
        }
        return out;
    }

    private static String href(JSONArray a) throws JSONException {
        return a != null && a.length() > 0 ? a.getJSONObject(0).optString("href", null) : null;
    }
}
