package com.talksy.messenger.media;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.talksy.messenger.R;
import com.talksy.messenger.data.TrafficStats;
import com.talksy.messenger.data.model.MediaAttachment;
import com.talksy.messenger.net.MediaCache;
import com.talksy.messenger.ui.widget.VoiceBubbleView;

import java.io.File;
import java.lang.ref.WeakReference;

public final class VoicePlayer {
    private static VoicePlayer instance;
    public static synchronized VoicePlayer get() { if (instance == null) instance = new VoicePlayer(); return instance; }

    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private String currentKey;
    private WeakReference<VoiceBubbleView> view = new WeakReference<>(null);
    private boolean loading, prepared;
    private float speed = 1f;
    private int lastPos;

    private final Runnable progress = new Runnable() {
        @Override public void run() {
            if (mp == null || !prepared) return;
            try {
                lastPos = mp.getCurrentPosition();
                VoiceBubbleView v = view.get();
                if (v != null) v.onPlayback(currentKey, true, lastPos, mp.getDuration());
            } catch (Exception ignored) {}
            main.postDelayed(this, 50);
        }
    };

    public String currentKey() { return currentKey; }
    public boolean isPlaying(String key) { return key.equals(currentKey) && mp != null && prepared && mp.isPlaying(); }
    public boolean isLoading(String key) { return key.equals(currentKey) && loading; }
    public float speed() { return speed; }
    public int position(String key) { return key.equals(currentKey) ? lastPos : 0; }

    public void attach(String key, VoiceBubbleView v) { if (key.equals(currentKey)) view = new WeakReference<>(v); }

    public void toggle(Context ctx, String key, MediaAttachment media, String baseUrl, VoiceBubbleView v) {
        if (key.equals(currentKey) && mp != null) {
            if (loading) return;
            if (mp.isPlaying()) pause(); else resume();
            return;
        }
        start(ctx, key, media, baseUrl, v);
    }

    private void start(Context ctx, String key, MediaAttachment media, String baseUrl, VoiceBubbleView v) {
        stop();
        com.talksy.messenger.ui.widget.RoundVideoView.stopCurrent();
        currentKey = key; view = new WeakReference<>(v); loading = true; lastPos = 0;
        v.onPlayback(key, false, 0, (int) media.durationMs);
        if (media.url == null && media.localPath != null) { prepare(ctx, key, new File(media.localPath), media); return; }
        File cached = MediaCache.peek(ctx, TrafficStats.CAT_VOICE, baseUrl + media.url);
        if (cached != null) { prepare(ctx, key, cached, media); return; }
        MediaCache.get(ctx, TrafficStats.CAT_VOICE, baseUrl + media.url, (file, err) -> {
            if (!key.equals(currentKey)) return;
            if (file == null) {
                loading = false; currentKey = null;
                Toast.makeText(ctx, R.string.voice_load_failed, Toast.LENGTH_SHORT).show();
                VoiceBubbleView vv = view.get(); if (vv != null) vv.onPlayback(key, false, 0, (int) media.durationMs);
                return;
            }
            prepare(ctx, key, file, media);
        });
    }

    private void prepare(Context ctx, String key, File file, MediaAttachment media) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setDataSource(file.getAbsolutePath());
            mp.setOnPreparedListener(p -> {
                prepared = true; loading = false;
                applySpeed();
                p.start();
                main.post(progress);
            });
            mp.setOnCompletionListener(p -> {
                VoiceBubbleView vv = view.get();
                int dur = p.getDuration();
                release();
                if (vv != null) vv.onPlayback(key, false, 0, dur);
            });
            mp.setOnErrorListener((p, w, e) -> {
                VoiceBubbleView vv = view.get();
                release();
                if (vv != null) vv.onPlayback(key, false, 0, (int) media.durationMs);
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            release();
        }
    }

    private void applySpeed() {
        if (mp == null) return;
        try {
            boolean was = mp.isPlaying();
            PlaybackParams pp = new PlaybackParams().setSpeed(speed);
            mp.setPlaybackParams(pp);
            if (!was) mp.pause();
        } catch (Exception ignored) {}
    }

    public float cycleSpeed() {
        speed = speed < 1.4f ? 1.5f : speed < 1.9f ? 2f : 1f;
        applySpeed();
        return speed;
    }

    public void pause() {
        if (mp == null || !prepared) return;
        try { mp.pause(); } catch (Exception ignored) {}
        main.removeCallbacks(progress);
        VoiceBubbleView v = view.get();
        if (v != null) v.onPlayback(currentKey, false, lastPos, mp.getDuration());
    }

    private void resume() {
        if (mp == null || !prepared) return;
        try { mp.start(); applySpeed(); if (!mp.isPlaying()) mp.start(); } catch (Exception ignored) {}
        main.removeCallbacks(progress); main.post(progress);
    }

    public void seekTo(String key, float fraction) {
        if (!key.equals(currentKey) || mp == null || !prepared) return;
        try { mp.seekTo((int) (mp.getDuration() * fraction)); lastPos = mp.getCurrentPosition(); } catch (Exception ignored) {}
    }

    public void stop() {
        VoiceBubbleView v = view.get(); String key = currentKey;
        int dur = 0; try { if (mp != null && prepared) dur = mp.getDuration(); } catch (Exception ignored) {}
        release();
        if (v != null && key != null) v.onPlayback(key, false, 0, dur);
    }

    private void release() {
        main.removeCallbacks(progress);
        try { if (mp != null) { mp.release(); } } catch (Exception ignored) {}
        mp = null; prepared = false; loading = false; currentKey = null; lastPos = 0;
    }
}
