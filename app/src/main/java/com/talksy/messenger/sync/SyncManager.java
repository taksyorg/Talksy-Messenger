package com.talksy.messenger.sync;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.net.CryptoManager;
import com.talksy.messenger.net.MessagePollService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SyncManager {
    public interface StateListener { void onStateChanged(ConnState state); }

    private static SyncManager instance;
    public static synchronized SyncManager get(Context ctx) {
        if (instance == null) instance = new SyncManager(ctx.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final ConnectionMonitor monitor;
    private final CopyOnWriteArrayList<StateListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean queued = new AtomicBoolean(false);
    private volatile ConnState state = ConnState.CONNECTING;
    private volatile boolean started;
    private int retryStep = 0;
    private Runnable retryRunnable;
    private Runnable offlineDebounce;

    private SyncManager(Context app) {
        this.app = app;
        this.monitor = new ConnectionMonitor(app);
    }

    public ConnState getState() { return state; }
    public boolean isOnline() { return state == ConnState.ONLINE; }
    public boolean hasInternet() { return monitor.hasInternet(); }

    public void addListener(StateListener l) { listeners.addIfAbsent(l); }
    public void removeListener(StateListener l) { listeners.remove(l); }

    private void setState(ConnState s) {
        if (state == s) return;
        state = s;
        main.post(() -> { for (StateListener l : listeners) l.onStateChanged(s); });
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        monitor.start(has -> main.post(() -> onNetwork(has)));
        onNetwork(monitor.hasInternet());
    }

    public synchronized void stop() { started = false; monitor.stop(); main.removeCallbacksAndMessages(null); }

    private void onNetwork(boolean has) {
        if (has) {
            if (offlineDebounce != null) { main.removeCallbacks(offlineDebounce); offlineDebounce = null; }
            if (state == ConnState.OFFLINE) setState(ConnState.CONNECTING);
            retryStep = 0;
            requestSync("network-restored");
        } else {

            if (offlineDebounce != null) main.removeCallbacks(offlineDebounce);
            offlineDebounce = () -> { if (!monitor.hasInternet()) setState(ConnState.OFFLINE); };
            main.postDelayed(offlineDebounce, 1200);
        }
    }

    public void requestSync(String reason) {
        if (!new Prefs(app).getBool(Prefs.KEY_LOGGED_IN, false)) return;
        if (!monitor.hasInternet()) { setState(ConnState.OFFLINE); return; }
        LegacyOutbox.get(app).flush();
        if (!queued.compareAndSet(false, true)) return;
        exec.execute(this::runSync);
    }

    private void runSync() {
        queued.set(false);
        LocalDb db = LocalDb.get(app);
        boolean visibleWork = state != ConnState.ONLINE || db.hasPendingOps();
        if (visibleWork) setState(ConnState.SYNCING);
        SyncEngine.Result r;
        try {
            SyncEngine engine = new SyncEngine(db, new HttpRemoteApi(app), System::currentTimeMillis);
            r = engine.syncOnce();
        } catch (Throwable t) {
            r = new SyncEngine.Result(); r.ok = false; r.networkError = true; r.error = String.valueOf(t);
        }
        if (!r.ok) android.util.Log.w("TalksySync", "sync failed: " + r.error);
        if (r.ok) {
            retryStep = 0;
            deliverNotifications(db);
            long next = db.nextRetryAt();
            if (db.hasPendingOps() && next > 0) {
                setState(ConnState.CONNECTING);
                scheduleRetry(Math.max(1000, next - System.currentTimeMillis()));
            } else {
                setState(ConnState.ONLINE);
            }
        } else if (r.authError) {
            setState(ConnState.CONNECTING);
        } else {
            setState(monitor.hasInternet() ? ConnState.CONNECTING : ConnState.OFFLINE);
            retryStep = Math.min(retryStep + 1, 6);
            scheduleRetry(Math.min(2000L << retryStep, 60_000L));
        }
    }

    private void scheduleRetry(long delayMs) {
        main.post(() -> {
            if (retryRunnable != null) main.removeCallbacks(retryRunnable);
            retryRunnable = () -> requestSync("retry");
            main.postDelayed(retryRunnable, delayMs);
        });
    }

    private void deliverNotifications(LocalDb db) {
        List<LocalDb.Incoming> list = new java.util.ArrayList<>(db.lastIncoming);
        for (LocalDb.Incoming in : list) {
            if (in.chatKey == null || !in.chatKey.startsWith("u:")) continue;
            long peerId = Long.parseLong(in.chatKey.substring(2));
            if (com.talksy.messenger.ui.ChatActivity.openPeerId == peerId) continue;
            String text = in.text;
            if (in.encText != null && !in.encText.isEmpty()) {
                try { text = CryptoManager.decryptDirect(in.encText, in.encKeyRecipient, in.iv); }
                catch (Exception e) { text = "Новое сообщение"; }
            }
            Intent i = new Intent(app, MessagePollService.class);
            i.setAction(MessagePollService.ACTION_NOTIFY);
            i.putExtra("peer_id", peerId); i.putExtra("text", text);
            try { app.startForegroundService(i); } catch (Exception ignored) {}
        }
    }
}
