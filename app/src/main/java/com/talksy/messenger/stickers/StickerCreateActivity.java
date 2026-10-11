package com.talksy.messenger.stickers;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.model.MediaAttachment;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.net.MediaUploader;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class StickerCreateActivity extends BaseActivity {

    private ApiClient apiClient;
    private SquareCropView cropView;
    private VideoView videoPreview;
    private boolean isVideoMode;
    private Uri pickedVideoUri;
    private long pickedVideoDurationMs;

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> { if (uri != null) onImagePicked(uri); });
    private final ActivityResultLauncher<String> pickVideo =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> { if (uri != null) onVideoPicked(uri); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_create);
        apiClient = new ApiClient(this);

        cropView = findViewById(R.id.crop_view);
        videoPreview = findViewById(R.id.video_preview);
        ImageButton btnBack = findViewById(R.id.btn_back);
        Button btnPickImage = findViewById(R.id.btn_pick_image);
        Button btnPickVideo = findViewById(R.id.btn_pick_video);
        Button btnSave = findViewById(R.id.btn_save);
        EditText inpPackName = findViewById(R.id.inp_pack_name);

        long existingPackId = getIntent().getLongExtra("pack_id", -1);
        if (existingPackId > 0) {
            inpPackName.setVisibility(View.GONE);
        }

        btnBack.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        btnPickImage.setOnClickListener(v -> pickImage.launch("image/*"));
        btnPickVideo.setOnClickListener(v -> pickVideo.launch("video/*"));

        btnSave.setOnClickListener(v -> {
            if (!isVideoMode && cropView.getVisibility() != View.VISIBLE) {
                Toast.makeText(this, "Сначала выбери фото или видео", Toast.LENGTH_SHORT).show();
                return;
            }
            btnSave.setEnabled(false);
            if (existingPackId > 0) {
                saveSticker(existingPackId);
            } else {
                String newPackName = inpPackName.getText().toString().trim();
                if (newPackName.isEmpty()) {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Укажи название пака", Toast.LENGTH_SHORT).show();
                    return;
                }
                apiClient.createStickerPack(newPackName, (ok, pack, error) -> {
                    if (!ok || pack == null) {
                        btnSave.setEnabled(true);
                        Toast.makeText(this, error != null ? error : "Не удалось создать пак", Toast.LENGTH_LONG).show();
                        return;
                    }
                    saveSticker(pack.id);
                });
            }
        });
    }

    private void onImagePicked(Uri uri) {
        isVideoMode = false;
        videoPreview.setVisibility(View.GONE);
        cropView.setVisibility(View.VISIBLE);
        try {
            Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            cropView.setBitmap(bmp);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть фото", Toast.LENGTH_SHORT).show();
        }
    }

    private void onVideoPicked(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = d != null ? Long.parseLong(d) : 0;
            if (durationMs <= 0 || durationMs > 2000) {
                Toast.makeText(this, "Видео должно быть не длиннее 2 секунд — выбери короче клип", Toast.LENGTH_LONG).show();
                return;
            }
            pickedVideoUri = uri;
            pickedVideoDurationMs = durationMs;
            isVideoMode = true;
            cropView.setVisibility(View.GONE);
            videoPreview.setVisibility(View.VISIBLE);
            videoPreview.setVideoURI(uri);
            videoPreview.setOnPreparedListener(mp -> { mp.setLooping(true); videoPreview.start(); });
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть видео", Toast.LENGTH_SHORT).show();
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    private void saveSticker(long targetPackId) {
        if (isVideoMode && pickedVideoUri != null) {
            uploadAndAdd(targetPackId, pickedVideoUri, "video/mp4", "sticker_video", pickedVideoDurationMs);
            return;
        }
        Bitmap square = cropView.renderSquareBitmap(512);
        Bitmap rounded = roundCorners(square, 512 * 0.16f);
        File tmp = new File(getCacheDir(), "sticker_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            rounded.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка сохранения стикера", Toast.LENGTH_SHORT).show();
            return;
        }
        uploadAndAdd(targetPackId, Uri.fromFile(tmp), "image/png", "sticker_image", 0);
    }

    private void uploadAndAdd(long targetPackId, Uri fileUri, String mime, String mediaType, long durationMs) {
        MediaUploader.FileInfo info = MediaUploader.describe(this, fileUri);
        apiClient.uploadMediaStream(fileUri, info.name, mime, info.size,
                (sent, total) -> {},
                new AtomicBoolean(false),
                (ok, media, error) -> {
                    if (!ok || media == null) {
                        Toast.makeText(this, error != null ? error : "Не удалось загрузить файл", Toast.LENGTH_LONG).show();
                        return;
                    }
                    apiClient.addStickerToPack(targetPackId, media.url, mediaType, durationMs, (ok2, sticker, error2) -> {
                        if (!ok2) {
                            Toast.makeText(this, error2 != null ? error2 : "Не удалось добавить стикер", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Toast.makeText(this, "Стикер добавлен!", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    });
                });
    }

    private static Bitmap roundCorners(Bitmap src, float radiusPx) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF rect = new RectF(0, 0, src.getWidth(), src.getHeight());
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(src, 0, 0, paint);
        return out;
    }
}
