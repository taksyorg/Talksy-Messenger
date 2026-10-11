package com.talksy.messenger.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.ui.widget.SettingsUi;

import java.io.File;

public class ExperimentsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(SettingsUi.dp(this, 6), 0, 0, 0);
        bar.setBackgroundColor(ContextCompat.getColor(this, R.color.bg2));
        bar.setElevation(SettingsUi.dp(this, 2));
        ImageButton back = new ImageButton(this);
        back.setBackgroundResource(R.drawable.ripple_item_round);
        back.setImageResource(R.drawable.ic_back);
        back.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        AnimUtils.pressScale(back);
        bar.addView(back, new LinearLayout.LayoutParams(SettingsUi.dp(this, 40), SettingsUi.dp(this, 40)));
        TextView title = new TextView(this);
        title.setText(R.string.experiments_title);
        title.setTextColor(ContextCompat.getColor(this, R.color.text1));
        title.setTextSize(16);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart(SettingsUi.dp(this, 8));
        bar.addView(title, tlp);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 58)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = SettingsUi.dp(this, 14);
        content.setPadding(pad, pad, pad, pad * 2);
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        content.addView(SettingsUi.header(this, getString(R.string.exp_tools).toUpperCase()));
        LinearLayout tools = SettingsUi.card(this);
        tools.addView(SettingsUi.styledRow(this, "sync", getString(R.string.exp_update), getString(R.string.exp_update_sub), null,
                v -> UpdateChecker.check(this, true)).view);
        SettingsUi.divider(tools);
        tools.addView(SettingsUi.styledRow(this, "server_error", getString(R.string.exp_ping), getString(R.string.exp_ping_sub), null,
                v -> EasterEggs.ping(this)).view);
        SettingsUi.divider(tools);
        tools.addView(SettingsUi.styledRow(this, "contact_card", getString(R.string.exp_me), getString(R.string.exp_me_sub), null,
                v -> EasterEggs.me(this)).view);
        SettingsUi.divider(tools);
        tools.addView(SettingsUi.styledRow(this, "delete", getString(R.string.exp_cache), getString(R.string.exp_cache_sub), null,
                v -> clearCache()).view);
        content.addView(tools);

        content.addView(SettingsUi.header(this, getString(R.string.exp_effects).toUpperCase()));
        LinearLayout fx = SettingsUi.card(this);
        fx.addView(SettingsUi.styledRow(this, "mini_app", getString(R.string.exp_matrix), null, null, v -> EasterEggs.matrix(this)).view);
        SettingsUi.divider(fx);
        fx.addView(SettingsUi.styledRow(this, "gift", getString(R.string.exp_confetti), null, null, v -> EasterEggs.confetti(this)).view);
        SettingsUi.divider(fx);
        fx.addView(SettingsUi.styledRow(this, "verified", getString(R.string.exp_secrets), getString(R.string.exp_secrets_sub), null,
                v -> EasterEggs.secrets(this)).view);
        content.addView(fx);
    }

    private void clearCache() {
        long freed = 0;
        File dir = getCacheDir();
        File[] list = dir == null ? null : dir.listFiles();
        if (list != null) for (File f : list) freed += deleteRecursive(f);
        Toast.makeText(this, getString(R.string.exp_cache_done, SettingsUi.bytes(freed)), Toast.LENGTH_SHORT).show();
    }

    private static long deleteRecursive(File f) {
        long n = 0;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) n += deleteRecursive(k);
        } else n = f.length();
        f.delete();
        return n;
    }
}
