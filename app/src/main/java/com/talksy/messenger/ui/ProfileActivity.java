package com.talksy.messenger.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.R;
import com.talksy.messenger.auth.PhoneActivity;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.net.AvatarLoader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ProfileActivity extends BaseActivity {
    @Override protected boolean listensForCalls() { return true; }

    private Prefs prefs;
    private ApiClient apiClient;
    private TextView avatarInitials;
    private ImageView avatarImg;

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) onImagePicked(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        prefs = new Prefs(this);
        apiClient = new ApiClient(this);

        android.widget.LinearLayout pr = findViewById(R.id.profile_root);
        android.widget.LinearLayout giftsHolder = new android.widget.LinearLayout(this);
        giftsHolder.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout.LayoutParams glp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        glp.topMargin = EconomyUi.dp(this, 14);
        int at = pr.indexOfChild(findViewById(R.id.txt_change_photo)) + 1;
        pr.addView(giftsHolder, Math.max(0, at), glp);
        GiftFlow.attachSection(this, apiClient, giftsHolder, com.talksy.messenger.data.Accounts.currentUserId(this), true);

        FrameLayout frameAvatar = findViewById(R.id.frame_avatar);
        avatarInitials = findViewById(R.id.txt_avatar);
        avatarImg = findViewById(R.id.img_avatar);
        TextView txtChangePhoto = findViewById(R.id.txt_change_photo);
        EditText inpName = findViewById(R.id.inp_name);
        EditText inpHandle = findViewById(R.id.inp_handle);
        EditText inpBio = findViewById(R.id.inp_bio);
        TextView txtPhone = findViewById(R.id.txt_phone);
        TextView txtEmail = findViewById(R.id.txt_email);
        android.widget.Switch switchHidePhone = findViewById(R.id.switch_hide_phone);
        android.widget.Switch switchHideHandle = findViewById(R.id.switch_hide_handle);
        android.widget.Switch switchHideLastSeen = findViewById(R.id.switch_hide_last_seen);
        ImageButton btnBack = findViewById(R.id.btn_back);
        android.widget.Button btnSave = findViewById(R.id.btn_save);
        View btnLogout = findViewById(R.id.btn_logout_row);
        AnimUtils.pressScale(btnSave);
        AnimUtils.pressScale(btnLogout);

        String name = prefs.get(Prefs.KEY_MY_NAME, "");
        String handle = prefs.get(Prefs.KEY_MY_HANDLE, "");
        String bio = prefs.get(Prefs.KEY_MY_BIO, "");
        String color = prefs.get(Prefs.KEY_MY_COLOR, "#6366F1");
        String avatarUrl = prefs.get(Prefs.KEY_MY_AVATAR_URL, "");

        avatarInitials.setText(initials(name));
        try { avatarInitials.getBackground().setTint(Color.parseColor(color)); } catch (Exception ignored) {}
        if (!avatarUrl.isEmpty()) {
            AvatarLoader.load(apiClient.getBaseUrl() + avatarUrl, bmp -> {
                avatarImg.setImageBitmap(bmp);
                avatarImg.setVisibility(android.view.View.VISIBLE);
            });
        }

        inpName.setText(name);
        inpHandle.setText(handle);
        inpBio.setText(bio);
        txtPhone.setText(prefs.get(Prefs.KEY_PHONE, ""));
        txtEmail.setText(prefs.get(Prefs.KEY_EMAIL, ""));

        apiClient.getProfile((ok, user, error) -> {
            if (ok && user != null) {

                if (user.phone != null && !user.phone.isEmpty()) txtPhone.setText(user.phone);
                txtEmail.setText(user.email != null && !user.email.isEmpty() ? user.email : "Не привязана");
                switchHidePhone.setChecked(user.hidePhone);
                switchHideHandle.setChecked(user.hideHandle);
                switchHideLastSeen.setChecked(user.hideLastSeen);
            }
        });

        View rowPhone = findViewById(R.id.row_phone);
        if (rowPhone != null) rowPhone.setOnClickListener(v -> NumberChange.showMenu(this, apiClient, txtPhone.getText().toString(), () -> recreate()));

        View.OnClickListener openPicker = v -> pickImage.launch("image/*");
        frameAvatar.setOnClickListener(openPicker);
        txtChangePhoto.setOnClickListener(openPicker);

        btnBack.setOnClickListener(v -> { finish(); AnimUtils.back(this); });

        AnimUtils.runLayoutAnimation((android.view.ViewGroup) findViewById(R.id.profile_root));

        for (int id : new int[]{R.id.switch_hide_phone, R.id.switch_hide_handle, R.id.switch_hide_last_seen}) {
            android.view.View sw = findViewById(id);
            if (sw != null && sw.getParent() instanceof android.view.View) ((android.view.View) sw.getParent()).setVisibility(android.view.View.GONE);
        }

        try {

            android.widget.LinearLayout profileRoot = findViewById(R.id.profile_root);
            long myId = com.talksy.messenger.data.Accounts.currentUserId(this);
            android.view.View logout = findViewById(R.id.row_logout);
            int logoutIndex = logout != null ? profileRoot.indexOfChild(logout) : -1;

            android.widget.LinearLayout holder = new android.widget.LinearLayout(this);
            holder.setOrientation(android.widget.LinearLayout.VERTICAL);

            profileRoot.addView(holder, logoutIndex >= 0 ? logoutIndex : profileRoot.getChildCount());

            com.talksy.messenger.ui.ProfileStories.attach(this, holder, myId, null, true);
        } catch (Throwable t) {
            android.util.Log.e("Talksy", "ProfileStories.attach failed", t);
        }
        AnimUtils.pressScale(frameAvatar);
        AnimUtils.pressScale(btnBack);

        btnSave.setOnClickListener(v -> {
            String newName = inpName.getText().toString().trim();
            String newHandle = inpHandle.getText().toString().trim();
            String newBio = inpBio.getText().toString().trim();
            boolean hidePhone = switchHidePhone.isChecked();
            boolean hideHandle = switchHideHandle.isChecked();
            boolean hideLastSeen = switchHideLastSeen.isChecked();

            btnSave.setEnabled(false);
            apiClient.updateProfile(newName, newHandle, newBio, null, hidePhone, hideHandle, hideLastSeen, (ok, user, error) -> {
                btnSave.setEnabled(true);
                if (ok) {
                    Toast.makeText(this, R.string.save, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, error != null ? error : getString(R.string.profile_save_failed), Toast.LENGTH_LONG).show();
                }
            });
        });

        btnLogout.setOnClickListener(v -> new com.talksy.messenger.ui.TDialog.Builder(this)
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirm)
                .setPositiveButton(R.string.logout, (d, w) -> {
                    com.talksy.messenger.net.MessagePollService.stop(this);
                    com.talksy.messenger.sync.SyncManager.get(this).stop();
                    com.talksy.messenger.sync.LocalDb.wipe(this);
                    prefs.clearSession();
                    Intent i = new Intent(this, PhoneActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    AnimUtils.forward(this);
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    private void onImagePicked(Uri uri) {
        Toast.makeText(this, getString(R.string.uploading_photo), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Bitmap bitmap = decodeSampled(uri, 512);
                if (bitmap == null) {
                    runOnUiThread(() -> Toast.makeText(this, getString(R.string.image_read_failed), Toast.LENGTH_SHORT).show());
                    return;
                }
                Bitmap square = centerCropSquare(bitmap, 512);

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                square.compress(Bitmap.CompressFormat.JPEG, 80, bos);
                String base64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);

                runOnUiThread(() -> apiClient.uploadAvatar(base64, (ok, user, error) -> {
                    if (ok) {
                        avatarImg.setImageBitmap(AvatarLoader.toRoundedSquare(square));
                        avatarImg.setVisibility(android.view.View.VISIBLE);
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

    private Bitmap decodeSampled(Uri uri, int maxDim) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, bounds);
        }
        int sample = 1;
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(is, null, opts);
        }
    }

    private Bitmap centerCropSquare(Bitmap src, int targetSize) {
        int size = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - size) / 2;
        int y = (src.getHeight() - size) / 2;
        Bitmap cropped = Bitmap.createBitmap(src, x, y, size, size);
        return Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true);
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] p = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.length && i < 2; i++) if (!p[i].isEmpty()) sb.append(Character.toUpperCase(p[i].charAt(0)));
        return sb.toString();
    }
}
