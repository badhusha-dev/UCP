package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    Optional<StockReservation> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<StockReservation> findByTenantIdAndReservationId(UUID tenantId, String reservationId);

    List<StockReservation> findByTenantIdAndProductId(UUID tenantId, Long productId);

    List<StockReservation> findByTenantIdAndStatus(UUID tenantId, String status);

    List<StockReservation> findByStatusAndExpiresAtBefore(String status, OffsetDateTime cutoff);
}
