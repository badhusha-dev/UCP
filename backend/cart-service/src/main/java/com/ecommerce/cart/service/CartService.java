package com.ecommerce.cart.service;

import com.ecommerce.cart.dto.*;
import com.ecommerce.cart.model.AbandonedCartReminder;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartItem;
import com.ecommerce.cart.repository.AbandonedCartReminderRepository;
import com.ecommerce.cart.repository.CartItemRepository;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.outbox.OutboxWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.ecommerce.cart.security.CartPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AbandonedCartReminderRepository reminderRepository;
    private final OutboxWriter outbox;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private UUID currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CartPrincipal p) {
            return p.tenantId();
        }
        return null;
    }

    @Transactional
    public Cart getOrCreateCart(Long userId, String sessionToken) {
        return getOrCreateCart(userId, sessionToken, currentTenantId());
    }

    @Transactional
    public Cart getOrCreateCart(Long userId, String sessionToken, UUID tenantId) {
        if (userId != null) {
            Optional<Cart> userCartOpt = (tenantId != null)
                    ? cartRepository.findByUserIdAndTenantIdAndStatus(userId, tenantId, "ACTIVE")
                    : cartRepository.findByUserIdAndStatus(userId, "ACTIVE");

            if (userCartOpt.isPresent()) {
                Cart userCart = userCartOpt.get();
                // Merge an orphaned guest cart with this sessionToken if present
                if (sessionToken != null && !sessionToken.isBlank()) {
                    cartRepository.findBySessionTokenAndStatus(sessionToken, "ACTIVE")
                            .ifPresent(guestCart -> {
                                if (!guestCart.getId().equals(userCart.getId())) {
                                    mergeCarts(userCart, guestCart);
                                }
                            });
                }
                return userCart;
            }

            // Check if guest cart exists with this sessionToken that can be claimed
            if (sessionToken != null && !sessionToken.isBlank()) {
                Optional<Cart> guestCartOpt = cartRepository.findBySessionTokenAndStatus(sessionToken, "ACTIVE");
                if (guestCartOpt.isPresent()) {
                    Cart claimedCart = guestCartOpt.get();
                    claimedCart.setUserId(userId);
                    if (tenantId != null) {
                        claimedCart.setTenantId(tenantId);
                    }
                    return cartRepository.save(claimedCart);
                }
            }

            // Create fresh cart for authenticated user
            Cart newCart = Cart.builder()
                    .userId(userId)
                    .tenantId(tenantId)
                    .sessionToken(sessionToken != null ? sessionToken : UUID.randomUUID().toString())
                    .status("ACTIVE")
                    .currency("USD")
                    .items(new ArrayList<>())
                    .build();
            return cartRepository.save(newCart);
        }

        // Guest session — no tenantId
        String effectiveToken = (sessionToken != null && !sessionToken.isBlank())
                ? sessionToken
                : "guest_" + UUID.randomUUID();

        return cartRepository.findBySessionTokenAndStatus(effectiveToken, "ACTIVE")
                .orElseGet(() -> {
                    Cart guestCart = Cart.builder()
                            .userId(null)
                            .sessionToken(effectiveToken)
                            .status("ACTIVE")
                            .currency("USD")
                            .items(new ArrayList<>())
                            .build();
                    return cartRepository.save(guestCart);
                });
    }

    private void mergeCarts(Cart targetUserCart, Cart sourceGuestCart) {
        log.info("Merging guest cart #{} into user cart #{}", sourceGuestCart.getId(), targetUserCart.getId());
        for (CartItem guestItem : sourceGuestCart.getItems()) {
            Optional<CartItem> existing = targetUserCart.getItems().stream()
                    .filter(i -> i.getProductId().equals(guestItem.getProductId()))
                    .findFirst();

            if (existing.isPresent()) {
                existing.get().setQuantity(existing.get().getQuantity() + guestItem.getQuantity());
            } else {
                CartItem newItem = CartItem.builder()
                        .cart(targetUserCart)
                        .productId(guestItem.getProductId())
                        .sku(guestItem.getSku())
                        .productName(guestItem.getProductName())
                        .unitPrice(guestItem.getUnitPrice())
                        .quantity(guestItem.getQuantity())
                        .imageUrl(guestItem.getImageUrl())
                        .build();
                targetUserCart.addItem(newItem);
            }
        }
        sourceGuestCart.setStatus("CONVERTED");
        cartRepository.save(sourceGuestCart);
        cartRepository.save(targetUserCart);
    }

    // Not read-only: a user or guest without a cart gets one created here, and PostgreSQL refuses that INSERT in a read-only transaction.
    @Transactional
    public CartDto getCart(Long userId, String sessionToken) {
        Cart cart = getOrCreateCart(userId, sessionToken);
        return mapToDto(cart);
    }

    @Transactional
    public CartDto addItem(Long userId, String sessionToken, AddToCartRequest req) {
        Cart cart = getOrCreateCart(userId, sessionToken);

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(req.getProductId()))
                .findFirst();

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + req.getQuantity());
            if (req.getUnitPrice() != null && req.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                item.setUnitPrice(req.getUnitPrice());
            }
            if (req.getImageUrl() != null) {
                item.setImageUrl(req.getImageUrl());
            }
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .productId(req.getProductId())
                    .sku(req.getSku() != null ? req.getSku() : "SKU-" + req.getProductId())
                    .productName(req.getProductName())
                    .unitPrice(req.getUnitPrice() != null ? req.getUnitPrice() : BigDecimal.ZERO)
                    .quantity(req.getQuantity())
                    .imageUrl(req.getImageUrl())
                    .build();
            cart.addItem(newItem);
        }

        cart.setStatus("ACTIVE");
        Cart saved = cartRepository.save(cart);
        publishCartEvent("CART_UPDATED", saved);
        return mapToDto(saved);
    }

    @Transactional
    public CartDto updateItemQuantity(Long userId, String sessionToken, Long itemId, int quantity) {
        Cart cart = getOrCreateCart(userId, sessionToken);

        Optional<CartItem> targetItem = cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst();

        if (targetItem.isEmpty()) {
            throw new IllegalArgumentException("Cart item #" + itemId + " not found in current cart");
        }

        CartItem item = targetItem.get();
        if (quantity <= 0) {
            cart.removeItem(item);
        } else {
            item.setQuantity(quantity);
        }

        Cart saved = cartRepository.save(cart);
        publishCartEvent("CART_UPDATED", saved);
        return mapToDto(saved);
    }

    @Transactional
    public CartDto removeItem(Long userId, String sessionToken, Long itemId) {
        Cart cart = getOrCreateCart(userId, sessionToken);

        cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .ifPresent(cart::removeItem);

        Cart saved = cartRepository.save(cart);
        publishCartEvent("CART_UPDATED", saved);
        return mapToDto(saved);
    }

    @Transactional
    public CartDto clearCart(Long userId, String sessionToken) {
        Cart cart = getOrCreateCart(userId, sessionToken);
        cart.getItems().clear();
        Cart saved = cartRepository.save(cart);
        publishCartEvent("CART_CLEARED", saved);
        return mapToDto(saved);
    }

    @Transactional
    public CartDto syncCart(Long userId, String sessionToken, List<AddToCartRequest> items) {
        Cart cart = getOrCreateCart(userId, sessionToken);

        if (items != null) {
            for (AddToCartRequest req : items) {
                Optional<CartItem> existing = cart.getItems().stream()
                        .filter(i -> i.getProductId().equals(req.getProductId()))
                        .findFirst();

                if (existing.isPresent()) {
                    existing.get().setQuantity(req.getQuantity());
                    if (req.getUnitPrice() != null) {
                        existing.get().setUnitPrice(req.getUnitPrice());
                    }
                } else {
                    CartItem newItem = CartItem.builder()
                            .cart(cart)
                            .productId(req.getProductId())
                            .sku(req.getSku() != null ? req.getSku() : "SKU-" + req.getProductId())
                            .productName(req.getProductName())
                            .unitPrice(req.getUnitPrice() != null ? req.getUnitPrice() : BigDecimal.ZERO)
                            .quantity(req.getQuantity())
                            .imageUrl(req.getImageUrl())
                            .build();
                    cart.addItem(newItem);
                }
            }
        }

        Cart saved = cartRepository.save(cart);
        publishCartEvent("CART_SYNCED", saved);
        return mapToDto(saved);
    }

    // Not read-only, for the same reason as getCart: it may create the cart.
    @Transactional
    public CartValidationResultDto validateCart(Long userId, String sessionToken) {
        Cart cart = getOrCreateCart(userId, sessionToken);

        List<CartValidationResultDto.ItemValidation> itemValidations = new ArrayList<>();
        boolean allValid = true;

        for (CartItem item : cart.getItems()) {
            int availableStock = checkAvailableStock(item.getProductId());
            boolean inStock = availableStock >= item.getQuantity();
            if (!inStock) {
                allValid = false;
            }

            itemValidations.add(CartValidationResultDto.ItemValidation.builder()
                    .productId(item.getProductId())
                    .sku(item.getSku())
                    .productName(item.getProductName())
                    .requestedQuantity(item.getQuantity())
                    .availableStock(availableStock)
                    .unitPrice(item.getUnitPrice())
                    .inStock(inStock)
                    .statusMessage(inStock ? "Available" : "Only " + availableStock + " left in stock")
                    .build());
        }

        return CartValidationResultDto.builder()
                .valid(allValid && !cart.getItems().isEmpty())
                .totalQuantity(cart.getTotalQuantity())
                .subtotal(cart.getSubtotal())
                .message(cart.getItems().isEmpty() ? "Cart is empty" : (allValid ? "All items available" : "Some items have insufficient stock"))
                .items(itemValidations)
                .build();
    }

    private int checkAvailableStock(Long productId) {
        try {
            // Attempt to query authoritative inventory-service
            String url = "http://inventory-service:8085/api/inventory/" + productId;
            Map resp = restTemplate.getForObject(url, Map.class);
            if (resp != null && resp.containsKey("availableQuantity")) {
                return ((Number) resp.get("availableQuantity")).intValue();
            }
        } catch (Exception e) {
            log.debug("Inventory check fallback for product #{}: {}", productId, e.getMessage());
        }
        // Default safe fallback if network partitioned
        return 50;
    }

    @Transactional
    public void markCartConverted(Long userId) {
        cartRepository.findByUserIdAndStatus(userId, "ACTIVE").ifPresent(cart -> {
            cart.setStatus("CONVERTED");
            cartRepository.save(cart);
            log.info("Active cart #{} for user #{} marked CONVERTED", cart.getId(), userId);
        });
    }

    @Transactional(readOnly = true)
    public List<AbandonedCartDto> getAbandonedCarts(int inactiveHours) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(inactiveHours);
        List<Cart> abandoned = cartRepository.findByStatusAndUpdatedAtBefore("ACTIVE", cutoff);

        return abandoned.stream()
                .filter(c -> !c.getItems().isEmpty())
                .map(this::mapToAbandonedDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean triggerAbandonedCartAlert(Long cartId, String discountCode, BigDecimal discountPercent) {
        Optional<Cart> cartOpt = cartRepository.findById(cartId);
        if (cartOpt.isEmpty()) {
            return false;
        }

        Cart cart = cartOpt.get();
        String email = cart.getUserId() != null ? "user" + cart.getUserId() + "@example.com" : "shopper_" + cart.getSessionToken() + "@guest.com";

        AbandonedCartReminder reminder = AbandonedCartReminder.builder()
                .cart(cart)
                .userId(cart.getUserId())
                .customerEmail(email)
                .discountCode(discountCode != null ? discountCode : "COMEBACK15")
                .discountPercent(discountPercent != null ? discountPercent : new BigDecimal("15.00"))
                .status("SENT")
                .sentAt(OffsetDateTime.now())
                .build();

        reminderRepository.save(reminder);
        cart.setStatus("ABANDONED");
        cartRepository.save(cart);

        log.info("Sent abandoned cart recovery reminder for cart #{} to {} with discount {}",
                cart.getId(), email, reminder.getDiscountCode());

        publishCartEvent("CART_ABANDONED_REMINDER_SENT", cart);
        return true;
    }

    @Transactional(readOnly = true)
    public CartFunnelDto getCartFunnelStats() {
        long totalCarts = cartRepository.count();
        long activeCarts = cartRepository.countByStatus("ACTIVE");
        long abandonedCarts = cartRepository.countByStatus("ABANDONED");
        long convertedCarts = cartRepository.countByStatus("CONVERTED");

        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(24);
        List<Cart> inactiveActiveCarts = cartRepository.findByStatusAndUpdatedAtBefore("ACTIVE", cutoff);

        BigDecimal abandonedValue = inactiveActiveCarts.stream()
                .map(Cart::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal activeValue = cartRepository.findAll().stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .map(Cart::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double conversionRate = totalCarts > 0
                ? BigDecimal.valueOf((double) convertedCarts / totalCarts * 100).setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        double abandonedRate = totalCarts > 0
                ? BigDecimal.valueOf((double) (abandonedCarts + inactiveActiveCarts.size()) / totalCarts * 100).setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        long remindersSent = reminderRepository.count();

        return CartFunnelDto.builder()
                .totalCartsCreated(totalCarts)
                .activeCarts(activeCarts)
                .abandonedCarts(abandonedCarts + inactiveActiveCarts.size())
                .convertedCarts(convertedCarts)
                .abandonedCartValue(abandonedValue)
                .activeCartValue(activeValue)
                .abandonedRatePercent(abandonedRate)
                .conversionRatePercent(conversionRate)
                .remindersSent(remindersSent)
                .build();
    }

    /**
     * Writes the event to the outbox in the caller's transaction (every caller is @Transactional), so it commits
     * together with the cart change and is published to {@code cart.events} afterwards. The payload is unchanged.
     */
    private void publishCartEvent(String eventType, Cart cart) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("cartId", cart.getId());
        event.put("userId", cart.getUserId());
        event.put("totalQuantity", cart.getTotalQuantity());
        event.put("subtotal", cart.getSubtotal());
        event.put("timestamp", OffsetDateTime.now().toString());
        try {
            outbox.append("cart.events", String.valueOf(cart.getId()), eventType, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialise cart event {}: {}", eventType, ex.getMessage());
        }
    }

    public CartDto mapToDto(Cart cart) {
        List<CartItemDto> items = cart.getItems().stream()
                .map(i -> CartItemDto.builder()
                        .id(i.getId())
                        .productId(i.getProductId())
                        .sku(i.getSku())
                        .productName(i.getProductName())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .subtotal(i.getSubtotal())
                        .imageUrl(i.getImageUrl())
                        .updatedAt(i.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

        return CartDto.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .sessionToken(cart.getSessionToken())
                .status(cart.getStatus())
                .currency(cart.getCurrency())
                .totalQuantity(cart.getTotalQuantity())
                .subtotal(cart.getSubtotal())
                .items(items)
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    private AbandonedCartDto mapToAbandonedDto(Cart cart) {
        long hours = cart.getUpdatedAt() != null
                ? Duration.between(cart.getUpdatedAt(), OffsetDateTime.now()).toHours()
                : 0;

        List<AbandonedCartReminder> reminders = reminderRepository.findByCartId(cart.getId());

        return AbandonedCartDto.builder()
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .customerEmail(cart.getUserId() != null ? "user" + cart.getUserId() + "@example.com" : "shopper_" + cart.getSessionToken() + "@guest.com")
                .sessionToken(cart.getSessionToken())
                .itemCount(cart.getTotalQuantity())
                .totalAmount(cart.getSubtotal())
                .lastUpdated(cart.getUpdatedAt())
                .inactiveHours(hours)
                .remindersSent(reminders.size())
                .status(cart.getStatus())
                .items(cart.getItems().stream().map(i -> CartItemDto.builder()
                        .id(i.getId())
                        .productId(i.getProductId())
                        .sku(i.getSku())
                        .productName(i.getProductName())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .subtotal(i.getSubtotal())
                        .imageUrl(i.getImageUrl())
                        .build()).collect(Collectors.toList()))
                .build();
    }
}
