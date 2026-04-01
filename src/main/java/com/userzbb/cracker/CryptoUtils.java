package com.userzbb.cracker;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Cryptographic utilities for AES-128-CBC encryption and HMAC-SHA256 signing.
 * Matches src/utils/crypto.py from UJN_lib_scaper.
 */
public class CryptoUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Encrypts plaintext using AES-128-CBC with PKCS5 padding.
     * Returns base64-encoded ciphertext with "_encrypt" suffix.
     * Matches the frontend implementation.
     */
    public static String encryptAES(String plaintext) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(Config.AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(Config.AES_IV.getBytes(StandardCharsets.UTF_8));

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        String encoded = Base64.getEncoder().encodeToString(encrypted);

        return encoded + "_encrypt";
    }

    /**
     * Generates HMAC-SHA256 signature headers.
     * Message format: seat::<UUID>::<Timestamp>::<Method>
     * Matches src/utils/crypto.py generate_headers().
     */
    public static String[] generateHeaders(String method) throws Exception {
        String reqId = UUID.randomUUID().toString();
        String reqDate = String.valueOf(System.currentTimeMillis());

        // Message format: seat::<UUID>::<Timestamp>::<Method>
        String message = String.format("seat::%s::%s::%s", reqId, reqDate, method);

        // HMAC-SHA256
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            Config.HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        String signature = bytesToHex(hmacBytes);

        return new String[]{
            reqId,       // x-request-id
            reqDate,     // x-request-date
            signature,   // x-hmac-request-key
        };
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
