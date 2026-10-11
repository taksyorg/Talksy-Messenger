package com.talksy.messenger.ui.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.R;
import com.talksy.messenger.data.ArticleHtml;
import com.talksy.messenger.data.IconManager;
import com.talksy.messenger.data.MapKey;
import com.talksy.messenger.data.SpecialMessage;
import com.talksy.messenger.net.AvatarLoader;
import com.talksy.messenger.ui.ArticleViewActivity;
import com.talksy.messenger.ui.LocationViewActivity;
import com.talksy.messenger.ui.TDialog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SpecialCardView extends LinearLayout {
    public SpecialCardView(Context c) { this(c, null); }
    public SpecialCardView(Context c, AttributeSet a) {
        super(c, a);
        setOrientation(VERTICAL);
        int p = SettingsUi.dp(c, 6);
        setPadding(p, p, p, p);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView button(String label, boolean outgoing) {
        TextView b = text(label, 13, outgoing ? Color.WHITE : ContextCompat.getColor(getContext(), R.color.accent), true);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(SettingsUi.dp(getContext(), 12));
        g.setColor(outgoing ? 0x33FFFFFF : 0x1F6366F1);
        b.setBackground(g);
        b.setPadding(0, SettingsUi.dp(getContext(), 10), 0, SettingsUi.dp(getContext(), 10));
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = SettingsUi.dp(getContext(), 8);
        b.setLayoutParams(lp);
        return b;
    }

    public void bind(SpecialMessage sm, boolean outgoing) {
        removeAllViews();
        Context c = getContext();
        int fg = outgoing ? Color.WHITE : ContextCompat.getColor(c, R.color.text1);
        int dim = outgoing ? 0xCCFFFFFF : ContextCompat.getColor(c, R.color.text2);
        switch (sm.type) {
            case SpecialMessage.LOC: bindLocation(sm, fg, dim, outgoing); break;
            case SpecialMessage.CONTACT: bindContact(sm, fg, dim, outgoing); break;
            case SpecialMessage.ARTICLE: bindArticle(sm, fg, dim, outgoing); break;
            default: bindPay(sm, fg, dim, outgoing); break;
        }
    }

    private void bindLocation(SpecialMessage sm, int fg, int dim, boolean outgoing) {
        Context c = getContext();
        final double lat = sm.data.optDouble("lat"), lon = sm.data.optDouble("lon");
        final boolean live = sm.data.optBoolean("live");
        final long until = sm.data.optLong("until");
        int w = 236, h = 150;
        ImageView map = new ImageView(c);
        map.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable ph = new GradientDrawable(); ph.setCornerRadius(SettingsUi.dp(c, 14)); ph.setColor(0xFF1F2937);
        map.setBackground(ph);
        map.setClipToOutline(true);
        map.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), SettingsUi.dp(c, 14)); }
        });
        addView(map, new LayoutParams(SettingsUi.dp(c, w), SettingsUi.dp(c, h)));
        String url = MapKey.staticMapUrl(c, lat, lon, w, h);
        if (!url.isEmpty()) AvatarLoader.loadRaw(url, 600, com.talksy.messenger.data.TrafficStats.CAT_PHOTO, bmp -> map.setImageBitmap(bmp));
        else { map.setScaleType(ImageView.ScaleType.CENTER); IconManager.set(map, "location", 0xFFEF4444); }
        String title = sm.data.optString("title");
        boolean active = live && until > System.currentTimeMillis();
        String head = active ? c.getString(R.string.loc_live_until, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(until)))
                : live ? c.getString(R.string.loc_live_ended) : (title.isEmpty() ? c.getString(R.string.attach_location) : title);
        TextView t = text(head, 14, fg, true);
        LayoutParams tp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = SettingsUi.dp(c, 6); tp.setMarginStart(SettingsUi.dp(c, 4));
        addView(t, tp);
        TextView sub = text(String.format(Locale.US, "%.5f, %.5f", lat, lon), 11, dim, false);
        sub.setPadding(SettingsUi.dp(c, 4), 0, 0, 0);
        addView(sub);
        setOnClickListener(v -> {
            Intent i = new Intent(c, LocationViewActivity.class);
            i.putExtra("lat", lat); i.putExtra("lon", lon); i.putExtra("title", title);
            i.putExtra("live_id", live ? sm.data.optString("id") : ""); i.putExtra("until", until);
            c.startActivity(i);
        });
    }

    private void bindContact(SpecialMessage sm, int fg, int dim, boolean outgoing) {
        Context c = getContext();
        final String name = sm.data.optString("name"), phone = sm.data.optString("phone");
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView av = text(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), 18, Color.WHITE, true);
        av.setGravity(Gravity.CENTER);
        GradientDrawable ag = new GradientDrawable(); ag.setShape(GradientDrawable.OVAL);
        ag.setColor(0xFF000000 | (Math.abs(name.hashCode()) % 0xFFFFFF | 0x404040)); av.setBackground(ag);
        row.addView(av, new LayoutParams(SettingsUi.dp(c, 46), SettingsUi.dp(c, 46)));
        LinearLayout col = new LinearLayout(c); col.setOrientation(VERTICAL);
        LayoutParams cp = new LayoutParams(SettingsUi.dp(c, 170), ViewGroup.LayoutParams.WRAP_CONTENT); cp.setMarginStart(SettingsUi.dp(c, 12));
        col.addView(text(name, 15, fg, true)); col.addView(text(phone, 13, dim, false));
        row.addView(col, cp);
        addView(row, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView b = button(c.getString(R.string.contact_view).toUpperCase(), outgoing);
        addView(b);
        OnClickListener open = v -> showContact(name, phone);
        b.setOnClickListener(open); setOnClickListener(open);
    }

    private void showContact(String name, String phone) {
        Context c = getContext();
        CharSequence[] items = {c.getString(R.string.contact_call), c.getString(R.string.contact_add), c.getString(R.string.copy)};
        new TDialog.Builder(c).setTitle(name + "\n" + phone).setItems(items, (d, which) -> {
            if (which == 0) c.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else if (which == 1) {
                Intent i = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
                i.putExtra(ContactsContract.Intents.Insert.NAME, name); i.putExtra(ContactsContract.Intents.Insert.PHONE, phone);
                c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else com.talksy.messenger.ui.LinkRouter.copy(c, phone);
        }).show();
    }

    private void bindArticle(SpecialMessage sm, int fg, int dim, boolean outgoing) {
        Context c = getContext();
        String title = sm.data.optString("title");
        LinearLayout box = new LinearLayout(c); box.setOrientation(VERTICAL);
        LinearLayout head = new LinearLayout(c); head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(c); IconManager.set(ic, "edit", dim);
        head.addView(ic, new LayoutParams(SettingsUi.dp(c, 18), SettingsUi.dp(c, 18)));
        TextView lbl = text(c.getString(R.string.article_title).toUpperCase(), 11, dim, true);
        lbl.setPadding(SettingsUi.dp(c, 6), 0, 0, 0); head.addView(lbl);
        box.addView(head);
        if (!title.isEmpty()) { TextView tt = text(title, 17, fg, true); tt.setMaxLines(2); tt.setEllipsize(android.text.TextUtils.TruncateAt.END); tt.setPadding(0, SettingsUi.dp(c, 4), 0, 0); box.addView(tt); }
        String sn = ArticleHtml.snippet(sm.data, 140);
        if (!sn.isEmpty()) { TextView st = text(sn, 13, dim, false); st.setMaxLines(3); st.setEllipsize(android.text.TextUtils.TruncateAt.END); box.addView(st); }
        addView(box, new LayoutParams(SettingsUi.dp(c, 230), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView b = button(c.getString(R.string.article_read), outgoing);
        addView(b);
        OnClickListener open = v -> c.startActivity(new Intent(c, ArticleViewActivity.class).putExtra(ArticleViewActivity.EXTRA_JSON, sm.data.toString()));
        b.setOnClickListener(open); setOnClickListener(open);
    }

    private void bindPay(SpecialMessage sm, int fg, int dim, boolean outgoing) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c); row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(c); IconManager.set(ic, "tsc");
        row.addView(ic, new LayoutParams(SettingsUi.dp(c, 40), SettingsUi.dp(c, 40)));
        LinearLayout col = new LinearLayout(c); col.setOrientation(VERTICAL);
        LayoutParams cp = new LayoutParams(SettingsUi.dp(c, 180), ViewGroup.LayoutParams.WRAP_CONTENT); cp.setMarginStart(SettingsUi.dp(c, 12));
        String who = outgoing ? c.getString(R.string.pay_you_sent, sm.data.optString("to")) : c.getString(R.string.pay_sent_you, sm.data.optString("from"));
        col.addView(text(who, 12, dim, false));
        col.addView(text(String.format(Locale.getDefault(), "%,d %s", sm.data.optLong("amount"), com.talksy.messenger.ui.EconomyUi.code), 22, fg, true));
        String comment = sm.data.optString("comment");
        if (!comment.isEmpty()) { TextView ct = text(comment, 13, fg, false); ct.setMaxLines(3); col.addView(ct); }
        row.addView(col, cp);
        addView(row);
    }
}
