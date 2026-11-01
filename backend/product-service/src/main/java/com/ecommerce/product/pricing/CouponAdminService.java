package com.ecommerce.product.pricing;

import com.ecommerce.product.security.CatalogPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

import static com.ecommerce.product.pricing.PricingSupport.*;

/** Coupon administration: single and bulk generation, lifecycle (pause / activate / revoke) and redemption history. */
@Service
public class CouponAdminService {
    public record CouponRequest(Long campaignId, String code, Integer count, String prefix, Integer usageLimit, Integer perCustomerLimit,
                                OffsetDateTime expiresAt, String restrictedCustomerId) {}

    // No 0/O/1/I so codes survive being read out loud.
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final PricingSupport support;
    private final PricingSettingsService settingsService;

    public CouponAdminService(JdbcTemplate jdbc, PricingSupport support, PricingSettingsService settingsService) {
        this.jdbc = jdbc; this.support = support; this.settingsService = settingsService;
    }

    @Transactional
    public List<Map<String, Object>> create(CatalogPrincipal actor, CouponRequest r) {
        if (r.campaignId() == null) throw bad("campaignId is required.");
        Map<String, Object> campaign = support.one("SELECT * FROM promotion_campaigns WHERE id=? AND tenant_id=?", "Campaign", r.campaignId(), actor.tenantId());
        if (Set.of("CANCELLED", "REJECTED").contains(campaign.get("status"))) throw conflict("Cannot add coupons to a " + campaign.get("status") + " campaign.");
        PricingSettingsService.Settings s = settingsService.effective(actor.tenantId(), (String) campaign.get("storeId"));
        if (r.usageLimit() != null && r.usageLimit() < 1) throw bad("usageLimit must be positive.");
        if (r.perCustomerLimit() != null && (r.perCustomerLimit() < 1 || r.perCustomerLimit() > s.couponPerCustomerMax()))
            throw bad("perCustomerLimit must be between 1 and " + s.couponPerCustomerMax() + ".");
        if (r.expiresAt() != null && !r.expiresAt().isAfter(OffsetDateTime.now())) throw bad("expiresAt must be in the future.");
        if (r.restrictedCustomerId() != null && r.restrictedCustomerId().length() > 64) throw bad("restrictedCustomerId is too long.");
        boolean bulk = r.count() != null && r.count() > 1;
        if (bulk && r.code() != null && !r.code().isBlank()) throw bad("Provide either a code or a count, not both.");
        int count = r.count() == null ? 1 : r.count();
        if (count < 1 || count > s.couponBulkMax()) throw bad("count must be between 1 and " + s.couponBulkMax() + ".");
        String prefix = r.prefix() == null ? "" : r.prefix().toUpperCase(Locale.ROOT);
        if (!prefix.matches("[A-Z0-9\\-]{0,12}")) throw bad("prefix may contain up to 12 letters, digits or '-'.");

        String batch = UUID.randomUUID().toString().substring(0, 8);
        List<String> codes = new ArrayList<>();
        if (r.code() != null && !r.code().isBlank()) {
            String code = r.code().trim().toUpperCase(Locale.ROOT);
            if (!code.matches("[A-Z0-9_\\-]{3,40}")) throw bad("code must be 3-40 characters: letters, digits, '_' or '-'.");
            codes.add(code);
        } else {
            Set<String> unique = new LinkedHashSet<>();
            while (unique.size() < count) unique.add(prefix + randomPart(10));
            codes.addAll(unique);
        }
        List<Map<String, Object>> created = new ArrayList<>();
        for (String code : codes) {
            try {
                jdbc.update("INSERT INTO coupon_codes(tenant_id, campaign_id, code, usage_limit, per_customer_limit, expires_at, restricted_customer_id, batch_id, created_by, updated_by) "
                                + "VALUES (?,?,?,?,?,?,?,?,?,?)", actor.tenantId(), r.campaignId(), code, r.usageLimit(),
                        r.perCustomerLimit(), r.expiresAt(), r.restrictedCustomerId(), batch, actor.userId(), actor.userId());
            } catch (DuplicateKeyException e) {
                throw conflict("Coupon code " + code + " already exists for this tenant.");
            }
        }
        for (String code : codes) created.add(support.one("SELECT * FROM coupon_codes WHERE tenant_id=? AND code=?", "Coupon", actor.tenantId(), code));
        support.audit(actor, "COUPON", batch, bulk ? "BULK_CREATE" : "CREATE", codes.size() + " code(s) for campaign " + r.campaignId());
        return created;
    }

    private static String randomPart(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    public List<Map<String, Object>> list(CatalogPrincipal actor, Long campaignId, String status, String q) {
        StringBuilder sql = new StringBuilder("SELECT * FROM coupon_codes WHERE tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(actor.tenantId()));
        if (campaignId != null) { sql.append(" AND campaign_id=?"); args.add(campaignId); }
        if (status != null && !status.isBlank()) { sql.append(" AND status=?"); args.add(status); }
        if (q != null && !q.isBlank()) { sql.append(" AND code LIKE ?"); args.add(q.trim().toUpperCase(Locale.ROOT).replace("%", "").replace("_", "") + "%"); }
        sql.append(" ORDER BY id DESC LIMIT 500");
        return support.rows(sql.toString(), args.toArray());
    }

    @Transactional
    public Map<String, Object> transition(CatalogPrincipal actor, long id, String action) {
        Map<String, Object> c = support.one("SELECT * FROM coupon_codes WHERE id=? AND tenant_id=? FOR UPDATE", "Coupon", id, actor.tenantId());
        String current = (String) c.get("status");
        String next = switch (action) {
            case "pause" -> { if (!current.equals("ACTIVE")) throw conflict("Only ACTIVE coupons can be paused."); yield "PAUSED"; }
            case "activate" -> { if (!current.equals("PAUSED")) throw conflict("Only PAUSED coupons can be activated."); yield "ACTIVE"; }
            case "revoke" -> { if (current.equals("REVOKED")) throw conflict("Coupon is already revoked."); yield "REVOKED"; }
            default -> throw bad("Unknown action " + action + ".");
        };
        jdbc.update("UPDATE coupon_codes SET status=?, version=version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", next, actor.userId(), id, actor.tenantId());
        support.audit(actor, "COUPON", id, action.toUpperCase(Locale.ROOT), current + " -> " + next);
        return support.one("SELECT * FROM coupon_codes WHERE id=? AND tenant_id=?", "Coupon", id, actor.tenantId());
    }

    public List<Map<String, Object>> redemptions(CatalogPrincipal actor, Long couponId, Long campaignId) {
        StringBuilder sql = new StringBuilder("SELECT r.*, c.code FROM coupon_redemptions r JOIN coupon_codes c ON c.id=r.coupon_id WHERE r.tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(actor.tenantId()));
        if (couponId != null) { sql.append(" AND r.coupon_id=?"); args.add(couponId); }
        if (campaignId != null) { sql.append(" AND r.campaign_id=?"); args.add(campaignId); }
        sql.append(" ORDER BY r.redeemed_at DESC, r.id DESC LIMIT 500");
        return support.rows(sql.toString(), args.toArray());
    }
}
