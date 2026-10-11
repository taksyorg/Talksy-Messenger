package com.talksy.messenger.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.adapters.ChatMediaAdapter;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.model.Message;
import com.talksy.messenger.data.model.StoryGroup;
import com.talksy.messenger.data.model.User;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.net.AvatarLoader;

import java.util.ArrayList;
import java.util.List;

public class UserProfileActivity extends BaseActivity {
    @Override protected boolean listensForCalls() { return true; }

    private static final int MAX_MEDIA_ITEMS = 15;

    private ApiClient apiClient;
    private long userId;
    private String profileName = "";
    private android.widget.LinearLayout giftsHolder;
    private View giftButton;

    private void addGiftButton() {
        android.view.ViewGroup content = findViewById(android.R.id.content);
        View sv = content.getChildAt(0);
        content.removeView(sv);
        android.widget.FrameLayout fl = new android.widget.FrameLayout(this);
        fl.addView(sv, new android.widget.FrameLayout.LayoutParams(-1, -1));
        content.addView(fl, new android.view.ViewGroup.LayoutParams(-1, -1));
        if (sv instanceof android.view.ViewGroup && ((android.view.ViewGroup) sv).getChildCount() > 0) {
            View inner = ((android.view.ViewGroup) sv).getChildAt(0);
            inner.setPadding(inner.getPaddingLeft(), inner.getPaddingTop(), inner.getPaddingRight(), EconomyUi.dp(this, 90));
        }
        giftButton = GiftFlow.floatingGiftButton(this, userId, profileName);
        fl.addView(giftButton);
    }
    private boolean currentlyBlocked;
    private StoryGroup storyGroup;

