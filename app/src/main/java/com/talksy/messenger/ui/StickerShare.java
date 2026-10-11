package com.talksy.messenger.ui;

import android.app.Activity;
import android.content.Context;

import com.talksy.messenger.net.ApiClient;

import org.json.JSONObject;

public final class StickerShare {
    private StickerShare() {}

    public static void onTap(Context ctx, String mediaUrl) {
        if (!(ctx instanceof Activity) || mediaUrl == null || mediaUrl.isEmpty()) return;
        final Activity a = (Activity) ctx;
        final ApiClient api = new ApiClient(a);
        String q;
        try { q = java.net.URLEncoder.encode(mediaUrl, "UTF-8"); } catch (Exception e) { return; }
        api.economyGet("/stickers/lookup?media_url=" + q, (ok, json, err) -> {
            if (!ok || json == null) return;
            JSONObject pack = json.optJSONObject("pack");
            if (pack == null) return;
            if (pack.optBoolean("installed")) { EconomyUi.toast(a, "Пак «" + pack.optString("name") + "» уже добавлен"); return; }
            new TDialog.Builder(a).setTitle(pack.optString("name"))
                    .setMessage("Стикеров в паке: " + pack.optInt("sticker_count") + ".\nДобавить этот стикер-пак к себе?")
                    .setPositiveButton("Добавить стикер-пак", (d, w) ->
                            api.economyPost("/stickers/packs/" + pack.optLong("id") + "/install", new JSONObject(), (ok2, j2, e2) ->
                                    EconomyUi.toast(a, ok2 ? "Стикер-пак добавлен" : (e2 != null ? e2 : "Не удалось добавить"))))
                    .setNegativeButton(android.R.string.cancel, null).show();
        });
    }
}
