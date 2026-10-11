package com.talksy.messenger.ui;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.talksy.messenger.R;
import com.talksy.messenger.net.ApiClient;

import org.json.JSONObject;

public final class UsernameDialog {
    private UsernameDialog() {}

    public static void show(Activity a, ApiClient api, String current, Runnable onChanged) {
        EditText in = NumbersUi.field(a, "username", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (current != null) in.setText(current.replaceFirst("^@", ""));
        TextView status = EconomyUi.text(a, "", 12, R.color.text2, false);
        status.setPadding(0, EconomyUi.dp(a, 8), 0, 0);
        LinearLayout form = NumbersUi.form(a, "Латиница, цифры и _, 3–32 символа, начинается с буквы.", in);
        form.addView(status);
        Handler h = new Handler(Looper.getMainLooper());
        final Runnable[] pending = new Runnable[1];
        final boolean[] available = {false};
        in.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void onTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void afterTextChanged(Editable s) {
                available[0] = false;
                if (pending[0] != null) h.removeCallbacks(pending[0]);
                final String name = s.toString().trim();
                if (name.isEmpty()) { status.setText(""); return; }
                pending[0] = () -> api.economyGet("/users/username-available?username=" + name, (ok, json, err) -> {
                    if (!ok || json == null) { status.setText(err != null ? err : ""); return; }
                    available[0] = json.optBoolean("available");
                    status.setText(available[0] ? "✓ Username свободен" : json.optString("error", "Недоступен"));
                    status.setTextColor(EconomyUi.color(a, available[0] ? R.color.ok : R.color.err));
                });
                h.postDelayed(pending[0], 400);
            }
        });
        new TDialog.Builder(a).setTitle("Имя пользователя").setView(form)
                .setPositiveButton("Сохранить", (d, w) -> {
                    JSONObject b = new JSONObject();
                    try { b.put("username", in.getText().toString().trim()); } catch (Exception ignored) {}
                    api.economyPost("/me/username", b, (ok, json, err) -> {
                        if (ok) {
                            EconomyUi.toast(a, "Username изменён");
                            if (onChanged != null) onChanged.run();
                        } else EconomyUi.toast(a, err != null ? err : "Не удалось изменить");
                    });
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