    private final List<Message> mediaItems = new ArrayList<>();
    private ChatMediaAdapter mediaAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        apiClient = new ApiClient(this);
        userId = getIntent().getLongExtra("user_id", -1);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> { finish(); AnimUtils.back(this); });

        android.view.View storiesCard = findViewById(R.id.card_stories);
        if (storiesCard != null && storiesCard.getParent() instanceof android.widget.LinearLayout) {
            android.widget.LinearLayout par = (android.widget.LinearLayout) storiesCard.getParent();
            android.widget.LinearLayout holder = new android.widget.LinearLayout(this);
            holder.setOrientation(android.widget.LinearLayout.VERTICAL);
            holder.setPadding(0, 0, 0, 12);
            par.addView(holder, par.indexOfChild(storiesCard) + 1);
            com.talksy.messenger.ui.ProfileStories.attach(this, holder, userId, getIntent().getStringExtra("user_name"), false);
        }
        AnimUtils.pressScale(btnBack);

        Button btnBlock = findViewById(R.id.btn_block);
        btnBlock.setOnClickListener(v -> {
            if (currentlyBlocked) {
                apiClient.unblockUser(userId, (ok, d, e) -> { if (ok) load(); });
            } else {
                apiClient.blockUser(userId, (ok, d, e) -> { if (ok) load(); });
            }
        });
        AnimUtils.pressScale(btnBlock);

        RecyclerView recyclerMedia = findViewById(R.id.recycler_media);
        recyclerMedia.setLayoutManager(new GridLayoutManager(this, 3));
        mediaAdapter = new ChatMediaAdapter(mediaItems, apiClient.getBaseUrl(), this::openMedia);
        recyclerMedia.setAdapter(mediaAdapter);

        giftsHolder = findViewById(R.id.card_gifts);

        View cardAddBotToGroup = findViewById(R.id.card_add_bot_to_group);
        cardAddBotToGroup.setOnClickListener(v -> {
            Intent i = new Intent(this, MyGroupsActivity.class);
            i.putExtra(MyGroupsActivity.EXTRA_PICK_BOT_ID, userId);
            startActivity(i);
            AnimUtils.forward(this);
        });
        AnimUtils.pressScale(cardAddBotToGroup);

        prefillFromCache();
        load();
        loadMedia();
        loadStories();
    }

    private int loadAttempts = 0;
    private final android.os.Handler retryHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private android.content.SharedPreferences profileCache() { return getSharedPreferences("profile_cache", MODE_PRIVATE); }

    private void prefillFromCache() {
        try {
            String raw = profileCache().getString("u" + userId, null);
            org.json.JSONObject o = raw == null ? new org.json.JSONObject() : new org.json.JSONObject(raw);
            String name = o.optString("name", getIntent().getStringExtra("user_name"));
            if (name == null || name.isEmpty()) return;
            User u = new User();
            u.name = name; u.colorHex = o.optString("color", "#6366F1"); u.avatarUrl = o.optString("avatar", "");
            TextView txtAvatar = findViewById(R.id.txt_avatar);
            txtAvatar.setText(u.initials());
            try { txtAvatar.getBackground().setTint(Color.parseColor(u.colorHex)); } catch (Exception ignored) {}
            ((TextView) findViewById(R.id.txt_name)).setText(name);
            if (!u.avatarUrl.isEmpty()) {
                final android.widget.ImageView img = findViewById(R.id.img_avatar);
                AvatarLoader.load(apiClient.getBaseUrl() + u.avatarUrl, bmp -> { img.setImageBitmap(bmp); img.setVisibility(View.VISIBLE); });
            }
        } catch (Exception ignored) { }
    }

    private void load() {
        apiClient.getUserProfile(userId, (ok, user, error) -> {
            if (!ok || user == null) {

                if (++loadAttempts < 4) { retryHandler.postDelayed(this::load, 1500L * loadAttempts); return; }
                Toast.makeText(this, error != null ? error : getString(R.string.profile_load_failed), Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                profileCache().edit().putString("u" + userId, new org.json.JSONObject().put("name", user.name == null ? "" : user.name)
                        .put("color", user.colorHex == null ? "#6366F1" : user.colorHex).put("avatar", user.avatarUrl == null ? "" : user.avatarUrl).toString()).apply();
            } catch (Exception ignored) { }
            bind(user);
        });
    }

    @Override
    protected void onDestroy() {
        retryHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void bind(User user) {
        TextView txtAvatar = findViewById(R.id.txt_avatar);
        android.widget.ImageView imgAvatar = findViewById(R.id.img_avatar);
        TextView txtName = findViewById(R.id.txt_name);
        View rowHandle = findViewById(R.id.row_handle);
        TextView txtHandle = findViewById(R.id.txt_handle);
        View rowPhone = findViewById(R.id.row_phone);
        TextView txtPhone = findViewById(R.id.txt_phone);
        TextView txtHiddenNote = findViewById(R.id.txt_hidden_note);
        Button btnBlock = findViewById(R.id.btn_block);

        txtAvatar.setText(user.initials());
        try { txtAvatar.getBackground().setTint(Color.parseColor(user.colorHex)); } catch (Exception ignored) {}
        if (user.avatarUrl != null && !user.avatarUrl.isEmpty()) {
            AvatarLoader.load(apiClient.getBaseUrl() + user.avatarUrl, bmp -> {
                imgAvatar.setImageBitmap(bmp);
                imgAvatar.setVisibility(View.VISIBLE);
            });
        }

        txtName.setText(user.isPremium ? com.talksy.messenger.data.IconManager.suffix(this, "premium_star", user.name,
                com.talksy.messenger.data.IconManager.tint(this), 16) : user.name);
        profileName = user.name;
        if (giftsHolder != null && !user.isBot) {
            GiftFlow.attachSection(this, apiClient, giftsHolder, userId, false);
            if (giftButton == null) addGiftButton();
        }
        TextView txtLastSeen = findViewById(R.id.txt_last_seen);
        txtLastSeen.setText(user.isBot ? user.botSubtitle(this) : user.lastSeenText(this));

        findViewById(R.id.card_gifts).setVisibility(user.isBot ? View.GONE : View.VISIBLE);
        findViewById(R.id.card_add_bot_to_group).setVisibility(user.isBot ? View.VISIBLE : View.GONE);

        boolean hasHandle = user.handle != null && !user.handle.isEmpty();
        boolean hasPhone = user.phone != null && !user.phone.isEmpty();
        rowHandle.setVisibility(hasHandle ? View.VISIBLE : View.GONE);
        rowPhone.setVisibility(hasPhone ? View.VISIBLE : View.GONE);
        if (hasHandle) txtHandle.setText(user.handle);
        if (hasPhone) txtPhone.setText(user.phone);
        txtHiddenNote.setVisibility((!hasHandle || !hasPhone) ? View.VISIBLE : View.GONE);

        currentlyBlocked = user.iBlockedThem;
        btnBlock.setText(currentlyBlocked ? getString(R.string.unblock) : getString(R.string.block));
    }

    private void loadMedia() {
        apiClient.getMessages(userId, 0, (ok, messages, error) -> {
            if (!ok || messages == null) return;
            mediaItems.clear();
            for (int i = messages.size() - 1; i >= 0 && mediaItems.size() < MAX_MEDIA_ITEMS; i--) {
                Message m = messages.get(i);
                if (m.media != null && (m.media.isImage() || m.media.isVideo())) mediaItems.add(m);
            }
            mediaAdapter.notifyDataSetChanged();
            findViewById(R.id.card_media).setVisibility(mediaItems.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    private void openMedia(Message m) {
        if (m.media == null) return;
        if (m.media.isVideo()) {
            com.talksy.messenger.net.FileOpener.downloadAndOpen(this, apiClient.getBaseUrl() + m.media.url, m.media.filename);
            return;
        }
        Intent i = new Intent(this, FullscreenImageActivity.class);
        i.putExtra("image_url", apiClient.getBaseUrl() + m.media.url);
        startActivity(i);
    }

    private void loadStories() {
        apiClient.getStories((ok, feed, error) -> {
            if (!ok || feed == null) return;
            storyGroup = null;
            for (StoryGroup g : feed.users) {
                if (g.userId == userId && !g.stories.isEmpty()) { storyGroup = g; break; }
            }
            View card = findViewById(R.id.card_stories);
            if (storyGroup == null) { card.setVisibility(View.GONE); return; }
            card.setVisibility(View.VISIBLE);
            bindStoryHighlight(storyGroup);
        });
    }

    private void bindStoryHighlight(StoryGroup g) {
        TextView txtAvatar = findViewById(R.id.txt_story_avatar);
        android.widget.ImageView imgAvatar = findViewById(R.id.img_story_avatar);
        android.widget.FrameLayout ringFrame = findViewById(R.id.frame_story_ring);
        View row = findViewById(R.id.row_story_highlight);

        String nm = g.name == null ? "" : g.name;
        txtAvatar.setText(nm.isEmpty() ? "?" : nm.substring(0, 1).toUpperCase());
        try { txtAvatar.getBackground().setTint(Color.parseColor(g.color)); } catch (Exception ignored) {}
        if (g.avatarUrl != null && !g.avatarUrl.isEmpty()) {
            AvatarLoader.load(apiClient.getBaseUrl() + g.avatarUrl, bmp -> {
                imgAvatar.setImageBitmap(bmp);
                imgAvatar.setVisibility(View.VISIBLE);
            });
        }

        android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
        float dp = getResources().getDisplayMetrics().density;
        ring.setCornerRadius(60 * dp * 0.5f);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke((int) (2.5f * dp), g.allViewed ? 0xFF8A94A6 : androidx.core.content.ContextCompat.getColor(this, R.color.accent));
        ringFrame.setBackground(ring);

        AnimUtils.pressScale(row);
        row.setOnClickListener(v -> openStoryViewer(g));
    }

    private void openStoryViewer(StoryGroup g) {
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
        intent.putExtra(StoryViewerActivity.EXTRA_USER_NAME, g.name);
        intent.putExtra(StoryViewerActivity.EXTRA_USER_COLOR, g.color);
        intent.putExtra(StoryViewerActivity.EXTRA_USER_AVATAR, g.avatarUrl);
        intent.putExtra(StoryViewerActivity.EXTRA_OWN, false);
        intent.putExtra(StoryViewerActivity.EXTRA_IDS, ids);
        intent.putExtra(StoryViewerActivity.EXTRA_URLS, urls);
        intent.putExtra(StoryViewerActivity.EXTRA_TYPES, types);
        intent.putExtra(StoryViewerActivity.EXTRA_TIMES, times);
        intent.putExtra(StoryViewerActivity.EXTRA_START, Math.max(0, start));
        startActivity(intent);
    }
}
