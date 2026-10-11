package com.talksy.messenger.data;

import android.content.Context;

import org.json.JSONObject;

public final class SpecialMessage {
    public static final char MARK = '\uE000';
    public static final String LOC = "loc", CONTACT = "contact", ARTICLE = "article", PAY = "pay";

    public final String type;
    public final JSONObject data;

    private SpecialMessage(String type, JSONObject data) { this.type = type; this.data = data; }

    public static String encode(String type, JSONObject data) { return MARK + type + "\n" + data.toString(); }

    public static SpecialMessage parse(String text) {
        if (text == null || text.length() < 3 || text.charAt(0) != MARK) return null;
        int nl = text.indexOf('\n');
        if (nl < 0) return null;
        String type = text.substring(1, nl);
        if (!LOC.equals(type) && !CONTACT.equals(type) && !ARTICLE.equals(type) && !PAY.equals(type)) return null;
        try { return new SpecialMessage(type, new JSONObject(text.substring(nl + 1))); }
        catch (Exception e) { return null; }
    }

    public static String previewRu(String text) {
        SpecialMessage s = parse(text);
        if (s == null) return text;
        switch (s.type) {
            case LOC: return "📍 Геопозиция";
            case CONTACT: return "👤 Контакт: " + s.data.optString("name");
            case ARTICLE: { String t = s.data.optString("title"); return "📄 " + (t.isEmpty() ? "Статья" : t); }
            default: return "💸 Перевод";
        }
    }

    public static String preview(Context c, String text) { return previewRu(text); }
}
