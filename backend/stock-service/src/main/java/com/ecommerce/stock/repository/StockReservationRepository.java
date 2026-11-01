package com.ecommerce.stock.repository;

import com.ecommerce.stock.model.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    Optional<StockReservation> findByReservationId(String reservationId);

    List<StockReservation> findByProductId(Long productId);

    List<StockReservation> findByStatus(String status);

    List<StockReservation> findByStatusAndExpiresAtBefore(String status, OffsetDateTime cutoff);
}
