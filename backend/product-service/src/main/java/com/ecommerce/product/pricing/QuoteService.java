package com.ecommerce.product.pricing;

import com.ecommerce.product.pricing.DiscountEngine.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static com.ecommerce.product.pricing.PricingSupport.*;

/**
 * Server-side price + promotion calculation used by checkout. Client-supplied prices are never trusted: unit prices come from
 * product_prices (falling back to the catalog price), and the payable total is always the value computed here.
 */
@Service
public class QuoteService {
    public record LineRequest(Long productId, Long variantId, Integer quantity) {}
    public record CustomerRequest(String id, List<String> segments, Boolean firstOrder) {}
    public record QuoteRequest(String storeId, String currency, CustomerRequest customer, List<LineRequest> lines, String couponCode, String orderRef) {}

    private final JdbcTemplate jdbc;
    private final PricingSettingsService settingsService;
    private final CampaignService campaigns;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public QuoteService(JdbcTemplate jdbc, PricingSettingsService settingsService, CampaignService campaigns, ObjectMapper mapper,
                        PlatformTransactionManager tm) {
        this.jdbc = jdbc; this.settingsService = settingsService; this.campaigns = campaigns; this.mapper = mapper; this.tx = new TransactionTemplate(tm);
    }

    /** Calculation only; nothing is consumed. */
    public Map<String, Object> quote(UUID tenant, QuoteRequest req) {
        return compute(tenant, req).response();
    }

    private record Computed(Map<String, Object> response, Result result, Long couponId, Long couponCampaignId, String code, String customerId, String currency) {}

