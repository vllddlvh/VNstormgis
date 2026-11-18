package vn.weathergis.subscription.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/** Tiện ích sinh mã xác thực OTP 6 chữ số, token URL-safe 256-bit và băm SHA-256. */
public final class SubscriptionTokenCodec {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SubscriptionTokenCodec() {}

    public static String generateSixDigitOtp() {
        int value = SECURE_RANDOM.nextInt(1_000_000);
        return String.format(Locale.ROOT, "%06d", value);
    }

    public static String generateUrlSafeToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 không khả dụng trên JVM", ex);
        }
    }

    public static String normalizeEmail(String rawEmail) {
        return rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    public static String maskEmail(String email) {
        if (email == null) {
            return "";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.substring(0, 2) + "***" + domain;
    }
}
