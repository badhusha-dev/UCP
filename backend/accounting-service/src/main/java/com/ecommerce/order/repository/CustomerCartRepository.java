package com.ecommerce.order.repository;

import com.ecommerce.order.model.CustomerCart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerCartRepository extends JpaRepository<CustomerCart, Long> {
    Optional<CustomerCart> findByUserId(Long userId);
    List<CustomerCart> findByStatusAndUpdatedAtBefore(String status, OffsetDateTime cutoff);
    long countByStatus(String status);
    long countByStatusAndUpdatedAtBefore(String status, OffsetDateTime cutoff);
}