    private Computed compute(UUID tenant, QuoteRequest req) {
        if (req.lines() == null || req.lines().isEmpty()) throw bad("At least one line is required.");
        if (req.lines().size() > 200) throw bad("At most 200 lines per quote.");
        String store = storeId(req.storeId());
        PricingSettingsService.Settings s = settingsService.effective(tenant, store);
        String currency = currency(req.currency() == null ? s.defaultCurrency() : req.currency(), s.supportedCurrencies());
        OffsetDateTime now = OffsetDateTime.now();

        List<CartLine> cart = new ArrayList<>();
        for (LineRequest l : req.lines()) {
            if (l.productId() == null) throw bad("productId is required.");
            if (l.quantity() == null || l.quantity() < 1 || l.quantity() > 10000) throw bad("quantity must be between 1 and 10000.");
            cart.add(resolveLine(tenant, store, currency, s, l, now));
        }
        Customer customer = req.customer() == null ? Customer.anonymous()
                : new Customer(req.customer().id(), req.customer().segments() == null ? Set.of() : Set.copyOf(req.customer().segments()),
                Boolean.TRUE.equals(req.customer().firstOrder()));

        Long couponId = null, couponCampaignId = null;
        String code = null, couponMessage = null;
        List<Campaign> pool = new ArrayList<>();
        if (req.couponCode() != null && !req.couponCode().isBlank()) {
            code = req.couponCode().trim().toUpperCase(Locale.ROOT);
            String failure = null;
            List<Map<String, Object>> found = jdbc.queryForList("SELECT * FROM coupon_codes WHERE tenant_id=? AND code=?", tenant, code);
            if (found.isEmpty()) failure = "Coupon is not valid.";
            else {
                Map<String, Object> cp = found.get(0);
                couponId = ((Number) cp.get("id")).longValue();
                couponCampaignId = ((Number) cp.get("campaign_id")).longValue();
                failure = couponProblem(tenant, cp, customer, now);
            }
            if (failure != null) { couponMessage = failure; couponId = null; couponCampaignId = null; }
        }
        Set<Long> include = couponCampaignId == null ? Set.of() : Set.of(couponCampaignId);
        for (Campaign c : campaigns.liveEngineCampaigns(tenant, store, now, currency, include)) {
            pool.add(couponCampaignId != null && c.id() == couponCampaignId
                    ? new Campaign(c.id(), c.name(), c.type(), c.scope(), c.priority(), c.stackable(), c.minOrderValue(), c.maxDiscountAmount(), c.segment(),
                    c.firstOrderOnly(), req.couponCode().trim().toUpperCase(Locale.ROOT), c.rules(), c.conditions(), c.rewards()) : c);
        }
        final Long wantedCampaign = couponCampaignId;
        if (wantedCampaign != null && pool.stream().noneMatch(c -> c.id() == wantedCampaign)) {
            couponMessage = "Coupon's promotion is not currently active for this order."; couponId = null; couponCampaignId = null;
        }
        Result result = DiscountEngine.calculate(cart, customer, pool, new Settings(s.maxDiscountPercent(), s.minMarginPercent(), s.stackingPolicy()));
        if (couponId != null) {
            final long cid = couponCampaignId;
            if (result.applied().stream().noneMatch(a -> a.campaignId() == cid)) {
                couponMessage = "Coupon does not apply to this order."; couponId = null; couponCampaignId = null;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("currency", currency);
        out.put("lines", result.lines());
        out.put("applied", result.applied());
        out.put("subtotal", result.subtotal());
        out.put("discount", result.discount());
        out.put("total", result.total());
        out.put("warnings", result.warnings());
        if (code != null) { out.put("couponCode", code); out.put("couponValid", couponId != null); if (couponMessage != null) out.put("couponMessage", couponMessage); }
        return new Computed(out, result, couponId, couponCampaignId, code, customer.id(), currency);
    }

    private String couponProblem(UUID tenant, Map<String, Object> cp, Customer customer, OffsetDateTime now) {
        if (customer.id() == null || customer.id().isBlank()) return "Sign in to use a coupon.";
        if (!"ACTIVE".equals(cp.get("status"))) return "Coupon is not valid.";
        OffsetDateTime exp = asOffset(cp.get("expires_at"));
        if (exp != null && !exp.isAfter(now)) return "Coupon has expired.";
        String restricted = (String) cp.get("restricted_customer_id");
        if (restricted != null && !restricted.equals(customer.id())) return "Coupon is not valid.";
        Integer limit = (Integer) cp.get("usage_limit");
        if (limit != null && ((Number) cp.get("redeemed_count")).intValue() >= limit) return "Coupon has been fully redeemed.";
        long id = ((Number) cp.get("id")).longValue();
        long campaignId = ((Number) cp.get("campaign_id")).longValue();
        Integer per = (Integer) cp.get("per_customer_limit");
        Integer campaignPer = jdbc.queryForObject("SELECT usage_limit_per_customer FROM promotion_campaigns WHERE id=? AND tenant_id=?", Integer.class, campaignId, tenant);
        if (per != null && countFor(tenant, "coupon_id", id, customer.id()) >= per) return "You have already used this coupon the maximum number of times.";
        if (campaignPer != null && countFor(tenant, "campaign_id", campaignId, customer.id()) >= campaignPer) return "You have reached the usage limit for this promotion.";
        return null;
    }

    private int countFor(UUID tenant, String column, long id, String customerId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM coupon_redemptions WHERE tenant_id=? AND " + column + "=? AND customer_id=? AND status='REDEEMED'",
                Integer.class, tenant, id, customerId);
        return n == null ? 0 : n;
    }

    private CartLine resolveLine(UUID tenant, String store, String currency, PricingSettingsService.Settings s, LineRequest l, OffsetDateTime now) {
        List<Map<String, Object>> products = jdbc.queryForList("SELECT id, price, cost_price, category_id, active FROM product WHERE id=? AND tenant_id=?", l.productId(), tenant);
        if (products.isEmpty() || Boolean.FALSE.equals(products.get(0).get("active"))) throw notFound("Product " + l.productId());
        Map<String, Object> p = products.get(0);
        long variant = l.variantId() == null ? 0 : l.variantId();
        List<Map<String, Object>> prices = jdbc.queryForList("SELECT * FROM product_prices WHERE tenant_id=? AND product_id=? AND variant_id=? AND currency=? AND status='ACTIVE' "
                + "AND (store_id=? OR store_id='*') AND effective_from<=? AND (effective_to IS NULL OR effective_to>?) ORDER BY CASE WHEN store_id='*' THEN 1 ELSE 0 END",
                tenant, l.productId(), variant, currency, store, now, now);
        BigDecimal unit; BigDecimal cost;
        if (!prices.isEmpty()) {
            Map<String, Object> pr = prices.get(0);
            unit = pr.get("sale_price") != null ? (BigDecimal) pr.get("sale_price") : (BigDecimal) pr.get("base_price");
            cost = pr.get("cost_price") != null ? (BigDecimal) pr.get("cost_price") : asDecimal(p.get("cost_price"));
        } else if (currency.equals(s.defaultCurrency())) {
            unit = BigDecimal.valueOf(((Number) p.get("price")).doubleValue()).setScale(2, java.math.RoundingMode.HALF_UP);
            cost = asDecimal(p.get("cost_price"));
        } else {
            throw bad("Product " + l.productId() + " has no price in " + currency + ".");
        }
        if (unit.signum() <= 0) throw bad("Product " + l.productId() + " has an invalid price.");
        Long category = p.get("category_id") == null ? null : ((Number) p.get("category_id")).longValue();
        BigDecimal minSelling = prices.isEmpty() ? null : (BigDecimal) prices.get(0).get("min_selling_price");
        BigDecimal maxPercent = prices.isEmpty() ? null : (BigDecimal) prices.get(0).get("max_discount_percent");
        return new CartLine(l.productId(), variant, category, l.quantity(), unit, cost, minSelling, maxPercent);
    }

    // ---- redemption --------------------------------------------------------------------------------------------------

    /**
     * Consumes the coupon and campaign usage atomically. Idempotent per (tenant, orderRef): a retry returns the original result.
     * Over-redemption is prevented by guarded UPDATEs whose WHERE clause carries the limit, so the database serializes racers.
     */
    public Map<String, Object> redeem(UUID tenant, QuoteRequest req) {
        if (req.orderRef() == null || req.orderRef().isBlank() || req.orderRef().length() > 64) throw bad("orderRef is required (max 64 chars).");
        try {
            return tx.execute(status -> doRedeem(tenant, req));
        } catch (DuplicateKeyException e) {
            return storedResult(tenant, req.orderRef());
        }
    }

    private Map<String, Object> storedResult(UUID tenant, String orderRef) {
        String json = jdbc.queryForObject("SELECT result FROM promotion_order_applications WHERE tenant_id=? AND order_ref=?", String.class, tenant, orderRef);
        try {
            @SuppressWarnings("unchecked") Map<String, Object> m = mapper.readValue(json, Map.class);
            m.put("idempotentReplay", true);
            return m;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private Map<String, Object> doRedeem(UUID tenant, QuoteRequest req) {
        List<Map<String, Object>> prior = jdbc.queryForList("SELECT 1 FROM promotion_order_applications WHERE tenant_id=? AND order_ref=?", tenant, req.orderRef());
        if (!prior.isEmpty()) return storedResult(tenant, req.orderRef());
        Computed c = compute(tenant, req);
        if (c.code() != null && c.couponId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, String.valueOf(c.response().getOrDefault("couponMessage", "Coupon is not valid.")));
        }
        // Reserve the order first: the primary key serializes duplicate redemptions of the same order.
        Map<String, Object> response = new LinkedHashMap<>(c.response());
        response.put("orderRef", req.orderRef());
        String json;
        try { json = mapper.writeValueAsString(response); } catch (Exception e) { throw new IllegalStateException(e); }
        jdbc.update("INSERT INTO promotion_order_applications(tenant_id, order_ref, customer_id, currency, subtotal, discount_total, total_amount, result) VALUES (?,?,?,?,?,?,?,?)",
                tenant, req.orderRef(), c.customerId(), c.currency(), c.result().subtotal(), c.result().discount(), c.result().total(), json);

        for (Applied a : c.result().applied()) {
            int ok = jdbc.update("UPDATE promotion_campaigns SET usage_count=usage_count+1 WHERE id=? AND tenant_id=? AND status='PUBLISHED' "
                    + "AND (usage_limit_total IS NULL OR usage_count<usage_limit_total)", a.campaignId(), tenant);
            if (ok == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Promotion '" + a.name() + "' is no longer available; recalculate the order.");
        }
        if (c.couponId() != null) {
            int ok = jdbc.update("UPDATE coupon_codes SET redeemed_count=redeemed_count+1, version=version+1 WHERE id=? AND tenant_id=? AND status='ACTIVE' "
                    + "AND (usage_limit IS NULL OR redeemed_count<usage_limit) AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP)", c.couponId(), tenant);
            if (ok == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon is no longer available.");
            // The row is now locked by this transaction, so these per-customer counts cannot race with another redemption.
            Map<String, Object> cp = jdbc.queryForMap("SELECT per_customer_limit, campaign_id FROM coupon_codes WHERE id=? AND tenant_id=?", c.couponId(), tenant);
            Integer per = (Integer) cp.get("per_customer_limit");
            if (per != null && countFor(tenant, "coupon_id", c.couponId(), c.customerId()) >= per) throw new ResponseStatusException(HttpStatus.CONFLICT, "Per-customer coupon limit reached.");
            Integer campaignPer = jdbc.queryForObject("SELECT usage_limit_per_customer FROM promotion_campaigns WHERE id=? AND tenant_id=?", Integer.class, c.couponCampaignId(), tenant);
            if (campaignPer != null && countFor(tenant, "campaign_id", c.couponCampaignId(), c.customerId()) >= campaignPer) throw new ResponseStatusException(HttpStatus.CONFLICT, "Per-customer promotion limit reached.");
            BigDecimal amount = c.result().applied().stream().filter(a -> a.campaignId() == c.couponCampaignId()).map(Applied::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            jdbc.update("INSERT INTO coupon_redemptions(tenant_id, coupon_id, campaign_id, customer_id, order_ref, discount_amount, currency) VALUES (?,?,?,?,?,?,?)",
                    tenant, c.couponId(), c.couponCampaignId(), c.customerId(), req.orderRef(), amount, c.currency());
        }
        jdbc.update("INSERT INTO promotion_audit_logs(tenant_id, entity_type, entity_id, action, actor_id, actor_email, detail) VALUES (?,?,?,?,?,?,?)",
                tenant, "ORDER", req.orderRef(), "REDEEM", 0, "checkout@system", "discount " + c.result().discount() + " " + c.currency());
        return response;
    }

    /** Called when an order is cancelled before fulfilment: releases coupon and campaign usage exactly once. */
    public Map<String, Object> reverse(UUID tenant, String orderRef) {
        return tx.execute(status -> {
            List<Map<String, Object>> apps = jdbc.queryForList("SELECT * FROM promotion_order_applications WHERE tenant_id=? AND order_ref=? FOR UPDATE", tenant, orderRef);
            if (apps.isEmpty()) throw notFound("Promotion application for order " + orderRef);
            if ("REVERSED".equals(apps.get(0).get("status"))) return Map.<String, Object>of("orderRef", orderRef, "reversed", false, "message", "already reversed");
            for (Map<String, Object> r : jdbc.queryForList("SELECT * FROM coupon_redemptions WHERE tenant_id=? AND order_ref=? AND status='REDEEMED'", tenant, orderRef)) {
                jdbc.update("UPDATE coupon_redemptions SET status='REVERSED', reversed_at=CURRENT_TIMESTAMP WHERE id=?", r.get("id"));
                jdbc.update("UPDATE coupon_codes SET redeemed_count=redeemed_count-1 WHERE id=? AND tenant_id=? AND redeemed_count>0", r.get("coupon_id"), tenant);
            }
            try {
                @SuppressWarnings("unchecked") Map<String, Object> saved = mapper.readValue((String) apps.get(0).get("result"), Map.class);
                @SuppressWarnings("unchecked") List<Map<String, Object>> applied = (List<Map<String, Object>>) saved.get("applied");
                for (Map<String, Object> a : applied) jdbc.update("UPDATE promotion_campaigns SET usage_count=usage_count-1 WHERE id=? AND tenant_id=? AND usage_count>0",
                        ((Number) a.get("campaignId")).longValue(), tenant);
            } catch (Exception e) { throw new IllegalStateException(e); }
            jdbc.update("UPDATE promotion_order_applications SET status='REVERSED', reversed_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND order_ref=?", tenant, orderRef);
            return Map.<String, Object>of("orderRef", orderRef, "reversed", true);
        });
    }

    private static BigDecimal asDecimal(Object v) { return v == null ? null : new BigDecimal(v.toString()); }
    private static OffsetDateTime asOffset(Object v) {
        if (v == null) return null;
        if (v instanceof OffsetDateTime o) return o;
        if (v instanceof java.sql.Timestamp t) return t.toInstant().atOffset(java.time.ZoneOffset.UTC);
        throw new IllegalStateException("Unexpected timestamp type " + v.getClass());
    }
}
