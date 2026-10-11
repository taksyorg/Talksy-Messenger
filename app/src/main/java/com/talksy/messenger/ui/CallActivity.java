package com.talksy.messenger.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.calls.CallManager;
import com.talksy.messenger.calls.CallService;

import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

public class CallActivity extends BaseActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_OUTGOING = "outgoing";
    public static final String MODE_INCOMING = "incoming";
    public static final String EXTRA_PEER_ID = "peer_id";
    public static final String EXTRA_PEER_NAME = "peer_name";
    public static final String EXTRA_PEER_COLOR = "peer_color";
    public static final String EXTRA_VIDEO = "video";
    public static final String EXTRA_CALL_ID = "call_id";

    public static final String EXTRA_AUTO_ACCEPT = "auto_accept";

    public static volatile boolean isShowing = false;

    private static final int REQ_PERMISSIONS = 501;

    private CallService callService;
    private boolean serviceBound = false;

    private String mode;
    private long peerId;
    private String peerName, peerColor;
    private boolean isVideo;
    private long callId = -1;
    private boolean accepted = false;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override public void run() {
            long connectedAt = callService != null ? callService.getConnectedAtMs() : 0;
            if (connectedAt > 0) {
                long secs = (System.currentTimeMillis() - connectedAt) / 1000;
                txtStatus.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", secs / 60, secs % 60));
            }
            timerHandler.postDelayed(this, 1000);
        }
    };

    private TextView txtName, txtStatus;
    private android.view.View incomingControls, activeControls;
    private ImageButton btnMute, btnSpeaker;
    private boolean muted = false;
    private boolean speakerOn = true;

    private final CallService.UiCallback uiCallback = new CallService.UiCallback() {
        @Override public void onStatusText(String text) {
            runOnUiThread(() -> txtStatus.setText(text));
        }
        @Override public void onConnected() {
            runOnUiThread(() -> {
                timerHandler.removeCallbacks(timerRunnable);
                timerHandler.post(timerRunnable);
            });
        }
        @Override public void onConnectionLost() {
            runOnUiThread(() -> {
                timerHandler.removeCallbacks(timerRunnable);
                txtStatus.setText(getString(R.string.call_connection_lost));
            });
        }
        @Override public void onRemoteVideoTrack(VideoTrack track) {
            runOnUiThread(() -> {
                android.view.View remoteView = findViewById(R.id.remote_video);
                if (remoteView instanceof org.webrtc.SurfaceViewRenderer) {
                    track.addSink((org.webrtc.SurfaceViewRenderer) remoteView);
                }
            });
        }
        @Override public void onLocalReady() {
            runOnUiThread(CallActivity.this::attachVideoRenderersIfNeeded);
        }
        @Override public void onCallEnded(String reasonMessage) {
            runOnUiThread(() -> {
                if (reasonMessage != null) Toast.makeText(CallActivity.this, reasonMessage, Toast.LENGTH_SHORT).show();
                finish();
            });
        }
        @Override public void onError(String message) {
            runOnUiThread(() -> Toast.makeText(CallActivity.this, message, Toast.LENGTH_LONG).show());
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            callService = ((CallService.LocalBinder) binder).getService();
            serviceBound = true;
            callService.setUiCallback(uiCallback);
            onServiceReady();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            callService = null;
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);
        isShowing = true;

        androidx.core.app.NotificationManagerCompat.from(this).cancel(2001);

        mode = getIntent().getStringExtra(EXTRA_MODE);
        peerId = getIntent().getLongExtra(EXTRA_PEER_ID, 0);
        peerName = getIntent().getStringExtra(EXTRA_PEER_NAME);
        peerColor = getIntent().getStringExtra(EXTRA_PEER_COLOR);
        isVideo = getIntent().getBooleanExtra(EXTRA_VIDEO, false);
        callId = getIntent().getLongExtra(EXTRA_CALL_ID, -1);

        txtName = findViewById(R.id.txt_call_peer_name);
        txtStatus = findViewById(R.id.txt_call_status);
        incomingControls = findViewById(R.id.row_incoming_controls);
        activeControls = findViewById(R.id.row_active_controls);
        btnMute = findViewById(R.id.btn_mute);
        btnSpeaker = findViewById(R.id.btn_speaker);
        ImageButton btnHangup = findViewById(R.id.btn_hangup);
        ImageButton btnAccept = findViewById(R.id.btn_accept);
        ImageButton btnDecline = findViewById(R.id.btn_decline);
        ImageButton btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        btnSwitchCamera.setVisibility(isVideo ? android.view.View.VISIBLE : android.view.View.GONE);
        ImageButton btnMinimize = findViewById(R.id.btn_minimize);

        txtName.setText(peerName);

        if (isVideo) {

            findViewById(R.id.txt_avatar_big).setVisibility(android.view.View.GONE);
            android.widget.LinearLayout top = findViewById(R.id.call_top);
            ((android.view.ViewGroup) txtName.getParent()).removeView(txtName);
            ((android.view.ViewGroup) txtStatus.getParent()).removeView(txtStatus);
            android.widget.LinearLayout.LayoutParams nameLp = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            android.widget.LinearLayout.LayoutParams statusLp = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            statusLp.topMargin = (int) (4 * getResources().getDisplayMetrics().density);
            txtName.setTextSize(20);
            top.addView(txtName, nameLp);
            top.addView(txtStatus, statusLp);
            top.setVisibility(android.view.View.VISIBLE);
            makeLocalVideoDraggable();
        }

        boolean incoming = MODE_INCOMING.equals(mode);
        incomingControls.setVisibility(incoming ? android.view.View.VISIBLE : android.view.View.GONE);
        activeControls.setVisibility(incoming ? android.view.View.GONE : android.view.View.VISIBLE);
        txtStatus.setText(incoming ? getString(R.string.call_incoming) : getString(R.string.call_calling));

        btnHangup.setOnClickListener(v -> hangUp());
        btnDecline.setOnClickListener(v -> decline());
        btnAccept.setOnClickListener(v -> accept());
        btnMute.setOnClickListener(v -> toggleMute());
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        btnSwitchCamera.setOnClickListener(v -> { if (callService != null) callService.switchCamera(); });
        btnMinimize.setOnClickListener(v -> minimizeCall());

        setSpeakerphone(true);

        bindService(new Intent(this, CallService.class), connection, Context.BIND_AUTO_CREATE);
    }

    private void onServiceReady() {
        boolean resuming = callService.isConfigured() && callService.getCallId() == callId && callId > 0;
        if (!resuming) {
            callService.configure(mode, peerId, peerName, peerColor, isVideo, callId);
            if (MODE_INCOMING.equals(mode)) {
                if (getIntent().getBooleanExtra(EXTRA_AUTO_ACCEPT, false)) accept();
            } else {
                requestPermissionsAndStart();
            }
            return;
        }

        accepted = true;
        callId = callService.getCallId();
        incomingControls.setVisibility(android.view.View.GONE);
        activeControls.setVisibility(android.view.View.VISIBLE);
        if (callService.getConnectedAtMs() > 0) {
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.post(timerRunnable);
        } else {
            txtStatus.setText(getString(R.string.call_connecting));
        }
        if (callService.isCallManagerReady()) attachVideoRenderersIfNeeded();
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void makeLocalVideoDraggable() {
        final android.view.View local = findViewById(R.id.local_video);
        final float margin = 16 * getResources().getDisplayMetrics().density;
        final float bottomReserve = 130 * getResources().getDisplayMetrics().density;
        local.setOnTouchListener(new android.view.View.OnTouchListener() {
            float dX, dY;
            @Override public boolean onTouch(android.view.View v, android.view.MotionEvent e) {
                android.view.View parent = (android.view.View) v.getParent();
                float minX = -v.getLeft() + margin, maxX = parent.getWidth() - v.getWidth() - v.getLeft() - margin;
                float minY = -v.getTop() + margin, maxY = parent.getHeight() - v.getHeight() - v.getTop() - bottomReserve;
                switch (e.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        dX = v.getTranslationX() - e.getRawX();
                        dY = v.getTranslationY() - e.getRawY();
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        v.setTranslationX(Math.max(minX, Math.min(maxX, e.getRawX() + dX)));
                        v.setTranslationY(Math.max(minY, Math.min(maxY, e.getRawY() + dY)));
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        float centerX = v.getLeft() + v.getTranslationX() + v.getWidth() / 2f;
                        float target = centerX < parent.getWidth() / 2f ? minX : maxX;
                        v.animate().translationX(target).setDuration(220)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f)).start();
                        return true;
                }
                return false;
            }
        });
    }

    private void attachVideoRenderersIfNeeded() {
        CallManager cm = callService != null ? callService.getCallManager() : null;
        if (cm == null || !isVideo) return;
        org.webrtc.SurfaceViewRenderer local = findViewById(R.id.local_video);
        org.webrtc.SurfaceViewRenderer remote = findViewById(R.id.remote_video);
        local.setVisibility(android.view.View.VISIBLE);
        remote.setVisibility(android.view.View.VISIBLE);
        cm.setLocalRenderer(local);
        cm.setRemoteRenderer(remote);
    }

    private void requestPermissionsAndStart() {
        List<String> need = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (isVideo && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.CAMERA);

        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERMISSIONS);
        } else {
            callService.startAsCaller();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMISSIONS) return;
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.call_permission_denied), Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }
        if (MODE_OUTGOING.equals(mode)) callService.startAsCaller(); else accept();
    }

    private void accept() {
        accepted = true;
        incomingControls.setVisibility(android.view.View.GONE);
        activeControls.setVisibility(android.view.View.VISIBLE);
        txtStatus.setText(getString(R.string.call_connecting));

        List<String> need = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (isVideo && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.CAMERA);
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERMISSIONS);
        } else {
            callService.acceptCall();
        }
    }

    private void decline() {
        if (callService != null) callService.decline();
        else finish();
    }

    private void toggleMute() {
        muted = !muted;
        if (callService != null) callService.toggleMute(muted);
        btnMute.setImageResource(muted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
    }

    private void toggleSpeaker() {
        speakerOn = !speakerOn;
        setSpeakerphone(speakerOn);
        btnSpeaker.setImageResource(speakerOn ? R.drawable.ic_speaker_on : R.drawable.ic_speaker_off);
    }

    private void setSpeakerphone(boolean on) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        am.setSpeakerphoneOn(on);
    }

    private void minimizeCall() {
        Intent toChats = new Intent(this, ChatsActivity.class);
        toChats.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(toChats);
        finish();
    }

    private void hangUp() {
        if (callService != null) callService.hangUp();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        androidx.core.app.NotificationManagerCompat.from(this).cancel(2001);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isShowing = false;
        timerHandler.removeCallbacksAndMessages(null);
        if (serviceBound) {

            if (callService != null) callService.setUiCallback(null);
            unbindService(connection);
            serviceBound = false;
        }
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.setMode(AudioManager.MODE_NORMAL);
    }

    @Override
    public void onBackPressed() {
        hangUp();
    }
}
