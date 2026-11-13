package com.github.ad.reeder;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;

import java.util.ArrayList;
import java.util.List;

public class ReaderSettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        App.init(this);
        LinearLayout root = Ui.column(this);
        root.addView(Ui.topBar(this, Ui.icon(this, R.drawable.ic_back, v -> finish()), null, null));
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = Ui.column(this);
        body.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Ui.edgeToEdge(this, root);

        SettingsActivity.title(this, body, "Reader View", null);
        List<Db.Feed> feeds = App.db.feeds();
        List<Switch> switches = new ArrayList<>();
        LinearLayout d = SettingsActivity.card(this, body);
        SettingsActivity.switchRow(this, d, "Default", App.prefs.getBoolean("reader_default", false), on -> {
            App.prefs.edit().putBoolean("reader_default", on).apply();
            for (int i = 0; i < feeds.size(); i++) switches.get(i).setChecked(App.readerFor(feeds.get(i).id));
        });
        SettingsActivity.caption(this, body, "Load the full article text from the website instead of the feed content. "
                + "Feeds without their own setting follow the default.");

        SettingsActivity.section(this, body, "FEEDS");
        LinearLayout card = SettingsActivity.card(this, body);
        for (Db.Feed f : feeds) {
            Switch s = SettingsActivity.switchRow(this, card, f.title, App.readerFor(f.id), on -> {
                if (on != App.readerFor(f.id)) App.prefs.edit().putBoolean("reader:" + f.id, on).apply();
            });
            LinearLayout row = (LinearLayout) s.getParent();
            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20));
            lp.rightMargin = Ui.dp(this, 10);
            row.addView(icon, 0, lp);
            Icons.load(icon, f.iconUrl, f.siteUrl);
            switches.add(s);
        }
    }
}
