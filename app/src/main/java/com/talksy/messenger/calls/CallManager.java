package com.talksy.messenger.calls;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.RtpTransceiver;
import org.webrtc.MediaStreamTrack;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

public class CallManager {
    private static final String TAG = "CallManager";

    public interface Listener {

        void onLocalDescription(SessionDescription sdp);

        void onIceCandidate(IceCandidate candidate);

        void onRemoteVideoTrack(VideoTrack track);
        void onConnectionStateChanged(PeerConnection.PeerConnectionState state);
        void onError(String message);
    }

    private final Context appContext;
    private final Listener listener;
    private final boolean videoEnabled;

    private EglBase eglBase;
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;

    private VideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private AudioTrack localAudioTrack;
    private VideoTrack localVideoTrack;

    private SurfaceViewRenderer localRenderer;
    private SurfaceViewRenderer remoteRenderer;

    public CallManager(Context ctx, boolean videoEnabled, Listener listener) {
        this.appContext = ctx.getApplicationContext();
        this.videoEnabled = videoEnabled;
        this.listener = listener;
    }

    public void start(List<PeerConnection.IceServer> iceServers) {
        PeerConnectionFactory.InitializationOptions initOptions =
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initOptions);

        eglBase = EglBase.create();

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .createPeerConnectionFactory();

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        peerConnection = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override public void onIceCandidate(IceCandidate candidate) {
                listener.onIceCandidate(candidate);
            }
            @Override public void onAddStream(MediaStream stream) {
                if (!stream.videoTracks.isEmpty()) {
                    listener.onRemoteVideoTrack(stream.videoTracks.get(0));
                }
            }
            @Override public void onTrack(RtpTransceiver transceiver) {
                MediaStreamTrack track = transceiver.getReceiver().track();
                if (track instanceof VideoTrack) {
                    listener.onRemoteVideoTrack((VideoTrack) track);
                }
            }
            @Override public void onConnectionChange(PeerConnection.PeerConnectionState state) {
                listener.onConnectionStateChanged(state);
            }
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) { }
            @Override public void onIceConnectionReceivingChange(boolean b) { }
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) { }
            @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) { }
            @Override public void onSignalingChange(PeerConnection.SignalingState s) { }
            @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) { }
            @Override public void onRemoveStream(MediaStream stream) { }
            @Override public void onDataChannel(org.webrtc.DataChannel channel) { }
            @Override public void onRenegotiationNeeded() { }
        });

        if (peerConnection == null) {
            listener.onError("Не удалось создать соединение (PeerConnection)");
            return;
        }

        setupLocalMedia();
    }

    private void setupLocalMedia() {
        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localAudioTrack = factory.createAudioTrack("audio0", audioSource);
        peerConnection.addTrack(localAudioTrack);

        if (videoEnabled) {
            videoCapturer = createCameraCapturer();
            if (videoCapturer != null) {
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
                VideoSource videoSource = factory.createVideoSource(false);
                videoCapturer.initialize(surfaceTextureHelper, appContext, videoSource.getCapturerObserver());
                videoCapturer.startCapture(1280, 720, 30);

                localVideoTrack = factory.createVideoTrack("video0", videoSource);
                if (localRenderer != null) localVideoTrack.addSink(localRenderer);
                peerConnection.addTrack(localVideoTrack);
            }
        }
    }

    private VideoCapturer createCameraCapturer() {
        Camera2Enumerator enumerator = new Camera2Enumerator(appContext);
        String[] names = enumerator.getDeviceNames();

        for (String name : names) {
            if (enumerator.isFrontFacing(name)) {
                CameraVideoCapturer capturer = enumerator.createCapturer(name, null);
                if (capturer != null) return capturer;
            }
        }
        for (String name : names) {
            CameraVideoCapturer capturer = enumerator.createCapturer(name, null);
            if (capturer != null) return capturer;
        }
        return null;
    }

    public void setLocalRenderer(SurfaceViewRenderer view) {
        this.localRenderer = view;
        view.init(eglBase.getEglBaseContext(), null);
        view.setMirror(true);
        if (localVideoTrack != null) localVideoTrack.addSink(view);
    }

    public void setRemoteRenderer(SurfaceViewRenderer view) {
        this.remoteRenderer = view;
        view.init(eglBase.getEglBaseContext(), null);
    }

    public void createOffer() {
        MediaConstraints constraints = new MediaConstraints();
        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sdp);
                listener.onLocalDescription(sdp);
            }
            @Override public void onCreateFailure(String error) {
                listener.onError("Не удалось подготовить предложение соединения: " + error);
            }
        }, constraints);
    }

    public void createAnswer() {
        MediaConstraints constraints = new MediaConstraints();
        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sdp);
                listener.onLocalDescription(sdp);
            }
            @Override public void onCreateFailure(String error) {
                listener.onError("Не удалось подготовить ответ на соединение: " + error);
            }
        }, constraints);
    }

    public void setRemoteDescription(SessionDescription.Type type, String sdp) {
        peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(type, sdp));
    }

    public void addIceCandidate(String sdpMid, int sdpMLineIndex, String candidate) {
        if (peerConnection != null) {
            peerConnection.addIceCandidate(new IceCandidate(sdpMid, sdpMLineIndex, candidate));
        }
    }

    public void setMicMuted(boolean muted) {
        if (localAudioTrack != null) localAudioTrack.setEnabled(!muted);
    }

    public void setCameraEnabled(boolean enabled) {
        if (localVideoTrack != null) localVideoTrack.setEnabled(enabled);
    }

    public void switchCamera() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            ((CameraVideoCapturer) videoCapturer).switchCamera(null);
        }
    }

    public void dispose() {
        try {
            if (videoCapturer != null) { videoCapturer.stopCapture(); videoCapturer.dispose(); }
        } catch (Exception e) { Log.w(TAG, "dispose videoCapturer", e); }
        if (surfaceTextureHelper != null) surfaceTextureHelper.dispose();
        if (localRenderer != null) localRenderer.release();
        if (remoteRenderer != null) remoteRenderer.release();
        if (peerConnection != null) { peerConnection.close(); peerConnection.dispose(); }
        if (factory != null) factory.dispose();
        if (eglBase != null) eglBase.release();
    }

    public static List<PeerConnection.IceServer> defaultIceServers(String turnUrl, String turnUser, String turnPass) {
        List<PeerConnection.IceServer> servers = new ArrayList<>();
        servers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        servers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        if (turnUrl != null && !turnUrl.isEmpty()) {
            PeerConnection.IceServer.Builder b = PeerConnection.IceServer.builder(turnUrl);
            if (turnUser != null && !turnUser.isEmpty()) b.setUsername(turnUser).setPassword(turnPass);
            servers.add(b.createIceServer());
        }
        return servers;
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sessionDescription) { }
        @Override public void onSetSuccess() { }
        @Override public void onCreateFailure(String s) { }
        @Override public void onSetFailure(String s) { }
    }
}
