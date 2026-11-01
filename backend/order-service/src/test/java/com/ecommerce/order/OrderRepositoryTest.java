package com.ecommerce.order;

import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save order with cascade items and retrieve by userId")
    void testSaveAndFindByUserId() {
        Order order = new Order();
        order.setUserId(42L);
        order.setStatus("PLACED");
        order.setTotalAmount(new BigDecimal("199.98"));

        OrderItem item1 = new OrderItem();
        item1.setProductId(1L);
        item1.setProductName("Test Item 1");
        item1.setPrice(new BigDecimal("99.99"));
        item1.setQuantity(2);
        order.addItem(item1);

        Order saved = orderRepository.save(order);
        assertThat(saved.getId()).isNotNull();

        List<Order> userOrders = orderRepository.findByUserId(42L);
        assertThat(userOrders).hasSize(1);
        assertThat(userOrders.get(0).getStatus()).isEqualTo("PLACED");
        assertThat(userOrders.get(0).getItems()).hasSize(1);
        assertThat(userOrders.get(0).getItems().get(0).getProductName()).isEqualTo("Test Item 1");
    }
}
