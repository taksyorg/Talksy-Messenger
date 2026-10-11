package com.talksy.messenger.data.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BotKeyboard {
    public List<List<String>> rows = new ArrayList<>();

    public static BotKeyboard parse(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray rowsArr = root.optJSONArray("buttons");
            if (rowsArr == null || rowsArr.length() == 0) return null;
            BotKeyboard kb = new BotKeyboard();
            for (int i = 0; i < rowsArr.length(); i++) {
                JSONArray rowArr = rowsArr.optJSONArray(i);
                if (rowArr == null) continue;
                List<String> row = new ArrayList<>();
                for (int j = 0; j < rowArr.length(); j++) {
                    String s = rowArr.optString(j, "");
                    if (!s.isEmpty()) row.add(s);
                }
                if (!row.isEmpty()) kb.rows.add(row);
            }
            return kb.rows.isEmpty() ? null : kb;
        } catch (Exception e) {
            return null;
        }
    }
}
