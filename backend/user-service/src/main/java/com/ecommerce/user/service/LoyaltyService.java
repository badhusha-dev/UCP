package com.ecommerce.user.service;

import com.ecommerce.user.dto.EarnPointsRequest;
import com.ecommerce.user.dto.LoyaltyAccountDto;
import com.ecommerce.user.dto.RedeemPointsRequest;
import com.ecommerce.user.dto.RedeemPointsResponse;
import com.ecommerce.user.model.CustomerLoyalty;
import com.ecommerce.user.model.LoyaltyTransaction;
import com.ecommerce.user.repository.CustomerLoyaltyRepository;
import com.ecommerce.user.repository.LoyaltyTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyService {

    private final CustomerLoyaltyRepository loyaltyRepository;
    private final LoyaltyTransactionRepository transactionRepository;

    public LoyaltyAccountDto getLoyaltyProfile(Long userId) {
        CustomerLoyalty loyalty = loyaltyRepository.findByUserId(userId)
                .orElseGet(() -> {
                    CustomerLoyalty created = new CustomerLoyalty();
                    created.setUserId(userId);
                    created.setPointsBalance(0);
                    created.setLifetimePoints(0);
                    created.setTier("BRONZE");
                    created.setUpdatedAt(OffsetDateTime.now());
                    return loyaltyRepository.save(created);
                });

        List<LoyaltyTransaction> recent = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // 100 points = $5.00 cash value -> 1 point = $0.05
        BigDecimal cashValue = BigDecimal.valueOf(loyalty.getPointsBalance())
                .multiply(new BigDecimal("0.05"))
                .setScale(2, RoundingMode.HALF_UP);

        int pointsToNext;
        String nextTier;
        int lifetime = loyalty.getLifetimePoints();
        if (lifetime < 500) {
            pointsToNext = 500 - lifetime;
            nextTier = "SILVER";
        } else if (lifetime < 2000) {
            pointsToNext = 2000 - lifetime;
            nextTier = "GOLD";
        } else if (lifetime < 5000) {
            pointsToNext = 5000 - lifetime;
            nextTier = "PLATINUM";
        } else {
            pointsToNext = 0;
            nextTier = "MAX_TIER";
        }

        return LoyaltyAccountDto.builder()
                .userId(userId)
                .pointsBalance(loyalty.getPointsBalance())
                .lifetimePoints(loyalty.getLifetimePoints())
                .tier(loyalty.getTier())
                .cashValue(cashValue)
                .pointsToNextTier(pointsToNext)
                .nextTier(nextTier)
                .recentTransactions(recent)
                .build();
    }

    @Transactional
    public CustomerLoyalty earnPoints(Long userId, EarnPointsRequest request) {
        CustomerLoyalty loyalty = loyaltyRepository.findByUserId(userId)
                .orElseGet(() -> {
                    CustomerLoyalty created = new CustomerLoyalty();
                    created.setUserId(userId);
                    return loyaltyRepository.save(created);
                });

        BigDecimal orderAmt = request.getOrderAmount() != null ? request.getOrderAmount() : BigDecimal.ZERO;
        // 1 dollar spent = 1 loyalty point (Gold/Platinum get multiplier)
        double multiplier = "PLATINUM".equalsIgnoreCase(loyalty.getTier()) ? 2.0
                : "GOLD".equalsIgnoreCase(loyalty.getTier()) ? 1.5
                : "SILVER".equalsIgnoreCase(loyalty.getTier()) ? 1.25 : 1.0;

        int pointsEarned = (int) Math.round(orderAmt.doubleValue() * multiplier);
        if (pointsEarned <= 0) {
            pointsEarned = 1;
        }

        loyalty.setPointsBalance(loyalty.getPointsBalance() + pointsEarned);
        loyalty.setLifetimePoints(loyalty.getLifetimePoints() + pointsEarned);
        loyalty.recalculateTier();
        CustomerLoyalty saved = loyaltyRepository.save(loyalty);

        LoyaltyTransaction tx = new LoyaltyTransaction();
        tx.setUserId(userId);
        tx.setPoints(pointsEarned);
        tx.setTransactionType("EARN");
        tx.setDescription("Points earned for Order #" + (request.getOrderId() != null ? request.getOrderId() : "purchase") + " ($" + orderAmt + ")");
        tx.setCreatedAt(OffsetDateTime.now());
        transactionRepository.save(tx);

        return saved;
    }

    @Transactional
    public RedeemPointsResponse redeemPoints(Long userId, RedeemPointsRequest request) {
        CustomerLoyalty loyalty = loyaltyRepository.findByUserId(userId)
                .orElseGet(() -> {
                    CustomerLoyalty created = new CustomerLoyalty();
                    created.setUserId(userId);
                    return loyaltyRepository.save(created);
                });

        int pts = request.getPoints();
        if (pts <= 0) {
            throw new IllegalArgumentException("Points to redeem must be greater than zero");
        }
        if (loyalty.getPointsBalance() < pts) {
            return RedeemPointsResponse.builder()
                    .success(false)
                    .pointsRedeemed(0)
                    .discountAmount(BigDecimal.ZERO)
                    .remainingBalance(loyalty.getPointsBalance())
                    .message("Insufficient reward points. Current balance: " + loyalty.getPointsBalance())
                    .build();
        }

        BigDecimal discount = BigDecimal.valueOf(pts)
                .multiply(new BigDecimal("0.05"))
                .setScale(2, RoundingMode.HALF_UP);

        loyalty.setPointsBalance(loyalty.getPointsBalance() - pts);
        loyalty.setUpdatedAt(OffsetDateTime.now());
        loyaltyRepository.save(loyalty);

        LoyaltyTransaction tx = new LoyaltyTransaction();
        tx.setUserId(userId);
        tx.setPoints(-pts);
        tx.setTransactionType("REDEEM");
        tx.setDescription("Points redeemed for $" + discount + " checkout discount on Order #" + (request.getOrderId() != null ? request.getOrderId() : "checkout"));
        tx.setCreatedAt(OffsetDateTime.now());
        transactionRepository.save(tx);

        return RedeemPointsResponse.builder()
                .success(true)
                .pointsRedeemed(pts)
                .discountAmount(discount)
                .remainingBalance(loyalty.getPointsBalance())
                .message("Successfully redeemed " + pts + " points for a $" + discount + " discount!")
                .build();
    }
}
