package com.talksy.messenger.calls;

import com.talksy.messenger.net.CryptoManager;

import org.json.JSONObject;

public class CallSignalCodec {

    public static String encrypt(String plainText, String recipientPublicKeyBase64) throws Exception {
        CryptoManager.DirectEncrypted enc = CryptoManager.encryptDirect(plainText, recipientPublicKeyBase64);
        JSONObject o = new JSONObject();
        o.put("encText", enc.encText);
        o.put("encKey", enc.encKeyRecipient);
        o.put("iv", enc.iv);
        return o.toString();
    }

    public static String decrypt(String payload) throws Exception {
        JSONObject o = new JSONObject(payload);
        return CryptoManager.decryptDirect(o.getString("encText"), o.getString("encKey"), o.getString("iv"));
    }
}
