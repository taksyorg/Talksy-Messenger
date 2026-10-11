package com.talksy.messenger.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.talksy.messenger.net.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class CommentsCache {
    private final SharedPreferences sp;
    private final String key;

    public CommentsCache(Context c, long groupId, long postId) {
        sp = c.getApplicationContext().getSharedPreferences("comments_cache", Context.MODE_PRIVATE);
        key = groupId + ":" + postId;
    }

    public static final class State {
        public final List<ApiClient.Comment> comments = new ArrayList<>();
        public final List<ApiClient.Comment> pending = new ArrayList<>();
        public boolean hasData, enabled = true, canModerate;
        public long total;
        public String postText = "";
    }

    private static JSONObject toJson(ApiClient.Comment c) throws Exception {
        return new JSONObject().put("id", c.id).put("sid", c.senderId).put("rid", c.replyToId).put("sn", c.senderName).put("sc", c.senderColor)
                .put("av", c.avatarUrl).put("t", c.text).put("rn", c.replyName).put("rt", c.replyText).put("d", c.deleted).put("m", c.mine)
                .put("cd", c.canDelete).put("ms", c.timeMs);
    }

    private static ApiClient.Comment fromJson(JSONObject o, boolean pending) {
        ApiClient.Comment c = new ApiClient.Comment();
        c.id = o.optLong("id"); c.senderId = o.optLong("sid"); c.replyToId = o.optLong("rid"); c.senderName = o.optString("sn"); c.senderColor = o.optString("sc");
        c.avatarUrl = o.optString("av"); c.text = o.optString("t"); c.replyName = o.optString("rn"); c.replyText = o.optString("rt");
        c.deleted = o.optBoolean("d"); c.mine = o.optBoolean("m"); c.canDelete = o.optBoolean("cd"); c.timeMs = o.optLong("ms"); c.pending = pending;
        return c;
    }

    public State load() {
        State st = new State();
        try {
            String raw = sp.getString(key, null);
            if (raw == null) return st;
            JSONObject o = new JSONObject(raw);
            st.hasData = true;
            st.enabled = o.optBoolean("enabled", true); st.canModerate = o.optBoolean("mod"); st.total = o.optLong("total"); st.postText = o.optString("post");
            JSONArray a = o.optJSONArray("comments");
            for (int i = 0; a != null && i < a.length(); i++) st.comments.add(fromJson(a.getJSONObject(i), false));
            JSONArray p = o.optJSONArray("pending");
            for (int i = 0; p != null && i < p.length(); i++) st.pending.add(fromJson(p.getJSONObject(i), true));
        } catch (Exception ignored) { }
        return st;
    }

    public void save(State st) {
        try {
            JSONArray a = new JSONArray();

            int from = Math.max(0, st.comments.size() - 300);
            for (int i = from; i < st.comments.size(); i++) a.put(toJson(st.comments.get(i)));
            JSONArray p = new JSONArray();
            for (ApiClient.Comment c : st.pending) p.put(toJson(c));
            sp.edit().putString(key, new JSONObject().put("enabled", st.enabled).put("mod", st.canModerate).put("total", st.total)
                    .put("post", st.postText == null ? "" : st.postText).put("comments", a).put("pending", p).toString()).apply();
        } catch (Exception ignored) { }
    }
}
