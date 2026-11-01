package com.ecommerce.product;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("Should persist product and query by ID")
    void testSaveAndFindById() {
        Product product = new Product();
        product.setName("Gaming Keyboard");
        product.setDescription("Mechanical RGB Keyboard");
        product.setPrice(89.99);
        product.setStockQuantity(25);

        Product saved = productRepository.save(product);
        assertThat(saved.getId()).isNotNull();

        Optional<Product> found = productRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Gaming Keyboard");
        assertThat(found.get().getPrice()).isEqualTo(89.99);
        assertThat(found.get().getStockQuantity()).isEqualTo(25);
    }

    @Test
    @DisplayName("Should retrieve all catalog products")
    void testFindAll() {
        Product p1 = new Product();
        p1.setName("Mouse");
        p1.setPrice(29.99);
        p1.setStockQuantity(100);

        Product p2 = new Product();
        p2.setName("Monitor");
        p2.setPrice(299.99);
        p2.setStockQuantity(15);

        productRepository.saveAll(List.of(p1, p2));

        List<Product> products = productRepository.findAll();
        assertThat(products).hasSize(2);
    }
}
