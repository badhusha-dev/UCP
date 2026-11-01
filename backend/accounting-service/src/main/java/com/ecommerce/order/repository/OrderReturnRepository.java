package com.ecommerce.order.repository;

import com.ecommerce.order.model.OrderReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderReturnRepository extends JpaRepository<OrderReturn, Long> {
    List<OrderReturn> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<OrderReturn> findByOrderIdOrderByCreatedAtDesc(Long orderId);
    List<OrderReturn> findByStatusOrderByCreatedAtDesc(String status);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(r.refundAmount), 0) FROM OrderReturn r WHERE r.status = 'REFUNDED' " +
           "AND r.createdAt >= :startDate " +
           "AND r.createdAt <= :endDate")
    java.math.BigDecimal sumRefundsInPeriod(@org.springframework.data.repository.query.Param("startDate") java.time.OffsetDateTime startDate,
                                            @org.springframework.data.repository.query.Param("endDate") java.time.OffsetDateTime endDate);
}
