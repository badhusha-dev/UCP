package com.ecommerce.stock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class LegacyInventoryDataImporter implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final StockProducerService producerService;

    @Value("${stock.legacy-import.enabled:false}")
    private boolean enabled;

    @Value("${stock.legacy-import.url:}")
    private String sourceUrl;

    @Value("${stock.legacy-import.username:}")
    private String sourceUsername;

    @Value("${stock.legacy-import.password:}")
    private String sourcePassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled || sourceUrl == null || sourceUrl.isBlank()) {
            return;
        }

        Integer alreadyImported = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_legacy_import WHERE source = ?", Integer.class, "inventory_db");
        if (alreadyImported != null && alreadyImported > 0) {
            log.info("Legacy inventory data was already imported into the Stock Service database.");
            return;
        }

        try (var source = DriverManager.getConnection(sourceUrl, sourceUsername, sourcePassword)) {
            AtomicInteger itemCount = new AtomicInteger();
            try (var statement = source.prepareStatement("""
                    SELECT product_id, sku, stock_quantity, reserved_quantity,
                           reorder_threshold, cost_price, warehouse_location
                    FROM inventory_item ORDER BY product_id
                    """); var rows = statement.executeQuery()) {
                while (rows.next()) {
                    long productId = rows.getLong("product_id");
                    int stockQuantity = rows.getInt("stock_quantity");
                    int reservedQuantity = rows.getInt("reserved_quantity");
                    jdbcTemplate.update("""
                            INSERT INTO stock_item
                                (product_id, sku, stock_quantity, reserved_quantity,
                                 reorder_threshold, cost_price, warehouse_location)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (product_id) DO UPDATE SET
                                sku = EXCLUDED.sku,
                                stock_quantity = EXCLUDED.stock_quantity,
                                reserved_quantity = EXCLUDED.reserved_quantity,
                                reorder_threshold = EXCLUDED.reorder_threshold,
                                cost_price = EXCLUDED.cost_price,
                                warehouse_location = EXCLUDED.warehouse_location,
                                updated_at = CURRENT_TIMESTAMP
                            """,
                            productId, rows.getString("sku"), stockQuantity,
                            reservedQuantity, rows.getInt("reorder_threshold"),
                            rows.getBigDecimal("cost_price"), rows.getString("warehouse_location"));
                    producerService.publishInventoryEvent("STOCK_ADJUSTED", productId, java.util.Map.of(
                            "productId", productId,
                            "stockQuantity", stockQuantity,
                            "availableQuantity", Math.max(0, stockQuantity - reservedQuantity),
                            "delta", 0,
                            "performedBy", "LEGACY_DATA_IMPORT"));
                    java.util.Map<String, Object> metadata = new java.util.HashMap<>();
                    metadata.put("productId", productId);
                    metadata.put("sku", rows.getString("sku") != null ? rows.getString("sku") : "");
                    metadata.put("reorderThreshold", rows.getInt("reorder_threshold"));
                    metadata.put("costPrice", rows.getBigDecimal("cost_price") != null ? rows.getBigDecimal("cost_price").toPlainString() : "0.00");
                    metadata.put("warehouseLocation", rows.getString("warehouse_location") != null ? rows.getString("warehouse_location") : "");
                    producerService.publishInventoryEvent("INVENTORY_METADATA_UPDATED", productId, metadata);
                    itemCount.incrementAndGet();
                }
            }

            AtomicInteger reservationCount = new AtomicInteger();
            try (var statement = source.prepareStatement("""
                    SELECT reservation_id, product_id, quantity, order_id, status, expires_at, created_at
                    FROM stock_reservation ORDER BY id
                    """); var rows = statement.executeQuery()) {
                while (rows.next()) {
                    jdbcTemplate.update("""
                            INSERT INTO stock_reservation
                                (reservation_id, product_id, quantity, order_id, status, expires_at, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (reservation_id) DO UPDATE SET
                                product_id = EXCLUDED.product_id,
                                quantity = EXCLUDED.quantity,
                                order_id = EXCLUDED.order_id,
                                status = EXCLUDED.status,
                                expires_at = EXCLUDED.expires_at
                            """,
                            rows.getString("reservation_id"), rows.getLong("product_id"), rows.getInt("quantity"),
                            (Long) rows.getObject("order_id"), rows.getString("status"),
                            rows.getObject("expires_at", OffsetDateTime.class),
                            rows.getObject("created_at", OffsetDateTime.class));
                    reservationCount.incrementAndGet();
                }
            }

            AtomicInteger movementCount = new AtomicInteger();
            try (var statement = source.prepareStatement("""
                    SELECT product_id, delta, movement_type, reason, performed_by, created_at
                    FROM stock_movement ORDER BY id
                    """); var rows = statement.executeQuery()) {
                while (rows.next()) {
                    jdbcTemplate.update("""
                            INSERT INTO stock_movement
                                (product_id, delta, movement_type, reason, performed_by, created_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                            rows.getLong("product_id"), rows.getInt("delta"), rows.getString("movement_type"),
                            rows.getString("reason"), rows.getString("performed_by"),
                            rows.getObject("created_at", OffsetDateTime.class));
                    movementCount.incrementAndGet();
                }
            }

            try (var statement = source.prepareStatement("""
                    SELECT product_id, user_email, user_id, status, created_at
                    FROM back_in_stock_alert ORDER BY id
                    """); var rows = statement.executeQuery()) {
                while (rows.next()) {
                    jdbcTemplate.update("""
                            INSERT INTO back_in_stock_alert (product_id, user_email, user_id, status, created_at)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                            rows.getLong("product_id"), rows.getString("user_email"),
                            (Long) rows.getObject("user_id"), rows.getString("status"),
                            rows.getObject("created_at", OffsetDateTime.class));
                }
            }

            jdbcTemplate.update("INSERT INTO stock_legacy_import (source) VALUES (?)", "inventory_db");
            log.info("Imported {} stock items, {} reservations and {} movements from inventory_db.",
                    itemCount.get(), reservationCount.get(), movementCount.get());
        }
    }
}
