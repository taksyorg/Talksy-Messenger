package com.talksy.messenger;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.talksy.messenger.auth.PhoneActivity;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.ui.ChatsActivity;

import java.util.Locale;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs prefs = new Prefs(this);
        boolean loggedIn = prefs.getBool(Prefs.KEY_LOGGED_IN, false);

        Intent i = new Intent(this, loggedIn ? ChatsActivity.class : PhoneActivity.class);
        startActivity(i);
        finish();
    }

    public static class LocaleHelper {
        public static Context wrap(Context context) {
            Prefs prefs = new Prefs(context);
            String lang = prefs.get(Prefs.KEY_LANGUAGE, "en");
            Locale locale = new Locale(lang);
            Locale.setDefault(locale);
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(locale);
            return context.createConfigurationContext(config);
        }
    }
}
