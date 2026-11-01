package com.ecommerce.order.service;

import com.ecommerce.order.model.CustomerCart;
import com.ecommerce.order.repository.CustomerCartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartPersistenceService {

    private final CustomerCartRepository cartRepository;
    private final RestTemplate restTemplate;

    @Value("${services.cart.url:http://cart-service:8086}")
    private String cartServiceUrl;

    public Optional<CustomerCart> getCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId);
    }

    @Transactional
    public CustomerCart syncCart(Long userId, String itemsJson) {
        CustomerCart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    CustomerCart newCart = new CustomerCart();
                    newCart.setUserId(userId);
                    newCart.setCreatedAt(OffsetDateTime.now());
                    return newCart;
                });

        cart.setItemsJson(itemsJson != null ? itemsJson : "[]");
        cart.setStatus("ACTIVE");
        cart.setUpdatedAt(OffsetDateTime.now());
        return cartRepository.save(cart);
    }

    @Transactional
    public void markCartConverted(Long userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.setStatus("CONVERTED");
            cart.setUpdatedAt(OffsetDateTime.now());
            cartRepository.save(cart);
        });
        try {
            restTemplate.postForEntity(cartServiceUrl + "/api/cart/convert?userId=" + userId, null, Void.class);
        } catch (Exception ex) {
            log.debug("Cart service notification failed: {}", ex.getMessage());
        }
    }

    @Transactional
    public void clearCart(Long userId) {
        cartRepository.findByUserId(userId).ifPresent(cartRepository::delete);
    }

    public List<CustomerCart> getAbandonedCarts(int inactiveHours) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(inactiveHours);
        return cartRepository.findByStatusAndUpdatedAtBefore("ACTIVE", cutoff);
    }

    @Transactional
    public boolean triggerAbandonedCartAlert(Long cartId) {
        Optional<CustomerCart> opt = cartRepository.findById(cartId);
        if (opt.isPresent()) {
            CustomerCart cart = opt.get();
            log.info("Sending abandoned cart notification reminder to customer #{} for cart items: {}",
                    cart.getUserId(), cart.getItemsJson());
            cart.setStatus("ABANDONED");
            cartRepository.save(cart);
            return true;
        }
        return false;
    }
}
