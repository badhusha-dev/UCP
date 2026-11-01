package com.ecommerce.notification;

import com.ecommerce.notification.model.DeadLetterEvent;
import com.ecommerce.notification.model.Notification;
import com.ecommerce.notification.repository.DeadLetterEventRepository;
import com.ecommerce.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DeadLetterEventRepository deadLetterEventRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        deadLetterEventRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save notification and verify idempotency key existence")
    void testNotificationIdempotencyKey() {
        Notification notification = new Notification();
        notification.setUserId(101L);
        notification.setType("ORDER_CREATED");
        notification.setMessage("Order #101 created");
        notification.setIdempotencyKey("ORDER-101-ORDER_CREATED");

        Notification saved = notificationRepository.save(notification);
        assertThat(saved.getId()).isNotNull();

        boolean exists = notificationRepository.existsByIdempotencyKey("ORDER-101-ORDER_CREATED");
        assertThat(exists).isTrue();

        boolean notExists = notificationRepository.existsByIdempotencyKey("ORDER-999-ORDER_CREATED");
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Should save and query dead letter events by status")
    void testDeadLetterEventPersistence() {
        DeadLetterEvent dle = DeadLetterEvent.builder()
                .topic("order-events")
                .partitionId(0)
                .offsetNumber(12L)
                .payload("{\"orderId\": null}")
                .exceptionMessage("Missing orderId")
                .status("FAILED")
                .build();

        DeadLetterEvent saved = deadLetterEventRepository.save(dle);
        assertThat(saved.getId()).isNotNull();

        List<DeadLetterEvent> failedEvents = deadLetterEventRepository.findByStatusOrderByCreatedAtDesc("FAILED");
        assertThat(failedEvents).hasSize(1);
        assertThat(failedEvents.get(0).getTopic()).isEqualTo("order-events");
        assertThat(failedEvents.get(0).getStatus()).isEqualTo("FAILED");
    }
}
