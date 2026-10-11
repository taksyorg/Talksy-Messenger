package com.talksy.messenger.ui;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.net.AvatarLoader;

public class FullscreenImageActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

        String url = getIntent().getStringExtra("image_url");
        ImageView img = findViewById(R.id.img_fullscreen);
        ImageButton btnClose = findViewById(R.id.btn_close);
        ProgressBar progress = findViewById(R.id.progress);

        btnClose.setOnClickListener(v -> finish());

        AvatarLoader.loadRaw(url, 2000, bmp -> {
            progress.setVisibility(android.view.View.GONE);
            img.setImageBitmap(bmp);
        });
    }
}
