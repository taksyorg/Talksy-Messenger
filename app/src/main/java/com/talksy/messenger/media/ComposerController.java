package com.talksy.messenger.media;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;

import com.talksy.messenger.R;
import com.talksy.messenger.data.IconManager;
import com.talksy.messenger.data.Prefs;

import java.io.File;
import java.util.Locale;

public final class ComposerController {

    public interface Host {
        void onVoice(File file, long durationMs, String waveformBase64);
        void onRound(File file, long durationMs);

        boolean canRecord();
    }

    private enum S { IDLE, PRESSED, HOLD, LOCKED }

    private static final String PREF_MODE = "composer_mode";
    private static final long HOLD_MS = 260, MIN_VOICE_MS = 700, MIN_ROUND_MS = 1000;

    private final AppCompatActivity act;
    private final Prefs prefs;
    private final ViewGroup inputRow;
    private final EditText input;
    private final View btnSend;
    private final ImageView btnRecord, btnLockSend;
    private final View[] hideWhileRecording;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final float dp;

    private final VoiceRecorder voice;
    private RoundCamera camera;
    private RoundOverlay overlay;
    private LinearLayout bar;
    private View dot;
    private TextView timer, hint, cancelBtn;

    private S state = S.IDLE;
    private boolean round, cancelArmed, startRequestedStop, camReady;
    private float downX, downY;
    private long recStart;
    private final ActivityResultLauncher<String[]> permLauncher;

