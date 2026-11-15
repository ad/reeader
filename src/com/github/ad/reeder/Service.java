package com.github.ad.reeder;

import java.io.IOException;

abstract class Service {
    static final Service FRESHRSS = new FreshRss();
    static final Service[] ALL = {new Feedbin(), new Feedly(), new NewsBlur(), new OldReader(), new Inoreader(), new BazQux(),
            FRESHRSS, new Miniflux(), new GoogleReader()};

    final String id, title, subtitle, host, hint;

    Service(String id, String title, String subtitle, String host, String hint) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.host = host;
        this.hint = hint;
    }

    static Service of(String id) {
        for (Service s : ALL) if (s.id.equals(id)) return s;
        return FRESHRSS;
    }

    String serverHint() {
        return "Server, e.g. https://rss.example.com";
    }

    /** null hides the field: the service signs in with a single token. */
    String userHint() {
        return "Username";
    }

    String passwordHint() {
        return "Password";
    }

    String apiBase(String url) {
        if (host != null) return host;
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    abstract String login(String base, String user, String pass) throws IOException;

    abstract Backend backend(String base, String auth);
}
