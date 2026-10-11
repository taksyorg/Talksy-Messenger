package com.talksy.messenger.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.talksy.messenger.net.ApiClient;

import java.lang.ref.WeakReference;

public final class MiniAppHost {
    private MiniAppHost() {}

    public static final class Session {
        public WebView web;
        public android.content.MutableContextWrapper ctx;
        public String url, sessionId, title, botUsername, originHost;
        public boolean verified = true;
        public boolean minimized;
        public TalksyMiniAppBridge bridge;
        public WeakReference<MiniAppActivity> screen = new WeakReference<>(null);
    }

    private static Session current;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable onChange;

    public static Session get() { return current; }
    public static boolean hasSession() { return current != null; }
    public static boolean isMinimized() { return current != null && current.minimized; }
    public static void setOnChange(Runnable r) { onChange = r; }
    private static void changed() { if (onChange != null) MAIN.post(onChange); }

    public static boolean matches(String url, String sessionId) {
        return current != null && current.url != null && current.url.equals(url) && current.sessionId != null && current.sessionId.equals(sessionId);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static Session create(Context appCtx, String url, String sessionId, String title, String botUsername) {
        close();
        Session s = new Session();
        s.ctx = new android.content.MutableContextWrapper(appCtx.getApplicationContext());
        s.url = url; s.sessionId = sessionId; s.title = title; s.botUsername = botUsername;
        s.originHost = Uri.parse(url).getHost();
        s.web = new WebView(s.ctx);
        WebSettings st = s.web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setAllowFileAccess(false);
        st.setAllowContentAccess(false);
        st.setAllowFileAccessFromFileURLs(false);
        st.setAllowUniversalAccessFromFileURLs(false);
        st.setGeolocationEnabled(false);
        st.setSaveFormData(false);
        st.setUserAgentString(st.getUserAgentString() + " TalksyMiniApp/1.0 (" + (botUsername == null ? "" : botUsername) + ")");
        s.bridge = new TalksyMiniAppBridge(new ApiClient(appCtx.getApplicationContext()), sessionId, new TalksyMiniAppBridge.UiActions() {
            @Override public void closeMiniApp() { MAIN.post(MiniAppHost::closeFromPage); }
            @Override public void openBotChat() { MAIN.post(MiniAppHost::minimizeFromPage); }
        });
        s.web.addJavascriptInterface(s.bridge, "TalksyNative");
        current = s;
        changed();
        return s;
    }

    private static void closeFromPage() {
        Session s = current;
        MiniAppActivity a = s == null ? null : s.screen.get();
        if (a != null) a.closeAndFinish(); else close();
    }

    private static void minimizeFromPage() {
        Session s = current;
        MiniAppActivity a = s == null ? null : s.screen.get();
        if (a != null) a.minimizeAndFinish();
    }

    public static void setMinimized(boolean v) { if (current != null && current.minimized != v) { current.minimized = v; changed(); } }

    public static void close() {
        Session s = current;
        current = null;
        if (s != null && s.web != null) {
            try {
                if (s.web.getParent() instanceof ViewGroup) ((ViewGroup) s.web.getParent()).removeView(s.web);
                s.web.removeJavascriptInterface("TalksyNative");
                s.web.destroy();
            } catch (Exception ignored) {}
        }
        changed();
    }

    public static void attach(Session s, MiniAppActivity a, ViewGroup container) {
        s.ctx.setBaseContext(a);
        s.screen = new WeakReference<>(a);
        if (s.web.getParent() instanceof ViewGroup) ((ViewGroup) s.web.getParent()).removeView(s.web);
        container.addView(s.web, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        s.minimized = false;
        changed();
    }

    public static void detach(Session s, ViewGroup container) {
        try { container.removeView(s.web); } catch (Exception ignored) {}
        s.ctx.setBaseContext(((ContextWrapper) s.ctx).getApplicationContext());
        s.screen = new WeakReference<>(null);
    }
}
