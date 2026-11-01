package com.ecommerce.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_STACK_URL =
            System.getenv().getOrDefault("ERROR_STACK_URL", "http://error-stack-service:8091");
    private static final String SERVICE_NAME = "user-service";

    // ── Error Stack async fire-and-forget reporter ──────────────────────────
    private void reportToErrorStack(Exception ex, int statusCode) {
        CompletableFuture.runAsync(() -> {
            try {
                String stack = Arrays.stream(ex.getStackTrace())
                        .limit(15)
                        .map(StackTraceElement::toString)
                        .reduce("", (a, b) -> a + "\n\tat " + b);

                String body = "{"
                        + "\"serviceName\":\"" + SERVICE_NAME + "\","
                        + "\"environment\":\"" + System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "docker") + "\","
                        + "\"errorType\":\"" + ex.getClass().getName() + "\","
                        + "\"message\":\"" + escape(ex.getMessage()) + "\","
                        + "\"stackTrace\":\"" + escape(stack) + "\","
                        + "\"severity\":\"" + (statusCode >= 500 ? "ERROR" : "WARNING") + "\","
                        + "\"timestamp\":\"" + Instant.now().toString() + "\""
                        + "}";

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(ERROR_STACK_URL + "/api/errors/ingest"))
                        .timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                client.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // Never let error reporting break the service
            }
        });
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    // ─────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason());
        if (ex.getStatusCode().is5xxServerError()) reportToErrorStack(ex, ex.getStatusCode().value());
        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());
        reportToErrorStack(ex, 500);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
