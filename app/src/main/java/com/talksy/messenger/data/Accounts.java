package com.talksy.messenger.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Accounts {

    public static final int MAX_ACCOUNTS = 3;

    public static class Account {
        public long userId;
        public String token;
        public String name;
        public String handle;
        public String color;
        public String avatarUrl;

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("user_id", userId);
            o.put("token", token);
            o.put("name", name == null ? "" : name);
            o.put("handle", handle == null ? "" : handle);
            o.put("color", color == null ? "#6366F1" : color);
            o.put("avatar_url", avatarUrl == null ? "" : avatarUrl);
            return o;
        }

        static Account fromJson(JSONObject o) {
            Account a = new Account();
            a.userId = o.optLong("user_id");
            a.token = o.optString("token", "");
            a.name = o.optString("name", "");
            a.handle = o.optString("handle", "");
            a.color = o.optString("color", "#6366F1");
            a.avatarUrl = o.optString("avatar_url", "");
            return a;
        }
    }

    private Accounts() {}

    public static List<Account> list(Context ctx) {
        List<Account> out = new ArrayList<>();
        String raw = new Prefs(ctx).get(Prefs.KEY_SAVED_ACCOUNTS, "");
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) out.add(Account.fromJson(arr.getJSONObject(i)));
        } catch (JSONException ignored) {}
        return out;
    }

    private static void save(Context ctx, List<Account> accounts) {
        JSONArray arr = new JSONArray();
        try {
            for (Account a : accounts) arr.put(a.toJson());
        } catch (JSONException ignored) {}
        new Prefs(ctx).put(Prefs.KEY_SAVED_ACCOUNTS, arr.toString());
    }

    private static Account snapshotCurrent(Context ctx) {
        Prefs p = new Prefs(ctx);
        String uid = p.get(Prefs.KEY_USER_ID, "");
        String token = p.get(Prefs.KEY_AUTH_TOKEN, "");
        if (uid.isEmpty() || token.isEmpty()) return null;
        Account a = new Account();
        a.userId = Long.parseLong(uid);
        a.token = token;
        a.name = p.get(Prefs.KEY_MY_NAME, "");
        a.handle = p.get(Prefs.KEY_MY_HANDLE, "");
        a.color = p.get(Prefs.KEY_MY_COLOR, "#6366F1");
        a.avatarUrl = p.get(Prefs.KEY_MY_AVATAR_URL, "");
        return a;
    }

    public static void rememberCurrent(Context ctx) {
        Account cur = snapshotCurrent(ctx);
        if (cur == null) return;
        List<Account> accounts = list(ctx);
        boolean replaced = false;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).userId == cur.userId) { accounts.set(i, cur); replaced = true; break; }
        }
        if (!replaced) accounts.add(cur);
        save(ctx, accounts);
    }

    public static boolean canAddMore(Context ctx) {
        List<Account> accounts = list(ctx);

        boolean currentAlreadyCounted = false;
        Account cur = snapshotCurrent(ctx);
        if (cur != null) for (Account a : accounts) if (a.userId == cur.userId) currentAlreadyCounted = true;
        int effective = accounts.size() + (cur != null && !currentAlreadyCounted ? 1 : 0);
        return effective < MAX_ACCOUNTS;
    }

    public static long currentUserId(Context ctx) {
        try { return Long.parseLong(new Prefs(ctx).get(Prefs.KEY_USER_ID, "0")); } catch (Exception e) { return 0; }
    }

    public static void switchTo(Context ctx, Account a) {
        rememberCurrent(ctx);
        Prefs p = new Prefs(ctx);
        p.put(Prefs.KEY_AUTH_TOKEN, a.token);
        p.put(Prefs.KEY_USER_ID, String.valueOf(a.userId));
        p.put(Prefs.KEY_MY_NAME, a.name);
        p.put(Prefs.KEY_MY_HANDLE, a.handle);
        p.put(Prefs.KEY_MY_COLOR, a.color);
        p.put(Prefs.KEY_MY_AVATAR_URL, a.avatarUrl);
    }

    public static void remove(Context ctx, long userId) {
        List<Account> accounts = list(ctx);
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).userId == userId) { accounts.remove(i); break; }
        }
        save(ctx, accounts);
    }
}
