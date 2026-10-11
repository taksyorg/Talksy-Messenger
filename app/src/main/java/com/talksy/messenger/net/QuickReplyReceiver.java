package com.talksy.messenger.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;

import com.talksy.messenger.R;
import com.talksy.messenger.data.Prefs;

public class QuickReplyReceiver extends BroadcastReceiver {

    public static final String ACTION_REPLY = "com.talksy.messenger.QUICK_REPLY";
    public static final String EXTRA_PEER_ID = "peer_id";
    public static final String EXTRA_NOTIF_ID = "notif_id";
    public static final String KEY_TEXT_REPLY = "key_text_reply";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_REPLY.equals(intent.getAction())) return;

        Bundle results = RemoteInput.getResultsFromIntent(intent);
        CharSequence typed = results != null ? results.getCharSequence(KEY_TEXT_REPLY) : null;
        long peerId = intent.getLongExtra(EXTRA_PEER_ID, 0);
        int notifId = intent.getIntExtra(EXTRA_NOTIF_ID, (int) peerId);
        if (typed == null || typed.toString().trim().isEmpty() || peerId <= 0) return;
        final String text = typed.toString().trim();

        final PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        ApiClient apiClient = new ApiClient(app);
        apiClient.setBackground(true);
        boolean e2e = new Prefs(app).getBool(Prefs.KEY_E2E_ENABLED, false);

        if (!e2e) {
            apiClient.sendMessage(peerId, text, null, 0, (ok, msg, error) -> {
                confirmSent(app, notifId, ok, text);
                pending.finish();
            });
            return;
        }

        apiClient.getUserProfile(peerId, (ok, user, error) -> {
            if (ok && user != null && user.publicKey != null && !user.publicKey.isEmpty()) {
                try {
                    CryptoManager.DirectEncrypted enc = CryptoManager.encryptDirect(text, user.publicKey);
                    apiClient.sendEncryptedMessage(peerId, enc, 0, (ok2, msg, error2) -> {
                        confirmSent(app, notifId, ok2, text);
                        pending.finish();
                    });
                } catch (Exception e) {
                    confirmSent(app, notifId, false, text);
                    pending.finish();
                }
            } else {

                apiClient.sendMessage(peerId, text, null, 0, (ok2, msg, error2) -> {
                    confirmSent(app, notifId, ok2, text);
                    pending.finish();
                });
            }
        });
    }

    private void confirmSent(Context ctx, int notifId, boolean ok, String text) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, MessagePollService.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentText(ok ? ctx.getString(R.string.notif_reply_sent, text)
                        : ctx.getString(R.string.notif_reply_failed))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTimeoutAfter(4000)
                .setAutoCancel(true);
        try { NotificationManagerCompat.from(ctx).notify(notifId, b.build()); } catch (SecurityException ignored) { }
    }
}
