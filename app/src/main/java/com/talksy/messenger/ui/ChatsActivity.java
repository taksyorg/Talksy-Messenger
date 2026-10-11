package com.talksy.messenger.ui;

import android.Manifest;
import com.talksy.messenger.ui.TDialog;
import android.content.Intent;
import com.talksy.messenger.sync.LocalDb;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.animation.ValueAnimator;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.R;
import com.talksy.messenger.adapters.ChatListItemAdapter;
import com.talksy.messenger.adapters.SearchResultAdapter;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.data.model.Chat;
import com.talksy.messenger.data.model.ChatListItem;
import com.talksy.messenger.data.model.Group;
import com.talksy.messenger.data.model.SearchItem;
import com.talksy.messenger.data.model.StoryGroup;
import com.talksy.messenger.net.AvatarLoader;
import com.talksy.messenger.net.MediaUploader;
import com.talksy.messenger.data.model.User;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.net.MessagePollService;

import java.util.ArrayList;
import java.util.List;

public class ChatsActivity extends BaseActivity {

    private static final long POLL_INTERVAL_MS = 4000;

    private RecyclerView rvList;
    private android.view.View emptyState;
    private EditText inpSearch;

    private ChatListItemAdapter listAdapter;
    private final List<ChatListItem> listItems = new ArrayList<>();

    private SearchResultAdapter searchAdapter;
    private final List<SearchItem> searchItems = new ArrayList<>();

