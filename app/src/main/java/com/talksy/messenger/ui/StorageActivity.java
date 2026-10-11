package com.talksy.messenger.ui;

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
import com.talksy.messenger.net.MediaCache;
import com.talksy.messenger.ui.widget.DonutView;
import com.talksy.messenger.ui.widget.SettingsUi;

import java.util.Locale;

public class StorageActivity extends BaseActivity {
    private LinearLayout body;
    private final int[] COLORS = {0xFF3B82F6, 0xFF22C55E, 0xFF8B5CF6, 0xFFF97316, 0xFFEAB308, 0xFF94A3B8};
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
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
        t.setText(R.string.storage_title); t.setTextSize(18); t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
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
        final String[] cats = {TrafficStats.CAT_VIDEO, TrafficStats.CAT_FILES, TrafficStats.CAT_PHOTO, TrafficStats.CAT_VOICE, TrafficStats.CAT_STICKERS};
        final int[] names = {R.string.cat_video, R.string.cat_files, R.string.cat_photo, R.string.cat_voice, R.string.cat_stickers};
        new Thread(() -> {
            final long[] sizes = new long[cats.length + 1];
            long total = 0;
            for (int i = 0; i < cats.length; i++) { sizes[i] = MediaCache.categorySize(this, cats[i]); total += sizes[i]; }
            sizes[cats.length] = MediaCache.otherSize(this); total += sizes[cats.length];
            final long tot = total;
            runOnUiThread(() -> draw(cats, names, sizes, tot));
        }).start();
    }

    private void draw(String[] cats, int[] names, long[] sizes, long total) {
        body.removeAllViews();
        DonutView donut = new DonutView(this);
        float[] vals = new float[sizes.length]; for (int i = 0; i < sizes.length; i++) vals[i] = sizes[i];
        String[] tb = SettingsUi.bytes(total).split(" ");
        donut.set(vals, COLORS, tb[0], tb.length > 1 ? tb[1] : "");
        body.addView(donut, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 240)));
        TextView sub = new TextView(this);
        sub.setText(R.string.storage_sub); sub.setGravity(Gravity.CENTER); sub.setTextColor(ContextCompat.getColor(this, R.color.text3)); sub.setTextSize(12);
        body.addView(sub);

        LinearLayout card = SettingsUi.card(this);
        ((LinearLayout.LayoutParams) card.getLayoutParams()).topMargin = SettingsUi.dp(this, 16);
        for (int i = 0; i < sizes.length; i++) {
            final int idx = i;
            String name = getString(i < names.length ? names[i] : R.string.cat_other);
            int pct = total > 0 ? Math.round(sizes[i] * 100f / total) : 0;
            SettingsUi.Row r = SettingsUi.row(this, null, name + "   " + (pct == 0 && sizes[i] > 0 ? "<1" : pct) + "%", null, SettingsUi.bytes(sizes[i]), v -> {
                if (idx >= cats.length || sizes[idx] == 0) return;
                new TDialog.Builder(this).setTitle(name).setMessage(getString(R.string.storage_clear_cat, SettingsUi.bytes(sizes[idx])))
                        .setPositiveButton(R.string.storage_clear, (d, w) -> { MediaCache.clearCategory(this, cats[idx]); render(); })
                        .setNegativeButton(android.R.string.cancel, null).show();
            });
            r.value.setTextColor(ContextCompat.getColor(this, R.color.text2));
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(SettingsUi.dp(this, 14), SettingsUi.dp(this, 14));
            dp.setMarginEnd(SettingsUi.dp(this, 12));
            r.view.addView(SettingsUi.colorDot(this, COLORS[i % COLORS.length]), 0, dp);
            card.addView(r.view);
            if (i < sizes.length - 1) SettingsUi.divider(card);
        }
        Button clear = new Button(this, null, 0, R.style.TalksyButton);
        clear.setText(getString(R.string.storage_clear_all, SettingsUi.bytes(total)));
        clear.setOnClickListener(v -> new TDialog.Builder(this).setTitle(R.string.storage_clear_title).setMessage(R.string.storage_clear_msg)
                .setPositiveButton(R.string.storage_clear, (d, w) -> { MediaCache.clearAll(this); render(); })
                .setNegativeButton(android.R.string.cancel, null).show());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 50));
        cp.setMargins(SettingsUi.dp(this, 10), SettingsUi.dp(this, 8), SettingsUi.dp(this, 10), SettingsUi.dp(this, 10));
        card.addView(clear, cp);
        body.addView(card);
        body.addView(SettingsUi.note(this, getString(R.string.storage_cloud_note)));

        body.addView(SettingsUi.header(this, getString(R.string.storage_auto_title)));
        LinearLayout auto = SettingsUi.card(this);
        final SettingsUi.Row ttl = SettingsUi.row(this, "timer", getString(R.string.storage_ttl), null, ttlLabel(prefs.getLong("cache_ttl_days", 30)), null);
        ttl.view.setOnClickListener(v -> {
            long[] days = {1, 7, 30, 90, 0};
            CharSequence[] labels = {getString(R.string.ttl_1d), getString(R.string.ttl_1w), getString(R.string.ttl_1m), getString(R.string.ttl_3m), getString(R.string.ttl_never)};
            int checked = 2; for (int i = 0; i < days.length; i++) if (days[i] == prefs.getLong("cache_ttl_days", 30)) checked = i;
            new TDialog.Builder(this).setTitle(R.string.storage_ttl).setSingleChoiceItems(labels, checked, (d, which) -> {
                prefs.putLong("cache_ttl_days", days[which]); d.dismiss(); MediaCache.trimAsync(this); ttl.value.setText(ttlLabel(days[which]));
            }).setNegativeButton(android.R.string.cancel, null).show();
        });
        auto.addView(ttl.view); SettingsUi.divider(auto);
        final SettingsUi.Row max = SettingsUi.row(this, "cloud", getString(R.string.storage_max), null, maxLabel(prefs.getLong("cache_max_mb", 5 * 1024)), null);
        max.view.setOnClickListener(v -> {
            long[] mb = {1024, 5 * 1024, 16 * 1024, 32 * 1024, 0};
            CharSequence[] labels = {"1 ГБ", "5 ГБ", "16 ГБ", "32 ГБ", getString(R.string.storage_unlimited)};
            int checked = 1; for (int i = 0; i < mb.length; i++) if (mb[i] == prefs.getLong("cache_max_mb", 5 * 1024)) checked = i;
            new TDialog.Builder(this).setTitle(R.string.storage_max).setSingleChoiceItems(labels, checked, (d, which) -> {
                prefs.putLong("cache_max_mb", mb[which]); d.dismiss(); MediaCache.trimAsync(this); max.value.setText(maxLabel(mb[which]));
            }).setNegativeButton(android.R.string.cancel, null).show();
        });
        auto.addView(max.view);
        body.addView(auto);
        body.addView(SettingsUi.note(this, getString(R.string.storage_auto_note)));
    }

    private String ttlLabel(long days) {
        return days == 1 ? getString(R.string.ttl_1d) : days == 7 ? getString(R.string.ttl_1w) : days == 30 ? getString(R.string.ttl_1m)
                : days == 90 ? getString(R.string.ttl_3m) : days == 0 ? getString(R.string.ttl_never) : days + " д.";
    }
    private String maxLabel(long mb) { return mb == 0 ? getString(R.string.storage_unlimited) : mb >= 1024 ? (mb / 1024) + " ГБ" : mb + " МБ"; }
}
