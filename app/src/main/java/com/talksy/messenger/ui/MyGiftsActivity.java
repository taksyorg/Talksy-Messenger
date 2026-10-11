package com.talksy.messenger.ui;

import com.talksy.messenger.R;
import com.talksy.messenger.data.Prefs;

public class MyGiftsActivity extends EconomyScreen {
    @Override protected String screenTitle() { return "Подарки"; }

    @Override
    protected void onBuilt() { }

    @Override
    protected void onResume() {
        super.onResume();
        content.removeAllViews();
        long me = 0;
        try { me = Long.parseLong(new Prefs(this).get(Prefs.KEY_USER_ID, "0")); } catch (Exception ignored) {}
        android.widget.LinearLayout holder = new android.widget.LinearLayout(this);
        holder.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.addView(holder);
        GiftFlow.attachSection(this, api, holder, me, true);
        android.widget.TextView n = EconomyUi.text(this, "Чтобы подарить подарок, откройте чат с человеком и нажмите на значок подарка. "
                + "Если не хватает средств — недостающую сумму можно оплатить реальными деньгами.", 12, R.color.text3, false);
        n.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 14), EconomyUi.dp(this, 20), 0);
        content.addView(n);
    }
}
