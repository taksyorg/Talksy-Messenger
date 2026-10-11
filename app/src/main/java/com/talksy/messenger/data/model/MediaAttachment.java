package com.talksy.messenger.data.model;

public class MediaAttachment {
    public String url;
    public String type;
    public String filename;
    public long size;
    public long durationMs;
    public String waveform;
    public String localPath;

    public static final String SPOILER_PREFIX = "SPOILER_";

    public static final java.util.Set<String> REVEALED = new java.util.HashSet<>();

    public boolean isSpoiler() { return filename != null && filename.startsWith(SPOILER_PREFIX); }
    public boolean isHidden() { return isSpoiler() && !REVEALED.contains(url); }
    public String displayName() {
        return isSpoiler() ? filename.substring(SPOILER_PREFIX.length()) : filename;
    }

    public boolean isImage() { return "image".equals(type); }
    public boolean isVideo() { return "video".equals(type); }
    public boolean isGift() { return "gift".equals(type); }
    public boolean isVoice() { return "voice".equals(type); }
    public boolean isRound() { return "round".equals(type); }
    public boolean isStickerImage() { return "sticker_image".equals(type); }
    public boolean isStickerVideo() { return "sticker_video".equals(type); }

    public String sizeText(android.content.Context ctx) {
        if (size <= 0) return "";
        if (size < 1024) return size + ctx.getString(com.talksy.messenger.R.string.size_bytes);
        if (size < 1024 * 1024) return ctx.getString(com.talksy.messenger.R.string.size_kb, size / 1024.0);
        return ctx.getString(com.talksy.messenger.R.string.size_mb, size / (1024.0 * 1024));
    }
}
