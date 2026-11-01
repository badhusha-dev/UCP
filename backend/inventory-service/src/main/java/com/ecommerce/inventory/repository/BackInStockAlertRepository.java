package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.BackInStockAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BackInStockAlertRepository extends JpaRepository<BackInStockAlert, Long> {

    List<BackInStockAlert> findByTenantIdAndProductIdAndStatus(UUID tenantId, Long productId, String status);

    Optional<BackInStockAlert> findByTenantIdAndProductIdAndUserEmailAndStatus(UUID tenantId, Long productId, String userEmail, String status);

    long countByTenantIdAndProductIdAndStatus(UUID tenantId, Long productId, String status);
}
