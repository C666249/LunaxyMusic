package com.xingyu.music.data;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class NeteaseCrypto {
    private static final String KEY1 = "0CoJUm6Qyw8W8jud";
    private static final String IV = "0102030405060708";
    private static final String PUBKEY = "010001";
    private static final String MODULUS = "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7";
    private static final char[] CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private NeteaseCrypto() {}

    public static String[] weapi(String text) throws Exception {
        String secret = randomKey();
        String first = aes(text, KEY1);
        return new String[]{aes(first, secret), rsa(secret)};
    }

    private static String aes(String text, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8)));
        return Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static String rsa(String key) {
        byte[] bytes = new StringBuilder(key).reverse().toString().getBytes(StandardCharsets.UTF_8);
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format("%02x", b & 0xff));
        String result = new BigInteger(hex.toString(), 16)
                .modPow(new BigInteger(PUBKEY, 16), new BigInteger(MODULUS, 16)).toString(16);
        while (result.length() < 256) result = "0" + result;
        return result;
    }

    public static String eapi(String url, String text) throws Exception {
        String message = "nobody" + url + "use" + text + "md5forencrypt";
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digestBytes = md.digest(message.getBytes(StandardCharsets.UTF_8));
        StringBuilder digest = new StringBuilder(32);
        for (byte b : digestBytes) digest.append(String.format("%02x", b & 0xff));
        String data = url + "-36cd479b6b5-" + text + "-36cd479b6b5-" + digest;
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec("e82ckenh8dichen8".getBytes(StandardCharsets.UTF_8), "AES"));
        byte[] out = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(out.length * 2);
        for (byte b : out) hex.append(String.format("%02X", b & 0xff));
        return hex.toString();
    }

    private static String randomKey() {
        StringBuilder s = new StringBuilder(16);
        for (int i = 0; i < 16; i++) s.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        return s.toString();
    }
}
