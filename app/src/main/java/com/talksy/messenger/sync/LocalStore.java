package com.talksy.messenger.sync;

import java.util.List;

public interface LocalStore {

    void resetSending();

    List<Op> claimDueOps(int limit, long nowMs);

    void applyResult(Op op, OpResult res);

    void markTransientFailure(List<Op> ops, long nowMs);

    long getCursor();

    void applyPull(PullResult r);
    boolean hasPendingOps();

    long nextRetryAt();
}
