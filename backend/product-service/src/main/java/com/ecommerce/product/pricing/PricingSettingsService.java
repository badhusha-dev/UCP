package com.ecommerce.product.pricing;

import com.ecommerce.product.security.CatalogPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static com.ecommerce.product.pricing.PricingSupport.bad;

/** Validated, DB-backed pricing policy per tenant (and optionally per store). Store rows override the '*' row. */
@Service
public class PricingSettingsService {
    public static final Set<String> TYPES = Set.of("PERCENTAGE", "FIXED_AMOUNT", "BUY_X_GET_Y", "BUNDLE", "QUANTITY_TIER");
    public static final Set<String> STACKING = Set.of("NO_STACKING", "FLAGGED_ONLY", "STACK_ALL");

    public record Settings(String defaultCurrency, Set<String> supportedCurrencies, BigDecimal maxDiscountPercent,
                           BigDecimal minMarginPercent, int defaultCampaignDays, int couponBulkMax, int couponPerCustomerMax,
                           BigDecimal approvalThresholdPercent, String stackingPolicy, Set<String> supportedTypes) {}

    private final JdbcTemplate jdbc;
    private final PricingSupport support;

    public PricingSettingsService(JdbcTemplate jdbc, PricingSupport support) { this.jdbc = jdbc; this.support = support; }

