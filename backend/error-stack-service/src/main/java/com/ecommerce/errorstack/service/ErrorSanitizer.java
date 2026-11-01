package com.ecommerce.errorstack.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class ErrorSanitizer {

    private static final int MAX_MESSAGE_LENGTH = 2048;
    private static final int MAX_STACK_TRACE_LENGTH = 16384;
    private static final int MAX_EXCEPTION_CHAIN_LENGTH = 4096;

    // Credit card pattern
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|6(?:011|5[0-9]{2})[0-9]{12})\\b");

    // Bearer / JWT token pattern
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b");

    // Authorization header / Bearer token
    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(?i)bearer\\s+[a-z0-9._~+/-]+=*", Pattern.CASE_INSENSITIVE);

    // Private key block
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "-----BEGIN[ A-Z0-9_-]*PRIVATE KEY-----.*?-----END[ A-Z0-9_-]*PRIVATE KEY-----", Pattern.DOTALL);

    // Sensitive key-value in JSON/query strings: password, secret, token, apiKey, cvv, pin, creditCard, auth
    private static final Pattern SENSITIVE_KV_PATTERN = Pattern.compile(
            "(?i)(\"?(?:password|pwd|secret|token|api[_-]?key|access[_-]?token|auth|cvv|cvc|pin|ssn|cardNumber)\"?\\s*[:=]\\s*\"?)([^\"&,\\s\r\n}]+)(\"?)");

    // Query param secrets
    private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile(
            "(?i)([?&](?:password|token|secret|apiKey|access_token|key|code)=)([^&\\s]+)");

    // Authorization headers in plain text
    private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile(
            "(?i)(authorization|cookie|set-cookie):\\s*[^\\r\\n]+");

    public String sanitizeMessage(String input) {
        if (input == null) return "";
        String sanitized = redactSensitivePatterns(input);
        if (sanitized.length() > MAX_MESSAGE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_MESSAGE_LENGTH) + "... [TRUNCATED]";
        }
        return sanitized;
    }

    public String sanitizeStackTrace(String input) {
        if (input == null) return null;
        String sanitized = redactSensitivePatterns(input);
        if (sanitized.length() > MAX_STACK_TRACE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_STACK_TRACE_LENGTH) + "\n... [STACK TRACE TRUNCATED]";
        }
        return sanitized;
    }

    public String sanitizeExceptionChain(String input) {
        if (input == null) return null;
        String sanitized = redactSensitivePatterns(input);
        if (sanitized.length() > MAX_EXCEPTION_CHAIN_LENGTH) {
            sanitized = sanitized.substring(0, MAX_EXCEPTION_CHAIN_LENGTH) + "... [TRUNCATED]";
        }
        return sanitized;
    }

    public String sanitizeUri(String uri) {
        if (uri == null) return null;
        return QUERY_PARAM_PATTERN.matcher(uri).replaceAll("$1[REDACTED]");
    }

    public String redactSensitivePatterns(String text) {
        if (text == null || text.isEmpty()) return text;
        String res = text;
        res = PRIVATE_KEY_PATTERN.matcher(res).replaceAll("[REDACTED_PRIVATE_KEY]");
        res = JWT_PATTERN.matcher(res).replaceAll("[REDACTED_JWT_TOKEN]");
        res = BEARER_PATTERN.matcher(res).replaceAll("Bearer [REDACTED_TOKEN]");
        res = CREDIT_CARD_PATTERN.matcher(res).replaceAll("[REDACTED_CREDIT_CARD]");
        res = AUTH_HEADER_PATTERN.matcher(res).replaceAll("$1: [REDACTED]");
        res = SENSITIVE_KV_PATTERN.matcher(res).replaceAll("$1[REDACTED]$3");
        res = QUERY_PARAM_PATTERN.matcher(res).replaceAll("$1[REDACTED]");
        return res;
    }
}
