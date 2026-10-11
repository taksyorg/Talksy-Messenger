package com.talksy.messenger.net;

import android.content.Context;
import android.provider.Settings;

import java.security.MessageDigest;

public final class DeviceId {
    private DeviceId() {}

    public static String get(Context c) {
        try {
            String raw = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (raw == null || raw.isEmpty()) return "";
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("talksy:" + raw).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
