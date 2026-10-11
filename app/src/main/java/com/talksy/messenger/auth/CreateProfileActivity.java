package com.talksy.messenger.auth;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.R;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.ui.ChatsActivity;

public class CreateProfileActivity extends BaseActivity {

    private static final String[] PALETTE = {
            "#6366F1", "#8B5CF6", "#06B6D4", "#22C55E", "#F59E0B", "#EF4444", "#EC4899", "#14B8A6"
    };

    private EditText inpFirstName;
    private EditText inpLastName;
    private EditText inpUsername;
    private android.widget.TextView txtUsernameStatus;
    private boolean usernameOk = true;
    private final android.os.Handler usernameHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable usernameCheck;

    private void checkUsernameLive(String name) {
        if (usernameCheck != null) usernameHandler.removeCallbacks(usernameCheck);
        if (name.isEmpty()) { usernameOk = true; txtUsernameStatus.setText(""); return; }
        usernameOk = false;
        usernameCheck = () -> apiClient.economyGet("/users/username-available?username=" + name, (ok, json, err) -> {
            if (!ok || json == null) { txtUsernameStatus.setText(err != null ? err : ""); return; }
            usernameOk = json.optBoolean("available");
            txtUsernameStatus.setText(usernameOk ? "✓ Username свободен" : json.optString("error", "Недоступен"));
            txtUsernameStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, usernameOk ? R.color.ok : R.color.err));
        });
        usernameHandler.postDelayed(usernameCheck, 400);
    }
    private TextView txtError;
    private TextView avatarPreview;
    private LinearLayout colorRow;
    private ApiClient apiClient;
    private String selectedColor = PALETTE[0];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_profile);

        apiClient = new ApiClient(this);

        inpFirstName = findViewById(R.id.inp_first_name);
        inpLastName = findViewById(R.id.inp_last_name);
        inpUsername = findViewById(R.id.inp_username);
        txtUsernameStatus = findViewById(R.id.txt_username_status);
        inpUsername.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void afterTextChanged(android.text.Editable e) { checkUsernameLive(e.toString().trim()); }
        });
        txtError = findViewById(R.id.txt_error);
        avatarPreview = findViewById(R.id.txt_avatar_preview);
        colorRow = findViewById(R.id.color_row);
        Button btnFinish = findViewById(R.id.btn_finish);

        buildColorSwatches();
        updateAvatarPreview();

        inpFirstName.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { updateAvatarPreview(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        btnFinish.setOnClickListener(v -> onFinish());
    }

    private void buildColorSwatches() {
        int size = (int) (36 * getResources().getDisplayMetrics().density);
        int margin = (int) (6 * getResources().getDisplayMetrics().density);

        colorRow.removeAllViews();
        for (String hex : PALETTE) {
            View swatch = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            swatch.setLayoutParams(lp);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.parseColor(hex));
            if (hex.equals(selectedColor)) {
                bg.setStroke((int) (3 * getResources().getDisplayMetrics().density), Color.WHITE);
            }
            swatch.setBackground(bg);
            swatch.setOnClickListener(v -> {
                selectedColor = hex;
                buildColorSwatches();
                updateAvatarPreview();
            });
            colorRow.addView(swatch);
        }
    }

    private void updateAvatarPreview() {
        String name = inpFirstName.getText().toString().trim();
        avatarPreview.setText(name.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(name.charAt(0))));
        try { avatarPreview.getBackground().setTint(Color.parseColor(selectedColor)); } catch (Exception ignored) {}
    }

    private void onFinish() {
        String first = inpFirstName.getText().toString().trim();
        String last = inpLastName.getText().toString().trim();

        if (first.isEmpty()) {
            txtError.setVisibility(View.VISIBLE);
            return;
        }
        if (!usernameOk) { txtUsernameStatus.setText("Выберите свободный username или оставьте поле пустым"); return; }
        txtError.setVisibility(View.GONE);
        String wantedHandle = inpUsername.getText().toString().trim();

        String fullName = last.isEmpty() ? first : (first + " " + last);

        apiClient.updateProfile(fullName, wantedHandle.isEmpty() ? null : "@" + wantedHandle.replaceFirst("^@", ""), "", selectedColor, (ok, user, error) -> {

            Intent i = new Intent(this, ChatsActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            AnimUtils.forward(this);
            finish();
        });
    }
}
