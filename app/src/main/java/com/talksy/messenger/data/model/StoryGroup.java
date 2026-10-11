package com.talksy.messenger.data.model;

import java.util.ArrayList;
import java.util.List;

public class StoryGroup {
    public long userId;
    public String name = "";
    public String color = "#6366F1";
    public String avatarUrl = "";
    public boolean allViewed = true;
    public List<Story> stories = new ArrayList<>();

    public static class Story {
        public long id;
        public String mediaUrl;
        public String mediaType;
        public long createdAtMs;
        public boolean viewed;
    }
}
