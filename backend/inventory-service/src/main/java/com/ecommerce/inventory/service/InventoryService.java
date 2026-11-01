package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.*;
import com.ecommerce.inventory.model.BackInStockAlert;
import com.ecommerce.inventory.model.InventoryItem;
import com.ecommerce.inventory.model.StockMovement;
import com.ecommerce.inventory.model.StockReservation;
import com.ecommerce.inventory.repository.BackInStockAlertRepository;
import com.ecommerce.inventory.repository.InventoryItemRepository;
import com.ecommerce.inventory.repository.StockMovementRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
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
public class InventoryService {
    private static final UUID LEGACY_TENANT_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final InventoryItemRepository inventoryItemRepository;
    private final StockReservationRepository stockReservationRepository;
    private final StockMovementRepository stockMovementRepository;
    private final BackInStockAlertRepository backInStockAlertRepository;
    private final InventoryProducerService producerService;
    private final StockMetadataSyncClient stockMetadataSyncClient;

    @Transactional
    public InventoryItemDto getInventory(UUID tenantId, Long productId) {
        InventoryItem item = inventoryItemRepository.findByTenantIdAndProductId(tenantId, productId)
                .orElseGet(() -> inventoryItemRepository.save(InventoryItem.builder()
                        .tenantId(tenantId)
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
    public List<InventoryItemDto> getAllItems(UUID tenantId) {
        return inventoryItemRepository.findByTenantId(tenantId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public InventoryItemDto createMetadata(UUID tenantId, InventoryMetadataRequest request, String performedBy) {
        validateMetadata(request);
        if (inventoryItemRepository.findByTenantIdAndProductId(tenantId, request.getProductId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inventory metadata already exists for product " + request.getProductId());
        }

        InventoryItem item = InventoryItem.builder()
                .tenantId(tenantId)
                .productId(request.getProductId())
                .sku(normalize(request.getSku()))
                .stockQuantity(0)
                .reservedQuantity(0)
                .reorderThreshold(request.getReorderThreshold())
                .costPrice(request.getCostPrice())
                .warehouseLocation(normalize(request.getWarehouseLocation()))
                .active(true)
                .build();
        InventoryItem saved = inventoryItemRepository.save(item);
        syncLegacyMetadata(saved);
        publishMetadataEvent(saved, performedBy);
        return mapToDto(saved);
    }

    @Transactional
    public InventoryItemDto updateMetadata(UUID tenantId, Long productId, InventoryMetadataRequest request, String performedBy) {
        validateMetadata(request);
        if (!productId.equals(request.getProductId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product ID in path and body must match.");
        }
        InventoryItem item = inventoryItemRepository.findByTenantIdAndProductId(tenantId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory metadata not found for product " + productId));
        if (!item.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Restore archived metadata before editing it.");
        }

        applyMetadata(item, request);
        item.setUpdatedAt(OffsetDateTime.now());
        InventoryItem saved = inventoryItemRepository.save(item);
        syncLegacyMetadata(saved);
        publishMetadataEvent(saved, performedBy);
        return mapToDto(saved);
    }

    @Transactional
    public void archiveMetadata(UUID tenantId, Long productId, String performedBy) {
        InventoryItem item = inventoryItemRepository.findByTenantIdAndProductId(tenantId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory metadata not found for product " + productId));
        if (!item.isActive()) return;
        if ((item.getStockQuantity() != null && item.getStockQuantity() > 0)
                || (item.getReservedQuantity() != null && item.getReservedQuantity() > 0)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Metadata can only be archived when physical and reserved stock are both zero.");
        }
        item.setActive(false);
        item.setUpdatedAt(OffsetDateTime.now());
        InventoryItem saved = inventoryItemRepository.save(item);
        publishMetadataEvent(saved, performedBy);
    }

    @Transactional
    public InventoryItemDto restoreMetadata(UUID tenantId, Long productId, String performedBy) {
        InventoryItem item = inventoryItemRepository.findByTenantIdAndProductId(tenantId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory metadata not found for product " + productId));
        if (!item.isActive()) {
            item.setActive(true);
            item.setUpdatedAt(OffsetDateTime.now());
            item = inventoryItemRepository.save(item);
            syncLegacyMetadata(item);
            publishMetadataEvent(item, performedBy);
        }
        return mapToDto(item);
    }

    @Transactional(readOnly = true)
    public List<InventoryItemDto> getBatchInventory(UUID tenantId, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return Collections.emptyList();
        List<InventoryItem> items = inventoryItemRepository.findByTenantIdAndProductIdIn(tenantId, productIds);
        Map<Long, InventoryItem> map = items.stream().collect(Collectors.toMap(InventoryItem::getProductId, i -> i));

        return productIds.stream().map(pid -> {
            InventoryItem it = map.get(pid);
            if (it != null) return mapToDto(it);
            return InventoryItemDto.builder()
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
    public InventoryItemDto adjustStock(UUID tenantId, StockAdjustmentRequest request, String performedBy) {
        InventoryItem item = inventoryItemRepository.findByTenantIdAndProductId(tenantId, request.getProductId())
                .orElseGet(() -> InventoryItem.builder()
                        .tenantId(tenantId)
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
        InventoryItem saved = inventoryItemRepository.save(item);

        stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId)
                .productId(request.getProductId())
                .delta(request.getDelta())
                .movementType(request.getMovementType() != null ? request.getMovementType() : "ADJUSTMENT")
                .reason(request.getReason())
                .performedBy(performedBy != null ? performedBy : "SYSTEM")
                .build());

        // Notify back in stock subscribers if stock changed from 0 to > 0
        if (prevAvailable == 0 && saved.getAvailableQuantity() > 0) {
            notifyBackInStockSubscribers(tenantId, saved.getProductId());
        }

        if (saved.isLowStock()) {
            producerService.publishInventoryEvent("LOW_STOCK_DETECTED", saved.getProductId(), Map.of(
                    "tenantId", tenantId.toString(),
                    "productId", saved.getProductId(),
                    "available", saved.getAvailableQuantity(),
                    "threshold", saved.getReorderThreshold()
            ));
        }

        producerService.publishInventoryEvent("STOCK_ADJUSTED", saved.getProductId(), Map.of(
                "tenantId", tenantId.toString(),
                "productId", saved.getProductId(),
                "stockQuantity", saved.getStockQuantity(),
                "delta", request.getDelta(),
                "performedBy", performedBy != null ? performedBy : "SYSTEM"
        ));

        return mapToDto(saved);
    }

    @Transactional
    public StockReservationResponse reserveStock(UUID tenantId, ReserveStockRequest request) {
        if (request.getIdempotencyKey() != null) {
            var existing = stockReservationRepository.findByTenantIdAndIdempotencyKey(tenantId, request.getIdempotencyKey());
            if (existing.isPresent()) {
                StockReservation r = existing.get();
                return StockReservationResponse.builder().reservationId(r.getReservationId()).productId(r.getProductId())
                        .quantity(r.getQuantity()).status(r.getStatus()).expiresAt(r.getExpiresAt())
                        .message("Idempotent replay of an existing reservation.").build();
            }
        }
        InventoryItem item = inventoryItemRepository.findByTenantIdAndProductId(tenantId, request.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product inventory record not found."));

        // Atomic conditional update: two simultaneous requests can never both take the last unit.
        int reservedQty = request.getQuantity();
        int backorder = 0;
        if (inventoryItemRepository.tryReserve(tenantId, request.getProductId(), reservedQty) == 0) {
            int available = inventoryItemRepository.findByTenantIdAndProductId(tenantId, request.getProductId())
                    .map(InventoryItem::getAvailableQuantity).orElse(0);
            if (!Boolean.TRUE.equals(request.getAllowPartial()) || available <= 0
                    || inventoryItemRepository.tryReserve(tenantId, request.getProductId(), available) == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Insufficient stock. Requested: " + request.getQuantity() + ", Available: " + available);
            }
            backorder = request.getQuantity() - available;
            reservedQty = available;
        }

        int duration = request.getHoldDurationMinutes() != null ? request.getHoldDurationMinutes() : 15;
        String resId = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(duration);

        StockReservation reservation = stockReservationRepository.save(StockReservation.builder()
                .tenantId(tenantId)
                .reservationId(resId)
                .productId(request.getProductId())
                .quantity(reservedQty)
                .backorderQuantity(backorder)
                .idempotencyKey(request.getIdempotencyKey())
                .orderId(request.getOrderId())
                .status("HOLD")
                .expiresAt(expiresAt)
                .build());

        producerService.publishInventoryEvent("STOCK_RESERVED", request.getProductId(), Map.of(
                "tenantId", tenantId.toString(),
                "reservationId", resId,
                "quantity", reservedQty,
                "backorderQuantity", backorder,
                "expiresAt", expiresAt.toString()
        ));

        return StockReservationResponse.builder()
                .reservationId(resId)
                .productId(request.getProductId())
                .quantity(reservedQty)
                .status("HOLD")
                .expiresAt(expiresAt)
                .message(backorder > 0 ? "Partially reserved; backorder " + backorder + "."
                        : "Stock reserved successfully for " + duration + " minutes.")
                .build();
    }

    @Transactional
    public StockReservationResponse confirmReservation(UUID tenantId, String reservationId, Long orderId) {
        StockReservation res = stockReservationRepository.findByTenantIdAndReservationId(tenantId, reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found: " + reservationId));

        if (!"HOLD".equals(res.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation cannot be confirmed. Current status: " + res.getStatus());
        }

        InventoryItem item = inventoryItemRepository.findByTenantIdAndProductId(tenantId, res.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found."));

        // Deduct from both physical and reserved
        item.setReservedQuantity(Math.max(0, (item.getReservedQuantity() != null ? item.getReservedQuantity() : 0) - res.getQuantity()));
        item.setStockQuantity(Math.max(0, (item.getStockQuantity() != null ? item.getStockQuantity() : 0) - res.getQuantity()));
        item.setUpdatedAt(OffsetDateTime.now());
        inventoryItemRepository.save(item);

        res.setStatus("CONFIRMED");
        if (orderId != null) res.setOrderId(orderId);
        stockReservationRepository.save(res);

        stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId)
                .productId(res.getProductId())
                .delta(-res.getQuantity())
                .movementType("RESERVATION_CONFIRM")
                .reason("Order #" + orderId + " confirmed")
                .performedBy("ORDER_ORCHESTRATOR")
                .build());

        producerService.publishInventoryEvent("STOCK_CONFIRMED", res.getProductId(), Map.of(
                "tenantId", tenantId.toString(),
                "reservationId", reservationId,
                "orderId", orderId != null ? orderId : 0L,
                "quantity", res.getQuantity()
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
    public void releaseReservation(UUID tenantId, String reservationId) {
        StockReservation res = stockReservationRepository.findByTenantIdAndReservationId(tenantId, reservationId).orElse(null);
        if (res == null || !"HOLD".equals(res.getStatus())) return;

        inventoryItemRepository.releaseReserved(tenantId, res.getProductId(), res.getQuantity());

        res.setStatus("RELEASED");
        stockReservationRepository.save(res);

        producerService.publishInventoryEvent("STOCK_RELEASED", res.getProductId(), Map.of(
                "tenantId", tenantId.toString(),
                "reservationId", reservationId,
                "quantity", res.getQuantity()
        ));
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expireOldReservations() {
        OffsetDateTime now = OffsetDateTime.now();
        List<StockReservation> expired = stockReservationRepository.findByStatusAndExpiresAtBefore("HOLD", now);
        for (StockReservation res : expired) {
            log.info("Expiring stale reservation [{}] for Product #{}", res.getReservationId(), res.getProductId());
            InventoryItem item = inventoryItemRepository.findByTenantIdAndProductId(res.getTenantId(), res.getProductId()).orElse(null);
            if (item != null) {
                item.setReservedQuantity(Math.max(0, (item.getReservedQuantity() != null ? item.getReservedQuantity() : 0) - res.getQuantity()));
                item.setUpdatedAt(now);
                inventoryItemRepository.save(item);
            }
            res.setStatus("EXPIRED");
            stockReservationRepository.save(res);

            producerService.publishInventoryEvent("STOCK_RELEASED", res.getProductId(), Map.of(
                    "tenantId", res.getTenantId().toString(),
                    "reservationId", res.getReservationId(),
                    "reason", "EXPIRED"
            ));
        }
    }

    @Transactional
    public void subscribeBackInStock(UUID tenantId, StockAlertSubscriptionRequest req) {
        if (backInStockAlertRepository.findByTenantIdAndProductIdAndUserEmailAndStatus(tenantId, req.getProductId(), req.getEmail(), "ACTIVE").isPresent()) {
            return;
        }
        backInStockAlertRepository.save(BackInStockAlert.builder()
                .tenantId(tenantId)
                .productId(req.getProductId())
                .userEmail(req.getEmail())
                .userId(req.getUserId())
                .status("ACTIVE")
                .build());
    }

    @Transactional(readOnly = true)
    public List<InventoryItemDto> getLowStockAlerts(UUID tenantId) {
        return inventoryItemRepository.findLowStockItems(tenantId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockReservation> getActiveReservations(UUID tenantId) {
        return stockReservationRepository.findByTenantIdAndStatus(tenantId, "HOLD");
    }

    @Transactional(readOnly = true)
    public List<StockMovement> getRecentMovements(UUID tenantId) {
        return stockMovementRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> getMovements(UUID tenantId, Long productId) {
        return stockMovementRepository.findByTenantIdAndProductIdOrderByCreatedAtDesc(tenantId, productId);
    }

    @Transactional(readOnly = true)
    public InventoryAnalyticsSummaryDto getAnalyticsSummary(UUID tenantId) {
        long totalProducts = inventoryItemRepository.countByTenantId(tenantId);
        long totalUnits = inventoryItemRepository.sumTotalUnits(tenantId);
        long lowStock = inventoryItemRepository.countLowStockItems(tenantId);
        long outOfStock = inventoryItemRepository.countOutOfStockItems(tenantId);
        BigDecimal valuation = inventoryItemRepository.sumStockValuation(tenantId);

        List<InventoryItemDto> alerts = inventoryItemRepository.findLowStockItems(tenantId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return InventoryAnalyticsSummaryDto.builder()
                .totalProductsTracked(totalProducts)
                .totalPhysicalUnits(totalUnits)
                .lowStockCount(lowStock)
                .outOfStockCount(outOfStock)
                .stockValuation(valuation != null ? valuation : BigDecimal.ZERO)
                .lowStockAlerts(alerts)
                .build();
    }

    @Transactional
    public CsvImportResultDto importCsv(UUID tenantId, byte[] csvBytes, String performedBy) {
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
                if (parts.length < 5) {
                    failed++;
                    errors.add("Line " + total + ": expected productId, sku, reorderThreshold, costPrice, warehouseLocation");
                    continue;
                }
                try {
                    Long productId = Long.parseLong(parts[0].trim().replace("\"", ""));
                    String sku = parts[1].trim().replace("\"", "");
                    int reorder = Integer.parseInt(parts[2].trim().replace("\"", ""));
                    BigDecimal cost = new BigDecimal(parts[3].trim().replace("\"", ""));
                    String loc = parts[4].trim().replace("\"", "");

                    Optional<InventoryItem> existing = inventoryItemRepository.findByTenantIdAndProductId(tenantId, productId);
                    if (existing.isPresent()) {
                        InventoryItem item = existing.get();
                        item.setSku(sku);
                        item.setReorderThreshold(reorder);
                        item.setCostPrice(cost);
                        item.setWarehouseLocation(loc);
                        item.setUpdatedAt(OffsetDateTime.now());
                        InventoryItem saved = inventoryItemRepository.save(item);
                        syncLegacyMetadata(saved);
                        publishMetadataEvent(saved, performedBy);
                        updated++;
                    } else {
                        InventoryItem item = inventoryItemRepository.save(InventoryItem.builder()
                                .tenantId(tenantId)
                                .productId(productId)
                                .sku(sku)
                                .stockQuantity(0)
                                .reservedQuantity(0)
                                .reorderThreshold(reorder)
                                .costPrice(cost)
                                .warehouseLocation(loc)
                                .build());
                        syncLegacyMetadata(item);
                        publishMetadataEvent(item, performedBy);
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
    public String exportCsv(UUID tenantId) {
        List<InventoryItem> items = inventoryItemRepository.findByTenantId(tenantId);
        StringBuilder sb = new StringBuilder();
        sb.append("productId,sku,reorderThreshold,costPrice,warehouseLocation\r\n");

        for (InventoryItem i : items) {
            sb.append(sanitizeForCsv(String.valueOf(i.getProductId()))).append(",");
            sb.append(sanitizeForCsv(i.getSku())).append(",");
            sb.append(sanitizeForCsv(String.valueOf(i.getReorderThreshold()))).append(",");
            sb.append(sanitizeForCsv(i.getCostPrice() != null ? i.getCostPrice().toPlainString() : "0.00")).append(",");
            sb.append(sanitizeForCsv(i.getWarehouseLocation())).append("\r\n");
        }
        return sb.toString();
    }

    private void notifyBackInStockSubscribers(UUID tenantId, Long productId) {
        List<BackInStockAlert> alerts = backInStockAlertRepository.findByTenantIdAndProductIdAndStatus(tenantId, productId, "ACTIVE");
        for (BackInStockAlert alert : alerts) {
            alert.setStatus("NOTIFIED");
            backInStockAlertRepository.save(alert);
            producerService.publishInventoryEvent("BACK_IN_STOCK_TRIGGERED", productId, Map.of(
                    "tenantId", tenantId.toString(),
                    "productId", productId,
                    "recipientEmail", alert.getUserEmail(),
                    "userId", alert.getUserId() != null ? alert.getUserId() : 0L
            ));
        }
    }

    private void publishMetadataEvent(InventoryItem item, String performedBy) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenantId", item.getTenantId().toString());
        metadata.put("productId", item.getProductId());
        metadata.put("sku", item.getSku() != null ? item.getSku() : "");
        metadata.put("reorderThreshold", item.getReorderThreshold());
        metadata.put("costPrice", item.getCostPrice() != null ? item.getCostPrice().toPlainString() : "0.00");
        metadata.put("warehouseLocation", item.getWarehouseLocation() != null ? item.getWarehouseLocation() : "");
        metadata.put("active", item.isActive());
        metadata.put("performedBy", performedBy != null ? performedBy : "STAFF");
        producerService.publishInventoryEvent("INVENTORY_METADATA_UPDATED", item.getProductId(), metadata);
    }

    private void syncLegacyMetadata(InventoryItem item) {
        // The downstream Stock service is not tenant-aware yet. Avoid overwriting another client's
        // product metadata until that service has its own tenant migration and scoped contract.
        if (LEGACY_TENANT_ID.equals(item.getTenantId())) stockMetadataSyncClient.sync(item);
    }

    private void validateMetadata(InventoryMetadataRequest request) {
        if (request == null || request.getProductId() == null || request.getProductId() <= 0
                || request.getReorderThreshold() == null || request.getReorderThreshold() < 0
                || request.getCostPrice() == null || request.getCostPrice().signum() < 0
                || request.getCostPrice().scale() > 4
                || request.getSku() != null && request.getSku().length() > 64
                || request.getWarehouseLocation() != null && request.getWarehouseLocation().length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a valid product ID, non-negative reorder threshold and cost, and metadata within supported field lengths.");
        }
    }

    private void applyMetadata(InventoryItem item, InventoryMetadataRequest request) {
        item.setSku(normalize(request.getSku()));
        item.setReorderThreshold(request.getReorderThreshold());
        item.setCostPrice(request.getCostPrice());
        item.setWarehouseLocation(normalize(request.getWarehouseLocation()));
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private InventoryItemDto mapToDto(InventoryItem i) {
        return InventoryItemDto.builder()
                .id(i.getId())
                .productId(i.getProductId())
                .sku(i.getSku())
                .stockQuantity(i.getStockQuantity() != null ? i.getStockQuantity() : 0)
                .reservedQuantity(i.getReservedQuantity() != null ? i.getReservedQuantity() : 0)
                .availableQuantity(i.getAvailableQuantity())
                .reorderThreshold(i.getReorderThreshold() != null ? i.getReorderThreshold() : 10)
                .costPrice(i.getCostPrice() != null ? i.getCostPrice() : BigDecimal.ZERO)
                .warehouseLocation(i.getWarehouseLocation())
                .active(i.isActive())
                .lowStock(i.isLowStock())
                .updatedAt(i.getUpdatedAt())
                .build();
    }
}
