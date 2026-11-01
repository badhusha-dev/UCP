package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.*;
import com.ecommerce.cart.security.CartPrincipal;
import com.ecommerce.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Customer Cart", description = "Endpoints for managing customer and guest shopping carts, sync, and validation")
public class CartController {

    private final CartService cartService;

    /** Prefer the verified JWT principal's userId; fall back to gateway-injected header for guest/internal calls. */
    private Long resolveUserId(Long headerUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CartPrincipal p) {
            return p.userId();
        }
        return headerUserId;
    }

    @Operation(summary = "Get Shopping Cart", description = "Retrieves active shopping cart for authenticated user or guest session.")
    @GetMapping
    public ResponseEntity<CartDto> getCart(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestHeader(value = "X-Cart-Session", required = false) String headerSession,
            @RequestParam(value = "sessionToken", required = false) String querySession) {
        String sessionToken = headerSession != null ? headerSession : querySession;
        return ResponseEntity.ok(cartService.getCart(resolveUserId(headerUserId), sessionToken));
    }

    @Operation(summary = "Add Item to Cart", description = "Adds a catalog product with quantity to the customer or guest cart.")
    @PostMapping("/items")
    public ResponseEntity<CartDto> addItem(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestHeader(value = "X-Cart-Session", required = false) String headerSession,
            @RequestParam(value = "sessionToken", required = false) String querySession,
            @Valid @RequestBody AddToCartRequest request) {
        String sessionToken = headerSession != null ? headerSession : querySession;
        return ResponseEntity.ok(cartService.addItem(resolveUserId(headerUserId), sessionToken, request));
    }

    @Operation(summary = "Update Cart Item Quantity", description = "Updates quantity of a specific line item in the cart.")
    @PutMapping("/items/{itemId}")
    public ResponseEntity<CartDto> updateItemQuantity(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestHeader(value = "X-Cart-Session", required = false) String headerSession,
            @RequestParam(value = "sessionToken", required = false) String querySession,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateItemQuantityRequest request) {
        String sessionToken = headerSession != null ? headerSession : querySession;
        return ResponseEntity.ok(cartService.updateItemQuantity(resolveUserId(headerUserId), sessionToken, itemId, request.getQuantity()));
    }

    @Operation(summary = "Remove Item from Cart", description = "Deletes a specific line item from the cart.")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartDto> removeItem(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestHeader(value = "X-Cart-Session", required = false) String headerSession,
            @RequestParam(value = "sessionToken", required = false) String querySession,
            @PathVariable Long itemId) {
        String sessionToken = headerSession != null ? headerSession : querySession;
        return ResponseEntity.ok(cartService.removeItem(resolveUserId(headerUserId), sessionToken, itemId));
    }

    @Operation(summary = "Clear Entire Cart", description = "Removes all items from the current active cart.")
    @DeleteMapping
    public ResponseEntity<CartDto> clearCart(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestHeader(value = "X-Cart-Session", required = false) String headerSession,
            @RequestParam(value = "sessionToken", required = false) String querySession) {
        String sessionToken = headerSession != null ? headerSession : querySession;
        return ResponseEntity.ok(cartService.clearCart(resolveUserId(headerUserId), sessionToken));
    }

    @Operation(summary = "Synchronize Cart", description = "Bulk merges or replaces items from client local storage / guest session.")
    @PostMapping("/sync")
    public ResponseEntity<CartDto> syncCart(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestHeader(value = "X-Cart-Session", required = false) String headerSession,
            @RequestParam(value = "sessionToken", required = false) String querySession,
            @RequestBody CartSyncRequest request) {
        String sessionToken = headerSession != null ? headerSession : (querySession != null ? querySession : request.getSessionToken());
        return ResponseEntity.ok(cartService.syncCart(resolveUserId(headerUserId), sessionToken, request.getItems()));
    }

    @Operation(summary = "Validate Cart Availability", description = "Validates items against inventory quantities and product pricing prior to checkout.")
    @GetMapping("/validate")
    public ResponseEntity<CartValidationResultDto> validateCart(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestHeader(value = "X-Cart-Session", required = false) String headerSession,
            @RequestParam(value = "sessionToken", required = false) String querySession) {
        String sessionToken = headerSession != null ? headerSession : querySession;
        return ResponseEntity.ok(cartService.validateCart(resolveUserId(headerUserId), sessionToken));
    }

    @Operation(summary = "Convert Cart", description = "Internal endpoint called upon successful checkout to mark cart as converted.")
    @PostMapping("/convert")
    public ResponseEntity<Void> convertCart(
            @RequestParam(value = "userId", required = false) Long queryUserId,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId) {
        Long userId = resolveUserId(headerUserId != null ? headerUserId : queryUserId);
        if (userId != null) {
            cartService.markCartConverted(userId);
        }
        return ResponseEntity.ok().build();
    }
}