    private final Runnable holdRunnable = this::beginRecording;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (state == S.IDLE || state == S.PRESSED) return;
            long el = round ? (camera != null && camera.isRecording() ? camera.elapsed() : 0) : System.currentTimeMillis() - recStart;
            timer.setText(fmt(el));
            dot.setAlpha((System.currentTimeMillis() / 500) % 2 == 0 ? 1f : 0.25f);
            if (overlay != null) overlay.setProgress(el / (float) RoundCamera.MAX_MS);
            if (!round && el >= VoiceRecorder.MAX_MS) { finish(true); return; }
            main.postDelayed(this, 100);
        }
    };

    public ComposerController(AppCompatActivity act, ViewGroup inputRow, EditText input, View btnSend,
                              ImageView btnRecord, ImageView btnLockSend, View[] hide, Host host) {
        this.act = act; this.prefs = new Prefs(act); this.inputRow = inputRow; this.input = input; this.btnSend = btnSend;
        this.btnRecord = btnRecord; this.btnLockSend = btnLockSend; this.hideWhileRecording = hide; this.host = host;
        this.dp = act.getResources().getDisplayMetrics().density;
        this.voice = new VoiceRecorder(act);
        this.round = "round".equals(prefs.get(PREF_MODE, "voice"));
        this.permLauncher = act.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
            Toast.makeText(act, R.string.record_hold_again, Toast.LENGTH_SHORT).show();
        });
        buildBar();
        bindModeIcon();
        refreshButtons();
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { refreshButtons(); }
        });
        btnRecord.setOnTouchListener(this::onTouch);
        btnLockSend.setOnClickListener(v -> finish(true));
        btnLockSend.setVisibility(View.GONE);
        act.getLifecycle().addObserver((androidx.lifecycle.LifecycleEventObserver) (src, ev) -> {
            if (ev == Lifecycle.Event.ON_PAUSE || ev == Lifecycle.Event.ON_DESTROY) { if (state != S.IDLE) discard(); }
        });
    }

    private void bindModeIcon() {
        IconManager.set(btnRecord, round ? "camera" : "voice_record", Color.WHITE);
        btnRecord.setContentDescription(act.getString(round ? R.string.record_round : R.string.record_voice));
    }

    public void refreshButtons() {
        if (state != S.IDLE && state != S.PRESSED) return;
        boolean hasText = input.getText() != null && input.getText().toString().trim().length() > 0;
        pop(btnSend, hasText);
        pop(btnRecord, !hasText);
    }

    private void pop(View v, boolean show) {
        if (show && v.getVisibility() != View.VISIBLE) {
            v.setScaleX(0.6f); v.setScaleY(0.6f); v.setAlpha(0f); v.setVisibility(View.VISIBLE);
            v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).start();
        } else if (!show) {
            v.animate().cancel(); v.setVisibility(View.GONE); v.setScaleX(1f); v.setScaleY(1f); v.setAlpha(1f);
        }
    }

    private void toggleMode() {
        round = !round;
        prefs.put(PREF_MODE, round ? "round" : "voice");
        bindModeIcon();
        btnRecord.setScaleX(0.8f); btnRecord.setScaleY(0.8f);
        btnRecord.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
    }

    private boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (state != S.IDLE) return true;
                downX = e.getRawX(); downY = e.getRawY(); state = S.PRESSED; startRequestedStop = false;
                v.setPressed(true);
                main.postDelayed(holdRunnable, HOLD_MS);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (state == S.HOLD) onMove(e.getRawX() - downX, e.getRawY() - downY);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                v.setPressed(false);
                main.removeCallbacks(holdRunnable);
                if (state == S.PRESSED) { state = S.IDLE; if (e.getActionMasked() == MotionEvent.ACTION_UP) toggleMode(); }
                else if (state == S.HOLD) {
                    if (round && !camReady) { startRequestedStop = true; }
                    else if (cancelArmed || e.getActionMasked() == MotionEvent.ACTION_CANCEL) discard();
                    else finish(true);
                }
                return true;
            default: return false;
        }
    }

    private void onMove(float dx, float dy) {
        boolean cancel = dx < -90 * dp;
        if (cancel != cancelArmed) {
            cancelArmed = cancel;
            hint.setText(cancel ? R.string.record_release_cancel : R.string.record_slide_cancel);
            hint.setTextColor(cancel ? 0xFFEF4444 : Color.WHITE);
            if (cancel) btnRecord.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        hint.setTranslationX(Math.max(-60 * dp, Math.min(0f, dx * 0.4f)));
        if (dy < -80 * dp && !cancelArmed) lock();
        if (overlay != null) overlay.setLockProgress(Math.min(1f, Math.max(0f, -dy / (80 * dp))));
    }

    private void lock() {
        if (state != S.HOLD) return;
        state = S.LOCKED;
        btnRecord.setVisibility(View.GONE);
        btnLockSend.setVisibility(View.VISIBLE);
        IconManager.set(btnLockSend, "send", Color.WHITE);
        hint.setVisibility(View.GONE);
        cancelBtn.setVisibility(View.VISIBLE);
        if (overlay != null) overlay.onLocked();
        btnLockSend.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    private boolean hasPerm(String p) { return ContextCompat.checkSelfPermission(act, p) == PackageManager.PERMISSION_GRANTED; }

    private void beginRecording() {
        if (state != S.PRESSED) return;
        if (!host.canRecord()) { state = S.IDLE; return; }
        boolean needCam = round;
        if (!hasPerm(Manifest.permission.RECORD_AUDIO) || (needCam && !hasPerm(Manifest.permission.CAMERA))) {
            state = S.IDLE;
            btnRecord.setPressed(false);
            permLauncher.launch(needCam ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}
                    : new String[]{Manifest.permission.RECORD_AUDIO});
            return;
        }
        state = S.HOLD; cancelArmed = false; camReady = !round;
        hint.setText(R.string.record_slide_cancel); hint.setTextColor(Color.WHITE); hint.setTranslationX(0); hint.setVisibility(View.VISIBLE);
        cancelBtn.setVisibility(View.GONE);
        showBar(true);
        btnRecord.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (round) startRound(); else startVoice();
        recStart = System.currentTimeMillis();
        main.post(ticker);
    }

    private void startVoice() {
        if (!voice.start((lvl, el) -> {   })) {
            Toast.makeText(act, R.string.record_failed, Toast.LENGTH_SHORT).show();
            reset();
        }
    }

    private void startRound() {
        overlay = new RoundOverlay(act, inputRow);
        overlay.show();
        camera = new RoundCamera(act, act);
        overlay.onFlip(() -> { camera.flip(overlay.previewView()); overlay.resetFlash(); });

        overlay.onTorch(() -> overlay.setTorch(camera.isFront() ? overlay.toggleScreenFlash() : camera.toggleTorch()));
        overlay.onZoom(z -> camera.setZoom(z));
        camera.bind(overlay.previewView(), ok -> {
            if (!ok || state == S.IDLE) { if (!ok) Toast.makeText(act, R.string.record_failed, Toast.LENGTH_SHORT).show(); reset(); return; }
            boolean started = camera.startRecording((file, ms, success) -> onRoundFinished(file, ms, success));
            if (!started) { Toast.makeText(act, R.string.record_failed, Toast.LENGTH_SHORT).show(); reset(); return; }
            camReady = true; recStart = System.currentTimeMillis();
            if (startRequestedStop) { if (cancelArmed) discard(); else finish(true); }
        });
    }

    private File pendingRoundFile; private long pendingRoundMs; private boolean roundSend;

    private void onRoundFinished(File file, long ms, boolean ok) {
        if (ok && roundSend) {
            if (ms < MIN_ROUND_MS) { file.delete(); Toast.makeText(act, R.string.record_too_short, Toast.LENGTH_SHORT).show(); }
            else host.onRound(file, ms);
        } else if (file != null) file.delete();
        reset();
    }

    private void finish(boolean send) {
        if (state != S.HOLD && state != S.LOCKED) return;
        main.removeCallbacks(ticker);
        if (round) {
            roundSend = send;
            if (camera != null && camera.isRecording()) camera.stopRecording(); else reset();
            return;
        }
        VoiceRecorder.Result r = voice.stop();
        if (r != null && send && r.durationMs >= MIN_VOICE_MS) host.onVoice(r.file, r.durationMs, r.waveformBase64);
        else {
            if (r != null) r.file.delete();
            if (send) Toast.makeText(act, R.string.record_too_short, Toast.LENGTH_SHORT).show();
        }
        reset();
    }

    private void discard() {
        if (state == S.IDLE) return;
        main.removeCallbacks(ticker);
        if (round) {
            roundSend = false;
            if (camera != null && camera.isRecording()) { camera.cancelRecording(); return; }
        } else voice.cancel();
        reset();
    }

    private void reset() {
        main.removeCallbacks(ticker); main.removeCallbacks(holdRunnable);
        state = S.IDLE; cancelArmed = false; startRequestedStop = false; camReady = false;
        showBar(false);
        btnLockSend.setVisibility(View.GONE);
        if (overlay != null) { overlay.hide(); overlay = null; }
        if (camera != null) { camera.unbind(); camera = null; }
        refreshButtons();
    }

    private String fmt(long ms) {
        long t = Math.max(0, ms) / 100, s = t / 10;
        return String.format(Locale.US, "%d:%02d,%d", s / 60, s % 60, t % 10);
    }

    private void buildBar() {
        bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setVisibility(View.GONE);
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(24 * dp); bg.setColor(ContextCompat.getColor(act, R.color.bg3));
        bar.setBackground(bg);
        bar.setPadding((int) (14 * dp), 0, (int) (8 * dp), 0);
        dot = new View(act);
        GradientDrawable dg = new GradientDrawable(); dg.setShape(GradientDrawable.OVAL); dg.setColor(0xFFEF4444);
        dot.setBackground(dg);
        bar.addView(dot, new LinearLayout.LayoutParams((int) (10 * dp), (int) (10 * dp)));
        timer = new TextView(act);
        timer.setTextColor(ContextCompat.getColor(act, R.color.text1)); timer.setTextSize(15);
        timer.setTypeface(android.graphics.Typeface.MONOSPACE);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMarginStart((int) (10 * dp));
        bar.addView(timer, tp);
        hint = new TextView(act);
        hint.setTextColor(ContextCompat.getColor(act, R.color.text2)); hint.setTextSize(13); hint.setGravity(Gravity.CENTER);
        bar.addView(hint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cancelBtn = new TextView(act);
        cancelBtn.setText(R.string.cancel_caps); cancelBtn.setTextColor(0xFFEF4444); cancelBtn.setTextSize(13);
        cancelBtn.setTypeface(cancelBtn.getTypeface(), android.graphics.Typeface.BOLD);
        cancelBtn.setPadding((int) (12 * dp), (int) (10 * dp), (int) (12 * dp), (int) (10 * dp));
        cancelBtn.setVisibility(View.GONE);
        cancelBtn.setOnClickListener(v -> discard());
        bar.addView(cancelBtn);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (44 * dp), 1f);
        int idx = 0;
        for (int i = 0; i < inputRow.getChildCount(); i++) { if (inputRow.getChildAt(i) == hideWhileRecording[0]) { idx = i; break; } }
        inputRow.addView(bar, idx, lp);
    }

    private int[] savedVis;

    private void showBar(boolean show) {
        bar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            savedVis = new int[hideWhileRecording.length];
            for (int i = 0; i < hideWhileRecording.length; i++) { savedVis[i] = hideWhileRecording[i].getVisibility(); hideWhileRecording[i].setVisibility(View.GONE); }
        } else if (savedVis != null) {
            for (int i = 0; i < hideWhileRecording.length; i++) hideWhileRecording[i].setVisibility(savedVis[i]);
            savedVis = null;
        }
        if (!show) hint.setVisibility(View.VISIBLE);
    }

    private static final class RoundOverlay {
        private final AppCompatActivity act;
        private final View anchor;
        private final float dp;
        private FrameLayout root;
        private PreviewView preview;
        private RingView ring;
        private TextView z1, z2, torchBtn, flipBtn, lockHint;
        private Runnable onFlip, onTorch;
        private boolean screenFlash;
        private ZoomCb onZoom;
        interface ZoomCb { void onZoom(float z); }

        RoundOverlay(AppCompatActivity act, View anchor) { this.act = act; this.anchor = anchor; this.dp = act.getResources().getDisplayMetrics().density; }

        PreviewView previewView() { return preview; }
        void onFlip(Runnable r) { onFlip = r; }
        void onTorch(Runnable r) { onTorch = r; }
        void onZoom(ZoomCb z) { onZoom = z; }

        void show() {
            FrameLayout content = act.findViewById(android.R.id.content);
            int[] loc = new int[2], cl = new int[2];
            anchor.getLocationInWindow(loc); content.getLocationInWindow(cl);
            int bottomGap = Math.max(0, content.getHeight() - (loc[1] - cl[1]));

            root = new FrameLayout(act);
            root.setBackgroundColor(0xB3000000);
            root.setClickable(true);
            FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            rp.bottomMargin = bottomGap;
            content.addView(root, rp);

            int size = (int) Math.min(270 * dp, act.getResources().getDisplayMetrics().widthPixels * 0.72f);
            FrameLayout holder = new FrameLayout(act);
            FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(size + (int) (16 * dp), size + (int) (16 * dp), Gravity.CENTER);
            hp.bottomMargin = (int) (40 * dp);
            root.addView(holder, hp);

            preview = new PreviewView(act);
            preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
            preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
            preview.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
            });
            preview.setClipToOutline(true);
            holder.addView(preview, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            ring = new RingView(act);
            holder.addView(ring, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout zoom = new LinearLayout(act);
            GradientDrawable zb = new GradientDrawable(); zb.setCornerRadius(40 * dp); zb.setColor(0x80000000); zoom.setBackground(zb);
            zoom.setPadding((int) (4 * dp), (int) (4 * dp), (int) (4 * dp), (int) (4 * dp));
            z1 = chip("1×", true); z2 = chip("2", false);
            zoom.addView(z1); zoom.addView(z2);
            FrameLayout.LayoutParams zp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            zp.topMargin = size / 2 + (int) (84 * dp);
            root.addView(zoom, zp);
            z1.setOnClickListener(v -> { setZoomUi(false); if (onZoom != null) onZoom.onZoom(1f); });
            z2.setOnClickListener(v -> { setZoomUi(true); if (onZoom != null) onZoom.onZoom(2f); });

            flipBtn = roundBtn("flip", "FLIP"); torchBtn = roundBtn("flash", "TORCH");
            LinearLayout tools = new LinearLayout(act);
            tools.addView(flipBtn); tools.addView(torchBtn);
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
            tp.setMargins((int) (16 * dp), 0, 0, (int) (12 * dp));
            root.addView(tools, tp);
            flipBtn.setOnClickListener(v -> { if (onFlip != null) onFlip.run(); });
            torchBtn.setOnClickListener(v -> { if (onTorch != null) onTorch.run(); });

            lockHint = new TextView(act);
            lockHint.setText(R.string.record_up_to_lock);
            lockHint.setTextColor(Color.WHITE); lockHint.setTextSize(12);
            GradientDrawable lb = new GradientDrawable(); lb.setCornerRadius(40 * dp); lb.setColor(0x99000000); lockHint.setBackground(lb);
            lockHint.setPadding((int) (14 * dp), (int) (6 * dp), (int) (14 * dp), (int) (6 * dp));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            lp.bottomMargin = (int) (16 * dp);
            root.addView(lockHint, lp);
        }

        private void setZoomUi(boolean two) {
            tint(z1, !two); tint(z2, two);
            z1.setText(two ? "1" : "1×"); z2.setText(two ? "2×" : "2");
        }
        private void tint(TextView t, boolean on) {
            GradientDrawable g = new GradientDrawable(); g.setCornerRadius(40 * dp); g.setColor(on ? 0xFF6366F1 : 0x00000000);
            t.setBackground(g);
        }
        private TextView chip(String s, boolean on) {
            TextView t = new TextView(act); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(13); t.setGravity(Gravity.CENTER);
            t.setMinWidth((int) (40 * dp)); t.setPadding((int) (10 * dp), (int) (6 * dp), (int) (10 * dp), (int) (6 * dp));
            tint(t, on); return t;
        }

        private TextView roundBtn(String icon, String fallbackText) {
            TextView t = new TextView(act); t.setTextColor(Color.WHITE); t.setTextSize(11); t.setGravity(Gravity.CENTER);
            if (act.getResources().getIdentifier("ic_tk_" + icon, "drawable", act.getPackageName()) != 0)
                com.talksy.messenger.data.IconManager.set(t, icon, 22, Color.WHITE);
            else t.setText(fallbackText);
            GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(0x80000000); t.setBackground(g);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams((int) (42 * dp), (int) (42 * dp)); p.setMarginEnd((int) (8 * dp));
            t.setLayoutParams(p); t.setAlpha(0.45f); return t;
        }

        void setProgress(float p) { if (ring != null) ring.setProgress(p); }
        void setLockProgress(float p) { if (lockHint != null) lockHint.setAlpha(1f - 0.5f * p); }
        void setTorch(boolean on) { if (torchBtn != null) torchBtn.setTextColor(on ? 0xFFFACC15 : Color.WHITE); }

        boolean toggleScreenFlash() { setScreenFlash(!screenFlash); return screenFlash; }

        void setScreenFlash(boolean on) {
            screenFlash = on;
            if (root != null) root.setBackgroundColor(on ? 0xF2FFFFFF : 0xB3000000);
            if (ring != null) ring.setAccent(on);
            android.view.WindowManager.LayoutParams lp = act.getWindow().getAttributes();
            lp.screenBrightness = on ? 1f : android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            act.getWindow().setAttributes(lp);
        }

        void resetFlash() { setScreenFlash(false); setTorch(false); }
        void onLocked() {
            if (lockHint != null) lockHint.setVisibility(View.GONE);
            if (flipBtn != null) { flipBtn.setAlpha(1f); torchBtn.setAlpha(1f); }
        }
        void hide() {
            if (screenFlash) setScreenFlash(false);
            if (root != null && root.getParent() instanceof ViewGroup) ((ViewGroup) root.getParent()).removeView(root);
            root = null;
        }
    }

    private static final class RingView extends View {
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), fg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float p;
        RingView(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            bg.setStyle(Paint.Style.STROKE); bg.setStrokeWidth(3 * d); bg.setColor(0x33FFFFFF);
            fg.setStyle(Paint.Style.STROKE); fg.setStrokeWidth(3 * d); fg.setColor(Color.WHITE); fg.setStrokeCap(Paint.Cap.ROUND);
        }
        void setProgress(float v) { p = Math.max(0, Math.min(1, v)); invalidate(); }
        void setAccent(boolean on) { fg.setColor(on ? 0xFF6366F1 : Color.WHITE); bg.setColor(on ? 0x33000000 : 0x33FFFFFF); invalidate(); }
        @Override protected void onDraw(android.graphics.Canvas c) {
            float pad = bg.getStrokeWidth();
            r.set(pad, pad, getWidth() - pad, getHeight() - pad);
            c.drawArc(r, 0, 360, false, bg);
            c.drawArc(r, -90, 360f * p, false, fg);
        }
    }
}
