package com.github.ad.reeder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

public class ArticleActivity extends Activity {
    private long[] ids;
    private int index;
    private Db.Item item;
    private boolean reader;
    private WebView web, fetcher;
    private ImageView placeholder, readBtn, starBtn, readerBtn;
    private String readabilityJs, body, tldr;
    private boolean summarizing;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable readerTimeout = () -> readerFailed("timeout");
    private Runnable extractLater = () -> {
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        App.init(this);
        ids = getIntent().getLongArrayExtra("ids");
        index = state != null ? state.getInt("index") : getIntent().getIntExtra("index", 0);

        LinearLayout root = Ui.column(this);
        root.addView(Ui.topBar(this, Ui.icon(this, R.drawable.ic_close, v -> finish()), null,
                Ui.icon(this, R.drawable.ic_share, v -> share(this, item))));

        PullFrame frame = new PullFrame(this);
        fetcher = new WebView(this);
        fetcher.getSettings().setJavaScriptEnabled(true);
        fetcher.getSettings().setDomStorageEnabled(true);
        fetcher.setVisibility(View.INVISIBLE);
        fetcher.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                if ("about:blank".equals(url)) return;
                long id = item.id;
                handler.removeCallbacks(extractLater);
                extractLater = () -> extract(id);
                // let client-rendered pages finish drawing their content
                handler.postDelayed(extractLater, 1500);
            }
        });
        frame.addView(fetcher, new FrameLayout.LayoutParams(-1, -1));

        web = new WebView(this);
        web.setBackgroundColor(Ui.color(this, R.color.bg));
        web.getSettings().setJavaScriptEnabled(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl()));
                } catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(ArticleActivity.this, "No app to open " + r.getUrl(), Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                web.setVisibility(View.VISIBLE);
                placeholder.setVisibility(View.GONE);
            }
        });
        frame.addView(web, new FrameLayout.LayoutParams(-1, -1));

        placeholder = new ImageView(this);
        placeholder.setImageResource(R.drawable.ic_lines_short);
        placeholder.setAlpha(0.35f);
        frame.addView(placeholder, new FrameLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64), Gravity.CENTER));
        frame.setTarget(web, new PullFrame.Callback() {
            public String neighbor(int dir) {
                int i = index + dir;
                if (i < 0 || i >= ids.length) return null;
                Db.Item it = App.db.item(ids[i]);
                return it == null ? null : it.title;
            }

            public void go(int dir) {
                index += dir;
                load();
            }
        });
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bar = Ui.row(this);
        readBtn = Ui.icon(this, R.drawable.ic_circle, v -> toggle(Db.READ));
        starBtn = Ui.icon(this, R.drawable.ic_star_border, v -> toggle(Db.STARRED));
        readerBtn = Ui.icon(this, R.drawable.ic_reader, v -> {
            reader = !reader;
            show();
        });
        View next = Ui.icon(this, R.drawable.ic_down, v -> {
            if (index + 1 < ids.length) {
                index++;
                load();
            }
        });
        View tldrBtn = Ui.icon(this, R.drawable.ic_lines_short, v -> summarize());
        for (View b : new View[]{readBtn, starBtn, next, tldrBtn, readerBtn}) {
            bar.addView(Ui.space(this, 1));
            bar.addView(b);
            bar.addView(Ui.space(this, 1));
        }
        root.addView(bar);
        Ui.edgeToEdge(this, root);
        load();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("index", index);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        fetcher.destroy();
        web.destroy();
        super.onDestroy();
    }

    private void load() {
        item = App.db.item(ids[index]);
        if (item == null) {
            finish();
            return;
        }
        if (!item.read && App.prefs.getBoolean("mark_open", true)) {
            item.read = true;
            App.sync.setFlag(Collections.singletonList(item.id), Db.READ, true);
        }
        setResult(RESULT_OK, new Intent().putExtra("id", item.id));
        reader = App.readerFor(item.feedId);
        tldr = null;
        updateButtons();
        show();
    }

    private void updateButtons() {
        readBtn.setImageResource(item.read ? R.drawable.ic_circle : R.drawable.ic_dot);
        starBtn.setImageResource(item.starred ? R.drawable.ic_star : R.drawable.ic_star_border);
        readerBtn.setImageResource(reader ? R.drawable.ic_reader_on : R.drawable.ic_reader);
    }

    private void toggle(String tag) {
        boolean on = Db.READ.equals(tag) ? (item.read = !item.read) : (item.starred = !item.starred);
        App.sync.setFlag(Collections.singletonList(item.id), tag, on);
        updateButtons();
    }

    private void show() {
        body = null;
        updateButtons();
        handler.removeCallbacks(readerTimeout);
        handler.removeCallbacks(extractLater);
        fetcher.stopLoading();
        web.setVisibility(View.INVISIBLE);
        placeholder.setVisibility(View.VISIBLE);
        if (!reader) {
            render(App.db.content(item.id));
            return;
        }
        String cached = App.db.reader(item.id);
        if (cached != null) {
            render(cached);
        } else if (item.url == null) {
            readerFailed("no link");
        } else {
            handler.postDelayed(readerTimeout, 25000);
            fetcher.loadUrl(item.url);
        }
    }

    private void extract(long id) {
        if (readabilityJs == null) {
            try {
                readabilityJs = Api.read(getAssets().open("Readability.js"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        fetcher.evaluateJavascript(readabilityJs + "\n;(function(){var a=new Readability(document.cloneNode(true)).parse();"
                + "return a&&a.content?a.content:null})()", res -> {
            if (item == null || item.id != id || !reader) return;
            try {
                String html = res == null || "null".equals(res) ? null : new JSONArray("[" + res + "]").getString(0);
                if (html == null) {
                    readerFailed("nothing found");
                    return;
                }
                handler.removeCallbacks(readerTimeout);
                App.db.setReader(id, html);
                render(html);
            } catch (Exception e) {
                readerFailed(e.getMessage());
            }
        });
    }

    private void readerFailed(String why) {
        if (!reader) return;
        handler.removeCallbacks(readerTimeout);
        fetcher.stopLoading();
        Toast.makeText(this, "Reader view failed: " + why, Toast.LENGTH_SHORT).show();
        reader = false;
        show();
    }

    private void summarize() {
        boolean hide = tldr != null;
        tldr = hide ? null : App.db.tldr(item.id);
        if (hide || tldr != null) {
            if (body != null) render(body);
            return;
        }
        String token = App.prefs.getString("ya300_token", "");
        if (token.isEmpty()) {
            Toast.makeText(this, "Set Yandex 300 token in Settings", Toast.LENGTH_SHORT).show();
            return;
        }
        if (item.url == null) {
            summaryFailed("no link");
            return;
        }
        if (summarizing) return;
        summarizing = true;
        long id = item.id;
        String url = item.url;
        Toast.makeText(this, "Summarizing…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                List<String> theses = Logic.theses(Api.yandex300(token, url));
                if (theses.isEmpty()) throw new IOException("nothing found");
                StringBuilder html = new StringBuilder("<ul>");
                for (String t : theses) html.append("<li>").append(Html.escapeHtml(t)).append("</li>");
                String done = html.append("</ul>").toString();
                App.db.setTldr(id, done);
                runOnUiThread(() -> {
                    summarizing = false;
                    if (isDestroyed() || item == null || item.id != id) return;
                    tldr = done;
                    if (body != null) render(body);
                });
            } catch (Exception e) {
                runOnUiThread(() -> summaryFailed(e.getMessage()));
            }
        }).start();
    }

    private void summaryFailed(String why) {
        summarizing = false;
        if (!isDestroyed()) Toast.makeText(this, "Summary failed: " + why, Toast.LENGTH_SHORT).show();
    }

    private void render(String body) {
        this.body = body;
        String title = Html.escapeHtml(item.title == null ? "" : item.title);
        String link = item.url == null ? title : "<a href=\"" + Html.escapeHtml(item.url) + "\">" + title + "</a>";
        String html = "<html><head><meta name=viewport content=\"width=device-width,initial-scale=1\"><style>"
                + "body{margin:0;padding:20px 22px 64px;background:" + hex(R.color.bg) + ";color:" + hex(R.color.text)
                + ";font-family:sans-serif;font-size:17px;line-height:1.55;overflow-wrap:break-word}"
                + ".meta{font-size:11px;color:" + hex(R.color.text2) + ";text-transform:uppercase;letter-spacing:.03em}"
                + "h1.t{font-size:28px;line-height:1.15;margin:6px 0 4px;font-weight:800}h1.t a{font-weight:800}"
                + ".src{margin-bottom:36px}img,video,iframe,figure{max-width:100%;height:auto}figure{margin:0}"
                + "a{color:" + hex(R.color.text) + ";font-weight:600;text-decoration:none}"
                + "pre{overflow-x:auto;white-space:pre-wrap}table{display:block;overflow-x:auto}"
                + "blockquote{margin:0;padding-left:14px;border-left:3px solid " + hex(R.color.divider) + ";color:" + hex(R.color.text2) + "}"
                + ".tldr{margin:-16px 0 32px;padding:12px 16px;border-radius:12px;background:" + hex(R.color.card) + "}"
                + ".tldr ul{margin:6px 0 0;padding-left:18px}.tldr li{margin:4px 0}"
                + "</style></head><body><div class=meta>" + Logic.articleDate(item.published, TimeZone.getDefault())
                + "</div><h1 class=t>" + link + "</h1><div class=\"meta src\">"
                + Html.escapeHtml(item.feedTitle == null ? "" : item.feedTitle) + "</div>"
                + (tldr == null ? "" : "<div class=tldr><div class=meta>Summary · Yandex 300</div>" + tldr + "</div>")
                + body + "</body></html>";
        web.loadDataWithBaseURL(item.url, html, "text/html", "utf-8", null);
        web.scrollTo(0, 0);
    }

    private String hex(int res) {
        return String.format("#%06X", Ui.color(this, res) & 0xFFFFFF);
    }

    static void share(Activity a, Db.Item it) {
        if (it == null || it.url == null) return;
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, it.title).putExtra(Intent.EXTRA_TEXT, it.url);
        a.startActivity(Intent.createChooser(i, null));
    }
}
