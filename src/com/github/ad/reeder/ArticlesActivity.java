package com.github.ad.reeder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ArticlesActivity extends Activity implements Sync.Listener {
    private final List<Db.Item> items = new ArrayList<>();
    private final List<Object> rows = new ArrayList<>();
    private String feedId, query;
    private SwipeListView list;
    private TextView status;
    private View refresh;
    private EditText search;
    private int lastFirst;
    private boolean syncing, userScrolling;

    private final BaseAdapter adapter = new BaseAdapter() {
        public int getCount() {
            return rows.size();
        }

        public Object getItem(int p) {
            return rows.get(p);
        }

        public long getItemId(int p) {
            return p;
        }

        public int getViewTypeCount() {
            return 2;
        }

        public int getItemViewType(int p) {
            return rows.get(p) instanceof String ? 0 : 1;
        }

        public boolean isEnabled(int p) {
            return rows.get(p) instanceof Db.Item;
        }

        public View getView(int p, View v, ViewGroup parent) {
            Object r = rows.get(p);
            return r instanceof String ? dayHeader((String) r, v) : itemRow((Db.Item) r, v);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        App.init(this);
        feedId = getIntent().getStringExtra("feed");
        LinearLayout root = Ui.column(this);
        refresh = Ui.refreshButton(this, v -> App.sync.start());
        root.addView(Ui.topBar(this, Ui.icon(this, R.drawable.ic_back, v -> finish()), refresh, null));

        search = new EditText(this);
        search.setHint("Search");
        search.setSingleLine();
        search.setTextColor(Ui.color(this, R.color.text));
        search.setHintTextColor(Ui.color(this, R.color.text2));
        search.setBackground(Ui.rounded(this, R.color.highlight, 10));
        search.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                reload();
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 8));
        root.addView(search, slp);

        list = new SwipeListView(this);
        LinearLayout header = Ui.column(this);
        header.setPadding(Ui.dp(this, 50), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 8));
        TextView title = Ui.text(this, 22, R.color.text, true);
        title.setText(getIntent().getStringExtra("title"));
        header.addView(title);
        status = Ui.text(this, 12, R.color.text2, true);
        status.setSingleLine();
        status.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(status);
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
                toggle((Db.Item) list.getItemAtPosition(position), right);
            }
        });
        list.setOnItemClickListener((a, v, pos, id) -> open((Db.Item) a.getItemAtPosition(pos)));
        list.setOnItemLongClickListener((a, v, pos, id) -> {
            Object o = a.getItemAtPosition(pos);
            if (!(o instanceof Db.Item)) return false;
            showMenu((Db.Item) o, v);
            return true;
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView v, int s) {
                userScrolling = s != SCROLL_STATE_IDLE;
            }

            public void onScroll(AbsListView v, int first, int visible, int total) {
                if (userScrolling && first > lastFirst && App.prefs.getBoolean("mark_scroll", false)) markPassed(lastFirst, first);
                lastFirst = first;
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(new FilterBar(this,
                Ui.icon(this, R.drawable.ic_search, v -> toggleSearch()),
                Ui.icon(this, R.drawable.ic_check, v -> markAll()),
                f -> reload()));
        Ui.edgeToEdge(this, root);
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        App.sync.addListener(this);
        syncing = App.sync.running();
        App.db.refreshFlags(items);
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        App.sync.removeListener(this);
    }

    @Override
    public void onBackPressed() {
        if (search.getVisibility() == View.VISIBLE) toggleSearch();
        else super.onBackPressed();
    }

    @Override
    public void onSyncState(boolean running) {
        syncing = running;
        if (!running) reload();
        updateStatus();
    }

    private void reload() {
        items.clear();
        items.addAll(App.db.items(feedId, App.filter(), query));
        rows.clear();
        TimeZone tz = TimeZone.getDefault();
        long now = System.currentTimeMillis();
        String day = null;
        for (Db.Item it : items) {
            String d = Logic.dayLabel(it.published, now, tz);
            if (!d.equals(day)) rows.add(day = d);
            rows.add(it);
        }
        adapter.notifyDataSetChanged();
        lastFirst = list.getFirstVisiblePosition();
        updateStatus();
    }

    private void updateStatus() {
        Ui.spin(refresh, syncing);
        if (syncing) {
            status.setText("Syncing…");
            return;
        }
        if (App.sync.error() != null) {
            status.setText("Sync failed: " + App.sync.error());
            return;
        }
        int filter = App.filter(), n = 0;
        for (Db.Item it : items) if (filter == App.STARRED ? it.starred : filter == App.ALL || !it.read) n++;
        status.setText(n + (filter == App.STARRED ? " Starred Items" : filter == App.UNREAD ? " Unread Items" : " Items"));
    }

    private void open(Db.Item it) {
        long[] ids = new long[items.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = items.get(i).id;
        startActivityForResult(new Intent(this, ArticleActivity.class).putExtra("ids", ids).putExtra("index", items.indexOf(it)), 1);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (data == null) return;
        long id = data.getLongExtra("id", -1);
        for (int p = 0; p < rows.size(); p++) {
            if (rows.get(p) instanceof Db.Item && ((Db.Item) rows.get(p)).id == id) {
                int pos = p + list.getHeaderViewsCount();
                if (pos < list.getFirstVisiblePosition() || pos > list.getLastVisiblePosition()) list.setSelectionFromTop(pos, list.getHeight() / 3);
                return;
            }
        }
    }

    private void toggle(Db.Item it, boolean read) {
        if (read) it.read = !it.read;
        else it.starred = !it.starred;
        App.sync.setFlag(Collections.singletonList(it.id), read ? Db.READ : Db.STARRED, read ? it.read : it.starred);
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void showMenu(Db.Item it, View anchor) {
        PopupMenu p = new PopupMenu(this, anchor);
        Menu m = p.getMenu();
        m.add(0, 1, 0, it.read ? "Unread" : "Read");
        m.add(0, 2, 0, it.starred ? "Unstar" : "Star");
        m.add(1, 3, 0, "Mark above as read");
        m.add(1, 4, 0, "Mark below as read");
        m.add(2, 5, 0, "Share");
        if (Build.VERSION.SDK_INT >= 28) m.setGroupDividerEnabled(true);
        p.setOnMenuItemClickListener(mi -> {
            int idx = rows.indexOf(it), pos = idx + list.getHeaderViewsCount();
            switch (mi.getItemId()) {
                case 1: toggle(it, true); break;
                case 2: toggle(it, false); break;
                case 3: if (idx >= 0) markPassed(0, pos); break;
                case 4: if (idx >= 0) markPassed(pos + 1, list.getCount()); break;
                default: ArticleActivity.share(this, it);
            }
            return true;
        });
        p.show();
    }

    private void markPassed(int from, int to) {
        List<Long> ids = new ArrayList<>();
        for (int p = from; p < to; p++) {
            Object o = list.getItemAtPosition(p);
            if (o instanceof Db.Item && !((Db.Item) o).read) {
                ((Db.Item) o).read = true;
                ids.add(((Db.Item) o).id);
            }
        }
        if (ids.isEmpty()) return;
        App.sync.setFlag(ids, Db.READ, true);
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void markAll() {
        List<Long> ids = new ArrayList<>();
        for (Db.Item it : items) if (!it.read) ids.add(it.id);
        if (ids.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setMessage("Mark " + ids.size() + " items as read?")
                .setPositiveButton("Mark as Read", (d, w) -> {
                    for (Db.Item it : items) it.read = true;
                    App.sync.setFlag(ids, Db.READ, true);
                    adapter.notifyDataSetChanged();
                    updateStatus();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleSearch() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (search.getVisibility() == View.VISIBLE) {
            search.setText("");
            search.setVisibility(View.GONE);
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        } else {
            search.setVisibility(View.VISIBLE);
            search.requestFocus();
            imm.showSoftInput(search, 0);
        }
    }

    private View dayHeader(String label, View v) {
        TextView t = (TextView) v;
        if (t == null) {
            t = Ui.text(this, 13, R.color.text, true);
            t.setLetterSpacing(0.02f);
            t.setPadding(Ui.dp(this, 50), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 6));
        }
        t.setText(label);
        return t;
    }

    private View itemRow(Db.Item it, View v) {
        SwipeListView.Row row = (SwipeListView.Row) v;
        if (row == null) {
            LinearLayout card = Ui.row(this);
            card.setBackground(getDrawable(R.drawable.row_bg));
            card.setDuplicateParentStateEnabled(true);
            card.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18));
            ilp.rightMargin = Ui.dp(this, 8);
            card.addView(icon, ilp);
            LinearLayout col = Ui.column(this);
            LinearLayout top = Ui.row(this);
            TextView feed = Ui.text(this, 11, R.color.text2, false);
            feed.setSingleLine();
            feed.setEllipsize(TextUtils.TruncateAt.END);
            top.addView(feed, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView star = new ImageView(this);
            star.setImageResource(R.drawable.ic_star);
            star.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.accent)));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12));
            slp.rightMargin = Ui.dp(this, 4);
            top.addView(star, slp);
            top.addView(Ui.text(this, 12, R.color.text, true));
            col.addView(top);
            LinearLayout body = new LinearLayout(this);
            LinearLayout text = Ui.column(this);
            TextView title = Ui.text(this, 16, R.color.text, true);
            title.setMaxLines(3);
            title.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(title);
            TextView summary = Ui.text(this, 14, R.color.text2, false);
            summary.setSingleLine();
            summary.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(summary);
            body.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackground(Ui.rounded(this, R.color.highlight, 6));
            thumb.setClipToOutline(true);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 60));
            tlp.leftMargin = Ui.dp(this, 10);
            tlp.topMargin = Ui.dp(this, 3);
            body.addView(thumb, tlp);
            col.addView(body);
            card.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout front = new LinearLayout(this);
            front.setDuplicateParentStateEnabled(true);
            front.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
            front.addView(card, new LinearLayout.LayoutParams(-1, -2));
            row = new SwipeListView.Row(this, front);
            row.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
        }
        LinearLayout card = (LinearLayout) ((LinearLayout) row.front).getChildAt(0);
        LinearLayout col = (LinearLayout) card.getChildAt(1);
        LinearLayout top = (LinearLayout) col.getChildAt(0);
        Icons.load((ImageView) card.getChildAt(0), it.iconUrl, it.siteUrl);
        ((TextView) top.getChildAt(0)).setText(it.feedTitle == null ? "" : it.feedTitle.toUpperCase(Locale.getDefault()));
        top.getChildAt(1).setVisibility(it.starred ? View.VISIBLE : View.GONE);
        TextView time = (TextView) top.getChildAt(2);
        time.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.published));
        LinearLayout body = (LinearLayout) col.getChildAt(1);
        LinearLayout text = (LinearLayout) body.getChildAt(0);
        Icons.thumb((ImageView) body.getChildAt(1), it.image);
        TextView title = (TextView) text.getChildAt(0);
        title.setText(it.title);
        title.setTypeface(it.read ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
        title.setTextColor(Ui.color(this, it.read ? R.color.text2 : R.color.text));
        time.setTextColor(Ui.color(this, it.read ? R.color.text2 : R.color.text));
        TextView summary = (TextView) text.getChildAt(1);
        summary.setText(it.summary);
        summary.setVisibility(TextUtils.isEmpty(it.summary) ? View.GONE : View.VISIBLE);
        row.setIcons(it.read ? R.drawable.ic_dot : R.drawable.ic_circle, it.starred ? R.drawable.ic_star_border : R.drawable.ic_star);
        return row;
    }
}
