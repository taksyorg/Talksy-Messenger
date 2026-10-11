package com.talksy.messenger.ui;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;

import org.json.JSONArray;
import org.json.JSONObject;

public class PaymentsActivity extends EconomyScreen {
    public static final String EXTRA_ITEM_TYPE = "item_type";
    public static final String EXTRA_ITEM_ID = "item_id";
    public static final String EXTRA_TO_USER = "to_user_id";
    public static final String EXTRA_QTY = "quantity";
    private static final int REQ_WEB = 4712;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private JSONObject config;
    private String currentOrderId;
    private int polls;
    private boolean itemMode;
    private boolean started;

    @Override protected String screenTitle() { return "Оплата"; }

    @Override
    protected void onBuilt() {
        itemMode = getIntent().getStringExtra(EXTRA_ITEM_TYPE) != null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentOrderId == null) load();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void load() {
        api.economyGet("/payments/config", (ok, cfg, err) -> {
            if (!ok) { content.removeAllViews(); content.addView(EconomyUi.text(this, err != null ? err : "Ошибка загрузки", 14, R.color.err, false)); return; }
            config = cfg;
            if (itemMode && !started) { started = true; startItemOrder(); return; }
            api.economyGet("/payments/orders", (ok2, h, err2) -> render(ok2 ? h : null));
        });
    }

