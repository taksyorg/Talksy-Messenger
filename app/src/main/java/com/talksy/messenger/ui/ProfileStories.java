package com.talksy.messenger.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.R;
import com.talksy.messenger.data.IconManager;
import com.talksy.messenger.data.model.StoryGroup;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.net.AvatarLoader;
import com.talksy.messenger.ui.widget.SettingsUi;

import java.util.ArrayList;
import java.util.List;

public final class ProfileStories {
    private ProfileStories() {}

    public static void attach(Activity act, LinearLayout parent, long userId, String userName, boolean own) {
        ApiClient api = new ApiClient(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = SettingsUi.dp(act, 8);
        parent.addView(box, bp);

        LinearLayout tabs = new LinearLayout(act);
        box.addView(tabs);
        final LinearLayout content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        box.addView(content);

        final int[] selected = {0};
        final TextView[] tv = new TextView[2];
        int[] names = {R.string.pub_tab, R.string.pub_archive_tab};
        for (int i = 0; i < tv.length; i++) {
            final int idx = i;
            tv[i] = new TextView(act);
            tv[i].setText(names[i]); tv[i].setTextSize(13); tv[i].setGravity(Gravity.CENTER);
            tv[i].setPadding(SettingsUi.dp(act, 16), 0, SettingsUi.dp(act, 16), 0);
            tabs.addView(tv[i], new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, SettingsUi.dp(act, 36)));
            tv[i].setOnClickListener(v -> { selected[0] = idx; paintTabs(act, tv, selected[0]); load(act, api, content, userId, userName, own, idx == 1); });
        }
        paintTabs(act, tv, 0);
        load(act, api, content, userId, userName, own, false);
    }

    private static void paintTabs(Activity act, TextView[] tv, int sel) {
        for (int i = 0; i < tv.length; i++) {
            boolean on = i == sel;
            tv[i].setTextColor(ContextCompat.getColor(act, on ? R.color.accent : R.color.text2));
            tv[i].setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            GradientDrawable g = new GradientDrawable(); g.setCornerRadius(SettingsUi.dp(act, 18)); g.setColor(on ? 0x226366F1 : 0x00000000);
            tv[i].setBackground(g);
        }
    }

    private static void load(Activity act, ApiClient api, LinearLayout content, long userId, String userName, boolean own, boolean archive) {
        content.removeAllViews();
        ApiClient.Callback<List<StoryGroup.Story>> cb = (ok, stories, err) -> {
            content.removeAllViews();
            if (!ok && "archive_hidden".equals(err)) { hidden(act, content); return; }
            if (!ok || stories == null || stories.isEmpty()) { empty(act, content, own && !archive, archive, own); return; }
            grid(act, api, content, stories, userId, userName, own, archive);
        };
        if (archive) { if (own) api.getStoriesArchive(cb); else api.getUserStoriesArchive(userId, cb); }
        else api.getUserStories(userId, cb);
    }

    private static void hidden(Activity act, LinearLayout content) {
        TextView t = new TextView(act);
        t.setText(R.string.pub_archive_hidden); t.setTextSize(14); t.setGravity(Gravity.CENTER);
        t.setTextColor(ContextCompat.getColor(act, R.color.text3));
        t.setPadding(SettingsUi.dp(act, 24), SettingsUi.dp(act, 32), SettingsUi.dp(act, 24), SettingsUi.dp(act, 32));
        content.addView(t);
    }

