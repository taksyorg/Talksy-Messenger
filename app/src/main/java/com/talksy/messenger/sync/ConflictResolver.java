package com.talksy.messenger.sync;

import java.util.HashMap;
import java.util.Map;

public final class ConflictResolver {
    private ConflictResolver() {}

    public enum Incoming { SKIP_STALE, APPLY_ALL, APPLY_KEEP_LOCAL_TEXT, DELETE_AND_DROP_PENDING }

    public static Incoming onIncomingMessage(long localVersion, String localStatus, boolean hasPendingEdit,
                                             long incomingVersion, boolean incomingDeleted) {
        if (incomingDeleted) return Incoming.DELETE_AND_DROP_PENDING;
        if (incomingVersion < localVersion && SyncStatus.SYNCED.equals(localStatus)) return Incoming.SKIP_STALE;
        if (hasPendingEdit) return Incoming.APPLY_KEEP_LOCAL_TEXT;
        return Incoming.APPLY_ALL;
    }

    public static Map<String, Object> mergeFields(Map<String, Object> incoming, Map<String, Object> pendingLocal) {
        Map<String, Object> out = new HashMap<>(incoming);
        if (pendingLocal != null) out.putAll(pendingLocal);
        return out;
    }

    public static String conflictTextToKeep(String localEditedText, String serverText, String reason) {
        if ("deleted".equals(reason)) return null;
        if (localEditedText == null || localEditedText.equals(serverText)) return null;
        return localEditedText;
    }

    public static long backoffMs(int attempts) {
        long d = 2000L << Math.min(Math.max(attempts, 0), 8);
        return Math.min(d, 5 * 60_000L);
    }
}
