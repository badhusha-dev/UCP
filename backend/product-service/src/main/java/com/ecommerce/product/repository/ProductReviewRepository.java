package com.ecommerce.product.repository;

import com.ecommerce.product.model.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    List<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId);
    List<ProductReview> findByUserIdOrderByCreatedAtDesc(Long userId);
    boolean existsByProductIdAndUserId(Long productId, Long userId);
}
