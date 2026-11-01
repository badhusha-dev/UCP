package com.ecommerce.loyalty.service;

import com.ecommerce.loyalty.dto.*;
import com.ecommerce.loyalty.model.*;
import com.ecommerce.loyalty.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.ecommerce.outbox.OutboxWriter;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LoyaltyService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyService.class);
    private static final BigDecimal POINT_VALUE_USD = new BigDecimal("0.05");

    @Autowired
    private LoyaltyAccountRepository accountRepository;

    @Autowired
    private LoyaltyTransactionRepository transactionRepository;

    @Autowired
    private LoyaltyTierRuleRepository tierRuleRepository;

    @Autowired
    private OutboxWriter outbox;

    // ─────────────────────────────────────────────
    // Profile
    // ─────────────────────────────────────────────

    @Transactional
    public LoyaltyAccountDto getOrCreateProfile(Long userId) {
        return getOrCreateProfile(userId, null);
    }

    @Transactional
    public LoyaltyAccountDto getOrCreateProfile(Long userId, java.util.UUID tenantId) {
        LoyaltyAccount account = (tenantId != null)
                ? accountRepository.findByUserIdAndTenantId(userId, tenantId)
                        .orElseGet(() -> {
                            LoyaltyAccount a = new LoyaltyAccount(userId);
                            a.setTenantId(tenantId);
                            return accountRepository.save(a);
                        })
                : accountRepository.findByUserId(userId)
                        .orElseGet(() -> accountRepository.save(new LoyaltyAccount(userId)));
        return toDto(account, true);
    }

    public LoyaltyAccountDto getProfile(Long userId) {
        LoyaltyAccount account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("No loyalty account for userId=" + userId));
        return toDto(account, true);
    }

    // ─────────────────────────────────────────────
    // Earn points
    // ─────────────────────────────────────────────

    @Transactional
    public LoyaltyAccountDto earnPoints(Long userId, EarnPointsRequest req) {
        return earnPoints(userId, null, req);
    }

    @Transactional
    public LoyaltyAccountDto earnPoints(Long userId, java.util.UUID tenantId, EarnPointsRequest req) {
        LoyaltyAccount account = (tenantId != null)
                ? accountRepository.findByUserIdAndTenantId(userId, tenantId)
                        .orElseGet(() -> { LoyaltyAccount a = new LoyaltyAccount(userId); a.setTenantId(tenantId); return accountRepository.save(a); })
                : accountRepository.findByUserId(userId)
                        .orElseGet(() -> accountRepository.save(new LoyaltyAccount(userId)));

        BigDecimal multiplier = getTierMultiplier(account.getTier());
        int basePoints = req.getOrderAmount().multiply(BigDecimal.TEN).intValue(); // 10 pts per $1
        int earnedPoints = BigDecimal.valueOf(basePoints).multiply(multiplier).setScale(0, RoundingMode.HALF_UP).intValue();

        account.setPointsBalance(account.getPointsBalance() + earnedPoints);
        account.setLifetimePoints(account.getLifetimePoints() + earnedPoints);
        LoyaltyTier prevTier = account.getTier();
        account.recalculateTier();
        accountRepository.save(account);

        String desc = req.getDescription() != null ? req.getDescription()
                : String.format("Earned %d points for order %s (%.2fx multiplier)", earnedPoints, req.getOrderId(), multiplier);

        LoyaltyTransaction tx = new LoyaltyTransaction(
                account.getId(), userId, earnedPoints,
                LoyaltyTransaction.TransactionType.EARN,
                req.getReferenceId() != null ? req.getReferenceId() : req.getOrderId(), desc);
        transactionRepository.save(tx);

        if (account.getTier() != prevTier) {
            publishEvent("loyalty.tier.upgraded", Map.of(
                    "userId", userId,
                    "oldTier", prevTier.name(),
                    "newTier", account.getTier().name()
            ));
        }

        publishEvent("loyalty.points.earned", Map.of(
                "userId", userId,
                "points", earnedPoints,
                "orderId", Optional.ofNullable(req.getOrderId()).orElse(""),
                "newBalance", account.getPointsBalance()
        ));

        return toDto(account, false);
    }

    // ─────────────────────────────────────────────
    // Redeem points
    // ─────────────────────────────────────────────

    @Transactional
    public RedeemPointsResponse redeemPoints(Long userId, RedeemPointsRequest req) {
        return redeemPoints(userId, null, req);
    }

    @Transactional
    public RedeemPointsResponse redeemPoints(Long userId, java.util.UUID tenantId, RedeemPointsRequest req) {
        LoyaltyAccount account = (tenantId != null)
                ? accountRepository.findByUserIdAndTenantId(userId, tenantId)
                        .orElseThrow(() -> new NoSuchElementException("No loyalty account for userId=" + userId + " in tenant " + tenantId))
                : accountRepository.findByUserId(userId)
                        .orElseThrow(() -> new NoSuchElementException("No loyalty account for userId=" + userId));

        RedeemPointsResponse resp = new RedeemPointsResponse();

        if (req.getPoints() <= 0) {
            resp.setSuccess(false);
            resp.setMessage("Points to redeem must be positive");
            return resp;
        }
        if (account.getPointsBalance() < req.getPoints()) {
            resp.setSuccess(false);
            resp.setMessage(String.format("Insufficient points. Balance: %d, Requested: %d",
                    account.getPointsBalance(), req.getPoints()));
            return resp;
        }

        BigDecimal discountAmount = BigDecimal.valueOf(req.getPoints()).multiply(POINT_VALUE_USD)
                .setScale(2, RoundingMode.HALF_UP);

        account.setPointsBalance(account.getPointsBalance() - req.getPoints());
        accountRepository.save(account);

        String desc = String.format("Redeemed %d points = $%.2f discount for order %s",
                req.getPoints(), discountAmount, req.getOrderId());
        LoyaltyTransaction tx = new LoyaltyTransaction(
                account.getId(), userId, -req.getPoints(),
                LoyaltyTransaction.TransactionType.REDEEM,
                req.getOrderId(), desc);
        transactionRepository.save(tx);

        publishEvent("loyalty.points.redeemed", Map.of(
                "userId", userId,
                "points", req.getPoints(),
                "discountAmount", discountAmount,
                "orderId", Optional.ofNullable(req.getOrderId()).orElse("")
        ));

        resp.setSuccess(true);
        resp.setPointsRedeemed(req.getPoints());
        resp.setDiscountAmount(discountAmount);
        resp.setRemainingBalance(account.getPointsBalance());
        resp.setMessage("Points redeemed successfully");
        return resp;
    }

    // ─────────────────────────────────────────────
    // Bonus points (admin)
    // ─────────────────────────────────────────────

    @Transactional
    public LoyaltyAccountDto awardBonus(Long userId, BonusPointsRequest req) {
        return awardBonus(userId, null, req);
    }

    @Transactional
    public LoyaltyAccountDto awardBonus(Long userId, java.util.UUID tenantId, BonusPointsRequest req) {
        LoyaltyAccount account = (tenantId != null)
                ? accountRepository.findByUserIdAndTenantId(userId, tenantId)
                        .orElseGet(() -> { LoyaltyAccount a = new LoyaltyAccount(userId); a.setTenantId(tenantId); return accountRepository.save(a); })
                : accountRepository.findByUserId(userId)
                        .orElseGet(() -> accountRepository.save(new LoyaltyAccount(userId)));

        account.setPointsBalance(account.getPointsBalance() + req.getPoints());
        account.setLifetimePoints(account.getLifetimePoints() + req.getPoints());
        account.recalculateTier();
        accountRepository.save(account);

        LoyaltyTransaction tx = new LoyaltyTransaction(
                account.getId(), userId, req.getPoints(),
                LoyaltyTransaction.TransactionType.BONUS,
                null, "Admin bonus: " + req.getReason());
        transactionRepository.save(tx);

        return toDto(account, false);
    }

    // ─────────────────────────────────────────────
    // Transactions
    // ─────────────────────────────────────────────

    public List<LoyaltyTransactionDto> getTransactions(Long userId, int page, int size) {
        return getTransactions(userId, null, page, size);
    }

    public List<LoyaltyTransactionDto> getTransactions(Long userId, java.util.UUID tenantId, int page, int size) {
        // Transactions are scoped by userId; tenant isolation is enforced at the account level
        return transactionRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .getContent()
                .stream()
                .map(this::toTransactionDto)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────
    // Admin stats
    // ─────────────────────────────────────────────

    public LoyaltyAdminStatsDto getAdminStats() {
        LoyaltyAdminStatsDto dto = new LoyaltyAdminStatsDto();
        dto.setTotalAccounts(accountRepository.count());
        dto.setTierBreakdown(buildTierBreakdown());
        Long outstanding = accountRepository.sumTotalPointsOutstanding();
        dto.setTotalPointsOutstanding(outstanding != null ? outstanding : 0L);
        Long lifetime = accountRepository.sumTotalLifetimePoints();
        dto.setTotalLifetimePointsIssued(lifetime != null ? lifetime : 0L);
        BigDecimal liability = BigDecimal.valueOf(outstanding != null ? outstanding : 0L)
                .multiply(POINT_VALUE_USD).setScale(2, RoundingMode.HALF_UP);
        dto.setEstimatedLiabilityUsd(liability);
        dto.setPointValueUsd(POINT_VALUE_USD);
        return dto;
    }

    public List<LoyaltyAccountDto> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(a -> toDto(a, false))
                .collect(Collectors.toList());
    }

    public List<LoyaltyTierRuleDto> getAllTierRules() {
        return tierRuleRepository.findAll().stream()
                .map(this::toTierRuleDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public LoyaltyTierRuleDto updateTierRule(LoyaltyTier tier, LoyaltyTierRuleDto request) {
        if (request.getMinLifetimePoints() == null || request.getMinLifetimePoints() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum lifetime points must be zero or greater.");
        if (request.getMultiplier() == null || request.getMultiplier().signum() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tier multiplier must be greater than zero.");
        BigDecimal discount = request.getDiscountPercentBonus() == null ? BigDecimal.ZERO : request.getDiscountPercentBonus();
        if (discount.signum() < 0 || discount.compareTo(new BigDecimal("100")) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount bonus must be between 0 and 100 percent.");

        LoyaltyTierRule rule = tierRuleRepository.findByTier(tier)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tier rule not found: " + tier));
        rule.setMinLifetimePoints(request.getMinLifetimePoints());
        rule.setMultiplier(request.getMultiplier());
        rule.setDiscountPercentBonus(discount);
        rule.setPerksDescription(request.getPerksDescription());
        return toTierRuleDto(tierRuleRepository.save(rule));
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private BigDecimal getTierMultiplier(LoyaltyTier tier) {
        return tierRuleRepository.findByTier(tier)
                .map(LoyaltyTierRule::getMultiplier)
                .orElse(BigDecimal.ONE);
    }

    private Map<String, Long> buildTierBreakdown() {
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (LoyaltyTier t : LoyaltyTier.values()) {
            breakdown.put(t.name(), accountRepository.countByTier(t));
        }
        return breakdown;
    }

    private LoyaltyAccountDto toDto(LoyaltyAccount a, boolean includeTransactions) {
        LoyaltyAccountDto dto = new LoyaltyAccountDto();
        dto.setId(a.getId());
        dto.setUserId(a.getUserId());
        dto.setPointsBalance(a.getPointsBalance());
        dto.setLifetimePoints(a.getLifetimePoints());
        dto.setTier(a.getTier());
        dto.setCashValue(BigDecimal.valueOf(a.getPointsBalance()).multiply(POINT_VALUE_USD).setScale(2, RoundingMode.HALF_UP));
        dto.setUpdatedAt(a.getUpdatedAt());

        // Next tier calculation
        LoyaltyTier nextTier = nextTier(a.getTier());
        dto.setNextTier(nextTier);
        if (nextTier != null) {
            int nextMin = tierRuleRepository.findByTier(nextTier).map(LoyaltyTierRule::getMinLifetimePoints).orElse(0);
            dto.setPointsToNextTier(Math.max(0, nextMin - a.getLifetimePoints()));
        }
        dto.setTierMultiplier(getTierMultiplier(a.getTier()));

        if (includeTransactions) {
            dto.setRecentTransactions(
                    transactionRepository.findByUserIdOrderByCreatedAtDesc(a.getUserId(),
                            PageRequest.of(0, 10)).getContent()
                            .stream().map(this::toTransactionDto).collect(Collectors.toList())
            );
        }
        return dto;
    }

    private LoyaltyTransactionDto toTransactionDto(LoyaltyTransaction t) {
        LoyaltyTransactionDto dto = new LoyaltyTransactionDto();
        dto.setId(t.getId());
        dto.setPoints(t.getPoints());
        dto.setTransactionType(t.getTransactionType());
        dto.setReferenceId(t.getReferenceId());
        dto.setDescription(t.getDescription());
        dto.setCreatedAt(t.getCreatedAt());
        return dto;
    }

    private LoyaltyTierRuleDto toTierRuleDto(LoyaltyTierRule r) {
        LoyaltyTierRuleDto dto = new LoyaltyTierRuleDto();
        dto.setTier(r.getTier());
        dto.setMinLifetimePoints(r.getMinLifetimePoints());
        dto.setMultiplier(r.getMultiplier());
        dto.setDiscountPercentBonus(r.getDiscountPercentBonus());
        dto.setPerksDescription(r.getPerksDescription());
        return dto;
    }

    private LoyaltyTier nextTier(LoyaltyTier current) {
        return switch (current) {
            case BRONZE -> LoyaltyTier.SILVER;
            case SILVER -> LoyaltyTier.GOLD;
            case GOLD -> LoyaltyTier.PLATINUM;
            case PLATINUM -> null;
        };
    }

    /**
     * Writes the event to the outbox in the caller's transaction; it is published to {@code topic} after commit.
     * The payload format is unchanged because crm-service consumes these topics.
     */
    private void publishEvent(String topic, Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder("{");
        payload.forEach((k, v) -> sb.append("\"").append(k).append("\":\"").append(v).append("\","));
        if (sb.length() > 1) sb.setLength(sb.length() - 1);
        sb.append("}");
        outbox.append(topic, null, topic, sb.toString());
    }
}
