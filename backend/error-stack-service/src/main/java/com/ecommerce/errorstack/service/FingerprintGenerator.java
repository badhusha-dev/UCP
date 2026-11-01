package com.ecommerce.errorstack.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FingerprintGenerator {

    private static final Pattern APP_FRAME_PATTERN = Pattern.compile(
            "at (com\\.ecommerce\\.[a-zA-Z0-9_$.]+)\\.([a-zA-Z0-9_$<>]+)\\(([^)]+)\\)");

    private static final Pattern JS_FRAME_PATTERN = Pattern.compile(
            "at (?:[a-zA-Z0-9_$]+ )?\\(?([a-zA-Z0-9_$./-]+):(\\d+)(?::(\\d+))?\\)?");

    public String generateFingerprint(String serviceName, String errorType, String message, String stackTrace) {
        String service = serviceName != null ? serviceName.trim().toLowerCase() : "unknown";
        String type = errorType != null ? errorType.trim() : "GenericError";
        String locationKey = extractSignificantLocation(stackTrace);

        if (locationKey == null || locationKey.isBlank()) {
            // Normalize message: replace variable numbers/UUIDs/timestamps
            locationKey = normalizeMessage(message);
        }

        String raw = service + "|" + type + "|" + locationKey;
        return sha256Hex(raw);
    }

    private String extractSignificantLocation(String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) return null;

        // Try matching Java application package first
        Matcher javaMatcher = APP_FRAME_PATTERN.matcher(stackTrace);
        if (javaMatcher.find()) {
            return javaMatcher.group(1) + "." + javaMatcher.group(2);
        }

        // Try matching JS frame
        Matcher jsMatcher = JS_FRAME_PATTERN.matcher(stackTrace);
        if (jsMatcher.find()) {
            return jsMatcher.group(1);
        }

        // Fall back to top non-empty line of stack trace
        String[] lines = stackTrace.split("\r?\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("at ") && !trimmed.contains("java.base") && !trimmed.contains("org.springframework")) {
                return trimmed;
            }
        }
        return null;
    }

    private String normalizeMessage(String message) {
        if (message == null) return "no-message";
        // Strip out UUIDs, numbers, hex hashes, and IP addresses to prevent fragmentation
        return message
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "{UUID}")
                .replaceAll("\\b0x[0-9a-fA-F]+\\b", "{HEX}")
                .replaceAll("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b", "{IP}")
                .replaceAll("\\b\\d+\\b", "{N}")
                .trim();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback
            return Integer.toHexString(input.hashCode());
        }
    }
}
