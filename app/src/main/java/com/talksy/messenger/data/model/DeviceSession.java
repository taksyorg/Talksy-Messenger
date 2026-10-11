package com.talksy.messenger.data.model;

public class DeviceSession {
    public long id;
    public String device;
    public String app;
    public String appVersion;
    public String platform;
    public String city;
    public String country;
    public long createdAtMillis;
    public long lastActiveMillis;
    public boolean online;
    public boolean isCurrent;
    public boolean acceptSecret = true;
    public boolean acceptCalls = true;

    public String whereText() {
        if (city == null || city.isEmpty()) return country == null ? "" : country;
        if (country == null || country.isEmpty()) return city;
        return city + ", " + country;
    }

    public String appText() {
        String a = app == null || app.isEmpty() ? "Talksy" : app;
        return appVersion == null || appVersion.isEmpty() ? a : a + " " + appVersion;
    }
}
