package com.ecommerce.product.repository;

import com.ecommerce.product.model.ProductAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductAuditLogRepository extends JpaRepository<ProductAuditLog, Long> {
    List<ProductAuditLog> findByTenantIdAndProductIdOrderByIdDesc(UUID tenantId, Long productId);
}
