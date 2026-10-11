package com.talksy.messenger.ui;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.talksy.messenger.R;

import org.json.JSONArray;
import org.json.JSONObject;

public class NumberSellActivity extends EconomyScreen {
    private int tab = 0;
    private JSONObject mine, history;
    private LinearLayout tabs;
    private LinearLayout body;
    private int fee = 5;

    @Override protected String screenTitle() { return "Продать номер"; }

    @Override
    protected void onBuilt() {
        tabs = EconomyUi.row(this);
        tabs.setPadding(EconomyUi.dp(this, 10), EconomyUi.dp(this, 6), EconomyUi.dp(this, 10), EconomyUi.dp(this, 4));
        content.addView(tabs);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        content.addView(body);
        drawTabs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void drawTabs() {
        tabs.removeAllViews();
        String[] names = {"Мои номера", "Объявления", "История"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView chip = EconomyUi.chip(this, names[i], tab == i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(EconomyUi.dp(this, 4), 0, EconomyUi.dp(this, 4), 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> { tab = idx; drawTabs(); render(); });
            tabs.addView(chip);
        }
    }

    private void load() {
        api.economyGet("/numbers/mine", (ok, m, err) -> {
            if (!ok) { body.removeAllViews(); body.addView(EconomyUi.text(this, err != null ? err : "Ошибка загрузки", 14, R.color.err, false)); return; }
            mine = m;
            fee = m.optInt("fee_percent", 5);
            api.economyGet("/numbers/history", (ok2, h, err2) -> { history = ok2 ? h : null; render(); });
        });
    }

    private void render() {
        body.removeAllViews();
        if (mine == null) return;
        if (tab == 0) renderMine();
        else if (tab == 1) renderListings();
        else renderHistory();
    }

    private void empty(String text) {
        TextView e = EconomyUi.text(this, text, 14, R.color.text3, false);
        e.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 20), EconomyUi.dp(this, 20), 0);
        body.addView(e);
    }

