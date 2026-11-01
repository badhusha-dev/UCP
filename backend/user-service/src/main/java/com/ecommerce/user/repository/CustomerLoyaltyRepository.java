package com.ecommerce.user.repository;

import com.ecommerce.user.model.CustomerLoyalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerLoyaltyRepository extends JpaRepository<CustomerLoyalty, Long> {
    Optional<CustomerLoyalty> findByUserId(Long userId);
}
