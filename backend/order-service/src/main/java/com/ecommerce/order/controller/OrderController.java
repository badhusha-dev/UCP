package com.ecommerce.order.controller;

import com.ecommerce.order.dto.*;
import com.ecommerce.order.model.*;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "Endpoints for order creation, checkout processing, and status tracking")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final CouponService couponService;
    private final CartPersistenceService cartPersistenceService;
    private final FulfillmentAndReturnsService fulfillmentService;
    private final ExecutiveAnalyticsService analyticsService;
    private final com.ecommerce.order.rma.ReturnService returnService;
    private final com.ecommerce.order.repository.OrderReturnRepository returnRepository;

    @Operation(summary = "List All Orders", description = "Retrieves all orders across all users (Admin operation).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of orders successfully retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Order.class))))
    })
    @GetMapping
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Operation(summary = "Get Orders by User ID", description = "Retrieves order history for a specific customer.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Customer orders retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Order.class))))
    })
    @GetMapping("/user/{userId}")
    public List<Order> getOrdersByUser(
            @Parameter(description = "Customer user ID", example = "1", required = true) @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long authenticatedUserId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireOwnUserOrAdmin(userId, authenticatedUserId, role);
        return orderRepository.findByUserId(userId);
    }

    @Operation(summary = "Checkout Order", description = "Processes shopping cart checkout, checks inventory via Circuit Breaker, persists order, and emits ORDER_CREATED Kafka event.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order placed successfully",
                    content = @Content(schema = @Schema(implementation = Order.class))),
            @ApiResponse(responseCode = "400", description = "Invalid checkout data or insufficient inventory")
    })
    @PostMapping("/checkout")
    public ResponseEntity<Order> checkout(
            @RequestBody CheckoutRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId) {
        // The order belongs to the authenticated user (gateway-verified X-User-Id); a userId in the body must not let a shopper
        // place orders as someone else.
        if (headerUserId != null) {
            request.setUserId(headerUserId);
        }
        return ResponseEntity.ok(orderService.placeOrder(request));
    }

    @Operation(summary = "Get Order by ID", description = "Retrieves detailed order record including all line items.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order found",
                    content = @Content(schema = @Schema(implementation = Order.class))),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(
            @Parameter(description = "Order unique ID", example = "501", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @Operation(summary = "Update Order Status", description = "Transitions order status (e.g., PENDING -> CONFIRMED).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order status updated",
                    content = @Content(schema = @Schema(implementation = Order.class))),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PutMapping("/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(
            @Parameter(description = "Order unique ID", example = "501", required = true)
            @PathVariable Long id,
            @RequestBody OrderStatusUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, request.getStatus()));
    }

    @Operation(summary = "Cancel Order", description = "Cancels an existing order and publishes ORDER_CANCELLED Kafka event.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order cancelled successfully",
                    content = @Content(schema = @Schema(implementation = Order.class))),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(
            @Parameter(description = "Order unique ID", example = "501", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    // ==========================================
    // SHIPMENT TRACKING & CARRIER CHECKPOINTS
    // ==========================================

    @Operation(summary = "Get Order Shipment Tracking", description = "Retrieves live tracking status, carrier, and milestone event timeline.")
    @GetMapping("/{id}/tracking")
    public ResponseEntity<OrderTrackingDto> getOrderTracking(@PathVariable Long id) {
        return ResponseEntity.ok(fulfillmentService.getOrderTracking(id));
    }

    @Operation(summary = "Add Tracking Checkpoint", description = "Appends a shipping status milestone event (Admin/Carrier webhook).")
    @PostMapping("/{id}/tracking")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderTrackingEvent> addTrackingCheckpoint(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        String status = payload.getOrDefault("status", "IN_TRANSIT");
        String location = payload.getOrDefault("location", "Distribution Center");
        String description = payload.getOrDefault("description", "Package scanned at facility");
        return ResponseEntity.ok(fulfillmentService.addTrackingCheckpoint(id, status, location, description));
    }

    // ==========================================
    // RETURNS & EXCHANGES
    // ==========================================

    @Operation(summary = "Request Order Return", description = "Initiates a customer return or exchange request.")
    @PostMapping("/{id}/returns")
    public ResponseEntity<OrderReturn> requestReturn(
            @PathVariable Long id,
            @Valid @RequestBody CreateReturnRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (headerUserId == null || headerUserId <= 0) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authenticated customer identity is required.");
        }
        java.util.UUID tenant = orderRepository.findById(id).map(Order::getTenantId).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Order not found."));
        java.util.List<com.ecommerce.order.rma.ReturnService.Line> lines = request.getItems().stream().map(l -> new com.ecommerce.order.rma.ReturnService.Line(l.getOrderItemId(), l.getQuantity())).toList();
        String reasonCode = request.getReasonCode() == null || request.getReasonCode().isBlank() ? "OTHER" : request.getReasonCode().trim().toUpperCase(java.util.Locale.ROOT);
        java.util.Map<String, Object> created = returnService.create(tenant, null, headerUserId, new com.ecommerce.order.rma.ReturnService.CreateCommand(id, reasonCode, request.getReason(), lines, request.getCondition(), request.getResolution(), idempotencyKey));
        return ResponseEntity.ok(returnRepository.findById(((Number) created.get("id")).longValue()).orElseThrow());
    }

    @Operation(summary = "Get Customer Return Requests", description = "Retrieves all return requests submitted by a customer.")
    @GetMapping("/returns/user/{userId}")
    public ResponseEntity<List<OrderReturn>> getUserReturns(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long authenticatedUserId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireOwnUserOrAdmin(userId, authenticatedUserId, role);
        return ResponseEntity.ok(fulfillmentService.getUserReturns(userId));
    }

    private static final java.util.Set<String> ADMIN_ROLES = java.util.Set.of("ADMIN", "SUPER_ADMIN");
    private static final java.util.Set<String> STAFF_ROLES = java.util.Set.of("ADMIN", "SUPER_ADMIN", "MANAGER");

    // Exact role match: a substring check would let any role whose name contains "ADMIN" pass.
    private static boolean hasRole(String role, java.util.Set<String> allowed) {
        if (role == null) return false;
        String normalized = role.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.startsWith("ROLE_")) normalized = normalized.substring(5);
        return allowed.contains(normalized);
    }

    private void requireOwnUserOrAdmin(Long requestedUserId, Long authenticatedUserId, String role) {
        boolean admin = hasRole(role, ADMIN_ROLES);
        if (!admin &&(authenticatedUserId == null || !authenticatedUserId.equals(requestedUserId))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "You can only view your own order history.");
        }
    }

    @Operation(summary = "List All Returns", description = "Retrieves all system returns for administrative review.")
    @GetMapping("/returns")
    public ResponseEntity<List<OrderReturn>> getAllReturns(@RequestHeader(value = "X-User-Role", required = false) String role) {
        throw retired();
    }

    @Operation(summary = "Process Return Request", description = "Approve, reject, or refund a return request with subledger integration.")
    @PostMapping("/returns/{returnId}/process")
    public ResponseEntity<OrderReturn> processReturn(
            @PathVariable Long returnId,
            @Valid @RequestBody ProcessReturnRequest request,
            @RequestHeader(value = "X-User-Email", required = false) String adminEmail,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        throw retired();
    }

    private static org.springframework.web.server.ResponseStatusException retired() {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE, "This endpoint was replaced by the tenant-scoped Returns & Refunds API at /api/orders/admin/rma.");
    }

    private void requireStaff(String role) {
        if (!hasRole(role, STAFF_ROLES)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Manager or Admin authorization is required.");
        }
    }

    // ==========================================
    // COUPON & DISCOUNT ENGINE
    // ==========================================

    @Operation(summary = "Validate Promotional Coupon", description = "Validates coupon code against order amount, expiry, and rules.")
    @PostMapping("/coupons/validate")
    public ResponseEntity<CouponValidationResponse> validateCoupon(@RequestBody ValidateCouponRequest request) {
        return ResponseEntity.ok(couponService.validateCoupon(request));
    }

    @Operation(summary = "List Available Coupons", description = "Retrieves list of active promotional discount coupons.")
    @GetMapping("/coupons")
    public ResponseEntity<List<Coupon>> getActiveCoupons() {
        return ResponseEntity.ok(couponService.getActiveCoupons());
    }

    @Operation(summary = "Create Coupon Promotion", description = "Creates a new coupon code (Admin only).")
    @PostMapping("/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Coupon> createCoupon(@RequestBody Coupon coupon) {
        return ResponseEntity.ok(couponService.createCoupon(coupon));
    }

    @Operation(summary = "Deactivate Coupon", description = "Deactivates an existing coupon promotion.")
    @DeleteMapping("/coupons/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateCoupon(@PathVariable Long id) {
        couponService.deactivateCoupon(id);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // PERSISTENT CART & ABANDONED RECOVERY
    // ==========================================

    @Operation(summary = "Get Customer Persistent Cart", description = "Retrieves server-synced cart items for the authenticated user.")
    @GetMapping("/cart")
    public ResponseEntity<CustomerCart> getCart(@RequestHeader(value = "X-User-Id", required = false) Long headerUserId) {
        Long userId = (headerUserId != null && headerUserId > 0) ? headerUserId : 1L;
        return cartPersistenceService.getCartByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @Operation(summary = "Synchronize Customer Cart", description = "Persists cart items to server for multi-device sync.")
    @PostMapping("/cart/sync")
    public ResponseEntity<CustomerCart> syncCart(
            @RequestBody CartSyncRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId) {
        Long userId = (headerUserId != null && headerUserId > 0) ? headerUserId : 1L;
        return ResponseEntity.ok(cartPersistenceService.syncCart(userId, request.getItemsJson()));
    }

    @Operation(summary = "Clear Customer Cart", description = "Removes persistent cart after purchase or manual cart reset.")
    @DeleteMapping("/cart")
    public ResponseEntity<Void> clearCart(@RequestHeader(value = "X-User-Id", required = false) Long headerUserId) {
        Long userId = (headerUserId != null && headerUserId > 0) ? headerUserId : 1L;
        cartPersistenceService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List Abandoned Carts", description = "Retrieves customer carts inactive for over 1 hour (Admin operation).")
    @GetMapping("/cart/abandoned")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CustomerCart>> getAbandonedCarts(
            @RequestParam(defaultValue = "1") int inactiveHours) {
        return ResponseEntity.ok(cartPersistenceService.getAbandonedCarts(inactiveHours));
    }

    @Operation(summary = "Send Abandoned Cart Recovery Alert", description = "Triggers email/notification reminder to customer.")
    @PostMapping("/cart/abandoned/{cartId}/remind")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> remindAbandonedCart(@PathVariable Long cartId) {
        boolean sent = cartPersistenceService.triggerAbandonedCartAlert(cartId);
        return ResponseEntity.ok(Map.of("success", sent, "cartId", cartId, "status", sent ? "SENT" : "NOT_FOUND"));
    }

    // ==========================================
    // EXECUTIVE COMMERCE ANALYTICS
    // ==========================================

    @Operation(summary = "Executive Commerce Analytics Dashboard", description = "High-level KPI metrics including GMV, Net Revenue, AOV, returns, and conversion.")
    @GetMapping("/analytics/executive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExecutiveAnalyticsDto> getExecutiveAnalytics() {
        return ResponseEntity.ok(analyticsService.getExecutiveAnalytics());
    }
}
