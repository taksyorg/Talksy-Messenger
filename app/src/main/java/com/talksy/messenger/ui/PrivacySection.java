package com.talksy.messenger.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;

import com.talksy.messenger.R;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.ui.widget.SettingsUi;

import java.util.HashMap;
import java.util.Map;

public final class PrivacySection {
    private PrivacySection() {}

    private static final String[] KEYS = {"phone", "username", "last_seen", "photo", "bio", "messages", "group_invites", "calls", "voice", "archive"};
    private static final int[] TITLES = {R.string.priv_phone, R.string.priv_username, R.string.priv_last_seen, R.string.priv_photo,
            R.string.priv_bio, R.string.priv_messages, R.string.priv_group_invites, R.string.priv_calls, R.string.priv_voice, R.string.priv_archive};
    private static final String[] ICONS = {"call_confirm", "mention", "eye", "photo", "about", "secure_chat", "group", "call_incoming", "voice_message", "archive"};

    public static void attach(Activity act, LinearLayout root, int index) {
        ApiClient api = new ApiClient(act);
        final Map<String, SettingsUi.Row> rows = new HashMap<>();
        final Map<String, String> values = new HashMap<>();
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);

        box.addView(SettingsUi.header(act, act.getString(R.string.settings_privacy)));
        LinearLayout card = SettingsUi.card(act);
        for (int i = 0; i < KEYS.length; i++) {
            final String key = KEYS[i];
            SettingsUi.Row r = SettingsUi.row(act, ICONS[i], act.getString(TITLES[i]), null, label(act, "all"), v -> choose(act, api, key, values, rows));
            rows.put(key, r);
            card.addView(r.view);
            if (i < KEYS.length - 1) SettingsUi.divider(card);
        }
        box.addView(card);
        box.addView(SettingsUi.note(act, act.getString(R.string.priv_group_invites_note)));

        final SettingsUi.Row inv = SettingsUi.row(act, "add_contact", act.getString(R.string.priv_invites), act.getString(R.string.priv_invites_sub), "",
                v -> act.startActivity(new Intent(act, InvitesActivity.class)));
        LinearLayout invCard = SettingsUi.card(act);
        invCard.addView(inv.view);
        box.addView(invCard);
        root.addView(box, index);

        api.getPrivacy((ok, st, err) -> {
            if (!ok || st == null) return;
            for (String k : KEYS) { values.put(k, st.rule(k)); rows.get(k).value.setText(label(act, st.rule(k))); }
            inv.value.setText(st.invitesPending > 0 ? String.valueOf(st.invitesPending) : "");
        });
    }

    static String label(Activity a, String v) {
        return a.getString("nobody".equals(v) ? R.string.priv_nobody : "contacts".equals(v) ? R.string.priv_contacts : R.string.priv_everybody);
    }

    private static void choose(Activity act, ApiClient api, String key, Map<String, String> values, Map<String, SettingsUi.Row> rows) {
        final String[] opts = {"all", "contacts", "nobody"};
        CharSequence[] labels = {act.getString(R.string.priv_everybody), act.getString(R.string.priv_contacts), act.getString(R.string.priv_nobody)};
        String cur = values.get(key) == null ? "all" : values.get(key);
        int checked = 0; for (int i = 0; i < opts.length; i++) if (opts[i].equals(cur)) checked = i;
        int titleRes = TITLES[java.util.Arrays.asList(KEYS).indexOf(key)];
        new TDialog.Builder(act).setTitle(titleRes).setSingleChoiceItems(labels, checked, (d, which) -> {
            d.dismiss();
            final String old = cur, nv = opts[which];
            if (nv.equals(old)) return;
            values.put(key, nv);
            rows.get(key).value.setText(label(act, nv));
            api.setPrivacyRule(key, nv, (ok, v, err) -> {
                if (!ok) {
                    values.put(key, old); rows.get(key).value.setText(label(act, old));
                    android.widget.Toast.makeText(act, err != null ? err : act.getString(R.string.error_generic), android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
