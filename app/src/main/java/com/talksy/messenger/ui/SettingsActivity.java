package com.talksy.messenger.ui;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.talksy.messenger.ui.TDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.ChatWallpaper;
import com.talksy.messenger.R;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.net.ProxyManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SettingsActivity extends BaseActivity {
    private String whoCanWrite = "all";
    @Override protected boolean listensForCalls() { return true; }

    private Prefs prefs;
    private ApiClient apiClient;
    private TDialog wallpaperDialog;

    private final ActivityResultLauncher<String> pickWallpaper =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) onWallpaperPicked(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = new Prefs(this);
        apiClient = new ApiClient(this);

        ImageButton btnBack = findViewById(R.id.btn_back);
        Switch switchTheme = findViewById(R.id.switch_theme);
        Switch switchAnimations = findViewById(R.id.switch_animations);
        Switch switchNotifications = findViewById(R.id.switch_notifications);
        Switch switchE2e = findViewById(R.id.switch_e2e);
        Switch switchLiquidGlass = findViewById(R.id.switch_liquid_glass);
        LinearLayout rowWhoCanWrite = findViewById(R.id.row_who_can_write);
        LinearLayout rowAccounts = findViewById(R.id.row_accounts);
        rowAccounts.setOnClickListener(v -> com.talksy.messenger.data.AccountSwitcher.show(this));
        AnimUtils.pressScale(rowAccounts);
        LinearLayout rowLanguage = findViewById(R.id.row_language);
        TextView txtLanguageValue = findViewById(R.id.txt_language_value);
        LinearLayout rowServer = findViewById(R.id.row_server);
        rowServer.setVisibility(android.view.View.GONE);
        LinearLayout rowDevices = findViewById(R.id.row_devices);
        LinearLayout rowBots = findViewById(R.id.row_bots);
        LinearLayout rowProxy = findViewById(R.id.row_proxy);
        LinearLayout rowWallpaper = findViewById(R.id.row_wallpaper);
        LinearLayout rowFontSize = findViewById(R.id.row_font_size);
        LinearLayout rowAutoDownload = findViewById(R.id.row_auto_download);
        LinearLayout rowStorage = findViewById(R.id.row_storage);
        LinearLayout rowBlocked = findViewById(R.id.row_blocked);
        LinearLayout rowFavorites = findViewById(R.id.row_favorites);
        AnimUtils.pressScale(rowFavorites);
        rowFavorites.setOnClickListener(v -> {
            startActivity(new Intent(this, FavoritesActivity.class));
            AnimUtils.forward(this);
        });

        LinearLayout rowAppLock = findViewById(R.id.row_applock);
        AnimUtils.pressScale(rowAppLock);
        rowAppLock.setOnClickListener(v -> showAppLockDialog());
        refreshAppLockLabel();

        LinearLayout rowAccountTtl = findViewById(R.id.row_account_ttl);
        AnimUtils.pressScale(rowAccountTtl);
        rowAccountTtl.setOnClickListener(v -> showAccountTtlDialog());
        apiClient.getAccountTtl((ok, months, err) -> {
            if (ok) setAccountTtlLabel(months);
        });

        btnBack.setOnClickListener(v -> { finish(); AnimUtils.back(this); });

        boolean dark = prefs.getBool(Prefs.KEY_DARK_THEME, true);
        switchTheme.setChecked(dark);
        switchTheme.setOnCheckedChangeListener((btn, checked) -> {
            prefs.putBool(Prefs.KEY_DARK_THEME, checked);
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    checked ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                            : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            recreate();
        });

        boolean animations = prefs.getBool(Prefs.KEY_ANIMATIONS_ENABLED, true);
        switchAnimations.setChecked(animations);
        switchAnimations.setOnCheckedChangeListener((btn, checked) -> prefs.putBool(Prefs.KEY_ANIMATIONS_ENABLED, checked));

        boolean notifications = prefs.getBool(Prefs.KEY_NOTIFICATIONS_ENABLED, true);
        switchNotifications.setChecked(notifications);
        switchNotifications.setOnCheckedChangeListener((btn, checked) -> {
            prefs.putBool(Prefs.KEY_NOTIFICATIONS_ENABLED, checked);
            if (checked) {
                com.talksy.messenger.net.MessagePollService.start(this);
            } else {
                com.talksy.messenger.net.MessagePollService.stop(this);
            }
        });

        boolean e2e = prefs.getBool(Prefs.KEY_E2E_ENABLED, false);
        switchE2e.setChecked(e2e);
        switchE2e.setOnCheckedChangeListener((btn, checked) -> prefs.putBool(Prefs.KEY_E2E_ENABLED, checked));

        switchLiquidGlass.setChecked(com.talksy.messenger.data.LiquidGlass.isEnabled(this));
        switchLiquidGlass.setOnCheckedChangeListener((btn, checked) ->
                com.talksy.messenger.data.LiquidGlass.setEnabled(this, checked));

        apiClient.getProfile((ok, user, error) -> {
            if (ok && user != null) {
                whoCanWrite = user.whoCanWrite == null ? "all" : user.whoCanWrite;
                bindWhoCanWrite();
            }
        });
        rowWhoCanWrite.setOnClickListener(v -> showWhoWriteDialog());
        AnimUtils.pressScale(rowWhoCanWrite);

        String lang = prefs.get(Prefs.KEY_LANGUAGE, "en");

        String langName;
        switch (lang) {
            case "ru": langName = "Русский"; break;
            case "tr": langName = "Türkçe"; break;
            case "uk": langName = "Українська"; break;
            case "de": langName = "Deutsch"; break;
            case "ka": langName = "ქართული"; break;
            default: langName = "English"; break;
        }
        txtLanguageValue.setText(langName);

        rowLanguage.setOnClickListener(v -> {
            String[] codes = {"ru", "en", "tr", "uk", "de", "ka"};
            String[] names = {"Русский", "English", "Türkçe", "Українська", "Deutsch", "ქართული"};

            int selected = 0;
            for (int i = 0; i < codes.length; i++) {
                if (codes[i].equals(lang)) {
                    selected = i;
                    break;
                }
            }

            new android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.language_title)
                    .setSingleChoiceItems(names, selected, (dialog, which) -> {
                        prefs.put(Prefs.KEY_LANGUAGE, codes[which]);
                        dialog.dismiss();
                        recreate();
                    })
                    .show();
        });

        rowServer.setOnClickListener(v -> showServerDialog());
        rowDevices.setOnClickListener(v -> { startActivity(new Intent(this, DevicesActivity.class)); AnimUtils.forward(this); });
        rowBots.setOnClickListener(v -> { startActivity(new Intent(this, BotFatherActivity.class)); AnimUtils.forward(this); });
        rowProxy.setOnClickListener(v -> { startActivity(new Intent(this, ProxySettingsActivity.class)); AnimUtils.forward(this); });
        rowWallpaper.setOnClickListener(v -> showWallpaperDialog());
        rowFontSize.setOnClickListener(v -> showFontSizeDialog());
        rowAutoDownload.setOnClickListener(v -> showAutoDownloadDialog());
        rowStorage.setOnClickListener(v -> { startActivity(new Intent(this, StorageActivity.class)); AnimUtils.forward(this); });
        rowBlocked.setOnClickListener(v -> { startActivity(new Intent(this, BlockedUsersActivity.class)); AnimUtils.forward(this); });

        AnimUtils.runLayoutAnimation((android.view.ViewGroup) findViewById(R.id.settings_root));
        for (View row : new View[]{rowLanguage, rowDevices, rowBots, rowProxy, rowWallpaper,
                rowFontSize, rowAutoDownload, rowStorage, rowBlocked, btnBack}) {
            AnimUtils.pressScale(row);
        }

        refreshServerLabel();
        refreshProxyLabel();
        refreshWallpaperLabel();
        refreshFontSizeLabel();
        refreshAutoDownloadLabel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshServerLabel();
        refreshProxyLabel();
        refreshWallpaperLabel();
        refreshFontSizeLabel();
        refreshAutoDownloadLabel();

        setupPrivacyAndLinks();
    }

    public static final String EXTRA_PRIVACY = "privacy";

    private boolean privacyAttached = false;
    private LinearLayout ecoCard;
    private LinearLayout quickCard;

    private void setupPrivacyAndLinks() {
        boolean privacyMode = getIntent().getBooleanExtra(EXTRA_PRIVACY, false);
        LinearLayout rootCol = findViewById(R.id.settings_root);
        int[] privacyIds = {R.id.row_applock, R.id.row_account_ttl, R.id.row_blocked, R.id.row_devices};
        java.util.List<View> keep = new java.util.ArrayList<>();
        for (int id : privacyIds) { View v = findViewById(id); if (v != null) keep.add(v); }
        View e2e = findViewById(R.id.switch_e2e);
        if (e2e != null && e2e.getParent() instanceof View) keep.add((View) e2e.getParent());
        View whoWrite = findViewById(R.id.row_who_can_write);
        if (whoWrite != null) whoWrite.setVisibility(View.GONE);

        if (privacyMode) {
            hideAllExcept(rootCol, keep);
            if (!privacyAttached) {
                privacyAttached = true;
                ((TextView) ((ViewGroup) findViewById(R.id.btn_back).getParent()).getChildAt(1)).setText(R.string.settings_privacy);
                com.talksy.messenger.ui.PrivacySection.attach(this, rootCol, 0);
            }
            tidyCards(rootCol);
            spaceCards(rootCol);
        } else {
            for (View v : keep) v.setVisibility(View.GONE);
            if (quickCard == null) {
                quickCard = com.talksy.messenger.ui.widget.SettingsUi.card(this);
                com.talksy.messenger.ui.widget.SettingsUi.Row priv = com.talksy.messenger.ui.widget.SettingsUi.styledRow(this, "security_check",
                        getString(R.string.settings_privacy), getString(R.string.settings_privacy_sub), null,
                        v -> startActivity(new Intent(this, SettingsActivity.class).putExtra(EXTRA_PRIVACY, true)));
                com.talksy.messenger.ui.widget.SettingsUi.Row data = com.talksy.messenger.ui.widget.SettingsUi.styledRow(this, "data_sync",
                        getString(R.string.data_title), getString(R.string.data_title_sub), null,
                        v -> startActivity(new Intent(this, DataUsageActivity.class)));
                Switch links = new Switch(this);
                links.setChecked(LinkRouter.inAppEnabled(this));
                links.setOnCheckedChangeListener((b, on) -> LinkRouter.setInAppEnabled(this, on));
                com.talksy.messenger.ui.widget.SettingsUi.Row lr = com.talksy.messenger.ui.widget.SettingsUi.styledRow(this, "link",
                        getString(R.string.settings_links_in_app), getString(R.string.settings_links_in_app_sub), links, v -> links.toggle());
                quickCard.addView(priv.view); com.talksy.messenger.ui.widget.SettingsUi.divider(quickCard);
                quickCard.addView(data.view); com.talksy.messenger.ui.widget.SettingsUi.divider(quickCard);
                quickCard.addView(lr.view);

                int at = rootCol.getChildCount();
                View fav = findViewById(R.id.row_favorites);
                if (fav != null && fav.getParent() instanceof View) {
                    int cardIdx = rootCol.indexOfChild((View) fav.getParent());
                    if (cardIdx > 0) at = cardIdx - 1;
                }
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) quickCard.getLayoutParams();
                lp.topMargin = com.talksy.messenger.ui.widget.SettingsUi.dp(this, 18);
                lp.bottomMargin = 0;
                rootCol.addView(quickCard, at, lp);
            }
            if (ecoCard == null) {

                ecoCard = com.talksy.messenger.ui.widget.SettingsUi.card(this);
                com.talksy.messenger.ui.widget.SettingsUi.Row wal = com.talksy.messenger.ui.widget.SettingsUi.styledRow(this, "tsc",
                        getString(R.string.wallet_title), getString(R.string.wallet_settings_sub), null,
                        v -> { startActivity(new Intent(this, WalletActivity.class)); AnimUtils.forward(this); });
                ecoCard.addView(wal.view);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) ecoCard.getLayoutParams();
                lp.topMargin = 0;
                lp.bottomMargin = com.talksy.messenger.ui.widget.SettingsUi.dp(this, 14);
                rootCol.addView(ecoCard, 0, lp);

                LinearLayout shop = com.talksy.messenger.ui.widget.SettingsUi.card(this);
                com.talksy.messenger.ui.widget.SettingsUi.Row buy = com.talksy.messenger.ui.widget.SettingsUi.styledRow(this, "store", "Покупка номеров", "Анонимные номера +888 и +999", null,
                        v -> { startActivity(new Intent(this, NumberShopActivity.class)); AnimUtils.forward(this); });
                com.talksy.messenger.ui.widget.SettingsUi.Row sell = com.talksy.messenger.ui.widget.SettingsUi.styledRow(this, "tsc", "Продажа номеров", "Свои номера, объявления и история", null,
                        v -> { startActivity(new Intent(this, NumberSellActivity.class)); AnimUtils.forward(this); });
                com.talksy.messenger.ui.widget.SettingsUi.Row pay = com.talksy.messenger.ui.widget.SettingsUi.styledRow(this, "tsc", "Оплата", "Пополнение за реальные деньги, история платежей", null,
                        v -> { startActivity(new Intent(this, PaymentsActivity.class)); AnimUtils.forward(this); });
                com.talksy.messenger.ui.widget.SettingsUi.Row gifts = com.talksy.messenger.ui.widget.SettingsUi.styledRow(this, "gift", "Подарки", "Ваши подарки и магазин подарков", null,
                        v -> { startActivity(new Intent(this, MyGiftsActivity.class)); AnimUtils.forward(this); });
                com.talksy.messenger.ui.widget.SettingsUi.Row prem = com.talksy.messenger.ui.widget.SettingsUi.styledRow(this, "premium", getString(R.string.premium_title), getString(R.string.premium_settings_sub), null,
                        v -> { startActivity(new Intent(this, PremiumActivity.class)); AnimUtils.forward(this); });
                shop.addView(buy.view); com.talksy.messenger.ui.widget.SettingsUi.divider(shop);
                shop.addView(sell.view); com.talksy.messenger.ui.widget.SettingsUi.divider(shop);
                shop.addView(pay.view); com.talksy.messenger.ui.widget.SettingsUi.divider(shop);
                shop.addView(gifts.view); com.talksy.messenger.ui.widget.SettingsUi.divider(shop);
                shop.addView(prem.view);
                rootCol.addView(shop);

                LinearLayout sup = com.talksy.messenger.ui.widget.SettingsUi.card(this);
                com.talksy.messenger.ui.widget.SettingsUi.Row help = com.talksy.messenger.ui.widget.SettingsUi.styledRow(this, "help",
                        getString(R.string.support_title), getString(R.string.support_settings_sub), null,
                        v -> { startActivity(new Intent(this, SupportActivity.class)); AnimUtils.forward(this); });
                sup.addView(help.view);
                rootCol.addView(sup);

                final Prefs exp = new Prefs(this);
                final LinearLayout expCard = com.talksy.messenger.ui.widget.SettingsUi.card(this);
                expCard.addView(com.talksy.messenger.ui.widget.SettingsUi.styledRow(this, "settings_2",
                        getString(R.string.experiments_title), getString(R.string.experiments_sub), null,
                        v -> { startActivity(new Intent(this, ExperimentsActivity.class)); AnimUtils.forward(this); }).view);
                expCard.setVisibility(exp.getBool("experiments_unlocked", false) ? View.VISIBLE : View.GONE);
                rootCol.addView(expCard);

                final TextView version = new TextView(this);
                version.setText("Talksy " + UpdateChecker.currentName(this) + " (" + UpdateChecker.currentCode(this) + ")");
                version.setGravity(android.view.Gravity.CENTER);
                version.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text3));
                version.setTextSize(12);
                int vp = com.talksy.messenger.ui.widget.SettingsUi.dp(this, 16);
                version.setPadding(vp, vp, vp, vp);
                final int[] taps = {0};
                final long[] lastTap = {0};
                version.setOnClickListener(v -> {
                    long now = android.os.SystemClock.elapsedRealtime();
                    taps[0] = now - lastTap[0] > 1500 ? 1 : taps[0] + 1;
                    lastTap[0] = now;
                    if (taps[0] < 4) return;
                    taps[0] = 0;
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    if (exp.getBool("experiments_unlocked", false)) {
                        Toast.makeText(this, R.string.experiments_already, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    exp.putBool("experiments_unlocked", true);
                    expCard.setVisibility(View.VISIBLE);
                    Toast.makeText(this, R.string.experiments_unlocked, Toast.LENGTH_LONG).show();
                });
                rootCol.addView(version);
            }
            tidyCards(rootCol);
        }
    }

    private static boolean isDivider(View v) {
        if (v.getClass() != View.class) return false;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        return lp != null && lp.height > 0 && lp.height <= com.talksy.messenger.ui.widget.SettingsUi.dp(v.getContext(), 2);
    }

    private static void tidyCards(LinearLayout root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View c = root.getChildAt(i);
            if (!(c instanceof LinearLayout) || c.getVisibility() == View.GONE) continue;
            LinearLayout card = (LinearLayout) c;
            if (card.getOrientation() != LinearLayout.VERTICAL) continue;
            boolean hasDivider = false;
            for (int k = 0; k < card.getChildCount(); k++) if (isDivider(card.getChildAt(k))) { hasDivider = true; break; }

            boolean rowsInside = hasDivider || card.getChildCount() == 1;
            if (!rowsInside) continue;
            View candidate = null; boolean seenRow = false, any = false;
            for (int k = 0; k < card.getChildCount(); k++) {
                View v = card.getChildAt(k);
                if (isDivider(v)) { v.setVisibility(View.GONE); candidate = v; continue; }
                if (v.getVisibility() != View.VISIBLE) continue;
                any = true;
                if (seenRow && candidate != null) candidate.setVisibility(View.VISIBLE);
                seenRow = true; candidate = null;
            }
            if (!any) {
                card.setVisibility(View.GONE);
                if (i > 0 && root.getChildAt(i - 1) instanceof TextView) root.getChildAt(i - 1).setVisibility(View.GONE);
            }
        }
    }

    private static void spaceCards(LinearLayout root) {
        int gap = com.talksy.messenger.ui.widget.SettingsUi.dp(root.getContext(), 14);
        for (int i = 0; i < root.getChildCount(); i++) {
            View c = root.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || !(c instanceof LinearLayout)) continue;
            if (((LinearLayout) c).getOrientation() != LinearLayout.VERTICAL) continue;
            ViewGroup.LayoutParams lp = c.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams && ((ViewGroup.MarginLayoutParams) lp).bottomMargin < gap) {
                ((ViewGroup.MarginLayoutParams) lp).bottomMargin = gap;
                c.setLayoutParams(lp);
            }
        }
    }

    private static boolean hideAllExcept(ViewGroup g, java.util.List<View> keep) {
        boolean any = false;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (keep.contains(c)) { any = true; continue; }
            if (c instanceof ViewGroup && containsAny((ViewGroup) c, keep)) { hideAllExcept((ViewGroup) c, keep); any = true; }
            else c.setVisibility(View.GONE);
        }
        return any;
    }
    private static boolean containsAny(ViewGroup g, java.util.List<View> keep) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (keep.contains(c)) return true;
            if (c instanceof ViewGroup && containsAny((ViewGroup) c, keep)) return true;
        }
        return false;
    }

    private void bindWhoCanWrite() {
        TextView t = findViewById(R.id.txt_who_can_write_value);
        int res = "nobody".equals(whoCanWrite) ? R.string.who_write_nobody
                : "contacts".equals(whoCanWrite) ? R.string.who_write_contacts
                : R.string.who_write_all;
        t.setText(res);
    }

    private void showWhoWriteDialog() {
        final String[] values = {"all", "contacts", "nobody"};
        CharSequence[] labels = {getString(R.string.who_write_all), getString(R.string.who_write_contacts),
                getString(R.string.who_write_nobody)};
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(whoCanWrite)) checked = i;
        }
        new com.talksy.messenger.ui.TDialog.Builder(this)
                .setTitle(R.string.who_write_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    final String chosen = values[which];
                    apiClient.setWhoCanWrite(chosen, (ok, v, error) -> {
                        if (ok) {
                            whoCanWrite = chosen;
                            bindWhoCanWrite();
                            Toast.makeText(this, R.string.who_write_saved, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, error != null ? error : getString(R.string.error_generic),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshServerLabel() {
        TextView txtServerValue = findViewById(R.id.txt_server_value);
        String url = apiClient.getBaseUrl();
        txtServerValue.setText(url.isEmpty() ? getString(R.string.settings_server_not_set) : url);
    }

    private void refreshProxyLabel() {
        TextView txtProxyStatus = findViewById(R.id.txt_proxy_status);
        String mode = new ProxyManager(this).getMode();
        txtProxyStatus.setText(proxyStatusLabel(mode));
    }

    private void refreshAppLockLabel() {
        TextView t = findViewById(R.id.txt_applock_value);
        if (!com.talksy.messenger.data.AppLock.isEnabled(this)) {
            t.setText(R.string.applock_off);
        } else if (com.talksy.messenger.data.AppLock.biometricEnabled(this)) {
            t.setText(R.string.applock_pin_bio);
        } else {
            t.setText(R.string.applock_pin_only);
        }
    }

    private void setAccountTtlLabel(int months) {
        TextView t = findViewById(R.id.txt_account_ttl_value);
        if (months == 6) t.setText(R.string.account_ttl_6);
        else if (months == 24) t.setText(R.string.account_ttl_24);
        else t.setText(R.string.account_ttl_12);
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> lockSetupLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), r -> {
                refreshAppLockLabel();
            });
    private final androidx.activity.result.ActivityResultLauncher<Intent> lockVerifyLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK) {
                    com.talksy.messenger.data.AppLock.disable(this);
                    refreshAppLockLabel();
                    Toast.makeText(this, R.string.applock_disabled, Toast.LENGTH_SHORT).show();
                }
            });

    private void showAppLockDialog() {
        boolean enabled = com.talksy.messenger.data.AppLock.isEnabled(this);
        java.util.List<String> items = new java.util.ArrayList<>();
        items.add(enabled ? getString(R.string.applock_change_pin) : getString(R.string.applock_set_pin));
        if (enabled) {
            boolean bio = com.talksy.messenger.data.AppLock.biometricEnabled(this);
            items.add(bio ? getString(R.string.applock_disable_biometric) : getString(R.string.applock_enable_biometric));
            items.add(getString(R.string.applock_disable));
        }
        new TDialog.Builder(this)
                .setTitle(R.string.applock_title)
                .setItems(items.toArray(new CharSequence[0]), (d, which) -> {
                    if (which == 0) {
                        lockSetupLauncher.launch(com.talksy.messenger.ui.LockActivity.intent(this, com.talksy.messenger.ui.LockActivity.MODE_SETUP));
                    } else if (which == 1) {
                        com.talksy.messenger.data.AppLock.setBiometric(this, !com.talksy.messenger.data.AppLock.biometricEnabled(this));
                        refreshAppLockLabel();
                    } else {
                        lockVerifyLauncher.launch(com.talksy.messenger.ui.LockActivity.intent(this, com.talksy.messenger.ui.LockActivity.MODE_VERIFY));
                    }
                })
                .show();
    }

    private static final int[] ACCOUNT_TTL_VALUES = {6, 12, 24};

    private void showAccountTtlDialog() {
        apiClient.getAccountTtl((ok, current, err) -> {
            int cur = ok && current != null ? current : 12;
            int checked = 1;
            for (int i = 0; i < ACCOUNT_TTL_VALUES.length; i++) if (ACCOUNT_TTL_VALUES[i] == cur) checked = i;
            CharSequence[] labels = {getString(R.string.account_ttl_6), getString(R.string.account_ttl_12), getString(R.string.account_ttl_24)};
            new TDialog.Builder(this)
                    .setTitle(R.string.account_ttl_title)
                    .setSingleChoiceItems(labels, checked, (d, which) -> {
                        apiClient.setAccountTtl(ACCOUNT_TTL_VALUES[which], (ok2, months, e2) -> {
                            d.dismiss();
                            if (ok2) setAccountTtlLabel(months);
                        });
                    })
                    .show();
        });
    }

    private void refreshWallpaperLabel() {
        TextView t = findViewById(R.id.txt_wallpaper_value);
        t.setText(ChatWallpaper.labelFor(this, prefs.get(Prefs.KEY_CHAT_WALLPAPER, ChatWallpaper.DEFAULT)));
    }

    private void refreshFontSizeLabel() {
        TextView t = findViewById(R.id.txt_font_size_value);
        t.setText(fontSizeLabel(prefs.get(Prefs.KEY_FONT_SIZE, "medium")));
    }

    private void refreshAutoDownloadLabel() {
        TextView t = findViewById(R.id.txt_auto_download_value);
        t.setText(autoDownloadLabel(prefs.get(Prefs.KEY_AUTO_DOWNLOAD, "wifi")));
    }

    private String fontSizeLabel(String v) {
        if ("small".equals(v)) return getString(R.string.font_size_small);
        if ("large".equals(v)) return getString(R.string.font_size_large);
        return getString(R.string.font_size_medium);
    }

    private String autoDownloadLabel(String v) {
        if ("always".equals(v)) return getString(R.string.auto_download_always);
        if ("never".equals(v)) return getString(R.string.auto_download_never);
        return getString(R.string.auto_download_wifi);
    }

    private void showWallpaperDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_wallpaper_picker, null);
        wallpaperDialog = new TDialog.Builder(this)
                .setTitle(R.string.settings_wallpaper)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        wireWallpaperSwatch(content, R.id.wp_default, ChatWallpaper.DEFAULT, wallpaperDialog);
        wireWallpaperSwatch(content, R.id.wp_indigo, "indigo", wallpaperDialog);
        wireWallpaperSwatch(content, R.id.wp_sunset, "sunset", wallpaperDialog);
        wireWallpaperSwatch(content, R.id.wp_ocean, "ocean", wallpaperDialog);
        wireWallpaperSwatch(content, R.id.wp_forest, "forest", wallpaperDialog);
        wireWallpaperSwatch(content, R.id.wp_rose, "rose", wallpaperDialog);
        wireWallpaperSwatch(content, R.id.wp_midnight, "midnight", wallpaperDialog);

        View wpUpload = content.findViewById(R.id.wp_upload);
        AnimUtils.pressScale(wpUpload);
        wpUpload.setOnClickListener(v -> pickWallpaper.launch("image/*"));

        wallpaperDialog.show();
    }

    private void onWallpaperPicked(Uri uri) {
        Toast.makeText(this, getString(R.string.uploading_photo), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Bitmap bitmap = decodeSampled(uri, 1080);
                if (bitmap == null) {
                    runOnUiThread(() -> Toast.makeText(this, getString(R.string.image_read_failed), Toast.LENGTH_SHORT).show());
                    return;
                }
                File out = ChatWallpaper.customFile(this);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 87, fos);
                }
                prefs.put(Prefs.KEY_CHAT_WALLPAPER, ChatWallpaper.CUSTOM);
                runOnUiThread(() -> {
                    refreshWallpaperLabel();
                    if (wallpaperDialog != null) wallpaperDialog.dismiss();
                    Toast.makeText(this, R.string.wallpaper_saved, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.photo_process_error) + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private Bitmap decodeSampled(Uri uri, int maxDim) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, bounds);
        }
        int sample = 1;
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(is, null, opts);
        }
    }

    private void wireWallpaperSwatch(View content, int viewId, String presetId, TDialog dialog) {
        View swatch = content.findViewById(viewId);
        AnimUtils.pressScale(swatch);
        swatch.setOnClickListener(v -> {
            prefs.put(Prefs.KEY_CHAT_WALLPAPER, presetId);
            refreshWallpaperLabel();
            dialog.dismiss();
        });
    }

    private void showFontSizeDialog() {
        String[] values = {"small", "medium", "large"};
        CharSequence[] labels = {getString(R.string.font_size_small), getString(R.string.font_size_medium), getString(R.string.font_size_large)};
        String current = prefs.get(Prefs.KEY_FONT_SIZE, "medium");
        int checked = java.util.Arrays.asList(values).indexOf(current);
        new TDialog.Builder(this)
                .setTitle(R.string.settings_font_size)
                .setSingleChoiceItems(labels, Math.max(checked, 0), (d, which) -> {
                    prefs.put(Prefs.KEY_FONT_SIZE, values[which]);
                    refreshFontSizeLabel();
                    d.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAutoDownloadDialog() {
        String[] values = {"wifi", "always", "never"};
        CharSequence[] labels = {getString(R.string.auto_download_wifi), getString(R.string.auto_download_always), getString(R.string.auto_download_never)};
        String current = prefs.get(Prefs.KEY_AUTO_DOWNLOAD, "wifi");
        int checked = java.util.Arrays.asList(values).indexOf(current);
        new TDialog.Builder(this)
                .setTitle(R.string.settings_auto_download)
                .setSingleChoiceItems(labels, Math.max(checked, 0), (d, which) -> {
                    prefs.put(Prefs.KEY_AUTO_DOWNLOAD, values[which]);
                    refreshAutoDownloadLabel();
                    d.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showServerDialog() {
        EditText input = new EditText(this);
        input.setHint(getString(R.string.settings_server_hint));
        input.setText(apiClient.getBaseUrl());
        input.setSingleLine(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new TDialog.Builder(this)
                .setTitle(R.string.settings_server)
                .setMessage(R.string.settings_server_sub)
                .setView(input)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    apiClient.setBaseUrl(input.getText().toString().trim());
                    refreshServerLabel();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String proxyStatusLabel(String mode) {
        switch (mode) {
            case ProxyManager.MODE_SOCKS5: return getString(R.string.proxy_mode_socks5);
            case ProxyManager.MODE_CUSTOM: return getString(R.string.proxy_mode_custom);
            default: return getString(R.string.proxy_mode_off);
        }
    }
}
