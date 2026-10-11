package com.talksy.messenger.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Favorites {
    private Favorites() {}

    public static class Item {
        public String id;
        public String from;
        public String text;
        public String mediaUrl;
        public String mediaName;
        public long time;
        public long savedAt;
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("talksy_favorites", Context.MODE_PRIVATE);
    }

    public static List<Item> list(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("items", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.id = o.optString("id");
                it.from = o.optString("from");
                it.text = o.optString("text");
                it.mediaUrl = o.isNull("media_url") ? null : o.optString("media_url", null);
                it.mediaName = o.optString("media_name", "");
                it.time = o.optLong("time");
                it.savedAt = o.optLong("saved_at");
                out.add(it);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static void save(Context c, List<Item> items) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("from", it.from);
                o.put("text", it.text);
                o.put("media_url", it.mediaUrl);
                o.put("media_name", it.mediaName);
                o.put("time", it.time);
                o.put("saved_at", it.savedAt);
                a.put(o);
            }
        } catch (Exception ignored) {}
        sp(c).edit().putString("items", a.toString()).apply();
    }

    public static boolean toggle(Context c, Item item) {
        List<Item> items = list(c);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(item.id)) {
                items.remove(i);
                save(c, items);
                return false;
            }
        }
        item.savedAt = System.currentTimeMillis();
        items.add(0, item);
        save(c, items);
        return true;
    }

    public static void remove(Context c, String id) {
        List<Item> items = list(c);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) { items.remove(i); break; }
        }
        save(c, items);
    }

    public static boolean has(Context c, String id) {
        for (Item it : list(c)) if (it.id.equals(id)) return true;
        return false;
    }
}
