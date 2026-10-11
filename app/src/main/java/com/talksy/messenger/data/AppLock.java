package com.talksy.messenger.data;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class AppLock {
    private AppLock() {}

    public static final int PIN_LENGTH = 6;
    public static final long GRACE_MS = 0;

    private static final int ITERATIONS = 120_000;
    private static final String SP = "talksy_lock";

    private static boolean unlocked = false;
    private static long backgroundAt = 0;
    private static int startedActivities = 0;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context c) { return sp(c).contains("hash"); }
    public static boolean biometricEnabled(Context c) { return sp(c).getBoolean("biometric", false); }
    public static void setBiometric(Context c, boolean on) { sp(c).edit().putBoolean("biometric", on).apply(); }

    public static void setPin(Context c, String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        sp(c).edit()
                .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("hash", Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
                .putInt("fails", 0).putLong("locked_until", 0)
                .apply();
        unlocked = true;
    }

    public static void disable(Context c) {
        sp(c).edit().clear().apply();
        unlocked = true;
    }

    public static long secondsUntilRetry(Context c) {
        long left = sp(c).getLong("locked_until", 0) - System.currentTimeMillis();
        return left > 0 ? (left + 999) / 1000 : 0;
    }

    public static boolean verify(Context c, String pin) {
        if (secondsUntilRetry(c) > 0) return false;
        SharedPreferences p = sp(c);
        String saltB64 = p.getString("salt", null), hashB64 = p.getString("hash", null);
        if (saltB64 == null || hashB64 == null) return true;
        byte[] expected = Base64.decode(hashB64, Base64.NO_WRAP);
        byte[] actual = hash(pin, Base64.decode(saltB64, Base64.NO_WRAP));
        if (MessageDigest.isEqual(expected, actual)) {
            p.edit().putInt("fails", 0).putLong("locked_until", 0).apply();
            unlocked = true;
            return true;
        }
        int fails = p.getInt("fails", 0) + 1;
        SharedPreferences.Editor e = p.edit().putInt("fails", fails);
        if (fails >= 5) {
            long pauseSec = Math.min(15 * 60, 30L * (1L << Math.min(5, fails - 5)));
            e.putLong("locked_until", System.currentTimeMillis() + pauseSec * 1000);
        }
        e.apply();
        return false;
    }

    private static byte[] hash(String pin, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void markUnlocked() { unlocked = true; }

    public static boolean shouldLock(Context c) { return isEnabled(c) && !unlocked; }

    public static void onActivityStarted(Activity a) {
        if (startedActivities == 0 && isEnabled(a)
                && System.currentTimeMillis() - backgroundAt > GRACE_MS) {
            unlocked = false;
        }
        startedActivities++;
    }

    public static void onActivityStopped() {
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities == 0) backgroundAt = System.currentTimeMillis();
    }
}
