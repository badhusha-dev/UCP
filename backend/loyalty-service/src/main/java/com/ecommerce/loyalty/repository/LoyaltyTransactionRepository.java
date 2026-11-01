package com.ecommerce.loyalty.repository;

import com.ecommerce.loyalty.model.LoyaltyTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {
    List<LoyaltyTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<LoyaltyTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<LoyaltyTransaction> findByLoyaltyAccountId(Long loyaltyAccountId);
}
