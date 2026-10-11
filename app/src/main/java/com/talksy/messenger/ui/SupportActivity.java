package com.talksy.messenger.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.data.SupportConfig;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.ui.widget.SettingsUi;

import org.json.JSONArray;
import org.json.JSONObject;

public class SupportActivity extends BaseActivity {

    private ApiClient api;
    private LinearLayout content;
    private LinearLayout staffHolder;
    private LinearLayout resultsBox;
    private EditText inpQuery;

    private long supportId = SupportConfig.SUPPORT_USER_ID;
    private String supportName = "Talksy Support";
    private String supportColor = "#6366F1";
    private String supportAvatar = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = new ApiClient(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(SettingsUi.dp(this, 6), 0, 0, 0);
        bar.setBackgroundColor(ContextCompat.getColor(this, R.color.bg2));
        bar.setElevation(SettingsUi.dp(this, 2));
        ImageButton back = new ImageButton(this);
        back.setBackgroundResource(R.drawable.ripple_item_round);
        back.setImageResource(R.drawable.ic_back);
        back.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        AnimUtils.pressScale(back);
        bar.addView(back, new LinearLayout.LayoutParams(SettingsUi.dp(this, 40), SettingsUi.dp(this, 40)));
        TextView title = new TextView(this);
        title.setText(R.string.support_title);
        title.setTextColor(ContextCompat.getColor(this, R.color.text1));
        title.setTextSize(16);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart(SettingsUi.dp(this, 8));
        bar.addView(title, tlp);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 58)));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = SettingsUi.dp(this, 14);
        content.setPadding(pad, pad, pad, pad * 2);
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        buildUserSection();
        loadInfo();
    }

    private void buildUserSection() {
        content.addView(SettingsUi.header(this, getString(R.string.support_faq).toUpperCase()));
        content.addView(SettingsUi.note(this, getString(R.string.support_faq_note)));
        LinearLayout faq = SettingsUi.card(this);
        java.util.List<SupportFaq.Item> items = SupportFaq.items(this);
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) SettingsUi.divider(faq);
            faq.addView(faqItem(items.get(i)));
        }
        content.addView(faq);

        content.addView(SettingsUi.header(this, getString(R.string.support_operator).toUpperCase()));
        LinearLayout card = SettingsUi.card(this);
        addTopic(card, "help", R.string.support_write, R.string.support_write_sub, "", false);
        SettingsUi.divider(card);
        addTopic(card, "report", R.string.support_bug, R.string.support_bug_sub, getString(R.string.support_bug_draft), true);
        SettingsUi.divider(card);
        addTopic(card, "security_check", R.string.support_account, R.string.support_account_sub, getString(R.string.support_account_draft), true);
        SettingsUi.divider(card);
        addTopic(card, "edit", R.string.support_idea, R.string.support_idea_sub, getString(R.string.support_idea_draft), false);
        content.addView(card);
        content.addView(SettingsUi.note(this, getString(R.string.support_note)));

        staffHolder = new LinearLayout(this);
        staffHolder.setOrientation(LinearLayout.VERTICAL);
        content.addView(staffHolder);
    }

    private View faqItem(SupportFaq.Item item) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        SettingsUi.Row row = SettingsUi.styledRow(this, "help", item.question, null, null, null);
        row.title.setMaxLines(3);
        box.addView(row.view);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(View.GONE);
        int p = SettingsUi.dp(this, 14);
        body.setPadding(SettingsUi.dp(this, 58), 0, p, p);

        TextView answer = new TextView(this);
        answer.setText(item.answer);
        answer.setTextColor(ContextCompat.getColor(this, R.color.text2));
        answer.setTextSize(13);
        answer.setLineSpacing(0, 1.15f);
        body.addView(answer);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = SettingsUi.dp(this, 10);
        body.addView(actions, alp);
        actions.addView(pill(getString(R.string.support_helped), false, v -> {
            body.setVisibility(View.GONE);
            Toast.makeText(this, R.string.support_glad, Toast.LENGTH_SHORT).show();
        }));
        TextView contact = pill(getString(R.string.support_not_helped), true,
                v -> openSupportChat(getString(R.string.support_question_prefix) + item.question + "\n\n" + deviceInfo()));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMarginStart(SettingsUi.dp(this, 8));
        actions.addView(contact, clp);

        box.addView(body);
        row.view.setClickable(true);
        row.view.setOnClickListener(v -> body.setVisibility(body.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        return box;
    }

    private TextView pill(String text, boolean accent, View.OnClickListener click) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        int h = SettingsUi.dp(this, 14), v = SettingsUi.dp(this, 8);
        t.setPadding(h, v, h, v);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(SettingsUi.dp(this, 18));
        if (accent) {
            bg.setColor(ContextCompat.getColor(this, R.color.accent));
            t.setTextColor(Color.WHITE);
        } else {
            bg.setColor(ContextCompat.getColor(this, R.color.bg2));
            t.setTextColor(ContextCompat.getColor(this, R.color.text1));
        }
        t.setBackground(bg);
        t.setOnClickListener(click);
        return t;
    }

    private void addTopic(LinearLayout card, String icon, int titleRes, int subRes, String draft, boolean withDeviceInfo) {
        SettingsUi.Row r = SettingsUi.styledRow(this, icon, getString(titleRes), getString(subRes), null,
                v -> openSupportChat(withDeviceInfo ? draft + "\n\n" + deviceInfo() : draft));
        card.addView(r.view);
    }

    private String deviceInfo() {
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        String uid = new Prefs(this).get(Prefs.KEY_USER_ID, "?");
        return "— Talksy " + ver + " · Android " + Build.VERSION.RELEASE + " · " + Build.MANUFACTURER + " " + Build.MODEL + " · ID " + uid;
    }

    private void openSupportChat(String draft) {
        if (supportId <= 0) {
            api.supportGet("/support/info", (ok, json, err) -> {
                if (ok && json != null) applyInfo(json);
                if (supportId > 0) openSupportChat(draft);
                else Toast.makeText(this, R.string.support_unavailable, Toast.LENGTH_SHORT).show();
            });
            return;
        }
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("peer_id", supportId);
        i.putExtra("peer_name", supportName);
        i.putExtra("peer_color", supportColor);
        i.putExtra("peer_avatar_url", supportAvatar);
        if (draft != null && !draft.isEmpty()) i.putExtra("draft_text", draft);
        startActivity(i);
        AnimUtils.forward(this);
    }

    private void applyInfo(JSONObject json) {
        long sid = json.optLong("support_user_id", 0);
        if (SupportConfig.SUPPORT_USER_ID <= 0 && sid > 0) supportId = sid;
        if (!json.optString("name").isEmpty()) supportName = json.optString("name");
        if (!json.optString("color").isEmpty()) supportColor = json.optString("color");
        supportAvatar = json.optString("avatar_url", "");
    }

    private void loadInfo() {
        api.supportGet("/support/info", (ok, json, err) -> {
            if (!ok || json == null) return;
            applyInfo(json);
            if (json.optBoolean("is_staff")) buildStaffSection();
        });
    }

    private void buildStaffSection() {
        if (staffHolder.getChildCount() > 0) return;
        staffHolder.addView(SettingsUi.header(this, getString(R.string.support_staff).toUpperCase()));
        LinearLayout card = SettingsUi.card(this);
        card.setPadding(SettingsUi.dp(this, 14), SettingsUi.dp(this, 12), SettingsUi.dp(this, 14), SettingsUi.dp(this, 12));

        inpQuery = new EditText(this);
        inpQuery.setHint(R.string.support_staff_hint);
        inpQuery.setSingleLine(true);
        inpQuery.setInputType(InputType.TYPE_CLASS_TEXT);
        inpQuery.setTextColor(ContextCompat.getColor(this, R.color.text1));
        inpQuery.setHintTextColor(ContextCompat.getColor(this, R.color.text3));
        inpQuery.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        inpQuery.setOnEditorActionListener((v, a, e) -> { search(); return true; });
        card.addView(inpQuery);

        TextView btn = new TextView(this);
        btn.setText(R.string.support_staff_search);
        btn.setGravity(Gravity.CENTER);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setBackgroundColor(ContextCompat.getColor(this, R.color.accent));
        btn.setPadding(0, SettingsUi.dp(this, 10), 0, SettingsUi.dp(this, 10));
        btn.setOnClickListener(v -> search());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = SettingsUi.dp(this, 8);
        card.addView(btn, blp);

        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(resultsBox);
        staffHolder.addView(card);
    }

    private void search() {
        String q = inpQuery.getText().toString().trim();
        if (q.isEmpty()) return;
        api.supportGet("/support/staff/search?q=" + android.net.Uri.encode(q), (ok, json, err) -> {
            resultsBox.removeAllViews();
            if (!ok || json == null) { toast(err); return; }
            JSONArray arr = json.optJSONArray("users");
            if (arr == null || arr.length() == 0) {
                resultsBox.addView(SettingsUi.note(this, getString(R.string.support_staff_none)));
                return;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject u = arr.optJSONObject(i);
                if (u == null) continue;
                String sub = u.optString("handle") + " · ID " + u.optLong("id")
                        + (u.optBoolean("banned") ? " · " + getString(R.string.support_banned) : "");
                SettingsUi.Row r = SettingsUi.styledRow(this, "contact_card", u.optString("name", "—"), sub, null, v -> showUserActions(u));
                resultsBox.addView(r.view);
            }
        });
    }

    private void showUserActions(JSONObject u) {
        final long uid = u.optLong("id");
        final boolean banned = u.optBoolean("banned");
        CharSequence[] items = {
                getString(R.string.support_act_info),
                getString(R.string.support_act_write),
                getString(R.string.support_act_logout),
                banned ? getString(R.string.support_act_unban) : getString(R.string.support_act_ban)
        };
        new TDialog.Builder(this).setTitle(u.optString("name")).setItems(items, (d, which) -> {
            if (which == 0) showInfo(uid);
            else if (which == 1) writeTo(u);
            else if (which == 2) confirm(R.string.support_act_logout, () -> action("/support/staff/logout", uid, null, R.string.support_done));
            else if (banned) confirm(R.string.support_act_unban, () -> action("/support/staff/unban", uid, null, R.string.support_done));
            else askReason(uid);
        }).show();
    }

    private void showInfo(long uid) {
        api.supportGet("/support/staff/user/" + uid, (ok, json, err) -> {
            if (!ok || json == null) { toast(err); return; }
            JSONObject u = json.optJSONObject("user");
            if (u == null) return;
            StringBuilder sb = new StringBuilder();
            sb.append("ID: ").append(u.optLong("id")).append('\n');
            sb.append(getString(R.string.support_info_name)).append(": ").append(u.optString("name")).append('\n');
            sb.append("@: ").append(u.optString("handle")).append('\n');
            sb.append("Email: ").append(u.optString("email")).append('\n');
            sb.append(getString(R.string.support_info_phone)).append(": ").append(u.optString("phone")).append('\n');
            sb.append(getString(R.string.support_info_registered)).append(": ").append(date(u.optDouble("created_at"))).append('\n');
            sb.append(getString(R.string.support_info_seen)).append(": ").append(date(u.optDouble("last_seen"))).append('\n');
            sb.append(getString(R.string.support_info_sessions)).append(": ").append(u.optInt("sessions")).append('\n');
            sb.append(getString(R.string.support_info_messages)).append(": ").append(u.optInt("messages_sent")).append('\n');
            if (u.optBoolean("banned")) sb.append(getString(R.string.support_banned)).append(": ").append(u.optString("ban_reason"));
            new TDialog.Builder(this).setTitle(u.optString("name")).setMessage(sb.toString())
                    .setPositiveButton(android.R.string.ok, null).show();
        });
    }

    private static String date(double sec) {
        if (sec <= 0) return "—";
        return new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(new java.util.Date((long) (sec * 1000)));
    }

    private void writeTo(JSONObject u) {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("peer_id", u.optLong("id"));
        i.putExtra("peer_name", u.optString("name"));
        i.putExtra("peer_color", u.optString("color", "#6366F1"));
        i.putExtra("peer_avatar_url", u.optString("avatar_url", ""));
        startActivity(i);
        AnimUtils.forward(this);
    }

    private void askReason(long uid) {
        EditText e = new EditText(this);
        e.setHint(R.string.support_ban_reason);
        e.setTextColor(ContextCompat.getColor(this, R.color.text1));
        e.setHintTextColor(ContextCompat.getColor(this, R.color.text3));
        int p = SettingsUi.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(p, p / 2, p, 0);
        box.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new TDialog.Builder(this).setTitle(R.string.support_act_ban).setView(box)
                .setPositiveButton(R.string.support_act_ban, (d, w) -> action("/support/staff/ban", uid, e.getText().toString().trim(), R.string.support_done))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void confirm(int titleRes, Runnable yes) {
        new TDialog.Builder(this).setTitle(titleRes).setMessage(R.string.support_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> yes.run())
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void action(String path, long uid, String reason, int doneRes) {
        JSONObject body = new JSONObject();
        try {
            body.put("user_id", uid);
            if (reason != null) body.put("reason", reason);
        } catch (Exception ignored) {}
        api.supportPost(path, body, (ok, json, err) -> {
            if (ok) { Toast.makeText(this, doneRes, Toast.LENGTH_SHORT).show(); search(); }
            else toast(err);
        });
    }

    private void toast(String err) {
        Toast.makeText(this, err != null ? err : getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
    }
}
