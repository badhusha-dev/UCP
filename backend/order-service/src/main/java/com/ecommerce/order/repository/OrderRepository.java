package com.ecommerce.order.repository;

import com.ecommerce.order.model.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    java.util.Optional<Order> findByIdForUpdate(@Param("id") Long id);

    List<Order> findByUserId(Long userId);

    List<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN ('PENDING', 'PROCESSING', 'PLACED') AND o.createdAt <= :threshold")
    long countDelayedOrders(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT o.userId, COUNT(o), SUM(o.totalAmount), MIN(o.createdAt), MAX(o.createdAt) " +
           "FROM Order o WHERE o.status != 'CANCELLED' AND o.userId IS NOT NULL " +
           "GROUP BY o.userId ORDER BY SUM(o.totalAmount) DESC")
    List<Object[]> findCustomerOrderSummaries();

    @Query("SELECT COUNT(DISTINCT o.userId) FROM Order o WHERE o.createdAt < :cutoff AND o.status != 'CANCELLED'")
    long countDistinctUsersBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :startDate AND o.createdAt <= :endDate")
    long countOrdersInPeriod(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = 'CANCELLED' AND o.createdAt >= :startDate AND o.createdAt <= :endDate")
    long countCancelledOrdersInPeriod(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
