package com.talksy.messenger.net;
import org.json.JSONException;

import android.content.Context;
import android.os.AsyncTask;

import com.talksy.messenger.R;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.data.model.Bot;
import com.talksy.messenger.data.model.Chat;
import com.talksy.messenger.data.model.Group;
import com.talksy.messenger.data.model.MediaAttachment;
import com.talksy.messenger.data.model.Message;
import com.talksy.messenger.data.model.StoryGroup;
import com.talksy.messenger.data.model.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ApiClient {

    public static final String DEFAULT_BASE_URL = com.talksy.messenger.BuildConfig.DEFAULT_BASE_URL;

    public interface Callback<T> { void onResult(boolean ok, T data, String error); }

    private final Context context;
    private final Prefs prefs;
    private final ProxyManager proxyManager;

    private boolean background = false;
    public void setBackground(boolean b) { this.background = b; }

    public static class PeerMeta {
        public boolean online, typing;
        public Long lastSeenMs;
        public long lastReadMs;
        public long pinnedId;
    }
    public volatile PeerMeta lastPeerMeta;
    public volatile List<Message> lastUpdates = new ArrayList<>();
    private double updCursor = -1;

    public void sendTyping(long peerId) {
        runPost("/chats/typing", body -> body.put("peer_id", peerId), (json, err) -> { });
    }

    public ApiClient(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.prefs = new Prefs(ctx);
        this.proxyManager = new ProxyManager(ctx);
    }

    public String getBaseUrl() { return prefs.get(Prefs.KEY_API_BASE_URL, DEFAULT_BASE_URL); }
    public void setBaseUrl(String url) { prefs.put(Prefs.KEY_API_BASE_URL, url.trim()); }
    public boolean isConfigured() { return !getBaseUrl().isEmpty(); }
    private String getToken() { return prefs.get(Prefs.KEY_AUTH_TOKEN, ""); }

    public void requestEmailCode(String email, Callback<Void> cb) {
        runPost("/auth/email", body -> body.put("email", email), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void verifyEmailCode(String email, String code, String localPhone, String deviceLabel, Callback<AuthResult> cb) {
        runPost("/auth/verify", body -> {
            body.put("email", email);
            body.put("code", code);
            body.put("phone", localPhone == null ? "" : localPhone);
            body.put("device", deviceLabel == null ? "" : deviceLabel);
            body.put("device_id", DeviceId.get(context));
            body.put("app", "Talksy");
            body.put("app_version", appVersionName());
            body.put("platform", "android");
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            String token = json.optString("token");
            boolean isNew = json.optBoolean("is_new", false);
            prefs.put(Prefs.KEY_AUTH_TOKEN, token);
            JSONObject user = json.optJSONObject("user");
            if (user != null) cacheProfile(user);
            cb.onResult(true, new AuthResult(token, isNew), null);
        });
    }

    public void loginWithNumber(String number, String password, String pin, String deviceLabel, JsonCallback cb) {
        runPost("/auth/number/login", body -> {
            body.put("number", number);
            body.put("password", password);
            if (pin != null && !pin.isEmpty()) body.put("pin", pin);
            body.put("device", deviceLabel == null ? "" : deviceLabel);
            body.put("device_id", DeviceId.get(context));
            body.put("app", "Talksy");
            body.put("app_version", appVersionName());
            body.put("platform", "android");
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            boolean ok = json.optBoolean("ok");
            if (ok) {
                prefs.put(Prefs.KEY_AUTH_TOKEN, json.optString("token"));
                JSONObject user = json.optJSONObject("user");
                if (user != null) cacheProfile(user);
            }
            cb.onResult(ok, json, json.optString("error", null));
        });
    }

    public void findByUsername(String username, Callback<User> cb) {
        runGet("/users/by-username?username=" + encode(username), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, userFromJson(json.optJSONObject("user")), null);
        });
    }

    public String authedUrl(String path) {
        return getBaseUrl() + path + (path.contains("?") ? "&" : "?") + "token=" + encode(getToken());
    }

    public void uploadPublicKey(String publicKeyBase64, Callback<Void> cb) {
        runPost("/me/public-key", body -> body.put("public_key", publicKeyBase64), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void terminateSession(long sessionId, boolean others, Callback<Void> cb) {
        runPost("/me/sessions/terminate", b -> { if (others) b.put("others", true); else b.put("session_id", sessionId); }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void setSessionSettings(long sessionId, Boolean acceptSecret, Boolean acceptCalls, Callback<Void> cb) {
        runPost("/me/sessions/" + sessionId + "/settings", b -> {
            if (acceptSecret != null) b.put("accept_secret", acceptSecret.booleanValue());
            if (acceptCalls != null) b.put("accept_calls", acceptCalls.booleanValue());
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void setSessionAutoTerminateDays(int days, Callback<Integer> cb) {
        runPost("/me/sessions/auto", b -> b.put("days", days), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), json.optInt("days", days), json.optString("error", null));
        });
    }

    private String appVersionName() {
        try { return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName; }
        catch (Exception e) { return ""; }
    }

    public static class SessionList {
        public final List<com.talksy.messenger.data.model.DeviceSession> sessions = new ArrayList<>();
        public int autoDays = 180;
    }

    public void getSessionList(Callback<SessionList> cb) {
        runGet("/me/sessions", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            SessionList out = new SessionList();
            out.autoDays = json.optInt("auto_days", 180);
            JSONArray arr = json.optJSONArray("sessions");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject s = arr.optJSONObject(i); if (s == null) continue;
                com.talksy.messenger.data.model.DeviceSession ds = new com.talksy.messenger.data.model.DeviceSession();
                ds.id = s.optLong("id"); ds.device = s.optString("device", ""); ds.app = s.optString("app", "Talksy");
                ds.appVersion = s.optString("app_version", ""); ds.platform = s.optString("platform", "other");
                ds.city = s.optString("city", ""); ds.country = s.optString("country", "");
                ds.createdAtMillis = (long) (s.optDouble("created_at", 0) * 1000); ds.lastActiveMillis = (long) (s.optDouble("last_active", 0) * 1000);
                ds.online = s.optBoolean("online"); ds.isCurrent = s.optBoolean("is_current");
                ds.acceptSecret = s.optBoolean("accept_secret", true); ds.acceptCalls = s.optBoolean("accept_calls", true);
                out.sessions.add(ds);
            }
            cb.onResult(true, out, null);
        });
    }

    public void getAccountTtl(Callback<Integer> cb) {
        runGet("/me/account_ttl", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", null)); return; }
            cb.onResult(true, json.optInt("months", 12), null);
        });
    }

    public void setAccountTtl(int months, Callback<Integer> cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("months", months);
            runPost("/me/account_ttl", b -> { b.put("days", body.optInt("days")); }, (json, err) -> {
                if (err != null) { cb.onResult(false, null, err); return; }
                if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", null)); return; }
                cb.onResult(true, json.optInt("months", months), null);
            });
        } catch (JSONException e) { cb.onResult(false, null, e.getMessage()); }
    }

    public void deleteChat(long peerId, boolean forEveryone, Callback<Void> cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("peer_id", peerId);
            body.put("for_everyone", forEveryone);
            runPost("/chats/delete", b -> { b.put("peer_id", body.optString("peer_id")); }, (json, err) -> {
                if (err != null) { cb.onResult(false, null, err); return; }
                cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
            });
        } catch (JSONException e) { cb.onResult(false, null, e.getMessage()); }
    }

    public void getChatTtl(long peerId, Callback<Integer> cb) {
        runGet("/chats/ttl?peer_id=" + peerId, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", null)); return; }
            cb.onResult(true, json.optInt("ttl_seconds", 0), null);
        });
    }

    public void setChatTtl(long peerId, int ttlSeconds, Callback<Integer> cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("peer_id", peerId);
            body.put("ttl_seconds", ttlSeconds);
            runPost("/chats/ttl", b -> { b.put("peer_id", body.optLong("peer_id")); b.put("ttl_seconds", body.optInt("ttl_seconds")); }, (json, err) -> {
                if (err != null) { cb.onResult(false, null, err); return; }
                if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", null)); return; }
                cb.onResult(true, json.optInt("ttl_seconds", ttlSeconds), null);
            });
        } catch (JSONException e) { cb.onResult(false, null, e.getMessage()); }
    }

    public void scheduleMessage(long peerId, String text, long sendAtMillis, Callback<Long> cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("to_id", peerId);
            body.put("text", text);
            body.put("send_at", sendAtMillis / 1000.0);
            runPost("/messages/schedule", b -> { b.put("to_id", body.optLong("to_id")); b.put("text", body.optString("text")); b.put("send_at", body.optDouble("send_at")); }, (json, err) -> {
                if (err != null) { cb.onResult(false, null, err); return; }
                if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", null)); return; }
                cb.onResult(true, json.optLong("id"), null);
            });
        } catch (JSONException e) { cb.onResult(false, null, e.getMessage()); }
    }

    public void getSessions(Callback<List<com.talksy.messenger.data.model.DeviceSession>> cb) {
        runGet("/me/sessions", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            List<com.talksy.messenger.data.model.DeviceSession> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("sessions");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.optJSONObject(i);
                    com.talksy.messenger.data.model.DeviceSession ds = new com.talksy.messenger.data.model.DeviceSession();
                    ds.device = s.optString("device", "");
                    ds.city = s.optString("city", "");
                    ds.country = s.optString("country", "");
                    ds.createdAtMillis = (long) (s.optDouble("created_at", 0) * 1000);
                    ds.id = s.optLong("id", 0);
                    ds.isCurrent = s.optBoolean("is_current", false);
                    out.add(ds);
                }
            }
            cb.onResult(true, out, null);
        });
    }

    public void uploadAvatar(String imageBase64, Callback<User> cb) {
        runPost("/me/avatar", body -> body.put("image_base64", imageBase64), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            JSONObject u = json.optJSONObject("user");
            cacheProfile(u);
            cb.onResult(true, userFromJson(u), null);
        });
    }

    public String fullAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) return null;
        return getBaseUrl() + avatarUrl;
    }

    public void getProfile(Callback<User> cb) {
        runGet("/me", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            JSONObject u = json.optJSONObject("user");
            cacheProfile(u);
            cb.onResult(true, userFromJson(u), null);
        });
    }

    public void updateProfile(String name, String handle, String bio, Callback<User> cb) {
        updateProfile(name, handle, bio, null, cb);
    }

    public void updateProfile(String name, String handle, String bio, String color, Callback<User> cb) {
        updateProfile(name, handle, bio, color, null, null, cb);
    }

    public void updateProfile(String name, String handle, String bio, String color,
                               Boolean hidePhone, Boolean hideHandle, Callback<User> cb) {
        updateProfile(name, handle, bio, color, hidePhone, hideHandle, null, cb);
    }

    public void updateProfile(String name, String handle, String bio, String color,
                               Boolean hidePhone, Boolean hideHandle, Boolean hideLastSeen, Callback<User> cb) {
        runPost("/me/update", body -> {
            body.put("name", name);
            body.put("handle", handle);
            body.put("bio", bio);
            if (color != null) body.put("color", color);
            if (hidePhone != null) body.put("hide_phone", hidePhone);
            if (hideHandle != null) body.put("hide_handle", hideHandle);
            if (hideLastSeen != null) body.put("hide_last_seen", hideLastSeen);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            JSONObject u = json.optJSONObject("user");
            cacheProfile(u);
            cb.onResult(true, userFromJson(u), null);
        });
    }

    public void getUserProfile(long userId, Callback<User> cb) {
        runGet("/users/" + userId, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, userFromJson(json.optJSONObject("user")), null);
        });
    }

    public void blockUser(long userId, Callback<Void> cb) {
        runPost("/users/" + userId + "/block", body -> {}, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void unblockUser(long userId, Callback<Void> cb) {
        runPost("/users/" + userId + "/unblock", body -> {}, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public interface JsonCallback { void onResult(boolean ok, JSONObject json, String error); }

    public void supportGet(String path, JsonCallback cb) {
        runGet(path, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), json, json.optString("error", null));
        });
    }

    public void supportPost(String path, JSONObject extra, JsonCallback cb) {
        runPost(path, body -> {
            if (extra != null) {
                java.util.Iterator<String> it = extra.keys();
                while (it.hasNext()) { String k = it.next(); body.put(k, extra.get(k)); }
            }
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), json, json.optString("error", null));
        });
    }

    public void economyGet(String path, JsonCallback cb) {
        runGet(path, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), json, json.optString("error", null));
        });
    }

    public void economyPost(String path, JSONObject extra, JsonCallback cb) {
        runPost(path, body -> {
            if (extra != null) {
                java.util.Iterator<String> it = extra.keys();
                while (it.hasNext()) { String k = it.next(); body.put(k, extra.get(k)); }
            }
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), json, json.optString("error", null));
        });
    }

    public void getBlockedUsers(Callback<List<User>> cb) {
        runGet("/users/blocked", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            List<User> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("users");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) out.add(userFromJson(arr.optJSONObject(i)));
            }
            cb.onResult(true, out, null);
        });
    }

    public static class MiniAppSession {
        public String sessionId, url, name, botUsername;
        public List<String> permissions = new ArrayList<>();
    }

    public void sendBotCallback(long messageId, String data, Callback<JSONObject> cb) {
        runPost("/messages/" + messageId + "/callback", body -> body.put("data", data), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, json, null);
        });
    }

    public void createMiniAppSession(long botId, Callback<MiniAppSession> cb) {
        runPost("/miniapp/session", body -> body.put("bot_id", botId), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            JSONObject r = json.optJSONObject("result");
            if (r == null) { cb.onResult(false, null, context.getString(R.string.error_generic)); return; }
            MiniAppSession s = new MiniAppSession();
            s.sessionId = r.optString("session_id");
            s.url = r.optString("url");
            s.name = r.optString("name");
            s.botUsername = r.optString("bot_username");
            JSONArray perms = r.optJSONArray("permissions");
            if (perms != null) for (int i = 0; i < perms.length(); i++) s.permissions.add(perms.optString(i));
            cb.onResult(true, s, null);
        });
    }

    public void setChatState(String peerType, long peerId, Boolean pinned, Boolean archived, Boolean muted, Callback<Void> cb) {
        runPost("/chats/state", body -> {
            body.put("peer_type", peerType);
            body.put("peer_id", peerId);
            if (pinned != null) body.put("pinned", pinned);
            if (archived != null) body.put("archived", archived);
            if (muted != null) body.put("muted", muted);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void updateGroupInfo(long groupId, String name, String handle, Callback<Group> cb) {
        runPost("/groups/" + groupId + "/settings", body -> {
            if (name != null) body.put("name", name);
            if (handle != null) body.put("handle", handle);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, groupFromJson(json.optJSONObject("group")), null);
        });
    }

    public void uploadGroupAvatar(long groupId, String imageBase64, Callback<Group> cb) {
        runPost("/groups/" + groupId + "/avatar", body -> body.put("image_base64", imageBase64), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, groupFromJson(json.optJSONObject("group")), null);
        });
    }

    private void cacheProfile(JSONObject u) {
        if (u == null) return;
        prefs.put(Prefs.KEY_USER_ID, String.valueOf(u.optLong("id")));
        prefs.put(Prefs.KEY_MY_NAME, u.optString("name", ""));
        prefs.put(Prefs.KEY_MY_HANDLE, u.optString("handle", ""));
        prefs.put(Prefs.KEY_MY_BIO, u.optString("bio", ""));
        prefs.put(Prefs.KEY_MY_COLOR, u.optString("color", "#6366F1"));
        prefs.put(Prefs.KEY_MY_AVATAR_URL, u.optString("avatar_url", ""));
    }

    private User userFromJson(JSONObject u) {
        User user = new User();
        user.id = u.optLong("id");
        user.name = u.optString("name", "");
        user.handle = u.isNull("handle") ? null : u.optString("handle", null);
        user.email = u.isNull("email") ? null : u.optString("email", null);
        user.phone = u.isNull("phone") ? null : u.optString("phone", null);
        user.bio = u.optString("bio", "");
        user.colorHex = u.optString("color", "#6366F1");
        user.avatarUrl = u.optString("avatar_url", "");
        user.hidePhone = u.optBoolean("hide_phone", false);
        user.hideHandle = u.optBoolean("hide_handle", false);
        user.hideLastSeen = u.optBoolean("hide_last_seen", false);
        user.lastSeen = u.isNull("last_seen") ? null : (long) (u.optDouble("last_seen", 0) * 1000);
        user.publicKey = u.isNull("public_key") ? null : u.optString("public_key", null);
        user.iBlockedThem = u.optBoolean("i_blocked_them", false);
        user.whoCanWrite = u.optString("who_can_write", "all");
        user.isBot = u.optBoolean("is_bot", false);
        user.isPremium = u.optBoolean("premium", false);
        user.hasMiniApp = u.optBoolean("has_mini_app", false);
        user.miniAppName = u.optString("mini_app_name", "");
        user.botKeyboard = u.isNull("bot_keyboard") ? null : u.optString("bot_keyboard", null);
        user.botUsersCount = u.optLong("bot_users_count", 0);
        user.canMessage = u.optBoolean("can_message", true);
        return user;
    }

    public void findUsersByPhones(List<String> phones, Callback<List<User>> cb) {
        runPost("/users/find-by-phones", body -> {
            JSONArray arr = new JSONArray();
            for (String p : phones) arr.put(p);
            body.put("phones", arr);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            List<User> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("users");
            if (arr != null) for (int i = 0; i < arr.length(); i++) out.add(userFromJson(arr.optJSONObject(i)));
            cb.onResult(true, out, null);
        });
    }

    public void searchUsers(String query, Callback<List<User>> cb) {
        runGet("/users/search?q=" + encode(query), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            List<User> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("users");
            if (arr != null) for (int i = 0; i < arr.length(); i++) out.add(userFromJson(arr.optJSONObject(i)));
            cb.onResult(true, out, null);
        });
    }

    public void getChats(Callback<List<Chat>> cb) { getChats(false, cb); }

    public void getChats(boolean archived, Callback<List<Chat>> cb) {
        runGet("/chats" + (archived ? "?archived=1" : ""), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            List<Chat> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("chats");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.optJSONObject(i);
                    Chat chat = new Chat();
                    chat.id = c.optLong("peer_id");
                    chat.peerUserId = c.optLong("peer_id");
                    chat.peerName = c.optString("peer_name", "");
                    chat.peerHandle = c.optString("peer_handle", "");
                    chat.peerColorHex = c.optString("peer_color", "#6366F1");
                    chat.peerAvatarUrl = c.optString("peer_avatar", "");
                    chat.lastMessage = c.optString("last_message", "");
                    chat.lastOutgoing = c.optBoolean("last_outgoing", false);
                    chat.lastMessageTime = (long) (c.optDouble("last_message_time", 0) * 1000);
                    chat.pinned = c.optBoolean("pinned", false);
                    chat.muted = c.optBoolean("muted", false);
                    chat.unreadCount = c.optInt("unread_count", 0);
                    chat.peerIsBot = c.optBoolean("peer_is_bot", false);
                    out.add(chat);
                }
            }
            cb.onResult(true, out, null);
        });
    }

    public void getMessages(long peerId, long sinceMillis, Callback<List<Message>> cb) {

        double sinceSec = Math.max(0, sinceMillis - (sinceMillis > 0 ? 3000 : 0)) / 1000.0;
        runGet("/messages?peer_id=" + peerId + "&since=" + sinceSec + (updCursor >= 0 ? "&upd=" + updCursor : ""), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            JSONObject pm = json.optJSONObject("peer");
            if (pm != null) {
                PeerMeta meta = new PeerMeta();
                meta.online = pm.optBoolean("online", false);
                meta.typing = pm.optBoolean("typing", false);
                meta.lastSeenMs = pm.isNull("last_seen") ? null : (Long) (long) (pm.optDouble("last_seen", 0) * 1000);
                meta.lastReadMs = (long) (pm.optDouble("last_read", 0) * 1000);
                meta.pinnedId = json.optLong("pinned_id", 0);
                lastPeerMeta = meta;
            }
            if (json.has("server_time")) updCursor = json.optDouble("server_time", updCursor);
            List<Message> ups = new ArrayList<>();
            JSONArray uarr = json.optJSONArray("updates");
            if (uarr != null) {
                for (int ui = 0; ui < uarr.length(); ui++) ups.add(messageFromJson(uarr.optJSONObject(ui), peerId));
            }
            lastUpdates = ups;
            List<Message> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("messages");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.optJSONObject(i);
                    Message msg = new Message();
                    msg.id = m.optLong("id");
                    msg.chatId = peerId;
                    msg.outgoing = m.optBoolean("outgoing");
                    msg.text = m.optString("text", "");
                    msg.time = (long) (m.optDouble("time", 0) * 1000);
                    msg.reactions = parseReactions(m.optJSONArray("reactions"));
                    msg.media = parseMedia(m);
                    msg.encText = m.isNull("enc_text") ? null : m.optString("enc_text", null);
                    msg.encKeyRecipient = m.isNull("enc_key_recipient") ? null : m.optString("enc_key_recipient", null);
                    msg.encKeySender = m.isNull("enc_key_sender") ? null : m.optString("enc_key_sender", null);
                    msg.iv = m.isNull("iv") ? null : m.optString("iv", null);
                    msg.deletedForEveryone = m.optBoolean("deleted_for_everyone", false);
                    msg.replyToId = m.isNull("reply_to_id") ? 0 : m.optLong("reply_to_id", 0);
                    msg.edited = m.optBoolean("edited", false);
                    msg.storyId = m.isNull("story_id") ? 0 : m.optLong("story_id", 0);
                    out.add(msg);
                }
            }
            cb.onResult(true, out, null);
        });
    }

    private Message messageFromJson(JSONObject m, long peerId) {
        Message msg = new Message();
        msg.id = m.optLong("id");
        msg.chatId = peerId;
        msg.outgoing = m.optBoolean("outgoing");
        msg.text = m.optString("text", "");
        msg.time = (long) (m.optDouble("time", 0) * 1000);
        msg.media = parseMedia(m);
        msg.encText = m.isNull("enc_text") ? null : m.optString("enc_text", null);
        msg.encKeyRecipient = m.isNull("enc_key_recipient") ? null : m.optString("enc_key_recipient", null);
        msg.encKeySender = m.isNull("enc_key_sender") ? null : m.optString("enc_key_sender", null);
        msg.iv = m.isNull("iv") ? null : m.optString("iv", null);
        msg.deletedForEveryone = m.optBoolean("deleted_for_everyone", false);
        msg.replyToId = m.isNull("reply_to_id") ? 0 : m.optLong("reply_to_id", 0);
        msg.edited = m.optBoolean("edited", false);
        msg.storyId = m.isNull("story_id") ? 0 : m.optLong("story_id", 0);
        msg.replyMarkup = m.isNull("reply_markup") ? null : m.optString("reply_markup", null);
        return msg;
    }

    public void editMessage(long messageId, String text, Callback<Void> cb) {
        runPost("/messages/" + messageId + "/edit", body -> body.put("text", text), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void editMessageEncrypted(long messageId, CryptoManager.DirectEncrypted enc, Callback<Void> cb) {
        runPost("/messages/" + messageId + "/edit", body -> {
            body.put("enc_text", enc.encText);
            body.put("enc_key_recipient", enc.encKeyRecipient);
            body.put("enc_key_sender", enc.encKeySender);
            body.put("iv", enc.iv);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void pinMessage(long peerId, long messageId, Callback<Void> cb) {
        runPost("/chats/pin_message", body -> {
            body.put("peer_id", peerId);
            body.put("message_id", messageId);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void sendMessage(long peerId, String text, Callback<Message> cb) {
        sendMessage(peerId, text, null, cb);
    }

    public void sendMessage(long peerId, String text, MediaAttachment media, Callback<Message> cb) {
        sendMessage(peerId, text, media, 0, cb);
    }

    public void sendMessage(long peerId, String text, MediaAttachment media, long replyToId, Callback<Message> cb) {
        runPost("/messages/send", body -> {
            body.put("to_id", peerId);
            body.put("text", text);
            if (replyToId > 0) body.put("reply_to_id", replyToId);
            if (media != null) {
                body.put("media_url", media.url);
                body.put("media_type", media.type);
                body.put("media_filename", media.filename);
                body.put("media_size", media.size);
                if (media.durationMs > 0) body.put("media_duration", media.durationMs);
                if (media.waveform != null && !media.waveform.isEmpty()) body.put("media_waveform", media.waveform);
            }
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            JSONObject m = json.optJSONObject("message");
            Message msg = new Message();
            msg.id = m.optLong("id");
            msg.chatId = peerId;
            msg.outgoing = true;
            msg.text = m.optString("text", "");
            msg.time = (long) (m.optDouble("time", 0) * 1000);
            msg.media = parseMedia(m);
            msg.replyToId = m.isNull("reply_to_id") ? 0 : m.optLong("reply_to_id", 0);
            cb.onResult(true, msg, null);
        });
    }

    public void sendEncryptedMessage(long peerId, CryptoManager.DirectEncrypted encrypted, Callback<Message> cb) {
        sendEncryptedMessage(peerId, encrypted, 0, cb);
    }

    public void sendEncryptedMessage(long peerId, CryptoManager.DirectEncrypted encrypted, long replyToId, Callback<Message> cb) {
        runPost("/messages/send", body -> {
            body.put("to_id", peerId);
            body.put("text", "");
            if (replyToId > 0) body.put("reply_to_id", replyToId);
            body.put("enc_text", encrypted.encText);
            body.put("enc_key_recipient", encrypted.encKeyRecipient);
            body.put("enc_key_sender", encrypted.encKeySender);
            body.put("iv", encrypted.iv);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            JSONObject m = json.optJSONObject("message");
            Message msg = new Message();
            msg.id = m.optLong("id");
            msg.chatId = peerId;
            msg.outgoing = true;
            msg.time = (long) (m.optDouble("time", 0) * 1000);
            msg.replyToId = m.isNull("reply_to_id") ? 0 : m.optLong("reply_to_id", 0);
            cb.onResult(true, msg, null);
        });
    }

    public interface UploadProgress { void onProgress(long sent, long total); }

    public void uploadMediaStream(android.net.Uri uri, String filename, String mimeType, long totalBytes,
                                  UploadProgress progress, java.util.concurrent.atomic.AtomicBoolean cancelled,
                                  Callback<MediaAttachment> cb) {
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            HttpURLConnection conn = null;
            java.io.File tmp = null;
            MediaAttachment media = null;
            String error = null;
            try {
                long total = totalBytes;
                InputStream in;
                if (total < 0) {

                    tmp = java.io.File.createTempFile("upload", ".bin", context.getCacheDir());
                    try (InputStream copySrc = context.getContentResolver().openInputStream(uri);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
                        if (copySrc == null) throw new java.io.IOException(context.getString(R.string.file_open_failed));
                        byte[] cb2 = new byte[64 * 1024];
                        int r;
                        while ((r = copySrc.read(cb2)) != -1) fos.write(cb2, 0, r);
                    }
                    total = tmp.length();
                    in = new java.io.FileInputStream(tmp);
                } else {
                    in = context.getContentResolver().openInputStream(uri);
                    if (in == null) throw new java.io.IOException(context.getString(R.string.file_open_failed));
                }
                final long totalFinal = total;
                if (totalFinal > MediaUploader.MAX_FILE_BYTES) {
                    in.close();
                    throw new java.io.IOException(context.getString(R.string.upload_too_big,
                            MediaUploader.MAX_FILE_BYTES / (1024 * 1024)));
                }

                String urlStr = getBaseUrl() + "/media/upload_raw?token=" + encode(getToken())
                        + "&filename=" + encode(filename) + "&mime_type=" + encode(mimeType);
                URL url = new URL(urlStr);
                Proxy proxy = proxyManager.getJavaProxyOrNull();
                conn = (HttpURLConnection) (proxy != null ? url.openConnection(proxy) : url.openConnection());
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(300000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/octet-stream");
                conn.setFixedLengthStreamingMode(totalFinal);

                try (InputStream fileIn = in; OutputStream out = conn.getOutputStream()) {
                    byte[] buf = new byte[64 * 1024];
                    long sent = 0;
                    long lastReport = 0;
                    int n;
                    while ((n = fileIn.read(buf)) != -1) {
                        if (cancelled != null && cancelled.get()) throw new java.io.IOException("cancelled");
                        out.write(buf, 0, n);
                        sent += n;
                        long now = System.currentTimeMillis();
                        if (progress != null && (now - lastReport >= 100 || sent >= totalFinal)) {
                            lastReport = now;
                            final long sentNow = sent;
                            main.post(() -> progress.onProgress(sentNow, totalFinal));
                        }
                    }
                    String mt = mimeType == null ? "" : mimeType;
                    com.talksy.messenger.data.TrafficStats.add(context,
                            mt.startsWith("image/") ? com.talksy.messenger.data.TrafficStats.CAT_PHOTO
                                    : mt.startsWith("video/") ? com.talksy.messenger.data.TrafficStats.CAT_VIDEO
                                    : mt.startsWith("audio/") ? com.talksy.messenger.data.TrafficStats.CAT_VOICE
                                    : com.talksy.messenger.data.TrafficStats.CAT_FILES, sent, 0);
                }

                int code = conn.getResponseCode();
                InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                JSONObject json = null;
                if (is != null) {
                    try { json = new JSONObject(readAll(is)); } catch (Exception ignored) { json = null; }
                }
                if (json != null && json.optBoolean("ok")) {
                    media = new MediaAttachment();
                    media.url = json.optString("url", "");
                    media.type = json.optString("media_type", "file");
                    media.filename = json.optString("filename", filename);
                    media.size = json.optLong("size", 0);
                } else if (json != null) {
                    error = json.optString("error", context.getString(R.string.error_generic));
                } else if (code == 413) {
                    error = "Сервер не принял файл: слишком большой (nginx: client_max_body_size)";
                } else {
                    error = context.getString(R.string.error_generic) + " (HTTP " + code + ")";
                }
            } catch (Exception e) {
                String msg = String.valueOf(e.getMessage());
                if ("cancelled".equals(msg)) {
                    error = "cancelled";
                } else if (msg.contains("Broken pipe") || msg.contains("reset") || msg.contains("EPIPE")) {
                    error = "Сервер оборвал загрузку. Возможно, файл больше лимита сервера (nginx client_max_body_size).";
                } else {
                    error = context.getString(R.string.error_generic) + ": " + msg;
                }
            } finally {
                if (conn != null) conn.disconnect();
                if (tmp != null) tmp.delete();
            }
            final MediaAttachment resultMedia = media;
            final String resultError = error;
            main.post(() -> cb.onResult(resultMedia != null, resultMedia, resultError));
        }).start();
    }

    public static class ChannelStats {
        public long members; public boolean commentsEnabled;
        public final java.util.Map<Long, long[]> posts = new java.util.HashMap<>();
    }

    public void getChannelStats(long groupId, Callback<ChannelStats> cb) {
        runGet("/channels/" + groupId + "/stats", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            ChannelStats st = new ChannelStats();
            st.members = json.optLong("members"); st.commentsEnabled = json.optBoolean("comments_enabled");
            JSONObject p = json.optJSONObject("posts");
            if (p != null) { java.util.Iterator<String> it = p.keys(); while (it.hasNext()) { String k = it.next(); JSONArray a = p.optJSONArray(k); if (a != null) st.posts.put(Long.parseLong(k), new long[]{a.optLong(0), a.optLong(1)}); } }
            cb.onResult(true, st, null);
        });
    }

    public void postChannelViews(long groupId, java.util.Collection<Long> ids, Callback<Void> cb) {
        runPost("/channels/" + groupId + "/views", b -> { JSONArray a = new JSONArray(); for (Long id : ids) a.put(id); b.put("ids", a); }, (json, err) -> {
            if (cb != null) cb.onResult(err == null && json.optBoolean("ok"), null, err);
        });
    }

    public void setCommentsEnabled(long groupId, boolean enabled, Callback<Void> cb) {
        runPost("/channels/" + groupId + "/comments-setting", b -> b.put("enabled", enabled), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public static class Comment {
        public long id, senderId, replyToId; public String senderName, senderColor, avatarUrl, text, replyName, replyText;
        public boolean deleted, mine, canDelete, pending; public long timeMs;
    }
    public static class Discussion {
        public boolean enabled, canModerate; public long total, views; public String channel, postText; public boolean postHasMedia; public long postTimeMs;
        public final List<Comment> comments = new ArrayList<>();
    }

    private static Comment parseComment(JSONObject o) {
        Comment c = new Comment();
        c.id = o.optLong("id"); c.senderId = o.optLong("sender_id"); c.senderName = o.optString("sender_name"); c.senderColor = o.optString("sender_color", "#6366F1");
        c.avatarUrl = o.optString("avatar_url"); c.text = o.optString("text"); c.deleted = o.optBoolean("deleted"); c.mine = o.optBoolean("mine");
        c.canDelete = o.optBoolean("can_delete"); c.timeMs = (long) (o.optDouble("time", 0) * 1000);
        JSONObject r = o.optJSONObject("reply_to");
        if (r != null) { c.replyToId = r.optLong("id"); c.replyName = r.optString("sender_name"); c.replyText = r.optString("text"); }
        return c;
    }

    public void getComments(long groupId, long postId, long afterId, Callback<Discussion> cb) {
        runGet("/channels/" + groupId + "/posts/" + postId + "/comments?after=" + afterId, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            Discussion d = new Discussion();
            d.enabled = json.optBoolean("enabled"); d.total = json.optLong("total"); d.views = json.optLong("views"); d.channel = json.optString("channel");
            d.canModerate = json.optBoolean("can_moderate");
            JSONObject p = json.optJSONObject("post");
            if (p != null) { d.postText = p.optString("text"); d.postHasMedia = p.optBoolean("has_media"); d.postTimeMs = (long) (p.optDouble("time", 0) * 1000); }
            JSONArray arr = json.optJSONArray("comments");
            for (int i = 0; arr != null && i < arr.length(); i++) { JSONObject o = arr.optJSONObject(i); if (o != null) d.comments.add(parseComment(o)); }
            cb.onResult(true, d, null);
        });
    }

    public void sendComment(long groupId, long postId, String text, long replyToId, Callback<Comment> cb) {
        runPost("/channels/" + groupId + "/posts/" + postId + "/comments", b -> { b.put("text", text); if (replyToId > 0) b.put("reply_to_id", replyToId); }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, parseComment(json.optJSONObject("comment")), null);
        });
    }

    public void deleteComment(long groupId, long commentId, Callback<Void> cb) {
        runPost("/channels/" + groupId + "/comments/" + commentId + "/delete", b -> {}, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public static class WalletSession { public String url, sessionId, name, botUsername; }

    public void openWallet(Callback<WalletSession> cb) {
        runPost("/wallet/open", b -> b.put("base_url", getBaseUrl()), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            JSONObject r = json.optJSONObject("result");
            WalletSession s = new WalletSession();
            s.url = r.optString("url"); s.sessionId = r.optString("session_id"); s.name = r.optString("name", "Wallet"); s.botUsername = r.optString("bot_username");
            cb.onResult(true, s, null);
        });
    }

    public boolean postLiveLocation(String id, double lat, double lon, long untilMs) {
        try {
            JSONObject body = new JSONObject().put("lat", lat).put("lon", lon).put("until", untilMs / 1000.0);
            return syncCall("POST", "/location/live/" + id + "?token=" + java.net.URLEncoder.encode(new com.talksy.messenger.data.Prefs(context).get(com.talksy.messenger.data.Prefs.KEY_AUTH_TOKEN, ""), "UTF-8"), body).optBoolean("ok");
        } catch (Exception e) { return false; }
    }

    public static class LivePoint { public double lat, lon; public long updatedMs, untilMs; public boolean active; public String name; }

    public void getLiveLocation(String id, Callback<LivePoint> cb) {
        runGet("/location/live/" + id, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", "")); return; }
            LivePoint p = new LivePoint();
            p.lat = json.optDouble("lat"); p.lon = json.optDouble("lon"); p.updatedMs = (long) (json.optDouble("updated_at") * 1000);
            p.untilMs = (long) (json.optDouble("until") * 1000); p.active = json.optBoolean("active"); p.name = json.optString("name");
            cb.onResult(true, p, null);
        });
    }

    public static class PrivacyState {
        public final java.util.Map<String, String> rules = new java.util.HashMap<>();
        public int invitesPending;
        public String rule(String key) { String v = rules.get(key); return v == null ? "all" : v; }
    }

    public void getPrivacy(Callback<PrivacyState> cb) {
        runGet("/privacy", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            PrivacyState st = new PrivacyState();
            JSONObject r = json.optJSONObject("rules");
            if (r != null) { java.util.Iterator<String> it = r.keys(); while (it.hasNext()) { String k = it.next(); st.rules.put(k, r.optString(k, "all")); } }
            st.invitesPending = json.optInt("invites_pending", 0);
            cb.onResult(true, st, null);
        });
    }

    public void setPrivacyRule(String key, String value, Callback<Void> cb) {
        runPost("/privacy", body -> { body.put("key", key); body.put("value", value); }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public static class GroupInvite {
        public long id, groupId, inviterId, members;
        public String groupName, groupAvatar, inviterName;
        public boolean isChannel;
    }

    public void getGroupInvites(Callback<List<GroupInvite>> cb) {
        runGet("/groups/invites", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            List<GroupInvite> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("invites");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i); if (o == null) continue;
                GroupInvite gi = new GroupInvite();
                gi.id = o.optLong("id"); gi.members = o.optLong("members");
                JSONObject g = o.optJSONObject("group"), u = o.optJSONObject("inviter");
                if (g != null) { gi.groupId = g.optLong("id"); gi.groupName = g.optString("name"); gi.groupAvatar = g.optString("avatar_url"); gi.isChannel = g.optBoolean("is_channel"); }
                if (u != null) { gi.inviterId = u.optLong("id"); gi.inviterName = u.optString("name"); }
                out.add(gi);
            }
            cb.onResult(true, out, null);
        });
    }

    public void acceptGroupInvite(long inviteId, Callback<Void> cb) {
        runPost("/groups/invites/" + inviteId + "/accept", body -> {}, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void declineGroupInvite(long inviteId, Callback<Void> cb) {
        runPost("/groups/invites/" + inviteId + "/decline", body -> {}, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void getUserStories(long userId, Callback<List<StoryGroup.Story>> cb) {
        runGet("/users/" + userId + "/stories", (json, err) -> deliverStories(json, err, cb));
    }

    public void getStoriesArchive(Callback<List<StoryGroup.Story>> cb) {
        runGet("/stories/archive", (json, err) -> deliverStories(json, err, cb));
    }

    public void getUserStoriesArchive(long userId, Callback<List<StoryGroup.Story>> cb) {
        runGet("/users/" + userId + "/stories/archive", (json, err) -> {
            if (err == null && json != null && json.optBoolean("hidden")) { cb.onResult(false, null, "archive_hidden"); return; }
            deliverStories(json, err, cb);
        });
    }

    public void deleteArchivedStory(long storyId, Callback<Void> cb) {
        runPost("/stories/archive/" + storyId + "/delete", body -> {}, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    private void deliverStories(JSONObject json, String err, Callback<List<StoryGroup.Story>> cb) {
        if (err != null) { cb.onResult(false, null, err); return; }
        if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
        List<StoryGroup.Story> out = new ArrayList<>();
        JSONArray arr = json.optJSONArray("stories");
        for (int i = 0; arr != null && i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i); if (s == null) continue;
            StoryGroup.Story st = new StoryGroup.Story();
            st.id = s.optLong("id"); st.mediaUrl = s.optString("media_url", ""); st.mediaType = s.optString("media_type", "image");
            st.createdAtMs = (long) (s.optDouble("created_at", 0) * 1000); st.viewed = s.optBoolean("viewed", false);
            out.add(st);
        }
        cb.onResult(true, out, null);
    }

    public static class StoriesFeed {
        public StoryGroup mine = new StoryGroup();
        public List<StoryGroup> users = new ArrayList<>();
    }

    public void getStories(Callback<StoriesFeed> cb) {
        runGet("/stories", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            StoriesFeed feed = new StoriesFeed();
            feed.mine = parseStoryGroup(json.optJSONObject("mine"));
            JSONArray arr = json.optJSONArray("users");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) feed.users.add(parseStoryGroup(arr.optJSONObject(i)));
            }
            cb.onResult(true, feed, null);
        });
    }

    private StoryGroup parseStoryGroup(JSONObject o) {
        StoryGroup g = new StoryGroup();
        if (o == null) return g;
        g.userId = o.optLong("id");
        g.name = o.optString("name", "");
        g.color = o.optString("color", "#6366F1");
        g.avatarUrl = o.optString("avatar_url", "");
        g.allViewed = true;
        JSONArray arr = o.optJSONArray("stories");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.optJSONObject(i);
                if (s == null) continue;
                StoryGroup.Story st = new StoryGroup.Story();
                st.id = s.optLong("id");
                st.mediaUrl = s.optString("media_url", "");
                st.mediaType = s.optString("media_type", "image");
                st.createdAtMs = (long) (s.optDouble("created_at", 0) * 1000);
                st.viewed = s.optBoolean("viewed", false);
                if (!st.viewed) g.allViewed = false;
                g.stories.add(st);
            }
        }
        return g;
    }

    public void createStory(MediaAttachment media, Callback<Void> cb) {
        runPost("/stories/create", body -> {
            body.put("url", media.url);
            body.put("media_type", "video".equals(media.type) ? "video" : "image");
            body.put("filename", media.filename);
            body.put("size", media.size);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public static class StoryViewer {
        public long id;
        public String name;
        public String color;
        public String avatarUrl;
        public long timeMs;
    }

    public void getStoryViewers(long storyId, Callback<List<StoryViewer>> cb) {
        runGet("/stories/" + storyId + "/viewers", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            List<StoryViewer> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("viewers");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    StoryViewer sv = new StoryViewer();
                    sv.id = o.optLong("id");
                    sv.name = o.optString("name", "");
                    sv.color = o.optString("color", "#6366F1");
                    sv.avatarUrl = o.optString("avatar_url", "");
                    sv.timeMs = (long) (o.optDouble("time", 0) * 1000);
                    out.add(sv);
                }
            }
            cb.onResult(true, out, null);
        });
    }

    public void setWhoCanWrite(String value, Callback<Void> cb) {
        runPost("/me/privacy", body -> body.put("who_can_write", value), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void viewStory(long storyId) {
        runPost("/stories/" + storyId + "/view", body -> { }, (json, err) -> { });
    }

    public void deleteStory(long storyId, Callback<Void> cb) {
        runPost("/stories/" + storyId + "/delete", body -> { }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void sendStoryReply(long peerId, String text, long storyId, Callback<Void> cb) {
        runPost("/messages/send", body -> {
            body.put("to_id", peerId);
            body.put("text", text);
            body.put("story_id", storyId);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void uploadMedia(String base64, String filename, String mimeType, Callback<MediaAttachment> cb) {
        runPost("/media/upload", body -> {
            body.put("file_base64", base64);
            body.put("filename", filename);
            body.put("mime_type", mimeType);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            MediaAttachment media = new MediaAttachment();
            media.url = json.optString("url", "");
            media.type = json.optString("media_type", "file");
            media.filename = json.optString("filename", filename);
            media.size = json.optLong("size", 0);
            cb.onResult(true, media, null);
        });
    }

    private MediaAttachment parseMedia(JSONObject m) {
        if (m == null || m.isNull("media_url")) return null;
        String url = m.optString("media_url", null);
        if (url == null || url.isEmpty()) return null;
        MediaAttachment media = new MediaAttachment();
        media.url = url;
        media.type = m.optString("media_type", "file");
        media.filename = m.optString("media_filename", "file");
        media.size = m.optLong("media_size", 0);
        media.durationMs = m.optLong("media_duration", 0);
        media.waveform = m.isNull("media_waveform") ? null : m.optString("media_waveform", null);
        return media;
    }

    public void createGroup(String name, Callback<Group> cb) {
        runPost("/groups/create", body -> body.put("name", name), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, groupFromJson(json.optJSONObject("group")), null);
        });
    }

    public void setOwnGroupKey(long groupId, String encryptedKeyBase64, Callback<Void> cb) {
        runPost("/groups/" + groupId + "/keys/set", body -> body.put("encrypted_key", encryptedKeyBase64), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void getMyGroupKey(long groupId, Callback<String> cb) {
        runGet("/groups/" + groupId + "/keys/mine", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, json.isNull("encrypted_key") ? null : json.optString("encrypted_key", null), null);
        });
    }

    public static class PendingKeyMember {
        public long id;
        public String publicKey;
    }

    public void getPendingGroupKeyMembers(long groupId, Callback<List<PendingKeyMember>> cb) {
        runGet("/groups/" + groupId + "/keys/pending", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            List<PendingKeyMember> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("members");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.optJSONObject(i);
                    if (m.isNull("public_key")) continue;
                    PendingKeyMember pm = new PendingKeyMember();
                    pm.id = m.optLong("id");
                    pm.publicKey = m.optString("public_key", null);
                    out.add(pm);
                }
            }
            cb.onResult(true, out, null);
        });
    }

    public void grantGroupKey(long groupId, long userId, String encryptedKeyBase64, Callback<Void> cb) {
        runPost("/groups/" + groupId + "/keys/grant", body -> {
            body.put("user_id", userId);
            body.put("encrypted_key", encryptedKeyBase64);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void searchGroups(String query, Callback<List<Group>> cb) {
        runGet("/groups/search?q=" + encode(query), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            List<Group> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("groups");
            if (arr != null) for (int i = 0; i < arr.length(); i++) out.add(groupFromJson(arr.optJSONObject(i)));
            cb.onResult(true, out, null);
        });
    }

    public void getGroup(long groupId, Callback<Group> cb) {
        runGet("/groups/" + groupId, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, groupFromJson(json.optJSONObject("group")), null);
        });
    }

    public void leaveGroup(long groupId, Callback<Void> cb) {
        runPost("/groups/" + groupId + "/leave", body -> { }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void setGroupMembersVisible(long groupId, boolean visible, Callback<Group> cb) {
        runPost("/groups/" + groupId + "/settings", body -> body.put("members_visible", visible), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, groupFromJson(json.optJSONObject("group")), null);
        });
    }

    public void joinGroup(long groupId, Callback<Group> cb) {
        runPost("/groups/join", body -> body.put("group_id", groupId), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, groupFromJson(json.optJSONObject("group")), null);
        });
    }

    public void getMyGroups(Callback<List<Group>> cb) { getMyGroups(false, cb); }

    public void getMyGroups(boolean archived, Callback<List<Group>> cb) {
        runGet("/groups/mine" + (archived ? "?archived=1" : ""), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            List<Group> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("groups");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject g = arr.optJSONObject(i);
                    Group group = groupFromJson(g);
                    group.lastMessage = g.optString("last_message", "");
                    group.lastMessageTime = (long) (g.optDouble("last_message_time", 0) * 1000);
                    group.unreadCount = g.optInt("unread_count", 0);
                    out.add(group);
                }
            }
            cb.onResult(true, out, null);
        });
    }

    public void getGroupMembers(long groupId, Callback<List<com.talksy.messenger.data.model.GroupMember>> cb) {
        runGet("/groups/" + groupId + "/members", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            List<com.talksy.messenger.data.model.GroupMember> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("members");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.optJSONObject(i);
                    com.talksy.messenger.data.model.GroupMember gm = new com.talksy.messenger.data.model.GroupMember();
                    gm.id = m.optLong("id");
                    gm.name = m.optString("name", "");
                    gm.handle = m.optString("handle", "");
                    gm.colorHex = m.optString("color", "#6366F1");
                    gm.avatarUrl = m.optString("avatar_url", "");
                    gm.role = m.optString("role", "member");
                    out.add(gm);
                }
            }
            cb.onResult(true, out, null);
        });
    }

    public void addGroupMember(long groupId, long userId, boolean asAdmin, Callback<Void> cb) {
        runPost("/groups/" + groupId + "/members/add", body -> {
            body.put("user_id", userId);
            body.put("as_admin", asAdmin);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, null, null);
        });
    }

    public interface HandleResolveCallback {

        void onResult(boolean ok, String type, long id, String name, String error);
    }

    public void resolveHandle(String handle, HandleResolveCallback cb) {
        runGet("/handles/resolve?handle=" + urlEnc(handle), (json, err) -> {
            if (err != null) { cb.onResult(false, null, 0, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, 0, null, json.optString("error", context.getString(R.string.mention_not_found))); return; }
            String type = json.optString("type", "user");
            if ("group".equals(type)) {
                JSONObject g = json.optJSONObject("group");
                cb.onResult(true, type, g == null ? 0 : g.optLong("id"), g == null ? null : g.optString("name"), null);
            } else {
                JSONObject u = json.optJSONObject("user");
                cb.onResult(true, type, u == null ? 0 : u.optLong("id"), u == null ? null : u.optString("name"), null);
            }
        });
    }

    private String urlEnc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); } catch (Exception e) { return ""; }
    }

    public void setGroupAdminOnlyMode(long groupId, boolean adminOnly, Callback<Group> cb) {
        runPost("/groups/" + groupId + "/settings", body -> body.put("admin_only_mode", adminOnly), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, groupFromJson(json.optJSONObject("group")), null);
        });
    }

    public void setGroupReactionsEnabled(long groupId, boolean enabled, Callback<Group> cb) {
        runPost("/groups/" + groupId + "/settings", body -> body.put("reactions_enabled", enabled), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, groupFromJson(json.optJSONObject("group")), null);
        });
    }

    public void setGroupVisibility(long groupId, boolean isChannel, boolean isPrivate, Callback<Group> cb) {
        runPost("/groups/" + groupId + "/settings", body -> {
            body.put("is_channel", isChannel);
            body.put("is_private", isPrivate);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, groupFromJson(json.optJSONObject("group")), null);
        });
    }

    public void setMemberRole(long groupId, long userId, String role, Callback<Void> cb) {
        runPost("/groups/" + groupId + "/members/" + userId + "/role", body -> body.put("role", role), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, json.optString("error", null));
        });
    }

    public void sendGroupMessage(long groupId, String text, Callback<com.talksy.messenger.data.model.GroupMessage> cb) {
        sendGroupMessage(groupId, text, null, cb);
    }

    public void sendGroupMessage(long groupId, String text, MediaAttachment media, Callback<com.talksy.messenger.data.model.GroupMessage> cb) {
        runPost("/groups/" + groupId + "/messages/send", body -> {
            body.put("text", text);
            if (media != null) {
                body.put("media_url", media.url);
                body.put("media_type", media.type);
                body.put("media_filename", media.filename);
                body.put("media_size", media.size);
                if (media.durationMs > 0) body.put("media_duration", media.durationMs);
                if (media.waveform != null && !media.waveform.isEmpty()) body.put("media_waveform", media.waveform);
            }
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            JSONObject m = json.optJSONObject("message");
            com.talksy.messenger.data.model.GroupMessage gm = new com.talksy.messenger.data.model.GroupMessage();
            gm.id = m.optLong("id");
            gm.senderId = m.optLong("sender_id");
            gm.outgoing = true;
            gm.text = m.optString("text", "");
            gm.time = (long) (m.optDouble("time", 0) * 1000);
            gm.media = parseMedia(m);
            cb.onResult(true, gm, null);
        });
    }

    public void sendEncryptedGroupMessage(long groupId, CryptoManager.GroupEncrypted encrypted,
                                           Callback<com.talksy.messenger.data.model.GroupMessage> cb) {
        runPost("/groups/" + groupId + "/messages/send", body -> {
            body.put("text", "");
            body.put("enc_text", encrypted.encText);
            body.put("iv", encrypted.iv);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            JSONObject m = json.optJSONObject("message");
            com.talksy.messenger.data.model.GroupMessage gm = new com.talksy.messenger.data.model.GroupMessage();
            gm.id = m.optLong("id");
            gm.senderId = m.optLong("sender_id");
            gm.outgoing = true;
            gm.time = (long) (m.optDouble("time", 0) * 1000);
            cb.onResult(true, gm, null);
        });
    }

    public void deleteMessage(long messageId, boolean forEveryone, Callback<Void> cb) {
        runPost("/messages/" + messageId + "/delete", body -> body.put("for_everyone", forEveryone), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, null, null);
        });
    }

    public void markChatRead(long peerId, Callback<Void> cb) {
        runPost("/chats/read", body -> { body.put("kind", "direct"); body.put("peer_id", peerId); }, (json, err) -> {
            if (cb == null) return;
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, null);
        });
    }

    public void markGroupRead(long groupId, Callback<Void> cb) {
        runPost("/chats/read", body -> { body.put("kind", "group"); body.put("peer_id", groupId); }, (json, err) -> {
            if (cb == null) return;
            if (err != null) { cb.onResult(false, null, err); return; }
            cb.onResult(json.optBoolean("ok"), null, null);
        });
    }

    private Bot botFromJson(org.json.JSONObject b) {
        if (b == null) return null;
        Bot bot = new Bot();
        bot.id = b.optLong("id");
        bot.name = b.optString("name", "");
        bot.username = b.optString("username", "");
        bot.avatarUrl = b.optString("avatar_url", "");
        bot.token = b.optString("token", "");
        bot.webhookUrl = b.optString("webhook_url", "");
        bot.miniAppUrl = b.optString("mini_app_url", "");
        bot.miniAppName = b.optString("mini_app_name", "");
        bot.createdAt = b.optLong("created_at");
        return bot;
    }

    public void createBot(String name, String username, Callback<Bot> cb) {
        runPost("/bots/create", body -> { body.put("name", name); body.put("username", username); }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, botFromJson(json.optJSONObject("bot")), null);
        });
    }

    public void getMyBots(Callback<List<Bot>> cb) {
        runGet("/bots/mine", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            List<Bot> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("bots");
            if (arr != null) for (int i = 0; i < arr.length(); i++) out.add(botFromJson(arr.optJSONObject(i)));
            cb.onResult(true, out, null);
        });
    }

    public void getBot(long botId, Callback<Bot> cb) {
        runGet("/bots/" + botId, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, botFromJson(json.optJSONObject("bot")), null);
        });
    }

    public void regenerateBotToken(long botId, Callback<Bot> cb) {
        runPost("/bots/" + botId + "/regenerate_token", body -> {}, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, botFromJson(json.optJSONObject("bot")), null);
        });
    }

    public void setBotWebhook(long botId, String url, Callback<Void> cb) {
        runPost("/bots/" + botId + "/webhook", body -> body.put("url", url), (json, err) -> {
            if (cb == null) return;
            cb.onResult(err == null && json.optBoolean("ok"), null, err != null ? err : json.optString("error", null));
        });
    }

    public void setBotMiniApp(long botId, String url, String name, Callback<Void> cb) {
        runPost("/bots/" + botId + "/mini_app", body -> { body.put("url", url); body.put("name", name); }, (json, err) -> {
            if (cb == null) return;
            cb.onResult(err == null && json.optBoolean("ok"), null, err != null ? err : json.optString("error", null));
        });
    }

    public void updateBotProfile(long botId, String name, String bio, Callback<Bot> cb) {
        runPost("/bots/" + botId + "/profile", body -> { body.put("name", name); body.put("bio", bio); }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, botFromJson(json.optJSONObject("bot")), null);
        });
    }

    public void uploadBotAvatar(long botId, String imageBase64, Callback<Bot> cb) {
        runPost("/bots/" + botId + "/avatar", body -> body.put("image_base64", imageBase64), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, botFromJson(json.optJSONObject("bot")), null);
        });
    }

    public void deleteBot(long botId, Callback<Void> cb) {
        runPost("/bots/" + botId + "/delete", body -> {}, (json, err) -> {
            if (cb == null) return;
            cb.onResult(err == null && json.optBoolean("ok"), null, err != null ? err : json.optString("error", null));
        });
    }

    public static class CallInfo {
        public long id, calleeId, callerId;
        public String callType, status;
        public String callerName, callerColor, callerAvatar;
    }

    private CallInfo callFromJson(org.json.JSONObject c) {
        if (c == null) return null;
        CallInfo call = new CallInfo();
        call.id = c.optLong("id");
        call.callerId = c.optLong("caller_id");
        call.calleeId = c.optLong("callee_id");
        call.callType = c.optString("call_type", "audio");
        call.status = c.optString("status", "ringing");
        call.callerName = c.optString("caller_name", "");
        call.callerColor = c.optString("caller_color", "#6366F1");
        call.callerAvatar = c.optString("caller_avatar", "");
        return call;
    }

    public void startCall(long calleeId, String callType, Callback<CallInfo> cb) {
        runPost("/calls/start", body -> { body.put("callee_id", calleeId); body.put("call_type", callType); }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, callFromJson(json.optJSONObject("call")), null);
        });
    }

    public void acceptCall(long callId, Callback<Void> cb) {
        runPost("/calls/" + callId + "/accept", body -> {}, (json, err) -> {
            if (cb == null) return;
            cb.onResult(err == null && json.optBoolean("ok"), null, err != null ? err : json.optString("error", null));
        });
    }

    public void rejectCall(long callId, Callback<Void> cb) {
        runPost("/calls/" + callId + "/reject", body -> {}, (json, err) -> {
            if (cb == null) return;
            cb.onResult(err == null && json.optBoolean("ok"), null, err != null ? err : json.optString("error", null));
        });
    }

    public void endCall(long callId, Callback<Void> cb) {
        runPost("/calls/" + callId + "/end", body -> {}, (json, err) -> {
            if (cb == null) return;
            cb.onResult(err == null && json.optBoolean("ok"), null, err != null ? err : json.optString("error", null));
        });
    }

    public void sendCallSignal(long callId, String kind, String payload, Callback<Void> cb) {
        runPost("/calls/" + callId + "/signal", body -> { body.put("kind", kind); body.put("payload", payload); }, (json, err) -> {
            if (cb == null) return;
            cb.onResult(err == null && json.optBoolean("ok"), null, err != null ? err : json.optString("error", null));
        });
    }

    public static class CallSignal {
        public long id;
        public String kind, payload;
        public double time;
    }

    public void getCallSignals(long callId, double since, Callback<List<CallSignal>> cb) {
        runGet("/calls/" + callId + "/signals?since=" + since, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            List<CallSignal> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("signals");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject s = arr.optJSONObject(i);
                CallSignal sig = new CallSignal();
                sig.id = s.optLong("id");
                sig.kind = s.optString("kind");
                sig.payload = s.optString("payload");
                sig.time = s.optDouble("time");
                out.add(sig);
            }
            cb.onResult(true, out, null);
        });
    }

    public void getIncomingCall(Callback<CallInfo> cb) {
        runGet("/calls/incoming", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, callFromJson(json.optJSONObject("call")), null);
        });
    }

    public void deleteGroupMessage(long groupId, long messageId, boolean forEveryone, Callback<Void> cb) {
        runPost("/groups/" + groupId + "/messages/" + messageId + "/delete",
                body -> body.put("for_everyone", forEveryone), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, null, null);
        });
    }

    public void getGroupMessages(long groupId, long sinceMillis, Callback<List<com.talksy.messenger.data.model.GroupMessage>> cb) {
        double sinceSec = Math.max(0, sinceMillis - (sinceMillis > 0 ? 3000 : 0)) / 1000.0;
        runGet("/groups/" + groupId + "/messages?since=" + sinceSec, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            List<com.talksy.messenger.data.model.GroupMessage> out = new ArrayList<>();
            JSONArray arr = json.optJSONArray("messages");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.optJSONObject(i);
                    com.talksy.messenger.data.model.GroupMessage gm = new com.talksy.messenger.data.model.GroupMessage();
                    gm.id = m.optLong("id");
                    gm.senderId = m.optLong("sender_id");
                    gm.senderName = m.optString("sender_name", "");
                    gm.outgoing = m.optBoolean("outgoing");
                    gm.text = m.optString("text", "");
                    gm.time = (long) (m.optDouble("time", 0) * 1000);
                    gm.reactions = parseReactions(m.optJSONArray("reactions"));
                    gm.media = parseMedia(m);
                    gm.encText = m.isNull("enc_text") ? null : m.optString("enc_text", null);
                    gm.iv = m.isNull("iv") ? null : m.optString("iv", null);
                    gm.deletedForEveryone = m.optBoolean("deleted_for_everyone", false);
                    out.add(gm);
                }
            }
            cb.onResult(true, out, null);
        });
    }

    private Group groupFromJson(JSONObject g) {
        Group group = new Group();
        if (g == null) return group;
        group.id = g.optLong("id");
        group.name = g.optString("name", "");
        group.handle = g.isNull("handle") ? null : g.optString("handle", null);
        group.description = g.optString("description", "");
        group.avatarUrl = g.optString("avatar_url", "");
        group.ownerId = g.optLong("owner_id");
        group.adminOnlyMode = g.optBoolean("admin_only_mode", false);
        group.reactionsEnabled = g.optBoolean("reactions_enabled", true);
        group.isChannel = g.optBoolean("is_channel", false);
        group.isPrivate = g.optBoolean("is_private", false);
        group.membersVisible = g.optBoolean("members_visible", false);
        group.membersCount = g.optInt("members_count", 0);
        group.myRole = g.isNull("my_role") ? null : g.optString("my_role", null);
        group.pinned = g.optBoolean("pinned", false);
        group.muted = g.optBoolean("muted", false);
        return group;
    }

    private List<com.talksy.messenger.data.model.Reaction> parseReactions(JSONArray arr) {
        List<com.talksy.messenger.data.model.Reaction> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            com.talksy.messenger.data.model.Reaction reaction = new com.talksy.messenger.data.model.Reaction();
            reaction.emoji = r.optString("emoji", "");
            reaction.count = r.optInt("count", 0);
            reaction.mine = r.optBoolean("mine", false);
            out.add(reaction);
        }
        return out;
    }

    public void toggleReaction(String messageType, long messageId, String emoji,
                                Callback<List<com.talksy.messenger.data.model.Reaction>> cb) {
        runPost("/reactions", body -> {
            body.put("message_type", messageType);
            body.put("message_id", messageId);
            body.put("emoji", emoji);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, parseReactions(json.optJSONArray("reactions")), null);
        });
    }

    private interface BodyBuilder { void build(JSONObject body) throws Exception; }
    private interface RawCallback { void onResult(JSONObject json, String error); }

    private void runPost(String path, BodyBuilder builder, RawCallback cb) {
        if (!isConfigured()) { cb.onResult(null, context.getString(R.string.error_server_not_configured)); return; }
        new AsyncTask<Void, Void, Object[]>() {
            @Override
            protected Object[] doInBackground(Void... voids) {
                try {
                    JSONObject body = new JSONObject();
                    body.put("token", getToken());
                    builder.build(body);
                    JSONObject json = httpJson("POST", getBaseUrl() + path, body);
                    return new Object[]{json, null};
                } catch (Exception e) {
                    return new Object[]{null, context.getString(R.string.error_server_connection) + e.getMessage()};
                }
            }
            @Override
            protected void onPostExecute(Object[] r) {
                cb.onResult((JSONObject) r[0], (String) r[1]);
            }
        }.execute();
    }

    private void runGet(String pathWithQuery, RawCallback cb) {
        if (!isConfigured()) { cb.onResult(null, context.getString(R.string.error_server_not_configured)); return; }
        new AsyncTask<Void, Void, Object[]>() {
            @Override
            protected Object[] doInBackground(Void... voids) {
                try {
                    String sep = pathWithQuery.contains("?") ? "&" : "?";
                    String url = getBaseUrl() + pathWithQuery + sep + "token=" + encode(getToken());
                    JSONObject json = httpJson("GET", url, null);
                    if (pathWithQuery.startsWith("/stickers/") && json.optBoolean("ok")) {
                        com.talksy.messenger.sync.LocalDb.get(context).cachePut(pathWithQuery, json.toString());
                    }
                    return new Object[]{json, null};
                } catch (Exception e) {
                    if (pathWithQuery.startsWith("/stickers/")) {
                        JSONObject saved = com.talksy.messenger.sync.LocalDb.get(context).cacheGet(pathWithQuery);
                        if (saved != null) return new Object[]{saved, null};
                    }
                    return new Object[]{null, context.getString(R.string.error_server_connection) + e.getMessage()};
                }
            }
            @Override
            protected void onPostExecute(Object[] r) {
                cb.onResult((JSONObject) r[0], (String) r[1]);
            }
        }.execute();
    }

    public JSONObject syncCall(String method, String path, JSONObject body) throws Exception {
        return httpJson(method, getBaseUrl() + path, body);
    }

    private JSONObject httpJson(String method, String urlStr, JSONObject body) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            Proxy proxy = proxyManager.getJavaProxyOrNull();
            conn = (HttpURLConnection) (proxy != null ? url.openConnection(proxy) : url.openConnection());
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            if (background) conn.setRequestProperty("X-Background", "1");

            long txBytes = 300;
            if (body != null) {
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                txBytes += payload.length;
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String respText = readAll(is);
            com.talksy.messenger.data.TrafficStats.add(context, com.talksy.messenger.data.TrafficStats.CAT_MESSAGES, txBytes, respText.length() + 300L);
            return new JSONObject(respText);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readAll(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private String encode(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    public void createStickerPack(String name, Callback<com.talksy.messenger.data.model.StickerPack> cb) {
        runPost("/stickers/packs/create", body -> body.put("name", name), (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            cb.onResult(true, parseStickerPack(json.optJSONObject("pack")), null);
        });
    }

    public void listStickerPacks(Callback<java.util.List<com.talksy.messenger.data.model.StickerPack>> cb) {
        runGet("/stickers/packs", (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            java.util.List<com.talksy.messenger.data.model.StickerPack> list = new java.util.ArrayList<>();
            JSONArray arr = json.optJSONArray("packs");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    com.talksy.messenger.data.model.StickerPack p = parseStickerPack(arr.optJSONObject(i));
                    if (p != null) {
                        list.add(p);
                        if (p.coverUrl != null && !p.coverUrl.isEmpty()) com.talksy.messenger.net.MediaCache.prefetch(getBaseUrl() + p.coverUrl, com.talksy.messenger.data.TrafficStats.CAT_STICKERS);
                    }
                }
            }
            cb.onResult(true, list, null);
        });
    }

    public void getStickerPack(long packId, Callback<java.util.List<com.talksy.messenger.data.model.Sticker>> cb) {
        runGet("/stickers/packs/" + packId, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            java.util.List<com.talksy.messenger.data.model.Sticker> list = new java.util.ArrayList<>();
            JSONArray arr = json.optJSONArray("stickers");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.optJSONObject(i);
                    if (s == null) continue;
                    com.talksy.messenger.data.model.Sticker st = new com.talksy.messenger.data.model.Sticker();
                    st.id = s.optLong("id");
                    st.packId = packId;
                    st.mediaUrl = s.optString("media_url", "");
                    st.mediaType = s.optString("media_type", "sticker_image");
                    st.durationMs = s.optLong("duration_ms", 0);
                    list.add(st);
                    if (!st.mediaUrl.isEmpty()) com.talksy.messenger.net.MediaCache.prefetch(getBaseUrl() + st.mediaUrl, com.talksy.messenger.data.TrafficStats.CAT_STICKERS);
                }
            }
            cb.onResult(true, list, null);
        });
    }

    public void addStickerToPack(long packId, String mediaUrl, String mediaType, long durationMs,
                                  Callback<com.talksy.messenger.data.model.Sticker> cb) {
        runPost("/stickers/packs/" + packId + "/add", body -> {
            body.put("media_url", mediaUrl);
            body.put("media_type", mediaType);
            if (durationMs > 0) body.put("duration_ms", durationMs);
        }, (json, err) -> {
            if (err != null) { cb.onResult(false, null, err); return; }
            if (!json.optBoolean("ok")) { cb.onResult(false, null, json.optString("error", context.getString(R.string.error_generic))); return; }
            JSONObject s = json.optJSONObject("sticker");
            com.talksy.messenger.data.model.Sticker st = new com.talksy.messenger.data.model.Sticker();
            if (s != null) {
                st.id = s.optLong("id");
                st.packId = packId;
                st.mediaUrl = s.optString("media_url", mediaUrl);
                st.mediaType = s.optString("media_type", mediaType);
                st.durationMs = s.optLong("duration_ms", durationMs);
            }
            cb.onResult(true, st, null);
        });
    }

    private com.talksy.messenger.data.model.StickerPack parseStickerPack(JSONObject p) {
        if (p == null) return null;
        com.talksy.messenger.data.model.StickerPack sp = new com.talksy.messenger.data.model.StickerPack();
        sp.id = p.optLong("id");
        sp.name = p.optString("name", "");
        sp.creatorId = p.optLong("creator_id");
        sp.stickerCount = p.optInt("sticker_count", 0);
        sp.coverUrl = p.isNull("cover_url") ? null : p.optString("cover_url", null);
        sp.coverType = p.isNull("cover_type") ? null : p.optString("cover_type", null);
        return sp;
    }

}
