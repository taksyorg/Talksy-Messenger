package com.talksy.messenger.data.model;

import java.util.ArrayList;
import java.util.List;

public class GroupMessage {
    public long id;
    public long senderId;
    public String senderName;
    public boolean outgoing;
    public String text;
    public long time;
    public List<Reaction> reactions = new ArrayList<>();
    public MediaAttachment media;
    public String encText, iv;
    public boolean deletedForEveryone;

    public long localId;
    public String syncStatus;
    public long views;
    public long commentsCount;
    public long version;
    public boolean uploading;
}