    private void render(JSONObject history) {
        content.removeAllViews();
        boolean enabled = config.optBoolean("enabled");
        String cur = config.optString("currency", "EUR");

        LinearLayout bal = EconomyUi.card(this);
        bal.setGravity(Gravity.CENTER_HORIZONTAL);
        bal.addView(EconomyUi.text(this, "Баланс", 12, R.color.text2, false));
        TextView big = EconomyUi.text(this, EconomyUi.price(this, config.optLong("balance"), 28), 30, R.color.text1, true);
        bal.addView(big);
        content.addView(bal);

        TextView h = EconomyUi.text(this, "Пополнить за реальные деньги", 14, R.color.text2, true);
        h.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 10), 0, EconomyUi.dp(this, 4));
        content.addView(h);

        if (!enabled) {
            LinearLayout c = EconomyUi.card(this);
            c.addView(EconomyUi.text(this, "Оплата реальными деньгами пока не подключена на сервере. "
                    + "Администратору нужно задать STRIPE_SECRET_KEY (боевой режим) или PAYMENTS_SANDBOX=1 (тест).", 13, R.color.text2, false));
            content.addView(c);
        } else {
            JSONArray packs = config.optJSONArray("packs");
            for (int i = 0; packs != null && i < packs.length(); i++) {
                JSONObject p = packs.optJSONObject(i);
                LinearLayout c = EconomyUi.card(this);
                LinearLayout r = EconomyUi.row(this);
                TextView t = EconomyUi.text(this, EconomyUi.price(this, p.optLong("ftr"), 18), 18, R.color.text1, true);
                t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                r.addView(t);
                Button b = EconomyUi.button(this, NumbersUi.money(p.optDouble("amount"), cur), true);
                b.setLayoutParams(new LinearLayout.LayoutParams(-2, EconomyUi.dp(this, 40)));
                b.setOnClickListener(v -> confirmPack(p, cur));
                r.addView(b);
                c.addView(r);
                content.addView(c);
            }
            if (config.optBoolean("sandbox")) {
                TextView n = EconomyUi.text(this, "Тестовый режим: деньги не списываются.", 12, R.color.text3, false);
                n.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 4), 0, 0);
                content.addView(n);
            }
        }

        TextView h2 = EconomyUi.text(this, "История платежей", 14, R.color.text2, true);
        h2.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 16), 0, EconomyUi.dp(this, 4));
        content.addView(h2);
        JSONArray arr = history == null ? null : history.optJSONArray("orders");
        if (arr == null || arr.length() == 0) {
            TextView e = EconomyUi.text(this, "Платежей пока нет", 13, R.color.text3, false);
            e.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 4), 0, 0);
            content.addView(e);
            return;
        }
        LinearLayout list = EconomyUi.card(this);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            LinearLayout r = EconomyUi.row(this);
            r.setPadding(0, EconomyUi.dp(this, 8), 0, EconomyUi.dp(this, 8));
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            left.addView(EconomyUi.text(this, o.optString("title"), 14, R.color.text1, false));
            left.addView(EconomyUi.text(this, EconomyUi.date(o.optDouble("created_at")) + " • " + statusName(o.optString("status")), 11, R.color.text3, false));
            r.addView(left);
            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            right.addView(EconomyUi.text(this, NumbersUi.money(o.optDouble("amount"), o.optString("currency")), 14, R.color.text1, true));
            right.addView(EconomyUi.text(this, "+" + EconomyUi.amount(o.optLong("ftr_amount")), 11, R.color.text2, false));
            r.addView(right);
            final String status = o.optString("status");
            if ("PENDING".equals(status) && o.optString("pay_path", "").length() > 0) {
                r.setOnClickListener(v -> openPay(o.optString("id"), o.optString("pay_path")));
            }
            list.addView(r);
        }
        content.addView(list);
    }

    private static String statusName(String s) {
        switch (s) {
            case "PAID": return "оплачен";
            case "PENDING": return "ожидает оплаты";
            case "CANCELED": return "отменён";
            case "EXPIRED": return "истёк";
            default: return s;
        }
    }

    private void confirmPack(JSONObject p, String cur) {
        new TDialog.Builder(this).setTitle("Подтверждение покупки")
                .setMessage("Купить " + EconomyUi.amount(p.optLong("ftr")) + " за " + NumbersUi.money(p.optDouble("amount"), cur) + "?\nДалее откроется страница оплаты.")
                .setPositiveButton("Перейти к оплате", (d, w) -> {
                    JSONObject b = new JSONObject();
                    try { b.put("kind", "pack"); b.put("pack_id", p.optString("id")); } catch (Exception ignored) {}
                    createOrder(b);
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void startItemOrder() {
        JSONObject b = new JSONObject();
        try {
            b.put("kind", "item");
            b.put("item_type", getIntent().getStringExtra(EXTRA_ITEM_TYPE));
            b.put("item_id", getIntent().getStringExtra(EXTRA_ITEM_ID));
            if (getIntent().hasExtra(EXTRA_TO_USER)) b.put("to_user_id", getIntent().getLongExtra(EXTRA_TO_USER, 0));
            if (getIntent().hasExtra(EXTRA_QTY)) b.put("quantity", getIntent().getIntExtra(EXTRA_QTY, 1));
        } catch (Exception ignored) {}
        api.economyPost("/payments/orders", b, (ok, json, err) -> {
            if (!ok || json == null) {
                if (err != null && err.contains("достаточно средств")) { setResult(RESULT_OK); finish(); return; }
                EconomyUi.toast(this, err != null ? err : "Не удалось создать заказ");
                itemMode = false;
                load();
                return;
            }
            JSONObject o = json.optJSONObject("order");
            String cur = o.optString("currency");
            new TDialog.Builder(this).setTitle("Подтверждение покупки")
                    .setMessage(o.optString("title") + "\n\nНе хватает средств. Будет оплачено " + NumbersUi.money(o.optDouble("amount"), cur)
                            + " и зачислено " + EconomyUi.amount(o.optLong("ftr_amount")) + ". После оплаты покупка завершится автоматически.")
                    .setCancelable(false)
                    .setPositiveButton("Перейти к оплате", (d, w) -> openPay(o.optString("id"), o.optString("pay_path")))
                    .setNegativeButton(android.R.string.cancel, (d, w) -> {
                        JSONObject c = new JSONObject();
                        api.economyPost("/payments/orders/" + o.optString("id") + "/cancel", c, (a, j, e) -> { });
                        finish();
                    }).show();
        });
    }

    private void createOrder(JSONObject body) {
        api.economyPost("/payments/orders", body, (ok, json, err) -> {
            if (!ok || json == null) { EconomyUi.toast(this, err != null ? err : "Не удалось создать заказ"); return; }
            JSONObject o = json.optJSONObject("order");
            openPay(o.optString("id"), o.optString("pay_path"));
        });
    }

    private void openPay(String orderId, String payPath) {
        currentOrderId = orderId;
        Intent i = new Intent(this, PayWebActivity.class);
        i.putExtra(PayWebActivity.EXTRA_URL, api.authedUrl(payPath));
        startActivityForResult(i, REQ_WEB);
        AnimUtils.forward(this);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_WEB && currentOrderId != null) {
            polls = 0;
            content.removeAllViews();
            content.addView(EconomyUi.text(this, "Проверяем оплату…", 14, R.color.text2, false));
            poll();
        }
    }

    private void poll() {
        final String id = currentOrderId;
        api.economyGet("/payments/orders/" + id, (ok, json, err) -> {
            if (!ok || json == null) { finishPoll(null); return; }
            String st = json.optJSONObject("order").optString("status");
            if ("PAID".equals(st)) { finishPoll(json); return; }
            if (!"PENDING".equals(st)) { finishPoll(json); return; }
            if (++polls >= 30) { finishPoll(json); return; }
            handler.postDelayed(this::poll, 2000);
        });
    }

    private void finishPoll(JSONObject json) {
        currentOrderId = null;
        String st = json == null ? "" : json.optJSONObject("order").optString("status");
        if ("PAID".equals(st)) {
            JSONObject o = json.optJSONObject("order");
            new TDialog.Builder(this).setTitle("Оплата прошла")
                    .setMessage("Зачислено " + EconomyUi.amount(o.optLong("ftr_amount")) + ".\nОплачено " + NumbersUi.money(o.optDouble("amount"), o.optString("currency")) + ".")
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        if (itemMode) { setResult(RESULT_OK); finish(); AnimUtils.back(this); }
                        else load();
                    }).show();
            return;
        }
        if ("PENDING".equals(st)) EconomyUi.toast(this, "Платёж ещё обрабатывается — баланс обновится автоматически");
        else if (!st.isEmpty()) EconomyUi.toast(this, "Оплата не завершена");
        if (itemMode) { itemMode = false; }
        load();
    }
}
