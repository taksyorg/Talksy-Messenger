package com.talksy.messenger.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.IconManager;
import com.talksy.messenger.data.model.DeviceSession;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.ui.widget.SettingsUi;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DevicesActivity extends BaseActivity {
    private ApiClient api;
    private LinearLayout body;
    private ApiClient.SessionList data;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        api = new ApiClient(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(SettingsUi.dp(this, 8), 0, SettingsUi.dp(this, 8), 0);
        ImageButton back = new ImageButton(this);
        back.setBackgroundResource(R.drawable.ripple_item_round);
        back.setImageResource(R.drawable.ic_back);
        back.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        bar.addView(back, new LinearLayout.LayoutParams(SettingsUi.dp(this, 44), SettingsUi.dp(this, 44)));
        TextView t = new TextView(this);
        t.setText(R.string.devices_title); t.setTextSize(18); t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(ContextCompat.getColor(this, R.color.text1));
        bar.addView(t);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 56)));
        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(SettingsUi.dp(this, 16), SettingsUi.dp(this, 4), SettingsUi.dp(this, 16), SettingsUi.dp(this, 24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); load(); }

    private void load() {
        api.getSessionList((ok, list, err) -> {
            if (!ok || list == null) {
                if (data == null) Toast.makeText(this, err != null ? err : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                return;
            }
            data = list;
            render();
        });
    }

    private void render() {
        body.removeAllViews();
        DeviceSession cur = null;
        for (DeviceSession s : data.sessions) if (s.isCurrent) cur = s;
        int others = data.sessions.size() - (cur != null ? 1 : 0);

        if (cur != null) {
            body.addView(SettingsUi.header(this, getString(R.string.devices_this)));
            LinearLayout card = SettingsUi.card(this);
            card.addView(sessionRow(cur));
            SettingsUi.divider(card);
            SettingsUi.Row end = SettingsUi.row(this, "close", getString(R.string.devices_end_others), null, null, v -> confirmEndOthers());
            end.title.setTextColor(0xFFEF4444);
            IconManager.set((ImageView) end.view.getChildAt(0), "close", 0xFFEF4444);
            end.view.setAlpha(others > 0 ? 1f : 0.45f);
            if (others == 0) end.view.setOnClickListener(null);
            card.addView(end.view);
            body.addView(card);
            body.addView(SettingsUi.note(this, getString(R.string.devices_end_others_note)));
        }

        body.addView(SettingsUi.header(this, getString(R.string.devices_active)));
        if (others > 0) {
            LinearLayout list = SettingsUi.card(this);
            boolean first = true;
            for (DeviceSession s : data.sessions) {
                if (s.isCurrent) continue;
                if (!first) SettingsUi.divider(list);
                list.addView(sessionRow(s));
                first = false;
            }
            body.addView(list);
        }
        body.addView(SettingsUi.note(this, getString(others > 0 ? R.string.devices_active_note : R.string.devices_none)));

        body.addView(SettingsUi.header(this, getString(R.string.devices_auto_title)));
        LinearLayout auto = SettingsUi.card(this);
        final SettingsUi.Row ar = SettingsUi.row(this, null, getString(R.string.devices_auto_if), null, daysLabel(data.autoDays), null);
        ar.view.setOnClickListener(v -> chooseAuto(ar));
        auto.addView(ar.view);
        body.addView(auto);
        body.addView(SettingsUi.note(this, getString(R.string.devices_auto_note)));
    }

    private View sessionRow(DeviceSession s) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(SettingsUi.dp(this, 14), SettingsUi.dp(this, 12), SettingsUi.dp(this, 14), SettingsUi.dp(this, 12));
        row.setBackgroundResource(R.drawable.ripple_item);
        row.addView(avatar(s, 42), new LinearLayout.LayoutParams(SettingsUi.dp(this, 42), SettingsUi.dp(this, 42)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.setMarginStart(SettingsUi.dp(this, 14));
        row.addView(col, cp);
        col.addView(text(s.device, 15, R.color.text1, true));
        col.addView(text(s.appText(), 12, R.color.text2, false));
        String where = s.whereText();
        col.addView(text((where.isEmpty() ? "" : where + " · ") + lastText(s), 12, s.online ? R.color.accent : R.color.text3, false));
        row.setOnClickListener(v -> showSheet(s));
        return row;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(ContextCompat.getColor(this, color));
        t.setSingleLine(true); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private View avatar(DeviceSession s, int dp) {
        FrameLayoutHelper h = new FrameLayoutHelper();
        return h.make(s, dp);
    }

    private final class FrameLayoutHelper {
        View make(DeviceSession s, int dp) {
            android.widget.FrameLayout f = new android.widget.FrameLayout(DevicesActivity.this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(platformColor(s.platform));
            f.setBackground(g);
            ImageView iv = new ImageView(DevicesActivity.this);
            IconManager.set(iv, "devices", Color.WHITE);
            int pad = SettingsUi.dp(DevicesActivity.this, dp / 4f);
            iv.setPadding(pad, pad, pad, pad);
            f.addView(iv, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return f;
        }
    }

    private int platformColor(String p) {
        if ("android".equals(p)) return 0xFF4CAF50;
        if ("desktop".equals(p)) return 0xFF22B8CF;
        if ("ios".equals(p)) return 0xFF3B82F6;
        if ("web".equals(p)) return 0xFFF59E0B;
        return ContextCompat.getColor(this, R.color.accent2);
    }

    private String lastText(DeviceSession s) {
        if (s.online) return getString(R.string.devices_online);
        Calendar now = Calendar.getInstance(), t = Calendar.getInstance();
        t.setTimeInMillis(s.lastActiveMillis);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(s.lastActiveMillis));
        int dayDiff = (int) ((startOfDay(now) - startOfDay(t)) / 86_400_000L);
        if (dayDiff <= 0) return getString(R.string.devices_today_at, time);
        if (dayDiff == 1) return getString(R.string.devices_yesterday_at, time);
        if (dayDiff < 7) return new SimpleDateFormat("EE", Locale.getDefault()).format(new Date(s.lastActiveMillis));
        return DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(s.lastActiveMillis));
    }

    private static long startOfDay(Calendar c) {
        Calendar d = (Calendar) c.clone();
        d.set(Calendar.HOUR_OF_DAY, 0); d.set(Calendar.MINUTE, 0); d.set(Calendar.SECOND, 0); d.set(Calendar.MILLISECOND, 0);
        return d.getTimeInMillis();
    }

    private void showSheet(DeviceSession s) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        float r = SettingsUi.dp(this, 26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setColor(ContextCompat.getColor(this, R.color.bg2));
        sheet.setBackground(bg);
        int pad = SettingsUi.dp(this, 20);
        sheet.setPadding(pad, SettingsUi.dp(this, 22), pad, SettingsUi.dp(this, 20));

        sheet.addView(avatar(s, 76), new LinearLayout.LayoutParams(SettingsUi.dp(this, 76), SettingsUi.dp(this, 76)));
        TextView name = text(s.device, 20, R.color.text1, true);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = SettingsUi.dp(this, 10);
        sheet.addView(name, np);
        TextView when = text(lastText(s), 13, s.online ? R.color.accent : R.color.text3, false);
        when.setGravity(Gravity.CENTER);
        sheet.addView(when, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = SettingsUi.dp(this, 14);
        sheet.addView(info, ip);
        info.addView(infoRow("devices", s.appText(), getString(R.string.devices_app)));
        if (!s.whereText().isEmpty()) info.addView(infoRow("location", s.whereText(), getString(R.string.devices_geo)));
        info.addView(switchRow("e2e_key", getString(R.string.devices_accept_secret), getString(R.string.devices_accept_secret_sub), s.acceptSecret, on -> {
            s.acceptSecret = on;
            api.setSessionSettings(s.id, on, null, (ok, v, e) -> { if (!ok) toastErr(e); });
        }));
        info.addView(switchRow("call_incoming", getString(R.string.devices_accept_calls), getString(R.string.devices_accept_calls_sub), s.acceptCalls, on -> {
            s.acceptCalls = on;
            api.setSessionSettings(s.id, null, on, (ok, v, e) -> { if (!ok) toastErr(e); });
        }));

        if (!s.isCurrent) {
            Button end = new Button(this, null, 0, R.style.TalksyButton);
            end.setText(R.string.devices_end_session);
            end.setOnClickListener(v -> new TDialog.Builder(this).setMessage(R.string.session_terminate_confirm)
                    .setPositiveButton(R.string.devices_end_session, (dd, w) -> api.terminateSession(s.id, false, (ok, x, e) -> {
                        if (ok) { d.dismiss(); load(); } else toastErr(e);
                    }))
                    .setNegativeButton(android.R.string.cancel, null).show());
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 50));
            bp.topMargin = SettingsUi.dp(this, 16);
            sheet.addView(end, bp);
        }

        d.setContentView(sheet);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.getAttributes().windowAnimations = android.R.style.Animation_InputMethod;
        }
        d.setOnDismissListener(x -> load());
        d.show();
    }

    private View infoRow(String icon, String title, String caption) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, SettingsUi.dp(this, 9), 0, SettingsUi.dp(this, 9));
        ImageView iv = new ImageView(this);
        IconManager.set(iv, icon);
        row.addView(iv, new LinearLayout.LayoutParams(SettingsUi.dp(this, 24), SettingsUi.dp(this, 24)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.setMarginStart(SettingsUi.dp(this, 16));
        row.addView(col, cp);
        col.addView(text(title, 15, R.color.text1, false));
        col.addView(text(caption, 12, R.color.text3, false));
        return row;
    }

    private interface OnToggle { void on(boolean checked); }

    private View switchRow(String icon, String title, String sub, boolean checked, OnToggle cb) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, SettingsUi.dp(this, 9), 0, SettingsUi.dp(this, 9));
        ImageView iv = new ImageView(this);
        IconManager.set(iv, icon);
        row.addView(iv, new LinearLayout.LayoutParams(SettingsUi.dp(this, 24), SettingsUi.dp(this, 24)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.setMarginStart(SettingsUi.dp(this, 16));
        row.addView(col, cp);
        col.addView(text(title, 15, R.color.text1, false));
        TextView st = text(sub, 12, R.color.text3, false);
        st.setSingleLine(false);
        col.addView(st);
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, on) -> cb.on(on));
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        return row;
    }

    private void toastErr(String e) { Toast.makeText(this, e != null ? e : getString(R.string.error_generic), Toast.LENGTH_SHORT).show(); load(); }

    private void confirmEndOthers() {
        new TDialog.Builder(this).setTitle(R.string.devices_end_others).setMessage(R.string.devices_end_others_confirm)
                .setPositiveButton(R.string.devices_end_session, (d, w) -> api.terminateSession(0, true, (ok, v, e) -> { if (ok) load(); else toastErr(e); }))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private String daysLabel(int days) {
        return days <= 7 ? getString(R.string.devices_1w) : days <= 30 ? getString(R.string.devices_1m) : days <= 90 ? getString(R.string.devices_3m)
                : days <= 180 ? getString(R.string.devices_6m) : getString(R.string.devices_1y);
    }

    private void chooseAuto(SettingsUi.Row row) {
        final int[] days = {7, 30, 90, 180, 365};
        CharSequence[] labels = {getString(R.string.devices_1w), getString(R.string.devices_1m), getString(R.string.devices_3m), getString(R.string.devices_6m), getString(R.string.devices_1y)};
        int checked = 3; for (int i = 0; i < days.length; i++) if (days[i] == data.autoDays) checked = i;
        new TDialog.Builder(this).setTitle(R.string.devices_auto_title).setSingleChoiceItems(labels, checked, (d, which) -> {
            d.dismiss();
            final int old = data.autoDays;
            data.autoDays = days[which]; row.value.setText(daysLabel(days[which]));
            api.setSessionAutoTerminateDays(days[which], (ok, v, e) -> { if (!ok) { data.autoDays = old; row.value.setText(daysLabel(old)); toastErr(e); } });
        }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
