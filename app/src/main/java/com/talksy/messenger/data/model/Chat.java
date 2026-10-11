package com.talksy.messenger.data.model;

public class Chat {
    public long id;
    public long peerUserId;
    public String peerName;
    public String peerHandle;
    public String peerColorHex;
    public String peerAvatarUrl;
    public String lastMessage;
    public boolean lastOutgoing;
    public long lastMessageTime;
    public boolean pinned;
    public boolean muted;
    public int unreadCount;
    public boolean peerIsBot;

    public Chat() {}
}
