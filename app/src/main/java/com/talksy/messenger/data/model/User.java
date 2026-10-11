package com.talksy.messenger.data.model;

public class User {
    public long id;
    public String name;
    public String handle;
    public String phone;
    public String email;
    public String bio;
    public String colorHex;
    public String avatarUrl;
    public boolean hidePhone;
    public boolean hideHandle;
    public boolean hideLastSeen;
    public Long lastSeen;
    public String publicKey;
    public boolean iBlockedThem;
    public String whoCanWrite = "all";
    public boolean isPremium;
    public boolean isBot;
    public boolean hasMiniApp;
    public String miniAppName;
    public String botKeyboard;
    public long botUsersCount;
    public boolean canMessage = true;

    public String botSubtitle(android.content.Context ctx) {
        if (botUsersCount > 1000) {
            return ctx.getResources().getQuantityString(
                    com.talksy.messenger.R.plurals.bot_users_count, (int) Math.min(botUsersCount, Integer.MAX_VALUE), botUsersCount);
        }
        return ctx.getString(com.talksy.messenger.R.string.bot_chat_subtitle);
    }

    public User() {}

    public User(long id, String name, String handle, String phone, String email, String bio, String colorHex) {
        this.id = id;
        this.name = name;
        this.handle = handle;
        this.phone = phone;
        this.email = email;
        this.bio = bio;
        this.colorHex = colorHex;
    }

    public String initials() {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && i < 2; i++) {
            if (!parts[i].isEmpty()) sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.toString();
    }

    public String lastSeenText(android.content.Context ctx) {
        if (lastSeen == null) return ctx.getString(com.talksy.messenger.R.string.last_seen_recently);
        long diffMs = System.currentTimeMillis() - lastSeen;
        if (diffMs < 60_000) return ctx.getString(com.talksy.messenger.R.string.last_seen_online);

        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar then = java.util.Calendar.getInstance();
        then.setTimeInMillis(lastSeen);

        java.text.SimpleDateFormat timeFmt = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        boolean sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR);
        if (sameDay) return ctx.getString(com.talksy.messenger.R.string.last_seen_at_time, timeFmt.format(lastSeen));

        java.util.Calendar yesterday = java.util.Calendar.getInstance();
        yesterday.add(java.util.Calendar.DAY_OF_YEAR, -1);
        boolean wasYesterday = yesterday.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
                && yesterday.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR);
        if (wasYesterday) return ctx.getString(com.talksy.messenger.R.string.last_seen_yesterday_at, timeFmt.format(lastSeen));

        java.text.SimpleDateFormat dateFmt = new java.text.SimpleDateFormat(
                ctx.getString(com.talksy.messenger.R.string.last_seen_date_format), java.util.Locale.getDefault());
        return ctx.getString(com.talksy.messenger.R.string.last_seen_date_at, dateFmt.format(lastSeen));
    }
}
