package com.talksy.messenger.net;

public class AuthResult {
    public final String token;
    public final boolean isNew;

    public AuthResult(String token, boolean isNew) {
        this.token = token;
        this.isNew = isNew;
    }
}
