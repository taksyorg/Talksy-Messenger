package com.talksy.messenger.sync;

import java.util.Map;

public class Change {
    public String entity, op;
    public long seq;
    public Map<String, Object> data;
}
