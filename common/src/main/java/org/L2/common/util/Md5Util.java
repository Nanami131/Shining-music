package org.L2.common.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Md5Util {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Md5Util() {}

    public static String md5(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(file.getBytes());
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    public static boolean verify(MultipartFile file, String expected) throws IOException {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        String actual = md5(file);
        return actual.equalsIgnoreCase(expected.trim());
    }

    private static String toHex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = HEX[(bytes[i] >> 4) & 0xF];
            result[i * 2 + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(result);
    }
}
