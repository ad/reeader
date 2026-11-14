package com.github.ad.reeder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class MainActivity extends Activity implements Sync.Listener {
    private static final int AGGREGATE = 0, SECTION = 1, FEED = 2, GAP = 3, FOLDERS = 4;

    private static final class Entry {
        int type;
        String title, category;
        Db.Feed feed;
        int count;
    }

    private final List<Entry> entries = new ArrayList<>();
    private final java.util.Set<String> shown = new java.util.HashSet<>();
    private int shownFilter = -1;
    private boolean alpha;
    private FilterBar filterBar;
    private SwipeListView list;
    private TextView status;
    private View refresh;
    private final BaseAdapter adapter = new BaseAdapter() {
        public int getCount() {
            return entries.size();
        }

        public Object getItem(int p) {
            return entries.get(p);
        }

        public long getItemId(int p) {
            return p;
        }

        public int getViewTypeCount() {
            return 5;
        }

        public int getItemViewType(int p) {
            return entries.get(p).type;
        }

        public boolean isEnabled(int p) {
            return entries.get(p).type != GAP;
        }

        public View getView(int p, View v, ViewGroup parent) {
            return bind(entries.get(p), v);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        App.init(this);
        if (!App.loggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        LinearLayout root = Ui.column(this);
        refresh = Ui.refreshButton(this, v -> App.sync.start());
        root.addView(Ui.topBar(this,
                Ui.icon(this, R.drawable.ic_back, v -> startActivity(new Intent(this, SettingsActivity.class))),
                refresh, null));

        list = new SwipeListView(this);
        LinearLayout header = Ui.column(this);
        header.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 10));
        TextView title = Ui.text(this, 34, R.color.text, true);
        title.setText("FreshRSS");
        header.addView(title);
        status = Ui.text(this, 13, R.color.text2, true);
        status.setSingleLine();
        status.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(status);
        View line = new View(this);
        line.setBackgroundColor(Ui.color(this, R.color.divider));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Ui.dp(this, 170), Ui.dp(this, 1));
        llp.gravity = Gravity.CENTER_HORIZONTAL;
        llp.topMargin = Ui.dp(this, 12);
        header.addView(line, llp);
        list.addHeaderView(header, null, false);
        list.setAdapter(adapter);
        list.setCallback(new SwipeListView.Callback() {
            public void onRefresh() {
                App.sync.start();
            }

            public void onPull(float progress) {
                Ui.pull(refresh, progress);
            }

            public void onSwipe(int position, boolean right) {
            }
        });
        list.setOnItemClickListener((a, v, pos, id) -> open((Entry) a.getItemAtPosition(pos)));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        filterBar = new FilterBar(this, null, null, f -> reload(true));
        root.addView(filterBar);
        Ui.edgeToEdge(this, root);

        if (state == null && !"manual".equals(App.prefs.getString("sync_mode", "start"))) App.sync.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list == null) return;
        App.sync.addListener(this);
        Update.check(this);
        filterBar.select(App.filter());
        showStatus(App.sync.running());
        reload(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (list != null) App.sync.removeListener(this);
    }

    @Override
    public void onSyncState(boolean running) {
        showStatus(running);
        if (!running) reload(true);
    }

    private void showStatus(boolean running) {
        String err = App.sync.error();
        long last = App.prefs.getLong("last_sync", 0);
        status.setText(running ? "Syncing…" : err != null ? "Sync failed: " + err
                : last > 0 ? Logic.syncLabel(last, System.currentTimeMillis(), TimeZone.getDefault()) : "");
        Ui.spin(refresh, running);
    }

    private void reload(boolean reset) {
        int filter = App.filter();
        if (reset || filter != shownFilter) shown.clear();
        shownFilter = filter;
        Map<String, Integer> unread = App.db.counts(false);
        Map<String, Integer> counts = filter == App.STARRED ? App.db.counts(true) : unread;
        entries.clear();
        Entry agg = new Entry();
        agg.type = AGGREGATE;
        agg.title = filter == App.STARRED ? "Starred" : filter == App.UNREAD ? "Unread" : "All Items";
        for (int n : counts.values()) agg.count += n;
        entries.add(agg);
        Entry gap = new Entry();
        gap.type = GAP;
        entries.add(gap);

        List<Db.Feed> feeds = App.db.feeds();
        alpha = "alpha".equals(App.prefs.getString("sort", "folders"));
        if (alpha) Collections.sort(feeds, (a, b) -> a.title.compareToIgnoreCase(b.title));
        Entry folders = new Entry();
        folders.type = FOLDERS;
        folders.title = alpha ? "Feeds" : "Folders";
        entries.add(folders);
        boolean hideAll = App.prefs.getBoolean("folders_collapsed", false);
        String current = null;
        Entry section = null;
        for (Db.Feed f : feeds) {
            Integer n = counts.get(f.id);
            if (filter != App.ALL && n == null && !shown.contains(f.id)) continue;
            shown.add(f.id);
            String cat = f.category;
            if (!alpha && !cat.equals(current)) {
                current = cat;
                section = new Entry();
                section.type = SECTION;
                section.title = cat;
                if (!hideAll) entries.add(section);
            }
            if (section != null && n != null) section.count += n;
            if (hideAll || !alpha && App.prefs.getBoolean("collapsed:" + cat, false)) continue;
            Entry e = new Entry();
            e.type = FEED;
            e.feed = f;
            e.count = n == null ? 0 : n;
            entries.add(e);
        }
        adapter.notifyDataSetChanged();
    }

    private void open(Entry e) {
        if (e.type == SECTION || e.type == FOLDERS) {
            String key = e.type == FOLDERS ? "folders_collapsed" : "collapsed:" + e.title;
            App.prefs.edit().putBoolean(key, !App.prefs.getBoolean(key, false)).apply();
            reload(false);
            return;
        }
        Intent i = new Intent(this, ArticlesActivity.class);
        if (e.type == FEED) {
            i.putExtra("feed", e.feed.id);
            i.putExtra("title", e.feed.title);
        } else {
            i.putExtra("title", e.title);
        }
        startActivity(i);
    }

    private View bind(Entry e, View v) {
        if (e.type == GAP) {
            if (v == null) {
                v = new View(this);
                v.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, Ui.dp(this, 12)));
            }
            return v;
        }
        if (v == null) {
            LinearLayout row = Ui.row(this);
            row.setBackground(getDrawable(R.drawable.row_bg));
            row.setDuplicateParentStateEnabled(true);
            ImageView fold = new ImageView(this);
            fold.setImageResource(R.drawable.ic_chevron_down);
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18));
            flp.rightMargin = Ui.dp(this, 6);
            row.addView(fold, flp);
            ImageView icon = new ImageView(this);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20));
            ilp.rightMargin = Ui.dp(this, 6);
            row.addView(icon, ilp);
            boolean big = e.type == AGGREGATE || e.type == FOLDERS;
            TextView title = Ui.text(this, big ? 18 : 16, R.color.text, e.type != FEED);
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            TextView count = Ui.text(this, 15, R.color.text2, false);
            row.addView(count);
            ImageView chevron = new ImageView(this);
            chevron.setImageResource(R.drawable.ic_chevron_down);
            row.addView(chevron, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
            LinearLayout wrap = new LinearLayout(this);
            wrap.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
            wrap.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(this, e.type == AGGREGATE ? 44 : e.type == FOLDERS ? 36 : 34)));
            v = wrap;
        }
        LinearLayout row = (LinearLayout) ((LinearLayout) v).getChildAt(0);
        ImageView fold = (ImageView) row.getChildAt(0);
        ImageView icon = (ImageView) row.getChildAt(1);
        TextView title = (TextView) row.getChildAt(2);
        TextView count = (TextView) row.getChildAt(3);
        ImageView chevron = (ImageView) row.getChildAt(4);
        int left = e.type == SECTION ? 6 : e.type == FEED && !alpha ? 30 : 8;
        row.setPadding(Ui.dp(this, left), 0, Ui.dp(this, e.type == FOLDERS ? 2 : 8), 0);
        fold.setVisibility(e.type == SECTION ? View.VISIBLE : View.GONE);
        icon.setVisibility(e.type == FEED ? View.VISIBLE : View.GONE);
        chevron.setVisibility(e.type == FOLDERS ? View.VISIBLE : View.GONE);
        count.setVisibility(e.type == FOLDERS ? View.GONE : View.VISIBLE);
        title.setText(e.type == FEED ? e.feed.title : e.title);
        if (e.type == SECTION) fold.setRotation(App.prefs.getBoolean("collapsed:" + e.title, false) ? -90 : 0);
        if (e.type == FOLDERS) chevron.setRotation(App.prefs.getBoolean("folders_collapsed", false) ? -90 : 0);
        if (e.type != FOLDERS) count.setText(e.count > 0 ? String.format("%,d", e.count) : "");
        if (e.type == FEED) Icons.load(icon, e.feed.iconUrl, e.feed.siteUrl);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
}
