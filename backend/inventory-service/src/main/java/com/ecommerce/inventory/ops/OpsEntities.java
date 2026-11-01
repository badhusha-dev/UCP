package com.ecommerce.inventory.ops;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "wms_warehouse", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "code"})) @Data
class Warehouse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(nullable = false, length = 40) String code;
    @Column(nullable = false, length = 160) String name;
    @Column(name = "store_id", length = 64) String storeId;
    @Column(length = 255) String address;
    @Column(name = "capacity_units", nullable = false) int capacityUnits;
    @Column(name = "manager_email", length = 180) String managerEmail;
    @Column(nullable = false) boolean active = true;
    @Column(name = "created_at", nullable = false) OffsetDateTime createdAt = OffsetDateTime.now();
}

@Entity @Table(name = "wms_location") @Data
class WarehouseLocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "warehouse_id", nullable = false) Long warehouseId;
    @Column(nullable = false, length = 40) String code;
    @Column(name = "capacity_units", nullable = false) int capacityUnits;
    @Column(name = "created_at", nullable = false) OffsetDateTime createdAt = OffsetDateTime.now();
}

@Entity @Table(name = "wms_stock", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "warehouse_id", "product_id"})) @Data
class WarehouseStock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "warehouse_id", nullable = false) Long warehouseId;
    @Column(name = "product_id", nullable = false) Long productId;
    @Column(name = "on_hand", nullable = false) int onHand;
    @Column(nullable = false) int reserved;
    @Column(nullable = false) int unavailable;
    @Column(name = "in_transit_out", nullable = false) int inTransitOut;
    @Column(name = "updated_at", nullable = false) OffsetDateTime updatedAt = OffsetDateTime.now();
}

@Entity @Table(name = "wms_transfer") @Data
class StockTransfer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "transfer_no", nullable = false, length = 40) String transferNo;
    @Column(name = "source_warehouse_id", nullable = false) Long sourceWarehouseId;
    @Column(name = "destination_warehouse_id", nullable = false) Long destinationWarehouseId;
    @Column(nullable = false, length = 24) String status;
    @Column(name = "requested_by", length = 180) String requestedBy;
    @Column(name = "approved_by", length = 180) String approvedBy;
    @Column(length = 500) String note;
    @Column(name = "created_at", nullable = false) OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) OffsetDateTime updatedAt = OffsetDateTime.now();
}

@Entity @Table(name = "wms_transfer_line") @Data
class StockTransferLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "transfer_id", nullable = false) Long transferId;
    @Column(name = "product_id", nullable = false) Long productId;
    @Column(nullable = false) int quantity;
    @Column(name = "received_quantity", nullable = false) int receivedQuantity;
    @Column(name = "damaged_quantity", nullable = false) int damagedQuantity;
}

@Entity @Table(name = "wms_adjustment", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "idempotency_key"})) @Data
class StockAdjustment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "warehouse_id", nullable = false) Long warehouseId;
    @Column(name = "product_id", nullable = false) Long productId;
    @Column(name = "before_quantity", nullable = false) int beforeQuantity;
    @Column(name = "after_quantity", nullable = false) int afterQuantity;
    @Column(nullable = false) int delta;
    @Column(nullable = false, length = 255) String reason;
    @Column(length = 500) String evidence;
    @Column(nullable = false, length = 16) String status;
    @Column(name = "requested_by", nullable = false, length = 180) String requestedBy;
    @Column(name = "approved_by", length = 180) String approvedBy;
    @Column(name = "idempotency_key", length = 120) String idempotencyKey;
    @Column(name = "created_at", nullable = false) OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "decided_at") OffsetDateTime decidedAt;
}

@Entity @Table(name = "wms_cost_layer", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "source_ref", "product_id"})) @Data
class CostLayer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "product_id", nullable = false) Long productId;
    @Column(name = "warehouse_id") Long warehouseId;
    @Column(nullable = false) int remaining;
    @Column(name = "unit_cost", nullable = false, precision = 38, scale = 4) BigDecimal unitCost;
    @Column(name = "batch_no", length = 80) String batchNo;
    @Column(name = "expiry_date") LocalDate expiryDate;
    @Column(name = "source_ref", nullable = false, length = 160) String sourceRef;
    @Column(name = "received_at", nullable = false) OffsetDateTime receivedAt = OffsetDateTime.now();
}

@Entity @Table(name = "wms_config") @Data
class InventoryConfig {
    @Id @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "adjustment_approval_threshold", nullable = false) int adjustmentApprovalThreshold = 20;
    @Column(name = "reservation_hold_minutes", nullable = false) int reservationHoldMinutes = 15;
    @Column(name = "default_lead_time_days", nullable = false) int defaultLeadTimeDays = 7;
    @Column(name = "default_safety_stock", nullable = false) int defaultSafetyStock = 5;
    @Column(name = "over_receipt_tolerance_percent", nullable = false) int overReceiptTolerancePercent = 5;
    @Column(name = "valuation_method", nullable = false, length = 16) String valuationMethod = "FIFO";
}

@Entity @Table(name = "wms_audit_log") @Data
class OpsAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(nullable = false, length = 40) String entity;
    @Column(name = "entity_id", nullable = false, length = 60) String entityId;
    @Column(nullable = false, length = 60) String action;
    @Column(length = 180) String actor;
    @Column(length = 500) String details;
    @Column(name = "created_at", nullable = false) OffsetDateTime createdAt = OffsetDateTime.now();
}
