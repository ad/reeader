package com.github.ad.reeder;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** In-app browser for article links. */
public class WebActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        App.init(this);
        LinearLayout root = Ui.column(this);
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        bar.addView(Ui.icon(this, R.drawable.ic_close, v -> finish()));
        TextView title = Ui.text(this, 15, R.color.text, true);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(Ui.icon(this, R.drawable.ic_open, v -> Browser.external(this, Uri.parse(web.getUrl()))));
        root.addView(bar);

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(progress, new LinearLayout.LayoutParams(-1, Ui.dp(this, 2)));

        web = new WebView(this);
        web.setBackgroundColor(Ui.color(this, R.color.bg));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String scheme = r.getUrl().getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                Browser.external(WebActivity.this, r.getUrl()); // mailto:, intent:, app links
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap icon) {
                title.setText(Uri.parse(url).getHost());
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                progress.setProgress(p);
                progress.setVisibility(p < 100 ? View.VISIBLE : View.INVISIBLE);
            }

            @Override
            public void onReceivedTitle(WebView v, String t) {
                if (!TextUtils.isEmpty(t)) title.setText(t);
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        Ui.edgeToEdge(this, root);
        if (state == null || web.restoreState(state) == null) web.loadUrl(getIntent().getDataString());
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
