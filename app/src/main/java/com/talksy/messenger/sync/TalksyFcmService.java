package com.talksy.messenger.sync;

import android.provider.Settings;

import com.talksy.messenger.data.Prefs;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class TalksyFcmService extends FirebaseMessagingService {

    @Override public void onMessageReceived(RemoteMessage msg) {
        if ("sync".equals(msg.getData().get("type"))) SyncManager.get(this).requestSync("fcm");
    }

    @Override public void onNewToken(String token) { register(this, token); }

    public static void registerCurrent(android.content.Context ctx) {
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t -> register(ctx, t));
        } catch (Throwable ignored) {   }
    }

    private static void register(android.content.Context ctx, String token) {
        Prefs prefs = new Prefs(ctx);
        if (!prefs.getBool(Prefs.KEY_LOGGED_IN, false)) return;
        String deviceId = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        new Thread(() -> {
            boolean ok = new HttpRemoteApi(ctx).registerPushToken(deviceId, "android", token);

            prefs.putBool("push_registered", ok);
        }).start();
    }
}
