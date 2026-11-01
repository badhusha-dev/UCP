package com.ecommerce.order.accounting.controller;

import com.ecommerce.order.accounting.service.SupplierPayablesService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/internal/accounting")
@RequiredArgsConstructor
public class InternalAccountingController {
    private final SupplierPayablesService service;

    @Value("${SERVICE_TO_SERVICE_TOKEN}")
    private String serviceToken;

    @PostMapping("/supplier-credit-notes")
    public Map<String, Object> recordSupplierCreditNote(@RequestHeader(value = "X-Service-Token", required = false) String token,
                                                        @RequestBody Map<String, Object> request) {
        requireServiceToken(token);
        return service.recordSupplierCreditNote(request);
    }

    @PostMapping("/supplier-payables")
    public Map<String, Object> recordSupplierPayable(@RequestHeader(value = "X-Service-Token", required = false) String token,
                                                     @RequestBody Map<String, Object> request) {
        requireServiceToken(token);
        return service.recordSupplierInvoice(request);
    }

    private void requireServiceToken(String token) {
        if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), serviceToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid service token required.");
        }
    }
}
