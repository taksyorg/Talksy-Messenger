package com.talksy.messenger.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;

public class PayWebActivity extends BaseActivity {
    public static final String EXTRA_URL = "url";
    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(EconomyUi.color(this, R.color.bg));

        LinearLayout bar = EconomyUi.row(this);
        bar.setBackgroundColor(EconomyUi.color(this, R.color.bg2));
        bar.setPadding(EconomyUi.dp(this, 6), 0, 0, 0);
        bar.setLayoutParams(new LinearLayout.LayoutParams(-1, EconomyUi.dp(this, 58)));
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setBackgroundResource(R.drawable.ripple_item_round);
        back.setLayoutParams(new LinearLayout.LayoutParams(EconomyUi.dp(this, 40), EconomyUi.dp(this, 40)));
        back.setOnClickListener(v -> close());
        bar.addView(back);
        android.widget.TextView t = EconomyUi.text(this, "Оплата", 16, R.color.text1, true);
        t.setPadding(EconomyUi.dp(this, 8), 0, 0, 0);
        bar.addView(t);
        root.addView(bar);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setLayoutParams(new LinearLayout.LayoutParams(-1, EconomyUi.dp(this, 3)));
        root.addView(pb);

        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) { pb.setVisibility(View.GONE); }
        });
        web.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(web);
        setContentView(root);

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null) { finish(); return; }
        web.loadUrl(url);
    }

    private void close() {
        setResult(RESULT_OK);
        finish();
        AnimUtils.back(this);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else close();
    }

    @Override protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
