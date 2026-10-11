package com.talksy.messenger.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.data.model.Chat;
import com.talksy.messenger.data.model.Group;
import com.talksy.messenger.net.ApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LegacySync {
    private static LegacySync instance;
    public static synchronized LegacySync get(Context ctx) {
        if (instance == null) instance = new LegacySync(ctx.getApplicationContext());
        return instance;
    }

    private static final long MIN_GAP_MS = 15_000;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private volatile long lastFinished;

    private LegacySync(Context app) { this.app = app; }

    private static final class Task {
        final String key; final boolean group; final long id; final long since;
        Task(String key, boolean group, long id, long since) { this.key = key; this.group = group; this.id = id; this.since = since; }
    }

    public void run() { run(false); }

    public void run(boolean force) {
        if (running) return;
        if (!force && System.currentTimeMillis() - lastFinished < MIN_GAP_MS) return;
        if (!new Prefs(app).getBool(Prefs.KEY_LOGGED_IN, false)) return;
        if (!SyncManager.get(app).hasInternet()) return;
        final ApiClient api = new ApiClient(app);
        if (!api.isConfigured()) return;
        running = true;
        main.post(() -> api.getChats(false, (okC, chats, eC) -> api.getMyGroups(false, (okG, groups, eG) -> {
            if (!okC && !okG) { finish(); return; }
            io.execute(() -> {
                LocalDb db = LocalDb.get(app);
                db.seedLegacyChats(okC ? chats : null, okG ? groups : null, false);
                List<Task> tasks = new ArrayList<>();
                if (okC && chats != null) for (Chat c : chats) {
                    String key = LocalDb.userKey(c.peerUserId);
                    long have = db.lastServerTimeMs(key);
                    if (have == 0 || c.lastMessageTime > have) tasks.add(new Task(key, false, c.peerUserId, have));
                }
                if (okG && groups != null) for (Group g : groups) {
                    String key = LocalDb.groupKey(g.id);
                    long have = db.lastServerTimeMs(key);
                    if (have == 0 || g.lastMessageTime > have) tasks.add(new Task(key, true, g.id, have));
                }
                main.post(() -> next(api, tasks, 0));
            });
        })));
    }

    private void next(ApiClient api, List<Task> tasks, int i) {
        if (i >= tasks.size()) { finish(); return; }
        if (!SyncManager.get(app).hasInternet()) { finish(); return; }
        final Task t = tasks.get(i);
        if (!t.group) {
            api.getMessages(t.id, t.since, (ok, list, err) -> {
                final ApiClient.PeerMeta meta = api.lastPeerMeta;
                io.execute(() -> {
                    LocalDb db = LocalDb.get(app);
                    if (ok && list != null) db.seedLegacyMessages(t.key, list);
                    if (ok && meta != null) db.setPeerLastReadMs(t.key, meta.lastReadMs);
                    main.post(() -> next(api, tasks, i + 1));
                });
            });
        } else {
            api.getGroupMessages(t.id, t.since, (ok, list, err) -> io.execute(() -> {
                if (ok && list != null) LocalDb.get(app).seedLegacyGroupMessages(t.key, list);
                main.post(() -> next(api, tasks, i + 1));
            }));
        }
    }

    private void finish() {
        lastFinished = System.currentTimeMillis();
        running = false;
    }
}
