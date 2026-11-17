package com.github.ad.reeder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

public class SettingsActivity extends Activity {
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        App.init(this);
        LinearLayout root = Ui.column(this);
        root.addView(Ui.topBar(this, Ui.icon(this, R.drawable.ic_back, v -> finish()), null, null));
        ScrollView scroll = new ScrollView(this);
        body = Ui.column(this);
        body.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Ui.edgeToEdge(this, root);
        build();
    }

    private void build() {
        body.removeAllViews();
        Service service = Service.of(App.prefs.getString("type", null));
        title(this, body, service.title, App.prefs.getString("user", ""));

        section(this, body, "GENERAL");
        LinearLayout g = card(this, body);
        String desc = App.prefs.getString("title", service.title);
        valueRow(this, g, "Description", desc.isEmpty() ? "Hidden" : desc, v -> {
            EditText e = new EditText(this);
            e.setSingleLine();
            e.setText(desc);
            new AlertDialog.Builder(this).setTitle("Description")
                    .setMessage("Shown above the subscriptions. Leave empty to hide it.").setView(e)
                    .setPositiveButton("Save", (d, w) -> {
                        App.prefs.edit().putString("title", e.getText().toString().trim()).apply();
                        build();
                    })
                    .setNeutralButton("Default", (d, w) -> {
                        App.prefs.edit().remove("title").apply();
                        build();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        if (service.host == null) {
            TextView server = valueRow(this, g, "Server", App.prefs.getString("base", ""), null);
            ((TextView) ((LinearLayout) server.getParent()).getChildAt(0)).setTextColor(Ui.color(this, R.color.text2));
        }

        section(this, body, "SYNCING");
        LinearLayout s = card(this, body);
        choice(s, "Sync", "sync_mode", new String[]{"start", "manual"}, new String[]{"On start", "Manual"}, "start");
        String[] keepLabels = {"1 week", "1 month", "3 months", "Forever"};
        int[] keepDays = {7, 30, 90, 0};
        int keep = App.prefs.getInt("keep_days", 30);
        String keepLabel = keepLabels[1];
        for (int i = 0; i < keepDays.length; i++) if (keepDays[i] == keep) keepLabel = keepLabels[i];
        valueRow(this, s, "Keep read items", keepLabel, v -> pick(v, keepLabels, i -> {
            App.prefs.edit().putInt("keep_days", keepDays[i]).apply();
            build();
        }));

        section(this, body, "READING");
        LinearLayout r = card(this, body);
        LinearLayout readerRow = rowIn(this, r, "Reader View (full-text)");
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        readerRow.addView(chevron);
        readerRow.setOnClickListener(v -> startActivity(new Intent(this, ReaderSettingsActivity.class)));
        choice(r, "Sort subscriptions", "sort", new String[]{"folders", "alpha"}, new String[]{"Folders", "Alphabetical"}, "folders");
        String[] markLabels = {"On open", "Manual"};
        valueRow(this, r, "Mark article as read", markLabels[App.prefs.getBoolean("mark_open", true) ? 0 : 1], v -> pick(v, markLabels, i -> {
            App.prefs.edit().putBoolean("mark_open", i == 0).apply();
            build();
        }));

        List<String[]> browsers = Browser.installed(this);
        String[] bValues = new String[browsers.size() + 2], bLabels = new String[bValues.length];
        bValues[0] = Browser.IN_APP;
        bLabels[0] = "Reeader";
        bValues[1] = Browser.SYSTEM;
        bLabels[1] = "Default browser";
        for (int i = 0; i < browsers.size(); i++) {
            bValues[i + 2] = browsers.get(i)[0];
            bLabels[i + 2] = browsers.get(i)[1];
        }
        choice(r, "Open links in", "browser", bValues, bLabels, Browser.SYSTEM);

        LinearLayout m = card(this, body);
        switchRow(this, m, "Mark as read on scroll", App.prefs.getBoolean("mark_scroll", false),
                on -> App.prefs.edit().putBoolean("mark_scroll", on).apply());
        caption(this, body, "If enabled, items will be automatically marked as read while scrolling down. "
                + "Note: scrolling up does not mark items as read, only scrolling down.");

        section(this, body, "SUMMARY");
        LinearLayout y = card(this, body);
        String token = App.prefs.getString("ya300_token", "");
        valueRow(this, y, "Yandex 300 token", token.isEmpty() ? "Not set" : "Set", v -> {
            EditText e = new EditText(this);
            e.setSingleLine();
            e.setText(token);
            new AlertDialog.Builder(this).setTitle("Yandex 300 token").setView(e)
                    .setPositiveButton("Save", (d, w) -> {
                        App.prefs.edit().putString("ya300_token", e.getText().toString().trim()).apply();
                        build();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        caption(this, body, "Get a token at 300.ya.ru. Article links are sent to Yandex for summarizing.");

        section(this, body, "STORAGE");
        LinearLayout st = card(this, body);
        valueRow(this, st, "Articles", android.text.format.Formatter.formatShortFileSize(this, getDatabasePath("reeder.db").length()), null);
        valueRow(this, st, "Cache", android.text.format.Formatter.formatShortFileSize(this, size(getCacheDir())), null);
        LinearLayout cc = card(this, body);
        cc.addView(centered("Clear Cache", v -> {
            Icons.clear(this);
            clearWebCache();
            build();
        }));
        caption(this, body, "Cache holds feed icons, article images and web pages. Articles are kept by the setting above.");

        section(this, body, "ACCOUNT");
        LinearLayout a = card(this, body);
        a.addView(centered("Remove Account…", v -> new AlertDialog.Builder(this)
                .setMessage("Remove this account and all downloaded articles?")
                .setPositiveButton("Remove", (d, w) -> removeAccount())
                .setNegativeButton("Cancel", null)
                .show()));

        try {
            TextView version = Ui.text(this, 12, R.color.text2, false);
            version.setText("Version " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
            version.setGravity(Gravity.CENTER);
            version.setPadding(0, Ui.dp(this, 16), 0, 0);
            body.addView(version);
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private void clearWebCache() {
        android.webkit.WebView w = new android.webkit.WebView(this);
        w.clearCache(true);
        w.destroy();
    }

    private TextView centered(String label, View.OnClickListener click) {
        TextView t = Ui.text(this, 16, R.color.text, false);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        t.setOnClickListener(click);
        return t;
    }

    private static long size(java.io.File f) {
        java.io.File[] kids = f.listFiles();
        if (kids == null) return f.length();
        long n = 0;
        for (java.io.File k : kids) n += size(k);
        return n;
    }

    private void choice(LinearLayout card, String label, String key, String[] values, String[] labels, String def) {
        String cur = App.prefs.getString(key, def);
        String shown = labels[0];
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) shown = labels[i];
        valueRow(this, card, label, shown, v -> pick(v, labels, i -> {
            App.prefs.edit().putString(key, values[i]).apply();
            build();
        }));
    }

    private void pick(View anchor, String[] labels, java.util.function.IntConsumer onPick) {
        PopupMenu p = new PopupMenu(this, anchor, Gravity.END);
        for (int i = 0; i < labels.length; i++) p.getMenu().add(0, i, i, labels[i]);
        p.setOnMenuItemClickListener(mi -> {
            onPick.accept(mi.getItemId());
            return true;
        });
        p.show();
    }

    private void removeAccount() {
        App.db.clear();
        App.prefs.edit().clear().apply();
        Icons.clear(this);
        android.webkit.CookieManager.getInstance().removeAllCookies(null);
        android.webkit.WebStorage.getInstance().deleteAllData();
        clearWebCache();
        startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    static void title(Context c, LinearLayout body, String title, String subtitle) {
        TextView t = Ui.text(c, 22, R.color.text, true);
        t.setText(title);
        t.setPadding(Ui.dp(c, 4), 0, 0, 0);
        body.addView(t);
        if (subtitle != null) {
            TextView s = Ui.text(c, 12, R.color.text2, true);
            s.setText(subtitle);
            s.setPadding(Ui.dp(c, 4), 0, 0, 0);
            body.addView(s);
        }
    }

    static void section(Context c, LinearLayout body, String label) {
        TextView t = Ui.text(c, 12, R.color.text2, false);
        t.setText(label);
        t.setLetterSpacing(0.04f);
        t.setPadding(Ui.dp(c, 18), Ui.dp(c, 24), 0, Ui.dp(c, 8));
        body.addView(t);
    }

    static LinearLayout card(Context c, LinearLayout body) {
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.rounded(c, R.color.card, 12));
        card.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(c, 8);
        body.addView(card, lp);
        return card;
    }

    static void caption(Context c, LinearLayout body, String text) {
        TextView t = Ui.text(c, 12, R.color.text2, false);
        t.setText(text);
        t.setPadding(Ui.dp(c, 18), Ui.dp(c, 8), Ui.dp(c, 18), 0);
        body.addView(t);
    }

    static LinearLayout rowIn(Context c, LinearLayout card, String label) {
        if (card.getChildCount() > 0) {
            View div = new View(c);
            div.setBackgroundColor(Ui.color(c, R.color.divider));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(c, 0.5f)));
            dl.leftMargin = Ui.dp(c, 18);
            card.addView(div, dl);
        }
        LinearLayout row = Ui.row(c);
        row.setMinimumHeight(Ui.dp(c, 48));
        row.setPadding(Ui.dp(c, 18), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
        TextView l = Ui.text(c, 16, R.color.text, false);
        l.setText(label);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row);
        return row;
    }

    static TextView valueRow(Context c, LinearLayout card, String label, String value, View.OnClickListener click) {
        LinearLayout row = rowIn(c, card, label);
        TextView v = Ui.text(c, 15, R.color.text2, false);
        v.setText(value);
        v.setSingleLine();
        v.setEllipsize(TextUtils.TruncateAt.START);
        v.setGravity(Gravity.END);
        row.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        v.setPadding(Ui.dp(c, 12), 0, 0, 0);
        row.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        if (click != null) {
            ImageView chevron = new ImageView(c);
            chevron.setImageResource(R.drawable.ic_chevron_down);
            chevron.setAlpha(0.6f);
            row.addView(chevron, new LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)));
            row.setOnClickListener(click);
        }
        return v;
    }

    static Switch switchRow(Context c, LinearLayout card, String label, boolean on, java.util.function.Consumer<Boolean> change) {
        LinearLayout row = rowIn(c, card, label);
        Switch s = new Switch(c);
        s.setChecked(on);
        s.setOnCheckedChangeListener((b, v) -> change.accept(v));
        row.addView(s);
        row.setOnClickListener(v -> s.toggle());
        return s;
    }
}
