package com.talksy.messenger.sync;

public final class SyncStatus {
    private SyncStatus() {}
    public static final String SYNCED = "SYNCED";
    public static final String PENDING = "PENDING";
    public static final String SENDING = "SENDING";
    public static final String FAILED = "FAILED";
    public static final String CONFLICT = "CONFLICT";
}
