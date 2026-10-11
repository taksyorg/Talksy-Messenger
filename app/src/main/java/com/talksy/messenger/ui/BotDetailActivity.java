package com.talksy.messenger.ui;

import com.talksy.messenger.ui.TDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.model.Bot;
import com.talksy.messenger.net.ApiClient;

public class BotDetailActivity extends BaseActivity {

    private ApiClient apiClient;
    private long botId;
    private Bot bot;
    private boolean tokenRevealed = false;

    private android.widget.ImageView imgBotAvatar;
    private final androidx.activity.result.ActivityResultLauncher<String> pickBotPhoto =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) onBotPhotoPicked(uri);
            });

    private TextView txtTitle, txtUsername, txtToken, txtCodeSample, txtBotModeValue;
    private EditText inpName, inpBio, inpWebhook, inpMiniAppUrl, inpMiniAppName;
    private Button btnRevealToken;
    private Switch switchBotMode;
    private android.view.View groupWebhook, groupMiniApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bot_detail);
        apiClient = new ApiClient(this);
        botId = getIntent().getLongExtra("bot_id", 0);

        findViewById(R.id.btn_back).setOnClickListener(v -> { finish(); AnimUtils.back(this); });

        imgBotAvatar = findViewById(R.id.img_bot_avatar);
        android.view.View.OnClickListener pickPhoto = v -> pickBotPhoto.launch("image/*");
        imgBotAvatar.setOnClickListener(pickPhoto);
        findViewById(R.id.btn_bot_photo).setOnClickListener(pickPhoto);
        txtTitle = findViewById(R.id.txt_title);
        txtUsername = findViewById(R.id.txt_username);
        txtToken = findViewById(R.id.txt_token);
        txtCodeSample = findViewById(R.id.txt_code_sample);
        txtBotModeValue = findViewById(R.id.txt_bot_mode_value);
        inpName = findViewById(R.id.inp_name);
        inpBio = findViewById(R.id.inp_bio);
        inpWebhook = findViewById(R.id.inp_webhook);
        inpMiniAppUrl = findViewById(R.id.inp_mini_app_url);
        inpMiniAppName = findViewById(R.id.inp_mini_app_name);
        btnRevealToken = findViewById(R.id.btn_reveal_token);
        switchBotMode = findViewById(R.id.switch_bot_mode);
        groupWebhook = findViewById(R.id.group_webhook);
        groupMiniApp = findViewById(R.id.group_mini_app);

        btnRevealToken.setOnClickListener(v -> {
            tokenRevealed = !tokenRevealed;
            refreshTokenView();
        });
        findViewById(R.id.btn_copy_token).setOnClickListener(v -> copyToken());
        findViewById(R.id.btn_regenerate_token).setOnClickListener(v -> confirmRegenerateToken());
        findViewById(R.id.btn_save_profile).setOnClickListener(v -> saveProfile());
        findViewById(R.id.btn_save_webhook).setOnClickListener(v -> saveWebhook(inpWebhook.getText().toString().trim()));
        findViewById(R.id.btn_clear_webhook).setOnClickListener(v -> { inpWebhook.setText(""); saveWebhook(""); });
        findViewById(R.id.btn_save_mini_app).setOnClickListener(v -> saveMiniApp(
                inpMiniAppUrl.getText().toString().trim(), inpMiniAppName.getText().toString().trim()));
        findViewById(R.id.btn_clear_mini_app).setOnClickListener(v -> {
            inpMiniAppUrl.setText(""); inpMiniAppName.setText("");
            saveMiniApp("", "");
        });

        switchBotMode.setOnCheckedChangeListener((btn, checked) -> applyBotModeUi(checked));
        findViewById(R.id.btn_delete_bot).setOnClickListener(v -> confirmDelete());

        load();
    }

    private void applyBotModeUi(boolean miniAppMode) {
        groupWebhook.setVisibility(miniAppMode ? android.view.View.GONE : android.view.View.VISIBLE);
        groupMiniApp.setVisibility(miniAppMode ? android.view.View.VISIBLE : android.view.View.GONE);
        txtBotModeValue.setText(miniAppMode ? getString(R.string.bot_mode_mini_app) : getString(R.string.bot_mode_webhook));
    }

    private void saveMiniApp(String url, String name) {
        apiClient.setBotMiniApp(botId, url, name, (ok, v, error) -> {
            if (ok) {
                if (bot != null) { bot.miniAppUrl = url; bot.miniAppName = name; }
                Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void load() {
        apiClient.getBot(botId, (ok, b, error) -> {
            if (!ok || b == null) {
                Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            bot = b;
            applyBot();
        });
    }

    private void applyBot() {
        txtTitle.setText(bot.name);
        txtUsername.setText(bot.username);
        refreshBotAvatar();
        inpName.setText(bot.name);
        inpWebhook.setText(bot.webhookUrl);
        inpMiniAppUrl.setText(bot.miniAppUrl);
        inpMiniAppName.setText(bot.miniAppName);
        boolean miniAppMode = bot.miniAppUrl != null && !bot.miniAppUrl.isEmpty();
        switchBotMode.setChecked(miniAppMode);
        applyBotModeUi(miniAppMode);
        refreshTokenView();
        txtCodeSample.setText(getString(R.string.bot_docs_code_sample, bot.token));
    }

    private void refreshBotAvatar() {
        if (imgBotAvatar == null || bot == null) return;
        imgBotAvatar.setImageBitmap(botPlaceholder(bot.name));
        if (bot.avatarUrl != null && !bot.avatarUrl.isEmpty()) {
            com.talksy.messenger.net.AvatarLoader.load(apiClient.fullAvatarUrl(bot.avatarUrl), bmp -> {
                if (imgBotAvatar != null) imgBotAvatar.setImageBitmap(bmp);
            });
        }
    }

    private android.graphics.Bitmap botPlaceholder(String name) {
        int size = 256;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(bmp);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(androidx.core.content.ContextCompat.getColor(this, R.color.accent));
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setColor(android.graphics.Color.WHITE);
        p.setTextSize(size * 0.45f);
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        p.setFakeBoldText(true);
        String letter = "?";
        if (name != null && !name.trim().isEmpty()) {
            letter = new String(Character.toChars(name.trim().codePointAt(0))).toUpperCase();
        }
        float y = size / 2f - (p.descent() + p.ascent()) / 2f;
        c.drawText(letter, size / 2f, y, p);
        return bmp;
    }

    private void onBotPhotoPicked(android.net.Uri uri) {
        Toast.makeText(this, getString(R.string.uploading_photo), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                android.graphics.Bitmap bitmap = decodeSampledBot(uri, 512);
                if (bitmap == null) {
                    runOnUiThread(() -> Toast.makeText(this, getString(R.string.image_read_failed), Toast.LENGTH_SHORT).show());
                    return;
                }
                android.graphics.Bitmap square = centerCropSquareBot(bitmap, 512);
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                square.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bos);
                String base64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP);

                runOnUiThread(() -> apiClient.uploadBotAvatar(botId, base64, (ok, b, error) -> {
                    if (ok) {
                        if (b != null) {
                            if (bot != null && (b.token == null || b.token.isEmpty())) b.token = bot.token;
                            bot = b;
                        }
                        imgBotAvatar.setImageBitmap(com.talksy.messenger.net.AvatarLoader.toRoundedSquare(square));
                        Toast.makeText(this, getString(R.string.photo_updated), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, error != null ? error : getString(R.string.photo_upload_failed), Toast.LENGTH_LONG).show();
                    }
                }));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.photo_process_error) + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private android.graphics.Bitmap decodeSampledBot(android.net.Uri uri, int maxDim) throws Exception {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
            android.graphics.BitmapFactory.decodeStream(is, null, bounds);
        }
        int sample = 1;
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2;
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
            return android.graphics.BitmapFactory.decodeStream(is, null, opts);
        }
    }

    private android.graphics.Bitmap centerCropSquareBot(android.graphics.Bitmap src, int targetSize) {
        int size = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - size) / 2;
        int y = (src.getHeight() - size) / 2;
        android.graphics.Bitmap cropped = android.graphics.Bitmap.createBitmap(src, x, y, size, size);
        return android.graphics.Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true);
    }

    private void refreshTokenView() {
        if (bot == null) return;
        txtToken.setText(tokenRevealed ? bot.token : maskToken(bot.token));
        btnRevealToken.setText(tokenRevealed ? getString(R.string.bot_hide_token) : getString(R.string.bot_show_token));
    }

    private String maskToken(String token) {
        int colon = token.indexOf(':');
        String prefix = colon > 0 ? token.substring(0, colon + 1) : "";
        return prefix + "••••••••••••••••••••";
    }

    private void copyToken() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("bot token", bot.token));
        Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show();
    }

    private void confirmRegenerateToken() {
        new TDialog.Builder(this)
                .setTitle(getString(R.string.bot_regenerate_token))
                .setMessage(getString(R.string.bot_regenerate_token_confirm))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.bot_regenerate_token), (d, w) ->
                        apiClient.regenerateBotToken(botId, (ok, b, error) -> {
                            if (!ok || b == null) {
                                Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                                return;
                            }
                            bot = b;
                            tokenRevealed = true;
                            applyBot();
                            Toast.makeText(this, getString(R.string.bot_token_updated), Toast.LENGTH_SHORT).show();
                        }))
                .show();
    }

    private void saveProfile() {
        String name = inpName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.bot_name_required), Toast.LENGTH_SHORT).show();
            return;
        }
        String bio = inpBio.getText().toString().trim();
        apiClient.updateBotProfile(botId, name, bio, (ok, b, error) -> {
            if (!ok || b == null) {
                Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                return;
            }
            bot = b;
            applyBot();
            Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show();
        });
    }

    private void saveWebhook(String url) {
        apiClient.setBotWebhook(botId, url, (ok, v, error) -> {
            if (!ok) {
                Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                return;
            }
            if (bot != null) bot.webhookUrl = url;
            Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show();
        });
    }

    private void confirmDelete() {
        new TDialog.Builder(this)
                .setTitle(getString(R.string.bot_delete))
                .setMessage(getString(R.string.bot_delete_confirm))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.bot_delete), (d, w) ->
                        apiClient.deleteBot(botId, (ok, v, error) -> {
                            if (!ok) {
                                Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                                return;
                            }
                            Toast.makeText(this, getString(R.string.bot_deleted), Toast.LENGTH_SHORT).show();
                            finish();
                            AnimUtils.back(this);
                        }))
                .show();
    }
}