    private ApiClient apiClient;
    private boolean searchMode = false;
    private boolean firstListLoad = true;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    public static final String EXTRA_ADD_STORY = "add_story";
    private android.widget.LinearLayout bottomStack, invitesBanner, miniBar;
    private int pendingInvites = 0;
    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (!searchMode) reloadList();
            checkIncomingCall();
            if (storiesExpanded) reloadStories();
            pollHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private void checkIncomingCall() {

        if (com.talksy.messenger.ui.CallActivity.isShowing) return;
        apiClient.getIncomingCall((ok, call, error) -> {
            if (ok && call != null && !com.talksy.messenger.ui.CallActivity.isShowing) {
                Intent i = new Intent(this, com.talksy.messenger.ui.CallActivity.class);
                i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_MODE, com.talksy.messenger.ui.CallActivity.MODE_INCOMING);
                i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_CALL_ID, call.id);
                i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_PEER_ID, call.callerId);
                i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_PEER_NAME, call.callerName);
                i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_PEER_COLOR, call.callerColor);
                i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_VIDEO, "video".equals(call.callType));
                startActivity(i);
            }
        });
    }

    private static final int STORIES_HEIGHT_DP = 104;
    private boolean storiesExpanded = false;
    private ApiClient.StoriesFeed storiesFeed;
    private ValueAnimator storiesAnimator;

    private final ActivityResultLauncher<String> pickStoryMedia =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) publishStory(uri);
            });

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private final ActivityResultLauncher<String> requestNotifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) MessagePollService.start(this);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chats);

        apiClient = new ApiClient(this);
        UpdateChecker.checkOnStart(this);
        apiClient.economyGet("/me/phone-status", (okP, st, errP) -> {
            if (okP && st != null && st.optBoolean("required") && !st.optBoolean("verified")) {
                startActivity(new Intent(this, com.talksy.messenger.auth.PhoneVerifyActivity.class));
                finish();
            }
        });

        rvList = findViewById(R.id.rv_chats);
        emptyState = findViewById(R.id.empty_state);
        ImageButton btnNewChat = findViewById(R.id.btn_new_chat);
        com.talksy.messenger.data.LiquidGlass.applyPanel(findViewById(R.id.top_bar), 0f);
        ImageButton btnProfile = findViewById(R.id.btn_profile);
        ImageButton btnSettings = findViewById(R.id.btn_settings);
        AnimUtils.pressScale(btnNewChat);
        AnimUtils.pressScale(btnProfile);
        AnimUtils.pressScale(btnSettings);
        com.talksy.messenger.data.Accounts.rememberCurrent(this);
        btnProfile.setOnLongClickListener(v -> {
            com.talksy.messenger.data.AccountSwitcher.show(this);
            return true;
        });
        inpSearch = findViewById(R.id.inp_search);

        rvList.setLayoutManager(new LinearLayoutManager(this));
        listAdapter = new ChatListItemAdapter(listItems, apiClient.getBaseUrl(), this::openItem, this::showItemMenu);
        searchAdapter = new SearchResultAdapter(searchItems, apiClient.getBaseUrl(), this::openSearchItem);
        rvList.setAdapter(listAdapter);
        if (!AnimUtils.isEnabled(this)) rvList.setItemAnimator(null);

        btnNewChat.setOnClickListener(v -> showCreateMenu());
        btnSettings.setOnClickListener(v -> { startActivity(new Intent(this, SettingsActivity.class)); AnimUtils.forward(this); });
        btnProfile.setOnClickListener(v -> { startActivity(new Intent(this, ProfileActivity.class)); AnimUtils.forward(this); });

        setupProfileAvatar(btnProfile);
        startNotificationServiceIfEnabled();
        ensureEncryptionKeyReady();

        inpSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { scheduleSearch(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        setupStories();
    }

    private void setupStories() {
        View.OnTouchListener pull = new View.OnTouchListener() {
            float startY;
            boolean atTop;
            @Override public boolean onTouch(View v, MotionEvent e) {
                float dp = getResources().getDisplayMetrics().density;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = e.getY();
                        atTop = !rvList.canScrollVertically(-1);
                        break;
                    case MotionEvent.ACTION_MOVE: {
                        float dy = e.getY() - startY;
                        if (!storiesExpanded && atTop && dy > 56 * dp) {
                            setStoriesExpanded(true);
                            atTop = false;
                        } else if (storiesExpanded && dy < -40 * dp && !rvList.canScrollVertically(-1)) {
                            setStoriesExpanded(false);
                        }
                        break;
                    }
                    default:
                        break;
                }
                return false;
            }
        };
        rvList.setOnTouchListener(pull);
        emptyState.setOnTouchListener(pull);

        rvList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (storiesExpanded && dy > 8) setStoriesExpanded(false);
            }
        });

        findViewById(R.id.txt_logo).setOnClickListener(v -> setStoriesExpanded(!storiesExpanded));

        buildBottomStack();
    }

    private void setStoriesExpanded(boolean expand) {
        if (expand == storiesExpanded) return;
        storiesExpanded = expand;
        final View bar = findViewById(R.id.stories_bar);
        final View logo = findViewById(R.id.txt_logo);
        final float density = getResources().getDisplayMetrics().density;
        final int target = expand ? (int) (STORIES_HEIGHT_DP * density) : 0;
        final int from = bar.getLayoutParams().height;
        final long duration = AnimUtils.isEnabled(this) ? 240 : 0;

        if (storiesAnimator != null) storiesAnimator.cancel();
        storiesAnimator = ValueAnimator.ofInt(from, target);
        storiesAnimator.setDuration(duration);
        storiesAnimator.addUpdateListener(a -> {
            bar.getLayoutParams().height = (Integer) a.getAnimatedValue();
            bar.requestLayout();
        });
        storiesAnimator.start();

        logo.animate().alpha(expand ? 0f : 1f).setDuration(duration).start();
        bar.animate().alpha(expand ? 1f : 0f).setDuration(duration).start();

        if (expand) reloadStories();
    }

    private void reloadStories() {
        if (!apiClient.isConfigured()) return;
        apiClient.getStories((ok, feed, error) -> {
            if (!ok || feed == null) return;
            storiesFeed = feed;
            renderStories();
        });
    }

    private void renderStories() {
        LinearLayout row = findViewById(R.id.stories_row);
        row.removeAllViews();
        if (storiesFeed == null) return;
        row.addView(buildStoryItem(storiesFeed.mine, true));
        for (StoryGroup g : storiesFeed.users) row.addView(buildStoryItem(g, false));
    }

    private View buildStoryItem(final StoryGroup g, final boolean own) {
        final float dp = getResources().getDisplayMetrics().density;
        final int accent = ContextCompat.getColor(this, R.color.accent);
        boolean hasStories = !g.stories.isEmpty();

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setMinimumWidth((int) (78 * dp));
        item.setPadding(0, (int) (8 * dp), 0, (int) (4 * dp));

        item.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int boxSize = (int) (64 * dp);
        FrameLayout box = new FrameLayout(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(boxSize * 0.3f);
        ring.setColor(Color.TRANSPARENT);
        int ringColor = !hasStories ? Color.TRANSPARENT : (g.allViewed ? 0xFF8A94A6 : accent);
        ring.setStroke((int) (2.5f * dp), ringColor);
        box.setBackground(ring);

        int inner = (int) (54 * dp);
        TextView initials = new TextView(this);
        initials.setGravity(Gravity.CENTER);
        initials.setTextColor(Color.WHITE);
        initials.setTextSize(18);
        initials.setTypeface(null, Typeface.BOLD);
        initials.setBackgroundResource(R.drawable.bg_avatar);
        String nm = g.name == null ? "" : g.name;
        initials.setText(nm.isEmpty() ? "?" : nm.substring(0, 1).toUpperCase());
        try { initials.getBackground().setTint(Color.parseColor(g.color)); } catch (Exception ignored) { }
        box.addView(initials, new FrameLayout.LayoutParams(inner, inner, Gravity.CENTER));

        final ImageView img = new ImageView(this);
        img.setVisibility(View.GONE);
        box.addView(img, new FrameLayout.LayoutParams(inner, inner, Gravity.CENTER));
        if (g.avatarUrl != null && !g.avatarUrl.isEmpty()) {
            AvatarLoader.load(apiClient.getBaseUrl() + g.avatarUrl, bmp -> {
                img.setImageBitmap(bmp);
                img.setVisibility(View.VISIBLE);
            });
        }

        if (own) {
            TextView plus = new TextView(this);
            plus.setText("+");
            plus.setGravity(Gravity.CENTER);
            plus.setTextColor(Color.WHITE);
            plus.setTextSize(16);
            plus.setTypeface(null, Typeface.BOLD);
            GradientDrawable plusBg = new GradientDrawable();
            plusBg.setShape(GradientDrawable.OVAL);
            plusBg.setColor(accent);
            plusBg.setStroke((int) (2 * dp), ContextCompat.getColor(this, R.color.bg2));
            plus.setBackground(plusBg);
            int ps = (int) (22 * dp);
            box.addView(plus, new FrameLayout.LayoutParams(ps, ps, Gravity.BOTTOM | Gravity.END));
            plus.setOnClickListener(v -> startStoryCreation());
        }
        item.addView(box, new LinearLayout.LayoutParams(boxSize, boxSize));

        TextView label = new TextView(this);
        label.setText(own ? getString(R.string.story_you) : nm);
        label.setTextColor(ContextCompat.getColor(this, R.color.text1));
        label.setTextSize(11);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setMaxWidth((int) (72 * dp));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = (int) (4 * dp);
        item.addView(label, llp);

        item.setOnClickListener(v -> openStories(g, own));
        return item;
    }

    private void openStories(StoryGroup g, boolean own) {
        if (g.stories.isEmpty()) {
            if (own) startStoryCreation();
            return;
        }
        int n = g.stories.size();
        long[] ids = new long[n];
        String[] urls = new String[n];
        String[] types = new String[n];
        long[] times = new long[n];
        int start = -1;
        for (int i = 0; i < n; i++) {
            StoryGroup.Story s = g.stories.get(i);
            ids[i] = s.id;
            urls[i] = s.mediaUrl;
            types[i] = s.mediaType;
            times[i] = s.createdAtMs;
            if (start < 0 && !s.viewed) start = i;
        }
        Intent intent = new Intent(this, StoryViewerActivity.class);
        intent.putExtra(StoryViewerActivity.EXTRA_USER_ID, g.userId);
        intent.putExtra(StoryViewerActivity.EXTRA_USER_NAME, own ? getString(R.string.story_you) : g.name);
        intent.putExtra(StoryViewerActivity.EXTRA_USER_COLOR, g.color);
        intent.putExtra(StoryViewerActivity.EXTRA_USER_AVATAR, g.avatarUrl);
        intent.putExtra(StoryViewerActivity.EXTRA_OWN, own);
        intent.putExtra(StoryViewerActivity.EXTRA_IDS, ids);
        intent.putExtra(StoryViewerActivity.EXTRA_URLS, urls);
        intent.putExtra(StoryViewerActivity.EXTRA_TYPES, types);
        intent.putExtra(StoryViewerActivity.EXTRA_TIMES, times);
        intent.putExtra(StoryViewerActivity.EXTRA_START, Math.max(0, start));
        startActivity(intent);
    }

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private void buildBottomStack() {
        android.widget.FrameLayout content = findViewById(android.R.id.content);
        bottomStack = new android.widget.LinearLayout(this);
        bottomStack.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM);
        lp.setMargins(com.talksy.messenger.ui.widget.SettingsUi.dp(this, 16), 0, com.talksy.messenger.ui.widget.SettingsUi.dp(this, 16),
                com.talksy.messenger.ui.widget.SettingsUi.dp(this, 96));
        content.addView(bottomStack, lp);

        invitesBanner = pill(R.string.invites_banner, "add_contact", v -> startActivity(new android.content.Intent(this, InvitesActivity.class)));
        miniBar = pill(0, "mini_app", v -> startActivity(new android.content.Intent(this, MiniAppActivity.class).putExtra(MiniAppActivity.EXTRA_RESTORE, true)));
        bottomStack.addView(invitesBanner); bottomStack.addView(miniBar);
        invitesBanner.setVisibility(android.view.View.GONE); miniBar.setVisibility(android.view.View.GONE);

        android.widget.ImageView x = new android.widget.ImageView(this);
        com.talksy.messenger.data.IconManager.set(x, "close");
        x.setOnClickListener(v -> com.talksy.messenger.ui.MiniAppHost.close());
        miniBar.addView(x, 0, new android.widget.LinearLayout.LayoutParams(com.talksy.messenger.ui.widget.SettingsUi.dp(this, 22), com.talksy.messenger.ui.widget.SettingsUi.dp(this, 22)));
        android.widget.ImageView up = new android.widget.ImageView(this);
        up.setImageResource(R.drawable.ic_chevron_up);
        miniBar.addView(up, new android.widget.LinearLayout.LayoutParams(com.talksy.messenger.ui.widget.SettingsUi.dp(this, 22), com.talksy.messenger.ui.widget.SettingsUi.dp(this, 22)));
    }

    private android.widget.LinearLayout pill(int textRes, String icon, android.view.View.OnClickListener click) {
        android.widget.LinearLayout p = new android.widget.LinearLayout(this);
        p.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = com.talksy.messenger.ui.widget.SettingsUi.dp(this, 14);
        p.setPadding(pad, pad / 2, pad, pad / 2);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(com.talksy.messenger.ui.widget.SettingsUi.dp(this, 24));
        g.setColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg2));
        g.setStroke(1, androidx.core.content.ContextCompat.getColor(this, R.color.bg3));
        p.setBackground(g);
        p.setElevation(com.talksy.messenger.ui.widget.SettingsUi.dp(this, 4));
        android.widget.TextView t = new android.widget.TextView(this);
        t.setTag("label");
        if (textRes != 0) t.setText(textRes);
        t.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text1)); t.setTextSize(14);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(pad / 2, 0, 0, 0);
        p.addView(t, new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        p.setOnClickListener(click);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, com.talksy.messenger.ui.widget.SettingsUi.dp(this, 46));
        lp.bottomMargin = com.talksy.messenger.ui.widget.SettingsUi.dp(this, 6);
        p.setLayoutParams(lp);
        return p;
    }

    private void updateBottomStack() {
        if (bottomStack == null) return;
        boolean mini = com.talksy.messenger.ui.MiniAppHost.isMinimized();
        miniBar.setVisibility(mini ? android.view.View.VISIBLE : android.view.View.GONE);
        if (mini) ((android.widget.TextView) miniBar.findViewWithTag("label")).setText(com.talksy.messenger.ui.MiniAppHost.get().title == null ? getString(R.string.miniapp_default_title) : com.talksy.messenger.ui.MiniAppHost.get().title);
        invitesBanner.setVisibility(pendingInvites > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
        if (pendingInvites > 0) ((android.widget.TextView) invitesBanner.findViewWithTag("label")).setText(getString(R.string.invites_banner, pendingInvites));
    }

    private void loadInvites() {
        apiClient.getGroupInvites((ok, list, err) -> {
            pendingInvites = ok && list != null ? list.size() : 0;
            updateBottomStack();
        });
    }

    private void startStoryCreation() {
        if (storiesFeed != null && storiesFeed.mine.stories.size() >= 3) {
            Toast.makeText(this, R.string.story_limit, Toast.LENGTH_LONG).show();
            return;
        }
        new TDialog.Builder(this)
                .setItems(new CharSequence[]{getString(R.string.story_photo), getString(R.string.story_video)},
                        (dialog, which) -> pickStoryMedia.launch(which == 0 ? "image/*" : "video/*"))
                .show();
    }

    private void publishStory(android.net.Uri uri) {
        final MediaUploader.FileInfo info = MediaUploader.describe(this, uri);
        if (info.size > MediaUploader.MAX_FILE_BYTES) {
            Toast.makeText(this, getString(R.string.upload_too_big, MediaUploader.MAX_FILE_BYTES / (1024 * 1024)),
                    Toast.LENGTH_LONG).show();
            return;
        }
        final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        final UploadProgressDialog dlg = new UploadProgressDialog(this, info.name, () -> cancelled.set(true));
        dlg.show();
        apiClient.uploadMediaStream(uri, info.name, info.mime, info.size, dlg::update, cancelled,
                (ok, media, error) -> {
                    if (!ok || media == null) {
                        dlg.dismiss();
                        if (!cancelled.get()) {
                            Toast.makeText(this, error != null ? error : getString(R.string.file_upload_failed),
                                    Toast.LENGTH_LONG).show();
                        }
                        return;
                    }
                    apiClient.createStory(media, (ok2, v, error2) -> {
                        dlg.dismiss();
                        if (ok2) {
                            Toast.makeText(this, R.string.story_published, Toast.LENGTH_SHORT).show();
                            reloadStories();
                        } else {
                            Toast.makeText(this, error2 != null ? error2 : getString(R.string.error_generic),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                });
    }

    private void showCreateMenu() {
        new TDialog.Builder(this)
                .setItems(new CharSequence[]{getString(R.string.fab_new_chat), getString(R.string.fab_new_group)}, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, NewChatActivity.class));
                    } else {
                        startActivity(new Intent(this, CreateGroupActivity.class));
                    }
                    AnimUtils.forward(this);
                })
                .show();
    }

    private void startNotificationServiceIfEnabled() {
        Prefs prefs = new Prefs(this);
        if (!prefs.getBool(Prefs.KEY_NOTIFICATIONS_ENABLED, true)) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                MessagePollService.start(this);
            } else {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            MessagePollService.start(this);
        }
    }

    private static final String CRYPTO_TAG = "TalksyCrypto";

    private void ensureEncryptionKeyReady() {
        new Thread(() -> {
            try {
                com.talksy.messenger.net.CryptoManager.ensureKeyPairExists();
                Prefs prefs = new Prefs(this);
                if (!prefs.getBool("public_key_uploaded", false)) {
                    String pub = com.talksy.messenger.net.CryptoManager.getMyPublicKeyBase64();
                    apiClient.uploadPublicKey(pub, (ok, d, e) -> {
                        if (ok) {
                            prefs.putBool("public_key_uploaded", true);
                        } else {
                            android.util.Log.e(CRYPTO_TAG, getString(R.string.pubkey_upload_failed) + e);
                            showCryptoErrorDialog(getString(R.string.pubkey_not_uploaded), String.valueOf(e));
                        }
                    });
                }
            } catch (Exception ex) {

                android.util.Log.e(CRYPTO_TAG, getString(R.string.keygen_failed), ex);
                showCryptoErrorDialog(getString(R.string.keygen_create_failed), android.util.Log.getStackTraceString(ex));
            }
        }).start();
    }

    private long lastCryptoErrorDialogAt = 0;
    private void showCryptoErrorDialog(String title, String details) {
        long now = System.currentTimeMillis();
        if (now - lastCryptoErrorDialogAt < 10000) return;
        lastCryptoErrorDialogAt = now;
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            TextView tv = new TextView(this);
            tv.setText(details);
            tv.setTextIsSelectable(true);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            tv.setPadding(pad, pad, pad, pad);
            android.widget.ScrollView scroll = new android.widget.ScrollView(this);
            scroll.addView(tv);
            new TDialog.Builder(this)
                    .setTitle(title)
                    .setView(scroll)
                    .setPositiveButton(getString(R.string.ok), null)
                    .show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupProfileAvatar(findViewById(R.id.btn_profile));
        if (!searchMode) reloadList();
        ensureEncryptionKeyReady();
        reloadStories();
        com.talksy.messenger.ui.MiniAppHost.setOnChange(this::updateBottomStack);
        updateBottomStack();
        loadInvites();
        if (getIntent().getBooleanExtra(EXTRA_ADD_STORY, false)) { getIntent().removeExtra(EXTRA_ADD_STORY); startStoryCreation(); }
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollRunnable);
        com.talksy.messenger.ui.MiniAppHost.setOnChange(null);
    }

    private void setupProfileAvatar(ImageButton btnProfile) {
        try {
            String color = new Prefs(this).get(Prefs.KEY_MY_COLOR, "#6366F1");
            btnProfile.getBackground().setTint(Color.parseColor(color));
        } catch (Exception ignored) {}
    }

    private boolean sameChatListSignature(List<ChatListItem> a, List<ChatListItem> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            ChatListItem x = a.get(i), y = b.get(i);
            if (x.id != y.id || x.isGroup != y.isGroup || x.lastMessageTime != y.lastMessageTime
                    || x.unreadCount != y.unreadCount || x.pinned != y.pinned || x.muted != y.muted
                    || !java.util.Objects.equals(x.name, y.name)
                    || !java.util.Objects.equals(x.lastMessage, y.lastMessage)) return false;
        }
        return true;
    }

    private void reloadList() {
        com.talksy.messenger.sync.LegacySync.get(this).run();

        try {
            LocalDb local = LocalDb.get(this);

            List<ChatListItem> cached = local.getChatList(false);

            if (cached != null && !cached.isEmpty()) {
                listItems.clear();
                listItems.addAll(cached);
                listAdapter.notifyDataSetChanged();
                showEmpty(false);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!apiClient.isConfigured()) {
            showEmpty(listItems.isEmpty());
            return;
        }

        apiClient.getChats((okC, chats, errC) -> apiClient.getMyGroups((okG, groups, errG) -> {

            List<ChatListItem> merged = new ArrayList<>();

            if (okC && chats != null) {
                for (Chat c : chats) {
                    merged.add(ChatListItem.fromChat(c));

                    android.util.Log.d(
                            "TALKSY_DEBUG",
                            "CHAT id=" + c.peerUserId +
                            " name=" + c.peerName +
                            " avatar=" + c.peerAvatarUrl +
                            " last=" + c.lastMessage
                    );

                }
            }

            LocalDb local = LocalDb.get(this);

            if ((okC && chats != null) || (okG && groups != null)) {
                local.seedLegacyChats(okC ? chats : null, okG ? groups : null, false);
            }

            if (okG && groups != null) {

                for (Group g : groups) {
                    merged.add(ChatListItem.fromGroup(g));

                    android.util.Log.d(
                            "TALKSY_DEBUG",
                            "GROUP id=" + g.id +
                            " name=" + g.name +
                            " avatar=" + g.avatarUrl +
                            " last=" + g.lastMessage
                    );
                }
            }

            java.util.HashSet<String> seenChats = new java.util.HashSet<>();
            java.util.List<ChatListItem> uniqueMerged = new java.util.ArrayList<>();

            for (ChatListItem item : merged) {
                String key = item.hashCode() + "";
                if (seenChats.add(key)) {
                    uniqueMerged.add(item);
                }
            }

            merged = uniqueMerged;

            merged.sort((a, b) -> {
                if (a.pinned != b.pinned)
                    return a.pinned ? -1 : 1;

                return Long.compare(b.lastMessageTime, a.lastMessageTime);
            });

            java.util.LinkedHashMap<String, ChatListItem> unique = new java.util.LinkedHashMap<>();

            for (ChatListItem item : merged) {
                String key;

                key = (item.isGroup ? "group:" : "user:") + item.id;

                unique.put(key, item);
            }

            merged.clear();
            merged.addAll(unique.values());

            android.util.Log.d(
                    "TALKSY_DEBUG",
                    "FINAL LIST SIZE=" + merged.size()
            );

            android.util.Log.d(
                    "TALKSY_DEBUG",
                    "DEDUP SIZE=" + merged.size()
            );

            if (!sameChatListSignature(listItems, merged)) {

                android.util.Log.d(
                        "TALKSY_DEBUG",
                        "SERVER MERGED SIZE=" + merged.size()
                        + " OLD SIZE=" + listItems.size()
                );

                if (merged.isEmpty() && !listItems.isEmpty()) {
                    android.util.Log.d(
                            "TALKSY_DEBUG",
                            "IGNORE EMPTY SERVER LIST"
                    );
                } else {

                    java.util.HashSet<String> current = new java.util.HashSet<>();

                    for (ChatListItem item : merged) {
                        current.add(item.hashCode() + "");
                    }

                    listItems.clear();
                    listItems.addAll(merged);

                    listAdapter.notifyDataSetChanged();
                }
            }

            showEmpty(listItems.isEmpty());

        }));

        loadArchivePreview();
    }

    private void loadArchivePreview() {
        android.view.View rowArchive = findViewById(R.id.row_archive_preview);
        TextView txtPreview = findViewById(R.id.txt_archive_preview);
        TextView txtCount = findViewById(R.id.txt_archive_count);

        apiClient.getChats(true, (okC, chats, errC) -> apiClient.getMyGroups(true, (okG, groups, errG) -> {
            List<String> names = new ArrayList<>();
            int count = 0;
            if (okC && chats != null) { for (Chat c : chats) names.add(c.peerName); count += chats.size(); }
            if (okG && groups != null) { for (Group g : groups) names.add(g.name); count += groups.size(); }

            if (count == 0) {
                rowArchive.setVisibility(android.view.View.GONE);
                return;
            }
            rowArchive.setVisibility(android.view.View.VISIBLE);
            txtPreview.setText(android.text.TextUtils.join(", ", names));
            txtCount.setText(String.valueOf(count));
            rowArchive.setOnClickListener(v -> {
                startActivity(new Intent(this, ArchivedActivity.class));
                AnimUtils.forward(this);
            });
        }));
    }

    private void showEmpty(boolean empty) {
        emptyState.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
        rvList.setVisibility(android.view.View.VISIBLE);
    }

    private void openItem(ChatListItem it) {
        if (it.isGroup) {
            Intent i = new Intent(this, GroupChatActivity.class);
            i.putExtra("group_id", it.id);
            i.putExtra("group_name", it.name);
            i.putExtra("my_role", it.myRole);
            i.putExtra("admin_only_mode", it.adminOnlyMode);
            startActivity(i);
        } else {
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra("peer_id", it.id);
            i.putExtra("peer_name", it.name);
            i.putExtra("peer_color", it.colorHex);
            i.putExtra("peer_avatar_url", it.avatarUrl);
            startActivity(i);
        }
        AnimUtils.forward(this);
    }

    private void showItemMenu(ChatListItem it) {
        String peerType = it.isGroup ? "group" : "user";
        java.util.List<String> options = new java.util.ArrayList<>(java.util.Arrays.asList(
                it.pinned ? getString(R.string.chat_unpin) : getString(R.string.chat_pin),
                getString(R.string.chat_archive),
                it.muted ? getString(R.string.notif_enable) : getString(R.string.notif_disable)));
        int deleteIndex = -1;
        if (!it.isGroup) { deleteIndex = options.size(); options.add(getString(R.string.chat_delete)); }
        final int delIdx = deleteIndex;
        new TDialog.Builder(this)
                .setTitle(it.name)
                .setItems(options.toArray(new CharSequence[0]), (dialog, which) -> {
                    if (which == 0) {
                        apiClient.setChatState(peerType, it.id, !it.pinned, null, null, (ok, d, e) -> reloadList());
                    } else if (which == 1) {
                        apiClient.setChatState(peerType, it.id, null, true, null, (ok, d, e) -> reloadList());
                    } else if (which == 2) {
                        apiClient.setChatState(peerType, it.id, null, null, !it.muted, (ok, d, e) -> reloadList());
                    } else if (which == delIdx) {
                        confirmDeleteChat(it);
                    }
                })
                .show();
    }

    private void confirmDeleteChat(ChatListItem it) {
        CharSequence[] opts = {getString(R.string.chat_delete_for_me), getString(R.string.chat_delete_for_everyone)};
        new TDialog.Builder(this)
                .setTitle(R.string.chat_delete)
                .setItems(opts, (d, which) -> apiClient.deleteChat(it.id, which == 1, (ok, v, e) -> {
                    if (ok) reloadList();
                    else Toast.makeText(this, e != null ? e : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                }))
                .show();
    }

    private void scheduleSearch(String query) {
        if (pendingSearch != null) debounceHandler.removeCallbacks(pendingSearch);
        pendingSearch = () -> runSearch(query);
        debounceHandler.postDelayed(pendingSearch, 350);
    }

    private void runSearch(String query) {
        String q = query.trim();
        if (EasterEggs.handle(this, q)) { inpSearch.setText(""); return; }
        if (q.isEmpty()) {
            searchMode = false;
            rvList.setAdapter(listAdapter);
            reloadList();
            return;
        }
        searchMode = true;

        apiClient.searchUsers(q, (okU, users, errU) -> apiClient.searchGroups(q, (okG, groups, errG) -> {
            searchItems.clear();
            if (okU && users != null) for (User u : users) searchItems.add(SearchItem.fromUser(u));
            if (okG && groups != null) for (Group g : groups) searchItems.add(SearchItem.fromGroup(g));
            rvList.setAdapter(searchAdapter);
            searchAdapter.notifyDataSetChanged();
            showEmpty(searchItems.isEmpty());
            if (searchItems.isEmpty()) android.widget.Toast.makeText(this, "Пользователь с таким username не найден", android.widget.Toast.LENGTH_SHORT).show();
        }));
    }

    private void openSearchItem(SearchItem it) {
        if (it.isGroup) {
            if (it.myRole != null) {
                Intent i = new Intent(this, GroupChatActivity.class);
                i.putExtra("group_id", it.id);
                i.putExtra("group_name", it.name);
                i.putExtra("my_role", it.myRole);
                startActivity(i);
                AnimUtils.forward(this);
            } else {

                Intent i = new Intent(this, GroupChatActivity.class);
                i.putExtra("group_id", it.id);
                i.putExtra("group_name", it.name);
                startActivity(i);
                AnimUtils.forward(this);
            }
        } else {
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra("peer_id", it.id);
            i.putExtra("peer_name", it.name);
            i.putExtra("peer_color", it.colorHex);
            i.putExtra("peer_avatar_url", it.avatarUrl);
            startActivity(i);
            AnimUtils.forward(this);
        }
    }
}
