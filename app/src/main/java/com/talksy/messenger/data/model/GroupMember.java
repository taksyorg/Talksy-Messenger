package com.talksy.messenger.data.model;

public class GroupMember {
    public long id;
    public String name;
    public String handle;
    public String colorHex;
    public String avatarUrl;
    public String role;

    public String initials() {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] p = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.length && i < 2; i++) if (!p[i].isEmpty()) sb.append(Character.toUpperCase(p[i].charAt(0)));
        return sb.toString();
    }
}
