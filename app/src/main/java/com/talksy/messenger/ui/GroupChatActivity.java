package com.talksy.messenger.ui;

import com.talksy.messenger.data.IconManager;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.adapters.GroupMessageAdapter;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.model.GroupMessage;
import com.talksy.messenger.data.model.MediaAttachment;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.net.CryptoManager;
import com.talksy.messenger.net.FileOpener;
import com.talksy.messenger.net.MediaUploader;
import com.talksy.messenger.stickers.StickerPickerActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GroupChatActivity extends BaseActivity {
    @Override protected boolean listensForCalls() { return true; }

    private static final long POLL_INTERVAL_MS = 3000;

    private long groupId;
    private RecyclerView rvMessages;
    private GroupMessageAdapter adapter;
    private final List<GroupMessage> messages = new ArrayList<>();
    private final Set<Long> seenMessageIds = new HashSet<>();
    private ApiClient apiClient;
    private long lastTime = 0;
    private boolean canSend;
    private String myRole;
    private boolean isChannel;
    private org.json.JSONObject cachedPosts;
    private boolean commentsEnabled;
    private int statsTick = 0;
    private final Set<Long> viewsSent = new HashSet<>();
    private final Set<Long> viewsPending = new java.util.LinkedHashSet<>();
    private final Runnable viewsFlusher = new Runnable() {
        @Override public void run() {
            flushViews();
            if (isChannel && ++statsTick % 4 == 0) refreshChannelStats();
            pollHandler.postDelayed(this, 2500);
        }
    };
    private com.talksy.messenger.media.ComposerController composer;
    private long pendingSeq = 0;
    private boolean adminOnlyMode;
    private TextView btnSubscribe;
    private boolean pollInFlight = false;
    private byte[] groupKeyBytes;

    private View rowInput, txtReadonlyBanner;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            loadNew();
            pollHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private final ActivityResultLauncher<Intent> pickLocationLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() != RESULT_OK || r.getData() == null) return;
                Intent d = r.getData();
                try {
                    org.json.JSONObject o = new org.json.JSONObject().put("lat", d.getDoubleExtra("lat", 0)).put("lon", d.getDoubleExtra("lon", 0));
                    String title = d.getStringExtra("title"); if (title != null) o.put("title", title);
                    int minutes = d.getIntExtra("live_minutes", 0);
                    if (minutes > 0) {
                        String id = java.util.UUID.randomUUID().toString(); long until = System.currentTimeMillis() + minutes * 60_000L;
                        o.put("live", true).put("id", id).put("until", until);
                        com.talksy.messenger.sync.LiveLocation.start(this, id, until);
                    }
                    sendSpecial(com.talksy.messenger.data.SpecialMessage.encode(com.talksy.messenger.data.SpecialMessage.LOC, o));
                } catch (Exception ignored) {}
            });

    private final ActivityResultLauncher<Intent> pickContactLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() != RESULT_OK || r.getData() == null) return;
                try {
                    org.json.JSONObject o = new org.json.JSONObject().put("name", r.getData().getStringExtra("name")).put("phone", r.getData().getStringExtra("phone"));
                    sendSpecial(com.talksy.messenger.data.SpecialMessage.encode(com.talksy.messenger.data.SpecialMessage.CONTACT, o));
                } catch (Exception ignored) {}
            });

    private final ActivityResultLauncher<Intent> createArticleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() != RESULT_OK || r.getData() == null) return;
                String payload = r.getData().getStringExtra(ArticleEditorActivity.EXTRA_PAYLOAD);
                if (payload != null) sendSpecial(payload);
            });

    private final ActivityResultLauncher<String> pickAudioLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> { if (uri != null) onFilePicked(uri); });

    private final ActivityResultLauncher<String> pickFile =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) onFilePicked(uri);
            });

    private final ActivityResultLauncher<Intent> pickGallery =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null && r.getData().getData() != null) {
                    askBlurAndSend(r.getData().getData());
                }
            });

    private boolean sendNextBlurred = false;

    private void askBlurAndSend(Uri uri) {
        CharSequence[] opts = {getString(R.string.attach_send_plain), getString(R.string.send_blurred)};
        new com.talksy.messenger.ui.TDialog.Builder(this)
                .setItems(opts, (d, which) -> {
                    sendNextBlurred = (which == 1);
                    onFilePicked(uri);
                })
                .show();
    }

    private AttachSheet attachSheet;
    private com.talksy.messenger.stickers.StickerPanel stickerPanel;

    private final ActivityResultLauncher<String[]> mediaPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                if (attachSheet != null) attachSheet.onPermissionResult();
            });

    private final ActivityResultLauncher<Intent> createStickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (stickerPanel != null) stickerPanel.reloadPacks();
            });

    private void showAttachMenu() {
        if (stickerPanel != null && stickerPanel.isShown()) stickerPanel.hide(false);
        attachSheet = new AttachSheet(this, new AttachSheet.Host() {
            @Override public void onMediaPicked(Uri uri, String mime) { askBlurAndSend(uri); }
            @Override public void onMediaPickedMany(java.util.List<Uri> uris) {
                for (Uri u : uris) onFilePicked(u);
            }
            @Override public void onPickFile() { pickFile.launch("*/*"); }
            @Override public void onOpenWallet() { WalletLauncher.open(GroupChatActivity.this); }
            @Override public void onPickLocation() { pickLocationLauncher.launch(new Intent(GroupChatActivity.this, LocationPickerActivity.class)); }
            @Override public void onPickContact() { pickContactLauncher.launch(new Intent(GroupChatActivity.this, ContactPickerActivity.class)); }
            @Override public void onCreateArticle() { createArticleLauncher.launch(new Intent(GroupChatActivity.this, ArticleEditorActivity.class)); }
            @Override public void onPickAudio() { pickAudioLauncher.launch("audio/*"); }
            @Override public void onRequestMediaPermission() {
                mediaPermLauncher.launch(AttachSheet.requiredPermissions());
            }
        });
        attachSheet.show();
    }

    private void setupStickerPanel() {
        android.view.ViewGroup container = findViewById(R.id.sticker_panel_container);
        android.widget.EditText inp = findViewById(R.id.inp_message);
        stickerPanel = new com.talksy.messenger.stickers.StickerPanel(this, container, inp, apiClient,
                new com.talksy.messenger.stickers.StickerPanel.Host() {
                    @Override public void onStickerChosen(com.talksy.messenger.data.model.Sticker s) {
                        MediaAttachment media = new MediaAttachment();
                        media.url = s.mediaUrl;
                        media.type = s.mediaType;
                        apiClient.sendGroupMessage(groupId, "", media, (ok, msg, error) -> {
                            if (ok && msg != null) addIfNew(msg);
                        });
                    }
                    @Override public void onCreateSticker(long packId) {
                        Intent i = new Intent(GroupChatActivity.this, com.talksy.messenger.stickers.StickerCreateActivity.class);
                        if (packId > 0) i.putExtra("pack_id", packId);
                        createStickerLauncher.launch(i);
                    }
                });
        final androidx.activity.OnBackPressedCallback backCb = new androidx.activity.OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { stickerPanel.hide(false); }
        };
        getOnBackPressedDispatcher().addCallback(this, backCb);
        stickerPanel.setShownListener(backCb::setEnabled);
        findViewById(R.id.btn_sticker).setOnClickListener(v -> stickerPanel.toggle());
    }

    private final ActivityResultLauncher<Intent> pickSticker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Intent data = result.getData();
                MediaAttachment media = new MediaAttachment();
                media.url = data.getStringExtra(StickerPickerActivity.EXTRA_MEDIA_URL);
                media.type = data.getStringExtra(StickerPickerActivity.EXTRA_MEDIA_TYPE);
                apiClient.sendGroupMessage(groupId, "", media, (ok, msg, error) -> {
                    if (ok && msg != null) addIfNew(msg);
                });
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        apiClient = new ApiClient(this);
        groupId = getIntent().getLongExtra("group_id", -1);
        String groupName = getIntent().getStringExtra("group_name");
        String myRole = getIntent().getStringExtra("my_role");
        this.myRole = myRole;
        boolean adminOnly = getIntent().getBooleanExtra("admin_only_mode", false);
        canSend = !adminOnly || "owner".equals(myRole) || "admin".equals(myRole);

        TextView txtName = findViewById(R.id.txt_name);
        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnInfo = findViewById(R.id.btn_info);
        ImageButton btnSend = findViewById(R.id.btn_send);
        AnimUtils.pressScale(btnSend);
        ImageButton btnAttach = findViewById(R.id.btn_attach);
        EditText inpMessage = findViewById(R.id.inp_message);
        String savedDraft = com.talksy.messenger.data.Drafts.get(this, "g" + groupId);
        if (!savedDraft.isEmpty()) { inpMessage.setText(savedDraft); inpMessage.setSelection(savedDraft.length()); }
        com.talksy.messenger.data.MessageTextUtils.attachSpoilerComposerAction(inpMessage);
        rowInput = findViewById(R.id.row_input);
        composer = new com.talksy.messenger.media.ComposerController(this, (ViewGroup) rowInput, inpMessage, btnSend,
                findViewById(R.id.btn_record), findViewById(R.id.btn_lock_send),
                new View[]{btnAttach, inpMessage, findViewById(R.id.btn_sticker)},
                new com.talksy.messenger.media.ComposerController.Host() {
                    @Override public void onVoice(java.io.File f, long ms, String wave) { sendRecorded(f, "voice.m4a", "audio/mp4", "voice", ms, wave); }
                    @Override public void onRound(java.io.File f, long ms) { sendRecorded(f, "round.mp4", "video/mp4", "round", ms, null); }
                    @Override public boolean canRecord() { return GroupChatActivity.this.canSend && GroupChatActivity.this.myRole != null; }
                });
        txtReadonlyBanner = findViewById(R.id.txt_readonly_banner);

        txtName.setText(groupName);

        rowInput.setVisibility(canSend ? View.VISIBLE : View.GONE);
        txtReadonlyBanner.setVisibility(canSend ? View.GONE : View.VISIBLE);

        adminOnlyMode = adminOnly;
        btnSubscribe = findViewById(R.id.btn_subscribe);
        btnSubscribe.setOnClickListener(v -> onSubscribeClick());
        if (this.myRole == null) {
            canSend = false;
            rowInput.setVisibility(View.GONE);
            txtReadonlyBanner.setVisibility(View.GONE);
        }

        apiClient.getGroup(groupId, (ok, g, error) -> {
            if (ok && g != null) {
                isChannel = g.isChannel;
                showHeaderAvatar(g.avatarUrl);
                adminOnlyMode = g.adminOnlyMode;
                this.myRole = g.myRole;
                applyMembershipUi();
                applyChannelMode();
                refreshChannelStats();
            }
        });

        rvMessages = findViewById(R.id.rv_messages);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GroupMessageAdapter(messages, apiClient.getBaseUrl(), true, this::showReactionPicker, this::openMedia);
        rvMessages.setAdapter(adapter);
        loadStatsCache();
        rvMessages.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top < oldBottom - oldTop) scrollToBottom();
        });
        if (!AnimUtils.isEnabled(this)) rvMessages.setItemAnimator(null);

        btnBack.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        btnInfo.setOnClickListener(v -> {
            Intent i = new Intent(this, GroupInfoActivity.class);
            i.putExtra("group_id", groupId);
            i.putExtra("group_name", groupName);
            i.putExtra("my_role", this.myRole);
            i.putExtra("admin_only_mode", adminOnlyMode);
            startActivity(i);
            AnimUtils.forward(this);
        });
        btnAttach.setOnClickListener(v -> showAttachMenu());
        setupStickerPanel();

        btnSend.setOnClickListener(v -> {
            String text = inpMessage.getText().toString().trim();
            if (text.isEmpty()) return;

            boolean e2e = new Prefs(this).getBool(Prefs.KEY_E2E_ENABLED, false);
            if (!e2e) {
                inpMessage.setText("");
                queueOutgoing(text, null, null);
                return;
            }

            if (groupKeyBytes == null) return;
            inpMessage.setText("");

            new Thread(() -> {
                try {
                    CryptoManager.GroupEncrypted encrypted = CryptoManager.encryptGroup(text, groupKeyBytes);
                    runOnUiThread(() -> queueOutgoing(text, encrypted.encText, encrypted.iv));
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        inpMessage.setText(text);
                        Toast.makeText(this, getString(R.string.encryption_error) + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        });

        loadAll();
        apiClient.markGroupRead(groupId, null);
        loadGroupKey();
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.talksy.messenger.sync.LegacyOutbox.get(this).addListener(outboxListener);
        com.talksy.messenger.sync.LegacyOutbox.get(this).flush();
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        pollHandler.postDelayed(viewsFlusher, 2500);
        refreshChannelStats();
        apiClient.markGroupRead(groupId, null);
    }

    private void applyChannelMode() {
        adapter.setChannelMode(isChannel, commentsEnabled, this::openComments, this::onPostShown);
        adapter.notifyDataSetChanged();
    }

    private void refreshChannelStats() {
        if (!isChannel) return;
        apiClient.getChannelStats(groupId, (ok, st, err) -> {
            if (!ok || st == null) return;
            boolean modeChanged = commentsEnabled != st.commentsEnabled;
            commentsEnabled = st.commentsEnabled;
            saveStatsCache(st.commentsEnabled, st.posts);
            if (modeChanged) { applyChannelMode(); }
            for (int i = 0; i < messages.size(); i++) {
                GroupMessage m = messages.get(i);
                long[] a = st.posts.get(m.id);
                if (a != null && (m.views != a[0] || m.commentsCount != a[1])) {
                    m.views = a[0]; m.commentsCount = a[1];
                    if (!modeChanged) adapter.notifyItemChanged(i);
                }
            }
        });
    }

    private void onPostShown(GroupMessage post) {
        if (post.id > 0 && !viewsSent.contains(post.id)) viewsPending.add(post.id);
    }

    private void flushViews() {
        if (viewsPending.isEmpty() || !isChannel) return;
        final List<Long> batch = new ArrayList<>(viewsPending);
        viewsPending.clear(); viewsSent.addAll(batch);
        apiClient.postChannelViews(groupId, batch, (ok, v, err) -> {
            if (ok) refreshChannelStats();
            else { viewsSent.removeAll(batch); viewsPending.addAll(batch); }
        });
    }

    private void openComments(GroupMessage post) {
        Intent i = new Intent(this, CommentsActivity.class);
        i.putExtra(CommentsActivity.EXTRA_GROUP_ID, groupId); i.putExtra(CommentsActivity.EXTRA_POST_ID, post.id);
        i.putExtra(CommentsActivity.EXTRA_POST_TEXT, post.text); i.putExtra(CommentsActivity.EXTRA_CHANNEL, ((TextView) findViewById(R.id.txt_name)).getText().toString());
        startActivity(i);
        AnimUtils.forward(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        com.talksy.messenger.sync.LegacyOutbox.get(this).removeListener(outboxListener);
        flushViews();
        pollHandler.removeCallbacks(pollRunnable);
        pollHandler.removeCallbacks(viewsFlusher);
        com.talksy.messenger.data.Drafts.set(this, "g" + groupId,
                ((android.widget.EditText) findViewById(R.id.inp_message)).getText().toString());
    }

    private void applyMembershipUi() {
        boolean manage = "owner".equals(myRole) || "admin".equals(myRole);
        if (isChannel) {
            canSend = manage;
            rowInput.setVisibility(manage ? View.VISIBLE : View.GONE);
            txtReadonlyBanner.setVisibility(View.GONE);
            if (myRole == null) {
                btnSubscribe.setText(R.string.channel_subscribe);
                btnSubscribe.setVisibility(View.VISIBLE);
            } else if (!manage) {
                btnSubscribe.setText(R.string.channel_unsubscribe);
                btnSubscribe.setVisibility(View.VISIBLE);
            } else {
                btnSubscribe.setVisibility(View.GONE);
            }
        } else {
            btnSubscribe.setVisibility(View.GONE);
            canSend = myRole != null && (!adminOnlyMode || manage);
            rowInput.setVisibility(canSend ? View.VISIBLE : View.GONE);
            txtReadonlyBanner.setVisibility(myRole != null && !canSend ? View.VISIBLE : View.GONE);
        }
    }

    private void onSubscribeClick() {
        btnSubscribe.setEnabled(false);
        if (myRole == null) {
            apiClient.joinGroup(groupId, (ok, g, error) -> {
                btnSubscribe.setEnabled(true);
                if (ok && g != null) {
                    myRole = g.myRole;
                    applyMembershipUi();
                    loadGroupKey();
                    apiClient.markGroupRead(groupId, null);
                    Toast.makeText(this, R.string.channel_subscribed, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, error != null ? error : getString(R.string.error_generic),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            new com.talksy.messenger.ui.TDialog.Builder(this)
                    .setMessage(R.string.channel_unsubscribe_confirm)
                    .setPositiveButton(R.string.channel_unsubscribe, (d, w) ->
                            apiClient.leaveGroup(groupId, (ok, v, error) -> {
                                if (ok) {
                                    myRole = null;
                                    applyMembershipUi();
                                    Toast.makeText(this, R.string.channel_unsubscribed, Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, error != null ? error : getString(R.string.error_generic),
                                            Toast.LENGTH_SHORT).show();
                                }
                            }))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setOnDismissListener(d -> btnSubscribe.setEnabled(true))
                    .show();
        }
    }

    private void loadGroupKey() {
        apiClient.getMyGroupKey(groupId, (ok, encryptedKey, error) -> {
            if (!ok) return;
            if (encryptedKey == null) {

                pollHandler.postDelayed(this::loadGroupKey, 5000);
                return;
            }
            new Thread(() -> {
                try {
                    groupKeyBytes = CryptoManager.unwrapGroupKey(encryptedKey);
                    runOnUiThread(() -> {
                        rowInput.setVisibility(canSend ? View.VISIBLE : View.GONE);
                        loadAll();
                        grantKeysToPendingMembers();
                    });
                } catch (Exception e) {
                }
            }).start();
        });
    }

    private void grantKeysToPendingMembers() {
        if (groupKeyBytes == null) return;
        apiClient.getPendingGroupKeyMembers(groupId, (ok, pending, error) -> {
            if (!ok || pending == null || pending.isEmpty()) return;
            new Thread(() -> {
                for (ApiClient.PendingKeyMember member : pending) {
                    try {
                        String wrapped = CryptoManager.wrapGroupKeyForMember(groupKeyBytes, member.publicKey);
                        apiClient.grantGroupKey(groupId, member.id, wrapped, (ok2, d, e) -> {});
                    } catch (Exception ignored) {}
                }
            }).start();
        });
    }

    private void queueOutgoing(String plain, String enc, String iv) {
        com.talksy.messenger.sync.LocalDb local = com.talksy.messenger.sync.LocalDb.get(this);
        String key = com.talksy.messenger.sync.LocalDb.groupKey(groupId);
        if (!local.hasChat(key)) {
            local.ensureChat(key, getIntent().getStringExtra("group_name"), "#06B6D4", null, false);
        }
        com.talksy.messenger.data.model.Message m = local.addPendingOutgoing(key, plain, enc, null, null, iv, 0, null);
        GroupMessage gm = new GroupMessage();
        gm.id = m.id; gm.localId = m.localId; gm.outgoing = true; gm.text = plain; gm.time = m.time;
        gm.encText = enc; gm.iv = iv; gm.syncStatus = m.syncStatus;
        seenMessageIds.add(gm.id);
        messages.add(gm);
        adapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();
        com.talksy.messenger.sync.LegacyOutbox.get(this).flush();
    }

    private int indexOfLocal(long localId) {
        for (int i = 0; i < messages.size(); i++) if (localId != 0 && messages.get(i).localId == localId) return i;
        return -1;
    }

    private final com.talksy.messenger.sync.LegacyOutbox.Listener outboxListener = new com.talksy.messenger.sync.LegacyOutbox.Listener() {
        @Override public void onDelivered(long localId, long serverId, long timeMs) {
            int idx = indexOfLocal(localId);
            if (idx < 0) return;
            GroupMessage m = messages.get(idx);
            seenMessageIds.remove(m.id);
            if (seenMessageIds.contains(serverId)) {
                messages.remove(idx);
                adapter.notifyItemRemoved(idx);
                return;
            }
            m.id = serverId;
            m.syncStatus = com.talksy.messenger.sync.SyncStatus.SYNCED;
            if (timeMs > 0) { m.time = timeMs; }
            seenMessageIds.add(serverId);
            adapter.notifyItemChanged(idx);
        }
        @Override public void onFailed(long localId) {
            int idx = indexOfLocal(localId);
            if (idx < 0) return;
            messages.get(idx).syncStatus = com.talksy.messenger.sync.SyncStatus.FAILED;
            adapter.notifyItemChanged(idx);
        }
    };

    private void appendUnsentLocal() {
        try {
            for (GroupMessage m : com.talksy.messenger.sync.LocalDb.get(this).getGroupMessages(com.talksy.messenger.sync.LocalDb.groupKey(groupId))) {
                if (m.id < 0 && !seenMessageIds.contains(m.id)) { seenMessageIds.add(m.id); messages.add(m); }
            }
        } catch (Exception ignored) {}
    }

    private void loadAll() {

        try {
            com.talksy.messenger.sync.LocalDb local = com.talksy.messenger.sync.LocalDb.get(this);
            List<GroupMessage> cached = local.getGroupMessages(com.talksy.messenger.sync.LocalDb.groupKey(groupId));
            if (cached != null && !cached.isEmpty()) {
                messages.clear();
                seenMessageIds.clear();
                for (GroupMessage m : cached) {
                    seenMessageIds.add(m.id);
                    if (m.id > 0) lastTime = Math.max(lastTime, m.time);
                    if (!m.deletedForEveryone) messages.add(m);
                }
                adapter.notifyDataSetChanged();
                applyCachedCounts();
                decryptPending();
                scrollToBottom();
            }
        } catch (Exception ignored) {}

        apiClient.getGroupMessages(groupId, 0, (ok, list, error) -> {
            if (!ok || list == null) return;
            try { com.talksy.messenger.sync.LocalDb.get(this).seedLegacyGroupMessages(com.talksy.messenger.sync.LocalDb.groupKey(groupId), list); } catch (Exception ignored) {}
            messages.clear();
            seenMessageIds.clear();
            for (GroupMessage m : list) {
                seenMessageIds.add(m.id);
                lastTime = Math.max(lastTime, m.time);
                if (m.deletedForEveryone) continue;
                messages.add(m);
            }
            appendUnsentLocal();
            adapter.notifyDataSetChanged();
            applyCachedCounts();
            decryptPending();
            scrollToBottom();
        });
    }

    private void loadNew() {
        if (pollInFlight || groupKeyBytes == null) return;
        pollInFlight = true;
        apiClient.getGroupMessages(groupId, lastTime, (ok, list, error) -> {
            pollInFlight = false;
            if (!ok || list == null) return;
            try { com.talksy.messenger.sync.LocalDb.get(this).seedLegacyGroupMessages(com.talksy.messenger.sync.LocalDb.groupKey(groupId), list); } catch (Exception ignored) {}
            for (GroupMessage m : list) addIfNew(m);
        });
    }

    private void addIfNew(GroupMessage m) {
        if (seenMessageIds.contains(m.id)) return;
        seenMessageIds.add(m.id);
        if (m.deletedForEveryone) return;
        messages.add(m);
        lastTime = Math.max(lastTime, m.time);
        adapter.notifyItemInserted(messages.size() - 1);
        decryptPending();
        scrollToBottom();
        if (!m.outgoing) apiClient.markGroupRead(groupId, null);
    }

    private void scrollToBottom() {
        if (messages.isEmpty()) return;
        rvMessages.post(() -> rvMessages.scrollToPosition(messages.size() - 1));
    }

    private void decryptPending() {
        if (groupKeyBytes == null) return;
        List<GroupMessage> toDecrypt = new ArrayList<>();
        for (GroupMessage m : messages) {
            if (m.encText != null && !m.encText.isEmpty() && (m.text == null || m.text.isEmpty())) {
                toDecrypt.add(m);
            }
        }
        if (toDecrypt.isEmpty()) return;

        new Thread(() -> {
            for (GroupMessage m : toDecrypt) {
                try {
                    m.text = CryptoManager.decryptGroup(m.encText, m.iv, groupKeyBytes);
                } catch (Exception e) {
                    m.text = "";
                }
            }
            runOnUiThread(() -> {
                adapter.notifyDataSetChanged();
                scrollToBottom();
            });
        }).start();
    }

    private void sendSpecial(String payload) {

        boolean e2e = new Prefs(this).getBool(Prefs.KEY_E2E_ENABLED, false);
        if (!e2e) { queueOutgoing(payload, null, null); return; }
        if (groupKeyBytes == null) { Toast.makeText(this, R.string.encryption_error, Toast.LENGTH_SHORT).show(); return; }
        new Thread(() -> {
            try {
                CryptoManager.GroupEncrypted enc = CryptoManager.encryptGroup(payload, groupKeyBytes);
                runOnUiThread(() -> queueOutgoing(payload, enc.encText, enc.iv));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.encryption_error) + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void onFilePicked(Uri uri) {
        if (!canSend) {
            Toast.makeText(this, R.string.group_admin_only_send, Toast.LENGTH_SHORT).show();
            return;
        }
        final MediaUploader.FileInfo info = MediaUploader.describe(this, uri);
        if (sendNextBlurred) {
            sendNextBlurred = false;
            if (info.name != null && !info.name.startsWith(MediaAttachment.SPOILER_PREFIX)) {
                info.name = MediaAttachment.SPOILER_PREFIX + info.name;
            }
        }
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
                    apiClient.sendGroupMessage(groupId, "", media, (ok2, msg, error2) -> {
                        dlg.dismiss();
                        if (ok2 && msg != null) {
                            addIfNew(msg);
                        } else {
                            Toast.makeText(this, error2 != null ? error2 : getString(R.string.msg_send_failed),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                });
    }

    private void sendRecorded(java.io.File f, String name, String mime, String kind, long ms, String wave) {
        final GroupMessage pending = new GroupMessage();
        pending.id = --pendingSeq;
        pending.outgoing = true;
        pending.text = "";
        pending.time = System.currentTimeMillis();
        pending.syncStatus = com.talksy.messenger.sync.SyncStatus.PENDING;
        MediaAttachment local = new MediaAttachment();
        local.type = kind; local.filename = name; local.size = f.length(); local.durationMs = ms; local.waveform = wave;
        local.localPath = f.getAbsolutePath();
        pending.media = local;
        seenMessageIds.add(pending.id);
        messages.add(pending);
        adapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();

        final Runnable drop = () -> {
            int idx = messages.indexOf(pending);
            if (idx >= 0) { messages.remove(idx); adapter.notifyItemRemoved(idx); }
            seenMessageIds.remove(pending.id);
        };
        apiClient.uploadMediaStream(android.net.Uri.fromFile(f), name, mime, f.length(), (sent, total) -> { }, new java.util.concurrent.atomic.AtomicBoolean(false),
                (ok, media, error) -> {
                    if (!ok || media == null) {
                        drop.run();
                        Toast.makeText(this, error != null ? error : getString(R.string.file_upload_failed), Toast.LENGTH_LONG).show();
                        return;
                    }
                    media.type = kind; media.durationMs = ms; media.waveform = wave;
                    apiClient.sendGroupMessage(groupId, "", media, (ok2, msg, error2) -> {
                        drop.run();
                        if (ok2 && msg != null) {
                            addIfNew(msg);
                        } else {
                            Toast.makeText(this, error2 != null ? error2 : getString(R.string.msg_send_failed), Toast.LENGTH_SHORT).show();
                        }
                    });
                });
    }

    private void openMedia(MediaAttachment media) {
        String fullUrl = apiClient.getBaseUrl() + media.url;
        if (media.isImage()) {
            Intent i = new Intent(this, FullscreenImageActivity.class);
            i.putExtra("image_url", fullUrl);
            startActivity(i);
        } else if (media.isVideo()) {
            Intent i = new Intent(this, VideoPlayerActivity.class);
            i.putExtra(VideoPlayerActivity.EXTRA_URL, fullUrl);
            startActivity(i);
            AnimUtils.forward(this);
        } else {
            FileOpener.downloadAndOpen(this, fullUrl, media.filename);
        }
    }

    private static final String[] REACTION_EMOJIS = {"👍", "❤️", "😂", "😮", "😢", "🔥"};

    private void showReactionPicker(GroupMessage message) {
        boolean canModerate = "owner".equals(myRole) || "admin".equals(myRole);

        if (message.deletedForEveryone) {
            new com.talksy.messenger.ui.TDialog.Builder(this)
                    .setItems(new CharSequence[]{getString(R.string.delete_for_me)}, (d, w) -> deleteMessage(message, false))
                    .show();
            return;
        }

        List<CharSequence> items = new ArrayList<>();
        for (String e : REACTION_EMOJIS) items.add(e);
        items.add(getString(R.string.delete_for_me));
        boolean canDeleteForEveryone = message.outgoing || canModerate;
        if (canDeleteForEveryone) items.add(getString(R.string.delete_for_everyone));
        final String favId = "g" + groupId + ":" + message.id;
        final int favIndex = items.size();
        items.add(IconManager.prefix(this, "save", getString(com.talksy.messenger.data.Favorites.has(this, favId)
                ? R.string.menu_unfavorite : R.string.menu_favorite)));

        new com.talksy.messenger.ui.TDialog.Builder(this)
                .setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
                    if (which < REACTION_EMOJIS.length) {
                        String emoji = REACTION_EMOJIS[which];
                        apiClient.toggleReaction("group", message.id, emoji, (ok, reactions, error) -> {
                            if (ok && reactions != null) {
                                message.reactions = reactions;
                                adapter.notifyDataSetChanged();
                            } else if (error != null) {
                                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else if (which == favIndex) {
                        com.talksy.messenger.data.Favorites.Item fi = new com.talksy.messenger.data.Favorites.Item();
                        fi.id = favId;
                        fi.from = message.outgoing ? getString(R.string.chat_you_label)
                                : (message.senderName == null ? "" : message.senderName);
                        String gn = getIntent().getStringExtra("group_name");
                        if (gn != null && !gn.isEmpty()) fi.from = fi.from.isEmpty() ? gn : fi.from + " · " + gn;
                        fi.text = message.text == null ? "" : message.text;
                        fi.time = message.time;
                        if (message.media != null) { fi.mediaUrl = message.media.url; fi.mediaName = message.media.filename; }
                        boolean added = com.talksy.messenger.data.Favorites.toggle(this, fi);
                        Toast.makeText(this, added ? R.string.favorite_added : R.string.favorite_removed, Toast.LENGTH_SHORT).show();
                    } else if (which == REACTION_EMOJIS.length) {
                        deleteMessage(message, false);
                    } else {
                        confirmDeleteForEveryone(message);
                    }
                })
                .show();
    }

    private void confirmDeleteForEveryone(GroupMessage message) {
        new com.talksy.messenger.ui.TDialog.Builder(this)
                .setMessage(R.string.delete_for_everyone_confirm)
                .setPositiveButton(R.string.delete_for_everyone, (d, w) -> deleteMessage(message, true))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteMessage(GroupMessage message, boolean forEveryone) {
        apiClient.deleteGroupMessage(groupId, message.id, forEveryone, (ok, v, error) -> {
            if (!ok) {
                Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
                return;
            }

            messages.remove(message);
            adapter.notifyDataSetChanged();
        });
    }

    private void showHeaderAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) return;
        final android.widget.ImageView iv = findViewById(R.id.img_group_avatar);
        if (iv == null) return;
        com.talksy.messenger.net.AvatarLoader.load(apiClient.getBaseUrl() + avatarUrl, bmp -> {
            iv.setBackground(null);
            iv.setColorFilter(null);
            iv.setImageTintList(null);
            iv.setImageBitmap(bmp);
        });
    }

    private android.content.SharedPreferences statsPrefs() { return getSharedPreferences("channel_stats_cache", MODE_PRIVATE); }

    private void loadStatsCache() {
        try {
            String raw = statsPrefs().getString("g" + groupId, null);
            if (raw == null) return;
            org.json.JSONObject o = new org.json.JSONObject(raw);
            if (o.optBoolean("ch")) isChannel = true;
            commentsEnabled = o.optBoolean("ce");
            cachedPosts = o.optJSONObject("posts");
            applyChannelMode();
        } catch (Exception ignored) { }
    }

    private void saveStatsCache(boolean enabled, java.util.Map<Long, long[]> posts) {
        try {
            org.json.JSONObject p = new org.json.JSONObject();
            for (java.util.Map.Entry<Long, long[]> e : posts.entrySet()) p.put(String.valueOf(e.getKey()), new org.json.JSONArray().put(e.getValue()[0]).put(e.getValue()[1]));
            statsPrefs().edit().putString("g" + groupId, new org.json.JSONObject().put("ch", true).put("ce", enabled).put("posts", p).toString()).apply();
            cachedPosts = p;
        } catch (Exception ignored) { }
    }

    private void applyCachedCounts() {
        if (cachedPosts == null || !isChannel) return;
        boolean changed = false;
        for (GroupMessage m : messages) {
            org.json.JSONArray a = cachedPosts.optJSONArray(String.valueOf(m.id));
            if (a != null && m.views == 0 && m.commentsCount == 0) { m.views = a.optLong(0); m.commentsCount = a.optLong(1); changed = true; }
        }
        if (changed) adapter.notifyDataSetChanged();
    }
}
