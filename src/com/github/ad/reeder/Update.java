package com.github.ad.reeder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class Update {
    private static final String LATEST = "https://api.github.com/repos/ad/reeder/releases/latest";
    private static final long INTERVAL = 3 * 3_600_000L;
    private static long checked;

    static void check(Activity activity) {
        long now = System.currentTimeMillis();
        if (checked != 0 && now - checked < INTERVAL) return;
        checked = now;
        new Thread(() -> {
            try {
                JSONObject release = new JSONObject(Api.read(open(LATEST).getInputStream()));
                String tag = release.optString("tag_name");
                long installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
                if (Logic.releaseBuild(tag) <= installed) return;
                JSONArray assets = release.optJSONArray("assets");
                for (int i = 0; assets != null && i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if (!asset.optString("name").endsWith(".apk")) continue;
                    String url = asset.optString("browser_download_url");
                    activity.runOnUiThread(() -> offer(activity, tag, url));
                    return;
                }
            } catch (Exception ignored) {
                // offline or rate-limited: try again on a later resume
            }
        }).start();
    }

    private static void offer(Activity activity, String tag, String url) {
        if (activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
                .setTitle("Update " + tag + " is available")
                .setPositiveButton("Install", (d, w) -> install(activity, url))
                .setNegativeButton("Later", null)
                .show();
    }

    private static void install(Activity activity, String url) {
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            checked = 0;
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
            return;
        }
        Toast.makeText(activity, "Downloading update…", Toast.LENGTH_SHORT).show();
        Context app = activity.getApplicationContext();
        new Thread(() -> {
            PackageInstaller installer = app.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            int id = -1;
            try {
                id = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(id)) {
                    try (InputStream in = open(url).getInputStream(); OutputStream out = session.openWrite("apk", 0, -1)) {
                        byte[] buf = new byte[65536];
                        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                        session.fsync(out);
                    }
                    Intent done = new Intent(app, Receiver.class);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    session.commit(PendingIntent.getBroadcast(app, 0, done, flags).getIntentSender());
                }
            } catch (Exception e) {
                if (id != -1) installer.abandonSession(id);
                activity.runOnUiThread(() -> Toast.makeText(app, "Update failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20_000);
        c.setReadTimeout(30_000);
        if (c.getResponseCode() >= 400) throw new IOException("HTTP " + c.getResponseCode());
        return c;
    }

    public static final class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                Toast.makeText(context, "Update failed: " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), Toast.LENGTH_LONG).show();
            }
        }
    }
}
