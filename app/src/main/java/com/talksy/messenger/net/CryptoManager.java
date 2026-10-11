package com.talksy.messenger.net;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoManager {

    private static final String KEYSTORE_ALIAS = "talksy_e2ee_identity";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    public static void ensureKeyPairExists() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(KEYSTORE_ALIAS)) return;

        KeyPairGenerator gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE);
        gen.initialize(new KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build());
        gen.generateKeyPair();
    }

    public static boolean hasKeyPair() {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            return ks.containsAlias(KEYSTORE_ALIAS);
        } catch (Exception e) {
            return false;
        }
    }

    public static String getMyPublicKeyBase64() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        PublicKey pub = ks.getCertificate(KEYSTORE_ALIAS).getPublicKey();
        return Base64.encodeToString(pub.getEncoded(), Base64.NO_WRAP);
    }

    private static PrivateKey getMyPrivateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        return (PrivateKey) ks.getKey(KEYSTORE_ALIAS, null);
    }

    private static PublicKey getMyPublicKeyObject() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        return ks.getCertificate(KEYSTORE_ALIAS).getPublicKey();
    }

    private static PublicKey decodePublicKey(String base64) throws Exception {
        byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
    }

    private static Cipher pkcs1Cipher() throws Exception {
        return Cipher.getInstance("RSA/ECB/PKCS1Padding");
    }

    private static Cipher oaepCipher() throws Exception {
        return Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
    }

    public static class DirectEncrypted {
        public String encText, encKeyRecipient, encKeySender, iv;
    }

    public static DirectEncrypted encryptDirect(String plainText, String recipientPublicKeyBase64) throws Exception {
        byte[] aesKeyBytes = randomBytes(32);
        byte[] iv = randomBytes(12);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKeyBytes, "AES"), new GCMParameterSpec(128, iv));
        byte[] cipherText = aes.doFinal(plainText.getBytes("UTF-8"));

        DirectEncrypted result = new DirectEncrypted();
        result.encText = Base64.encodeToString(cipherText, Base64.NO_WRAP);
        result.encKeyRecipient = wrapAesKey(aesKeyBytes, decodePublicKey(recipientPublicKeyBase64));
        result.encKeySender = wrapAesKey(aesKeyBytes, getMyPublicKeyObject());
        result.iv = Base64.encodeToString(iv, Base64.NO_WRAP);
        return result;
    }

    public static String decryptDirect(String encText, String encKeyForMe, String ivBase64) throws Exception {
        byte[] aesKeyBytes = unwrapAesKey(encKeyForMe);
        byte[] iv = Base64.decode(ivBase64, Base64.NO_WRAP);
        byte[] cipherText = Base64.decode(encText, Base64.NO_WRAP);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKeyBytes, "AES"), new GCMParameterSpec(128, iv));
        return new String(aes.doFinal(cipherText), "UTF-8");
    }

    public static byte[] generateGroupKey() { return randomBytes(32); }

    public static class GroupEncrypted {
        public String encText, iv;
    }

    public static GroupEncrypted encryptGroup(String plainText, byte[] groupKeyBytes) throws Exception {
        byte[] iv = randomBytes(12);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(groupKeyBytes, "AES"), new GCMParameterSpec(128, iv));
        byte[] cipherText = aes.doFinal(plainText.getBytes("UTF-8"));

        GroupEncrypted result = new GroupEncrypted();
        result.encText = Base64.encodeToString(cipherText, Base64.NO_WRAP);
        result.iv = Base64.encodeToString(iv, Base64.NO_WRAP);
        return result;
    }

    public static String decryptGroup(String encText, String ivBase64, byte[] groupKeyBytes) throws Exception {
        byte[] iv = Base64.decode(ivBase64, Base64.NO_WRAP);
        byte[] cipherText = Base64.decode(encText, Base64.NO_WRAP);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(groupKeyBytes, "AES"), new GCMParameterSpec(128, iv));
        return new String(aes.doFinal(cipherText), "UTF-8");
    }

    public static String wrapGroupKeyForMember(byte[] groupKeyBytes, String memberPublicKeyBase64) throws Exception {
        return wrapAesKey(groupKeyBytes, decodePublicKey(memberPublicKeyBase64));
    }

    public static String wrapGroupKeyForSelf(byte[] groupKeyBytes) throws Exception {
        return wrapAesKey(groupKeyBytes, getMyPublicKeyObject());
    }

    public static byte[] unwrapGroupKey(String encryptedKeyBase64) throws Exception {
        return unwrapAesKey(encryptedKeyBase64);
    }

    private static String wrapAesKey(byte[] aesKeyBytes, PublicKey publicKey) throws Exception {

        Cipher pkcs1 = pkcs1Cipher();
        pkcs1.init(Cipher.ENCRYPT_MODE, publicKey);
        String pkcs1Part = Base64.encodeToString(pkcs1.doFinal(aesKeyBytes), Base64.NO_WRAP);

        Cipher oaep = oaepCipher();
        oaep.init(Cipher.ENCRYPT_MODE, publicKey);
        String oaepPart = Base64.encodeToString(oaep.doFinal(aesKeyBytes), Base64.NO_WRAP);

        return pkcs1Part + "." + oaepPart;
    }

    private static byte[] unwrapAesKey(String wrappedBase64) throws Exception {

        String[] parts = wrappedBase64.split("\\.", 2);
        Exception lastError = null;
        if (parts.length == 2) {
            try {
                Cipher pkcs1 = pkcs1Cipher();
                pkcs1.init(Cipher.DECRYPT_MODE, getMyPrivateKey());
                return pkcs1.doFinal(Base64.decode(parts[0], Base64.NO_WRAP));
            } catch (Exception e) { lastError = e; }
            try {
                Cipher oaep = oaepCipher();
                oaep.init(Cipher.DECRYPT_MODE, getMyPrivateKey());
                return oaep.doFinal(Base64.decode(parts[1], Base64.NO_WRAP));
            } catch (Exception e) { lastError = e; }
        } else {

            try {
                Cipher pkcs1 = pkcs1Cipher();
                pkcs1.init(Cipher.DECRYPT_MODE, getMyPrivateKey());
                return pkcs1.doFinal(Base64.decode(wrappedBase64, Base64.NO_WRAP));
            } catch (Exception e) { lastError = e; }
            try {
                Cipher oaep = oaepCipher();
                oaep.init(Cipher.DECRYPT_MODE, getMyPrivateKey());
                return oaep.doFinal(Base64.decode(wrappedBase64, Base64.NO_WRAP));
            } catch (Exception e) { lastError = e; }
        }
        throw lastError != null ? lastError : new IllegalStateException("unwrap failed");
    }

    private static byte[] randomBytes(int count) {
        byte[] b = new byte[count];
        new SecureRandom().nextBytes(b);
        return b;
    }
}
