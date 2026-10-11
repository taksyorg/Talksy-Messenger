package com.talksy.messenger.net;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.talksy.messenger.R;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.data.model.Chat;
import com.talksy.messenger.data.model.Message;
import com.talksy.messenger.ui.ChatActivity;
import com.talksy.messenger.ui.CallActivity;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import androidx.core.app.RemoteInput;

public class MessagePollService extends Service {

    public static final String CHANNEL_ID = "talksy_messages_v2";
    private static final long POLL_INTERVAL_MS = 5000;
    private static final int FOREGROUND_NOTIF_ID = 1;

    private static final String CALL_CHANNEL_ID = "talksy_calls";
    private static final int CALL_NOTIF_ID = 2001;
    private static final long CALL_POLL_INTERVAL_MS = 2000;
    public static final String ACTION_DECLINE_CALL = "com.talksy.messenger.DECLINE_CALL";
    public static final String ACTION_NOTIFY = "com.talksy.messenger.NOTIFY_FROM_SYNC";
    private long lastCallNotified = -1;

    private final Runnable callRunnable = new Runnable() {
        @Override public void run() {
            pollCalls();
            handler.postDelayed(this, CALL_POLL_INTERVAL_MS);
        }
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ApiClient apiClient;
    private Prefs prefs;

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            poll();
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public static void start(Context ctx) {
        Intent intent = new Intent(ctx, MessagePollService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
    }

    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, MessagePollService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        apiClient = new ApiClient(this);
        apiClient.setBackground(true);
        prefs = new Prefs(this);
        createChannel();
        createCallChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(FOREGROUND_NOTIF_ID, buildForegroundNotification());

        if (intent != null && ACTION_DECLINE_CALL.equals(intent.getAction())) {
            long id = intent.getLongExtra(CallActivity.EXTRA_CALL_ID, -1);
            if (id > 0) apiClient.rejectCall(id, null);
            NotificationManagerCompat.from(this).cancel(CALL_NOTIF_ID);
        }

        handler.removeCallbacks(pollRunnable);
        handler.post(pollRunnable);
        handler.removeCallbacks(callRunnable);
        handler.post(callRunnable);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(pollRunnable);
        handler.removeCallbacks(callRunnable);
        super.onDestroy();
    }

    private void pollServerNotifications() {
        final long last = prefs.getLong("srv_notif_last", -1);
        apiClient.economyGet("/notifications?after_id=" + Math.max(0, last), (ok, json, err) -> {
            if (!ok || json == null) return;
            org.json.JSONArray arr = json.optJSONArray("notifications");
            long max = Math.max(0, last);
            for (int i = 0; arr != null && i < arr.length(); i++) {
                org.json.JSONObject n = arr.optJSONObject(i);
                max = Math.max(max, n.optLong("id"));
                if (last < 0) continue;
                NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_logo)
                        .setContentTitle(n.optString("title"))
                        .setContentText(n.optString("body"))
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(n.optString("body")))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);
                try { NotificationManagerCompat.from(this).notify(900000 + (int) n.optLong("id"), b.build()); }
                catch (SecurityException ignored) { }
            }
            prefs.putLong("srv_notif_last", max);
        });
    }

    private void poll() {
        if (!prefs.getBool(Prefs.KEY_LOGGED_IN, false) || !apiClient.isConfigured()) return;
        pollServerNotifications();

        apiClient.getChats((ok, chats, error) -> {
            if (!ok || chats == null) return;
            for (Chat c : chats) {
                String key = "notif_seen_" + c.peerUserId;
                long seen = prefs.getLong(key, 0);
                if (c.lastMessageTime > seen) {
                    if (!c.lastOutgoing) {
                        fetchAndNotify(c, seen);
                    }
                    prefs.putLong(key, c.lastMessageTime);
                }
            }
        });
    }

    private void fetchAndNotify(Chat c, long since) {
        apiClient.getMessages(c.peerUserId, since, (ok, list, error) -> {
            if (!ok || list == null || list.isEmpty()) { showNotification(c, c.lastMessage); return; }
            Message last = null;
            for (int i = list.size() - 1; i >= 0; i--) {
                if (!list.get(i).outgoing) { last = list.get(i); break; }
            }
            if (last == null) { showNotification(c, c.lastMessage); return; }
            if (last.encText != null && !last.encText.isEmpty()) {
                try {
                    showNotification(c, CryptoManager.decryptDirect(last.encText, last.encKeyRecipient, last.iv));
                } catch (Exception e) {
                }
            } else {
                String body = last.text != null && !last.text.isEmpty() ? last.text : c.lastMessage;
                String card = com.talksy.messenger.data.SpecialMessage.previewRu(body);
                showNotification(c, card != null ? card : body);
            }
        });
    }

    private void pollCalls() {
        if (!prefs.getBool(Prefs.KEY_LOGGED_IN, false) || !apiClient.isConfigured()) return;
        apiClient.getIncomingCall((ok, call, error) -> {
            if (!ok) return;
            if (call == null) {
                if (lastCallNotified != -1) {
                    NotificationManagerCompat.from(this).cancel(CALL_NOTIF_ID);
                    lastCallNotified = -1;
                }
                return;
            }
            if (CallActivity.isShowing) return;
            if (call.id == lastCallNotified) return;
            lastCallNotified = call.id;
            showCallNotification(call);
        });
    }

    private void showCallNotification(ApiClient.CallInfo call) {
        Intent open = new Intent(this, CallActivity.class);
        open.putExtra(CallActivity.EXTRA_MODE, CallActivity.MODE_INCOMING);
        open.putExtra(CallActivity.EXTRA_CALL_ID, call.id);
        open.putExtra(CallActivity.EXTRA_PEER_ID, call.callerId);
        open.putExtra(CallActivity.EXTRA_PEER_NAME, call.callerName);
        open.putExtra(CallActivity.EXTRA_PEER_COLOR, call.callerColor);
        open.putExtra(CallActivity.EXTRA_VIDEO, "video".equals(call.callType));
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        PendingIntent openPi = PendingIntent.getActivity(this, 2001, open, piFlags);

        Intent acceptIntent = new Intent(open);
        acceptIntent.putExtra(CallActivity.EXTRA_AUTO_ACCEPT, true);
        PendingIntent acceptPi = PendingIntent.getActivity(this, 2002, acceptIntent, piFlags);

        Intent declineIntent = new Intent(this, MessagePollService.class);
        declineIntent.setAction(ACTION_DECLINE_CALL);
        declineIntent.putExtra(CallActivity.EXTRA_CALL_ID, call.id);
        PendingIntent declinePi = PendingIntent.getService(this, 2003, declineIntent, piFlags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(call.callerName)
                .setContentText(getString(R.string.call_incoming))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(true)
                .setTimeoutAfter(60000)
                .setContentIntent(openPi)
                .setFullScreenIntent(openPi, true)
                .addAction(0, getString(R.string.notif_decline), declinePi)
                .addAction(0, getString(R.string.notif_accept), acceptPi);

        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT;

        try {
            NotificationManagerCompat.from(this).notify(CALL_NOTIF_ID, n);
        } catch (SecurityException ignored) {

        }
    }

    private void createCallChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CALL_CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CALL_CHANNEL_ID, getString(R.string.call_channel_name), NotificationManager.IMPORTANCE_HIGH);
                Uri ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                ch.setSound(ring, attrs);
                ch.enableVibration(true);
                ch.setVibrationPattern(new long[]{0, 700, 500, 700, 500, 700});
                ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(ch);
            }
        }
    }

    private void showNotification(Chat c, String text) {

        if (com.talksy.messenger.ui.ChatActivity.openPeerId == c.peerUserId) return;
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("peer_id", c.peerUserId);
        i.putExtra("peer_name", c.peerName);
        i.putExtra("peer_color", c.peerColorHex);
        i.putExtra("peer_avatar_url", c.peerAvatarUrl);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int notifId = (int) c.peerUserId;
        PendingIntent pi = PendingIntent.getActivity(
                this, notifId, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent replyIntent = new Intent(this, QuickReplyReceiver.class);
        replyIntent.setAction(QuickReplyReceiver.ACTION_REPLY);
        replyIntent.putExtra(QuickReplyReceiver.EXTRA_PEER_ID, c.peerUserId);
        replyIntent.putExtra(QuickReplyReceiver.EXTRA_NOTIF_ID, notifId);
        PendingIntent replyPi = PendingIntent.getBroadcast(this, notifId, replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        RemoteInput remoteInput = new RemoteInput.Builder(QuickReplyReceiver.KEY_TEXT_REPLY)
                .setLabel(getString(R.string.notif_reply_hint))
                .build();
        NotificationCompat.Action replyAction =
                new NotificationCompat.Action.Builder(0, getString(R.string.notif_reply), replyPi)
                        .addRemoteInput(remoteInput)
                        .setAllowGeneratedReplies(true)
                        .build();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(c.peerName)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                .setVibrate(new long[]{0, 250, 150, 250})
                .setAutoCancel(true)
                .setContentIntent(pi)
                .addAction(replyAction);

        try {
            NotificationManagerCompat.from(this).notify(notifId, builder.build());
        } catch (SecurityException ignored) {

        }
    }

    private static final String SERVICE_CHANNEL_ID = "talksy_service_silent";

    private Notification buildForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(SERVICE_CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(SERVICE_CHANNEL_ID, "Фоновая работа", NotificationManager.IMPORTANCE_MIN);
                ch.setShowBadge(false);
                ch.setSound(null, null);
                ch.enableVibration(false);
                nm.createNotificationChannel(ch);
            }
        }
        return new NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle("Talksy")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription(getString(R.string.notif_channel_desc));
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 250, 150, 250});
                nm.createNotificationChannel(channel);
            }
        }
    }
}
