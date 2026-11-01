package com.ecommerce.cart;

import com.ecommerce.cart.service.CartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading a cart creates it when the shopper has none, so those methods must run in a read-write transaction.
 * PostgreSQL rejects the INSERT inside a read-only one (GET /api/cart answered 500); the H2 tests do not enforce
 * read-only mode, so only this contract check catches a regression without a Postgres database.
 */
class CartTransactionContractTest {

    private static final List<String> METHODS_THAT_MAY_CREATE_A_CART = List.of("getCart", "validateCart");

    @Test
    @DisplayName("methods that can create a cart are not read-only transactions")
    void methodsThatCanCreateACartAreReadWrite() {
        for (String name : METHODS_THAT_MAY_CREATE_A_CART) {
            Method method = Arrays.stream(CartService.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("CartService." + name + " not found"));
            Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
            assertThat(transactional).as("CartService." + name + " is transactional").isNotNull();
            assertThat(transactional.readOnly()).as("CartService." + name + " readOnly").isFalse();
        }
    }
}
