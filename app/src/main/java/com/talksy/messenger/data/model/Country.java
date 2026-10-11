package com.talksy.messenger.data.model;

public class Country {
    public final String name;
    public final String dialCode;
    public final String flag;
    public final int minDigits;
    public final int maxDigits;

    public Country(String name, String dialCode, String flag, int minDigits, int maxDigits) {
        this.name = name;
        this.dialCode = dialCode;
        this.flag = flag;
        this.minDigits = minDigits;
        this.maxDigits = maxDigits;
    }

    @Override
    public String toString() {
        return flag + "  " + name;
    }
}
