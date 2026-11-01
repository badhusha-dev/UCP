package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long>, JpaSpecificationExecutor<InventoryItem> {

    Optional<InventoryItem> findByTenantIdAndProductId(UUID tenantId, Long productId);

    List<InventoryItem> findByTenantIdAndProductIdIn(UUID tenantId, List<Long> productIds);

    List<InventoryItem> findByTenantId(UUID tenantId);

    long countByTenantId(UUID tenantId);

    @Query("SELECT i FROM InventoryItem i WHERE i.tenantId = :tenantId AND (i.stockQuantity - i.reservedQuantity) <= i.reorderThreshold")
    List<InventoryItem> findLowStockItems(@Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(i) FROM InventoryItem i WHERE i.tenantId = :tenantId AND (i.stockQuantity - i.reservedQuantity) <= i.reorderThreshold")
    long countLowStockItems(@Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(i) FROM InventoryItem i WHERE i.tenantId = :tenantId AND (i.stockQuantity - i.reservedQuantity) <= 0")
    long countOutOfStockItems(@Param("tenantId") UUID tenantId);

    @Query("SELECT COALESCE(SUM(i.costPrice * i.stockQuantity), 0) FROM InventoryItem i WHERE i.tenantId = :tenantId")
    java.math.BigDecimal sumStockValuation(@Param("tenantId") UUID tenantId);

    @Query("SELECT COALESCE(SUM(i.stockQuantity), 0) FROM InventoryItem i WHERE i.tenantId = :tenantId")
    long sumTotalUnits(@Param("tenantId") UUID tenantId);

    /** Atomic conditional reservation: succeeds (1 row) only if enough available stock remains. No lost updates under concurrency. */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE InventoryItem i SET i.reservedQuantity = i.reservedQuantity + :qty, i.updatedAt = :now WHERE i.tenantId = :tenantId AND i.productId = :productId AND i.active = true AND (i.stockQuantity - i.reservedQuantity - i.unavailableQuantity) >= :qty")
    int tryReserve(@Param("tenantId") UUID tenantId, @Param("productId") Long productId, @Param("qty") int qty, @Param("now") java.time.OffsetDateTime now);
    default int tryReserve(UUID tenantId, Long productId, int qty) { return tryReserve(tenantId, productId, qty, java.time.OffsetDateTime.now()); }

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE InventoryItem i SET i.reservedQuantity = i.reservedQuantity - :qty, i.updatedAt = :now WHERE i.tenantId = :tenantId AND i.productId = :productId AND i.reservedQuantity >= :qty")
    int releaseReserved(@Param("tenantId") UUID tenantId, @Param("productId") Long productId, @Param("qty") int qty, @Param("now") java.time.OffsetDateTime now);
    default int releaseReserved(UUID tenantId, Long productId, int qty) { return releaseReserved(tenantId, productId, qty, java.time.OffsetDateTime.now()); }

    /** Atomic on-hand change; a negative delta succeeds only if on-hand stays at or above reserved + unavailable. */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE InventoryItem i SET i.stockQuantity = i.stockQuantity + :delta, i.updatedAt = :now WHERE i.tenantId = :tenantId AND i.productId = :productId AND (i.stockQuantity + :delta) >= (i.reservedQuantity + i.unavailableQuantity)")
    int tryAdjustOnHand(@Param("tenantId") UUID tenantId, @Param("productId") Long productId, @Param("delta") int delta, @Param("now") java.time.OffsetDateTime now);
    default int tryAdjustOnHand(UUID tenantId, Long productId, int delta) { return tryAdjustOnHand(tenantId, productId, delta, java.time.OffsetDateTime.now()); }
}
