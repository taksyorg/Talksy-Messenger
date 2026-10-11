package com.talksy.messenger.data.model;

public class Group {
    public long id;
    public String name;
    public String handle;
    public String description;
    public String avatarUrl;
    public long ownerId;
    public boolean adminOnlyMode;
    public boolean reactionsEnabled = true;
    public boolean isChannel;
    public boolean isPrivate;
    public boolean membersVisible;
    public int membersCount;
    public String myRole;
    public String lastMessage;
    public long lastMessageTime;
    public boolean pinned;
    public boolean muted;
    public int unreadCount;

    public boolean canSend() {
        return !adminOnlyMode || "owner".equals(myRole) || "admin".equals(myRole);
    }

    public boolean canManage() {
        return "owner".equals(myRole) || "admin".equals(myRole);
    }

    public boolean isOwner() {
        return "owner".equals(myRole);
    }
}
