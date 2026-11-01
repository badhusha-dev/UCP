package com.ecommerce.stock.repository;

import com.ecommerce.stock.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId);

    Page<StockMovement> findByProductId(Long productId, Pageable pageable);

    List<StockMovement> findTop50ByOrderByCreatedAtDesc();

    Optional<StockMovement> findByReferenceId(String referenceId);
}
