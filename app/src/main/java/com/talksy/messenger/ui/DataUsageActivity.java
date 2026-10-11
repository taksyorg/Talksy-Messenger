package com.talksy.messenger.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.data.TrafficStats;
import com.talksy.messenger.ui.widget.DonutView;
import com.talksy.messenger.ui.widget.SettingsUi;

import java.text.DateFormat;
import java.util.Date;

public class DataUsageActivity extends BaseActivity {
    private LinearLayout body;
    private String net = null;
    private final int[] COLORS = {0xFF3B82F6, 0xFF22C55E, 0xFF06B6D4, 0xFFF97316, 0xFF8B5CF6, 0xFFEC4899, 0xFF94A3B8};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
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
        t.setText(R.string.data_title); t.setTextSize(18); t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(ContextCompat.getColor(this, R.color.text1));
        bar.addView(t);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 56)));
        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(SettingsUi.dp(this, 16), SettingsUi.dp(this, 4), SettingsUi.dp(this, 16), SettingsUi.dp(this, 24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); render(); }

    private void render() {
        body.removeAllViews();

        LinearLayout tabs = new LinearLayout(this);
        String[] keys = {null, TrafficStats.NET_MOBILE, TrafficStats.NET_WIFI, TrafficStats.NET_ROAMING};
        int[] names = {R.string.net_all, R.string.net_mobile, R.string.net_wifi, R.string.net_roaming};
        for (int i = 0; i < keys.length; i++) {
            final String k = keys[i];
            boolean on = (k == null && net == null) || (k != null && k.equals(net));
            TextView tv = new TextView(this);
            tv.setText(names[i]); tv.setTextSize(13); tv.setGravity(Gravity.CENTER);
            tv.setTextColor(ContextCompat.getColor(this, on ? R.color.accent : R.color.text2));
            if (on) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            GradientDrawable g = new GradientDrawable(); g.setCornerRadius(SettingsUi.dp(this, 20));
            g.setColor(on ? 0x226366F1 : 0x00000000); tv.setBackground(g);
            tv.setOnClickListener(v -> { net = k; render(); });
            tabs.addView(tv, new LinearLayout.LayoutParams(0, SettingsUi.dp(this, 38), 1f));
        }
        body.addView(tabs);

        long tx = TrafficStats.total(this, net, true), rx = TrafficStats.total(this, net, false), all = tx + rx;
        float[] vals = new float[TrafficStats.CATS.length];
        for (int i = 0; i < vals.length; i++) vals[i] = TrafficStats.get(this, net, TrafficStats.CATS[i], true) + TrafficStats.get(this, net, TrafficStats.CATS[i], false);
        DonutView donut = new DonutView(this);
        String[] tb = SettingsUi.bytes(all).split(" ");
        donut.set(vals, COLORS, tb[0], tb.length > 1 ? tb[1] : "");
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 240));
        dp.topMargin = SettingsUi.dp(this, 10);
        body.addView(donut, dp);
        TextView since = new TextView(this);
        since.setText(getString(R.string.data_since, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(TrafficStats.since(this)))));
        since.setGravity(Gravity.CENTER); since.setTextColor(ContextCompat.getColor(this, R.color.text3)); since.setTextSize(12);
        body.addView(since);

        int[] catNames = {R.string.cat_video, R.string.cat_files, R.string.cat_photo, R.string.cat_messages, R.string.cat_voice, R.string.cat_stickers, R.string.cat_other};
        String[] icons = {"video", "file", "photo", "secure_chat", "voice_message", "sticker", "cloud"};
        LinearLayout card = SettingsUi.card(this);
        ((LinearLayout.LayoutParams) card.getLayoutParams()).topMargin = SettingsUi.dp(this, 14);
        for (int i = 0; i < TrafficStats.CATS.length; i++) {
            final String cat = TrafficStats.CATS[i];
            long s = TrafficStats.get(this, net, cat, true), r = TrafficStats.get(this, net, cat, false), sum = s + r;
            int pct = all > 0 ? Math.round(sum * 100f / all) : 0;
            final SettingsUi.Row row = SettingsUi.row(this, icons[i], getString(catNames[i]) + "   " + (pct == 0 && sum > 0 ? "<1" : pct) + "%", null, SettingsUi.bytes(sum), null);
            row.value.setTextColor(ContextCompat.getColor(this, R.color.text2));
            final String detail = getString(R.string.data_sent_received, SettingsUi.bytes(s), SettingsUi.bytes(r));
            row.view.setOnClickListener(v -> {
                boolean shown = row.sub.getVisibility() == View.VISIBLE;
                row.sub.setText(detail); row.sub.setVisibility(shown ? View.GONE : View.VISIBLE);
            });
            card.addView(row.view);
            if (i < TrafficStats.CATS.length - 1) SettingsUi.divider(card);
        }
        body.addView(card);
        body.addView(SettingsUi.note(this, getString(R.string.data_tap_hint)));

        body.addView(SettingsUi.header(this, getString(R.string.data_total)));
        LinearLayout totals = SettingsUi.card(this);
        SettingsUi.Row a = SettingsUi.row(this, "file_upload", getString(R.string.data_sent), null, SettingsUi.bytes(tx), null);
        SettingsUi.Row c = SettingsUi.row(this, "file_download", getString(R.string.data_received), null, SettingsUi.bytes(rx), null);
        a.value.setTextColor(ContextCompat.getColor(this, R.color.text2)); c.value.setTextColor(ContextCompat.getColor(this, R.color.text2));
        totals.addView(a.view); SettingsUi.divider(totals); totals.addView(c.view);
        body.addView(totals);
        body.addView(SettingsUi.note(this, getString(R.string.data_note)));

        LinearLayout actions = SettingsUi.card(this);
        final Prefs prefs = new Prefs(this);
        final SettingsUi.Row ad = SettingsUi.row(this, "settings_2", getString(R.string.settings_auto_download), getString(R.string.data_auto_hint), autoLabel(prefs.get(Prefs.KEY_AUTO_DOWNLOAD, "wifi")), null);
        ad.view.setOnClickListener(v -> {
            String[] values = {"wifi", "always", "never"};
            CharSequence[] labels = {getString(R.string.auto_download_wifi), getString(R.string.auto_download_always), getString(R.string.auto_download_never)};
            int checked = java.util.Arrays.asList(values).indexOf(prefs.get(Prefs.KEY_AUTO_DOWNLOAD, "wifi"));
            new TDialog.Builder(this).setTitle(R.string.settings_auto_download).setSingleChoiceItems(labels, Math.max(checked, 0), (d, which) -> {
                prefs.put(Prefs.KEY_AUTO_DOWNLOAD, values[which]); d.dismiss(); ad.value.setText(autoLabel(values[which]));
            }).setNegativeButton(android.R.string.cancel, null).show();
        });
        actions.addView(ad.view);
        body.addView(actions);

        Button reset = new Button(this);
        reset.setText(R.string.data_reset); reset.setAllCaps(false); reset.setTextColor(0xFFEF4444);
        reset.setOnClickListener(v -> new TDialog.Builder(this).setTitle(R.string.data_reset).setMessage(R.string.data_reset_msg)
                .setPositiveButton(R.string.data_reset, (d, w) -> { TrafficStats.reset(this); render(); })
                .setNegativeButton(android.R.string.cancel, null).show());
        body.addView(reset, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 48)));
    }

    private String autoLabel(String v) {
        return getString("always".equals(v) ? R.string.auto_download_always : "never".equals(v) ? R.string.auto_download_never : R.string.auto_download_wifi);
    }
}
