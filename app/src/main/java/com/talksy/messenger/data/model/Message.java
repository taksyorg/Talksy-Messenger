package com.talksy.messenger.data.model;

import java.util.ArrayList;
import java.util.List;

public class Message {
    public long id;
    public long chatId;
    public boolean outgoing;
    public String text;
    public long time;
    public List<Reaction> reactions = new ArrayList<>();
    public MediaAttachment media;
    public String encText, encKeyRecipient, encKeySender, iv;
    public boolean deletedForEveryone;
    public long storyId;

    public boolean uploading;
    public String uploadName;
    public long uploadDone, uploadTotal;
    public java.util.concurrent.atomic.AtomicBoolean uploadCancel;
    public boolean read;
    public long replyToId;
    public boolean edited;
    public String replyMarkup;

    public long localId;
    public boolean group;
    public String clientUuid;
    public String syncStatus;
    public long version;
    public String conflictText;

    public Message() {}

    public Message(long chatId, boolean outgoing, String text, long time) {
        this.chatId = chatId;
        this.outgoing = outgoing;
        this.text = text;
        this.time = time;
    }
}
