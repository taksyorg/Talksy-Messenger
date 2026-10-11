package com.talksy.messenger.media;

import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;

public class RoundCamera {
    public interface Ready { void onReady(boolean ok); }
    public interface Done { void onDone(File file, long durationMs, boolean ok); }

    public static final long MAX_MS = 60_000L;

    private final Context ctx;
    private final LifecycleOwner owner;
    private ProcessCameraProvider provider;
    private Camera camera;
    private VideoCapture<Recorder> capture;
    private Recording recording;
    private boolean front = true, torch;
    private float zoom = 1f;
    private long startedAt;
    private File outFile;
    private boolean cancelled;
    private Done doneCb;
    private PreviewView previewRef;
    private boolean stopAfterSwitch;
    private boolean switching;

    public RoundCamera(Context ctx, LifecycleOwner owner) { this.ctx = ctx; this.owner = owner; }

    public boolean isFront() { return front; }

    public void bind(PreviewView view, Ready cb) {
        ListenableFuture<ProcessCameraProvider> f = ProcessCameraProvider.getInstance(ctx);
        f.addListener(() -> {
            try {
                provider = f.get();
                rebind(view);
                cb.onReady(true);
            } catch (Exception e) { cb.onReady(false); }
        }, ContextCompat.getMainExecutor(ctx));
    }

    private void rebind(PreviewView view) {
        previewRef = view;
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(view.getSurfaceProvider());
        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build();
        capture = VideoCapture.withOutput(recorder);
        CameraSelector sel = front ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
        provider.unbindAll();
        camera = provider.bindToLifecycle(owner, sel, preview, capture);
        try { camera.getCameraControl().setZoomRatio(zoom); } catch (Exception ignored) {}
        try { camera.getCameraControl().enableTorch(torch && !front); } catch (Exception ignored) {}
    }

    public void flip(PreviewView view) {
        if (provider == null || switching) return;
        previewRef = view;
        front = !front; torch = false;
        if (recording != null) {
            switching = true;
            try { recording.stop(); } catch (Exception e) { switching = false; rebind(view); }
        } else {
            rebind(view);
        }
    }

    public boolean toggleTorch() {
        if (camera == null || front) return false;
        torch = !torch;
        try { camera.getCameraControl().enableTorch(torch); } catch (Exception ignored) {}
        return torch;
    }

    public void setZoom(float ratio) {
        zoom = ratio;
        if (camera != null) { try { camera.getCameraControl().setZoomRatio(ratio); } catch (Exception ignored) {} }
    }

    public boolean startRecording(Done done) {
        if (capture == null || recording != null) return false;
        doneCb = done;
        return startInternal();
    }

    private boolean startInternal() {
        try {
            File dir = new File(ctx.getFilesDir(), "outbox_media");
            if (!dir.exists()) dir.mkdirs();
            outFile = new File(dir, "round_" + System.currentTimeMillis() + ".mp4");
            FileOutputOptions opts = new FileOutputOptions.Builder(outFile).setDurationLimitMillis(MAX_MS).build();
            PendingRecording pr = capture.getOutput().prepareRecording(ctx, opts);
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pr = pr.withAudioEnabled();
            cancelled = false; startedAt = System.currentTimeMillis();
            recording = pr.start(ContextCompat.getMainExecutor(ctx), event -> {
                if (event instanceof VideoRecordEvent.Finalize) {
                    VideoRecordEvent.Finalize fin = (VideoRecordEvent.Finalize) event;
                    long dur = System.currentTimeMillis() - startedAt;
                    recording = null;
                    if (switching) {
                        switching = false;
                        if (outFile != null) outFile.delete();
                        try {
                            final boolean keepCancel = cancelled;
                            rebind(previewRef);
                            if (!startInternal()) { doneCb.onDone(null, 0, false); return; }
                            cancelled = keepCancel;
                            if (stopAfterSwitch) { stopAfterSwitch = false; recording.stop(); }
                        } catch (Exception e) { doneCb.onDone(null, 0, false); }
                        return;
                    }
                    boolean ok = !cancelled && (!fin.hasError() || fin.getError() == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED)
                            && outFile != null && outFile.exists() && outFile.length() > 0;
                    if (!ok && outFile != null) outFile.delete();
                    doneCb.onDone(ok ? outFile : null, Math.min(dur, MAX_MS), ok);
                }
            });
            return true;
        } catch (Exception e) { recording = null; return false; }
    }

    public long elapsed() { return recording == null ? 0 : System.currentTimeMillis() - startedAt; }
    public boolean isRecording() { return recording != null || switching; }

    public void stopRecording() {
        if (switching) { stopAfterSwitch = true; return; }
        if (recording != null) recording.stop();
    }
    public void cancelRecording() {
        cancelled = true;
        if (switching) { stopAfterSwitch = true; return; }
        if (recording != null) recording.stop();
    }

    public void unbind() {
        try { if (provider != null) provider.unbindAll(); } catch (Exception ignored) {}
        camera = null; capture = null; switching = false; stopAfterSwitch = false;
    }
}
