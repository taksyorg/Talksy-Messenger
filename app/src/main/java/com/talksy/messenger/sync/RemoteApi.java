package com.talksy.messenger.sync;

import java.util.List;

public interface RemoteApi {
    List<OpResult> push(List<Op> ops) throws SyncException;
    PullResult pull(long cursor, int limit) throws SyncException;
}
