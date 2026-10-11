package com.talksy.messenger.ui;

import com.talksy.messenger.data.IconManager;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.R;
import com.talksy.messenger.adapters.MessageAdapter;
import com.talksy.messenger.data.model.MediaAttachment;
import com.talksy.messenger.data.model.Message;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.net.AvatarLoader;
import com.talksy.messenger.net.CryptoManager;
import com.talksy.messenger.net.FileOpener;
import com.talksy.messenger.net.MediaUploader;
import com.talksy.messenger.stickers.StickerPickerActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatActivity extends BaseActivity {
    @Override protected boolean listensForCalls() { return true; }

    private static final long POLL_INTERVAL_MS = 2000;
    private long lastTypingSentAt = 0;
    private long peerLastReadMs = 0;
    private Message replyTo;
    private Message editingMessage;
    private long pinnedId = 0;

    private long peerId;
    private RecyclerView rvMessages;
    private MessageAdapter adapter;

    public static volatile long openPeerId = -1;

    private final List<Message> messages = new ArrayList<>();
    private final Set<Long> seenMessageIds = new HashSet<>();
    private ApiClient apiClient;
    private long lastTime = 0;
    private boolean pollInFlight = false;
    private String peerPublicKey;
    private boolean peerIsBot;
    private boolean peerHasMiniApp;
    private boolean peerCanMessage = true;
    private com.talksy.messenger.data.model.User peerUser;
    private com.talksy.messenger.data.model.BotKeyboard peerKeyboard;

    private boolean botStarted;
    private boolean imeOpen;
    private com.talksy.messenger.media.ComposerController composer;

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
            @Override public void onOpenWallet() { showWalletChoice(); }
            @Override public void onPickLocation() { pickLocationLauncher.launch(new Intent(ChatActivity.this, LocationPickerActivity.class)); }
            @Override public void onPickContact() { pickContactLauncher.launch(new Intent(ChatActivity.this, ContactPickerActivity.class)); }
            @Override public void onCreateArticle() { createArticleLauncher.launch(new Intent(ChatActivity.this, ArticleEditorActivity.class)); }
            @Override public void onPickAudio() { pickAudioLauncher.launch("audio/*"); }
            @Override public void onRequestMediaPermission() {
                mediaPermLauncher.launch(AttachSheet.requiredPermissions());
            }
            @Override public boolean supportsGift() { return true; }
            @Override public void onPickGift() {

                GiftFlow.openShop(ChatActivity.this, peerId, getIntent().getStringExtra("peer_name"));
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
                        apiClient.sendMessage(peerId, "", media, (ok, msg, error) -> {
                            if (ok && msg != null) addIfNew(msg);
                        });
                    }
                    @Override public void onCreateSticker(long packId) {
                        Intent i = new Intent(ChatActivity.this, com.talksy.messenger.stickers.StickerCreateActivity.class);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        apiClient = new ApiClient(this);
        peerId = getIntent().getLongExtra("peer_id", -1);
        String peerName = getIntent().getStringExtra("peer_name");
        String peerColor = getIntent().getStringExtra("peer_color");

        com.talksy.messenger.data.ChatWallpaper.apply(
                findViewById(R.id.chat_root),
                new com.talksy.messenger.data.Prefs(this).get(com.talksy.messenger.data.Prefs.KEY_CHAT_WALLPAPER, com.talksy.messenger.data.ChatWallpaper.DEFAULT));

        TextView txtName = findViewById(R.id.txt_name);
        TextView txtAvatar = findViewById(R.id.txt_avatar);
        android.widget.ImageView imgAvatar = findViewById(R.id.img_avatar);
        View.OnClickListener openProfile = v -> {
            Intent pi = new Intent(this, UserProfileActivity.class);
            pi.putExtra("user_id", peerId);
            startActivity(pi);
            AnimUtils.forward(this);
        };
        txtName.setOnClickListener(openProfile);
        txtAvatar.setOnClickListener(openProfile);
        imgAvatar.setOnClickListener(openProfile);
        String peerAvatarUrl = getIntent().getStringExtra("peer_avatar_url");
        if (peerAvatarUrl != null && !peerAvatarUrl.isEmpty()) {
            AvatarLoader.load(apiClient.getBaseUrl() + peerAvatarUrl, bmp -> {
                imgAvatar.setImageBitmap(bmp);
                imgAvatar.setVisibility(View.VISIBLE);
            });
        }
        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnSend = findViewById(R.id.btn_send);
        AnimUtils.pressScale(btnSend);
        ImageButton btnAttach = findViewById(R.id.btn_attach);
        EditText inpMessage = findViewById(R.id.inp_message);
        String savedDraft = com.talksy.messenger.data.Drafts.get(this, "u" + peerId);
        if (!savedDraft.isEmpty()) { inpMessage.setText(savedDraft); inpMessage.setSelection(savedDraft.length()); }
        String presetDraft = getIntent().getStringExtra("draft_text");
        if (savedDraft.isEmpty() && presetDraft != null && !presetDraft.isEmpty()) { inpMessage.setText(presetDraft); inpMessage.setSelection(presetDraft.length()); }
        com.talksy.messenger.data.MessageTextUtils.attachSpoilerComposerAction(inpMessage);

        composer = new com.talksy.messenger.media.ComposerController(this, findViewById(R.id.input_row), inpMessage, btnSend,
                findViewById(R.id.btn_record), findViewById(R.id.btn_lock_send),
                new View[]{btnAttach, findViewById(R.id.btn_mini_app), inpMessage, findViewById(R.id.btn_sticker)},
                new com.talksy.messenger.media.ComposerController.Host() {
                    @Override public void onVoice(java.io.File f, long ms, String wave) { sendRecorded(f, "voice.m4a", "audio/mp4", "voice", ms, wave); }
                    @Override public void onRound(java.io.File f, long ms) { sendRecorded(f, "round.mp4", "video/mp4", "round", ms, null); }
                    @Override public boolean canRecord() { return editingMessage == null && (peerIsBot || peerCanMessage); }
                });
        inpMessage.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                if (s.length() == 0) return;
                long now = System.currentTimeMillis();
                if (now - lastTypingSentAt > 3000) {
                    lastTypingSentAt = now;
                    apiClient.sendTyping(peerId);
                }
            }
        });

        findViewById(R.id.btn_reply_close).setOnClickListener(v -> {
            if (editingMessage != null) inpMessage.setText("");
            clearComposerBar();
        });
        findViewById(R.id.pin_bar).setOnClickListener(v -> scrollToMessage(pinnedId));

        ImageButton btnCall = findViewById(R.id.btn_call);
        AnimUtils.pressScale(btnCall);
        btnCall.setOnClickListener(v -> {
            Intent i = new Intent(this, com.talksy.messenger.ui.CallActivity.class);
            i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_MODE, com.talksy.messenger.ui.CallActivity.MODE_OUTGOING);
            i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_PEER_ID, peerId);
            i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_PEER_NAME, getIntent().getStringExtra("peer_name"));
            i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_PEER_COLOR, getIntent().getStringExtra("peer_color"));
            i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_VIDEO, false);
            startActivity(i);
        });

        ImageButton btnVideoCall = findViewById(R.id.btn_video_call);
        AnimUtils.pressScale(btnVideoCall);
        btnVideoCall.setOnClickListener(v -> {
            Intent i = new Intent(this, com.talksy.messenger.ui.CallActivity.class);
            i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_MODE, com.talksy.messenger.ui.CallActivity.MODE_OUTGOING);
            i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_PEER_ID, peerId);
            i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_PEER_NAME, getIntent().getStringExtra("peer_name"));
            i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_PEER_COLOR, getIntent().getStringExtra("peer_color"));
            i.putExtra(com.talksy.messenger.ui.CallActivity.EXTRA_VIDEO, true);
            startActivity(i);
        });

        txtName.setText(peerName);
        TextView txtLastSeen = findViewById(R.id.txt_last_seen);
        ImageButton btnMiniAppEarly = findViewById(R.id.btn_mini_app);
        com.talksy.messenger.data.LiquidGlass.applyPanel(findViewById(R.id.top_bar), 0f);
        com.talksy.messenger.data.LiquidGlass.applyInputCapsule(findViewById(R.id.input_row));
        apiClient.getUserProfile(peerId, (ok, user, error) -> {
            if (ok && user != null) {
                peerUser = user;
                txtLastSeen.setText(user.isBot ? user.botSubtitle(this) : user.lastSeenText(this));
                peerPublicKey = user.publicKey;
                peerIsBot = user.isBot;
                peerCanMessage = user.canMessage;
                peerHasMiniApp = user.isBot && user.hasMiniApp;
                if (user.isBot) {
                    findViewById(R.id.btn_call).setVisibility(android.view.View.GONE);
                    findViewById(R.id.btn_video_call).setVisibility(android.view.View.GONE);
                    peerKeyboard = com.talksy.messenger.data.model.BotKeyboard.parse(user.botKeyboard);
                    renderBotKeyboard();
                }
                btnMiniAppEarly.setVisibility(peerHasMiniApp ? android.view.View.VISIBLE : android.view.View.GONE);
                btnMiniAppEarly.setOnClickListener(v -> openBotMiniApp());
                refreshComposerState();
            }
        });
        txtAvatar.setText(initials(peerName));
        setupBotMenuToggle();
        try { txtAvatar.getBackground().setTint(Color.parseColor(peerColor)); } catch (Exception ignored) {}

        rvMessages = findViewById(R.id.rv_messages);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter(messages, apiClient.getBaseUrl(), this::showMessageMenu, this::openMedia);
        adapter.setOnKeyboardButtonClick(this::onKeyboardButtonTap);
        rvMessages.setAdapter(adapter);
        setupSwipeToReply();

        rvMessages.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top < oldBottom - oldTop) scrollToBottom();
        });
        if (!AnimUtils.isEnabled(this)) rvMessages.setItemAnimator(null);

        btnBack.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        btnAttach.setOnClickListener(v -> showAttachMenu());
        setupStickerPanel();

        btnSend.setOnLongClickListener(v -> {
            String text = inpMessage.getText().toString().trim();
            showSendOptionsMenu(text, inpMessage, btnSend);
            return true;
        });
        btnSend.setOnClickListener(v -> {
            String text = inpMessage.getText().toString().trim();
            if (text.isEmpty()) return;
            if (editingMessage != null) { submitEdit(text, inpMessage, btnSend); return; }

            boolean e2e = new Prefs(this).getBool(Prefs.KEY_E2E_ENABLED, false);
            if (!e2e) {

                sendPlain(text, inpMessage, btnSend);
                return;
            }

            btnSend.setEnabled(false);
            apiClient.getUserProfile(peerId, (ok, user, error) -> {
                btnSend.setEnabled(true);
                if (ok && user != null && user.publicKey != null && !user.publicKey.isEmpty()) {
                    peerPublicKey = user.publicKey;
                    sendText(text, inpMessage, btnSend);
                } else {
                    Toast.makeText(this, getString(R.string.peer_no_key_yet), Toast.LENGTH_LONG).show();
                }
            });
        });

        loadAll();
        apiClient.markChatRead(peerId, null);
    }

    private void showSendOptionsMenu(String text, EditText inpMessage, ImageButton btnSend) {
        java.util.List<String> items = new java.util.ArrayList<>();
        if (!text.isEmpty()) items.add(getString(R.string.send_schedule));
        items.add(getString(R.string.send_disappearing));
        new TDialog.Builder(this)
                .setItems(items.toArray(new CharSequence[0]), (d, which) -> {
                    String picked = items.get(which);
                    if (picked.equals(getString(R.string.send_schedule))) showScheduleDialog(text, inpMessage, btnSend);
                    else showDisappearingDialog();
                })
                .show();
    }

    private void showScheduleDialog(String text, EditText inpMessage, ImageButton btnSend) {
        PremiumGate.require(this, apiClient, "Отложенные сообщения", () -> showScheduleDialogPremium(text, inpMessage, btnSend));
    }

    private void showScheduleDialogPremium(String text, EditText inpMessage, ImageButton btnSend) {
        final java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.MINUTE, 5);
        new android.app.DatePickerDialog(this, (dv, y, m, day) -> {
            cal.set(y, m, day);
            new android.app.TimePickerDialog(this, (tv, h, min) -> {
                cal.set(java.util.Calendar.HOUR_OF_DAY, h);
                cal.set(java.util.Calendar.MINUTE, min);
                cal.set(java.util.Calendar.SECOND, 0);
                long sendAt = cal.getTimeInMillis();
                if (sendAt <= System.currentTimeMillis() + 5000) {
                    Toast.makeText(this, R.string.send_schedule_past, Toast.LENGTH_SHORT).show();
                    return;
                }
                apiClient.scheduleMessage(peerId, text, sendAt, (ok, id, err) -> {
                    if (ok) {
                        inpMessage.setText("");
                        Toast.makeText(this, R.string.send_schedule_done, Toast.LENGTH_SHORT).show();
                    } else if (PremiumGate.isPremiumError(err)) {
                        PremiumGate.show(this, "Отложенные сообщения");
                    } else {
                        Toast.makeText(this, err != null ? err : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                    }
                });
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show();
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    private static final int[] TTL_VALUES = {0, 3600, 86400, 604800, 2592000};

    private void showDisappearingDialog() {
        apiClient.getChatTtl(peerId, (ok, current, err) -> {
            int cur = ok && current != null ? current : 0;
            int checked = 0;
            for (int i = 0; i < TTL_VALUES.length; i++) if (TTL_VALUES[i] == cur) checked = i;
            CharSequence[] labels = {
                    getString(R.string.ttl_off), getString(R.string.ttl_hour),
                    getString(R.string.ttl_day), getString(R.string.ttl_week), getString(R.string.ttl_month)
            };
            new TDialog.Builder(this)
                    .setTitle(R.string.send_disappearing)
                    .setSingleChoiceItems(labels, checked, (d, which) -> {
                        apiClient.setChatTtl(peerId, TTL_VALUES[which], (ok2, v, e2) -> {
                            d.dismiss();
                            if (!ok2 && PremiumGate.isPremiumError(e2)) { PremiumGate.show(this, "Исчезающие сообщения"); return; }
                            Toast.makeText(this, ok2 ? R.string.ttl_saved : R.string.error_generic, Toast.LENGTH_SHORT).show();
                        });
                    })
                    .show();
        });
    }

    private void sendPlain(String text, EditText inpMessage, ImageButton btnSend) {
        inpMessage.setText("");
        queueOutgoing(text, null, null, null, null);
    }

    private void sendText(String text, EditText inpMessage, ImageButton btnSend) {
        inpMessage.setText("");
        new Thread(() -> {
            try {
                CryptoManager.DirectEncrypted encrypted = CryptoManager.encryptDirect(text, peerPublicKey);
                runOnUiThread(() -> queueOutgoing(text, encrypted.encText, encrypted.encKeyRecipient, encrypted.encKeySender, encrypted.iv));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    inpMessage.setText(text);
                    Toast.makeText(this, getString(R.string.encryption_error) + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void queueOutgoing(String plain, String enc, String keyRecipient, String keySender, String iv) {
        com.talksy.messenger.sync.LocalDb local = com.talksy.messenger.sync.LocalDb.get(this);
        String key = com.talksy.messenger.sync.LocalDb.userKey(peerId);
        if (!local.hasChat(key)) {
            local.ensureChat(key, getIntent().getStringExtra("peer_name"), getIntent().getStringExtra("peer_color"),
                    getIntent().getStringExtra("peer_avatar_url"), peerIsBot);
        }
        long reply = takeReplyId();
        Message m = local.addPendingOutgoing(key, plain, enc, keyRecipient, keySender, iv, reply, null);
        m.text = plain;
        seenMessageIds.add(m.id);
        messages.add(m);
        adapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();
        com.talksy.messenger.sync.LegacyOutbox.get(this).flush();
    }

    private int indexOfLocal(long localId) {
        for (int i = 0; i < messages.size(); i++) if (messages.get(i).localId == localId && localId != 0) return i;
        return -1;
    }

    private final com.talksy.messenger.sync.LegacyOutbox.Listener outboxListener = new com.talksy.messenger.sync.LegacyOutbox.Listener() {
        @Override public void onDelivered(long localId, long serverId, long timeMs) {
            int idx = indexOfLocal(localId);
            if (idx < 0) return;
            Message m = messages.get(idx);
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
            for (Message m : com.talksy.messenger.sync.LocalDb.get(this).getMessages(com.talksy.messenger.sync.LocalDb.userKey(peerId))) {
                if (m.id < 0 && !seenMessageIds.contains(m.id)) { seenMessageIds.add(m.id); messages.add(m); }
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        openPeerId = peerId;
        com.talksy.messenger.sync.LegacyOutbox.get(this).addListener(outboxListener);
        com.talksy.messenger.sync.LegacyOutbox.get(this).flush();
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel((int) peerId);
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        apiClient.markChatRead(peerId, null);
        com.talksy.messenger.data.ChatWallpaper.apply(
                findViewById(R.id.chat_root),
                new com.talksy.messenger.data.Prefs(this).get(com.talksy.messenger.data.Prefs.KEY_CHAT_WALLPAPER, com.talksy.messenger.data.ChatWallpaper.DEFAULT));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (openPeerId == peerId) openPeerId = -1;
        com.talksy.messenger.sync.LegacyOutbox.get(this).removeListener(outboxListener);
        pollHandler.removeCallbacks(pollRunnable);

        if (editingMessage == null) {
            com.talksy.messenger.data.Drafts.set(this, "u" + peerId, ((android.widget.EditText) findViewById(R.id.inp_message)).getText().toString());
        }
    }

    private void loadAll() {

        try {
            com.talksy.messenger.sync.LocalDb local =
                    com.talksy.messenger.sync.LocalDb.get(this);

            List<Message> cached =
                    local.getMessages(
                            com.talksy.messenger.sync.LocalDb.userKey(peerId)
                    );

            if (cached != null && !cached.isEmpty()) {
                messages.clear();
                seenMessageIds.clear();

                for (Message m : cached) {
                    seenMessageIds.add(m.id);
                    if (m.id > 0) lastTime = Math.max(lastTime, m.time);
                    if (!m.deletedForEveryone) {
                        messages.add(m);
                    }
                }

                adapter.notifyDataSetChanged();
                decryptPending();
                scrollToBottom();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        apiClient.getMessages(peerId, 0, (ok, list, error) -> {
            if (!ok || list == null) return;
            try { com.talksy.messenger.sync.LocalDb.get(this).seedLegacyMessages(com.talksy.messenger.sync.LocalDb.userKey(peerId), list); } catch (Exception ignored) {}
            messages.clear();
            seenMessageIds.clear();
            for (Message m : list) {
                seenMessageIds.add(m.id);
                lastTime = Math.max(lastTime, m.time);
                if (m.deletedForEveryone) continue;
                messages.add(m);
            }
            appendUnsentLocal();
            applyPeerMeta(apiClient.lastPeerMeta);
            adapter.notifyDataSetChanged();
            decryptPending();
            scrollToBottom();
            refreshComposerState();
        });
    }

    private void refreshComposerState() {
        View inputRow = findViewById(R.id.input_row);
        View startBar = findViewById(R.id.bar_bot_start);
        View blockedBar = findViewById(R.id.bar_blocked);

        if (!peerIsBot && !peerCanMessage) {
            inputRow.setVisibility(View.GONE);
            startBar.setVisibility(View.GONE);
            blockedBar.setVisibility(View.VISIBLE);
            return;
        }
        blockedBar.setVisibility(View.GONE);

        boolean showStart = peerIsBot && messages.isEmpty() && !botStarted;
        inputRow.setVisibility(showStart ? View.GONE : View.VISIBLE);
        startBar.setVisibility(showStart ? View.VISIBLE : View.GONE);
        if (showStart) {
            View btnStart = findViewById(R.id.btn_bot_start);
            AnimUtils.pressScale(btnStart);
            btnStart.setOnClickListener(v -> {

                botStarted = true;
                refreshComposerState();
                EditText inp = findViewById(R.id.inp_message);
                ImageButton btnSend = findViewById(R.id.btn_send);
                sendPlain("/start", inp, btnSend);
            });
        }
        updateBotMenuVisibility();
    }

    private void openBotMiniApp() {
        apiClient.createMiniAppSession(peerId, (ok, session, error) -> {
            if (!ok || session == null) {
                Toast.makeText(this, error != null ? error : getString(R.string.miniapp_invalid), Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(this, MiniAppActivity.class);
            i.putExtra("url", session.url);
            i.putExtra("session_id", session.sessionId);
            i.putExtra("title", session.name);
            i.putExtra("bot_username", session.botUsername);
            startActivity(i);
            AnimUtils.forward(this);
        });
    }

    private void renderBotKeyboard() {
        android.widget.LinearLayout container = findViewById(R.id.container_bot_keyboard);
        container.removeAllViews();
        if (peerKeyboard == null) { updateBotMenuVisibility(); return; }
        float dp = getResources().getDisplayMetrics().density;
        for (List<String> row : peerKeyboard.rows) {
            android.widget.LinearLayout rowLayout = new android.widget.LinearLayout(this);
            rowLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            android.widget.LinearLayout.LayoutParams rowLp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = (int) (4 * dp);
            rowLayout.setLayoutParams(rowLp);
            for (String label : row) {
                TextView tv = new TextView(this);
                tv.setText(label);
                tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.accent));
                tv.setTextSize(13f);
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setBackgroundResource(R.drawable.bg_keyboard_button);
                tv.setPadding((int) (8 * dp), (int) (10 * dp), (int) (8 * dp), (int) (10 * dp));
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                        0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMarginStart((int) (3 * dp));
                lp.setMarginEnd((int) (3 * dp));
                tv.setLayoutParams(lp);
                AnimUtils.pressScale(tv);
                tv.setOnClickListener(v -> {
                    EditText inp = findViewById(R.id.inp_message);
                    ImageButton btnSend = findViewById(R.id.btn_send);
                    sendPlain(label, inp, btnSend);
                });
                rowLayout.addView(tv);
            }
            container.addView(rowLayout);
        }
        updateBotMenuVisibility();
    }

    private void updateBotMenuVisibility() {
        View container = findViewById(R.id.container_bot_keyboard);
        ImageButton toggle = findViewById(R.id.btn_bot_menu);
        if (container == null) return;
        boolean hasMenu = peerIsBot && peerKeyboard != null
                && peerKeyboard.rows != null && !peerKeyboard.rows.isEmpty();
        boolean started = botStarted || !messages.isEmpty();
        boolean showMenu = hasMenu && started && !imeOpen;
        container.setVisibility(showMenu ? View.VISIBLE : View.GONE);
        if (toggle != null) {
            toggle.setVisibility(hasMenu && started ? View.VISIBLE : View.GONE);
            toggle.setImageResource(imeOpen ? R.drawable.ic_bot_menu : R.drawable.ic_bot_keyboard);
        }
    }

    private void setupBotMenuToggle() {
        final View decor = getWindow().getDecorView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            android.graphics.Rect r = new android.graphics.Rect();
            decor.getWindowVisibleDisplayFrame(r);
            int hidden = decor.getRootView().getHeight() - r.bottom;
            boolean open = hidden > 150 * getResources().getDisplayMetrics().density;
            if (open != imeOpen) {
                imeOpen = open;
                updateBotMenuVisibility();
            }
        });
        ImageButton toggle = findViewById(R.id.btn_bot_menu);
        if (toggle == null) return;
        toggle.setOnClickListener(v -> {
            EditText inp = findViewById(R.id.inp_message);
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm == null || inp == null) return;
            if (imeOpen) {
                imm.hideSoftInputFromWindow(inp.getWindowToken(), 0);
                inp.clearFocus();
            } else {
                inp.requestFocus();
                imm.showSoftInput(inp, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void onKeyboardButtonTap(Message m, com.talksy.messenger.data.model.InlineKeyboard.Button btn) {
        if (btn.miniApp) {
            openBotMiniApp();
        } else if (btn.url != null) {
            try { com.talksy.messenger.ui.LinkRouter.open(this, btn.url); } catch (Exception ignored) { }
        } else if (btn.callbackData != null) {
            if (m.id <= 0) return;
            apiClient.sendBotCallback(m.id, btn.callbackData, (ok, r, error) -> {
                if (!ok) { Toast.makeText(this, error, Toast.LENGTH_SHORT).show(); return; }
                String txt = r.optString("text", "");
                if (!r.optBoolean("answered") || txt.isEmpty()) return;
                if (r.optBoolean("show_alert")) {
                    new android.app.AlertDialog.Builder(this).setMessage(txt)
                            .setPositiveButton(android.R.string.ok, null).show();
                } else {
                    Toast.makeText(this, txt, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            EditText inp = findViewById(R.id.inp_message);
            ImageButton btnSend = findViewById(R.id.btn_send);
            sendPlain(btn.text, inp, btnSend);
        }
    }

    private void loadNew() {
        if (pollInFlight) return;
        pollInFlight = true;
        apiClient.getMessages(peerId, lastTime, (ok, list, error) -> {
            pollInFlight = false;
            if (!ok || list == null) return;
            try { com.talksy.messenger.sync.LocalDb.get(this).seedLegacyMessages(com.talksy.messenger.sync.LocalDb.userKey(peerId), list); } catch (Exception ignored) {}
            for (Message m : list) addIfNew(m);
            applyPeerMeta(apiClient.lastPeerMeta);
            applyUpdates(apiClient.lastUpdates);
        });
    }

    private void applyPeerMeta(ApiClient.PeerMeta meta) {
        if (meta == null) return;
        TextView tv = findViewById(R.id.txt_last_seen);
        if (tv != null) {
            if (peerIsBot) {

                tv.setText(peerUser != null ? peerUser.botSubtitle(this) : getString(R.string.bot_chat_subtitle));
                tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text2));
            } else if (meta.typing) {
                tv.setText(getString(R.string.typing_now));
                tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.accent));
            } else {
                com.talksy.messenger.data.model.User u = new com.talksy.messenger.data.model.User();
                u.lastSeen = meta.online ? (Long) System.currentTimeMillis() : meta.lastSeenMs;
                tv.setText(u.lastSeenText(this));
                tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text2));
            }
        }
        peerLastReadMs = meta.lastReadMs;
        try { com.talksy.messenger.sync.LocalDb.get(this).setPeerLastReadMs(com.talksy.messenger.sync.LocalDb.userKey(peerId), meta.lastReadMs); } catch (Exception ignored) {}
        pinnedId = meta.pinnedId;
        updatePinBar();
        boolean changed = false;
        for (Message m : messages) {
            if (!m.outgoing) continue;
            boolean r = m.id > 0 && m.time <= peerLastReadMs;
            if (r != m.read) { m.read = r; changed = true; }
        }
        if (changed) adapter.notifyDataSetChanged();
    }

    private void applyUpdates(List<Message> ups) {
        if (ups == null || ups.isEmpty()) return;
        boolean changed = false;
        for (Message u : ups) {
            for (Message m : messages) {
                if (m.id != u.id) continue;
                m.text = u.text;
                m.encText = u.encText;
                m.encKeyRecipient = u.encKeyRecipient;
                m.encKeySender = u.encKeySender;
                m.iv = u.iv;
                m.edited = u.edited;
                m.deletedForEveryone = u.deletedForEveryone;
                if (u.deletedForEveryone) { messages.remove(m); changed = true; break; }
                changed = true;
                break;
            }
        }
        if (changed) {
            decryptPending();
            adapter.notifyDataSetChanged();
            updatePinBar();
        }
    }

    private void addIfNew(Message m) {
        if (seenMessageIds.contains(m.id)) return;
        seenMessageIds.add(m.id);
        if (m.deletedForEveryone) return;
        messages.add(m);
        lastTime = Math.max(lastTime, m.time);
        adapter.notifyItemInserted(messages.size() - 1);
        decryptPending();
        scrollToBottom();
        if (!m.outgoing) apiClient.markChatRead(peerId, null);
    }

    private void scrollToBottom() {
        if (messages.isEmpty()) return;
        rvMessages.post(() -> rvMessages.scrollToPosition(messages.size() - 1));
    }

    private void decryptPending() {
        List<Message> toDecrypt = new ArrayList<>();
        for (Message m : messages) {
            if (m.encText != null && !m.encText.isEmpty() && (m.text == null || m.text.isEmpty())) {
                toDecrypt.add(m);
            }
        }
        if (toDecrypt.isEmpty()) return;

        new Thread(() -> {
            for (Message m : toDecrypt) {
                try {
                    String keyForMe = m.outgoing ? m.encKeySender : m.encKeyRecipient;
                    m.text = CryptoManager.decryptDirect(m.encText, keyForMe, m.iv);
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

    private long pendingSeq = 0;

    private void sendSpecial(String payload) {

        boolean e2e = new Prefs(this).getBool(Prefs.KEY_E2E_ENABLED, false) && !peerIsBot;
        if (!e2e) { queueOutgoing(payload, null, null, null, null); return; }
        final String key = peerPublicKey;
        if (key == null || key.isEmpty()) { Toast.makeText(this, getString(R.string.peer_no_key_yet), Toast.LENGTH_LONG).show(); return; }
        new Thread(() -> {
            try {
                CryptoManager.DirectEncrypted enc = CryptoManager.encryptDirect(payload, key);
                runOnUiThread(() -> queueOutgoing(payload, enc.encText, enc.encKeyRecipient, enc.encKeySender, enc.iv));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.encryption_error) + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void onFilePicked(Uri uri) {
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

        final Message pending = new Message(peerId, true, "", System.currentTimeMillis());
        pending.id = --pendingSeq;
        pending.uploading = true;
        pending.uploadName = info.name;
        pending.uploadTotal = info.size;
        pending.uploadDone = 0;
        pending.uploadCancel = new java.util.concurrent.atomic.AtomicBoolean(false);
        messages.add(pending);
        adapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();

        apiClient.uploadMediaStream(uri, info.name, info.mime, info.size,
                (sent, total) -> {
                    pending.uploadDone = sent;
                    if (total > 0) pending.uploadTotal = total;
                    adapter.refreshProgress(rvMessages, messages.indexOf(pending));
                },
                pending.uploadCancel,
                (ok, media, error) -> {
                    if (!ok || media == null) {
                        removePending(pending);
                        if (!pending.uploadCancel.get()) {
                            Toast.makeText(this, error != null ? error : getString(R.string.file_upload_failed),
                                    Toast.LENGTH_LONG).show();
                        }
                        return;
                    }
                    apiClient.sendMessage(peerId, "", media, (ok2, msg, error2) -> {
                        removePending(pending);
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
        final long reply = takeReplyId();
        final Message pending = new Message(peerId, true, "", System.currentTimeMillis());
        pending.id = --pendingSeq;
        pending.syncStatus = com.talksy.messenger.sync.SyncStatus.PENDING;
        MediaAttachment local = new MediaAttachment();
        local.type = kind; local.filename = name; local.size = f.length(); local.durationMs = ms; local.waveform = wave;
        local.localPath = f.getAbsolutePath();
        pending.media = local;
        messages.add(pending);
        adapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();

        apiClient.uploadMediaStream(android.net.Uri.fromFile(f), name, mime, f.length(), (sent, total) -> { }, new java.util.concurrent.atomic.AtomicBoolean(false),
                (ok, media, error) -> {
                    if (!ok || media == null) {
                        removePending(pending);
                        Toast.makeText(this, error != null ? error : getString(R.string.file_upload_failed), Toast.LENGTH_LONG).show();
                        return;
                    }
                    media.type = kind; media.durationMs = ms; media.waveform = wave;
                    apiClient.sendMessage(peerId, "", media, reply, (ok2, msg, error2) -> {
                        removePending(pending);
                        if (ok2 && msg != null) {
                            addIfNew(msg);
                        } else {
                            Toast.makeText(this, error2 != null ? error2 : getString(R.string.msg_send_failed), Toast.LENGTH_SHORT).show();
                        }
                    });
                });
    }

    private void removePending(Message pending) {
        int i = messages.indexOf(pending);
        if (i >= 0) {
            messages.remove(i);
            adapter.notifyItemRemoved(i);
        }
    }

    private void confirmCancelUpload(Message m) {
        new com.talksy.messenger.ui.TDialog.Builder(this)
                .setMessage(R.string.upload_cancel_confirm)
                .setPositiveButton(R.string.upload_cancel_yes, (d, w) -> {
                    if (m.uploadCancel != null) m.uploadCancel.set(true);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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

    private void showMessageMenu(Message message) {
        if (message.uploading) { confirmCancelUpload(message); return; }
        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        final float dp = getResources().getDisplayMetrics().density;
        int cBg = androidx.core.content.ContextCompat.getColor(this, R.color.bg2);
        int cSub = androidx.core.content.ContextCompat.getColor(this, R.color.text2);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (6 * dp);
        root.setPadding(pad, pad, pad, pad);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(cBg);
        bg.setCornerRadius(16 * dp);
        root.setBackground(bg);

        if (!message.deletedForEveryone) {
            android.widget.LinearLayout emojiRow = new android.widget.LinearLayout(this);
            emojiRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            emojiRow.setGravity(android.view.Gravity.CENTER);
            for (String emoji : REACTION_EMOJIS) {
                TextView e = new TextView(this);
                e.setText(emoji);
                e.setTextSize(24);
                e.setPadding((int) (7 * dp), (int) (6 * dp), (int) (7 * dp), (int) (6 * dp));
                e.setOnClickListener(v -> {
                    dialog.dismiss();
                    apiClient.toggleReaction("direct", message.id, emoji, (ok, reactions, error) -> {
                        if (ok && reactions != null) {
                            message.reactions = reactions;
                            adapter.notifyDataSetChanged();
                        }
                    });
                });
                emojiRow.addView(e);
            }
            root.addView(emojiRow);
        }

        boolean hasText = message.text != null && !message.text.isEmpty() && !message.deletedForEveryone;

        if (!message.deletedForEveryone) {
            root.addView(menuRow(IconManager.prefix(this, "reply", getString(R.string.msg_reply)), 0, dp, v -> {
                dialog.dismiss();
                startReply(message);
            }));
            if (message.outgoing && hasText) {
                root.addView(menuRow(IconManager.prefix(this, "edit", getString(R.string.msg_edit)), 0, dp, v -> {
                    dialog.dismiss();
                    startEdit(message);
                }));
            }
            final boolean isPinned = message.id == pinnedId;
            root.addView(menuRow(IconManager.prefix(this, "pin", getString(isPinned ? R.string.msg_unpin : R.string.msg_pin)), 0, dp, v -> {
                dialog.dismiss();
                togglePin(message, isPinned);
            }));
        }

        if (hasText) {
            root.addView(menuRow(IconManager.prefix(this, "copy", getString(R.string.menu_copy_text)), 0, dp, v -> {
                dialog.dismiss();
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("message", message.text));
                Toast.makeText(this, R.string.text_copied, Toast.LENGTH_SHORT).show();
            }));
        }
        if (!message.deletedForEveryone) {
            final String favId = "u" + peerId + ":" + message.id;
            final boolean isFav = com.talksy.messenger.data.Favorites.has(this, favId);
            root.addView(menuRow(IconManager.prefix(this, "save", getString(isFav ? R.string.menu_unfavorite : R.string.menu_favorite)), 0, dp, v -> {
                dialog.dismiss();
                com.talksy.messenger.data.Favorites.Item fi = new com.talksy.messenger.data.Favorites.Item();
                fi.id = favId;
                fi.from = message.outgoing ? getString(R.string.chat_you_label) : chatTitleForFavorites();
                fi.text = message.text == null ? "" : message.text;
                fi.time = message.time;
                if (message.media != null && !message.media.isGift()) { fi.mediaUrl = message.media.url; fi.mediaName = message.media.filename; }
                boolean added = com.talksy.messenger.data.Favorites.toggle(this, fi);
                Toast.makeText(this, added ? R.string.favorite_added : R.string.favorite_removed, Toast.LENGTH_SHORT).show();
            }));
        }
        if (message.media != null && !message.media.isGift() && !message.deletedForEveryone) {
            root.addView(menuRow(IconManager.prefix(this, "file_download", getString(R.string.menu_save_downloads)), 0, dp, v -> {
                dialog.dismiss();
                FileOpener.downloadAndOpen(this, apiClient.getBaseUrl() + message.media.url, message.media.filename);
            }));
        }
        if (!message.deletedForEveryone) {
            root.addView(menuRow(IconManager.prefix(this, "forward", getString(R.string.menu_forward)), 0, dp, v -> {
                dialog.dismiss();
                showForwardPicker(message);
            }));
        }
        root.addView(menuRow(IconManager.prefix(this, "delete", getString(R.string.menu_delete), 0xFFE5484D), 0xFFE5484D, dp, v -> {
            dialog.dismiss();
            deleteFromMenu(message);
        }));

        String when = new java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault()).format(message.time);
        android.text.SpannableStringBuilder status = new android.text.SpannableStringBuilder(when);
        if (message.outgoing && !message.deletedForEveryone) {
            status.append("   ").append(IconManager.inline(this, message.read ? "check_double" : "check_single", 15, IconManager.tint(this)))
                    .append(' ').append(getString(message.read ? R.string.msg_status_read : R.string.msg_status_sent));
        }
        TextView footer = new TextView(this);
        footer.setText(status);
        footer.setTextSize(12);
        footer.setTextColor(cSub);
        footer.setPadding((int) (14 * dp), (int) (10 * dp), (int) (14 * dp), (int) (6 * dp));
        root.addView(footer);

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout((int) (290 * dp), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private String chatTitleForFavorites() {
        String n = getIntent().getStringExtra("peer_name");
        return n == null || n.isEmpty() ? "Talksy" : n;
    }

    private TextView menuRow(CharSequence label, int color, float dp, View.OnClickListener l) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setTextSize(15);
        row.setTextColor(color != 0 ? color
                : androidx.core.content.ContextCompat.getColor(this, R.color.text1));
        row.setPadding((int) (14 * dp), (int) (13 * dp), (int) (14 * dp), (int) (13 * dp));
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);
        row.setOnClickListener(l);
        return row;
    }

    private void deleteFromMenu(Message message) {
        if (message.deletedForEveryone || !message.outgoing) {
            deleteMessage(message, false);
            return;
        }
        new com.talksy.messenger.ui.TDialog.Builder(this)
                .setItems(new CharSequence[]{getString(R.string.delete_for_me), getString(R.string.delete_for_everyone)},
                        (d, which) -> {
                            if (which == 0) deleteMessage(message, false);
                            else confirmDeleteForEveryone(message);
                        })
                .show();
    }

    private void showForwardPicker(Message message) {
        final String text = message.text == null ? "" : message.text;
        if (text.isEmpty() && message.media == null) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        apiClient.getChats((ok, chats, error) -> {
            if (!ok || chats == null || chats.isEmpty()) {
                Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
                return;
            }
            CharSequence[] names = new CharSequence[chats.size()];
            for (int i = 0; i < chats.size(); i++) names[i] = chats.get(i).peerName;
            new com.talksy.messenger.ui.TDialog.Builder(this)
                    .setTitle(R.string.forward_to)
                    .setItems(names, (d, which) -> {
                        final long target = chats.get(which).peerUserId;
                        apiClient.sendMessage(target, text, message.media, (ok2, msg, err2) -> {
                            Toast.makeText(this, ok2 ? getString(R.string.forwarded_ok)
                                    : (err2 != null ? err2 : getString(R.string.msg_send_failed)), Toast.LENGTH_SHORT).show();
                            if (ok2 && msg != null && target == peerId) addIfNew(msg);
                        });
                    })
                    .show();
        });
    }

    private String previewOf(Message m) {
        if (m == null) return "";
        if (m.deletedForEveryone) return getString(R.string.message_deleted);
        String t = m.text == null ? "" : m.text.replace('\n', ' ').trim();
        if (t.isEmpty() && m.media != null) t = (m.media.filename == null || m.media.filename.isEmpty()) ? getString(R.string.attach_file) : m.media.filename;
        if (t.length() > 80) t = t.substring(0, 80) + "…";
        return t;
    }

    private void showComposerBar(String title, String preview) {
        ((TextView) findViewById(R.id.txt_reply_title)).setText(title);
        ((TextView) findViewById(R.id.txt_reply_preview)).setText(preview);
        findViewById(R.id.reply_bar).setVisibility(View.VISIBLE);
        EditText inp = findViewById(R.id.inp_message);
        inp.requestFocus();
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(inp, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    private void clearComposerBar() {
        replyTo = null;
        editingMessage = null;
        findViewById(R.id.reply_bar).setVisibility(View.GONE);
    }

    private void setupSwipeToReply() {
        new androidx.recyclerview.widget.ItemTouchHelper(new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0,
                androidx.recyclerview.widget.ItemTouchHelper.LEFT | androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public float getSwipeThreshold(RecyclerView.ViewHolder vh) {
                return 0.28f;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                Message m = adapter.getMessageAt(pos);
                adapter.notifyItemChanged(pos);
                if (m.deletedForEveryone) return;
                startReply(m);
            }
        }).attachToRecyclerView(rvMessages);
    }

    private void startReply(Message m) {
        editingMessage = null;
        replyTo = m;
        showComposerBar(getString(R.string.reply_title), previewOf(m));
    }

    private void startEdit(Message m) {
        replyTo = null;
        editingMessage = m;
        showComposerBar(getString(R.string.edit_title), previewOf(m));
        EditText inp = findViewById(R.id.inp_message);
        inp.setText(m.text);
        inp.setSelection(inp.getText().length());
    }

    private long takeReplyId() {
        long id = replyTo != null ? replyTo.id : 0;
        if (replyTo != null) clearComposerBar();
        return id;
    }

    private void submitEdit(String newText, EditText inp, ImageButton btnSend) {
        final Message target = editingMessage;
        if (target == null) return;
        if (newText.equals(target.text)) {
            inp.setText("");
            clearComposerBar();
            return;
        }
        btnSend.setEnabled(false);
        boolean e2eMsg = target.encText != null && !target.encText.isEmpty();
        if (e2eMsg) {
            if (peerPublicKey == null || peerPublicKey.isEmpty()) {
                btnSend.setEnabled(true);
                Toast.makeText(this, getString(R.string.peer_no_key_yet), Toast.LENGTH_LONG).show();
                return;
            }
            new Thread(() -> {
                try {
                    CryptoManager.DirectEncrypted enc = CryptoManager.encryptDirect(newText, peerPublicKey);
                    runOnUiThread(() -> apiClient.editMessageEncrypted(target.id, enc,
                            (ok, v, error) -> finishEdit(ok, error, target, newText, enc, inp, btnSend)));
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        btnSend.setEnabled(true);
                        Toast.makeText(this, getString(R.string.encryption_error) + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        } else {
            apiClient.editMessage(target.id, newText,
                    (ok, v, error) -> finishEdit(ok, error, target, newText, null, inp, btnSend));
        }
    }

    private void finishEdit(boolean ok, String error, Message target, String newText,
                            CryptoManager.DirectEncrypted enc, EditText inp, ImageButton btnSend) {
        btnSend.setEnabled(true);
        if (!ok) {
            Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
            return;
        }
        target.text = newText;
        target.edited = true;
        if (enc != null) {
            target.encText = enc.encText;
            target.encKeyRecipient = enc.encKeyRecipient;
            target.encKeySender = enc.encKeySender;
            target.iv = enc.iv;
        }
        inp.setText("");
        clearComposerBar();
        adapter.notifyDataSetChanged();
        updatePinBar();
    }

    private void togglePin(Message m, boolean unpin) {
        final long newId = unpin ? 0 : m.id;
        apiClient.pinMessage(peerId, newId, (ok, v, error) -> {
            if (!ok) {
                Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
                return;
            }
            pinnedId = newId;
            updatePinBar();
        });
    }

    private void updatePinBar() {
        View bar = findViewById(R.id.pin_bar);
        Message pinned = null;
        if (pinnedId > 0) {
            for (Message x : messages) {
                if (x.id == pinnedId) { pinned = x; break; }
            }
        }
        if (pinned == null || pinned.deletedForEveryone) {
            bar.setVisibility(View.GONE);
            return;
        }
        ((TextView) findViewById(R.id.txt_pin_text)).setText(previewOf(pinned));
        bar.setVisibility(View.VISIBLE);
    }

    private void scrollToMessage(long id) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).id == id) { rvMessages.scrollToPosition(i); return; }
        }
    }

    private static final String[] REACTION_EMOJIS = {"👍", "❤️", "😂", "😮", "😢", "🔥"};

    private void showReactionPicker(Message message) {
        if (message.deletedForEveryone) {

            new com.talksy.messenger.ui.TDialog.Builder(this)
                    .setItems(new CharSequence[]{getString(R.string.delete_for_me)}, (d, w) -> deleteMessage(message, false))
                    .show();
            return;
        }

        List<CharSequence> items = new ArrayList<>();
        for (String e : REACTION_EMOJIS) items.add(e);
        items.add(getString(R.string.delete_for_me));
        boolean canDeleteForEveryone = message.outgoing;
        if (canDeleteForEveryone) items.add(getString(R.string.delete_for_everyone));

        new com.talksy.messenger.ui.TDialog.Builder(this)
                .setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
                    if (which < REACTION_EMOJIS.length) {
                        String emoji = REACTION_EMOJIS[which];
                        apiClient.toggleReaction("direct", message.id, emoji, (ok, reactions, error) -> {
                            if (ok && reactions != null) {
                                message.reactions = reactions;
                                adapter.notifyDataSetChanged();
                            }
                        });
                    } else if (which == REACTION_EMOJIS.length) {
                        deleteMessage(message, false);
                    } else {
                        confirmDeleteForEveryone(message);
                    }
                })
                .show();
    }

    private void confirmDeleteForEveryone(Message message) {
        new com.talksy.messenger.ui.TDialog.Builder(this)
                .setIcon(R.mipmap.ic_launcher)
                .setMessage(R.string.delete_for_everyone_confirm)
                .setPositiveButton(R.string.delete_for_everyone, (d, w) -> deleteMessage(message, true))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private final ActivityResultLauncher<Intent> pickSticker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Intent data = result.getData();
                MediaAttachment media = new MediaAttachment();
                media.url = data.getStringExtra(StickerPickerActivity.EXTRA_MEDIA_URL);
                media.type = data.getStringExtra(StickerPickerActivity.EXTRA_MEDIA_TYPE);
                apiClient.sendMessage(peerId, "", media, (ok, msg, error) -> {
                    if (ok && msg != null) addIfNew(msg);
                });
            });

    private void deleteMessage(Message message, boolean forEveryone) {
        apiClient.deleteMessage(message.id, forEveryone, (ok, v, error) -> {
            if (!ok) {
                Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
                return;
            }

            messages.remove(message);
            adapter.notifyDataSetChanged();
            updatePinBar();
        });
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] p = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.length && i < 2; i++) if (!p[i].isEmpty()) sb.append(Character.toUpperCase(p[i].charAt(0)));
        return sb.toString();
    }

    private void showWalletChoice() {
        final String name = getIntent().getStringExtra("peer_name");
        CharSequence[] items = {"Перевести " + (name == null ? "собеседнику" : name), "Открыть кошелёк"};
        new TDialog.Builder(this).setTitle("Кошелёк").setItems(items, (d, which) -> {
            if (which == 1) { WalletLauncher.open(ChatActivity.this); return; }
            android.widget.EditText amount = NumbersUi.field(this, "Сумма, ◈", android.text.InputType.TYPE_CLASS_NUMBER);
            android.widget.EditText comment = NumbersUi.field(this, "Комментарий (необязательно)", android.text.InputType.TYPE_CLASS_TEXT);
            new TDialog.Builder(this).setTitle("Перевод" + (name == null ? "" : ": " + name))
                    .setView(NumbersUi.form(this, "Деньги сразу поступят получателю, в чате появится карточка перевода.", amount, comment))
                    .setPositiveButton("Отправить", (d2, w2) -> {
                        long a;
                        try { a = Long.parseLong(amount.getText().toString().trim()); } catch (Exception e) { EconomyUi.toast(this, "Введите сумму"); return; }
                        org.json.JSONObject b = new org.json.JSONObject();
                        try { b.put("to_user_id", peerId); b.put("amount", a); b.put("comment", comment.getText().toString().trim());
                              b.put("request_id", java.util.UUID.randomUUID().toString()); } catch (Exception ignored) { }
                        apiClient.economyPost("/wallet/send", b, (ok, json, err) -> {
                            if (ok) { EconomyUi.toast(this, "Перевод отправлен"); loadNew(); }
                            else EconomyUi.toast(this, err != null ? err : "Не удалось отправить");
                        });
                    })
                    .setNegativeButton(android.R.string.cancel, null).show();
        }).show();
    }
}
