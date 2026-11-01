package com.ecommerce.cart;

import com.ecommerce.cart.dto.*;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.cart.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class CartServiceIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private com.ecommerce.cart.repository.AbandonedCartReminderRepository reminderRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @BeforeEach
    void setUp() {
        reminderRepository.deleteAll();
        cartRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create guest cart and add item successfully")
    void testCreateAndGetGuestCart() {
        AddToCartRequest req = AddToCartRequest.builder()
                .productId(101L)
                .sku("SKU-101")
                .productName("Premium Wireless Headphones")
                .unitPrice(new BigDecimal("199.99"))
                .quantity(2)
                .build();

        CartDto cart = cartService.addItem(null, "guest_session_123", req);

        assertThat(cart).isNotNull();
        assertThat(cart.getSessionToken()).isEqualTo("guest_session_123");
        assertThat(cart.getTotalQuantity()).isEqualTo(2);
        assertThat(cart.getSubtotal()).isEqualByComparingTo(new BigDecimal("399.98"));
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getProductName()).isEqualTo("Premium Wireless Headphones");
    }

    @Test
    @DisplayName("Should update item quantity and remove item")
    void testUpdateItemQuantityAndRemove() {
        AddToCartRequest req = AddToCartRequest.builder()
                .productId(201L)
                .sku("SKU-201")
                .productName("Smart Watch")
                .unitPrice(new BigDecimal("150.00"))
                .quantity(1)
                .build();

        CartDto cart = cartService.addItem(null, "session_test", req);
        Long itemId = cart.getItems().get(0).getId();

        // Update quantity to 3
        CartDto updated = cartService.updateItemQuantity(null, "session_test", itemId, 3);
        assertThat(updated.getTotalQuantity()).isEqualTo(3);
        assertThat(updated.getSubtotal()).isEqualByComparingTo(new BigDecimal("450.00"));

        // Remove item
        CartDto emptyCart = cartService.removeItem(null, "session_test", itemId);
        assertThat(emptyCart.getTotalQuantity()).isEqualTo(0);
        assertThat(emptyCart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("Should merge guest cart into user cart upon login")
    void testGuestCartMergeIntoAuthenticatedUser() {
        // Step 1: Guest adds an item
        AddToCartRequest guestReq = AddToCartRequest.builder()
                .productId(301L)
                .sku("SKU-301")
                .productName("Mechanical Keyboard")
                .unitPrice(new BigDecimal("89.99"))
                .quantity(1)
                .build();
        cartService.addItem(null, "guest_browser_token", guestReq);

        // Step 2: User already has a cart with another item
        AddToCartRequest userReq = AddToCartRequest.builder()
                .productId(302L)
                .sku("SKU-302")
                .productName("Ergonomic Mouse")
                .unitPrice(new BigDecimal("49.99"))
                .quantity(1)
                .build();
        cartService.addItem(42L, null, userReq);

        // Step 3: User logs in with guest sessionToken
        Cart mergedCart = cartService.getOrCreateCart(42L, "guest_browser_token");
        CartDto mergedDto = cartService.mapToDto(mergedCart);

        assertThat(mergedDto.getUserId()).isEqualTo(42L);
        assertThat(mergedDto.getItems()).hasSize(2);
        assertThat(mergedDto.getTotalQuantity()).isEqualTo(2);
        assertThat(mergedDto.getSubtotal()).isEqualByComparingTo(new BigDecimal("139.98"));
    }

    @Test
    @DisplayName("Should batch sync cart items")
    void testCartSync() {
        List<AddToCartRequest> items = List.of(
                AddToCartRequest.builder().productId(1L).productName("Item A").unitPrice(new BigDecimal("10.00")).quantity(2).build(),
                AddToCartRequest.builder().productId(2L).productName("Item B").unitPrice(new BigDecimal("25.00")).quantity(1).build()
        );

        CartDto synced = cartService.syncCart(100L, null, items);
        assertThat(synced.getItems()).hasSize(2);
        assertThat(synced.getTotalQuantity()).isEqualTo(3);
        assertThat(synced.getSubtotal()).isEqualByComparingTo(new BigDecimal("45.00"));
    }

    @Test
    @DisplayName("Should validate cart items stock status")
    void testCartValidation() {
        AddToCartRequest req = AddToCartRequest.builder()
                .productId(500L)
                .productName("Laptop Sleeve")
                .unitPrice(new BigDecimal("29.99"))
                .quantity(2)
                .build();
        cartService.addItem(77L, null, req);

        CartValidationResultDto validation = cartService.validateCart(77L, null);
        assertThat(validation).isNotNull();
        assertThat(validation.isValid()).isTrue();
        assertThat(validation.getItems()).hasSize(1);
        assertThat(validation.getItems().get(0).isInStock()).isTrue();
    }

    @Test
    @DisplayName("Should detect abandoned carts and trigger recovery reminders")
    void testAbandonedCartDetectionAndReminder() {
        // Create an inactive cart updated 30 hours ago
        Cart oldCart = Cart.builder()
                .userId(99L)
                .sessionToken("old_session")
                .status("ACTIVE")
                .currency("USD")
                .build();
        oldCart = cartRepository.save(oldCart);

        cartService.addItem(99L, "old_session", AddToCartRequest.builder()
                .productId(88L)
                .productName("Coffee Maker")
                .unitPrice(new BigDecimal("79.00"))
                .quantity(1)
                .build());

        // Artificially age the cart using direct JPQL update to bypass @UpdateTimestamp
        cartRepository.forceUpdateTimestamp(oldCart.getId(), OffsetDateTime.now().minusHours(30));
        entityManager.clear();

        List<AbandonedCartDto> abandoned = cartService.getAbandonedCarts(24);
        assertThat(abandoned).isNotEmpty();
        assertThat(abandoned.get(0).getCartId()).isEqualTo(oldCart.getId());

        // Send recovery reminder
        boolean reminderSent = cartService.triggerAbandonedCartAlert(oldCart.getId(), "SAVE15", new BigDecimal("15.00"));
        assertThat(reminderSent).isTrue();

        Cart updated = cartRepository.findById(oldCart.getId()).get();
        assertThat(updated.getStatus()).isEqualTo("ABANDONED");
    }

    @Test
    @DisplayName("Should compute cart funnel analytics")
    void testCartFunnelStats() {
        cartService.addItem(10L, null, AddToCartRequest.builder().productId(1L).productName("P1").unitPrice(new BigDecimal("50.00")).quantity(1).build());
        cartService.markCartConverted(10L);

        cartService.addItem(20L, null, AddToCartRequest.builder().productId(2L).productName("P2").unitPrice(new BigDecimal("100.00")).quantity(2).build());

        CartFunnelDto funnel = cartService.getCartFunnelStats();
        assertThat(funnel).isNotNull();
        assertThat(funnel.getTotalCartsCreated()).isGreaterThanOrEqualTo(2);
        assertThat(funnel.getConvertedCarts()).isGreaterThanOrEqualTo(1);
        assertThat(funnel.getActiveCarts()).isGreaterThanOrEqualTo(1);
    }
}
