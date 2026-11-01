package com.ecommerce.loyalty.repository;

import com.ecommerce.loyalty.model.LoyaltyAccount;
import com.ecommerce.loyalty.model.LoyaltyTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {
    Optional<LoyaltyAccount> findByUserId(Long userId);
    List<LoyaltyAccount> findAllByTier(LoyaltyTier tier);
    long countByTier(LoyaltyTier tier);

    @Query("SELECT SUM(a.pointsBalance) FROM LoyaltyAccount a")
    Long sumTotalPointsOutstanding();

    @Query("SELECT SUM(a.lifetimePoints) FROM LoyaltyAccount a")
    Long sumTotalLifetimePoints();
}
