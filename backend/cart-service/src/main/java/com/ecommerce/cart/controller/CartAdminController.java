package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.AbandonedCartDto;
import com.ecommerce.cart.dto.CartFunnelDto;
import com.ecommerce.cart.dto.SendReminderRequest;
import com.ecommerce.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/cart/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cart Management & Abandoned Recovery", description = "Admin & Manager endpoints for tracking cart abandonment, funnels, and issuing recovery notifications")
public class CartAdminController {

    private final CartService cartService;

    private void assertManagerOrAdmin(String role) {
        if (role == null || (!role.toUpperCase().contains("ADMIN") && !role.toUpperCase().contains("MANAGER"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Staff or Administrator privileges required");
        }
    }

    @Operation(summary = "Get Abandoned Carts", description = "Retrieves all carts untouched for more than specified hours with customer emails and line items.")
    @GetMapping("/abandoned")
    public ResponseEntity<List<AbandonedCartDto>> getAbandonedCarts(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "24") int inactiveHours) {
        assertManagerOrAdmin(role);
        return ResponseEntity.ok(cartService.getAbandonedCarts(inactiveHours));
    }

    @Operation(summary = "Send Abandoned Cart Recovery Reminder", description = "Triggers an automated customer recovery reminder with custom discount promo code.")
    @PostMapping("/abandoned/{cartId}/remind")
    public ResponseEntity<Void> sendAbandonedReminder(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long cartId,
            @RequestBody(required = false) SendReminderRequest request) {
        assertManagerOrAdmin(role);
        SendReminderRequest req = request != null ? request : new SendReminderRequest();
        boolean sent = cartService.triggerAbandonedCartAlert(cartId, req.getDiscountCode(), req.getDiscountPercent());
        if (!sent) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart #" + cartId + " not found");
        }
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get Cart Funnel & Conversion Analytics", description = "Provides macro funnel metrics: total carts, abandonment rate, active cart value, and conversion rate.")
    @GetMapping("/funnel")
    public ResponseEntity<CartFunnelDto> getFunnelAnalytics(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        assertManagerOrAdmin(role);
        return ResponseEntity.ok(cartService.getCartFunnelStats());
    }
}
