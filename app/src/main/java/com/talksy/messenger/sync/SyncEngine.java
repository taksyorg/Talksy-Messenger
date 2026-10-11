package com.talksy.messenger.sync;

import java.util.List;

public class SyncEngine {
    public static final int PUSH_BATCH = 50;
    public static final int PULL_LIMIT = 300;

    public static class Result {
        public boolean ok;
        public boolean authError;
        public int pushed, pulled;
        public String error;
        public boolean networkError;
    }

    public interface Clock { long now(); }

    private final LocalStore store;
    private final RemoteApi api;
    private final Clock clock;
    private boolean started;

    public SyncEngine(LocalStore store, RemoteApi api, Clock clock) {
        this.store = store; this.api = api; this.clock = clock;
    }

    public synchronized Result syncOnce() {
        Result res = new Result();
        if (!started) { store.resetSending(); started = true; }
        try {

            while (true) {
                List<Op> batch = store.claimDueOps(PUSH_BATCH, clock.now());
                if (batch.isEmpty()) break;
                List<OpResult> results;
                try {
                    results = api.push(batch);
                } catch (SyncException e) {
                    store.markTransientFailure(batch, clock.now());
                    throw e;
                }
                java.util.Map<String, OpResult> byId = new java.util.HashMap<>();
                for (OpResult r : results) byId.put(r.opId, r);
                boolean retryLater = false;
                for (Op op : batch) {
                    OpResult r = byId.get(op.opId);
                    if (r == null || OpResult.ERROR.equals(r.status)) {
                        store.markTransientFailure(java.util.Collections.singletonList(op), clock.now());
                        retryLater = true;
                    } else {
                        store.applyResult(op, r);
                        res.pushed++;
                    }
                }
                if (retryLater) break;
            }

            while (true) {
                PullResult p = api.pull(store.getCursor(), PULL_LIMIT);
                store.applyPull(p);
                res.pulled += p.changes == null ? 0 : p.changes.size();
                if (!p.hasMore) break;
            }
            res.ok = true;
        } catch (SyncException e) {
            res.ok = false; res.error = e.getMessage();
            res.authError = e.auth; res.networkError = e.transientErr;
        } catch (RuntimeException e) {
            res.ok = false; res.networkError = true;
            res.error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        return res;
    }
}
