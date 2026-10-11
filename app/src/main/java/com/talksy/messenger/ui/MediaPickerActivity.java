package com.talksy.messenger.ui;

import com.talksy.messenger.data.IconManager;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaPickerActivity extends BaseActivity {

    public static final String EXTRA_MIME = "mime";
    private static final int LIMIT = 300;

    private static class Item {
        Uri uri;
        boolean video;
        long durationMs;
        String mime;
    }

    private final List<Item> items = new ArrayList<>();
    private RecyclerView rv;
    private View btnPermission;
    private TextView txtEmpty;
    private GridAdapter adapter;

    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<Long, Bitmap> cache = new LruCache<>(120);

    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                if (hasPermission()) loadMedia(); else showPermissionButton();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_picker);

        findViewById(R.id.btn_back).setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        com.talksy.messenger.data.LiquidGlass.applyPanel(findViewById(R.id.top_bar), 0f);

        rv = findViewById(R.id.rv_media);
        btnPermission = findViewById(R.id.btn_permission);
        txtEmpty = findViewById(R.id.txt_empty);

        rv.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new GridAdapter();
        rv.setAdapter(adapter);

        btnPermission.setOnClickListener(v -> permLauncher.launch(requiredPermissions()));

        if (hasPermission()) loadMedia();
        else permLauncher.launch(requiredPermissions());
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private boolean hasPermission() {
        for (String p : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void showPermissionButton() {
        btnPermission.setVisibility(View.VISIBLE);
        rv.setVisibility(View.GONE);
    }

    private void loadMedia() {
        btnPermission.setVisibility(View.GONE);
        rv.setVisibility(View.VISIBLE);
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
            try (Cursor c = getContentResolver().query(files, proj, sel, args,
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
                items.clear();
                items.addAll(loaded);
                adapter.notifyDataSetChanged();
                txtEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private Bitmap loadThumb(Item it) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                return getContentResolver().loadThumbnail(it.uri, new Size(320, 320), null);
            }
            long id = ContentUris.parseId(it.uri);
            return it.video
                    ? MediaStore.Video.Thumbnails.getThumbnail(getContentResolver(), id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                    : MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), id, MediaStore.Images.Thumbnails.MINI_KIND, null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmtDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60);
    }

    private void pick(Item it) {
        Intent data = new Intent();
        data.setData(it.uri);
        data.putExtra(EXTRA_MIME, it.mime);
        data.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        setResult(RESULT_OK, data);
        finish();
        AnimUtils.back(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
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
                io.execute(() -> {
                    Bitmap b = loadThumb(it);
                    if (b == null) return;
                    cache.put(key, b);
                    main.post(() -> {
                        if (h.thumb.getTag() instanceof Long && (Long) h.thumb.getTag() == key) h.thumb.setImageBitmap(b);
                    });
                });
            }
            h.duration.setVisibility(it.video ? View.VISIBLE : View.GONE);
            if (it.video) h.duration.setText(IconManager.prefix(h.duration.getContext(), "play_tri", fmtDuration(it.durationMs), 0xFFFFFFFF, 11));
            h.itemView.setOnClickListener(v -> pick(it));
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView duration;
            VH(View v) {
                super(v);
                thumb = v.findViewById(R.id.img_thumb);
                duration = v.findViewById(R.id.txt_duration);
            }
        }
    }
}