    private static void empty(Activity act, LinearLayout content, boolean canAdd, boolean archive, boolean own) {
        LinearLayout e = new LinearLayout(act);
        e.setOrientation(LinearLayout.VERTICAL); e.setGravity(Gravity.CENTER);
        e.setPadding(0, SettingsUi.dp(act, 28), 0, SettingsUi.dp(act, 28));
        TextView t = new TextView(act);
        t.setText(archive ? R.string.pub_archive_empty : R.string.pub_empty); t.setTextSize(16); t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(ContextCompat.getColor(act, R.color.text1)); t.setGravity(Gravity.CENTER);
        e.addView(t);
        TextView s = new TextView(act);
        s.setText(own ? (archive ? R.string.pub_archive_empty_sub : R.string.pub_empty_sub) : (archive ? R.string.pub_archive_empty_other : R.string.pub_empty_other)); s.setTextSize(13); s.setGravity(Gravity.CENTER);
        s.setTextColor(ContextCompat.getColor(act, R.color.text3));
        s.setPadding(SettingsUi.dp(act, 24), SettingsUi.dp(act, 6), SettingsUi.dp(act, 24), 0);
        e.addView(s);
        if (canAdd) {
            Button add = new Button(act, null, 0, R.style.TalksyButton);
            add.setText(R.string.pub_add);
            add.setGravity(Gravity.CENTER);
            add.setOnClickListener(v -> {
                Intent i = new Intent(act, ChatsActivity.class);
                i.putExtra(ChatsActivity.EXTRA_ADD_STORY, true);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                act.startActivity(i);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(SettingsUi.dp(act, 180), SettingsUi.dp(act, 46));
            lp.topMargin = SettingsUi.dp(act, 16);
            e.addView(add, lp);
        }
        content.addView(e);
    }

    private static void grid(Activity act, ApiClient api, LinearLayout content, List<StoryGroup.Story> stories, long userId, String userName, boolean own, boolean archive) {
        int cols = 3, gap = SettingsUi.dp(act, 3);
        int size = (act.getResources().getDisplayMetrics().widthPixels - SettingsUi.dp(act, 32) - gap * (cols - 1)) / cols;
        String base = api.getBaseUrl();
        long[] ids = new long[stories.size()], times = new long[stories.size()];
        String[] urls = new String[stories.size()], types = new String[stories.size()];
        for (int i = 0; i < stories.size(); i++) {
            StoryGroup.Story s = stories.get(i);
            ids[i] = s.id; urls[i] = s.mediaUrl; types[i] = s.mediaType; times[i] = s.createdAtMs;
        }
        LinearLayout row = null;
        for (int i = 0; i < stories.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(act);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.topMargin = i == 0 ? 0 : gap;
                content.addView(row, rp);
            }
            final int idx = i;
            StoryGroup.Story s = stories.get(i);
            android.widget.FrameLayout cell = new android.widget.FrameLayout(act);
            GradientDrawable ph = new GradientDrawable(); ph.setCornerRadius(SettingsUi.dp(act, 8)); ph.setColor(ContextCompat.getColor(act, R.color.bg3));
            cell.setBackground(ph); cell.setClipToOutline(true);
            ImageView iv = new ImageView(act);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cell.addView(iv, new android.widget.FrameLayout.LayoutParams(size, size));
            if ("video".equals(s.mediaType)) {
                loadVideoThumb(act, base + s.mediaUrl, iv);
                ImageView play = new ImageView(act);
                IconManager.set(play, "play_tri", Color.WHITE);
                android.widget.FrameLayout.LayoutParams pp = new android.widget.FrameLayout.LayoutParams(SettingsUi.dp(act, 28), SettingsUi.dp(act, 28), Gravity.CENTER);
                cell.addView(play, pp);
            } else {
                AvatarLoader.loadRaw(base + s.mediaUrl, 360, bmp -> iv.setImageBitmap(bmp));
            }
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(size, size);
            cp.setMarginEnd(i % cols == cols - 1 ? 0 : gap);
            row.addView(cell, cp);
            cell.setOnClickListener(v -> {
                Intent it = new Intent(act, StoryViewerActivity.class);
                it.putExtra(StoryViewerActivity.EXTRA_USER_ID, userId);
                it.putExtra(StoryViewerActivity.EXTRA_USER_NAME, userName);
                it.putExtra(StoryViewerActivity.EXTRA_OWN, own);
                it.putExtra(StoryViewerActivity.EXTRA_ARCHIVE, archive);
                it.putExtra(StoryViewerActivity.EXTRA_IDS, ids);
                it.putExtra(StoryViewerActivity.EXTRA_URLS, urls);
                it.putExtra(StoryViewerActivity.EXTRA_TYPES, types);
                it.putExtra(StoryViewerActivity.EXTRA_TIMES, times);
                it.putExtra(StoryViewerActivity.EXTRA_START, idx);
                act.startActivity(it);
            });
            if (archive && own) cell.setOnLongClickListener(v -> {
                new TDialog.Builder(act).setTitle(R.string.pub_delete_title).setMessage(R.string.pub_delete_msg)
                        .setPositiveButton(R.string.delete, (d, w) -> api.deleteArchivedStory(s.id, (ok, x, e) -> load(act, api, content, userId, userName, own, true)))
                        .setNegativeButton(android.R.string.cancel, null).show();
                return true;
            });
        }
    }

    private static void loadVideoThumb(Activity act, String url, ImageView target) {
        target.setTag(url);
        com.talksy.messenger.net.MediaCache.withLocalPath(url, com.talksy.messenger.data.TrafficStats.CAT_VIDEO, path -> {
            if (path == null) return;
            new Thread(() -> {
                android.graphics.Bitmap bmp = null;
                android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                try {
                    r.setDataSource(path);
                    bmp = r.getFrameAtTime(500_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (bmp == null) bmp = r.getFrameAtTime();
                } catch (Exception ignored) { }
                finally { try { r.release(); } catch (Exception ignored) { } }
                final android.graphics.Bitmap fin = bmp;
                if (fin != null) act.runOnUiThread(() -> { if (url.equals(target.getTag())) target.setImageBitmap(fin); });
            }).start();
        });
    }
}
