package com.github.ad.reeder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class Api {
    static final class HttpException extends IOException {
        final int code;

        HttpException(int code, String url) {
            super("HTTP " + code + " " + url);
            this.code = code;
        }
    }

    private final String base;
    final String auth;
    private String token;

    Api(String base, String auth) {
        this.base = base;
        this.auth = auth;
    }

    static String login(String base, String user, String pass) throws IOException {
        String body = request(base + "/accounts/ClientLogin", null, "Email=" + enc(user) + "&Passwd=" + enc(pass));
        for (String line : body.split("\n")) if (line.startsWith("Auth=")) return line.substring(5).trim();
        throw new IOException("No Auth in response");
    }

    String get(String path) throws IOException {
        return request(base + "/reader/api/0/" + path, auth, null);
    }

    String post(String path, String form) throws IOException {
        if (token == null) token = get("token").trim();
        try {
            return request(base + "/reader/api/0/" + path, auth, form + "&T=" + enc(token));
        } catch (HttpException e) {
            if (e.code != 401) throw e;
            token = get("token").trim();
            return request(base + "/reader/api/0/" + path, auth, form + "&T=" + enc(token));
        }
    }

    static String idsForm(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (long id : ids) sb.append("&i=").append(enc(Logic.longItemId(id)));
        return sb.substring(1);
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    private static String request(String url, String auth, String form) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        if (auth != null) c.setRequestProperty("Authorization", "GoogleLogin auth=" + auth);
        try {
            if (form != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream o = c.getOutputStream()) {
                    o.write(form.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new HttpException(code, url);
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    static String read(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = i.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
