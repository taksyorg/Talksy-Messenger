package com.talksy.messenger.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.talksy.messenger.R;
import com.talksy.messenger.data.model.Message;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.net.CryptoManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LegacyOutbox {
    public interface Listener {
        void onDelivered(long localId, long serverId, long timeMs);
        void onFailed(long localId);
    }

    private static LegacyOutbox instance;
    public static synchronized LegacyOutbox get(Context ctx) {
        if (instance == null) instance = new LegacyOutbox(ctx.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean again = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private int retryStep = 0;
    private Runnable retryRunnable;

    private LegacyOutbox(Context app) { this.app = app; }

    public void addListener(Listener l) { listeners.addIfAbsent(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public void flush() {
        if (!running.compareAndSet(false, true)) { again.set(true); return; }
        exec.execute(this::run);
    }

    private void run() {
        boolean needRetry = false;
        try {
            do {
                again.set(false);
                LocalDb db = LocalDb.get(app);
                List<Message> list = db.getUnsentOutgoing();
                for (Message m : list) {
                    if (!SyncManager.get(app).hasInternet()) { needRetry = true; break; }
                    int r = sendOne(db, m);
                    if (r == 1) { needRetry = true; break; }
                }
            } while (again.get());
        } catch (Throwable t) {
            needRetry = true;
        } finally {
            running.set(false);
        }
        if (needRetry) scheduleRetry(); else retryStep = 0;
    }

    private int sendOne(LocalDb db, Message m) {
        if (m.chatId <= 0) return 0;
        final ApiClient api = new ApiClient(app);
        final CountDownLatch latch = new CountDownLatch(1);
        final Message[] res = new Message[1];
        final String[] err = new String[1];
        final boolean[] ok = new boolean[1];
        ApiClient.Callback<Message> cb = (success, data, error) -> {
            ok[0] = success; res[0] = data; err[0] = error; latch.countDown();
        };
        if (m.group) {
            final ApiClient.Callback<com.talksy.messenger.data.model.GroupMessage> gcb = (success, data, error) -> {
                ok[0] = success; err[0] = error;
                if (data != null) { res[0] = new Message(); res[0].id = data.id; res[0].time = data.time; }
                latch.countDown();
            };
            main.post(() -> {
                if (m.encText != null && !m.encText.isEmpty()) {
                    CryptoManager.GroupEncrypted ge = new CryptoManager.GroupEncrypted();
                    ge.encText = m.encText; ge.iv = m.iv;
                    api.sendEncryptedGroupMessage(m.chatId, ge, gcb);
                } else {
                    api.sendGroupMessage(m.chatId, m.text == null ? "" : m.text, m.media, gcb);
                }
            });
        } else
        main.post(() -> {
            if (m.encText != null && !m.encText.isEmpty()) {
                CryptoManager.DirectEncrypted de = new CryptoManager.DirectEncrypted();
                de.encText = m.encText; de.encKeyRecipient = m.encKeyRecipient; de.encKeySender = m.encKeySender; de.iv = m.iv;
                api.sendEncryptedMessage(m.chatId, de, m.replyToId, cb);
            } else {
                api.sendMessage(m.chatId, m.text == null ? "" : m.text, m.media, m.replyToId, cb);
            }
        });
        try {
            if (!latch.await(40, TimeUnit.SECONDS)) return 1;
        } catch (InterruptedException e) { return 1; }

        if (ok[0] && res[0] != null) {
            final long serverId = res[0].id, t = res[0].time;
            db.markDelivered(m.localId, serverId, t);
            main.post(() -> { for (Listener l : listeners) l.onDelivered(m.localId, serverId, t); });
            return 0;
        }
        String e = err[0] == null ? "" : err[0];
        boolean transientErr = e.isEmpty()
                || e.startsWith(app.getString(R.string.error_server_connection))
                || e.equals(app.getString(R.string.error_server_not_configured));
        if (transientErr) return 1;
        db.markFailed(m.localId);
        main.post(() -> { for (Listener l : listeners) l.onFailed(m.localId); });
        return 0;
    }

    private void scheduleRetry() {
        retryStep = Math.min(retryStep + 1, 5);
        final long delay = Math.min(2000L << retryStep, 60_000L);
        main.post(() -> {
            if (retryRunnable != null) main.removeCallbacks(retryRunnable);
            retryRunnable = this::flush;
            main.postDelayed(retryRunnable, delay);
        });
    }
}
