package com.talksy.messenger.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.talksy.messenger.R;

public final class NumbersUi {
    private NumbersUi() {}

    public static EditText field(Context c, String hint, int inputType) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setTextColor(EconomyUi.color(c, R.color.text1));
        e.setHintTextColor(EconomyUi.color(c, R.color.text3));
        e.setBackground(EconomyUi.round(c, EconomyUi.color(c, R.color.bg3), 12));
        e.setPadding(EconomyUi.dp(c, 14), EconomyUi.dp(c, 12), EconomyUi.dp(c, 14), EconomyUi.dp(c, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = EconomyUi.dp(c, 8);
        e.setLayoutParams(lp);
        return e;
    }

    public static int PASSWORD = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
    public static int PIN = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;

    public static LinearLayout form(Context c, CharSequence note, EditText... fields) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(EconomyUi.dp(c, 20), EconomyUi.dp(c, 4), EconomyUi.dp(c, 20), EconomyUi.dp(c, 4));
        if (note != null && note.length() > 0) l.addView(EconomyUi.text(c, note, 13, R.color.text2, false));
        for (EditText f : fields) l.addView(f);
        return l;
    }

    public static void copy(Context c, String label, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
        EconomyUi.toast(c, "Скопировано");
    }

    public static void showPassword(Activity a, String title, String display, String password, Runnable done) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(EconomyUi.dp(a, 20), EconomyUi.dp(a, 4), EconomyUi.dp(a, 20), EconomyUi.dp(a, 4));
        l.addView(EconomyUi.text(a, display, 20, R.color.text1, true));
        TextView pw = EconomyUi.text(a, password, 20, R.color.accent, true);
        pw.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pw.setGravity(Gravity.CENTER);
        pw.setTextIsSelectable(true);
        pw.setPadding(0, EconomyUi.dp(a, 14), 0, EconomyUi.dp(a, 8));
        l.addView(pw);
        l.addView(EconomyUi.text(a, "Это единственный раз, когда пароль виден. Войдите по нему на экране входа → «Анонимный номер». "
                + "Позже его можно изменить в Настройках → Безопасность аккаунта.", 12, R.color.text2, false));
        android.widget.Button cp = EconomyUi.button(a, "Копировать пароль", false);
        LinearLayout.LayoutParams cpLp = new LinearLayout.LayoutParams(-1, EconomyUi.dp(a, 42));
        cpLp.topMargin = EconomyUi.dp(a, 12);
        cp.setLayoutParams(cpLp);
        cp.setOnClickListener(v -> copy(a, "password", password));
        l.addView(cp);
        new TDialog.Builder(a).setTitle(title).setView(l).setCancelable(false)
                .setPositiveButton("Я сохранил пароль", (d, w) -> { if (done != null) done.run(); })
                .show();
    }

    public static String money(double amount, String currency) {
        return String.format(java.util.Locale.US, "%.2f %s", amount, currency);
    }
}
