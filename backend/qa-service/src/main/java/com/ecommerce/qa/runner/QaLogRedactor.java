package com.ecommerce.qa.runner;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class QaLogRedactor {
    private static final int MAX_LOG_CHARS = 120_000;
    private static final int MAX_RESULT_CHARS = 12_000;
    private static final Pattern SECRET_PAIR = Pattern.compile("(?i)(authorization|password|secret|access[_-]?token|refresh[_-]?token|cookie|otp|card[_-]?number)(\\s*[=:]\\s*|\\s+)([^\\s,;]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");

    public String redact(String input) { return redact(input, MAX_RESULT_CHARS); }
    public String redactLog(String input) { return redact(input, MAX_LOG_CHARS); }

    private String redact(String input, int limit) {
        if (input == null || input.isBlank()) return "";
        String value = BEARER.matcher(input).replaceAll("Bearer [REDACTED]");
        value = SECRET_PAIR.matcher(value).replaceAll("$1$2[REDACTED]");
        value = JWT.matcher(value).replaceAll("[REDACTED_TOKEN]");
        value = EMAIL.matcher(value).replaceAll("[REDACTED_EMAIL]");
        if (value.length() > limit) value = value.substring(value.length() - limit) + "\n[Earlier output truncated]";
        return value;
    }
}
