package com.ecommerce.order.repository;

import com.ecommerce.order.model.OrderReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderReturnItemRepository extends JpaRepository<OrderReturnItem, Long> {
    @Query(value = "SELECT COALESCE(SUM(i.quantity), 0) FROM order_return_items i " +
            "JOIN order_return r ON r.id = i.return_id " +
            "WHERE i.order_item_id = :orderItemId AND r.status <> 'REJECTED'", nativeQuery = true)
    Long sumRequestedQuantity(@Param("orderItemId") Long orderItemId);
}
