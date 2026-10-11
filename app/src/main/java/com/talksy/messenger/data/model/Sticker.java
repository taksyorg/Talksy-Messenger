package com.talksy.messenger.data.model;

public class Sticker {
    public long id;
    public long packId;
    public String mediaUrl;
    public String mediaType;
    public long durationMs;

    public boolean isVideo() { return "sticker_video".equals(mediaType); }
}
