package com.talksy.messenger.sync;

import java.util.List;

public class PullResult {
    public List<Change> changes;
    public long cursor;
    public boolean hasMore, fullResync;
    public double serverTime;
}
