package com.talksy.messenger.ui;

import com.talksy.messenger.data.IconManager;

import com.talksy.messenger.ui.TDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.net.AvatarLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class StoryViewerActivity extends BaseActivity {

    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_USER_NAME = "user_name";
    public static final String EXTRA_USER_COLOR = "user_color";
    public static final String EXTRA_USER_AVATAR = "user_avatar";
    public static final String EXTRA_OWN = "own";
    public static final String EXTRA_ARCHIVE = "archive";
    public static final String EXTRA_IDS = "ids";
    public static final String EXTRA_URLS = "urls";
    public static final String EXTRA_TYPES = "types";
    public static final String EXTRA_TIMES = "times";
    public static final String EXTRA_START = "start";

    private static final long IMAGE_DURATION_MS = 6000;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private ApiClient api;
    private long userId;
    private boolean own;
    private boolean archive;
    private long[] ids;
    private String[] urls;
    private String[] types;
    private long[] times;
    private int index = 0;

    private ImageView imageView;
    private VideoView videoView;
    private ProgressBar loading;
    private View[] segFill;
    private TextView txtTime;
    private EditText inpReply;
    private TextView txtViews;
    private TextView btnLike;
    private boolean liked;
    private long likesCount;
    private final java.util.List<ApiClient.StoryViewer> viewers = new java.util.ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile int loadToken = 0;

    private boolean isVideoNow = false;
    private long durationMs = 0;
    private long elapsedMs = 0;
    private long lastTickAt = 0;

    private boolean paused = false;
    private boolean pausedByTouch = false;
    private boolean pausedByInput = false;
    private boolean pausedByDialog = false;
    private boolean pausedByLifecycle = false;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            long now = SystemClock.uptimeMillis();
            if (!paused) {
                float p = 0f;
                if (isVideoNow) {
                    int dur = videoView.getDuration();
                    if (dur > 0) p = videoView.getCurrentPosition() / (float) dur;
                } else if (durationMs > 0) {
                    elapsedMs += now - lastTickAt;
                    p = elapsedMs / (float) durationMs;
                    if (p >= 1f) {
                        setSegment(1f);
                        next();
                        return;
                    }
                }
                setSegment(p);
            }
            lastTickAt = now;
            handler.postDelayed(this, 40);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        api = new ApiClient(this);

        userId = getIntent().getLongExtra(EXTRA_USER_ID, 0);
        own = getIntent().getBooleanExtra(EXTRA_OWN, false);
        archive = getIntent().getBooleanExtra(EXTRA_ARCHIVE, false);
        ids = getIntent().getLongArrayExtra(EXTRA_IDS);
        urls = getIntent().getStringArrayExtra(EXTRA_URLS);
        types = getIntent().getStringArrayExtra(EXTRA_TYPES);
        times = getIntent().getLongArrayExtra(EXTRA_TIMES);
        if (ids == null || ids.length == 0 || urls == null || types == null || times == null) {
            finish();
            return;
        }
        int start = getIntent().getIntExtra(EXTRA_START, 0);

        buildUi();
        showStory(Math.max(0, Math.min(start, ids.length - 1)));
    }

    private void buildUi() {
        final float dp = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setVisibility(View.GONE);
        root.addView(imageView, new FrameLayout.LayoutParams(MATCH, MATCH));

        videoView = new VideoView(this);
        videoView.setVisibility(View.GONE);
        root.addView(videoView, new FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER));

        loading = new ProgressBar(this);
        root.addView(loading, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER));

        View touch = new View(this);
        touch.setOnTouchListener(new View.OnTouchListener() {
            long downAt;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downAt = SystemClock.uptimeMillis();
                        if (inpReply != null && inpReply.hasFocus()) {
                            inpReply.clearFocus();
                            hideKeyboard();
                        }
                        pausedByTouch = true;
                        applyPause();
                        return true;
                    case MotionEvent.ACTION_UP:
                        pausedByTouch = false;
                        applyPause();
                        if (SystemClock.uptimeMillis() - downAt < 250) {
                            if (e.getX() < v.getWidth() / 3f) prev(); else next();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        pausedByTouch = false;
                        applyPause();
                        return true;
                    default:
                        return true;
                }
            }
        });
        root.addView(touch, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding((int) (8 * dp), (int) (8 * dp), (int) (8 * dp), (int) (14 * dp));
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xAA000000, 0x00000000}));

        LinearLayout segments = new LinearLayout(this);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        segFill = new View[ids.length];
        for (int i = 0; i < ids.length; i++) {
            FrameLayout seg = new FrameLayout(this);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, (int) (3 * dp), 1f);
            slp.setMargins((int) (2 * dp), 0, (int) (2 * dp), 0);
            View segBg = new View(this);
            segBg.setBackgroundColor(0x55FFFFFF);
            seg.addView(segBg, new FrameLayout.LayoutParams(MATCH, MATCH));
            View fill = new View(this);
            fill.setBackgroundColor(Color.WHITE);
            fill.setPivotX(0f);
            fill.setScaleX(0f);
            seg.addView(fill, new FrameLayout.LayoutParams(MATCH, MATCH));
            segments.addView(seg, slp);
            segFill[i] = fill;
        }
        top.addView(segments, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding((int) (4 * dp), (int) (10 * dp), (int) (4 * dp), 0);

        int avSize = (int) (38 * dp);
        FrameLayout avatar = new FrameLayout(this);
        TextView initials = new TextView(this);
        initials.setGravity(Gravity.CENTER);
        initials.setTextColor(Color.WHITE);
        initials.setTextSize(15);
        initials.setTypeface(null, Typeface.BOLD);
        initials.setBackgroundResource(R.drawable.bg_avatar);
        String userName = getIntent().getStringExtra(EXTRA_USER_NAME);
        if (userName == null) userName = "";
        initials.setText(userName.isEmpty() ? "?" : userName.substring(0, 1).toUpperCase());
        try {
            initials.getBackground().setTint(Color.parseColor(getIntent().getStringExtra(EXTRA_USER_COLOR)));
        } catch (Exception ignored) { }
        avatar.addView(initials, new FrameLayout.LayoutParams(MATCH, MATCH));
        final ImageView avatarImg = new ImageView(this);
        avatarImg.setVisibility(View.GONE);
        avatar.addView(avatarImg, new FrameLayout.LayoutParams(MATCH, MATCH));
        String avatarUrl = getIntent().getStringExtra(EXTRA_USER_AVATAR);
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            AvatarLoader.load(api.getBaseUrl() + avatarUrl, bmp -> {
                avatarImg.setImageBitmap(bmp);
                avatarImg.setVisibility(View.VISIBLE);
            });
        }
        header.addView(avatar, new LinearLayout.LayoutParams(avSize, avSize));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        nlp.setMargins((int) (10 * dp), 0, (int) (8 * dp), 0);
        TextView txtName = new TextView(this);
        txtName.setText(userName);
        txtName.setTextColor(Color.WHITE);
        txtName.setTextSize(15);
        txtName.setTypeface(null, Typeface.BOLD);
        txtName.setSingleLine(true);
        names.addView(txtName);
        txtTime = new TextView(this);
        txtTime.setTextColor(0xCCFFFFFF);
        txtTime.setTextSize(12);
        names.addView(txtTime);
        header.addView(names, nlp);

        if (own && !archive) {
            TextView del = new TextView(this);
            IconManager.set(del, "delete", 24, 0xFFFFFFFF);
            del.setGravity(Gravity.CENTER);
            del.setOnClickListener(v -> deleteCurrent());
            header.addView(del, new LinearLayout.LayoutParams((int) (44 * dp), (int) (44 * dp)));
        }
        TextView close = new TextView(this);
        IconManager.set(close, "close", 24, 0xFFFFFFFF);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> finish());
        header.addView(close, new LinearLayout.LayoutParams((int) (44 * dp), (int) (44 * dp)));

        top.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));
        root.addView(top, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP));

        if (!own && !archive) {
            LinearLayout replyRow = new LinearLayout(this);
            replyRow.setOrientation(LinearLayout.HORIZONTAL);
            replyRow.setGravity(Gravity.CENTER_VERTICAL);
            replyRow.setPadding((int) (12 * dp), (int) (14 * dp), (int) (12 * dp), (int) (12 * dp));
            replyRow.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{0xAA000000, 0x00000000}));

            inpReply = new EditText(this);
            inpReply.setHint(R.string.story_reply_hint);
            inpReply.setHintTextColor(0x99FFFFFF);
            inpReply.setTextColor(Color.WHITE);
            inpReply.setTextSize(15);
            inpReply.setSingleLine(true);
            inpReply.setImeOptions(EditorInfo.IME_ACTION_SEND);
            GradientDrawable inpBg = new GradientDrawable();
            inpBg.setCornerRadius(24 * dp);
            inpBg.setColor(0x44FFFFFF);
            inpReply.setBackground(inpBg);
            inpReply.setPadding((int) (16 * dp), (int) (10 * dp), (int) (16 * dp), (int) (10 * dp));
            inpReply.setOnEditorActionListener((v, actionId, ev) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendReply();
                    return true;
                }
                return false;
            });
            inpReply.setOnFocusChangeListener((v, hasFocus) -> {
                pausedByInput = hasFocus;
                applyPause();
            });
            replyRow.addView(inpReply, new LinearLayout.LayoutParams(0, WRAP, 1f));

            btnLike = new TextView(this);
            btnLike.setText("♡"); btnLike.setTextSize(26); btnLike.setTextColor(0xFFFFFFFF); btnLike.setGravity(Gravity.CENTER);
            btnLike.setOnClickListener(v -> toggleLike());
            replyRow.addView(btnLike, new LinearLayout.LayoutParams((int) (48 * dp), (int) (44 * dp)));

            TextView send = new TextView(this);
            IconManager.set(send, "send", 26, 0xFFFFFFFF);
            send.setGravity(Gravity.CENTER);
            send.setOnClickListener(v -> sendReply());
            replyRow.addView(send, new LinearLayout.LayoutParams((int) (48 * dp), (int) (44 * dp)));

            root.addView(replyRow, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM));
        } else {

            txtViews = new TextView(this);
            txtViews.setText(IconManager.prefix(StoryViewerActivity.this, "eye", "0", 0xFFFFFFFF, 18));
            txtViews.setTextColor(Color.WHITE);
            txtViews.setTextSize(16);
            txtViews.setGravity(Gravity.CENTER);
            txtViews.setPadding((int) (16 * dp), (int) (16 * dp), (int) (16 * dp), (int) (20 * dp));
            txtViews.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{0xAA000000, 0x00000000}));
            txtViews.setOnClickListener(v -> showViewers());
            root.addView(txtViews, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM));
        }

        setContentView(root);
    }

    private void showStory(int i) {
        if (i < 0) i = 0;
        if (i >= ids.length) {
            finish();
            return;
        }
        index = i;
        final int token = ++loadToken;

        handler.removeCallbacks(ticker);
        isVideoNow = false;
        durationMs = 0;
        elapsedMs = 0;
        try { videoView.stopPlayback(); } catch (Exception ignored) { }
        imageView.setVisibility(View.GONE);
        videoView.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        for (int k = 0; k < segFill.length; k++) segFill[k].setScaleX(k < index ? 1f : 0f);
        txtTime.setText(timeAgo(times[index]));

        if (archive) {   } else if (!own) { api.viewStory(ids[index]); loadLikeState(); } else { loadViewers(); loadLikeState(); }

        final String fullUrl = api.getBaseUrl() + urls[index];
        if ("video".equals(types[index])) {
            downloadVideo(fullUrl, ids[index], token);
        } else {
            AvatarLoader.loadRaw(fullUrl, 2000, bmp -> {
                if (token != loadToken) return;
                loading.setVisibility(View.GONE);
                imageView.setImageBitmap(bmp);
                imageView.setVisibility(View.VISIBLE);
                startImageTimer();
            });
            handler.postDelayed(() -> {
                if (token == loadToken && loading.getVisibility() == View.VISIBLE) {
                    Toast.makeText(this, R.string.story_load_failed, Toast.LENGTH_SHORT).show();
                    next();
                }
            }, 20000);
        }
    }

    private void startImageTimer() {
        isVideoNow = false;
        durationMs = IMAGE_DURATION_MS;
        elapsedMs = 0;
        lastTickAt = SystemClock.uptimeMillis();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void downloadVideo(final String fullUrl, final long storyId, final int token) {
        final File out = new File(getCacheDir(), "story_" + storyId + ".mp4");
        if (out.exists() && out.length() > 0) {
            playVideo(out, token);
            return;
        }
        new Thread(() -> {
            File tmp = new File(getCacheDir(), "story_" + storyId + ".part");
            boolean ok = false;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(fullUrl).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                try (InputStream in = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        if (token != loadToken) throw new IOException("cancelled");
                        fos.write(buf, 0, n);
                    }
                }
                ok = tmp.renameTo(out);
            } catch (Exception ignored) {
                ok = false;
            } finally {
                if (conn != null) conn.disconnect();
            }
            final boolean done = ok;
            runOnUiThread(() -> {
                if (token != loadToken) {
                    tmp.delete();
                    return;
                }
                if (done) {
                    playVideo(out, token);
                } else {
                    tmp.delete();
                    loading.setVisibility(View.GONE);
                    Toast.makeText(this, R.string.story_load_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void playVideo(File file, int token) {
        if (token != loadToken) return;
        loading.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);
        videoView.setOnPreparedListener(mp -> {
            if (token != loadToken) return;
            isVideoNow = true;
            if (!paused) mp.start();
            lastTickAt = SystemClock.uptimeMillis();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        });
        videoView.setOnCompletionListener(mp -> {
            if (token == loadToken) {
                setSegment(1f);
                next();
            }
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            if (token == loadToken) {
                Toast.makeText(this, R.string.story_load_failed, Toast.LENGTH_SHORT).show();
                next();
            }
            return true;
        });
        videoView.setVideoPath(file.getAbsolutePath());
    }

    private void setSegment(float p) {
        if (index < 0 || index >= segFill.length) return;
        segFill[index].setScaleX(Math.max(0f, Math.min(1f, p)));
    }

    private void next() {
        handler.removeCallbacks(ticker);
        if (index + 1 >= ids.length) {
            finish();
        } else {
            showStory(index + 1);
        }
    }

    private void prev() {
        showStory(Math.max(0, index - 1));
    }

    private void applyPause() {
        boolean p = pausedByTouch || pausedByInput || pausedByDialog || pausedByLifecycle;
        if (p == paused) return;
        paused = p;
        if (isVideoNow) {
            try {
                if (p) videoView.pause(); else videoView.start();
            } catch (Exception ignored) { }
        }
        lastTickAt = SystemClock.uptimeMillis();
    }

    private String timeAgo(long ms) {
        long minutes = (System.currentTimeMillis() - ms) / 60000;
        if (minutes < 1) return getString(R.string.story_just_now);
        if (minutes < 60) return getString(R.string.story_min_ago, minutes);
        return getString(R.string.story_hours_ago, minutes / 60);
    }

    private void applyLikeUi() {
        if (btnLike != null) { btnLike.setText(liked ? "♥" : "♡"); btnLike.setTextColor(liked ? 0xFFFF4D6D : 0xFFFFFFFF); }
        if (own && txtViews != null) {
            txtViews.setText(IconManager.prefix(StoryViewerActivity.this, "eye", String.valueOf(viewers.size()), 0xFFFFFFFF, 18) + "     ♥ " + likesCount);
        }
    }

    private void loadLikeState() {
        final long storyId = ids[index];
        liked = false; likesCount = 0; applyLikeUi();
        api.economyGet("/stories/" + storyId + "/like", (ok, json, err) -> {
            if (!ok || json == null || storyId != ids[index]) return;
            liked = json.optBoolean("liked"); likesCount = json.optLong("likes");
            applyLikeUi();
        });
    }

    private void toggleLike() {
        final long storyId = ids[index];
        final boolean want = !liked;
        liked = want; likesCount += want ? 1 : -1; applyLikeUi();
        org.json.JSONObject b = new org.json.JSONObject();
        try { b.put("liked", want); } catch (Exception ignored) { }
        api.economyPost("/stories/" + storyId + "/like", b, (ok, json, err) -> {
            if (storyId != ids[index]) return;
            if (!ok || json == null) { liked = !want; likesCount += want ? -1 : 1; applyLikeUi(); return; }
            liked = json.optBoolean("liked"); likesCount = json.optLong("likes"); applyLikeUi();
        });
    }

    private void loadViewers() {
        final long storyId = ids[index];
        viewers.clear();
        if (txtViews != null) txtViews.setText(IconManager.prefix(StoryViewerActivity.this, "eye", "0", 0xFFFFFFFF, 18));
        api.getStoryViewers(storyId, (ok, list, error) -> {
            if (!ok || list == null || storyId != ids[index]) return;
            viewers.clear();
            viewers.addAll(list);
            applyLikeUi();
        });
    }

    private void showViewers() {
        if (viewers.isEmpty()) {
            Toast.makeText(this, R.string.story_no_views, Toast.LENGTH_SHORT).show();
            return;
        }
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault());
        CharSequence[] rows = new CharSequence[viewers.size()];
        for (int i = 0; i < rows.length; i++) {
            ApiClient.StoryViewer sv = viewers.get(i);
            rows[i] = sv.name + "   ·   " + fmt.format(sv.timeMs);
        }
        pausedByDialog = true;
        applyPause();
        new TDialog.Builder(this)
                .setTitle(getString(R.string.story_views_title, viewers.size()))
                .setItems(rows, null)
                .setOnDismissListener(d -> {
                    pausedByDialog = false;
                    applyPause();
                })
                .show();
    }

    private void sendReply() {
        if (inpReply == null) return;
        String text = inpReply.getText().toString().trim();
        if (text.isEmpty()) return;
        long storyId = ids[index];
        inpReply.setText("");
        inpReply.clearFocus();
        hideKeyboard();
        api.sendStoryReply(userId, text, storyId, (ok, v, error) -> {
            if (ok) {
                Toast.makeText(this, R.string.story_reply_sent, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, error != null ? error : getString(R.string.msg_send_failed),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteCurrent() {
        pausedByDialog = true;
        applyPause();
        new TDialog.Builder(this)
                .setMessage(R.string.story_delete_confirm)
                .setPositiveButton(R.string.story_delete_btn, (d, w) ->
                        api.deleteStory(ids[index], (ok, v, error) -> {
                            if (ok) {
                                Toast.makeText(this, R.string.story_deleted, Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(this, error != null ? error : getString(R.string.error_generic),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(d -> {
                    pausedByDialog = false;
                    applyPause();
                })
                .show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && inpReply != null) imm.hideSoftInputFromWindow(inpReply.getWindowToken(), 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pausedByLifecycle = true;
        applyPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pausedByLifecycle = false;
        applyPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        loadToken++;
        handler.removeCallbacksAndMessages(null);
        try { if (videoView != null) videoView.stopPlayback(); } catch (Exception ignored) { }
    }
}
