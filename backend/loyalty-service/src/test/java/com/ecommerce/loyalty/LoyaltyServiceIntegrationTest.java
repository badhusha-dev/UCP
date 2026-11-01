package com.ecommerce.loyalty;

import com.ecommerce.loyalty.dto.*;
import com.ecommerce.loyalty.model.LoyaltyTier;
import com.ecommerce.loyalty.service.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoyaltyServiceIntegrationTest {

    @Autowired
    private LoyaltyService loyaltyService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void testEarnPoints_recordsPointsAndTierEventsInOutbox() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        loyaltyService.getOrCreateProfile(9100L);

        EarnPointsRequest req = new EarnPointsRequest();
        req.setOrderId("ORD-OUTBOX");
        req.setOrderAmount(new BigDecimal("100.00"));
        loyaltyService.earnPoints(9100L, req); // 1000 pts: BRONZE -> SILVER

        java.util.List<String> topics = jdbcTemplate.queryForList(
                "SELECT topic FROM outbox_events WHERE status = 'NEW' ORDER BY created_at", String.class);
        assertThat(topics).contains("loyalty.points.earned", "loyalty.tier.upgraded");
        String payload = jdbcTemplate.queryForObject(
                "SELECT payload FROM outbox_events WHERE topic = 'loyalty.points.earned'", String.class);
        assertThat(payload).startsWith("{").endsWith("}").contains("\"userId\":\"9100\"");
    }

    @Test
    void testGetOrCreateProfile_createsNewAccount() {
        LoyaltyAccountDto profile = loyaltyService.getOrCreateProfile(9001L);
        assertThat(profile).isNotNull();
        assertThat(profile.getUserId()).isEqualTo(9001L);
        assertThat(profile.getPointsBalance()).isEqualTo(0);
        assertThat(profile.getTier()).isEqualTo(LoyaltyTier.BRONZE);
    }

    @Test
    void testEarnPoints_basicAccumulation() {
        loyaltyService.getOrCreateProfile(9002L);

        EarnPointsRequest req = new EarnPointsRequest();
        req.setOrderId("ORD-001");
        req.setOrderAmount(new BigDecimal("100.00"));
        req.setReferenceId("REF-001");

        LoyaltyAccountDto result = loyaltyService.earnPoints(9002L, req);
        // 100 * 10 pts/dollar * 1.0x BRONZE = 1000 pts
        assertThat(result.getPointsBalance()).isEqualTo(1000);
        assertThat(result.getLifetimePoints()).isEqualTo(1000);
    }

    @Test
    void testEarnPoints_tierPromotion() {
        loyaltyService.getOrCreateProfile(9003L);

        // Earn enough for SILVER (1000 lifetime pts)
        EarnPointsRequest req = new EarnPointsRequest();
        req.setOrderId("ORD-002");
        req.setOrderAmount(new BigDecimal("100.00"));

        LoyaltyAccountDto result = loyaltyService.earnPoints(9003L, req);
        assertThat(result.getTier()).isEqualTo(LoyaltyTier.SILVER);
    }

    @Test
    void testRedeemPoints_success() {
        loyaltyService.getOrCreateProfile(9004L);

        EarnPointsRequest earn = new EarnPointsRequest();
        earn.setOrderId("ORD-003");
        earn.setOrderAmount(new BigDecimal("50.00"));
        loyaltyService.earnPoints(9004L, earn);

        RedeemPointsRequest redeem = new RedeemPointsRequest();
        redeem.setPoints(100);
        redeem.setOrderId("ORD-004");

        RedeemPointsResponse resp = loyaltyService.redeemPoints(9004L, redeem);
        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getPointsRedeemed()).isEqualTo(100);
        // 100 pts * $0.05 = $5.00
        assertThat(resp.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void testRedeemPoints_insufficientBalance() {
        loyaltyService.getOrCreateProfile(9005L);

        RedeemPointsRequest redeem = new RedeemPointsRequest();
        redeem.setPoints(500);
        redeem.setOrderId("ORD-005");

        RedeemPointsResponse resp = loyaltyService.redeemPoints(9005L, redeem);
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getMessage()).contains("Insufficient");
    }

    @Test
    void testAwardBonus_adminBonus() {
        loyaltyService.getOrCreateProfile(9006L);

        BonusPointsRequest bonus = new BonusPointsRequest();
        bonus.setPoints(250);
        bonus.setReason("Welcome bonus");

        LoyaltyAccountDto result = loyaltyService.awardBonus(9006L, bonus);
        assertThat(result.getPointsBalance()).isEqualTo(250);
    }

    @Test
    void testGetAdminStats() {
        loyaltyService.getOrCreateProfile(9007L);
        LoyaltyAdminStatsDto stats = loyaltyService.getAdminStats();
        assertThat(stats).isNotNull();
        assertThat(stats.getTotalAccounts()).isGreaterThan(0);
        assertThat(stats.getTierBreakdown()).containsKeys("BRONZE", "SILVER", "GOLD", "PLATINUM");
    }

    @Test
    void testGetTransactions_returnsHistory() {
        loyaltyService.getOrCreateProfile(9008L);

        EarnPointsRequest earn = new EarnPointsRequest();
        earn.setOrderId("ORD-010");
        earn.setOrderAmount(new BigDecimal("20.00"));
        loyaltyService.earnPoints(9008L, earn);

        var transactions = loyaltyService.getTransactions(9008L, 0, 10);
        assertThat(transactions).isNotEmpty();
        assertThat(transactions.get(0).getTransactionType().name()).isEqualTo("EARN");
    }
}
