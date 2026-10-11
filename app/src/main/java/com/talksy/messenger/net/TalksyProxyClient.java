package com.talksy.messenger.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class TalksyProxyClient {

    private static final int IV_LEN = 16;
    private static final int CONNECT_TIMEOUT_MS = 8000;

    public static class Tunnel {
        public final Socket socket;
        public final InputStream in;
        public final OutputStream out;
        Tunnel(Socket s, InputStream i, OutputStream o) { socket = s; in = i; out = o; }
        public void close() { try { socket.close(); } catch (IOException ignored) {} }
    }

    public static Tunnel connect(String proxyHost, int proxyPort, String secret,
                                  String targetHost, int targetPort) throws IOException {
        byte[] key = deriveKey(secret);

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(proxyHost, proxyPort), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(CONNECT_TIMEOUT_MS);

        byte[] iv = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);

        DataOutputStream rawOut = new DataOutputStream(socket.getOutputStream());
        rawOut.write(iv);
        rawOut.flush();

        try {

            byte[] ivReverse = addCounterOffset(iv);

            Cipher encryptCipher = Cipher.getInstance("AES/CTR/NoPadding");
            encryptCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            Cipher decryptCipher = Cipher.getInstance("AES/CTR/NoPadding");
            decryptCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(ivReverse));

            OutputStream encOut = new CipherOutputStream(rawOut, encryptCipher);
            InputStream decIn = new CipherInputStream(socket.getInputStream(), decryptCipher);

            byte[] hostBytes = targetHost.getBytes(StandardCharsets.UTF_8);
            DataOutputStream handshake = new DataOutputStream(encOut);
            handshake.writeShort(hostBytes.length);
            handshake.write(hostBytes);
            handshake.writeShort(targetPort);
            handshake.flush();

            DataInputStream ack = new DataInputStream(decIn);
            int status = ack.readUnsignedByte();
            if (status != 0x01) {
                socket.close();
                throw new IOException("Talksy-proxy: server rejected the handshake (code " + status + ")");
            }

            return new Tunnel(socket, decIn, encOut);
        } catch (Exception e) {
            socket.close();
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Talksy-proxy encryption error: " + e.getMessage(), e);
        }
    }

    public static boolean test(String proxyHost, int proxyPort, String secret) {
        try {
            Tunnel t = connect(proxyHost, proxyPort, secret, "example.com", 443);
            t.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] deriveKey(String secret) throws IOException {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return sha256.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static byte[] addCounterOffset(byte[] iv) {
        java.math.BigInteger value = new java.math.BigInteger(1, iv);
        java.math.BigInteger offset = java.math.BigInteger.ONE.shiftLeft(64);
        java.math.BigInteger mod = java.math.BigInteger.ONE.shiftLeft(128);
        value = value.add(offset).mod(mod);
        byte[] raw = value.toByteArray();
        byte[] out = new byte[16];
        int srcPos = Math.max(0, raw.length - 16);
        int len = raw.length - srcPos;
        System.arraycopy(raw, srcPos, out, 16 - len, len);
        return out;
    }
}
