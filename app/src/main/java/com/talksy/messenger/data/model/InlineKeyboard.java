package com.talksy.messenger.data.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class InlineKeyboard {

    public static class Button {
        public String text = "";
        public boolean miniApp;
        public String url;
        public String callbackData;

    }

    public List<List<Button>> rows = new ArrayList<>();

    public static InlineKeyboard parse(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray rowsArr = root.optJSONArray("inline_keyboard");
            if (rowsArr == null || rowsArr.length() == 0) return null;
            InlineKeyboard kb = new InlineKeyboard();
            for (int i = 0; i < rowsArr.length(); i++) {
                JSONArray rowArr = rowsArr.optJSONArray(i);
                if (rowArr == null) continue;
                List<Button> row = new ArrayList<>();
                for (int j = 0; j < rowArr.length(); j++) {
                    JSONObject b = rowArr.optJSONObject(j);
                    if (b == null) continue;
                    Button btn = new Button();
                    btn.text = b.optString("text", "");
                    btn.miniApp = b.optBoolean("mini_app", false);
                    btn.url = b.has("url") ? b.optString("url") : null;
                    btn.callbackData = b.has("callback_data") ? b.optString("callback_data") : null;
                    if (!btn.text.isEmpty()) row.add(btn);
                }
                if (!row.isEmpty()) kb.rows.add(row);
            }
            return kb.rows.isEmpty() ? null : kb;
        } catch (Exception e) {
            return null;
        }
    }
}