    @Transactional(readOnly = true)
    public Settings effective(UUID tenant, String store) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM pricing_settings WHERE tenant_id=? AND store_id IN (?, '*') ORDER BY CASE WHEN store_id='*' THEN 1 ELSE 0 END",
                tenant, PricingSupport.storeId(store));
        return rows.isEmpty() ? defaults() : map(rows.get(0));
    }

    public Map<String, Object> view(UUID tenant, String store) {
        Settings s = effective(tenant, store);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("storeId", PricingSupport.storeId(store));
        out.put("defaultCurrency", s.defaultCurrency());
        out.put("supportedCurrencies", new TreeSet<>(s.supportedCurrencies()));
        out.put("maxDiscountPercent", s.maxDiscountPercent());
        out.put("minMarginPercent", s.minMarginPercent());
        out.put("defaultCampaignDays", s.defaultCampaignDays());
        out.put("couponBulkMax", s.couponBulkMax());
        out.put("couponPerCustomerMax", s.couponPerCustomerMax());
        out.put("approvalThresholdPercent", s.approvalThresholdPercent());
        out.put("stackingPolicy", s.stackingPolicy());
        out.put("supportedTypes", new TreeSet<>(s.supportedTypes()));
        return out;
    }

    @Transactional
    public Map<String, Object> upsert(CatalogPrincipal actor, String store, Map<String, Object> body) {
        String storeId = PricingSupport.storeId(store);
        Settings base = effective(actor.tenantId(), storeId);
        String currency = body.containsKey("defaultCurrency") ? String.valueOf(body.get("defaultCurrency")).toUpperCase(Locale.ROOT) : base.defaultCurrency();
        Set<String> currencies = body.containsKey("supportedCurrencies") ? csv(body.get("supportedCurrencies")) : base.supportedCurrencies();
        currencies.forEach(c -> { if (!c.matches("[A-Z]{3}")) throw bad("Invalid currency " + c + "."); try { Currency.getInstance(c); } catch (IllegalArgumentException e) { throw bad("Unknown currency " + c + "."); } });
        if (!currencies.contains(currency)) throw bad("Default currency must be one of the supported currencies.");
        BigDecimal max = body.containsKey("maxDiscountPercent") ? num(body.get("maxDiscountPercent"), "maxDiscountPercent") : base.maxDiscountPercent();
        BigDecimal margin = body.containsKey("minMarginPercent") ? num(body.get("minMarginPercent"), "minMarginPercent") : base.minMarginPercent();
        BigDecimal threshold = body.containsKey("approvalThresholdPercent") ? num(body.get("approvalThresholdPercent"), "approvalThresholdPercent") : base.approvalThresholdPercent();
        int days = body.containsKey("defaultCampaignDays") ? (int) num(body.get("defaultCampaignDays"), "defaultCampaignDays").longValue() : base.defaultCampaignDays();
        int bulk = body.containsKey("couponBulkMax") ? (int) num(body.get("couponBulkMax"), "couponBulkMax").longValue() : base.couponBulkMax();
        int perCustomer = body.containsKey("couponPerCustomerMax") ? (int) num(body.get("couponPerCustomerMax"), "couponPerCustomerMax").longValue() : base.couponPerCustomerMax();
        String stacking = body.containsKey("stackingPolicy") ? String.valueOf(body.get("stackingPolicy")) : base.stackingPolicy();
        Set<String> types = body.containsKey("supportedTypes") ? csv(body.get("supportedTypes")) : base.supportedTypes();

        if (max.signum() <= 0 || max.compareTo(BigDecimal.valueOf(100)) > 0) throw bad("maxDiscountPercent must be in (0, 100].");
        if (margin.signum() < 0 || margin.compareTo(BigDecimal.valueOf(100)) >= 0) throw bad("minMarginPercent must be in [0, 100).");
        if (threshold.signum() < 0 || threshold.compareTo(BigDecimal.valueOf(100)) > 0) throw bad("approvalThresholdPercent must be in [0, 100].");
        if (days < 1 || days > 366) throw bad("defaultCampaignDays must be 1-366.");
        if (bulk < 1 || bulk > 10000) throw bad("couponBulkMax must be 1-10000.");
        if (perCustomer < 1 || perCustomer > 1000) throw bad("couponPerCustomerMax must be 1-1000.");
        if (!STACKING.contains(stacking)) throw bad("stackingPolicy must be one of " + STACKING + ".");
        if (types.isEmpty() || !TYPES.containsAll(types)) throw bad("supportedTypes must be a non-empty subset of " + TYPES + ".");

        Map<String, Object> before = view(actor.tenantId(), storeId);
        int updated = jdbc.update("UPDATE pricing_settings SET default_currency=?, supported_currencies=?, max_discount_percent=?, min_margin_percent=?, "
                        + "default_campaign_days=?, coupon_bulk_max=?, coupon_per_customer_max=?, approval_threshold_percent=?, stacking_policy=?, "
                        + "supported_types=?, version=version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND store_id=?",
                currency, String.join(",", new TreeSet<>(currencies)), max, margin, days, bulk, perCustomer, threshold, stacking,
                String.join(",", new TreeSet<>(types)), actor.userId(), actor.tenantId(), storeId);
        if (updated == 0) {
            jdbc.update("INSERT INTO pricing_settings(tenant_id, store_id, default_currency, supported_currencies, max_discount_percent, min_margin_percent, "
                            + "default_campaign_days, coupon_bulk_max, coupon_per_customer_max, approval_threshold_percent, stacking_policy, supported_types, created_by, updated_by) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    actor.tenantId(), storeId, currency, String.join(",", new TreeSet<>(currencies)), max, margin, days, bulk, perCustomer, threshold,
                    stacking, String.join(",", new TreeSet<>(types)), actor.userId(), actor.userId());
        }
        Map<String, Object> after = view(actor.tenantId(), storeId);
        support.audit(actor, "SETTINGS", storeId, "UPDATE", before + " -> " + after);
        return after;
    }

    private static Settings defaults() {
        return new Settings("USD", new TreeSet<>(List.of("USD", "EUR", "GBP", "INR")), new BigDecimal("50.00"), BigDecimal.ZERO, 14, 1000, 5,
                new BigDecimal("20.00"), "FLAGGED_ONLY", new TreeSet<>(TYPES));
    }

    private static Settings map(Map<String, Object> r) {
        return new Settings((String) r.get("default_currency"), csv(r.get("supported_currencies")), (BigDecimal) r.get("max_discount_percent"),
                (BigDecimal) r.get("min_margin_percent"), ((Number) r.get("default_campaign_days")).intValue(),
                ((Number) r.get("coupon_bulk_max")).intValue(), ((Number) r.get("coupon_per_customer_max")).intValue(),
                (BigDecimal) r.get("approval_threshold_percent"), (String) r.get("stacking_policy"), csv(r.get("supported_types")));
    }

    private static Set<String> csv(Object value) {
        Set<String> out = new TreeSet<>();
        if (value instanceof Collection<?> c) c.forEach(v -> out.add(String.valueOf(v).trim().toUpperCase(Locale.ROOT)));
        else if (value != null) for (String p : String.valueOf(value).split(",")) if (!p.isBlank()) out.add(p.trim().toUpperCase(Locale.ROOT));
        return out;
    }

    private static BigDecimal num(Object value, String field) {
        try { return new BigDecimal(String.valueOf(value)); } catch (NumberFormatException e) { throw bad(field + " must be a number."); }
    }
}
