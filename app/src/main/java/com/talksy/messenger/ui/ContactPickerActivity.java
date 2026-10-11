package com.talksy.messenger.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.ui.widget.SettingsUi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ContactPickerActivity extends BaseActivity {
    private static final class C { String name, phone; }

    private final List<C> all = new ArrayList<>(), shown = new ArrayList<>();
    private RecyclerView rv;
    private TextView msg;
    private Button grant;

    private final ActivityResultLauncher<String> perm = registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> {
        if (ok) load(); else showDenied();
    });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(SettingsUi.dp(this, 8), 0, SettingsUi.dp(this, 8), 0);
        ImageButton back = new ImageButton(this);
        back.setBackgroundResource(R.drawable.ripple_item_round); back.setImageResource(R.drawable.ic_back);
        back.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        bar.addView(back, new LinearLayout.LayoutParams(SettingsUi.dp(this, 44), SettingsUi.dp(this, 44)));
        TextView t = new TextView(this); t.setText(R.string.attach_contact); t.setTextSize(18); t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(ContextCompat.getColor(this, R.color.text1)); bar.addView(t);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 56)));

        EditText search = new EditText(this);
        search.setHint(R.string.contact_search); search.setSingleLine(true);
        search.setTextColor(ContextCompat.getColor(this, R.color.text1)); search.setHintTextColor(ContextCompat.getColor(this, R.color.text3));
        GradientDrawable sg = new GradientDrawable(); sg.setCornerRadius(SettingsUi.dp(this, 22)); sg.setColor(ContextCompat.getColor(this, R.color.bg2));
        search.setBackground(sg); search.setPadding(SettingsUi.dp(this, 16), SettingsUi.dp(this, 10), SettingsUi.dp(this, 16), SettingsUi.dp(this, 10));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(SettingsUi.dp(this, 14), SettingsUi.dp(this, 4), SettingsUi.dp(this, 14), SettingsUi.dp(this, 8));
        root.addView(search, sp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        msg = new TextView(this); msg.setGravity(Gravity.CENTER); msg.setTextColor(ContextCompat.getColor(this, R.color.text3));
        msg.setPadding(SettingsUi.dp(this, 24), SettingsUi.dp(this, 40), SettingsUi.dp(this, 24), 0); msg.setVisibility(View.GONE);
        root.addView(msg);
        grant = new Button(this, null, 0, R.style.TalksyButton); grant.setText(R.string.contact_allow); grant.setVisibility(View.GONE);
        grant.setOnClickListener(v -> perm.launch(Manifest.permission.READ_CONTACTS));
        root.addView(grant, new LinearLayout.LayoutParams(SettingsUi.dp(this, 220), SettingsUi.dp(this, 48)));
        ((LinearLayout.LayoutParams) grant.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;

        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p, int vt) {
                LinearLayout row = new LinearLayout(ContactPickerActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(SettingsUi.dp(ContactPickerActivity.this, 16), SettingsUi.dp(ContactPickerActivity.this, 8), SettingsUi.dp(ContactPickerActivity.this, 16), SettingsUi.dp(ContactPickerActivity.this, 8));
                row.setBackgroundResource(R.drawable.ripple_item);
                row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerView.ViewHolder(row) {};
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder vh, int pos) {
                C c = shown.get(pos); LinearLayout row = (LinearLayout) vh.itemView; row.removeAllViews();
                TextView av = new TextView(ContactPickerActivity.this);
                av.setText(c.name.isEmpty() ? "#" : c.name.substring(0, 1).toUpperCase()); av.setGravity(Gravity.CENTER); av.setTextColor(Color.WHITE); av.setTextSize(17);
                GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(0xFF000000 | (Math.abs(c.name.hashCode()) % 0xFFFFFF | 0x404040)); av.setBackground(g);
                row.addView(av, new LinearLayout.LayoutParams(SettingsUi.dp(ContactPickerActivity.this, 44), SettingsUi.dp(ContactPickerActivity.this, 44)));
                LinearLayout col = new LinearLayout(ContactPickerActivity.this); col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); cp.setMarginStart(SettingsUi.dp(ContactPickerActivity.this, 14));
                row.addView(col, cp);
                TextView n = new TextView(ContactPickerActivity.this); n.setText(c.name); n.setTextSize(15); n.setTextColor(ContextCompat.getColor(ContactPickerActivity.this, R.color.text1)); col.addView(n);
                TextView p = new TextView(ContactPickerActivity.this); p.setText(c.phone); p.setTextSize(13); p.setTextColor(ContextCompat.getColor(ContactPickerActivity.this, R.color.text3)); col.addView(p);
                row.setOnClickListener(v -> { setResult(RESULT_OK, new Intent().putExtra("name", c.name).putExtra("phone", c.phone)); finish(); });
            }
            @Override public int getItemCount() { return shown.size(); }
        });
        root.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) load();
        else perm.launch(Manifest.permission.READ_CONTACTS);
    }

    private void showDenied() {
        msg.setText(R.string.contact_denied); msg.setVisibility(View.VISIBLE); grant.setVisibility(View.VISIBLE);
    }

    private void load() {
        msg.setVisibility(View.GONE); grant.setVisibility(View.GONE);
        new Thread(() -> {
            List<C> list = new ArrayList<>(); Set<String> seen = new HashSet<>();
            try (Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")) {
                while (c != null && c.moveToNext()) {
                    String num = c.getString(1) == null ? "" : c.getString(1).trim(), key = num.replaceAll("[^0-9+]", "");
                    if (num.isEmpty() || !seen.add(key)) continue;
                    C x = new C(); x.name = c.getString(0) == null ? num : c.getString(0); x.phone = num; list.add(x);
                }
            } catch (Exception ignored) {}
            runOnUiThread(() -> {
                all.clear(); all.addAll(list); filter("");
                if (all.isEmpty()) { msg.setText(R.string.contact_none); msg.setVisibility(View.VISIBLE); }
            });
        }).start();
    }

    private void filter(String q) {
        String s = q.trim().toLowerCase(Locale.getDefault()), digits = s.replaceAll("[^0-9+]", "");
        shown.clear();
        for (C c : all) if (s.isEmpty() || c.name.toLowerCase(Locale.getDefault()).contains(s) || (!digits.isEmpty() && c.phone.replaceAll("[^0-9+]", "").contains(digits))) shown.add(c);
        rv.getAdapter().notifyDataSetChanged();
    }
}
