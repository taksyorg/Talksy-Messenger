package com.talksy.messenger.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.net.ApiClient;

public abstract class EconomyScreen extends BaseActivity {
    protected ApiClient api;
    protected LinearLayout content;
    protected LinearLayout root;
    protected LinearLayout bar;
    protected android.widget.ScrollView scroll;

    protected abstract String screenTitle();
    protected abstract void onBuilt();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = new ApiClient(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(EconomyUi.color(this, R.color.bg));

        bar = EconomyUi.row(this);
        bar.setBackgroundColor(EconomyUi.color(this, R.color.bg2));
        bar.setElevation(EconomyUi.dp(this, 2));
        bar.setPadding(EconomyUi.dp(this, 6), 0, 0, 0);
        bar.setLayoutParams(new LinearLayout.LayoutParams(-1, EconomyUi.dp(this, 58)));
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setBackgroundResource(R.drawable.ripple_item_round);
        back.setLayoutParams(new LinearLayout.LayoutParams(EconomyUi.dp(this, 40), EconomyUi.dp(this, 40)));
        back.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        AnimUtils.pressScale(back);
        bar.addView(back);
        android.widget.TextView title = EconomyUi.text(this, screenTitle(), 16, R.color.text1, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(EconomyUi.dp(this, 8), 0, 0, 0);
        bar.addView(title);
        root.addView(bar);

        ScrollView sv = new ScrollView(this);
        scroll = sv;
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, EconomyUi.dp(this, 6), 0, EconomyUi.dp(this, 24));
        sv.addView(content);
        root.addView(sv);
        setContentView(root);
        onBuilt();
    }

    private static final int REQ_TOPUP = 4713;
    private Runnable payRetry;

    protected boolean offerPay(String error, String itemType, String itemId, long toUserId, int quantity, Runnable retry) {
        if (error == null || !error.contains("Недостаточно")) return false;
        new TDialog.Builder(this).setTitle("Не хватает средств")
                .setMessage(error + "\n\nПополнить недостающую сумму реальными деньгами и завершить покупку?")
                .setPositiveButton("Оплатить", (d, w) -> {
                    payRetry = retry;
                    android.content.Intent i = new android.content.Intent(this, PaymentsActivity.class);
                    i.putExtra(PaymentsActivity.EXTRA_ITEM_TYPE, itemType);
                    i.putExtra(PaymentsActivity.EXTRA_ITEM_ID, itemId);
                    if (toUserId > 0) i.putExtra(PaymentsActivity.EXTRA_TO_USER, toUserId);
                    if (quantity > 0) i.putExtra(PaymentsActivity.EXTRA_QTY, quantity);
                    startActivityForResult(i, REQ_TOPUP);
                    AnimUtils.forward(this);
                })
                .setNegativeButton(android.R.string.cancel, null).show();
        return true;
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_TOPUP && res == RESULT_OK && payRetry != null) { Runnable r = payRetry; payRetry = null; r.run(); }
    }
}
