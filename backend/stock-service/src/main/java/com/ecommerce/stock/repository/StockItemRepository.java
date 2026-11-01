package com.ecommerce.stock.repository;

import com.ecommerce.stock.model.StockItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockItemRepository extends JpaRepository<StockItem, Long>, JpaSpecificationExecutor<StockItem> {

    Optional<StockItem> findByProductId(Long productId);

    List<StockItem> findByProductIdIn(List<Long> productIds);

    @Query("SELECT i FROM StockItem i WHERE (i.stockQuantity - i.reservedQuantity) <= i.reorderThreshold")
    List<StockItem> findLowStockItems();

    @Query("SELECT COUNT(i) FROM StockItem i WHERE (i.stockQuantity - i.reservedQuantity) <= i.reorderThreshold")
    long countLowStockItems();

    @Query("SELECT COUNT(i) FROM StockItem i WHERE (i.stockQuantity - i.reservedQuantity) <= 0")
    long countOutOfStockItems();

    @Query("SELECT COALESCE(SUM(i.costPrice * i.stockQuantity), 0) FROM StockItem i")
    java.math.BigDecimal sumStockValuation();

    @Query("SELECT COALESCE(SUM(i.stockQuantity), 0) FROM StockItem i")
    long sumTotalUnits();
}
