package com.talksy.messenger.data.model;

public class ChatListItem {
    public long id;
    public boolean isGroup;
    public String name;
    public String handle;
    public String colorHex;
    public String avatarUrl;
    public String lastMessage;
    public long lastMessageTime;
    public boolean pinned;
    public boolean muted;
    public String myRole;
    public boolean adminOnlyMode;
    public int unreadCount;
    public boolean isBot;

    public static ChatListItem fromChat(Chat c) {
        ChatListItem item = new ChatListItem();
        item.id = c.peerUserId;
        item.isGroup = false;
        item.name = c.peerName;
        item.isBot = c.peerIsBot;
        item.handle = c.peerHandle;
        item.colorHex = c.peerColorHex;
        item.avatarUrl = c.peerAvatarUrl;
        item.lastMessage = c.lastMessage;
        item.lastMessageTime = c.lastMessageTime;
        item.pinned = c.pinned;
        item.muted = c.muted;
        item.unreadCount = c.unreadCount;
        return item;
    }

    public static ChatListItem fromGroup(Group g) {
        ChatListItem item = new ChatListItem();
        item.id = g.id;
        item.isGroup = true;
        item.name = g.name;
        item.handle = g.handle;
        item.colorHex = "#06B6D4";
        item.avatarUrl = g.avatarUrl;
        item.lastMessage = g.lastMessage;
        item.lastMessageTime = g.lastMessageTime;
        item.pinned = g.pinned;
        item.muted = g.muted;
        item.myRole = g.myRole;
        item.adminOnlyMode = g.adminOnlyMode;
        item.unreadCount = g.unreadCount;
        return item;
    }
}
