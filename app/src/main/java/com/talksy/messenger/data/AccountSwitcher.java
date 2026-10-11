package com.talksy.messenger.data;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.talksy.messenger.ui.TDialog;

import com.talksy.messenger.R;
import com.talksy.messenger.ui.ChatsActivity;

import java.util.List;

public class AccountSwitcher {

    private AccountSwitcher() {}

    public static void show(Activity activity) {
        Accounts.rememberCurrent(activity);
        List<Accounts.Account> accounts = Accounts.list(activity);
        long currentId = Accounts.currentUserId(activity);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 8);
        root.setPadding(pad, pad, pad, pad);

        TDialog dialog = new TDialog.Builder(activity)
                .setTitle(R.string.switch_account_title)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        for (Accounts.Account acc : accounts) {
            root.addView(buildRow(activity, acc.name, acc.handle, acc.color,
                    acc.userId == currentId, v -> {
                        dialog.dismiss();
                        if (acc.userId != currentId) {
                            Accounts.switchTo(activity, acc);
                            restartToChats(activity);
                        }
                    }));
        }

        if (Accounts.canAddMore(activity)) {
            root.addView(buildRow(activity, activity.getString(R.string.add_account), null, null, false, v -> {
                dialog.dismiss();

                activity.startActivity(new Intent(activity, com.talksy.messenger.auth.PhoneActivity.class));
            }));
        } else if (accounts.size() >= Accounts.MAX_ACCOUNTS) {
            TextView hint = new TextView(activity);
            hint.setText(activity.getString(R.string.max_accounts_hint, Accounts.MAX_ACCOUNTS));
            hint.setTextColor(Color.GRAY);
            hint.setTextSize(12f);
            hint.setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 4));
            root.addView(hint);
        }

        dialog.show();
    }

    private static View buildRow(Activity ctx, String title, String subtitle, String colorHex,
                                  boolean active, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padV = dp(ctx, 10), padH = dp(ctx, 8);
        row.setPadding(padH, padV, padH, padV);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackgroundResource(R.drawable.ripple_item);
        row.setOnClickListener(onClick);

        TextView avatar = new TextView(ctx);
        int size = dp(ctx, 40);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(size, size);
        avatarLp.setMarginEnd(dp(ctx, 12));
        avatar.setLayoutParams(avatarLp);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextColor(Color.WHITE);
        avatar.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(ctx, 14));
        try {
            bg.setColor(colorHex != null ? Color.parseColor(colorHex) : ctx.getResources().getColor(R.color.accent));
        } catch (Exception e) {
            bg.setColor(ctx.getResources().getColor(R.color.accent));
        }
        avatar.setBackground(bg);
        avatar.setText(colorHex == null ? "+" : initials(title));
        row.addView(avatar);

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(textsLp);

        TextView txtTitle = new TextView(ctx);
        txtTitle.setText(title);
        txtTitle.setTextSize(15f);
        txtTitle.setTextColor(ctx.getResources().getColor(R.color.text1));
        texts.addView(txtTitle);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView txtSub = new TextView(ctx);
            txtSub.setText(subtitle);
            txtSub.setTextSize(12f);
            txtSub.setTextColor(ctx.getResources().getColor(R.color.text2));
            texts.addView(txtSub);
        }
        row.addView(texts);

        if (active) {
            TextView check = new TextView(ctx);
            IconManager.set(check, "success", 20, ctx.getResources().getColor(R.color.accent));
            row.addView(check);
        }
        return row;
    }

    private static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] p = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.length && i < 2; i++) if (!p[i].isEmpty()) sb.append(Character.toUpperCase(p[i].charAt(0)));
        return sb.toString();
    }

    private static void restartToChats(Activity from) {
        Intent i = new Intent(from, ChatsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        from.startActivity(i);
        Toast.makeText(from, R.string.account_switched, Toast.LENGTH_SHORT).show();
    }

    private static int dp(Activity ctx, float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }
}
