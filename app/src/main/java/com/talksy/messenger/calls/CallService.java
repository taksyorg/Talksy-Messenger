package com.talksy.messenger.calls;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.talksy.messenger.R;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.ui.CallActivity;

import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

public class CallService extends Service {

    private static final String CHANNEL_ID = "talksy_call_ongoing";
    private static final int NOTIF_ID = 2002;
    private static final int POLL_MS = 1000;

    public static volatile CallService current;

    public interface UiCallback {
        void onStatusText(String text);
        void onConnected();
        void onConnectionLost();
        void onRemoteVideoTrack(VideoTrack track);
        void onLocalReady();
        void onCallEnded(String reasonMessage);
        void onError(String message);
    }

    public class LocalBinder extends Binder {
        public CallService getService() { return CallService.this; }
    }

    private final IBinder binder = new LocalBinder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler pollHandler = new Handler(Looper.getMainLooper());

    private ApiClient apiClient;
    private CallManager callManager;
    private UiCallback ui;

    private String mode, peerName, peerColor, peerPublicKey;
    private long peerId = 0, callId = -1;
    private boolean isVideo, accepted = false, callManagerReady = false, ended = false;
    private double lastSignalTime = 0;
    private long connectedAtMs = 0;
    private final List<JSONObject> pendingSignals = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        apiClient = new ApiClient(this);
        current = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (current == this) current = null;
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    public void setUiCallback(UiCallback cb) { this.ui = cb; }

    public boolean isActiveCall(long forCallId) {
        return !ended && (callId > 0 ? callId == forCallId : true);
    }

    public boolean isConfigured() { return mode != null; }
    public long getCallId() { return callId; }
    public long getPeerId() { return peerId; }
    public String getMode() { return mode; }
    public String getPeerName() { return peerName; }
    public String getPeerColor() { return peerColor; }
    public boolean isVideoCall() { return isVideo; }
    public long getConnectedAtMs() { return connectedAtMs; }
    public CallManager getCallManager() { return callManager; }

    public void configure(String mode, long peerId, String peerName, String peerColor,
                           boolean isVideo, long callId) {
        this.mode = mode;
        this.peerId = peerId;
        this.peerName = peerName;
        this.peerColor = peerColor;
        this.isVideo = isVideo;
        if (callId > 0) this.callId = callId;
        createChannel();
        startForeground(NOTIF_ID, buildNotification());
    }

    public void startAsCaller() {
        apiClient.getUserProfile(peerId, (ok, user, error) -> {
            if (!ok || user == null || user.publicKey == null || user.publicKey.isEmpty()) {
                endWithError(getString(R.string.peer_no_key_yet));
                return;
            }
            peerPublicKey = user.publicKey;
            apiClient.startCall(peerId, isVideo ? "video" : "audio", (ok2, call, error2) -> {
                if (!ok2 || call == null) {
                    endWithError(error2 != null ? error2 : getString(R.string.call_failed));
                    return;
                }
                callId = call.id;
                initCallManager();
                callManager.createOffer();
                startPolling();
            });
        });
    }

    public void acceptCall() {
        accepted = true;
        if (ui != null) ui.onStatusText(getString(R.string.call_connecting));
        apiClient.getUserProfile(peerId, (ok, user, error) -> {
            if (!ok || user == null || user.publicKey == null || user.publicKey.isEmpty()) {
                endWithError(getString(R.string.peer_no_key_yet));
                return;
            }
            peerPublicKey = user.publicKey;
            apiClient.acceptCall(callId, (ok2, v, error2) -> {
                if (!ok2) {
                    endWithError(error2 != null ? error2 : getString(R.string.call_failed));
                    return;
                }
                initCallManager();
                List<JSONObject> buffered = new ArrayList<>(pendingSignals);
                pendingSignals.clear();
                for (JSONObject s : buffered) applySignal(s);
                startPolling();
            });
        });
    }

