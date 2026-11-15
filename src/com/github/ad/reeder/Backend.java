package com.github.ad.reeder;

import android.content.ContentValues;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

interface Backend {
    List<Db.Feed> subscriptions() throws IOException, JSONException;

    List<Long> unreadIds() throws IOException, JSONException;

    List<Long> starredIds() throws IOException, JSONException;

    List<ContentValues> items(List<Long> ids) throws IOException, JSONException;

    void mark(List<Long> ids, String tag, boolean on) throws IOException;

    /** optString(k, null) returns "null" for a JSON null on Android. */
    static String str(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optString(key);
    }

    static ContentValues row(long id, String sid, String feedId, String title, String content, String url,
                             long publishedMs, boolean read, boolean starred) {
        if (content == null) content = "";
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("sid", sid);
        v.put("feed_id", feedId);
        v.put("title", title);
        v.put("summary", Logic.stripHtml(content, 300));
        v.put("content", content);
        v.put("image", Logic.firstImage(content));
        v.put("url", url);
        v.put("published", publishedMs);
        v.put("read", read ? 1 : 0);
        v.put("starred", starred ? 1 : 0);
        return v;
    }
}
