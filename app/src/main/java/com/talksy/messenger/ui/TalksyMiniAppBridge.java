package com.talksy.messenger.ui;

import android.webkit.JavascriptInterface;

import com.talksy.messenger.net.ApiClient;

import org.json.JSONObject;

public class TalksyMiniAppBridge {

    public interface UiActions {
        void closeMiniApp();
        void openBotChat();
    }

    private final ApiClient apiClient;
    private final String sessionId;
    private final UiActions uiActions;

    public TalksyMiniAppBridge(ApiClient apiClient, String sessionId, UiActions uiActions) {
        this.apiClient = apiClient;
        this.sessionId = sessionId;
        this.uiActions = uiActions;
    }

    @JavascriptInterface
    public String getUser() {
        return safeGet("/miniapp/api/user?session_id=" + enc(sessionId));
    }

    @JavascriptInterface
    public String getBalance() {
        return safeGet("/miniapp/api/wallet/balance?session_id=" + enc(sessionId));
    }

    @JavascriptInterface
    public String pay(String paramsJson) {
        try {
            JSONObject body = new JSONObject(paramsJson == null ? "{}" : paramsJson);
            body.put("session_id", sessionId);
            return apiClient.syncCall("POST", "/miniapp/api/wallet/pay", body).toString();
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    @JavascriptInterface
    public String sendMessage(String paramsJson) {
        try {
            JSONObject body = new JSONObject(paramsJson == null ? "{}" : paramsJson);
            body.put("session_id", sessionId);
            return apiClient.syncCall("POST", "/miniapp/api/sendMessage", body).toString();
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    @JavascriptInterface
    public String searchUsers(String query) {
        return safeGet("/miniapp/api/users/search?session_id=" + enc(sessionId) + "&q=" + enc(query));
    }

    @JavascriptInterface
    public String transfer(String paramsJson) {
        try {
            JSONObject body = new JSONObject(paramsJson == null ? "{}" : paramsJson);
            body.put("session_id", sessionId);
            return apiClient.syncCall("POST", "/miniapp/api/wallet/transfer", body).toString();
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    @JavascriptInterface
    public String history() {
        return safeGet("/miniapp/api/wallet/history?session_id=" + enc(sessionId));
    }

    @JavascriptInterface
    public void openChat() {
        if (uiActions != null) uiActions.openBotChat();
    }

    @JavascriptInterface
    public void close() {
        if (uiActions != null) uiActions.closeMiniApp();
    }

    private String safeGet(String path) {
        try {
            return apiClient.syncCall("GET", path, null).toString();
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    private String errorJson(Exception e) {
        try {
            return new JSONObject().put("ok", false).put("error", "network_error: " + e.getMessage()).toString();
        } catch (Exception e2) {
            return "{\"ok\":false,\"error\":\"network_error\"}";
        }
    }

    private String enc(String s) {
        try { return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }
}
