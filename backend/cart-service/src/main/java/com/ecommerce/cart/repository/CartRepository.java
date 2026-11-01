package com.ecommerce.cart.repository;

import com.ecommerce.cart.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserIdAndStatus(Long userId, String status);

    Optional<Cart> findByUserIdAndTenantIdAndStatus(Long userId, UUID tenantId, String status);

    Optional<Cart> findBySessionTokenAndStatus(String sessionToken, String status);

    List<Cart> findByStatusAndUpdatedAtBefore(String status, OffsetDateTime cutoff);

    long countByStatus(String status);

    @Query("SELECT c FROM Cart c WHERE c.userId = :userId ORDER BY c.updatedAt DESC")
    List<Cart> findAllByUserId(@Param("userId") Long userId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Cart c SET c.updatedAt = :updatedAt WHERE c.id = :id")
    void forceUpdateTimestamp(@Param("id") Long id, @Param("updatedAt") OffsetDateTime updatedAt);
}
