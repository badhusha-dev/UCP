package com.ecommerce.product.repository;

import com.ecommerce.product.model.CustomerWishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerWishlistRepository extends JpaRepository<CustomerWishlist, Long> {
    List<CustomerWishlist> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<CustomerWishlist> findByUserIdAndProductId(Long userId, Long productId);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserIdAndProductId(Long userId, Long productId);
}
