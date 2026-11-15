package com.github.ad.reeder;

final class FreshRss extends GReaderService {
    FreshRss() {
        super("FRESHRSS", "FreshRSS", "freshrss.org", null, "Use the API password from FreshRSS → Settings → Profile.");
    }

    String passwordHint() {
        return "API password";
    }

    String apiBase(String url) {
        String u = super.apiBase(url);
        if (u.endsWith("/api/greader.php")) return u;
        if (u.endsWith("/api")) return u + "/greader.php";
        return u + "/api/greader.php";
    }
}
