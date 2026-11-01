package com.ecommerce.notification.repository;

import com.ecommerce.notification.model.DeadLetterEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, Long> {
    List<DeadLetterEvent> findByStatusOrderByCreatedAtDesc(String status);
}