    private void renderMine() {
        JSONArray arr = mine.optJSONArray("numbers");
        if (arr == null || arr.length() == 0) { empty("У вас пока нет номеров. Купить номер можно в разделе «Покупка номеров»."); return; }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject n = arr.optJSONObject(i);
            boolean listed = "listed".equals(n.optString("status"));
            LinearLayout c = EconomyUi.card(this);
            LinearLayout top = EconomyUi.row(this);
            TextView num = EconomyUi.text(this, n.optString("display"), 18, R.color.text1, true);
            num.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            top.addView(num);
            TextView rar = EconomyUi.text(this, n.optString("rarity_label"), 12, R.color.text2, true);
            rar.setTextColor(EconomyUi.rarityColor(n.optString("rarity")));
            top.addView(rar);
            c.addView(top);
            String sub = listed ? "В продаже за " + EconomyUi.amount(n.optLong("listing_price")) : "Не выставлен на продажу";
            c.addView(EconomyUi.text(this, sub, 12, R.color.text2, false));

            LinearLayout actions = EconomyUi.row(this);
            actions.setPadding(0, EconomyUi.dp(this, 10), 0, 0);
            Button primary = EconomyUi.button(this, listed ? "Снять с продажи" : "Продать", !listed);
            primary.setLayoutParams(new LinearLayout.LayoutParams(0, EconomyUi.dp(this, 42), 1f));
            primary.setOnClickListener(v -> { if (listed) unlist(n.optString("number")); else askPrice(n); });
            actions.addView(primary);
            Button reset = EconomyUi.button(this, "Новый пароль", false);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, EconomyUi.dp(this, 42), 1f);
            rl.setMarginStart(EconomyUi.dp(this, 8));
            reset.setLayoutParams(rl);
            reset.setOnClickListener(v -> confirmReset(n));
            actions.addView(reset);
            c.addView(actions);
            body.addView(c);
        }
        TextView note = EconomyUi.text(this, "Комиссия Talksy с продажи — " + fee + "%. После продажи прежний владелец теряет доступ к номеру: "
                + "пароль перевыпускается, сеансы завершаются, профиль и переписка номера не передаются.", 12, R.color.text3, false);
        note.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 10), EconomyUi.dp(this, 20), 0);
        body.addView(note);
    }

    private void askPrice(JSONObject n) {
        EditText price = NumbersUi.field(this, "Цена, ◈", InputType.TYPE_CLASS_NUMBER);
        long suggested = n.optLong("suggested_price");
        if (suggested > 0) price.setText(String.valueOf(suggested));
        TextView get = EconomyUi.text(this, "", 12, R.color.text2, false);
        get.setPadding(0, EconomyUi.dp(this, 8), 0, 0);
        Runnable upd = () -> {
            long p = parse(price.getText().toString());
            get.setText(p > 0 ? "Вы получите: " + EconomyUi.amount(p - p * fee / 100) + " (комиссия " + fee + "%)" : "");
        };
        price.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { upd.run(); }
        });
        LinearLayout form = NumbersUi.form(this, "Назначьте цену для " + n.optString("display"), price);
        form.addView(get);
        upd.run();
        new TDialog.Builder(this).setTitle("Выставить на продажу").setView(form)
                .setPositiveButton("Выставить", (d, w) -> {
                    long p = parse(price.getText().toString());
                    JSONObject b = new JSONObject();
                    try { b.put("number", n.optString("number")); b.put("price", p); } catch (Exception ignored) {}
                    api.economyPost("/numbers/sell", b, (ok, json, err) -> {
                        if (ok) { EconomyUi.toast(this, "Номер выставлен на продажу"); load(); }
                        else EconomyUi.toast(this, err != null ? err : "Ошибка");
                    });
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private static long parse(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    private void unlist(String number) {
        JSONObject b = new JSONObject();
        try { b.put("number", number); } catch (Exception ignored) {}
        api.economyPost("/numbers/unlist", b, (ok, json, err) -> {
            if (ok) load(); else EconomyUi.toast(this, err != null ? err : "Ошибка");
        });
    }

    private void confirmReset(JSONObject n) {
        new TDialog.Builder(this).setTitle("Новый пароль")
                .setMessage("Для " + n.optString("display") + " будет создан новый пароль, а все текущие сеансы этого номера завершатся. Продолжить?")
                .setPositiveButton("Создать", (d, w) -> {
                    JSONObject b = new JSONObject();
                    try { b.put("number", n.optString("number")); } catch (Exception ignored) {}
                    api.economyPost("/numbers/reset-password", b, (ok, json, err) -> {
                        if (ok) NumbersUi.showPassword(this, "Новый пароль", json.optString("display"), json.optString("password"), null);
                        else EconomyUi.toast(this, err != null ? err : "Ошибка");
                    });
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void renderListings() {
        JSONArray arr = history == null ? null : history.optJSONArray("listings");
        if (arr == null || arr.length() == 0) { empty("Активных объявлений нет."); return; }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject l = arr.optJSONObject(i);
            LinearLayout c = EconomyUi.card(this);
            LinearLayout top = EconomyUi.row(this);
            TextView num = EconomyUi.text(this, l.optString("display"), 17, R.color.text1, true);
            num.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            top.addView(num);
            top.addView(EconomyUi.pricePill(this, l.optLong("price")));
            c.addView(top);
            c.addView(EconomyUi.text(this, "Выставлен " + EconomyUi.date(l.optDouble("time")), 12, R.color.text3, false));
            Button un = EconomyUi.button(this, "Снять с продажи", false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, EconomyUi.dp(this, 40));
            lp.topMargin = EconomyUi.dp(this, 8);
            un.setLayoutParams(lp);
            un.setOnClickListener(v -> unlist(l.optString("number")));
            c.addView(un);
            body.addView(c);
        }
    }

    private void renderHistory() {
        if (history == null) { empty("История недоступна."); return; }
        section("Купленные номера", history.optJSONArray("bought"), "Куплен", "Покупок пока нет");
        section("Проданные номера", history.optJSONArray("sold"), "Продан (получено)", "Продаж пока нет");
    }

    private void section(String title, JSONArray arr, String verb, String emptyText) {
        TextView h = EconomyUi.text(this, title, 14, R.color.text2, true);
        h.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 14), 0, EconomyUi.dp(this, 4));
        body.addView(h);
        if (arr == null || arr.length() == 0) {
            TextView e = EconomyUi.text(this, emptyText, 13, R.color.text3, false);
            e.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 4), 0, 0);
            body.addView(e);
            return;
        }
        LinearLayout list = EconomyUi.card(this);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject x = arr.optJSONObject(i);
            LinearLayout r = EconomyUi.row(this);
            r.setPadding(0, EconomyUi.dp(this, 8), 0, EconomyUi.dp(this, 8));
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            left.addView(EconomyUi.text(this, x.optString("display"), 14, R.color.text1, true));
            left.addView(EconomyUi.text(this, EconomyUi.date(x.optDouble("time")), 11, R.color.text3, false));
            r.addView(left);
            TextView amt = EconomyUi.text(this, EconomyUi.amount(x.optLong("price")), 14, R.color.text1, true);
            amt.setGravity(Gravity.END);
            r.addView(amt);
            list.addView(r);
        }
        body.addView(list);
    }
}
