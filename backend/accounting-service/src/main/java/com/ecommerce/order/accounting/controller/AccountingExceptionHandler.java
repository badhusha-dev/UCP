package com.ecommerce.order.accounting.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * The accounting service reports rejected input (missing reason, non-positive amount, ...) with
 * {@link IllegalArgumentException}. Without a handler those surfaced as HTTP 500.
 */
@RestControllerAdvice(basePackageClasses = AccountingExceptionHandler.class)
public class AccountingExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidInput(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "Request failed." : exception.getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
