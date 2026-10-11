package com.talksy.messenger.ui;

import com.talksy.messenger.data.IconManager;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.Favorites;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.net.FileOpener;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class FavoritesActivity extends BaseActivity {

    private LinearLayout list;
    private TextView empty;
    private ApiClient api;

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        api = new ApiClient(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding((int) dp(8), (int) dp(12), (int) dp(16), (int) dp(12));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(30);
        back.setTextColor(ContextCompat.getColor(this, R.color.accent));
        back.setPadding((int) dp(12), 0, (int) dp(16), (int) dp(4));
        back.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        TextView title = new TextView(this);
        title.setText(R.string.favorites_title);
        title.setTextSize(20);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.text1));
        top.addView(back);
        top.addView(title);
        root.addView(top);

        android.widget.FrameLayout body = new android.widget.FrameLayout(this);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding((int) dp(12), (int) dp(4), (int) dp(12), (int) dp(24));
        sv.addView(list);
        body.addView(sv);
        empty = new TextView(this);
        empty.setText(R.string.favorites_empty);
        empty.setGravity(Gravity.CENTER);
        empty.setTextColor(ContextCompat.getColor(this, R.color.text2));
        empty.setTextSize(15);
        body.addView(empty, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        list.removeAllViews();
        List<Favorites.Item> items = Favorites.list(this);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault());
        int index = 0;
        for (Favorites.Item it : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(ContextCompat.getColor(this, R.color.bg2));
            bg.setCornerRadius(dp(18));
            card.setBackground(bg);
            card.setPadding((int) dp(16), (int) dp(12), (int) dp(16), (int) dp(12));

            TextView from = new TextView(this);
            from.setText(it.from + "  ·  " + fmt.format(it.time));
            from.setTextSize(12);
            from.setTextColor(ContextCompat.getColor(this, R.color.accent));
            card.addView(from);

            if (it.text != null && !it.text.isEmpty()) {
                TextView t = new TextView(this);
                t.setText(it.text);
                t.setTextSize(15);
                t.setTextColor(ContextCompat.getColor(this, R.color.text1));
                t.setPadding(0, (int) dp(6), 0, 0);
                card.addView(t);
            }
            if (it.mediaUrl != null && !it.mediaUrl.isEmpty()) {
                TextView m = new TextView(this);
                m.setText(IconManager.prefix(this, "file", (it.mediaName == null || it.mediaName.isEmpty() ? getString(R.string.attach_file) : it.mediaName), ContextCompat.getColor(this, R.color.text2), 14));
                m.setTextSize(14);
                m.setTextColor(ContextCompat.getColor(this, R.color.text2));
                m.setPadding(0, (int) dp(6), 0, 0);
                card.addView(m);
                card.setOnClickListener(v -> FileOpener.downloadAndOpen(this, api.getBaseUrl() + it.mediaUrl, it.mediaName));
            }
            card.setOnLongClickListener(v -> {
                new TDialog.Builder(this)
                        .setMessage(R.string.favorites_remove_confirm)
                        .setPositiveButton(R.string.favorites_remove, (d, w) -> { Favorites.remove(this, it.id); render(); })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
            AnimUtils.pressScale(card);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) dp(8);
            list.addView(card, lp);

            card.setAlpha(0f);
            card.setTranslationY(dp(16));
            card.animate().alpha(1f).translationY(0).setStartDelay(Math.min(index, 8) * 40L).setDuration(260).start();
            index++;
        }
    }
}
