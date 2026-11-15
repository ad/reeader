package com.github.ad.reeder;

import android.content.ContentValues;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;

interface Backend {
    List<Db.Feed> subscriptions() throws IOException, JSONException;

    List<Long> unreadIds() throws IOException, JSONException;

    List<Long> starredIds() throws IOException, JSONException;

    List<ContentValues> items(List<Long> ids) throws IOException, JSONException;

    void mark(List<Long> ids, String tag, boolean on) throws IOException;
}
