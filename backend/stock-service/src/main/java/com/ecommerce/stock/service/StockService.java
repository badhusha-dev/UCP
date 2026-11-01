package com.ecommerce.stock.service;

import com.ecommerce.stock.dto.*;
import com.ecommerce.stock.model.BackInStockAlert;
import com.ecommerce.stock.model.StockItem;
import com.ecommerce.stock.model.StockMovement;
import com.ecommerce.stock.model.StockReservation;
import com.ecommerce.stock.repository.BackInStockAlertRepository;
import com.ecommerce.stock.repository.StockItemRepository;
import com.ecommerce.stock.repository.StockMovementRepository;
import com.ecommerce.stock.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockItemRepository stockItemRepository;
    private final StockReservationRepository stockReservationRepository;
    private final StockMovementRepository stockMovementRepository;
    private final BackInStockAlertRepository backInStockAlertRepository;
    private final StockProducerService producerService;

    @Transactional
    public StockItemDto getInventory(Long productId) {
        StockItem item = stockItemRepository.findByProductId(productId)
                .orElseGet(() -> stockItemRepository.save(StockItem.builder()
                        .productId(productId)
                        .sku("PROD-" + productId)
                        .stockQuantity(0)
                        .reservedQuantity(0)
                        .reorderThreshold(10)
                        .costPrice(BigDecimal.ZERO)
                        .warehouseLocation("DEFAULT")
                        .build()));
        return mapToDto(item);
    }

    @Transactional(readOnly = true)
    public List<StockItemDto> getAllItems() {
        return stockItemRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockItemDto> getBatchInventory(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return Collections.emptyList();
        List<StockItem> items = stockItemRepository.findByProductIdIn(productIds);
        Map<Long, StockItem> map = items.stream().collect(Collectors.toMap(StockItem::getProductId, i -> i));

        return productIds.stream().map(pid -> {
            StockItem it = map.get(pid);
            if (it != null) return mapToDto(it);
            return StockItemDto.builder()
                    .productId(pid)
                    .stockQuantity(0)
                    .reservedQuantity(0)
                    .availableQuantity(0)
                    .reorderThreshold(10)
                    .costPrice(BigDecimal.ZERO)
                    .lowStock(true)
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public StockItemDto updateMetadata(StockMetadataRequest request) {
        StockItem item = stockItemRepository.findByProductId(request.getProductId())
                .orElseGet(() -> StockItem.builder()
                        .productId(request.getProductId())
                        .stockQuantity(0)
                        .reservedQuantity(0)
                        .build());
        item.setSku(request.getSku());
        if (request.getReorderThreshold() != null) item.setReorderThreshold(request.getReorderThreshold());
        if (request.getCostPrice() != null) item.setCostPrice(request.getCostPrice());
        item.setWarehouseLocation(request.getWarehouseLocation());
        item.setUpdatedAt(OffsetDateTime.now());
        return mapToDto(stockItemRepository.save(item));
    }

    @Transactional
    public StockItemDto adjustStock(StockAdjustmentRequest request, String performedBy) {
        StockItem item = stockItemRepository.findByProductId(request.getProductId())
                .orElseGet(() -> StockItem.builder()
                        .productId(request.getProductId())
                        .sku("PROD-" + request.getProductId())
                        .stockQuantity(0)
                        .reservedQuantity(0)
                        .reorderThreshold(10)
                        .costPrice(BigDecimal.ZERO)
                        .warehouseLocation("DEFAULT")
                        .build());

        int prevAvailable = item.getAvailableQuantity();
        int newStock = (item.getStockQuantity() != null ? item.getStockQuantity() : 0) + request.getDelta();
        if (newStock < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resulting stock quantity cannot be negative.");
        }

        item.setStockQuantity(newStock);
        item.setUpdatedAt(OffsetDateTime.now());
        StockItem saved = stockItemRepository.save(item);

        stockMovementRepository.save(StockMovement.builder()
                .productId(request.getProductId())
                .delta(request.getDelta())
                .movementType(request.getMovementType() != null ? request.getMovementType() : "ADJUSTMENT")
                .reason(request.getReason())
                .performedBy(performedBy != null ? performedBy : "SYSTEM")
                .build());

        // Notify back in stock subscribers if stock changed from 0 to > 0
        if (prevAvailable == 0 && saved.getAvailableQuantity() > 0) {
            notifyBackInStockSubscribers(saved.getProductId());
        }

        if (saved.isLowStock()) {
            producerService.publishInventoryEvent("LOW_STOCK_DETECTED", saved.getProductId(), Map.of(
                    "productId", saved.getProductId(),
                    "available", saved.getAvailableQuantity(),
                    "threshold", saved.getReorderThreshold()
            ));
        }

        producerService.publishInventoryEvent("STOCK_ADJUSTED", saved.getProductId(), Map.of(
                "productId", saved.getProductId(),
                "stockQuantity", saved.getStockQuantity(),
                "availableQuantity", saved.getAvailableQuantity(),
                "delta", request.getDelta(),
                "performedBy", performedBy != null ? performedBy : "SYSTEM"
        ));

        return mapToDto(saved);
    }

    @Transactional
    public StockItemDto receivePurchaseOrder(StockReceiptRequest request, String performedBy) {
        StockMovement existingMovement = stockMovementRepository.findByReferenceId(request.getReferenceId()).orElse(null);
        if (existingMovement != null) {
            if (!existingMovement.getProductId().equals(request.getProductId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Receipt reference was already used for another product.");
            }
            return getInventory(request.getProductId());
        }

        StockItem item = stockItemRepository.findByProductId(request.getProductId())
                .orElseGet(() -> stockItemRepository.save(StockItem.builder()
                        .productId(request.getProductId()).sku("PROD-" + request.getProductId())
                        .stockQuantity(0).reservedQuantity(0).reorderThreshold(10)
                        .costPrice(BigDecimal.ZERO).warehouseLocation("DEFAULT").build()));
        int previousStock = item.getStockQuantity() == null ? 0 : item.getStockQuantity();
        int previousAvailable = item.getAvailableQuantity();
        int received = request.getQuantity();
        int newStock = previousStock + received;
        BigDecimal oldCost = item.getCostPrice() == null ? BigDecimal.ZERO : item.getCostPrice();
        BigDecimal weightedCost = BigDecimal.valueOf(previousStock).multiply(oldCost)
                .add(BigDecimal.valueOf(received).multiply(request.getUnitCost()))
                .divide(BigDecimal.valueOf(newStock), 4, java.math.RoundingMode.HALF_UP);
        item.setStockQuantity(newStock);
        item.setCostPrice(weightedCost);
        item.setUpdatedAt(OffsetDateTime.now());
        StockItem saved = stockItemRepository.save(item);
        stockMovementRepository.save(StockMovement.builder()
                .productId(request.getProductId()).delta(received).movementType("PURCHASE_RECEIPT")
                .reason(request.getReason()).performedBy(performedBy).referenceId(request.getReferenceId()).build());

        if (previousAvailable == 0 && saved.getAvailableQuantity() > 0) notifyBackInStockSubscribers(saved.getProductId());
        producerService.publishInventoryEvent("STOCK_RECEIVED", saved.getProductId(), Map.of(
                "productId", saved.getProductId(), "quantity", received, "referenceId", request.getReferenceId(),
                "unitCost", request.getUnitCost().toPlainString(), "stockQuantity", saved.getStockQuantity()));
        return mapToDto(saved);
    }

    @Transactional
    public StockItemDto restockCustomerReturn(StockReturnRequest request, String performedBy) {
        StockMovement existing = stockMovementRepository.findByReferenceId(request.getReferenceId()).orElse(null);
        if (existing != null) {
            if (!existing.getProductId().equals(request.getProductId()) || !existing.getDelta().equals(request.getQuantity())
                    || !"RETURN_RESTOCK".equals(existing.getMovementType())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Return reference was already used for a different stock movement.");
            }
            return getInventory(request.getProductId());
        }

        StockItem item = stockItemRepository.findByProductId(request.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product inventory record not found."));
        int previousAvailable = item.getAvailableQuantity();
        int currentStock = item.getStockQuantity() == null ? 0 : item.getStockQuantity();
        item.setStockQuantity(Math.addExact(currentStock, request.getQuantity()));
        item.setUpdatedAt(OffsetDateTime.now());
        StockItem saved = stockItemRepository.save(item);
        stockMovementRepository.save(StockMovement.builder()
                .productId(request.getProductId()).delta(request.getQuantity()).movementType("RETURN_RESTOCK")
                .reason(request.getReason()).performedBy(performedBy).referenceId(request.getReferenceId()).build());

        if (previousAvailable == 0 && saved.getAvailableQuantity() > 0) notifyBackInStockSubscribers(saved.getProductId());
        producerService.publishInventoryEvent("STOCK_RETURNED", saved.getProductId(), Map.of(
                "productId", saved.getProductId(), "quantity", request.getQuantity(),
                "referenceId", request.getReferenceId(), "stockQuantity", saved.getStockQuantity()));
        return mapToDto(saved);
    }

    @Transactional
    public StockReservationResponse reserveStock(ReserveStockRequest request) {
        StockItem item = stockItemRepository.findByProductId(request.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product inventory record not found."));

        if (item.getAvailableQuantity() < request.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Insufficient stock. Requested: " + request.getQuantity() + ", Available: " + item.getAvailableQuantity());
        }

        item.setReservedQuantity((item.getReservedQuantity() != null ? item.getReservedQuantity() : 0) + request.getQuantity());
        item.setUpdatedAt(OffsetDateTime.now());
        stockItemRepository.save(item);

        int duration = request.getHoldDurationMinutes() != null ? request.getHoldDurationMinutes() : 15;
        String resId = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(duration);

        StockReservation reservation = stockReservationRepository.save(StockReservation.builder()
                .reservationId(resId)
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .orderId(request.getOrderId())
                .status("HOLD")
                .expiresAt(expiresAt)
                .build());

        producerService.publishInventoryEvent("STOCK_RESERVED", request.getProductId(), Map.of(
                "reservationId", resId,
                "quantity", request.getQuantity(),
                "availableQuantity", item.getAvailableQuantity(),
                "expiresAt", expiresAt.toString()
        ));

        return StockReservationResponse.builder()
                .reservationId(resId)
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .status("HOLD")
                .expiresAt(expiresAt)
                .message("Stock reserved successfully for " + duration + " minutes.")
                .build();
    }

    @Transactional
    public StockReservationResponse confirmReservation(String reservationId, Long orderId) {
        StockReservation res = stockReservationRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found: " + reservationId));

        if (!"HOLD".equals(res.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation cannot be confirmed. Current status: " + res.getStatus());
        }

        StockItem item = stockItemRepository.findByProductId(res.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found."));

        // Deduct from both physical and reserved
        item.setReservedQuantity(Math.max(0, (item.getReservedQuantity() != null ? item.getReservedQuantity() : 0) - res.getQuantity()));
        item.setStockQuantity(Math.max(0, (item.getStockQuantity() != null ? item.getStockQuantity() : 0) - res.getQuantity()));
        item.setUpdatedAt(OffsetDateTime.now());
        stockItemRepository.save(item);

        res.setStatus("CONFIRMED");
        if (orderId != null) res.setOrderId(orderId);
        stockReservationRepository.save(res);

        stockMovementRepository.save(StockMovement.builder()
                .productId(res.getProductId())
                .delta(-res.getQuantity())
                .movementType("RESERVATION_CONFIRM")
                .reason("Order #" + orderId + " confirmed")
                .performedBy("ORDER_ORCHESTRATOR")
                .build());

        producerService.publishInventoryEvent("STOCK_CONFIRMED", res.getProductId(), Map.of(
                "reservationId", reservationId,
                "orderId", orderId != null ? orderId : 0L,
                "quantity", res.getQuantity(),
                "availableQuantity", item.getAvailableQuantity()
        ));

        return StockReservationResponse.builder()
                .reservationId(reservationId)
                .productId(res.getProductId())
                .quantity(res.getQuantity())
                .status("CONFIRMED")
                .expiresAt(res.getExpiresAt())
                .message("Stock reservation confirmed.")
                .build();
    }

    @Transactional
    public void releaseReservation(String reservationId) {
        StockReservation res = stockReservationRepository.findByReservationId(reservationId).orElse(null);
        if (res == null || !"HOLD".equals(res.getStatus())) return;

        StockItem item = stockItemRepository.findByProductId(res.getProductId()).orElse(null);
        if (item != null) {
            item.setReservedQuantity(Math.max(0, (item.getReservedQuantity() != null ? item.getReservedQuantity() : 0) - res.getQuantity()));
            item.setUpdatedAt(OffsetDateTime.now());
            stockItemRepository.save(item);
        }

        res.setStatus("RELEASED");
        stockReservationRepository.save(res);

        producerService.publishInventoryEvent("STOCK_RELEASED", res.getProductId(), Map.of(
                "reservationId", reservationId,
                "quantity", res.getQuantity(),
                "availableQuantity", item != null ? item.getAvailableQuantity() : 0
        ));
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expireOldReservations() {
        OffsetDateTime now = OffsetDateTime.now();
        List<StockReservation> expired = stockReservationRepository.findByStatusAndExpiresAtBefore("HOLD", now);
        for (StockReservation res : expired) {
            log.info("Expiring stale reservation [{}] for Product #{}", res.getReservationId(), res.getProductId());
            StockItem item = stockItemRepository.findByProductId(res.getProductId()).orElse(null);
            if (item != null) {
                item.setReservedQuantity(Math.max(0, (item.getReservedQuantity() != null ? item.getReservedQuantity() : 0) - res.getQuantity()));
                item.setUpdatedAt(now);
                stockItemRepository.save(item);
            }
            res.setStatus("EXPIRED");
            stockReservationRepository.save(res);

            producerService.publishInventoryEvent("STOCK_RELEASED", res.getProductId(), Map.of(
                    "reservationId", res.getReservationId(),
                    "reason", "EXPIRED",
                    "availableQuantity", item != null ? item.getAvailableQuantity() : 0
            ));
        }
    }

    @Transactional
    public void subscribeBackInStock(StockAlertSubscriptionRequest req) {
        if (backInStockAlertRepository.findByProductIdAndUserEmailAndStatus(req.getProductId(), req.getEmail(), "ACTIVE").isPresent()) {
            return;
        }
        backInStockAlertRepository.save(BackInStockAlert.builder()
                .productId(req.getProductId())
                .userEmail(req.getEmail())
                .userId(req.getUserId())
                .status("ACTIVE")
                .build());
    }

    @Transactional(readOnly = true)
    public List<StockItemDto> getLowStockAlerts() {
        return stockItemRepository.findLowStockItems().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockReservation> getActiveReservations() {
        return stockReservationRepository.findByStatus("HOLD");
    }

    @Transactional(readOnly = true)
    public List<StockMovement> getRecentMovements() {
        return stockMovementRepository.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public StockAnalyticsSummaryDto getAnalyticsSummary() {
        long totalProducts = stockItemRepository.count();
        long totalUnits = stockItemRepository.sumTotalUnits();
        long lowStock = stockItemRepository.countLowStockItems();
        long outOfStock = stockItemRepository.countOutOfStockItems();
        BigDecimal valuation = stockItemRepository.sumStockValuation();

        List<StockItemDto> alerts = stockItemRepository.findLowStockItems().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return StockAnalyticsSummaryDto.builder()
                .totalProductsTracked(totalProducts)
                .totalPhysicalUnits(totalUnits)
                .lowStockCount(lowStock)
                .outOfStockCount(outOfStock)
                .stockValuation(valuation != null ? valuation : BigDecimal.ZERO)
                .lowStockAlerts(alerts)
                .build();
    }

    @Transactional
    public CsvImportResultDto importCsv(byte[] csvBytes, String performedBy) {
        int total = 0, updated = 0, created = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(csvBytes), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }
                if (line.trim().isEmpty()) continue;
                total++;
                String[] parts = line.split(",", -1);
                if (parts.length < 2) {
                    failed++;
                    errors.add("Line " + total + ": expected productId, stockQuantity");
                    continue;
                }
                try {
                    Long productId = Long.parseLong(parts[0].trim().replace("\"", ""));
                    int stock = Integer.parseInt(parts[1].trim().replace("\"", ""));
                    if (stock < 0) throw new IllegalArgumentException("stockQuantity cannot be negative");

                    Optional<StockItem> existing = stockItemRepository.findByProductId(productId);
                    if (existing.isPresent()) {
                        StockItem item = existing.get();
                        int delta = stock - item.getStockQuantity();
                        item.setStockQuantity(stock);
                        item.setUpdatedAt(OffsetDateTime.now());
                        stockItemRepository.save(item);

                        stockMovementRepository.save(StockMovement.builder()
                                .productId(productId)
                                .delta(delta)
                                .movementType("CSV_IMPORT")
                                .reason("Stock count import")
                                .performedBy(performedBy)
                                .build());
                        producerService.publishInventoryEvent("STOCK_ADJUSTED", productId, Map.of(
                                "productId", productId,
                                "stockQuantity", item.getStockQuantity(),
                                "availableQuantity", item.getAvailableQuantity(),
                                "delta", delta,
                                "performedBy", performedBy != null ? performedBy : "SYSTEM"
                        ));
                        updated++;
                    } else {
                        StockItem item = stockItemRepository.save(StockItem.builder()
                                .productId(productId)
                                .stockQuantity(stock)
                                .reservedQuantity(0)
                                .build());

                        stockMovementRepository.save(StockMovement.builder()
                                .productId(productId)
                                .delta(stock)
                                .movementType("CSV_IMPORT")
                                .reason("Initial stock count import")
                                .performedBy(performedBy)
                                .build());
                        producerService.publishInventoryEvent("STOCK_ADJUSTED", productId, Map.of(
                                "productId", productId,
                                "stockQuantity", item.getStockQuantity(),
                                "availableQuantity", item.getAvailableQuantity(),
                                "delta", stock,
                                "performedBy", performedBy != null ? performedBy : "SYSTEM"
                        ));
                        created++;
                    }
                } catch (Exception ex) {
                    failed++;
                    errors.add("Line " + total + ": " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            errors.add("Fatal CSV parsing error: " + e.getMessage());
        }

        return CsvImportResultDto.builder()
                .totalProcessed(total)
                .updatedCount(updated)
                .createdCount(created)
                .failedCount(failed)
                .errors(errors)
                .build();
    }

    @Transactional(readOnly = true)
    public String exportCsv() {
        List<StockItem> items = stockItemRepository.findAll();
        StringBuilder sb = new StringBuilder();
        sb.append("productId,stockQuantity,reservedQuantity,availableQuantity\r\n");

        for (StockItem i : items) {
            sb.append(sanitizeForCsv(String.valueOf(i.getProductId()))).append(",");
            sb.append(sanitizeForCsv(String.valueOf(i.getStockQuantity()))).append(",");
            sb.append(sanitizeForCsv(String.valueOf(i.getReservedQuantity()))).append(",");
            sb.append(sanitizeForCsv(String.valueOf(i.getAvailableQuantity())));
            sb.append("\r\n");
        }
        return sb.toString();
    }

    private void notifyBackInStockSubscribers(Long productId) {
        List<BackInStockAlert> alerts = backInStockAlertRepository.findByProductIdAndStatus(productId, "ACTIVE");
        for (BackInStockAlert alert : alerts) {
            alert.setStatus("NOTIFIED");
            backInStockAlertRepository.save(alert);
            producerService.publishInventoryEvent("BACK_IN_STOCK_TRIGGERED", productId, Map.of(
                    "productId", productId,
                    "recipientEmail", alert.getUserEmail(),
                    "userId", alert.getUserId() != null ? alert.getUserId() : 0L
            ));
        }
    }

    public static String sanitizeForCsv(String value) {
        if (value == null) return "\"\"";
        String trimmed = value.trim();
        if (trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("-") ||
                trimmed.startsWith("@") || trimmed.startsWith("\t") || trimmed.startsWith("\r")) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private StockItemDto mapToDto(StockItem i) {
        return StockItemDto.builder()
                .id(i.getId())
                .productId(i.getProductId())
                .sku(i.getSku())
                .stockQuantity(i.getStockQuantity() != null ? i.getStockQuantity() : 0)
                .reservedQuantity(i.getReservedQuantity() != null ? i.getReservedQuantity() : 0)
                .availableQuantity(i.getAvailableQuantity())
                .reorderThreshold(i.getReorderThreshold() != null ? i.getReorderThreshold() : 10)
                .costPrice(i.getCostPrice() != null ? i.getCostPrice() : BigDecimal.ZERO)
                .warehouseLocation(i.getWarehouseLocation())
                .lowStock(i.isLowStock())
                .updatedAt(i.getUpdatedAt())
                .build();
    }
}
