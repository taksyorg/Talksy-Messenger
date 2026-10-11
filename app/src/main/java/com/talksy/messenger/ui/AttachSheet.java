package com.talksy.messenger.ui;

import com.talksy.messenger.data.IconManager;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AttachSheet {

    public interface Host {

        void onMediaPicked(Uri uri, String mime);

        void onPickFile();

        void onRequestMediaPermission();

        default boolean supportsGift() { return false; }

        default void onMediaPickedMany(java.util.List<Uri> uris) { for (Uri u : uris) onMediaPicked(u, null); }
        default void onPickGift() {}

        default void onOpenWallet() {}

        default void onPickLocation() {}

        default void onPickContact() {}

        default void onCreateArticle() {}

        default void onPickAudio() {}
    }

    private static final int LIMIT = 300;

    private static class Item {
        Uri uri;
        boolean video;
        long durationMs;
        String mime;
    }

    private static class Tab {
        final String key, emoji;
        final int labelRes;
        final boolean stub;
        Tab(String key, String emoji, int labelRes, boolean stub) {
            this.key = key; this.emoji = emoji; this.labelRes = labelRes; this.stub = stub;
        }
    }

    private static final Tab[] TABS = {
            new Tab("gallery", "photo", R.string.attach_gallery, false),
            new Tab("gift", "gift", R.string.attach_gift, true),
            new Tab("wallet", "tsc", R.string.attach_wallet, false),
            new Tab("file", "file", R.string.attach_file, false),
            new Tab("location", "location", R.string.attach_location, false),
            new Tab("article", "edit", R.string.attach_article, false),
            new Tab("contact", "contact_card", R.string.attach_contact, false),
            new Tab("music", "music", R.string.attach_music, false),
    };

    private final Activity activity;
    private final Host host;
    private final List<Item> items = new ArrayList<>();
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<Long, Bitmap> cache = new LruCache<>(120);
    private final List<View> tabViews = new ArrayList<>();
    private final GridAdapter adapter = new GridAdapter();

    private BottomSheetDialog dialog;
    private RecyclerView rv;
    private View pagePermission, pageStub;
    private TextView txtEmpty, stubIcon, stubTitle;
    private int selected = 0;
    private static final int MAX_MULTI = 10;
    private final java.util.LinkedHashMap<Long, Item> multi = new java.util.LinkedHashMap<>();
    private TextView sendBar;

    public AttachSheet(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
    }

    public static String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    public static boolean hasPermission(android.content.Context ctx) {
        for (String p : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    public void onPermissionResult() {
        if (dialog == null || !dialog.isShowing() || selected != 0) return;
        if (hasPermission(activity)) {
            showPage(rv);
            loadMedia();
        } else {
            showPage(pagePermission);
        }
    }

    public void show() {
        dialog = new BottomSheetDialog(activity);
        View root = LayoutInflater.from(activity).inflate(R.layout.sheet_attach, null);
        dialog.setContentView(root);

        rv = root.findViewById(R.id.rv_gallery);
        pagePermission = root.findViewById(R.id.page_permission);
        pageStub = root.findViewById(R.id.page_stub);
        txtEmpty = root.findViewById(R.id.txt_empty);
        stubIcon = root.findViewById(R.id.stub_icon);
        stubTitle = root.findViewById(R.id.stub_title);

        rv.setLayoutManager(new GridLayoutManager(activity, 3));
        rv.setAdapter(adapter);
        multi.clear();

        sendBar = new TextView(activity);
        sendBar.setTextColor(Color.WHITE); sendBar.setTextSize(15); sendBar.setTypeface(sendBar.getTypeface(), android.graphics.Typeface.BOLD);
        sendBar.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable sbg = new android.graphics.drawable.GradientDrawable();
        sbg.setCornerRadius(dp(24)); sbg.setColor(ContextCompat.getColor(activity, R.color.accent));
        sendBar.setBackground(sbg);
        sendBar.setPadding((int) dp(28), (int) dp(12), (int) dp(28), (int) dp(12));
        sendBar.setVisibility(View.GONE);
        android.widget.FrameLayout.LayoutParams sbp = new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        sbp.bottomMargin = (int) dp(14);
        ((android.widget.FrameLayout) root.findViewById(R.id.sheet_content)).addView(sendBar, sbp);
        sendBar.setOnClickListener(v -> {
            java.util.List<Uri> uris = new java.util.ArrayList<>();
            for (Item it : multi.values()) uris.add(it.uri);
            if (dialog != null) dialog.dismiss();
            if (!uris.isEmpty()) host.onMediaPickedMany(uris);
        });
        root.findViewById(R.id.btn_permission).setOnClickListener(v -> host.onRequestMediaPermission());

        root.findViewById(R.id.sheet_content).getLayoutParams().height =
                (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.5f);

        buildTabs((LinearLayout) root.findViewById(R.id.tab_bar));

        View sheet = (View) root.getParent();
        sheet.setBackgroundColor(Color.TRANSPARENT);
        dialog.setOnShowListener(d -> {
            BottomSheetBehavior<View> b = BottomSheetBehavior.from(sheet);
            b.setState(BottomSheetBehavior.STATE_EXPANDED);
            b.setSkipCollapsed(true);
        });
        dialog.setOnDismissListener(d -> io.shutdownNow());

        selectTab(0);
        dialog.show();
    }

    private float dp(float v) {
        return v * activity.getResources().getDisplayMetrics().density;
    }

    private void buildTabs(LinearLayout bar) {
        tabViews.clear();
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            final Tab t = TABS[i];
            LinearLayout tab = new LinearLayout(activity);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setMinimumWidth((int) dp(76));
            tab.setPadding((int) dp(8), (int) dp(6), (int) dp(8), (int) dp(6));

            TextView ico = new TextView(activity);
            IconManager.set(ico, t.emoji, 28);

            TextView lbl = new TextView(activity);
            lbl.setText(t.labelRes);
            lbl.setTextSize(11);
            lbl.setGravity(Gravity.CENTER);
            lbl.setMaxLines(1);

            tab.addView(ico);
            tab.addView(lbl);
            tab.setOnClickListener(v -> onTabClicked(index));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd((int) dp(2));
            bar.addView(tab, lp);
            tabViews.add(tab);
        }
    }

    private void styleTabs() {
        int accent = ContextCompat.getColor(activity, R.color.accent);
        int muted = ContextCompat.getColor(activity, R.color.text2);
        for (int i = 0; i < tabViews.size(); i++) {
            LinearLayout tab = (LinearLayout) tabViews.get(i);
            TextView lbl = (TextView) tab.getChildAt(1);
            boolean sel = i == selected;
            lbl.setTextColor(sel ? accent : muted);
            if (sel) {
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(14));
                bg.setColor(ColorUtils.setAlphaComponent(accent, 40));
                tab.setBackground(bg);
            } else {
                tab.setBackground(null);
            }
        }
    }

    private void onTabClicked(int index) {
        Tab t = TABS[index];
        if ("gift".equals(t.key)) {
            if (host.supportsGift()) { dialog.dismiss(); host.onPickGift(); return; }
            selectTab(index);
            return;
        }
        if (!"gallery".equals(t.key)) {
            dialog.dismiss();
            switch (t.key) {
                case "file": host.onPickFile(); break;
                case "wallet": host.onOpenWallet(); break;
                case "location": host.onPickLocation(); break;
                case "article": host.onCreateArticle(); break;
                case "contact": host.onPickContact(); break;
                case "music": host.onPickAudio(); break;
                default: break;
            }
            return;
        }
        selectTab(index);
    }

    private void selectTab(int index) {
        selected = index;
        styleTabs();
        Tab t = TABS[index];
        if ("gallery".equals(t.key)) {
            if (hasPermission(activity)) {
                showPage(rv);
                if (items.isEmpty()) loadMedia();
            } else {
                showPage(pagePermission);
                host.onRequestMediaPermission();
            }
        } else {
            IconManager.set(stubIcon, t.emoji, 44);
            stubTitle.setText(t.labelRes);
            showPage(pageStub);
        }
    }

    private void showPage(View page) {
        rv.setVisibility(page == rv ? View.VISIBLE : View.GONE);
        pagePermission.setVisibility(page == pagePermission ? View.VISIBLE : View.GONE);
        pageStub.setVisibility(page == pageStub ? View.VISIBLE : View.GONE);
        txtEmpty.setVisibility(page == rv && items.isEmpty() && loadedOnce ? View.VISIBLE : View.GONE);
    }

    private boolean loadedOnce = false;

    private void loadMedia() {
        io.execute(() -> {
            List<Item> loaded = new ArrayList<>();
            Uri files = MediaStore.Files.getContentUri("external");
            String[] proj = {
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.MEDIA_TYPE,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Video.VideoColumns.DURATION
            };
            String sel = MediaStore.Files.FileColumns.MEDIA_TYPE + "=? OR " + MediaStore.Files.FileColumns.MEDIA_TYPE + "=?";
            String[] args = {
                    String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
                    String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            };
            try (Cursor c = activity.getContentResolver().query(files, proj, sel, args,
                    MediaStore.Files.FileColumns.DATE_ADDED + " DESC")) {
                if (c != null) {
                    while (c.moveToNext() && loaded.size() < LIMIT) {
                        Item it = new Item();
                        long id = c.getLong(0);
                        int type = c.getInt(1);
                        it.mime = c.getString(2);
                        it.video = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                        it.durationMs = it.video ? c.getLong(3) : 0;
                        it.uri = ContentUris.withAppendedId(
                                it.video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                         : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                        loaded.add(it);
                    }
                }
            } catch (Exception ignored) {}
            main.post(() -> {
                loadedOnce = true;
                items.clear();
                items.addAll(loaded);
                adapter.notifyDataSetChanged();
                if (selected == 0 && rv != null) showPage(rv);
            });
        });
    }

    private Bitmap loadThumb(Item it) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                return activity.getContentResolver().loadThumbnail(it.uri, new Size(320, 320), null);
            }
            long id = ContentUris.parseId(it.uri);
            return it.video
                    ? MediaStore.Video.Thumbnails.getThumbnail(activity.getContentResolver(), id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                    : MediaStore.Images.Thumbnails.getThumbnail(activity.getContentResolver(), id, MediaStore.Images.Thumbnails.MINI_KIND, null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmtDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60);
    }

    private void toggleMulti(long key, Item it) {
        if (multi.containsKey(key)) multi.remove(key);
        else if (multi.size() >= MAX_MULTI) { android.widget.Toast.makeText(activity, "Можно выбрать не больше " + MAX_MULTI, android.widget.Toast.LENGTH_SHORT).show(); return; }
        else multi.put(key, it);
        adapter.notifyDataSetChanged();
        if (sendBar != null) {
            sendBar.setVisibility(multi.isEmpty() ? View.GONE : View.VISIBLE);
            sendBar.setText("Отправить (" + multi.size() + ")");
        }
    }

    private class GridAdapter extends RecyclerView.Adapter<GridAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int viewType) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_media_grid, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Item it = items.get(pos);
            final long key = ContentUris.parseId(it.uri) * (it.video ? -1 : 1);
            h.thumb.setTag(key);
            Bitmap cached = cache.get(key);
            if (cached != null) {
                h.thumb.setImageBitmap(cached);
            } else {
                h.thumb.setImageDrawable(null);
                try {
                    io.execute(() -> {
                        Bitmap b = loadThumb(it);
                        if (b == null) return;
                        cache.put(key, b);
                        main.post(() -> {
                            if (h.thumb.getTag() instanceof Long && (Long) h.thumb.getTag() == key) h.thumb.setImageBitmap(b);
                        });
                    });
                } catch (Exception ignored) {

                }
            }
            h.duration.setVisibility(it.video ? View.VISIBLE : View.GONE);
            if (it.video) h.duration.setText(IconManager.prefix(h.duration.getContext(), "play_tri", fmtDuration(it.durationMs), 0xFFFFFFFF, 11));
            final boolean sel = multi.containsKey(key);
            h.check.setVisibility(sel ? View.VISIBLE : View.GONE);
            if (sel) {
                android.graphics.drawable.GradientDrawable cg = new android.graphics.drawable.GradientDrawable();
                cg.setShape(android.graphics.drawable.GradientDrawable.OVAL); cg.setColor(ContextCompat.getColor(activity, R.color.accent)); cg.setStroke((int) dp(1.5f), Color.WHITE);
                h.check.setBackground(cg);
                int n = 1; for (Long k : multi.keySet()) { if (k == key) break; n++; }
                h.check.setText(String.valueOf(n));
            }
            h.thumb.setAlpha(sel ? 0.55f : 1f);
            h.itemView.setOnClickListener(v -> {
                if (!multi.isEmpty()) { toggleMulti(key, it); return; }
                if (dialog != null) dialog.dismiss();
                host.onMediaPicked(it.uri, it.mime);
            });
            h.itemView.setOnLongClickListener(v -> { toggleMulti(key, it); return true; });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView duration, check;
            VH(View v) {
                super(v);
                thumb = v.findViewById(R.id.img_thumb);
                duration = v.findViewById(R.id.txt_duration);
                check = v.findViewById(R.id.txt_check);
            }
        }
    }
}
