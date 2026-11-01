package com.ecommerce.order.service;

import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.dto.CartItemDto;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderProducerService orderProducerService;
    private final RestTemplate restTemplate;
    private final CouponService couponService;
    private final CartPersistenceService cartPersistenceService;
    private final PricingClient pricingClient;
    private final StorefrontClient storefrontClient;
    private final com.ecommerce.order.admin.OrderAdminService orderAdminService;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private OrderService self;

    @Value("${services.stock.url:http://localhost:8090}")
    private String stockServiceUrl;

    @Value("${SERVICE_TO_SERVICE_TOKEN}")
    private String serviceToServiceToken;

    @Transactional
    public Order placeOrder(CheckoutRequest request) {
        if (request.getStorefront() != null && !request.getStorefront().isBlank()) return placeStorefrontOrder(request);
        if (!pricingClient.enabled() || request.getItems() == null || request.getItems().isEmpty()) {
            return placeOrderInternal(request, null, null, null);
        }
        // Prices, promotions and the payable total come from the pricing module; cart-supplied prices are ignored.
        Long customerId = (request.getUserId() != null && request.getUserId() > 0) ? request.getUserId() : 1L;
        String promotionRef = "chk-" + java.util.UUID.randomUUID();
        Map<String, Object> priced = pricingClient.redeem(promotionRef, customerId, request.getItems(), request.getCouponCode());
        try {
            return placeOrderInternal(request, promotionRef, priced, null);
        } catch (RuntimeException e) {
            try { pricingClient.reverse(promotionRef); } catch (RuntimeException reverseFailure) {
                log.error("Could not release promotions for failed checkout {}: {}", promotionRef, reverseFailure.getMessage());
            }
            throw e;
        }
    }

    /**
     * Multi-tenant checkout. The tenant, store and order type come from the storefront (resolved by product-service), and prices
     * always come from that tenant's pricing, whatever the deployment-wide pricing flag says: pricing refuses products the tenant
     * does not sell, so an order can never mix in another tenant's catalogue, and client-supplied prices are ignored.
     */
    private Order placeStorefrontOrder(CheckoutRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "A checkout needs at least one item.");
        }
        StorefrontClient.Storefront storefront = storefrontClient.resolve(request.getStorefront());
        Long customerId = (request.getUserId() != null && request.getUserId() > 0) ? request.getUserId() : 1L;
        String promotionRef = "chk-" + java.util.UUID.randomUUID();
        Map<String, Object> priced;
        try {
            priced = pricingClient.redeem(storefront.tenantId(), promotionRef, customerId, request.getItems(), request.getCouponCode());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "An item is not sold by this storefront or cannot be priced.");
        } catch (org.springframework.web.client.RestClientException e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Pricing is temporarily unavailable.");
        }
        try {
            return placeOrderInternal(request, promotionRef, priced, storefront);
        } catch (RuntimeException e) {
            try { pricingClient.reverse(storefront.tenantId(), promotionRef); } catch (RuntimeException reverseFailure) {
                log.error("Could not release promotions for failed checkout {}: {}", promotionRef, reverseFailure.getMessage());
            }
            throw e;
        }
    }

    private Order placeOrderInternal(CheckoutRequest request, String promotionRef, Map<String, Object> priced, StorefrontClient.Storefront storefront) {
        Order order = new Order();
        Long userId = (request.getUserId() != null && request.getUserId() > 0) ? request.getUserId() : 1L;
        order.setUserId(userId);
        // Tenant: the storefront's tenant and store when the shopper names a storefront; otherwise the configured pricing tenant
        // (single-tenant deployments) or the legacy tenant.
        if (storefront != null) {
            order.setTenantId(storefront.tenantId());
            order.setStoreId(storefront.storeId());
        } else {
            if (pricingClient.tenantUuid() != null) order.setTenantId(pricingClient.tenantUuid());
            if (request.getStoreId() != null && !request.getStoreId().isBlank()) order.setStoreId(request.getStoreId().trim());
        }
        if (request.getVendorId() != null && !request.getVendorId().isBlank()) order.setVendorId(request.getVendorId().trim());
        boolean restaurant = storefront != null ? "RESTAURANT".equalsIgnoreCase(storefront.storefrontType()) : "RESTAURANT".equalsIgnoreCase(request.getOrderType());
        order.setOrderType(restaurant ? "RESTAURANT" : "RETAIL");
        order.setCustomerName(request.getCustomerName()); order.setCustomerEmail(request.getCustomerEmail()); order.setCustomerPhone(request.getCustomerPhone());
        order.setDeliveryAddress(request.getDeliveryAddress()); order.setSpecialInstructions(request.getSpecialInstructions());
        order.setStatus(restaurant ? "RECEIVED" : "PLACED");
        order.setCarrier("FedEx Express");
        order.setTrackingNumber("TRK-" + System.currentTimeMillis() % 100000000);
        order.setEstimatedDelivery(java.time.LocalDateTime.now().plusDays(3));
        order.setShippingStatus("PROCESSING");
        
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean inventoryDeductedSuccessfully = true;

        if (request.getItems() != null) {
            int lineIndex = 0;
            for (CartItemDto cartItem : request.getItems()) {
                BigDecimal unitPrice = priced == null ? cartItem.getPrice() : pricedUnitPrice(priced, lineIndex);
                lineIndex++;
                // Stock Service is the single write authority for physical quantities.
                boolean success = (self != null)
                        ? self.deductInventoryCall(cartItem.getProductId(), cartItem.getQuantity())
                        : deductInventoryCall(cartItem.getProductId(), cartItem.getQuantity());
                if (!success) {
                    inventoryDeductedSuccessfully = false;
                }

                // 2. Add to Order
                OrderItem orderItem = new OrderItem();
                orderItem.setProductId(cartItem.getProductId());
                orderItem.setProductName(cartItem.getName());
                orderItem.setPrice(unitPrice);
                orderItem.setQuantity(cartItem.getQuantity());
                orderItem.setVendorId(cartItem.getVendorId());
                order.addItem(orderItem);

                subtotal = subtotal.add(unitPrice.multiply(new BigDecimal(cartItem.getQuantity())));
            }
        }

        // Apply Coupon Discount if provided
        BigDecimal discount = BigDecimal.ZERO;
        if (priced != null) {
            discount = new BigDecimal(String.valueOf(priced.get("discount")));
            order.setPromotionRef(promotionRef);
            if (Boolean.TRUE.equals(priced.get("couponValid"))) order.setCouponCode(String.valueOf(priced.get("couponCode")));
        } else if (request.getCouponCode() != null && !request.getCouponCode().trim().isEmpty()) {
            discount = couponService.applyCouponToOrder(request.getCouponCode(), subtotal);
            order.setCouponCode(request.getCouponCode().trim().toUpperCase());
        }
        order.setDiscountAmount(discount);

        BigDecimal finalTotal = subtotal.subtract(discount);
        if (finalTotal.compareTo(BigDecimal.ZERO) < 0) {
            finalTotal = BigDecimal.ZERO;
        }
        order.setTotalAmount(finalTotal);

        if (!inventoryDeductedSuccessfully) {
            order.setStatus("PENDING_INVENTORY");
            log.warn("Order created in PENDING_INVENTORY status due to inventory service issue.");
        }
        
        // Save Order
        Order savedOrder = orderRepository.save(order);

        // Mark persistent cart converted
        try {
            cartPersistenceService.markCartConverted(userId);
        } catch (Exception e) {
            log.warn("Could not mark cart converted for user #{}: {}", userId, e.getMessage());
        }

        // Emit Kafka Event
        orderProducerService.publishOrderEvent(new com.ecommerce.order.dto.OrderEvent(
                "ORDER_CREATED", savedOrder.getId(), savedOrder.getUserId(), savedOrder.getStatus(), savedOrder.getTotalAmount()
        ));

        return savedOrder;
    }

    @SuppressWarnings("unchecked")
    private static BigDecimal pricedUnitPrice(Map<String, Object> priced, int index) {
        java.util.List<Map<String, Object>> lines = (java.util.List<Map<String, Object>>) priced.get("lines");
        return new BigDecimal(String.valueOf(lines.get(index).get("unitPrice")));
    }

    @CircuitBreaker(name = "orderServiceCB", fallbackMethod = "deductInventoryFallback")
    @Retry(name = "orderServiceRetry")
    public boolean deductInventoryCall(Long productId, Integer quantity) {
        try {
            String invUrl = stockServiceUrl + "/api/stock/adjustments";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Role", "ADMIN");
            headers.set("X-User-Email", "order-service@ecommerce.local");
            headers.set("X-Service-Token", serviceToServiceToken);
            Map<String, Object> body = new HashMap<>();
            body.put("productId", productId);
            body.put("delta", -quantity);
            body.put("movementType", "ORDER_DEDUCTION");
            body.put("reason", "ORDER_CHECKOUT");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Void> res = restTemplate.exchange(invUrl, HttpMethod.POST, entity, Void.class);
            if (res.getStatusCode().is2xxSuccessful()) {
                return true;
            }
        } catch (Exception ex) {
            log.warn("Stock Service deduction failed for product {}: {}", productId, ex.getMessage());
            return false;
        }
        return false;
    }

    public boolean deductInventoryFallback(Long productId, Integer quantity, Throwable t) {
        log.error("Fallback triggered for deductInventoryCall. Product ID: {}, Quantity: {}, Error: {}", 
                productId, quantity, t.getMessage());
        return false;
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + id));
    }

    /** Legacy status endpoint. Now validated by the order state machine (and recorded in the status history) instead of accepting any string. */
    @Transactional
    public Order updateOrderStatus(Long id, String status) {
        return orderAdminService.legacyTransition(id, status, null);
    }

    /** Cancels through the state machine: unshipped allocations are released, promotions reversed and ORDER_CANCELLED published. */
    @Transactional
    public Order cancelOrder(Long id) {
        Order order = getOrderById(id);
        if ("CANCELLED".equals(order.getStatus())) {
            throw new IllegalArgumentException("Order is already cancelled.");
        }
        return orderAdminService.legacyTransition(id, "CANCELLED", "Cancelled through the order API");
    }
}
