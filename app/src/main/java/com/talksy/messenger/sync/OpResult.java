package com.talksy.messenger.sync;

import java.util.List;
import java.util.Map;

public class OpResult {
    public static final String OK = "ok", CONFLICT = "conflict", REJECTED = "rejected", ERROR = "error";
    public String opId;
    public String status;
    public boolean retryable;
    public String error, reason;
    public Map<String, Object> entity;
    public List<String> conflictFields;
}
