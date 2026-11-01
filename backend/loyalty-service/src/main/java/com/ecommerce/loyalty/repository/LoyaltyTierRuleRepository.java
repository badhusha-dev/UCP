package com.ecommerce.loyalty.repository;

import com.ecommerce.loyalty.model.LoyaltyTier;
import com.ecommerce.loyalty.model.LoyaltyTierRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoyaltyTierRuleRepository extends JpaRepository<LoyaltyTierRule, Long> {
    Optional<LoyaltyTierRule> findByTier(LoyaltyTier tier);
}
