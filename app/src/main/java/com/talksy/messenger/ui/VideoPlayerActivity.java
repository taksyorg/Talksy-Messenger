package com.talksy.messenger.ui;

import com.talksy.messenger.data.IconManager;

import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.talksy.messenger.ui.TDialog;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;

import java.util.Locale;

public class VideoPlayerActivity extends BaseActivity implements SurfaceHolder.Callback {

    public static final String EXTRA_URL = "video_url";

    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};

    private MediaPlayer player;
    private SurfaceView surface;
    private SurfaceHolder holder;
    private TextView txtLoading, btnSpeed, btnQuality, btnPlay, txtTime, txtTotal;
    private SeekBar seek;

    private String url;
    private boolean prepared = false;
    private boolean userSeeking = false;
    private float speed = 1f;
    private int quality = 0;
    private int videoW, videoH;
    private int lastPercent = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (player != null && prepared && !userSeeking) {
                try {
                    seek.setProgress(player.getCurrentPosition());
                    txtTime.setText(fmt(player.getCurrentPosition()));
                } catch (IllegalStateException ignored) {}
            }
            handler.postDelayed(this, 300);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null) { finish(); return; }

        surface = findViewById(R.id.surface);
        txtLoading = findViewById(R.id.txt_loading);
        btnSpeed = findViewById(R.id.btn_speed);
        btnQuality = findViewById(R.id.btn_quality);
        btnPlay = findViewById(R.id.btn_play);
        IconManager.set(btnPlay, "pause", 24, 0xFFFFFFFF);
        txtTime = findViewById(R.id.txt_time);
        txtTotal = findViewById(R.id.txt_total);
        seek = findViewById(R.id.seek);

        holder = surface.getHolder();
        holder.addCallback(this);

        findViewById(R.id.btn_close).setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        btnPlay.setOnClickListener(v -> togglePlay());
        btnSpeed.setOnClickListener(v -> showSpeedMenu());
        btnQuality.setOnClickListener(v -> showQualityMenu());
        surface.setOnClickListener(v -> {
            View c = findViewById(R.id.bottom_controls);
            c.setVisibility(c.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) txtTime.setText(fmt(p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                userSeeking = false;
                if (player != null && prepared) player.seekTo(s.getProgress());
            }
        });

        setLoadingPercent(0);
    }

    @Override public void surfaceCreated(SurfaceHolder h) {
        if (player == null) startPlayer();
        else player.setDisplay(h);
    }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) { }
    @Override public void surfaceDestroyed(SurfaceHolder h) {
        if (player != null) player.setDisplay(null);
    }

    private void startPlayer() {
        player = new MediaPlayer();
        try {
            java.io.File cachedVideo = com.talksy.messenger.net.MediaCache.cachedFile(url);
            if (cachedVideo != null) player.setDataSource(cachedVideo.getAbsolutePath());
            else { player.setDataSource(this, Uri.parse(url)); com.talksy.messenger.net.MediaCache.prefetch(url); }
            player.setDisplay(holder);
            player.setOnBufferingUpdateListener((mp, percent) -> setLoadingPercent(percent));
            player.setOnVideoSizeChangedListener((mp, w, h) -> {
                videoW = w; videoH = h;
                applyAspectAndQuality();
            });
            player.setOnPreparedListener(mp -> {
                prepared = true;
                seek.setMax(mp.getDuration());
                txtTotal.setText(fmt(mp.getDuration()));
                applySpeed();
                mp.start();
                IconManager.set(btnPlay, "pause", 24, 0xFFFFFFFF);
                handler.post(ticker);
            });
            player.setOnCompletionListener(mp -> IconManager.set(btnPlay, "play", 24, 0xFFFFFFFF));
            player.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show();
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void togglePlay() {
        if (player == null || !prepared) return;
        if (player.isPlaying()) {
            player.pause();
            IconManager.set(btnPlay, "play", 24, 0xFFFFFFFF);
        } else {
            player.start();
            applySpeed();
            IconManager.set(btnPlay, "pause", 24, 0xFFFFFFFF);
        }
    }

    private void applySpeed() {
        if (player == null || !prepared) return;
        try {
            PlaybackParams p = new PlaybackParams().setSpeed(speed);
            boolean wasPlaying = player.isPlaying();
            player.setPlaybackParams(p);

            if (!wasPlaying && player.isPlaying()) player.pause();
        } catch (Exception ignored) {}
        btnSpeed.setText(speed == (int) speed ? ((int) speed + "x") : (speed + "x"));
    }

    private void showSpeedMenu() {
        CharSequence[] labels = new CharSequence[SPEEDS.length];
        int checked = 2;
        for (int i = 0; i < SPEEDS.length; i++) {
            labels[i] = SPEEDS[i] + "x";
            if (SPEEDS[i] == speed) checked = i;
        }
        new TDialog.Builder(this)
                .setTitle(R.string.video_speed)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    speed = SPEEDS[which];
                    applySpeed();
                    d.dismiss();
                })
                .show();
    }

    private void showQualityMenu() {
        CharSequence[] labels = {
                getString(R.string.video_quality_auto),
                getString(R.string.video_quality_high),
                getString(R.string.video_quality_medium)
        };
        new TDialog.Builder(this)
                .setTitle(R.string.video_quality)
                .setSingleChoiceItems(labels, quality, (d, which) -> {
                    quality = which;
                    btnQuality.setText(labels[which]);
                    applyAspectAndQuality();
                    d.dismiss();
                })
                .show();
    }

    private void applyAspectAndQuality() {
        if (videoW <= 0 || videoH <= 0) return;
        View holderView = findViewById(R.id.video_holder);
        int maxW = holderView.getWidth(), maxH = holderView.getHeight();
        if (maxW <= 0 || maxH <= 0) { holderView.post(this::applyAspectAndQuality); return; }
        float scale = Math.min(maxW / (float) videoW, maxH / (float) videoH);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (videoW * scale), (int) (videoH * scale));
        lp.gravity = android.view.Gravity.CENTER;
        surface.setLayoutParams(lp);
        if (quality == 2) holder.setFixedSize(Math.max(2, videoW / 2), Math.max(2, videoH / 2));
        else holder.setSizeFromLayout();
    }

    private void setLoadingPercent(int percent) {
        lastPercent = percent;
        if (percent >= 100) {
            txtLoading.setVisibility(View.GONE);
        } else {
            txtLoading.setVisibility(View.VISIBLE);
            txtLoading.setText(getString(R.string.video_loading_percent, percent));
        }
    }

    private static String fmt(int ms) {
        int s = Math.max(0, ms / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60);
    }

    @Override protected void onPause() {
        super.onPause();
        if (player != null && prepared && player.isPlaying()) {
            player.pause();
            IconManager.set(btnPlay, "play", 24, 0xFFFFFFFF);
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (player != null) { try { player.release(); } catch (Exception ignored) {} player = null; }
    }
}
