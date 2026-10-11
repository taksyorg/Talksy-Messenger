package com.talksy.messenger.net;

import android.content.Context;

import com.talksy.messenger.data.Prefs;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

public class ProxyManager {

    public static final String MODE_OFF = "off";
    public static final String MODE_SOCKS5 = "socks5";
    public static final String MODE_CUSTOM = "custom";

    private final Prefs prefs;

    public ProxyManager(Context ctx) {
        this.prefs = new Prefs(ctx);
    }

    public String getMode() {
        return prefs.get(Prefs.KEY_PROXY_MODE, MODE_OFF);
    }

    public void save(String mode, String host, int port, String user, String pass, String secret) {
        prefs.put(Prefs.KEY_PROXY_MODE, mode);
        prefs.put(Prefs.KEY_PROXY_HOST, host);
        prefs.put(Prefs.KEY_PROXY_PORT, String.valueOf(port));
        prefs.put(Prefs.KEY_PROXY_USER, user == null ? "" : user);
        prefs.put(Prefs.KEY_PROXY_PASS, pass == null ? "" : pass);
        prefs.put(Prefs.KEY_PROXY_SECRET, secret == null ? "" : secret);
    }

    public String getHost() { return prefs.get(Prefs.KEY_PROXY_HOST, ""); }
    public int getPort() {
        try { return Integer.parseInt(prefs.get(Prefs.KEY_PROXY_PORT, "0")); }
        catch (NumberFormatException e) { return 0; }
    }
    public String getUser() { return prefs.get(Prefs.KEY_PROXY_USER, ""); }
    public String getPass() { return prefs.get(Prefs.KEY_PROXY_PASS, ""); }
    public String getSecret() { return prefs.get(Prefs.KEY_PROXY_SECRET, ""); }

    public Proxy getJavaProxyOrNull() {
        if (!MODE_SOCKS5.equals(getMode())) return null;
        String host = getHost();
        int port = getPort();
        if (host.isEmpty() || port <= 0) return null;
        return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
    }

    public boolean testConnection() {
        String mode = getMode();
        String host = getHost();
        int port = getPort();
        if (host.isEmpty() || port <= 0) return false;

        if (MODE_SOCKS5.equals(mode)) {
            try (Socket s = new Socket(getJavaProxyOrNull())) {
                s.connect(new InetSocketAddress("example.com", 443), 8000);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        if (MODE_CUSTOM.equals(mode)) {
            String secret = getSecret();
            if (secret.isEmpty()) return false;
            return TalksyProxyClient.test(host, port, secret);
        }

        return false;
    }
}
