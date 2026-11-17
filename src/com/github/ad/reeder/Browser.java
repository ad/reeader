package com.github.ad.reeder;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Opens article links where the "browser" setting says: in-app, the system default, or a chosen browser package. */
final class Browser {
    static final String IN_APP = "inapp", SYSTEM = "system";

    static String pref() {
        return App.prefs.getString("browser", SYSTEM);
    }

    static void open(Activity a, Uri uri) {
        String b = pref();
        boolean web = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        if (web && IN_APP.equals(b)) {
            a.startActivity(new Intent(a, WebActivity.class).setData(uri));
            return;
        }
        if (web && !SYSTEM.equals(b)) {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage(b));
                return;
            } catch (ActivityNotFoundException ignored) {
                // chosen browser was uninstalled: fall back to the default
            }
        }
        external(a, uri);
    }

    static void external(Activity a, Uri uri) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "No app to open " + uri, Toast.LENGTH_SHORT).show();
        }
    }

    /** Installed browsers as {package, label}, sorted by label. */
    static List<String[]> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE);
        List<String[]> r = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(probe, PackageManager.MATCH_ALL)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) continue;
            r.add(new String[]{pkg, ri.loadLabel(pm).toString()});
        }
        r.sort((x, y) -> x[1].compareToIgnoreCase(y[1]));
        return r;
    }
}
