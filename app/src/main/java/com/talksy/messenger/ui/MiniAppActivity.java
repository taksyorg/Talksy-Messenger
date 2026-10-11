package com.talksy.messenger.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;

public class MiniAppActivity extends BaseActivity {
    public static final String EXTRA_RESTORE = "restore";

    private MiniAppHost.Session s;
    private FrameLayout container;
    private View loading, errorView;
    private ProgressBar progress;
    private TextView txtError;
    private boolean keepAlive, closed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mini_app);

        container = findViewById(R.id.web_container);
        loading = findViewById(R.id.loading_view);
        errorView = findViewById(R.id.error_view);
        progress = findViewById(R.id.progress);
        txtError = findViewById(R.id.txt_error);
        TextView txtTitle = findViewById(R.id.txt_title);
        TextView txtOrigin = findViewById(R.id.txt_origin);
        ImageButton btnClose = findViewById(R.id.btn_close), btnMin = findViewById(R.id.btn_minimize), btnMenu = findViewById(R.id.btn_menu);
        findViewById(R.id.img_verified).setVisibility(View.VISIBLE);

        boolean restore = getIntent().getBooleanExtra(EXTRA_RESTORE, false);
        String url = getIntent().getStringExtra("url"), sessionId = getIntent().getStringExtra("session_id");
        boolean fresh = false;
        if (restore && MiniAppHost.hasSession()) {
            s = MiniAppHost.get();
        } else {
            if (url == null || sessionId == null || !(url.startsWith("https://") || url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1"))) {
                Toast.makeText(this, R.string.miniapp_invalid, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (MiniAppHost.matches(url, sessionId)) s = MiniAppHost.get();
            else { s = MiniAppHost.create(getApplication(), url, sessionId, getIntent().getStringExtra("title"), getIntent().getStringExtra("bot_username")); fresh = true; }
        }

        txtTitle.setText(s.title == null || s.title.isEmpty() ? getString(R.string.miniapp_default_title) : s.title);
        txtOrigin.setText(s.originHost);
        btnClose.setOnClickListener(v -> closeAndFinish());
        btnMin.setOnClickListener(v -> minimizeAndFinish());
        btnMenu.setOnClickListener(v -> showMenu(btnMenu));
        AnimUtils.pressScale(btnClose); AnimUtils.pressScale(btnMin); AnimUtils.pressScale(btnMenu);

        s.web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri dest = request.getUrl();
                if (s.originHost != null && s.originHost.equalsIgnoreCase(dest.getHost())) return false;
                LinkRouter.open(MiniAppActivity.this, dest.toString());
                return true;
            }
            @Override public void onPageStarted(WebView view, String u, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, u, favicon);
                errorView.setVisibility(View.GONE);
                view.evaluateJavascript(TALKSY_BRIDGE_JS, null);
            }
            @Override public void onPageFinished(WebView view, String u) { hideLoading(); }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    hideLoading();
                    errorView.setVisibility(View.VISIBLE);
                    txtError.setText(getString(R.string.miniapp_load_error, String.valueOf(s.originHost)));
                }
            }
        });
        s.web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
                if (p >= 70) hideLoading();
            }
        });
        MiniAppHost.attach(s, this, container);

        if (fresh) {
            loading.animate().alpha(0.35f).setDuration(700).withEndAction(this::pulse).start();
            s.web.loadUrl(s.url + (s.url.contains("?") ? "&" : "?") + "tsq_boot=1");
        } else {
            loading.setVisibility(View.GONE);
        }
    }

    private void pulse() {
        if (loading.getVisibility() != View.VISIBLE) return;
        loading.animate().alpha(loading.getAlpha() < 0.6f ? 1f : 0.35f).setDuration(700).withEndAction(this::pulse).start();
    }

    private void hideLoading() {
        if (loading.getVisibility() != View.VISIBLE) return;
        loading.animate().cancel();
        loading.animate().alpha(0f).setDuration(180).withEndAction(() -> loading.setVisibility(View.GONE)).start();
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, R.string.link_reload);
        pm.getMenu().add(0, 2, 1, R.string.link_copy);
        pm.setOnMenuItemClickListener(it -> {
            if (it.getItemId() == 1) { loading.setAlpha(1f); loading.setVisibility(View.VISIBLE); s.web.reload(); }
            else LinkRouter.copy(this, s.url);
            return true;
        });
        pm.show();
    }

    public void closeAndFinish() {
        if (closed) return;
        closed = true;
        MiniAppHost.detach(s, container);
        MiniAppHost.close();
        finish();
        AnimUtils.back(this);
    }

    public void minimizeAndFinish() {
        if (closed) return;
        closed = true; keepAlive = true;
        s.web.setWebViewClient(new WebViewClient());
        s.web.setWebChromeClient(null);
        MiniAppHost.detach(s, container);
        MiniAppHost.setMinimized(true);
        finish();
        AnimUtils.back(this);
    }

    private static final String TALKSY_BRIDGE_JS =
            "window.Talksy = window.Talksy || {" +
            "  getUser: function(){ return Promise.resolve(JSON.parse(TalksyNative.getUser())); }," +
            "  getBalance: function(){ return Promise.resolve(JSON.parse(TalksyNative.getBalance())); }," +
            "  pay: function(opts){ return Promise.resolve(JSON.parse(TalksyNative.pay(JSON.stringify(opts||{})))); }," +
            "  searchUsers: function(q){ return Promise.resolve(JSON.parse(TalksyNative.searchUsers(q||''))); }," +
            "  transfer: function(opts){ return Promise.resolve(JSON.parse(TalksyNative.transfer(JSON.stringify(opts||{})))); }," +
            "  history: function(){ return Promise.resolve(JSON.parse(TalksyNative.history())); },"+
            "  sendMessage: function(opts){ return Promise.resolve(JSON.parse(TalksyNative.sendMessage(JSON.stringify(opts||{})))); }," +
            "  openChat: function(){ TalksyNative.openChat(); }," +
            "  close: function(){ TalksyNative.close(); }" +
            "};" +
            "window.dispatchEvent(new Event('talksy-ready'));";

    @Override
    public void onBackPressed() {
        if (s != null && s.web != null && s.web.canGoBack()) s.web.goBack();
        else closeAndFinish();
    }

    @Override
    protected void onDestroy() {
        if (!closed && !keepAlive && !isChangingConfigurations() && s != null) {
            try { MiniAppHost.detach(s, container); } catch (Exception ignored) {}
            MiniAppHost.close();
        } else if (s != null && !keepAlive && s.screen.get() == this) {
            s.screen.clear();
        }
        super.onDestroy();
    }
}
