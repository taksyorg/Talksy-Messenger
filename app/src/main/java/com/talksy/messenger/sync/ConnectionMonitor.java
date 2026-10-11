package com.talksy.messenger.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

public class ConnectionMonitor {
    public interface Listener { void onNetworkChanged(boolean hasInternet); }

    private final ConnectivityManager cm;
    private Listener listener;
    private boolean started;
    private final ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { fire(); }
        @Override public void onLost(Network n) { fire(); }
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { fire(); }
    };

    public ConnectionMonitor(Context ctx) {
        cm = (ConnectivityManager) ctx.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public boolean hasInternet() {
        try {
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (SecurityException e) { return true; }
    }

    public void start(Listener l) {
        listener = l;
        if (started) return;
        started = true;
        NetworkRequest req = new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        try { cm.registerNetworkCallback(req, cb); } catch (SecurityException ignored) {}
    }

    public void stop() {
        if (!started) return;
        started = false;
        try { cm.unregisterNetworkCallback(cb); } catch (Exception ignored) {}
    }

    private void fire() { Listener l = listener; if (l != null) l.onNetworkChanged(hasInternet()); }
}
