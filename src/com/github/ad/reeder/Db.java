package com.github.ad.reeder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Db extends SQLiteOpenHelper {
    static final String READ = "user/-/state/com.google/read";
    static final String STARRED = "user/-/state/com.google/starred";

    static final class Feed {
        String id, title, category, iconUrl, siteUrl;
    }

    static final class Item {
        long id, published;
        String feedId, feedTitle, iconUrl, siteUrl, title, summary, url;
        boolean read, starred;
    }

    static final class Op {
        long seq, itemId;
        String tag;
        boolean add;
    }

    Db(Context c) {
        super(c, "reeder.db", null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE feeds(id TEXT PRIMARY KEY, title TEXT, category TEXT, icon_url TEXT, site_url TEXT)");
        db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY, feed_id TEXT, title TEXT, summary TEXT, content TEXT,"
                + " url TEXT, published INTEGER, read INTEGER, starred INTEGER, reader TEXT, tldr TEXT)");
        db.execSQL("CREATE INDEX items_feed ON items(feed_id)");
        db.execSQL("CREATE INDEX items_published ON items(published)");
        db.execSQL("CREATE TABLE ops(seq INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER, tag TEXT, add_tag INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) db.execSQL("ALTER TABLE feeds ADD COLUMN site_url TEXT");
        if (o < 3) db.execSQL("ALTER TABLE items ADD COLUMN tldr TEXT");
    }

    void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("feeds", null, null);
        db.delete("items", null, null);
        db.delete("ops", null, null);
    }

    List<Feed> feeds() {
        List<Feed> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, title, category, icon_url, site_url FROM feeds ORDER BY category COLLATE NOCASE, title COLLATE NOCASE", null)) {
            while (c.moveToNext()) {
                Feed f = new Feed();
                f.id = c.getString(0);
                f.title = c.getString(1);
                f.category = c.getString(2);
                f.iconUrl = c.getString(3);
                f.siteUrl = c.getString(4);
                out.add(f);
            }
        }
        return out;
    }

    void replaceFeeds(List<Feed> feeds) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("feeds", null, null);
            StringBuilder ids = new StringBuilder();
            for (Feed f : feeds) {
                ContentValues v = new ContentValues();
                v.put("id", f.id);
                v.put("title", f.title);
                v.put("category", f.category);
                v.put("icon_url", f.iconUrl);
                v.put("site_url", f.siteUrl);
                db.insert("feeds", null, v);
                ids.append(ids.length() == 0 ? "" : ",").append(android.database.DatabaseUtils.sqlEscapeString(f.id));
            }
            if (!feeds.isEmpty()) db.execSQL("DELETE FROM items WHERE feed_id NOT IN (" + ids + ")");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    Map<String, Integer> counts(boolean starred) {
        Map<String, Integer> out = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT feed_id, COUNT(*) FROM items WHERE "
                + (starred ? "starred=1" : "read=0") + " GROUP BY feed_id", null)) {
            while (c.moveToNext()) out.put(c.getString(0), c.getInt(1));
        }
        return out;
    }

    List<Item> items(String feedId, int filter, String query) {
        StringBuilder where = new StringBuilder("1=1");
        List<String> args = new ArrayList<>();
        if (feedId != null) {
            where.append(" AND i.feed_id=?");
            args.add(feedId);
        }
        if (filter == App.UNREAD) where.append(" AND i.read=0");
        if (filter == App.STARRED) where.append(" AND i.starred=1");
        if (query != null && !query.isEmpty()) {
            where.append(" AND (i.title LIKE ? OR i.content LIKE ?)");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
        }
        List<Item> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT i.id, i.feed_id, f.title, f.icon_url, i.title, i.summary, i.url,"
                + " i.published, i.read, i.starred, f.site_url FROM items i LEFT JOIN feeds f ON f.id=i.feed_id WHERE " + where
                + " ORDER BY i.published DESC", args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                Item it = new Item();
                it.id = c.getLong(0);
                it.feedId = c.getString(1);
                it.feedTitle = c.getString(2);
                it.iconUrl = c.getString(3);
                it.title = c.getString(4);
                it.summary = c.getString(5);
                it.url = c.getString(6);
                it.published = c.getLong(7);
                it.read = c.getInt(8) == 1;
                it.starred = c.getInt(9) == 1;
                it.siteUrl = c.getString(10);
                out.add(it);
            }
        }
        return out;
    }

    Item item(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT i.feed_id, f.title, i.title, i.url, i.published, i.read, i.starred"
                + " FROM items i LEFT JOIN feeds f ON f.id=i.feed_id WHERE i.id=?", new String[]{"" + id})) {
            if (!c.moveToFirst()) return null;
            Item it = new Item();
            it.id = id;
            it.feedId = c.getString(0);
            it.feedTitle = c.getString(1);
            it.title = c.getString(2);
            it.url = c.getString(3);
            it.published = c.getLong(4);
            it.read = c.getInt(5) == 1;
            it.starred = c.getInt(6) == 1;
            return it;
        }
    }

    void refreshFlags(Collection<Item> items) {
        Map<Long, Item> byId = new HashMap<>();
        for (Item it : items) byId.put(it.id, it);
        List<Long> ids = new ArrayList<>(byId.keySet());
        for (int i = 0; i < ids.size(); i += 500) {
            StringBuilder in = new StringBuilder();
            for (long id : ids.subList(i, Math.min(ids.size(), i + 500))) in.append(in.length() == 0 ? "" : ",").append(id);
            try (Cursor c = getReadableDatabase().rawQuery("SELECT id, read, starred FROM items WHERE id IN (" + in + ")", null)) {
                while (c.moveToNext()) {
                    Item it = byId.get(c.getLong(0));
                    it.read = c.getInt(1) == 1;
                    it.starred = c.getInt(2) == 1;
                }
            }
        }
    }

    String content(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT content FROM items WHERE id=?", new String[]{"" + id})) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }

    String reader(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT reader FROM items WHERE id=?", new String[]{"" + id})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    void setReader(long id, String html) {
        ContentValues v = new ContentValues();
        v.put("reader", html);
        getWritableDatabase().update("items", v, "id=?", new String[]{"" + id});
    }

    String tldr(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT tldr FROM items WHERE id=?", new String[]{"" + id})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    void setTldr(long id, String html) {
        ContentValues v = new ContentValues();
        v.put("tldr", html);
        getWritableDatabase().update("items", v, "id=?", new String[]{"" + id});
    }

    void setFlag(Collection<Long> ids, String tag, boolean on) {
        String col = READ.equals(tag) ? "read" : "starred";
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (long id : ids) {
                db.execSQL("UPDATE items SET " + col + "=? WHERE id=?", new Object[]{on ? 1 : 0, id});
                db.execSQL("DELETE FROM ops WHERE item_id=? AND tag=?", new Object[]{id, tag});
                db.execSQL("INSERT INTO ops(item_id, tag, add_tag) VALUES(?,?,?)", new Object[]{id, tag, on ? 1 : 0});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    List<Op> ops() {
        List<Op> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT seq, item_id, tag, add_tag FROM ops ORDER BY seq", null)) {
            while (c.moveToNext()) {
                Op o = new Op();
                o.seq = c.getLong(0);
                o.itemId = c.getLong(1);
                o.tag = c.getString(2);
                o.add = c.getInt(3) == 1;
                out.add(o);
            }
        }
        return out;
    }

    void deleteOps(Collection<Op> ops) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Op o : ops) db.execSQL("DELETE FROM ops WHERE seq=?", new Object[]{o.seq});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    List<Long> missing(Collection<Long> ids) {
        List<Long> out = new ArrayList<>();
        java.util.HashSet<Long> have = new java.util.HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id FROM items", null)) {
            while (c.moveToNext()) have.add(c.getLong(0));
        }
        for (long id : ids) if (!have.contains(id)) out.add(id);
        return out;
    }

    void insertItems(List<ContentValues> rows) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (ContentValues v : rows) db.insertWithOnConflict("items", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void applyServerState(Collection<Long> unread, Collection<Long> starred) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("CREATE TEMP TABLE IF NOT EXISTS u(id INTEGER PRIMARY KEY)");
            db.execSQL("CREATE TEMP TABLE IF NOT EXISTS s(id INTEGER PRIMARY KEY)");
            db.execSQL("DELETE FROM u");
            db.execSQL("DELETE FROM s");
            for (long id : unread) db.execSQL("INSERT OR IGNORE INTO u VALUES(?)", new Object[]{id});
            for (long id : starred) db.execSQL("INSERT OR IGNORE INTO s VALUES(?)", new Object[]{id});
            db.execSQL("UPDATE items SET read = CASE WHEN id IN (SELECT id FROM u) THEN 0 ELSE 1 END"
                    + " WHERE id NOT IN (SELECT item_id FROM ops WHERE tag=?)", new Object[]{READ});
            db.execSQL("UPDATE items SET starred = CASE WHEN id IN (SELECT id FROM s) THEN 1 ELSE 0 END"
                    + " WHERE id NOT IN (SELECT item_id FROM ops WHERE tag=?)", new Object[]{STARRED});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void prune(long olderThanMs) {
        getWritableDatabase().execSQL("DELETE FROM items WHERE read=1 AND starred=0 AND published<?"
                + " AND id NOT IN (SELECT item_id FROM ops)", new Object[]{olderThanMs});
    }
}
