package com.ecommerce.order.repository;

import com.ecommerce.order.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT i.productId, i.productName, SUM(i.quantity), SUM(i.price * i.quantity), COUNT(DISTINCT i.order.id) " +
           "FROM OrderItem i WHERE i.order.status != 'CANCELLED' " +
           "AND i.order.createdAt >= :startDate " +
           "AND i.order.createdAt <= :endDate " +
           "GROUP BY i.productId, i.productName " +
           "ORDER BY SUM(i.price * i.quantity) DESC")
    List<Object[]> findTopSellingByRevenue(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    @Query("SELECT i.productId, i.productName, SUM(i.quantity), SUM(i.price * i.quantity), COUNT(DISTINCT i.order.id) " +
           "FROM OrderItem i WHERE i.order.status != 'CANCELLED' " +
           "AND i.order.createdAt >= :startDate " +
           "AND i.order.createdAt <= :endDate " +
           "GROUP BY i.productId, i.productName " +
           "ORDER BY SUM(i.quantity) DESC")
    List<Object[]> findTopSellingByUnits(@Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM OrderItem i WHERE i.order.status != 'CANCELLED' " +
           "AND i.order.createdAt >= :startDate " +
           "AND i.order.createdAt <= :endDate")
    Long sumUnitsSoldInPeriod(@Param("startDate") LocalDateTime startDate,
                              @Param("endDate") LocalDateTime endDate);
}
