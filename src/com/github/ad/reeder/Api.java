package com.github.ad.reeder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class Api {
    static final class HttpException extends IOException {
        final int code;

        HttpException(int code, String url) {
            super("HTTP " + code + " " + url);
            this.code = code;
        }
    }

    static final String FORM = "application/x-www-form-urlencoded", JSON = "application/json";

    private final String base;
    private final String[] headers;
    private String token;

    Api(String base, String auth, String... extraHeaders) {
        this.base = base;
        headers = Arrays.copyOf(extraHeaders, extraHeaders.length + 2);
        headers[extraHeaders.length] = "Authorization";
        headers[extraHeaders.length + 1] = "GoogleLogin auth=" + auth;
    }

    static String login(String base, String user, String pass, String... headers) throws IOException {
        String body = call("POST", base + "/accounts/ClientLogin", FORM, "Email=" + enc(user) + "&Passwd=" + enc(pass), headers);
        for (String line : body.split("\n")) if (line.startsWith("Auth=")) return line.substring(5).trim();
        throw new IOException("No Auth in response");
    }

    String get(String path) throws IOException {
        return call("GET", base + "/reader/api/0/" + path, null, null, headers);
    }

    String post(String path, String form) throws IOException {
        if (token == null) token = get("token").trim();
        try {
            return call("POST", base + "/reader/api/0/" + path, FORM, form + "&T=" + enc(token), headers);
        } catch (HttpException e) {
            if (e.code != 401) throw e;
            token = get("token").trim();
            return call("POST", base + "/reader/api/0/" + path, FORM, form + "&T=" + enc(token), headers);
        }
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    /** The caller must disconnect. */
    static HttpURLConnection connect(String method, String url, String type, String body, String... headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestMethod(method);
        for (int i = 0; i < headers.length; i += 2) c.setRequestProperty(headers[i], headers[i + 1]);
        try {
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", type);
                try (OutputStream o = c.getOutputStream()) {
                    o.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new HttpException(code, url);
            return c;
        } catch (IOException e) {
            c.disconnect();
            throw e;
        }
    }

    static String call(String method, String url, String type, String body, String... headers) throws IOException {
        HttpURLConnection c = connect(method, url, type, body, headers);
        try {
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    static String yandex300(String token, String articleUrl) throws IOException, JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL("https://300.ya.ru/api/sharing-url").openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("Authorization", "OAuth " + token);
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        try {
            try (OutputStream o = c.getOutputStream()) {
                o.write(new JSONObject().put("article_url", articleUrl).toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            InputStream in = code / 100 == 2 ? c.getInputStream() : c.getErrorStream();
            if (in == null) throw new HttpException(code, c.getURL().toString());
            String s = read(in);
            if (!s.startsWith("{")) throw new IOException(s.length() < 100 ? s : "HTTP " + code);
            JSONObject r = new JSONObject(s);
            if (!"success".equals(r.optString("status"))) throw new IOException(r.optString("message", "HTTP " + code));
            return call("GET", r.getString("sharing_url"), null, null);
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
