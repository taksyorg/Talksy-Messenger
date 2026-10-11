package com.talksy.messenger.media;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class VoiceRecorder {
    public interface Level { void onLevel(float level01, long elapsedMs); }

    public static class Result {
        public File file;
        public long durationMs;
        public String waveformBase64;
    }

    public static final long MAX_MS = 10 * 60_000L;
    private static final int BARS = 64;

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaRecorder recorder;
    private File file;
    private long startedAt;
    private final List<Integer> amps = new ArrayList<>();
    private Level levelCb;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running || recorder == null) return;
            int a = 0;
            try { a = recorder.getMaxAmplitude(); } catch (Exception ignored) {}
            amps.add(a);
            long el = System.currentTimeMillis() - startedAt;
            if (levelCb != null) levelCb.onLevel(Math.min(1f, a / 14000f), el);
            main.postDelayed(this, 80);
        }
    };

    public VoiceRecorder(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    public boolean isRunning() { return running; }

    public boolean start(Level cb) {
        try {
            File dir = new File(ctx.getFilesDir(), "outbox_media");
            if (!dir.exists()) dir.mkdirs();
            file = new File(dir, "voice_" + System.currentTimeMillis() + ".m4a");
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(ctx) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            recorder.setAudioSamplingRate(32000);
            recorder.setAudioEncodingBitRate(48000);
            recorder.setMaxDuration((int) MAX_MS);
            recorder.setOutputFile(file.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            levelCb = cb; amps.clear(); running = true; startedAt = System.currentTimeMillis();
            main.postDelayed(tick, 80);
            return true;
        } catch (Exception e) {
            release(true);
            return false;
        }
    }

    public Result stop() {
        if (!running) return null;
        running = false;
        main.removeCallbacks(tick);
        long el = System.currentTimeMillis() - startedAt;
        try { recorder.stop(); } catch (Exception e) { release(true); return null; }
        release(false);
        Result r = new Result();
        r.file = file; r.durationMs = el; r.waveformBase64 = waveform(amps);
        return r;
    }

    public void cancel() {
        running = false;
        main.removeCallbacks(tick);
        try { if (recorder != null) recorder.stop(); } catch (Exception ignored) {}
        release(true);
    }

    private void release(boolean deleteFile) {
        try { if (recorder != null) recorder.release(); } catch (Exception ignored) {}
        recorder = null; running = false;
        if (deleteFile && file != null) file.delete();
    }

    static String waveform(List<Integer> a) {
        byte[] out = new byte[BARS];
        if (a.isEmpty()) return Base64.encodeToString(out, Base64.NO_WRAP);
        float[] bars = new float[BARS]; float peak = 1;
        for (int i = 0; i < BARS; i++) {
            int from = i * a.size() / BARS, to = Math.max(from + 1, (i + 1) * a.size() / BARS);
            int mx = 0; for (int j = from; j < to && j < a.size(); j++) mx = Math.max(mx, a.get(j));
            bars[i] = (float) Math.sqrt(mx);
            peak = Math.max(peak, bars[i]);
        }
        for (int i = 0; i < BARS; i++) out[i] = (byte) Math.round(Math.max(0.08f, bars[i] / peak) * 255);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }
}
