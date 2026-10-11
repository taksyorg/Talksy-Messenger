package com.talksy.messenger.ui;

import android.app.Activity;
import android.content.Intent;

import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.net.ApiClient;

public final class PremiumGate {
    private PremiumGate() {}

    public static void require(Activity a, ApiClient api, String featureName, Runnable onOk) {
        api.economyGet("/economy/premium", (ok, json, err) -> {
            if (ok && json.optBoolean("active")) { onOk.run(); return; }
            if (!ok && err != null) { EconomyUi.toast(a, err); return; }
            show(a, featureName);
        });
    }

    public static void show(Activity a, String featureName) {
        new TDialog.Builder(a).setTitle("Talksy Premium")
                .setMessage(featureName + " — функция Talksy Premium. Личные сообщения, группы, звонки и файлы остаются бесплатными.")
                .setPositiveButton("Подробнее", (d, w) -> { a.startActivity(new Intent(a, PremiumActivity.class)); AnimUtils.forward(a); })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    public static boolean isPremiumError(String err) {
        return err != null && err.contains("Premium");
    }
}
