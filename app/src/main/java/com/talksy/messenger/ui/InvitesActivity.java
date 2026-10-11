package com.talksy.messenger.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.IconManager;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.sync.SyncManager;
import com.talksy.messenger.ui.widget.SettingsUi;

import java.util.List;

public class InvitesActivity extends BaseActivity {
    private ApiClient api;
    private LinearLayout list;
    private TextView empty;

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
        t.setText(R.string.priv_invites); t.setTextSize(18); t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(ContextCompat.getColor(this, R.color.text1));
        bar.addView(t);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 56)));

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(SettingsUi.dp(this, 16), SettingsUi.dp(this, 8), SettingsUi.dp(this, 16), SettingsUi.dp(this, 24));
        sv.addView(list);
        empty = new TextView(this);
        empty.setText(R.string.invites_empty); empty.setGravity(Gravity.CENTER); empty.setTextColor(ContextCompat.getColor(this, R.color.text3));
        empty.setPadding(0, SettingsUi.dp(this, 60), 0, 0);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); reload(); }

    private void reload() {
        api.getGroupInvites((ok, invites, err) -> {
            list.removeAllViews();
            if (!ok || invites == null || invites.isEmpty()) { list.addView(empty); return; }
            for (ApiClient.GroupInvite gi : invites) list.addView(card(gi));
        });
    }

    private View card(ApiClient.GroupInvite gi) {
        LinearLayout c = SettingsUi.card(this);
        c.setPadding(SettingsUi.dp(this, 16), SettingsUi.dp(this, 14), SettingsUi.dp(this, 16), SettingsUi.dp(this, 14));
        TextView name = new TextView(this);
        name.setText(gi.groupName); name.setTextSize(16); name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        name.setTextColor(ContextCompat.getColor(this, R.color.text1));
        c.addView(name);
        TextView sub = new TextView(this);
        sub.setText(getString(gi.isChannel ? R.string.invite_channel_from : R.string.invite_group_from, gi.inviterName, gi.members));
        sub.setTextColor(ContextCompat.getColor(this, R.color.text2)); sub.setTextSize(13);
        c.addView(sub);
        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, SettingsUi.dp(this, 12), 0, 0);
        Button ok = new Button(this, null, 0, R.style.TalksyButton);
        ok.setText(R.string.invite_accept);
        ok.setOnClickListener(v -> api.acceptGroupInvite(gi.id, (s, x, e) -> {
            if (s) { Toast.makeText(this, R.string.invite_accepted, Toast.LENGTH_SHORT).show(); SyncManager.get(this).requestSync("invite-accepted"); reload(); }
            else Toast.makeText(this, e != null ? e : getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
        }));
        Button no = new Button(this);
        no.setText(R.string.invite_decline); no.setAllCaps(false);
        no.setOnClickListener(v -> api.declineGroupInvite(gi.id, (s, x, e) -> reload()));
        btns.addView(ok, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        np.setMarginStart(SettingsUi.dp(this, 10));
        btns.addView(no, np);
        c.addView(btns);
        return c;
    }
}
