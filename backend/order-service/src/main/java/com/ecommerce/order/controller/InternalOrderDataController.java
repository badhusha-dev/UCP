package com.ecommerce.order.controller;

import com.ecommerce.order.dto.DeliveryStatusReport;
import com.ecommerce.order.dto.OrderStatusUpdateRequest;
import com.ecommerce.order.service.FulfillmentAndReturnsService;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.CustomerCartRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OrderReturnRepository;
import com.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Internal read model feed for services that maintain their own database projections. */
@RestController
@RequestMapping("/internal/order-data")
@RequiredArgsConstructor
public class InternalOrderDataController {
    private final OrderRepository orderRepository;
    private final CustomerCartRepository cartRepository;
    private final OrderReturnRepository returnRepository;
    private final OrderService orderService;
    private final FulfillmentAndReturnsService fulfillmentService;

    @Value("${SERVICE_TO_SERVICE_TOKEN:}")
    private String serviceToken;

    @GetMapping
    public Map<String, Object> snapshot(@RequestHeader(value = "X-Service-Token", required = false) String suppliedToken) {
        authorize(suppliedToken);
        return Map.of("orders", orderRepository.findAll(),
                "carts", cartRepository.findAll(),
                "returns", returnRepository.findAll());
    }

    @GetMapping("/{orderId}")
    public Order order(@PathVariable Long orderId,
                       @RequestHeader(value = "X-Service-Token", required = false) String suppliedToken) {
        authorize(suppliedToken);
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    @PutMapping("/{orderId}/status")
    public Order updateStatus(@PathVariable Long orderId,
                              @RequestHeader(value = "X-Service-Token", required = false) String suppliedToken,
                              @RequestBody OrderStatusUpdateRequest request) {
        authorize(suppliedToken);
        return orderService.updateOrderStatus(orderId, request.getStatus());
    }

    @PostMapping("/{orderId}/delivery-status")
    public Map<String, Object> deliveryStatus(@PathVariable Long orderId,
                                              @RequestHeader(value = "X-Service-Token", required = false) String suppliedToken,
                                              @RequestBody DeliveryStatusReport report) {
        authorize(suppliedToken);
        if (report.getDeliveryStatus() == null
                || !java.util.Set.of("PICKED_UP", "IN_TRANSIT", "DELIVERED", "DELIVERY_FAILED", "RETURNED", "CANCELLED").contains(report.getDeliveryStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported delivery status");
        }
        try {
            return Map.of("applied", fulfillmentService.applyDeliveryStatus(orderId, report.getDeliveryStatus()));
        } catch (IllegalArgumentException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
    }

    private void authorize(String suppliedToken) {
        if (serviceToken == null || serviceToken.isBlank() || suppliedToken == null
                || !MessageDigest.isEqual(serviceToken.getBytes(StandardCharsets.UTF_8),
                                          suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service credential");
        }
    }
}
