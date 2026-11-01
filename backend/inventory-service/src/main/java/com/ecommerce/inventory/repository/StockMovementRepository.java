package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByTenantIdAndProductIdOrderByCreatedAtDesc(UUID tenantId, Long productId);

    Page<StockMovement> findByTenantIdAndProductId(UUID tenantId, Long productId, Pageable pageable);

    List<StockMovement> findTop50ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
