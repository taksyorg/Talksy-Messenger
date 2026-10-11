package com.talksy.messenger.sync;

import java.util.Map;

public class Op {
    public static final String MESSAGE_SEND = "message.send";
    public static final String MESSAGE_EDIT = "message.edit";
    public static final String MESSAGE_DELETE = "message.delete";
    public static final String CHAT_STATE = "chat.state";
    public static final String CHAT_READ = "chat.read";
    public static final String PROFILE_UPDATE = "profile.update";

    public long seq;
    public String opId;
    public String type;
    public String chatKey;
    public Map<String, Object> payload;
    public int attempts;
    public long nextRetryAt;
}
