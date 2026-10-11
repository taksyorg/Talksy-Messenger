package com.talksy.messenger.data.model;

public class SearchItem {
    public long id;
    public boolean isGroup;
    public String name;
    public String handle;
    public String colorHex;
    public String avatarUrl;
    public String myRole;

    public static SearchItem fromUser(User u) {
        SearchItem item = new SearchItem();
        item.id = u.id;
        item.isGroup = false;
        item.name = u.name;
        item.handle = u.handle;
        item.colorHex = u.colorHex;
        item.avatarUrl = u.avatarUrl;
        return item;
    }

    public static SearchItem fromGroup(Group g) {
        SearchItem item = new SearchItem();
        item.id = g.id;
        item.isGroup = true;
        item.name = g.name;
        item.handle = g.handle;
        item.colorHex = "#06B6D4";
        item.avatarUrl = g.avatarUrl;
        item.myRole = g.myRole;
        return item;
    }
}
