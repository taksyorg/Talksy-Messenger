package com.talksy.messenger;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.talksy.messenger.data.Prefs;

public class TalksyApp extends Application {
    private static android.content.Context sAppContext;

    public static android.content.Context appContext() { return sAppContext; }

    @Override
    public void onCreate() {
        super.onCreate();
        sAppContext = getApplicationContext();
        com.talksy.messenger.data.TrafficStats.init(this);
        com.talksy.messenger.net.MediaCache.init(this);
        com.talksy.messenger.net.MediaCache.trimAsync(this);
        startBackgroundSync();
        boolean dark = new Prefs(this).getBool(Prefs.KEY_DARK_THEME, true);
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void startBackgroundSync() {
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final com.talksy.messenger.sync.ConnectionMonitor monitor = new com.talksy.messenger.sync.ConnectionMonitor(this);
        monitor.start(has -> h.post(() -> {
            if (!has) return;
            com.talksy.messenger.sync.LegacyOutbox.get(this).flush();
            com.talksy.messenger.sync.LegacySync.get(this).run(true);
        }));
        h.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    com.talksy.messenger.sync.LegacyOutbox.get(TalksyApp.this).flush();
                    com.talksy.messenger.sync.LegacySync.get(TalksyApp.this).run();
                } catch (Throwable ignored) {}
                h.postDelayed(this, 30_000);
            }
        }, 3_000);
    }
}
