package com.talksy.messenger.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.talksy.messenger.R;
import com.talksy.messenger.data.Prefs;

import java.util.Locale;

public final class LinkRouter {
    private LinkRouter() {}

    public static final String PREF_IN_APP = "open_links_in_app";

    public static boolean inAppEnabled(Context c) { return new Prefs(c).getBool(PREF_IN_APP, true); }
    public static void setInAppEnabled(Context c, boolean on) { new Prefs(c).putBool(PREF_IN_APP, on); }

    public static String normalize(String raw) {
        if (raw == null) return null;
        String u = raw.trim();
        if (u.isEmpty()) return null;
        String low = u.toLowerCase(Locale.ROOT);
        if (low.startsWith("http://") || low.startsWith("https://")) return u;
        if (low.contains("://") || low.startsWith("tel:") || low.startsWith("mailto:") || low.startsWith("sms:") || low.startsWith("geo:")) return u;
        return "https://" + u;
    }

    public static void open(Context c, String rawUrl) {
        String url = normalize(rawUrl);
        if (url == null) return;
        String low = url.toLowerCase(Locale.ROOT);
        if (low.startsWith("http://") || low.startsWith("https://")) {
            if (inAppEnabled(c)) {
                Intent i = new Intent(c, InAppBrowserActivity.class);
                i.putExtra(InAppBrowserActivity.EXTRA_URL, url);
                if (!(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } else openExternal(c, url);
            return;
        }

        if (low.startsWith("tel:") || low.startsWith("mailto:") || low.startsWith("sms:") || low.startsWith("geo:")) { openExternal(c, url); return; }
        Toast.makeText(c, R.string.link_blocked, Toast.LENGTH_SHORT).show();
    }

    public static void openExternal(Context c, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (!(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) { Toast.makeText(c, R.string.link_blocked, Toast.LENGTH_SHORT).show(); }
    }

    public static void copy(Context c, String url) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) { cm.setPrimaryClip(ClipData.newPlainText("url", url)); Toast.makeText(c, R.string.link_copied, Toast.LENGTH_SHORT).show(); }
    }

    public static void share(Context c, String url) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, url);
        Intent ch = Intent.createChooser(i, null);
        if (!(c instanceof android.app.Activity)) ch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(ch);
    }
}
