package com.talksy.messenger.ui;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.ArticleHtml;
import com.talksy.messenger.ui.widget.SettingsUi;

import org.json.JSONObject;

public class ArticleViewActivity extends BaseActivity {
    public static final String EXTRA_JSON = "article_json";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        JSONObject article;
        try { article = new JSONObject(getIntent().getStringExtra(EXTRA_JSON)); } catch (Exception e) { finish(); return; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(SettingsUi.dp(this, 8), 0, SettingsUi.dp(this, 8), 0);
        ImageButton back = new ImageButton(this);
        back.setBackgroundResource(R.drawable.ripple_item_round);
        back.setImageResource(R.drawable.ic_back);
        back.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        bar.addView(back, new LinearLayout.LayoutParams(SettingsUi.dp(this, 44), SettingsUi.dp(this, 44)));
        TextView t = new TextView(this);
        t.setText(R.string.article_title); t.setTextSize(18); t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(ContextCompat.getColor(this, R.color.text1));
        bar.addView(t);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 56)));

        WebView web = new WebView(this);
        WebSettings st = web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setAllowFileAccess(false);
        st.setAllowContentAccess(false);
        st.setBlockNetworkLoads(true);
        st.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setBackgroundColor(0);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
        });
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        web.loadDataWithBaseURL("file:///android_asset/katex/", ArticleHtml.build(article, dark), "text/html", "utf-8", null);
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }
}
