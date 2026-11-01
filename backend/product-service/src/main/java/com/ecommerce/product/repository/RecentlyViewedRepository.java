package com.ecommerce.product.repository;

import com.ecommerce.product.model.RecentlyViewed;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecentlyViewedRepository extends JpaRepository<RecentlyViewed, Long> {
    List<RecentlyViewed> findByUserIdOrderByViewedAtDesc(Long userId, Pageable pageable);
    Optional<RecentlyViewed> findByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserIdAndProductId(Long userId, Long productId);
}
