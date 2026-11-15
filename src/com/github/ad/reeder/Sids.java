package com.github.ad.reeder;

import java.util.HashMap;
import java.util.Map;

// Db keys items by long, so string server ids are hashed.
final class Sids {
    private final Map<Long, String> seen = new HashMap<>();

    long id(String sid) {
        long id = Logic.hashId(sid);
        seen.put(id, sid);
        return id;
    }

    String seen(long id) {
        return seen.get(id);
    }

    String sid(long id) {
        String s = seen.get(id);
        return s != null ? s : App.db.sid(id);
    }
}