    public void decline() {
        apiClient.rejectCall(callId, null);
        finishCall(null);
    }

    private void initCallManager() {
        callManager = new CallManager(this, isVideo, new CallManager.Listener() {
            @Override public void onLocalDescription(SessionDescription sdp) {
                sendEncryptedSignal(sdp.type == SessionDescription.Type.OFFER ? "offer" : "answer", sdpToJson(sdp));
            }
            @Override public void onIceCandidate(IceCandidate candidate) {
                JSONObject o = new JSONObject();
                try {
                    o.put("sdpMid", candidate.sdpMid);
                    o.put("sdpMLineIndex", candidate.sdpMLineIndex);
                    o.put("candidate", candidate.sdp);
                } catch (Exception ignored) { }
                sendEncryptedSignal("ice", o.toString());
            }
            @Override public void onRemoteVideoTrack(VideoTrack track) {
                mainHandler.post(() -> { if (ui != null) ui.onRemoteVideoTrack(track); });
            }
            @Override public void onConnectionStateChanged(PeerConnection.PeerConnectionState state) {
                mainHandler.post(() -> {
                    if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                        if (connectedAtMs == 0) {
                            connectedAtMs = System.currentTimeMillis();
                            refreshNotification();
                        }
                        if (ui != null) ui.onConnected();
                    } else if (state == PeerConnection.PeerConnectionState.FAILED
                            || state == PeerConnection.PeerConnectionState.DISCONNECTED) {
                        if (ui != null) ui.onConnectionLost();
                    }
                });
            }
            @Override public void onError(String message) {
                mainHandler.post(() -> { if (ui != null) ui.onError(message); });
            }
        });

