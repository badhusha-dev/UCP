package com.ecommerce.inventory.ops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    List<Warehouse> findByTenantIdOrderByCode(UUID tenantId);
    Optional<Warehouse> findByTenantIdAndId(UUID tenantId, Long id);
    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}

interface LocationRepository extends JpaRepository<WarehouseLocation, Long> {
    List<WarehouseLocation> findByTenantIdAndWarehouseId(UUID tenantId, Long warehouseId);
    boolean existsByTenantIdAndWarehouseIdAndCode(UUID tenantId, Long warehouseId, String code);
}

interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {
    List<WarehouseStock> findByTenantId(UUID tenantId);
    List<WarehouseStock> findByTenantIdAndWarehouseId(UUID tenantId, Long warehouseId);
    Optional<WarehouseStock> findByTenantIdAndWarehouseIdAndProductId(UUID tenantId, Long warehouseId, Long productId);
}

interface TransferRepository extends JpaRepository<StockTransfer, Long> {
    List<StockTransfer> findByTenantIdOrderByIdDesc(UUID tenantId);
    Optional<StockTransfer> findByTenantIdAndId(UUID tenantId, Long id);
}

interface TransferLineRepository extends JpaRepository<StockTransferLine, Long> {
    List<StockTransferLine> findByTenantIdAndTransferId(UUID tenantId, Long transferId);
}

interface AdjustmentRepository extends JpaRepository<StockAdjustment, Long> {
    List<StockAdjustment> findByTenantIdOrderByIdDesc(UUID tenantId);
    Optional<StockAdjustment> findByTenantIdAndId(UUID tenantId, Long id);
    Optional<StockAdjustment> findByTenantIdAndIdempotencyKey(UUID tenantId, String key);
    long countByTenantIdAndStatus(UUID tenantId, String status);
}

interface CostLayerRepository extends JpaRepository<CostLayer, Long> {
    List<CostLayer> findByTenantIdAndProductIdAndRemainingGreaterThanOrderByReceivedAtAscIdAsc(UUID tenantId, Long productId, int remaining);
    List<CostLayer> findByTenantIdAndRemainingGreaterThan(UUID tenantId, int remaining);
    boolean existsByTenantIdAndSourceRef(UUID tenantId, String sourceRef);
}

interface ConfigRepository extends JpaRepository<InventoryConfig, UUID> {}

interface AuditRepository extends JpaRepository<OpsAudit, Long> {
    List<OpsAudit> findTop100ByTenantIdOrderByIdDesc(UUID tenantId);
}
