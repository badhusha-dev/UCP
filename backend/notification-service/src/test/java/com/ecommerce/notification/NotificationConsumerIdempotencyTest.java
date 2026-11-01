package com.ecommerce.notification;

import com.ecommerce.notification.model.Notification;
import com.ecommerce.notification.repository.NotificationRepository;
import com.ecommerce.notification.service.NotificationConsumerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class NotificationConsumerIdempotencyTest {

    @Autowired
    private NotificationConsumerService notificationConsumerService;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @Test
    @DisplayName("Should process new Kafka order event and persist notification with idempotencyKey")
    void testConsumeNewOrderEvent() {
        String payload = "{\"eventType\":\"ORDER_CREATED\",\"orderId\":999,\"userId\":10,\"status\":\"PLACED\",\"totalAmount\":150.00}";

        notificationConsumerService.consume(payload);

        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(10L);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getIdempotencyKey()).isEqualTo("ORDER-999-ORDER_CREATED");
        assertThat(notifications.get(0).getMessage()).contains("Your order #999 has been placed successfully");
    }

    @Test
    @DisplayName("Should detect duplicate Kafka order event and skip re-processing (Idempotency)")
    void testConsumeDuplicateOrderEvent() {
        String payload = "{\"eventType\":\"ORDER_CREATED\",\"orderId\":888,\"userId\":10,\"status\":\"PLACED\",\"totalAmount\":50.00}";

        // First message delivery
        notificationConsumerService.consume(payload);
        assertThat(notificationRepository.findAll()).hasSize(1);

        // Duplicate message delivery (simulating broker rebalance or at-least-once retry)
        notificationConsumerService.consume(payload);

        // Assert no duplicate was saved!
        List<Notification> all = notificationRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getIdempotencyKey()).isEqualTo("ORDER-888-ORDER_CREATED");
    }

    @Test
    @DisplayName("Lifecycle updates for one order each notify once; a redelivery of the same change is skipped")
    void testLifecycleEventsAreNotCollapsed() {
        String confirmed = "{\"eventType\":\"ORDER_STATUS_UPDATED\",\"orderId\":777,\"userId\":10,\"status\":\"CONFIRMED\",\"totalAmount\":10,\"referenceId\":\"corr-0001\"}";
        String shipped = "{\"eventType\":\"ORDER_STATUS_UPDATED\",\"orderId\":777,\"userId\":10,\"status\":\"SHIPPED\",\"totalAmount\":10,\"referenceId\":\"corr-0002\"}";
        String shippedAgain = "{\"eventType\":\"ORDER_STATUS_UPDATED\",\"orderId\":777,\"userId\":10,\"status\":\"SHIPPED\",\"totalAmount\":10,\"referenceId\":\"corr-0003\"}";
        notificationConsumerService.consume(confirmed);
        notificationConsumerService.consume(shipped);
        notificationConsumerService.consume(shipped);        // same change redelivered
        assertThat(notificationRepository.findAll()).hasSize(2);
        notificationConsumerService.consume(shippedAgain);   // a genuinely new change to the same status
        assertThat(notificationRepository.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("Internal SLA events never become customer notifications; cancellation requests do")
    void testInternalAndCancellationRequestEvents() {
        notificationConsumerService.consume("{\"eventType\":\"ORDER_SLA_BREACHED\",\"orderId\":555,\"userId\":10,\"status\":\"CREATED\",\"totalAmount\":10}");
        assertThat(notificationRepository.findAll()).isEmpty();
        notificationConsumerService.consume("{\"eventType\":\"ORDER_CANCELLATION_REQUESTED\",\"orderId\":555,\"userId\":10,\"status\":\"CANCELLATION_REQUESTED\",\"totalAmount\":10,\"referenceId\":\"c-1\"}");
        List<Notification> all = notificationRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getMessage()).contains("cancellation has been requested");
    }

    @Test
    @DisplayName("Return events: customer steps notify once each, internal Payment/Delivery requests never reach the customer")
    void testReturnEvents() {
        String requested = "{\"eventType\":\"RETURN_REQUESTED\",\"orderId\":501,\"userId\":12,\"status\":\"REQUESTED\",\"totalAmount\":20.00,\"referenceId\":\"RMA-7-REQUESTED\"}";
        String approved = "{\"eventType\":\"RETURN_APPROVED\",\"orderId\":501,\"userId\":12,\"status\":\"APPROVED\",\"totalAmount\":20.00,\"referenceId\":\"RMA-7-APPROVED\"}";
        String internal = "{\"eventType\":\"RETURN_REFUND_REQUESTED\",\"orderId\":501,\"userId\":12,\"status\":\"REFUND_PROCESSING\",\"totalAmount\":20.00,\"referenceId\":\"rf-7-1\"}";
        String refunded = "{\"eventType\":\"RETURN_REFUNDED\",\"orderId\":501,\"userId\":12,\"status\":\"REFUNDED\",\"totalAmount\":20.00,\"referenceId\":\"RMA-7-REFUNDED\"}";
        for (String p : new String[]{requested, requested, approved, internal, refunded, refunded}) notificationConsumerService.consume(p);
        List<Notification> all = notificationRepository.findByUserIdOrderByCreatedAtDesc(12L);
        assertThat(all).hasSize(3);
        assertThat(all).extracting(Notification::getMessage).anyMatch(m -> m.contains("received your return request")).anyMatch(m -> m.contains("has been approved")).anyMatch(m -> m.contains("refund of $20.00"));
    }
}