        List<PeerConnection.IceServer> iceServers = CallManager.defaultIceServers(
                "turn:" + com.talksy.messenger.BuildConfig.TURN_HOST + ":3478?transport=udp",
                com.talksy.messenger.BuildConfig.TURN_USER, com.talksy.messenger.BuildConfig.TURN_PASS);
        iceServers.add(PeerConnection.IceServer.builder("turn:" + com.talksy.messenger.BuildConfig.TURN_HOST + ":3478?transport=tcp")
                .setUsername(com.talksy.messenger.BuildConfig.TURN_USER).setPassword(com.talksy.messenger.BuildConfig.TURN_PASS).createIceServer());
        callManager.start(iceServers);
        callManagerReady = true;
        if (ui != null) ui.onLocalReady();
    }

    public boolean isCallManagerReady() { return callManagerReady; }

    private String sdpToJson(SessionDescription sdp) {
        JSONObject o = new JSONObject();
        try {
            o.put("type", sdp.type.canonicalForm());
            o.put("sdp", sdp.description);
        } catch (Exception ignored) { }
        return o.toString();
    }

    private void sendEncryptedSignal(String kind, String plainJson) {
        new Thread(() -> {
            try {
                String payload = CallSignalCodec.encrypt(plainJson, peerPublicKey);
                apiClient.sendCallSignal(callId, kind, payload, null);
            } catch (Exception e) {
                mainHandler.post(() -> { if (ui != null) ui.onError(getString(R.string.encryption_error) + e.getMessage()); });
            }
        }).start();
    }

    private void startPolling() {
        pollHandler.postDelayed(pollRunnable, POLL_MS);
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (callId > 0) {
                apiClient.getCallSignals(callId, lastSignalTime, (ok, signals, error) -> {
                    if (ok && signals != null) {
                        for (ApiClient.CallSignal s : signals) {
                            lastSignalTime = Math.max(lastSignalTime, s.time);
                            new Thread(() -> {
                                try {
                                    String plain = CallSignalCodec.decrypt(s.payload);
                                    JSONObject o = new JSONObject(plain);
                                    o.put("__kind", s.kind);
                                    mainHandler.post(() -> {
                                        if (callManagerReady) applySignal(o); else pendingSignals.add(o);
                                    });
                                } catch (Exception ignored) {

                                }
                            }).start();
                        }
                    }
                });
            }
            pollHandler.postDelayed(this, POLL_MS);
        }
    };

    private void applySignal(JSONObject o) {
        String kind = o.optString("__kind");
        try {
            if ("offer".equals(kind)) {
                callManager.setRemoteDescription(SessionDescription.Type.OFFER, o.getString("sdp"));
                callManager.createAnswer();
            } else if ("answer".equals(kind)) {
                callManager.setRemoteDescription(SessionDescription.Type.ANSWER, o.getString("sdp"));
            } else if ("ice".equals(kind)) {
                callManager.addIceCandidate(o.getString("sdpMid"), o.getInt("sdpMLineIndex"), o.getString("candidate"));
            } else if ("hangup".equals(kind)) {
                finishCall(getString(R.string.call_ended_by_peer));
            }
        } catch (Exception ignored) { }
    }

    public void toggleMute(boolean muted) {
        if (callManager != null) callManager.setMicMuted(muted);
    }

    public void switchCamera() {
        if (callManager != null) callManager.switchCamera();
    }

    private void endWithError(String message) {
        if (ui != null) ui.onError(message);
        finishCall(message);
    }

    public void hangUp() {
        if (callId > 0) {
            apiClient.endCall(callId, null);
            if (accepted || CallActivity.MODE_OUTGOING.equals(mode)) sendEncryptedSignal("hangup", "{}");
        }
        finishCall(null);
    }

    private void finishCall(String reasonMessage) {
        if (ended) return;
        ended = true;
        pollHandler.removeCallbacksAndMessages(null);
        if (callManager != null) { callManager.dispose(); callManager = null; }
        if (ui != null) {
            ui.onCallEnded(reasonMessage);
        } else if (reasonMessage != null) {

            postShortNotification(reasonMessage);
        }
        stopForeground(true);
        try { NotificationManagerCompat.from(this).cancel(NOTIF_ID); } catch (Exception ignored) { }
        stopSelf();
    }

    private void postShortNotification(String text) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(peerName)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setTimeoutAfter(5000)
                .setAutoCancel(true);
        try { NotificationManagerCompat.from(this).notify(NOTIF_ID, b.build()); } catch (Exception ignored) { }
    }

    private void refreshNotification() {
        try {
            androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification());
        } catch (Exception ignored) { }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, CallActivity.class);
        open.putExtra(CallActivity.EXTRA_MODE, mode);
        open.putExtra(CallActivity.EXTRA_PEER_ID, peerId);
        open.putExtra(CallActivity.EXTRA_PEER_NAME, peerName);
        open.putExtra(CallActivity.EXTRA_PEER_COLOR, peerColor);
        open.putExtra(CallActivity.EXTRA_VIDEO, isVideo);
        open.putExtra(CallActivity.EXTRA_CALL_ID, callId);
        open.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPi = PendingIntent.getActivity(this, 2004, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent hangupIntent = new Intent(this, CallHangupReceiver.class);
        hangupIntent.setAction(CallHangupReceiver.ACTION_HANGUP);
        PendingIntent hangupPi = PendingIntent.getBroadcast(this, 2005, hangupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(peerName)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(openPi)
                .addAction(0, getString(R.string.notif_hangup), hangupPi);

        if (connectedAtMs > 0) {
            b.setUsesChronometer(true).setWhen(connectedAtMs)
                    .setContentText(getString(isVideo ? R.string.call_video_in_progress : R.string.call_in_progress));
        } else {
            b.setContentText(getString(CallActivity.MODE_INCOMING.equals(mode) ? R.string.call_incoming : R.string.call_calling));
        }
        return b.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, getString(R.string.call_ongoing_channel_name), NotificationManager.IMPORTANCE_HIGH);
                ch.setSound(null, null);
                nm.createNotificationChannel(ch);
            }
        }
    }
}
