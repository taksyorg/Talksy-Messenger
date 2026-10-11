package com.talksy.messenger.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class NumberShopActivity extends EconomyScreen {
    private static final int REQ_PAY = 4711;

    private String prefix = "888";
    private String sort = "price_asc";
    private boolean onlyListed = false;
    private String query = "";
    private long balance = 0;
    private int total = 0;
    private final List<JSONObject> items = new ArrayList<>();
    private LinearLayout listBox;
    private TextView balanceView;
    private LinearLayout headerBox;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private String pendingBuyNumber;
    private long pendingBuyPrice;

    @Override protected String screenTitle() { return "Покупка номеров"; }

    @Override
    protected void onBuilt() {
        headerBox = new LinearLayout(this);
        headerBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(headerBox);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(listBox);
        buildHeader();
        reload();
    }

    private void buildHeader() {
        headerBox.removeAllViews();

        LinearLayout balCard = EconomyUi.card(this);
        LinearLayout r = EconomyUi.row(this);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        left.addView(EconomyUi.text(this, "Ваш баланс", 12, R.color.text2, false));
        balanceView = EconomyUi.text(this, EconomyUi.price(this, balance, 20), 20, R.color.text1, true);
        left.addView(balanceView);
        r.addView(left);
        Button top = EconomyUi.button(this, "Пополнить", false);
        top.setLayoutParams(new LinearLayout.LayoutParams(-2, EconomyUi.dp(this, 38)));
        top.setOnClickListener(v -> { startActivity(new Intent(this, PaymentsActivity.class)); AnimUtils.forward(this); });
        r.addView(top);
        balCard.addView(r);
        balCard.addView(EconomyUi.text(this, "Номера локальные: работают только внутри Talksy, почта для них не нужна. "
                + "Вход — по паролю, который выдаётся при покупке.", 12, R.color.text3, false));
        headerBox.addView(balCard);

        headerBox.addView(chipRow(new String[]{"+888", "+999"}, new String[]{"888", "999"}, prefix, false, v -> { prefix = v; refresh(); }));
        headerBox.addView(chipRow(new String[]{"Дешёвые", "Редкие", "Короткие", "Дорогие", "От игроков"},
                new String[]{"price_asc", "rare", "short", "price_desc", "listed"},
                onlyListed ? "listed" : sort, true, v -> {
                    if ("listed".equals(v)) onlyListed = !onlyListed; else { sort = v; onlyListed = false; }
                    refresh();
                }));

        EditText search = NumbersUi.field(this, "Поиск по цифрам", InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) search.getLayoutParams();
        lp.setMargins(EconomyUi.dp(this, 14), EconomyUi.dp(this, 6), EconomyUi.dp(this, 14), EconomyUi.dp(this, 4));
        search.setText(query);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                final String q = s.toString().trim();
                pendingSearch = () -> { query = q; reload(); };
                handler.postDelayed(pendingSearch, 350);
            }
        });
        headerBox.addView(search);
    }

    private interface Pick { void on(String value); }

    private HorizontalScrollView chipRow(String[] labels, String[] values, String selected, boolean multi, Pick pick) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(EconomyUi.dp(this, 10), EconomyUi.dp(this, 6), EconomyUi.dp(this, 10), EconomyUi.dp(this, 2));
        for (int i = 0; i < labels.length; i++) {
            final String val = values[i];
            boolean on = val.equals(selected) || ("listed".equals(val) && onlyListed) || (multi && !"listed".equals(val) && !onlyListed && val.equals(sort));
            TextView chip = EconomyUi.chip(this, labels[i], on);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(EconomyUi.dp(this, 4), 0, EconomyUi.dp(this, 4), 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> pick.on(val));
            row.addView(chip);
        }
        hs.addView(row);
        return hs;
    }

    private void refresh() { buildHeader(); reload(); }

    @Override
    protected void onResume() {
        super.onResume();
        if (api != null && content != null && balanceView != null) reload();
    }

    private void reload() { items.clear(); load(0); }

    private void load(int offset) {
        String path = "/numbers/market?prefix=" + prefix + "&sort=" + sort + "&offset=" + offset
                + (onlyListed ? "&only=listed" : "") + (query.isEmpty() ? "" : "&q=" + query);
        api.economyGet(path, (ok, json, err) -> {
            if (!ok || json == null) {
                listBox.removeAllViews();
                listBox.addView(EconomyUi.text(this, err != null ? err : "Не удалось загрузить номера", 14, R.color.err, false));
                return;
            }
            balance = json.optLong("balance");
            total = json.optInt("total");
            if (balanceView != null) balanceView.setText(EconomyUi.price(this, balance, 20));
            JSONArray arr = json.optJSONArray("numbers");
            if (offset == 0) items.clear();
            for (int i = 0; arr != null && i < arr.length(); i++) items.add(arr.optJSONObject(i));
            render();
        });
    }

    private void render() {
        listBox.removeAllViews();
        if (items.isEmpty()) {
            TextView e = EconomyUi.text(this, "Подходящих номеров нет", 14, R.color.text3, false);
            e.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 20), 0, 0);
            listBox.addView(e);
            return;
        }
        for (JSONObject n : items) listBox.addView(card(n));
        if (items.size() < total) {
            Button more = EconomyUi.button(this, "Показать ещё", false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, EconomyUi.dp(this, 44));
            lp.setMargins(EconomyUi.dp(this, 14), EconomyUi.dp(this, 8), EconomyUi.dp(this, 14), 0);
            more.setLayoutParams(lp);
            more.setOnClickListener(v -> load(items.size()));
            listBox.addView(more);
        }
    }

    private LinearLayout card(JSONObject n) {
        LinearLayout c = EconomyUi.card(this);
        LinearLayout top = EconomyUi.row(this);
        TextView num = EconomyUi.text(this, n.optString("display"), 19, R.color.text1, true);
        num.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(num);
        top.addView(EconomyUi.pricePill(this, n.optLong("price")));
        c.addView(top);

        LinearLayout meta = EconomyUi.row(this);
        meta.setPadding(0, EconomyUi.dp(this, 6), 0, 0);
        TextView code = EconomyUi.text(this, "Код +" + n.optString("prefix"), 12, R.color.text2, false);
        meta.addView(code);
        TextView rar = EconomyUi.text(this, "  •  " + n.optString("rarity_label"), 12, R.color.text2, true);
        rar.setTextColor(EconomyUi.rarityColor(n.optString("rarity")));
        meta.addView(rar);
        boolean mine = n.optBoolean("mine");
        String status = mine ? "Ваше объявление" : (n.optBoolean("by_user") ? "Продаёт игрок" : "Доступен");
        TextView st = EconomyUi.text(this, "  •  " + status, 12, R.color.text2, false);
        meta.addView(st);
        c.addView(meta);

        Button buy = EconomyUi.button(this, mine ? "Ваш номер" : "Купить", !mine);
        buy.setEnabled(!mine);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, EconomyUi.dp(this, 42));
        lp.topMargin = EconomyUi.dp(this, 10);
        buy.setLayoutParams(lp);
        buy.setOnClickListener(v -> confirmBuy(n));
        c.addView(buy);
        return c;
    }

    private void confirmBuy(JSONObject n) {
        long price = n.optLong("price");
        String msg = "Купить " + n.optString("display") + " за " + EconomyUi.amount(price) + "?\n\nБаланс после покупки: "
                + EconomyUi.amount(balance - price) + ".\nСервер сгенерирует пароль для входа и покажет его один раз.";
        if (balance < price) {
            new TDialog.Builder(this).setTitle("Недостаточно средств")
                    .setMessage("Нужно " + EconomyUi.amount(price) + ", на балансе " + EconomyUi.amount(balance) + ". Пополнить недостающую сумму и купить номер?")
                    .setPositiveButton("Пополнить", (d, w) -> {
                        pendingBuyNumber = n.optString("number");
                        pendingBuyPrice = price;
                        Intent i = new Intent(this, PaymentsActivity.class);
                        i.putExtra(PaymentsActivity.EXTRA_ITEM_TYPE, "number");
                        i.putExtra(PaymentsActivity.EXTRA_ITEM_ID, pendingBuyNumber);
                        startActivityForResult(i, REQ_PAY);
                        AnimUtils.forward(this);
                    })
                    .setNegativeButton(android.R.string.cancel, null).show();
            return;
        }
        new TDialog.Builder(this).setTitle("Подтверждение покупки").setMessage(msg)
                .setPositiveButton("Купить", (d, w) -> doBuy(n.optString("number"), price))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void doBuy(String number, long price) {
        JSONObject b = new JSONObject();
        try { b.put("number", number); b.put("expected_price", price); } catch (Exception ignored) {}
        api.economyPost("/numbers/buy", b, (ok, json, err) -> {
            if (!ok || json == null) {
                EconomyUi.toast(this, err != null ? err : "Не удалось купить номер");
                reload();
                return;
            }
            NumbersUi.showPassword(this, "Номер куплен", json.optString("display"), json.optString("password"), this::reload);
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PAY && res == RESULT_OK && pendingBuyNumber != null) {
            String n = pendingBuyNumber; long p = pendingBuyPrice;
            pendingBuyNumber = null;
            doBuy(n, p);
        }
    }
}
