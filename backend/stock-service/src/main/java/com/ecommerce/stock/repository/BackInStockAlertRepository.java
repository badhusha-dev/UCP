package com.ecommerce.stock.repository;

import com.ecommerce.stock.model.BackInStockAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BackInStockAlertRepository extends JpaRepository<BackInStockAlert, Long> {

    List<BackInStockAlert> findByProductIdAndStatus(Long productId, String status);

    Optional<BackInStockAlert> findByProductIdAndUserEmailAndStatus(Long productId, String userEmail, String status);

    long countByProductIdAndStatus(Long productId, String status);
}
