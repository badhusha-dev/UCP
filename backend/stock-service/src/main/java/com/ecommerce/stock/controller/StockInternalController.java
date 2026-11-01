package com.ecommerce.stock.controller;

import com.ecommerce.stock.dto.StockItemDto;
import com.ecommerce.stock.dto.StockMetadataRequest;
import com.ecommerce.stock.dto.StockReceiptRequest;
import com.ecommerce.stock.dto.StockReturnRequest;
import com.ecommerce.stock.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/stock")
@RequiredArgsConstructor
public class StockInternalController {

    private final StockService stockService;

    @Value("${SERVICE_TO_SERVICE_TOKEN:local-dev-service-token-change-me}")
    private String serviceToken;

    @PutMapping("/metadata/{productId}")
    public ResponseEntity<StockItemDto> syncInventoryMetadata(
            @RequestHeader(value = "X-Service-Token", required = false) String token,
            @PathVariable Long productId,
            @Valid @RequestBody StockMetadataRequest request) {
        if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), serviceToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid service token required.");
        }
        if (!productId.equals(request.getProductId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product ID in path and body must match.");
        }
        return ResponseEntity.ok(stockService.updateMetadata(request));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<StockItemDto>> lowStockItems(
            @RequestHeader(value = "X-Service-Token", required = false) String token) {
        requireServiceToken(token);
        return ResponseEntity.ok(stockService.getLowStockAlerts());
    }

    @PostMapping("/receive")
    public ResponseEntity<StockItemDto> receivePurchaseOrder(
            @RequestHeader(value = "X-Service-Token", required = false) String token,
            @Valid @RequestBody StockReceiptRequest request) {
        requireServiceToken(token);
        return ResponseEntity.ok(stockService.receivePurchaseOrder(request, "PROCUREMENT_SERVICE"));
    }

    @PostMapping("/returns/restock")
    public ResponseEntity<StockItemDto> restockCustomerReturn(
            @RequestHeader(value = "X-Service-Token", required = false) String token,
            @Valid @RequestBody StockReturnRequest request) {
        requireServiceToken(token);
        return ResponseEntity.ok(stockService.restockCustomerReturn(request, "ORDER_RETURNS_SERVICE"));
    }

    private void requireServiceToken(String token) {
        if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), serviceToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid service token required.");
        }
    }
}
