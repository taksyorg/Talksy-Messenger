package com.talksy.messenger.ui;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;

import java.util.Locale;

public class InAppBrowserActivity extends BaseActivity {
    public static final String EXTRA_URL = "url";

    private WebView web;
    private String current;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mini_app);

        String url = LinkRouter.normalize(getIntent().getStringExtra(EXTRA_URL));
        if (url == null || !(url.toLowerCase(Locale.ROOT).startsWith("http://") || url.toLowerCase(Locale.ROOT).startsWith("https://"))) { finish(); return; }
        current = url;

        FrameLayout container = findViewById(R.id.web_container);
        View loading = findViewById(R.id.loading_view), errorView = findViewById(R.id.error_view);
        ProgressBar progress = findViewById(R.id.progress);
        TextView txtTitle = findViewById(R.id.txt_title), txtOrigin = findViewById(R.id.txt_origin), txtError = findViewById(R.id.txt_error);
        ImageButton btnClose = findViewById(R.id.btn_close), btnMenu = findViewById(R.id.btn_menu);
        findViewById(R.id.btn_minimize).setVisibility(View.GONE);

        txtTitle.setText(Uri.parse(url).getHost());
        txtOrigin.setText(url);
        btnClose.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        btnMenu.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, btnMenu);
            pm.getMenu().add(0, 1, 0, R.string.link_reload);
            pm.getMenu().add(0, 2, 1, R.string.link_open_in_browser);
            pm.getMenu().add(0, 3, 2, R.string.link_copy);
            pm.getMenu().add(0, 4, 3, R.string.link_share);
            pm.setOnMenuItemClickListener(it -> {
                switch (it.getItemId()) {
                    case 1: web.reload(); break;
                    case 2: LinkRouter.openExternal(this, current); break;
                    case 3: LinkRouter.copy(this, current); break;
                    default: LinkRouter.share(this, current);
                }
                return true;
            });
            pm.show();
        });
        AnimUtils.pressScale(btnClose); AnimUtils.pressScale(btnMenu);

        web = new WebView(this);
        container.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        WebSettings st = web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setAllowFileAccess(false);
        st.setAllowContentAccess(false);
        st.setAllowFileAccessFromFileURLs(false);
        st.setAllowUniversalAccessFromFileURLs(false);
        st.setGeolocationEnabled(false);
        st.setSaveFormData(false);
        st.setSupportMultipleWindows(false);
        web.setDownloadListener((u, ua, cd, mime, len) -> LinkRouter.openExternal(this, u));

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                String low = u.toLowerCase(Locale.ROOT);
                if (low.startsWith("http://") || low.startsWith("https://")) return false;
                LinkRouter.open(InAppBrowserActivity.this, u);
                return true;
            }
            @Override public void onPageStarted(WebView view, String u, android.graphics.Bitmap favicon) {
                current = u; txtOrigin.setText(u);
                errorView.setVisibility(View.GONE);
            }
            @Override public void onPageFinished(WebView view, String u) {
                loading.setVisibility(View.GONE);
                String t = view.getTitle();
                if (t != null && !t.isEmpty()) txtTitle.setText(t);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    loading.setVisibility(View.GONE);
                    errorView.setVisibility(View.VISIBLE);
                    txtError.setText(getString(R.string.link_load_error, String.valueOf(Uri.parse(current).getHost())));
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
                if (p >= 60) loading.setVisibility(View.GONE);
            }
            @Override public void onReceivedTitle(WebView view, String title) { if (title != null && !title.isEmpty()) txtTitle.setText(title); }
        });
        web.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else { super.onBackPressed(); AnimUtils.back(this); }
    }

    @Override
    protected void onDestroy() {
        if (web != null) { ((FrameLayout) web.getParent()).removeView(web); web.destroy(); web = null; }
        super.onDestroy();
    }
}
