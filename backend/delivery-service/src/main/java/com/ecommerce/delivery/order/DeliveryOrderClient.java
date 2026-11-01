package com.ecommerce.delivery.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Authenticated service-to-service access to Order Service. Order Service stays authoritative for the order lifecycle and totals;
 * Delivery only reads a snapshot and reports delivery progress. Every failure other than "order not found" fails closed.
 */
@Component
public class DeliveryOrderClient {
    public record OrderSnapshot(long id, Long userId, String status, BigDecimal totalAmount) {}

    public static class OrderServiceUnavailableException extends RuntimeException {
        public OrderServiceUnavailableException(String message, Throwable cause) { super(message, cause); }
    }

    private final RestClient client = RestClient.builder().build();
    @Value("${order.service.url:http://localhost:8083}")
    private String orderServiceUrl;
    @Value("${SERVICE_TO_SERVICE_TOKEN:}")
    private String serviceToken;

    public Optional<OrderSnapshot> findOrder(long orderId) {
        requireCredential();
        try {
            Map<?, ?> body = client.get().uri(orderServiceUrl + "/internal/order-data/" + orderId)
                    .header("X-Service-Token", serviceToken).retrieve().body(Map.class);
            if (body == null || body.get("id") == null) throw new OrderServiceUnavailableException("Order Service returned an unreadable order.", null);
            Object user = body.get("userId");
            Object total = body.get("totalAmount");
            return Optional.of(new OrderSnapshot(((Number) body.get("id")).longValue(),
                    user == null ? null : ((Number) user).longValue(), String.valueOf(body.get("status")),
                    total == null ? null : new BigDecimal(String.valueOf(total))));
        } catch (HttpClientErrorException.NotFound missing) {
            return Optional.empty();
        } catch (RestClientException | ClassCastException | NumberFormatException failure) {
            throw new OrderServiceUnavailableException("Order Service could not be reached or understood.", failure);
        }
    }

    /** Idempotent on the Order Service side: it applies only forward-moving, valid changes and ignores repeats. */
    public void reportDeliveryStatus(long orderId, UUID eventId, UUID deliveryId, String deliveryStatus) {
        requireCredential();
        try {
            client.post().uri(orderServiceUrl + "/internal/order-data/" + orderId + "/delivery-status")
                    .header("X-Service-Token", serviceToken)
                    .body(Map.of("eventId", eventId.toString(), "deliveryId", deliveryId.toString(), "deliveryStatus", deliveryStatus))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException failure) {
            HttpStatusCode status = failure.getStatusCode();
            throw new OrderServiceUnavailableException("Order Service rejected the delivery status (" + status.value() + ").", failure);
        } catch (RestClientException failure) {
            throw new OrderServiceUnavailableException("Order Service could not be reached.", failure);
        }
    }

    private void requireCredential() {
        if (!StringUtils.hasText(serviceToken)) throw new OrderServiceUnavailableException("Service credential is not configured.", null);
    }
}
