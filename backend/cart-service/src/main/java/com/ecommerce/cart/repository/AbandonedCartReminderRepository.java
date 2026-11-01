package com.ecommerce.cart.repository;

import com.ecommerce.cart.model.AbandonedCartReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AbandonedCartReminderRepository extends JpaRepository<AbandonedCartReminder, Long> {
    List<AbandonedCartReminder> findByCartId(Long cartId);
    List<AbandonedCartReminder> findByUserId(Long userId);
    long countByStatus(String status);
}
