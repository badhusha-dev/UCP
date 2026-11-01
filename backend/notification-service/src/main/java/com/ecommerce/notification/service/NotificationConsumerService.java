package com.ecommerce.notification.service;

import com.ecommerce.notification.dto.OrderEvent;
import com.ecommerce.notification.model.DeadLetterEvent;
import com.ecommerce.notification.model.Notification;
import com.ecommerce.notification.repository.DeadLetterEventRepository;
import com.ecommerce.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumerService {

    private final NotificationRepository notificationRepository;
    private final DeadLetterEventRepository deadLetterEventRepository;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = "order-events", groupId = "notification-group")
    public void consume(String payload) {
        log.info("Processing Kafka event from order-events: {}", payload);
        try {
            OrderEvent event = objectMapper.readValue(payload, OrderEvent.class);
            if (event == null || event.getOrderId() == null) {
                throw new IllegalArgumentException("Invalid payload structure: missing orderId");
            }

            // Operational events (e.g. SLA breaches) are for staff dashboards, never for the customer.
            if ("ORDER_SLA_BREACHED".equals(event.getEventType())) {
                log.info("Skipping customer notification for internal event {} on order {}", event.getEventType(), event.getOrderId());
                return;
            }

            // Return and refund lifecycle events: only the steps a customer cares about produce a message; refund/pickup requests to Payment and Delivery,
            // exception handling and internal bookkeeping stay out of the customer's inbox.
            String returnMessage = null;
            boolean returnEvent = event.getEventType() != null && event.getEventType().startsWith("RETURN_");
            if (returnEvent) {
                returnMessage = returnMessage(event);
                if (returnMessage == null) {
                    log.info("Skipping customer notification for internal return event {} on order {}", event.getEventType(), event.getOrderId());
                    return;
                }
            }

            // Idempotency: one-off events are keyed by order and type. Lifecycle events repeat for one order, so they are keyed by the
            // status they announce and the change's correlation id (a redelivery of the same change is skipped; a new change is not).
            String idempotencyKey = "ORDER-" + event.getOrderId() + "-" + (event.getEventType() != null ? event.getEventType() : "UNKNOWN");
            if (java.util.Set.of("ORDER_STATUS_UPDATED", "ORDER_CANCELLATION_REQUESTED", "ORDER_RETURN_REFUND").contains(event.getEventType()) || returnEvent) {
                idempotencyKey += "-" + event.getStatus() + (event.getReferenceId() != null ? "-" + event.getReferenceId() : "");
            }
            if (notificationRepository.existsByIdempotencyKey(idempotencyKey)) {
                log.warn("Idempotency match found: Event [{}] has already been processed. Skipping duplicate.", idempotencyKey);
                return;
            }

            Notification notification = new Notification();
            notification.setUserId(event.getUserId() != null ? event.getUserId() : 1L);
            notification.setType(event.getEventType() != null ? event.getEventType() : "ORDER_UPDATE");
            notification.setIdempotencyKey(idempotencyKey);

            String message;
            String eventType = event.getEventType() != null ? event.getEventType() : "";
            if (returnMessage != null) message = returnMessage;
            else switch (eventType) {
                case "ORDER_CREATED":
                    message = "Your order #" + event.getOrderId() + " has been placed successfully. Total: $" + event.getTotalAmount();
                    break;
                case "ORDER_CANCELLED":
                    message = "Your order #" + event.getOrderId() + " has been cancelled.";
                    break;
                case "ORDER_CANCELLATION_REQUESTED":
                    message = "A cancellation has been requested for your order #" + event.getOrderId() + ".";
                    break;
                case "ORDER_STATUS_UPDATED":
                    message = "The status of your order #" + event.getOrderId() + " has been updated to " + event.getStatus();
                    break;
                default:
                    message = "You have an update on order #" + event.getOrderId();
            }
            notification.setMessage(message);
            notificationRepository.save(notification);
            log.info("Notification successfully saved for user ID: {} with idempotencyKey: [{}]", notification.getUserId(), idempotencyKey);

        } catch (Exception e) {
            log.error("Error processing order event [payload: {}]. Exception: {}", payload, e.getMessage());
            throw new RuntimeException("Kafka message processing failed, triggering retry: " + e.getMessage(), e);
        }
    }

    /** Customer-facing wording for the return events worth telling a customer about; null for internal events. */
    static String returnMessage(OrderEvent e) {
        String order = "order #" + e.getOrderId();
        String reason = e.getReason() == null || e.getReason().isBlank() ? "" : " (" + e.getReason() + ")";
        return switch (e.getEventType()) {
            case "RETURN_REQUESTED" -> "We have received your return request for " + order + ". We will review it shortly.";
            case "RETURN_INFO_REQUESTED" -> "We need a little more information to review your return for " + order + reason + ".";
            case "RETURN_APPROVED" -> "Your return request for " + order + " has been approved.";
            case "RETURN_REJECTED" -> "Your return request for " + order + " could not be approved" + reason + ".";
            case "RETURN_PICKUP_SCHEDULED" -> "A pickup has been scheduled for your return of " + order + ".";
            case "RETURN_PICKUP_FAILED" -> "We could not collect your return for " + order + ". We will reschedule the pickup.";
            case "RETURN_RECEIVED" -> "We have received your returned items for " + order + ".";
            case "RETURN_INSPECTION_COMPLETED" -> "The inspection of your returned items for " + order + " is complete.";
            case "RETURN_REFUNDED" -> "Your refund" + (e.getTotalAmount() == null ? "" : " of $" + e.getTotalAmount()) + " for " + order + " has been processed.";
            case "RETURN_CUSTOMER_CANCELLED" -> "Your return request for " + order + " has been cancelled.";
            case "RETURN_EXCHANGED" -> "Your replacement for " + order + " is on its way.";
            default -> null;
        };
    }

    @DltHandler
    public void handleDeadLetter(
            String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage) {

        log.error("Dead Letter Queue (DLQ) invoked for payload on topic [{}] partition [{}] offset [{}]. Cause: {}",
                topic, partition, offset, exceptionMessage);

        DeadLetterEvent deadLetter = DeadLetterEvent.builder()
                .topic(topic)
                .partitionId(partition)
                .offsetNumber(offset)
                .payload(payload)
                .exceptionMessage(exceptionMessage != null ? exceptionMessage : "Exhausted all consumer retries")
                .status("FAILED")
                .build();

        deadLetterEventRepository.save(deadLetter);
        log.warn("Dead letter event persisted to database: ID={}, topic={}, status={}", deadLetter.getId(), topic, deadLetter.getStatus());
    }
}
