package com.talksy.messenger.ui;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.sync.SyncManager;

public final class WalletLauncher {
    private WalletLauncher() {}

    public static void open(Activity a) {
        if (!SyncManager.get(a).hasInternet()) { Toast.makeText(a, R.string.needs_internet, Toast.LENGTH_SHORT).show(); return; }
        new ApiClient(a).openWallet((ok, s, err) -> {
            if (!ok || s == null) { Toast.makeText(a, err != null ? err : a.getString(R.string.error_generic), Toast.LENGTH_LONG).show(); return; }
            Intent i = new Intent(a, MiniAppActivity.class);
            i.putExtra("url", s.url); i.putExtra("session_id", s.sessionId); i.putExtra("title", s.name); i.putExtra("bot_username", s.botUsername);
            a.startActivity(i);
            AnimUtils.forward(a);
        });
    }
}
