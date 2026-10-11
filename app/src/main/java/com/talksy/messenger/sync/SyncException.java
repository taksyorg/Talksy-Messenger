package com.talksy.messenger.sync;

public class SyncException extends Exception {
    public final boolean auth;
    public final boolean transientErr;
    public SyncException(String msg, boolean transientErr, boolean auth) { super(msg); this.transientErr = transientErr; this.auth = auth; }
    public static SyncException network(String m) { return new SyncException(m, true, false); }
    public static SyncException auth(String m) { return new SyncException(m, false, true); }
}
