package com.talksy.messenger.ui.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.R;
import com.talksy.messenger.data.IconManager;
import com.talksy.messenger.ui.ChatsActivity;
import com.talksy.messenger.ui.NewChatActivity;
import com.talksy.messenger.ui.ProfileActivity;
import com.talksy.messenger.ui.SettingsActivity;

public final class BottomNav {
    private BottomNav() {}

    public static final int CHATS = 0, CONTACTS = 1, SETTINGS = 2, PROFILE = 3;
    public static final int HEIGHT_DP = 64, MARGIN_DP = 10;

    public static int occupied(Context c) { return SettingsUi.dp(c, HEIGHT_DP + MARGIN_DP * 2); }

    public static void attach(Activity act, int selected) {
        FrameLayout content = act.findViewById(android.R.id.content);
        if (content == null || content.findViewWithTag("tk_bottom_nav") != null) return;
        Context c = act;
        LinearLayout bar = new LinearLayout(c);
        bar.setTag("tk_bottom_nav");
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(SettingsUi.dp(c, 32));
        g.setColor(ContextCompat.getColor(c, R.color.bg2));
        g.setStroke(1, ContextCompat.getColor(c, R.color.bg3));
        bar.setBackground(g);
        bar.setElevation(SettingsUi.dp(c, 8));
        bar.setPadding(SettingsUi.dp(c, 8), 0, SettingsUi.dp(c, 8), 0);
        String[] icons = {"secure_chat", "group", "settings", "contact_card"};
        int[] labels = {R.string.nav_chats, R.string.nav_contacts, R.string.nav_settings, R.string.nav_profile};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(c);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(c);
            int color = ContextCompat.getColor(c, i == selected ? R.color.accent : R.color.text3);
            IconManager.set(iv, icons[i], color);
            item.addView(iv, new LinearLayout.LayoutParams(SettingsUi.dp(c, 24), SettingsUi.dp(c, 24)));
            TextView tv = new TextView(c);
            tv.setText(labels[i]); tv.setTextSize(11); tv.setTextColor(color);
            if (i == selected) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            item.addView(tv);
            item.setOnClickListener(v -> go(act, selected, idx));
            bar.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(c, HEIGHT_DP), Gravity.BOTTOM);
        lp.setMargins(SettingsUi.dp(c, 16), 0, SettingsUi.dp(c, 16), SettingsUi.dp(c, MARGIN_DP));
        content.addView(bar, lp);

        if (content.getChildCount() > 1) {
            View main = content.getChildAt(0);
            main.setPadding(main.getPaddingLeft(), main.getPaddingTop(), main.getPaddingRight(), main.getPaddingBottom() + occupied(c));
            if (main instanceof ViewGroup) ((ViewGroup) main).setClipToPadding(false);
        }
    }

    private static void go(Activity act, int selected, int target) {
        if (target == selected) return;
        Class<?> cls = target == CHATS ? ChatsActivity.class : target == CONTACTS ? NewChatActivity.class
                : target == SETTINGS ? SettingsActivity.class : ProfileActivity.class;
        Intent i = new Intent(act, cls);
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        if (target == CHATS) i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        act.startActivity(i);
        act.overridePendingTransition(0, 0);
        if (selected != CHATS) act.finish();
    }
}
