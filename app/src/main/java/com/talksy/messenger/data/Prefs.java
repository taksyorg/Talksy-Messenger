package com.talksy.messenger.data;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private static final String FILE = "talksy_prefs";

    public static final String KEY_LOGGED_IN = "logged_in";
    public static final String KEY_USER_ID = "user_id";
    public static final String KEY_PHONE = "pending_phone";
    public static final String KEY_EMAIL = "pending_email";
    public static final String KEY_LAST_CODE = "pending_code";
    public static final String KEY_DARK_THEME = "dark_theme";
    public static final String KEY_LANGUAGE = "app_language";

    public static final String KEY_E2E_ENABLED = "e2e_enabled";
    public static final String KEY_ANIMATIONS_ENABLED = "animations_enabled";
    public static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";

    public static final String KEY_LIQUID_GLASS_ENABLED = "liquid_glass_enabled";

    public static final String KEY_SAVED_ACCOUNTS = "saved_accounts_json";

    public static final String KEY_CHAT_WALLPAPER = "chat_wallpaper";
    public static final String KEY_FONT_SIZE = "chat_font_size";
    public static final String KEY_AUTO_DOWNLOAD = "auto_download_media";

    public static final String KEY_PROXY_MODE = "proxy_mode";
    public static final String KEY_PROXY_HOST = "proxy_host";
    public static final String KEY_PROXY_PORT = "proxy_port";
    public static final String KEY_PROXY_USER = "proxy_user";
    public static final String KEY_PROXY_PASS = "proxy_pass";
    public static final String KEY_PROXY_SECRET = "proxy_secret";

    public static final String KEY_API_BASE_URL = "api_base_url";
    public static final String KEY_AUTH_TOKEN = "auth_token";

    public static final String KEY_MY_NAME = "my_name";
    public static final String KEY_MY_HANDLE = "my_handle";
    public static final String KEY_MY_BIO = "my_bio";
    public static final String KEY_MY_COLOR = "my_color";
    public static final String KEY_MY_AVATAR_URL = "my_avatar_url";

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String get(String key, String def) { return sp.getString(key, def); }
    public void put(String key, String value) { sp.edit().putString(key, value).apply(); }

    public boolean getBool(String key, boolean def) { return sp.getBoolean(key, def); }
    public void putBool(String key, boolean value) { sp.edit().putBoolean(key, value).apply(); }

    public long getLong(String key, long def) { return sp.getLong(key, def); }
    public void putLong(String key, long value) { sp.edit().putLong(key, value).apply(); }

    public void clearSession() {
        sp.edit()
            .remove(KEY_LOGGED_IN)
            .remove(KEY_USER_ID)
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_MY_NAME)
            .remove(KEY_MY_HANDLE)
            .remove(KEY_MY_BIO)
            .remove(KEY_MY_COLOR)
            .remove(KEY_MY_AVATAR_URL)
            .remove(KEY_EMAIL)
            .remove(KEY_PHONE)
            .apply();
    }
}
