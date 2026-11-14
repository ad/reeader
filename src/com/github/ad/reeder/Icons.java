package com.github.ad.reeder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Icons {
    private static final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(24 << 20) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final Set<String> failed = new HashSet<>();
    private static final java.util.Map<String, java.util.List<ImageView>> waiting = new java.util.HashMap<>();
    private static final ExecutorService pool = Executors.newFixedThreadPool(3);
    private static final Handler main = new Handler(Looper.getMainLooper());

    static void load(ImageView v, String iconUrl, String siteUrl) {
        String url = iconUrl != null ? iconUrl : favicon(siteUrl);
        into(v, url, iconUrl != null ? favicon(siteUrl) : null, true);
    }

    static void thumb(ImageView v, String url) {
        v.setVisibility(url == null || failed.contains(url) ? android.view.View.GONE : android.view.View.VISIBLE);
        into(v, url, null, false);
    }

    private static void into(ImageView v, String url, String fallback, boolean icon) {
        v.setTag(url);
        Bitmap b = url == null ? null : mem.get(url);
        if (b != null) {
            show(v, b, icon);
            return;
        }
        if (icon) {
            v.setBackground(null);
            v.setPadding(0, 0, 0, 0);
            v.setImageResource(R.drawable.ic_feed);
        } else {
            v.setImageDrawable(null);
        }
        if (url == null || failed.contains(url)) return;
        java.util.List<ImageView> views = waiting.get(url);
        if (views != null) {
            views.add(v);
            return;
        }
        waiting.put(url, views = new java.util.ArrayList<>());
        views.add(v);
        Context c = v.getContext().getApplicationContext();
        pool.execute(() -> {
            Bitmap bm = fetch(c, url);
            if (bm == null && fallback != null) bm = fetch(c, fallback);
            Bitmap result = bm;
            main.post(() -> {
                java.util.List<ImageView> done = waiting.remove(url);
                if (result == null) {
                    failed.add(url);
                    if (!icon) for (ImageView w : done) if (url.equals(w.getTag())) w.setVisibility(android.view.View.GONE);
                    return;
                }
                mem.put(url, result);
                for (ImageView w : done) if (url.equals(w.getTag())) show(w, result, icon);
            });
        });
    }

    private static void show(ImageView v, Bitmap b, boolean icon) {
        v.setImageBitmap(b);
        if (!icon) return;
        v.setBackground(Ui.rounded(v.getContext(), R.color.icon_tile, 4));
        int p = Ui.color(v.getContext(), R.color.icon_tile) == 0 ? 0 : Ui.dp(v.getContext(), 2);
        v.setPadding(p, p, p, p);
    }

    private static String favicon(String site) {
        android.net.Uri u = site == null ? null : android.net.Uri.parse(site);
        return u == null || u.getScheme() == null || u.getHost() == null ? null : u.getScheme() + "://" + u.getHost() + "/favicon.ico";
    }

    static void clear(Context c) {
        mem.evictAll();
        File[] files = dir(c).listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    private static File dir(Context c) {
        File d = new File(c.getCacheDir(), "icons");
        d.mkdirs();
        return d;
    }

    private static Bitmap fetch(Context c, String url) {
        File f = new File(dir(c), java.util.UUID.nameUUIDFromBytes(url.getBytes()).toString());
        try {
            if (!f.exists()) {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(10000);
                try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[8192];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                } finally {
                    con.disconnect();
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= 256 || Math.max(o.outWidth, o.outHeight) / sample > 2048) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            if (b != null && sample > 1) try (FileOutputStream out = new FileOutputStream(f)) {
                b.compress(Bitmap.CompressFormat.JPEG, 85, out);
            }
            if (b == null) f.delete();
            return b;
        } catch (Exception e) {
            f.delete();
            return null;
        }
    }
}
