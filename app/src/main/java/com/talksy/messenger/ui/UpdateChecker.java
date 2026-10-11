package com.talksy.messenger.ui;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.pm.PackageInfoCompat;

import com.talksy.messenger.R;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.net.ApiClient;

import org.json.JSONObject;

import java.io.File;

public final class UpdateChecker {
    private UpdateChecker() {}

    private static final String KEY_DISMISSED = "update_dismissed_code";
    private static final String KEY_LAST_CHECK = "update_last_check";
    private static final long CHECK_EVERY_MS = 6L * 60 * 60 * 1000;
    private static final String APK_PATH = "updates/talksy-update.apk";
    private static boolean dialogShown;

    public static long currentCode(Context c) {
        try {
            return PackageInfoCompat.getLongVersionCode(c.getPackageManager().getPackageInfo(c.getPackageName(), 0));
        } catch (Exception e) { return 0; }
    }

    public static String currentName(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; }
        catch (Exception e) { return ""; }
    }

    public static void checkOnStart(Activity a) {
        Prefs p = new Prefs(a);
        long now = System.currentTimeMillis();
        if (now - p.getLong(KEY_LAST_CHECK, 0) < CHECK_EVERY_MS && p.getLong(KEY_DISMISSED, 0) <= 0) return;
        check(a, false);
    }

    public static void check(Activity a, boolean userAsked) {
        ApiClient api = new ApiClient(a);
        api.supportGet("/app/version?code=" + currentCode(a), (ok, json, err) -> {
            if (a.isFinishing()) return;
            if (!ok || json == null) {
                if (userAsked) Toast.makeText(a, err != null ? err : a.getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
                return;
            }
            new Prefs(a).putLong(KEY_LAST_CHECK, System.currentTimeMillis());
            if (!json.optBoolean("update_available")) {
                if (userAsked) Toast.makeText(a, R.string.update_latest, Toast.LENGTH_SHORT).show();
                return;
            }
            show(a, api, json, userAsked);
        });
    }

    private static void show(Activity a, ApiClient api, JSONObject json, boolean userAsked) {
        if (dialogShown) return;
        long latest = json.optLong("latest_code");
        boolean force = json.optBoolean("force");
        Prefs p = new Prefs(a);
        if (!force && !userAsked && p.getLong(KEY_DISMISSED, 0) == latest) return;
        String url = json.optString("download_url", "");
        if (url.isEmpty()) return;
        String name = json.optString("latest_name", "");
        String notes = json.optString("notes", "");
        String msg = a.getString(force ? R.string.update_message_force : R.string.update_message, name);
        if (!notes.isEmpty()) msg += "\n\n" + notes;
        dialogShown = true;
        TDialog.Builder b = new TDialog.Builder(a)
                .setTitle(R.string.update_title)
                .setMessage(msg)
                .setCancelable(!force)
                .setOnDismissListener(d -> dialogShown = false)
                .setPositiveButton(R.string.update_now, (d, w) -> download(a, api, url));
        if (!force) b.setNegativeButton(R.string.update_later, (d, w) -> p.putLong(KEY_DISMISSED, latest));
        b.show();
    }

    private static void download(Activity a, ApiClient api, String url) {
        Context app = a.getApplicationContext();
        String full = url.startsWith("/") ? api.authedUrl(url) : url;
        try {
            File dir = app.getExternalFilesDir(null);
            if (dir == null) throw new IllegalStateException();
            File apk = new File(dir, APK_PATH);
            if (apk.exists()) apk.delete();
            DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(full))
                    .setTitle("Talksy")
                    .setDescription(app.getString(R.string.update_downloading))
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(app, null, APK_PATH);
            final long id = dm.enqueue(r);
            Toast.makeText(app, R.string.update_downloading, Toast.LENGTH_SHORT).show();
            BroadcastReceiver rcv = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    if (id != i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)) return;
                    try { c.unregisterReceiver(this); } catch (Exception ignored) {}
                    boolean okDl = false;
                    try (Cursor cur = dm.query(new DownloadManager.Query().setFilterById(id))) {
                        if (cur != null && cur.moveToFirst()) {
                            okDl = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL;
                        }
                    }
                    if (okDl) install(c, apk);
                    else Toast.makeText(c, R.string.update_failed, Toast.LENGTH_LONG).show();
                }
            };
            ContextCompat.registerReceiver(app, rcv, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED);
        } catch (Exception e) {
            Toast.makeText(app, R.string.update_failed, Toast.LENGTH_LONG).show();
        }
    }

    private static void install(Context c, File apk) {
        if (!c.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(c, R.string.update_allow_install, Toast.LENGTH_LONG).show();
            c.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", apk);
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }
}
