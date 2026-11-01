package com.ecommerce.product.pricing;

import com.ecommerce.product.pricing.DiscountEngine.Campaign;
import com.ecommerce.product.pricing.DiscountEngine.Condition;
import com.ecommerce.product.pricing.DiscountEngine.Reward;
import com.ecommerce.product.pricing.DiscountEngine.Rule;
import com.ecommerce.product.security.CatalogPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static com.ecommerce.product.pricing.PricingSupport.*;

/** Promotion campaign lifecycle: DRAFT -> PENDING_APPROVAL -> APPROVED -> PUBLISHED (+ PAUSED / CANCELLED / REJECTED). */
@Service
public class CampaignService {
    public record RuleDto(String role, String effect, String targetType, Long targetId) {}
    public record ConditionDto(String type, BigDecimal numericValue, String textValue) {}
    public record RewardDto(String type, BigDecimal percent, BigDecimal amount, Integer buyQuantity, Integer getQuantity, Integer minQuantity) {}
    public record CampaignRequest(String name, String description, String promotionType, String scope, Boolean automatic, Integer priority,
                                  Boolean stackable, String currency, String storeId, OffsetDateTime startsAt, OffsetDateTime endsAt,
                                  BigDecimal minOrderValue, BigDecimal maxDiscountAmount, Integer usageLimitTotal,
                                  Integer usageLimitPerCustomer, String customerSegment, Boolean firstOrderOnly,
                                  List<RuleDto> rules, List<ConditionDto> conditions, List<RewardDto> rewards) {}

    private static final Map<String, String> REWARD_FOR_TYPE = Map.of("PERCENTAGE", "PERCENT_OFF", "FIXED_AMOUNT", "AMOUNT_OFF",
            "BUY_X_GET_Y", "BUY_X_GET_Y", "BUNDLE", "BUNDLE_PRICE", "QUANTITY_TIER", "TIER_PERCENT");

    private final JdbcTemplate jdbc;
    private final PricingSupport support;
    private final PricingSettingsService settingsService;

    public CampaignService(JdbcTemplate jdbc, PricingSupport support, PricingSettingsService settingsService) {
        this.jdbc = jdbc; this.support = support; this.settingsService = settingsService;
    }

    // ---- reads -------------------------------------------------------------------------------------------------

    public List<Map<String, Object>> list(CatalogPrincipal actor, String type, String status, Boolean automatic, Boolean segmentOnly) {
        StringBuilder sql = new StringBuilder("SELECT * FROM promotion_campaigns WHERE tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(actor.tenantId()));
        if (type != null && !type.isBlank()) { sql.append(" AND promotion_type=?"); args.add(type); }
        if (status != null && !status.isBlank()) { sql.append(" AND status=?"); args.add(status); }
        if (automatic != null) { sql.append(" AND automatic=?"); args.add(automatic); }
        if (Boolean.TRUE.equals(segmentOnly)) sql.append(" AND customer_segment IS NOT NULL");
        sql.append(" ORDER BY starts_at DESC, id DESC LIMIT 500");
        return support.rows(sql.toString(), args.toArray());
    }

    public Map<String, Object> get(CatalogPrincipal actor, long id) {
        Map<String, Object> c = support.one("SELECT * FROM promotion_campaigns WHERE id=? AND tenant_id=?", "Campaign", id, actor.tenantId());
        c.put("rules", support.rows("SELECT * FROM promotion_rules WHERE campaign_id=? AND tenant_id=? ORDER BY id", id, actor.tenantId()));
        c.put("conditions", support.rows("SELECT * FROM promotion_conditions WHERE campaign_id=? AND tenant_id=? ORDER BY id", id, actor.tenantId()));
        c.put("rewards", support.rows("SELECT * FROM promotion_rewards WHERE campaign_id=? AND tenant_id=? ORDER BY id", id, actor.tenantId()));
        return c;
    }

    // ---- create / update ---------------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> create(CatalogPrincipal actor, CampaignRequest r) {
        long id = insert(actor, validate(actor, r));
        support.audit(actor, "CAMPAIGN", id, "CREATE", r.name());
        return get(actor, id);
    }

    @Transactional
    public Map<String, Object> update(CatalogPrincipal actor, long id, CampaignRequest r) {
        Map<String, Object> existing = lock(actor, id);
        if (!Set.of("DRAFT", "REJECTED").contains(existing.get("status"))) throw conflict("Only DRAFT or REJECTED campaigns can be edited; pause or cancel the campaign instead.");
        Validated v = validate(actor, r);
        jdbc.update("UPDATE promotion_campaigns SET store_id=?, name=?, description=?, promotion_type=?, scope=?, automatic=?, priority=?, stackable=?, currency=?, "
                        + "starts_at=?, ends_at=?, min_order_value=?, max_discount_amount=?, usage_limit_total=?, usage_limit_per_customer=?, customer_segment=?, "
                        + "first_order_only=?, status='DRAFT', version=version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                v.store, v.name, v.description, v.type, v.scope, v.automatic, v.priority, v.stackable, v.currency, v.starts, v.ends, v.minOrder, v.maxDiscount,
                v.limitTotal, v.limitPerCustomer, v.segment, v.firstOrder, actor.userId(), id, actor.tenantId());
        for (String t : List.of("promotion_rules", "promotion_conditions", "promotion_rewards"))
            jdbc.update("DELETE FROM " + t + " WHERE campaign_id=? AND tenant_id=?", id, actor.tenantId());
        children(actor, id, v);
        support.audit(actor, "CAMPAIGN", id, "UPDATE", r.name());
        return get(actor, id);
    }

    private record Validated(String store, String name, String description, String type, String scope, boolean automatic, int priority,
                             boolean stackable, String currency, OffsetDateTime starts, OffsetDateTime ends, BigDecimal minOrder,
                             BigDecimal maxDiscount, Integer limitTotal, Integer limitPerCustomer, String segment, boolean firstOrder,
                             List<RuleDto> rules, List<ConditionDto> conditions, List<RewardDto> rewards) {}

    private Validated validate(CatalogPrincipal actor, CampaignRequest r) {
        String store = storeId(r.storeId());
        PricingSettingsService.Settings s = settingsService.effective(actor.tenantId(), store);
        String name = text(r.name(), "name", 160);
        if (r.description() != null && r.description().length() > 500) throw bad("description must be at most 500 characters.");
        String type = r.promotionType() == null ? "" : r.promotionType();
        if (!s.supportedTypes().contains(type)) throw bad("promotionType must be one of " + s.supportedTypes() + ".");
        String scope = r.scope() == null ? "PRODUCT" : r.scope();
        if (!Set.of("PRODUCT", "CATEGORY", "STORE_WIDE").contains(scope)) throw bad("scope must be PRODUCT, CATEGORY or STORE_WIDE.");
        String currency = currency(r.currency() == null ? s.defaultCurrency() : r.currency(), s.supportedCurrencies());
        OffsetDateTime starts = required(r.startsAt(), "startsAt");
        OffsetDateTime ends = r.endsAt() != null ? r.endsAt() : starts.plusDays(s.defaultCampaignDays());
        if (!ends.isAfter(starts)) throw bad("endsAt must be after startsAt.");
        if (!ends.isAfter(OffsetDateTime.now())) throw bad("endsAt must be in the future.");
        int priority = r.priority() == null ? 100 : r.priority();
        if (priority < 0 || priority > 10000) throw bad("priority must be 0-10000.");
        if (r.minOrderValue() != null && r.minOrderValue().signum() < 0) throw bad("minOrderValue cannot be negative.");
        if (r.maxDiscountAmount() != null && r.maxDiscountAmount().signum() <= 0) throw bad("maxDiscountAmount must be positive.");
        if (r.usageLimitTotal() != null && r.usageLimitTotal() <= 0) throw bad("usageLimitTotal must be positive.");
        if (r.usageLimitPerCustomer() != null && r.usageLimitPerCustomer() <= 0) throw bad("usageLimitPerCustomer must be positive.");
        if (r.usageLimitPerCustomer() != null && Boolean.TRUE.equals(r.automatic())) throw bad("Per-customer limits apply to coupon campaigns only (automatic campaigns have no redemption record per customer).");
        if (r.usageLimitPerCustomer() != null && r.usageLimitPerCustomer() > s.couponPerCustomerMax()) throw bad("usageLimitPerCustomer exceeds the tenant maximum of " + s.couponPerCustomerMax() + ".");
        if (r.customerSegment() != null && !r.customerSegment().isBlank() && !r.customerSegment().matches("[A-Za-z0-9_\\-]{1,64}")) throw bad("customerSegment may only contain letters, digits, '_' and '-'.");

        List<RuleDto> rules = r.rules() == null ? List.of() : r.rules();
        List<RewardDto> rewards = r.rewards() == null ? List.of() : r.rewards();
        List<ConditionDto> conditions = r.conditions() == null ? List.of() : r.conditions();
        if (rewards.isEmpty()) throw bad("At least one reward is required.");
        for (RuleDto rule : rules) {
            if (!Set.of("TRIGGER", "REWARD").contains(rule.role() == null ? "TRIGGER" : rule.role())) throw bad("rule role must be TRIGGER or REWARD.");
            if (!Set.of("INCLUDE", "EXCLUDE").contains(rule.effect() == null ? "INCLUDE" : rule.effect())) throw bad("rule effect must be INCLUDE or EXCLUDE.");
            if (!Set.of("PRODUCT", "CATEGORY", "ALL").contains(rule.targetType())) throw bad("rule targetType must be PRODUCT, CATEGORY or ALL.");
            if (!"ALL".equals(rule.targetType())) {
                if (rule.targetId() == null || rule.targetId() <= 0) throw bad("rule targetId is required.");
                if ("PRODUCT".equals(rule.targetType())) {
                    Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id=? AND tenant_id=?", Integer.class, rule.targetId(), actor.tenantId());
                    if (owned == null || owned == 0) throw bad("Product " + rule.targetId() + " does not exist in this tenant.");
                }
            }
        }
        boolean hasInclude = rules.stream().anyMatch(x -> (x.effect() == null || x.effect().equals("INCLUDE")) && (x.role() == null || x.role().equals("TRIGGER")));
        if (scope.equals("PRODUCT") && rules.stream().noneMatch(x -> "PRODUCT".equals(x.targetType()) && !"EXCLUDE".equals(x.effect()))) throw bad("PRODUCT scope needs at least one included product rule.");
        if (scope.equals("CATEGORY") && rules.stream().noneMatch(x -> "CATEGORY".equals(x.targetType()) && !"EXCLUDE".equals(x.effect()))) throw bad("CATEGORY scope needs at least one included category rule.");
        if (scope.equals("STORE_WIDE") && hasInclude && rules.stream().anyMatch(x -> !"ALL".equals(x.targetType()) && !"EXCLUDE".equals(x.effect()))) throw bad("STORE_WIDE scope cannot include specific targets (use EXCLUDE rules).");

        String expectedReward = REWARD_FOR_TYPE.get(type);
        for (RewardDto w : rewards) {
            if (!expectedReward.equals(w.type())) throw bad(type + " campaigns need " + expectedReward + " rewards.");
            switch (w.type()) {
                case "PERCENT_OFF", "TIER_PERCENT" -> {
                    if (w.percent() == null || w.percent().signum() <= 0 || w.percent().compareTo(BigDecimal.valueOf(100)) > 0) throw bad("percent must be in (0, 100].");
                    if (w.percent().compareTo(s.maxDiscountPercent()) > 0) throw bad("percent exceeds the tenant maximum discount of " + s.maxDiscountPercent() + "%.");
                    if (w.type().equals("TIER_PERCENT") && (w.minQuantity() == null || w.minQuantity() < 1)) throw bad("minQuantity is required for tiers.");
                }
                case "AMOUNT_OFF" -> positive(w.amount(), "amount");
                case "BUY_X_GET_Y" -> {
                    if (w.buyQuantity() == null || w.buyQuantity() < 1 || w.getQuantity() == null || w.getQuantity() < 1) throw bad("buyQuantity and getQuantity must be at least 1.");
                    if (w.percent() != null && (w.percent().signum() <= 0 || w.percent().compareTo(BigDecimal.valueOf(100)) > 0)) throw bad("percent must be in (0, 100].");
                    if (w.percent() != null && w.percent().compareTo(s.maxDiscountPercent()) > 0 && s.maxDiscountPercent().compareTo(BigDecimal.valueOf(100)) < 0)
                        throw bad("percent exceeds the tenant maximum discount of " + s.maxDiscountPercent() + "%.");
                }
                case "BUNDLE_PRICE" -> {
                    positive(w.amount(), "amount (bundle price)");
                    if (rules.stream().filter(x -> "PRODUCT".equals(x.targetType()) && !"EXCLUDE".equals(x.effect())).count() < 2) throw bad("A bundle needs at least two products.");
                }
                default -> throw bad("Unsupported reward type.");
            }
        }
        for (ConditionDto c : conditions) {
            if (!Set.of("MIN_ORDER_VALUE", "MIN_QUANTITY", "CUSTOMER_SEGMENT", "FIRST_ORDER").contains(c.type())) throw bad("Unsupported condition type " + c.type() + ".");
            if (Set.of("MIN_ORDER_VALUE", "MIN_QUANTITY").contains(c.type()) && (c.numericValue() == null || c.numericValue().signum() <= 0)) throw bad(c.type() + " needs a positive numericValue.");
            if (c.type().equals("CUSTOMER_SEGMENT") && (c.textValue() == null || !c.textValue().matches("[A-Za-z0-9_\\-]{1,64}"))) throw bad("CUSTOMER_SEGMENT needs a valid textValue.");
        }
        return new Validated(store, name, r.description(), type, scope, Boolean.TRUE.equals(r.automatic()), priority, Boolean.TRUE.equals(r.stackable()),
                currency, starts, ends, r.minOrderValue(), r.maxDiscountAmount(), r.usageLimitTotal(), r.usageLimitPerCustomer(),
                r.customerSegment() == null || r.customerSegment().isBlank() ? null : r.customerSegment(), Boolean.TRUE.equals(r.firstOrderOnly()),
                rules, conditions, rewards);
    }

    private long insert(CatalogPrincipal actor, Validated v) {
        jdbc.update("INSERT INTO promotion_campaigns(tenant_id, store_id, name, description, promotion_type, scope, automatic, priority, stackable, currency, starts_at, ends_at, "
                        + "min_order_value, max_discount_amount, usage_limit_total, usage_limit_per_customer, customer_segment, first_order_only, created_by, updated_by) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                actor.tenantId(), v.store, v.name, v.description, v.type, v.scope, v.automatic, v.priority, v.stackable, v.currency, v.starts, v.ends, v.minOrder,
                v.maxDiscount, v.limitTotal, v.limitPerCustomer, v.segment, v.firstOrder, actor.userId(), actor.userId());
        long id = jdbc.queryForObject("SELECT MAX(id) FROM promotion_campaigns WHERE tenant_id=? AND created_by=?", Long.class, actor.tenantId(), actor.userId());
        children(actor, id, v);
        return id;
    }

    private void children(CatalogPrincipal actor, long id, Validated v) {
        Set<String> seen = new HashSet<>();
        for (RuleDto r : v.rules) {
            String role = r.role() == null ? "TRIGGER" : r.role(), effect = r.effect() == null ? "INCLUDE" : r.effect();
            long target = "ALL".equals(r.targetType()) ? 0 : r.targetId();
            if (!seen.add(role + effect + r.targetType() + target)) continue;
            jdbc.update("INSERT INTO promotion_rules(tenant_id, campaign_id, rule_role, effect, target_type, target_id) VALUES (?,?,?,?,?,?)", actor.tenantId(), id, role, effect, r.targetType(), target);
        }
        for (ConditionDto c : v.conditions) jdbc.update("INSERT INTO promotion_conditions(tenant_id, campaign_id, condition_type, numeric_value, text_value) VALUES (?,?,?,?,?)",
                actor.tenantId(), id, c.type(), c.numericValue(), c.textValue());
        for (RewardDto w : v.rewards) jdbc.update("INSERT INTO promotion_rewards(tenant_id, campaign_id, reward_type, percent_value, amount_value, buy_quantity, get_quantity, min_quantity) VALUES (?,?,?,?,?,?,?,?)",
                actor.tenantId(), id, w.type(), w.percent(), w.amount(), w.buyQuantity(), w.getQuantity(), w.minQuantity());
    }

    // ---- lifecycle ---------------------------------------------------------------------------------------------

    private Map<String, Object> lock(CatalogPrincipal actor, long id) {
        return support.one("SELECT * FROM promotion_campaigns WHERE id=? AND tenant_id=? FOR UPDATE", "Campaign", id, actor.tenantId());
    }

    private void setStatus(CatalogPrincipal actor, long id, String status, String action, String note) {
        jdbc.update("UPDATE promotion_campaigns SET status=?, version=version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", status, actor.userId(), id, actor.tenantId());
        support.audit(actor, "CAMPAIGN", id, action, note);
    }

    /** Campaigns within the tenant's approval threshold go straight to APPROVED; larger or amount-based ones need a human decision. */
    @Transactional
    public Map<String, Object> submit(CatalogPrincipal actor, long id) {
        Map<String, Object> c = lock(actor, id);
        if (!Set.of("DRAFT", "REJECTED").contains(c.get("status"))) throw conflict("Only DRAFT or REJECTED campaigns can be submitted.");
        PricingSettingsService.Settings s = settingsService.effective(actor.tenantId(), (String) c.get("storeId"));
        List<Map<String, Object>> rewards = support.rows("SELECT * FROM promotion_rewards WHERE campaign_id=? AND tenant_id=?", id, actor.tenantId());
        boolean needsApproval = rewards.stream().anyMatch(w -> w.get("percentValue") == null
                || ((BigDecimal) w.get("percentValue")).compareTo(s.approvalThresholdPercent()) > 0);
        if (needsApproval) {
            setStatus(actor, id, "PENDING_APPROVAL", "SUBMIT", "approval required");
            jdbc.update("INSERT INTO promotion_approvals(tenant_id, subject_type, subject_id, reason, requested_by) VALUES (?,?,?,?,?)",
                    actor.tenantId(), "CAMPAIGN", id, "Discount exceeds the approval threshold or is amount-based", actor.userId());
        } else {
            setStatus(actor, id, "APPROVED", "SUBMIT", "auto-approved within threshold");
            jdbc.update("INSERT INTO promotion_approvals(tenant_id, subject_type, subject_id, status, reason, requested_by, decided_by, decided_at, decision_note) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?)",
                    actor.tenantId(), "CAMPAIGN", id, "APPROVED", "Within approval threshold", actor.userId(), actor.userId(), "auto-approved");
        }
        return get(actor, id);
    }

    @Transactional
    public Map<String, Object> decide(CatalogPrincipal actor, long id, boolean approve, String note) {
        Map<String, Object> c = lock(actor, id);
        if (!"PENDING_APPROVAL".equals(c.get("status"))) throw conflict("Campaign is not awaiting approval.");
        if (((Number) c.get("createdBy")).longValue() == actor.userId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot approve your own campaign.");
        if (!approve && (note == null || note.isBlank())) throw bad("A reason is required to reject.");
        jdbc.update("UPDATE promotion_approvals SET status=?, decided_by=?, decided_at=CURRENT_TIMESTAMP, decision_note=? WHERE tenant_id=? AND subject_type='CAMPAIGN' AND subject_id=? AND status='PENDING'",
                approve ? "APPROVED" : "REJECTED", actor.userId(), note, actor.tenantId(), id);
        setStatus(actor, id, approve ? "APPROVED" : "REJECTED", approve ? "APPROVE" : "REJECT", note);
        return get(actor, id);
    }

    @Transactional
    public Map<String, Object> publish(CatalogPrincipal actor, long id) {
        Map<String, Object> c = lock(actor, id);
        if (!"APPROVED".equals(c.get("status"))) throw conflict("Only APPROVED campaigns can be published.");
        if (!((OffsetDateTime) toOffset(c.get("endsAt"))).isAfter(OffsetDateTime.now())) throw conflict("The campaign has already ended.");
        List<Map<String, Object>> clashes = conflicts(actor, id);
        if (!clashes.isEmpty()) throw conflict("Publishing would create an ambiguous overlap with campaign(s) " + clashes.stream().map(m -> m.get("id")).toList()
                + " (same priority, overlapping dates and targets, neither stackable).");
        setStatus(actor, id, "PUBLISHED", "PUBLISH", null);
        return get(actor, id);
    }

    @Transactional
    public Map<String, Object> pause(CatalogPrincipal actor, long id) {
        Map<String, Object> c = lock(actor, id);
        if (!"PUBLISHED".equals(c.get("status"))) throw conflict("Only PUBLISHED campaigns can be paused.");
        setStatus(actor, id, "PAUSED", "PAUSE", null);
        return get(actor, id);
    }

    @Transactional
    public Map<String, Object> resume(CatalogPrincipal actor, long id) {
        Map<String, Object> c = lock(actor, id);
        if (!"PAUSED".equals(c.get("status"))) throw conflict("Only PAUSED campaigns can be resumed.");
        if (!conflicts(actor, id).isEmpty()) throw conflict("Resuming would create an ambiguous overlap with another published campaign.");
        setStatus(actor, id, "PUBLISHED", "RESUME", null);
        return get(actor, id);
    }

    @Transactional
    public Map<String, Object> cancel(CatalogPrincipal actor, long id, String reason) {
        Map<String, Object> c = lock(actor, id);
        if (Set.of("CANCELLED").contains(c.get("status"))) throw conflict("Campaign is already cancelled.");
        jdbc.update("UPDATE promotion_approvals SET status='WITHDRAWN', decided_by=?, decided_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND subject_type='CAMPAIGN' AND subject_id=? AND status='PENDING'",
                actor.userId(), actor.tenantId(), id);
        // Coupons of a cancelled campaign stop working immediately; past redemptions and orders are untouched.
        jdbc.update("UPDATE coupon_codes SET status='REVOKED', version=version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE campaign_id=? AND tenant_id=? AND status<>'REVOKED'",
                actor.userId(), id, actor.tenantId());
        setStatus(actor, id, "CANCELLED", "CANCEL", reason);
        return get(actor, id);
    }

    // ---- conflicts / calendar / approvals / performance ------------------------------------------------------------

    /** Published or approved campaigns that would be ambiguous with this one: same priority, overlapping window and targets, neither stackable. */
    public List<Map<String, Object>> conflicts(CatalogPrincipal actor, long id) {
        Map<String, Object> me = support.one("SELECT * FROM promotion_campaigns WHERE id=? AND tenant_id=?", "Campaign", id, actor.tenantId());
        Set<String> myTargets = targets(actor.tenantId(), id);
        List<Map<String, Object>> clashes = new ArrayList<>();
        for (Map<String, Object> o : support.rows("SELECT * FROM promotion_campaigns WHERE tenant_id=? AND id<>? AND status IN ('PUBLISHED','APPROVED') "
                        + "AND starts_at < ? AND ends_at > ? AND priority=? AND stackable=FALSE AND (store_id=? OR store_id='*' OR ?='*')",
                actor.tenantId(), id, me.get("endsAt"), me.get("startsAt"), me.get("priority"), me.get("storeId"), me.get("storeId"))) {
            if (Boolean.TRUE.equals(me.get("stackable"))) continue;
            Set<String> theirs = targets(actor.tenantId(), ((Number) o.get("id")).longValue());
            if (overlap(myTargets, theirs)) clashes.add(o);
        }
        return clashes;
    }

    private Set<String> targets(UUID tenant, long campaignId) {
        Set<String> out = new HashSet<>();
        String scope = jdbc.queryForObject("SELECT scope FROM promotion_campaigns WHERE id=? AND tenant_id=?", String.class, campaignId, tenant);
        if ("STORE_WIDE".equals(scope)) out.add("ALL");
        jdbc.queryForList("SELECT target_type, target_id FROM promotion_rules WHERE campaign_id=? AND tenant_id=? AND effect='INCLUDE' AND rule_role='TRIGGER'", campaignId, tenant)
                .forEach(m -> out.add("ALL".equals(m.get("target_type")) ? "ALL" : m.get("target_type") + ":" + m.get("target_id")));
        return out;
    }

    private static boolean overlap(Set<String> a, Set<String> b) {
        if (a.contains("ALL") || b.contains("ALL")) return true;
        return a.stream().anyMatch(b::contains);
    }

    /** Calendar entries in a window, each flagged with the ids of campaigns it clashes with. */
    public List<Map<String, Object>> calendar(CatalogPrincipal actor, OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null || !to.isAfter(from)) throw bad("from and to are required and to must be after from.");
        if (java.time.Duration.between(from, to).toDays() > 400) throw bad("The calendar window is limited to 400 days.");
        List<Map<String, Object>> rows = support.rows("SELECT id, name, promotion_type, status, starts_at, ends_at, priority, stackable, automatic, store_id FROM promotion_campaigns "
                + "WHERE tenant_id=? AND status NOT IN ('CANCELLED','REJECTED') AND starts_at < ? AND ends_at > ? ORDER BY starts_at", actor.tenantId(), to, from);
        for (Map<String, Object> r : rows) {
            long id = ((Number) r.get("id")).longValue();
            List<Object> clash = new ArrayList<>();
            if (Set.of("PUBLISHED", "APPROVED").contains(r.get("status"))) conflicts(actor, id).forEach(m -> clash.add(m.get("id")));
            r.put("conflictsWith", clash);
        }
        return rows;
    }

    public List<Map<String, Object>> approvals(CatalogPrincipal actor, String status) {
        if (status == null || status.isBlank()) return support.rows("SELECT * FROM promotion_approvals WHERE tenant_id=? ORDER BY requested_at DESC, id DESC LIMIT 500", actor.tenantId());
        return support.rows("SELECT * FROM promotion_approvals WHERE tenant_id=? AND status=? ORDER BY requested_at DESC, id DESC LIMIT 500", actor.tenantId(), status);
    }

    public List<Map<String, Object>> audit(CatalogPrincipal actor, String entityType, String entityId) {
        StringBuilder sql = new StringBuilder("SELECT * FROM promotion_audit_logs WHERE tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(actor.tenantId()));
        if (entityType != null && !entityType.isBlank()) { sql.append(" AND entity_type=?"); args.add(entityType); }
        if (entityId != null && !entityId.isBlank()) { sql.append(" AND entity_id=?"); args.add(entityId); }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT 500");
        return support.rows(sql.toString(), args.toArray());
    }

    /** Real redemption numbers only; campaigns without redemptions report zeros. */
    public List<Map<String, Object>> performance(CatalogPrincipal actor) {
        return support.rows("SELECT c.id, c.name, c.promotion_type, c.status, c.usage_count, "
                + "COALESCE(SUM(CASE WHEN r.status='REDEEMED' THEN 1 ELSE 0 END),0) AS redemptions, "
                + "COALESCE(SUM(CASE WHEN r.status='REDEEMED' THEN r.discount_amount ELSE 0 END),0) AS discount_given, "
                + "COUNT(DISTINCT r.customer_id) AS unique_customers FROM promotion_campaigns c "
                + "LEFT JOIN coupon_redemptions r ON r.campaign_id=c.id AND r.tenant_id=c.tenant_id WHERE c.tenant_id=? "
                + "GROUP BY c.id, c.name, c.promotion_type, c.status, c.usage_count ORDER BY c.id DESC LIMIT 200", actor.tenantId());
    }

    // ---- engine loading ------------------------------------------------------------------------------------------

    /** Campaigns that are live right now for the store: PUBLISHED and inside their window. */
    public List<Campaign> liveEngineCampaigns(UUID tenant, String store, OffsetDateTime now, String currency, Collection<Long> alsoInclude) {
        List<Campaign> out = new ArrayList<>();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM promotion_campaigns WHERE tenant_id=? AND status='PUBLISHED' AND starts_at<=? AND ends_at>? "
                + "AND currency=? AND (store_id=? OR store_id='*') AND (usage_limit_total IS NULL OR usage_count < usage_limit_total)", tenant, now, now, currency, storeId(store));
        for (Map<String, Object> r : rows) {
            long id = ((Number) r.get("id")).longValue();
            if (Boolean.TRUE.equals(r.get("automatic")) || alsoInclude.contains(id)) out.add(toEngine(tenant, r, null));
        }
        return out;
    }

    public Campaign toEngine(UUID tenant, Map<String, Object> r, String couponCode) {
        long id = ((Number) r.get("id")).longValue();
        List<Rule> rules = jdbc.queryForList("SELECT * FROM promotion_rules WHERE campaign_id=? AND tenant_id=?", id, tenant).stream()
                .map(m -> new Rule((String) m.get("rule_role"), (String) m.get("effect"), (String) m.get("target_type"), ((Number) m.get("target_id")).longValue())).toList();
        List<Condition> conds = jdbc.queryForList("SELECT * FROM promotion_conditions WHERE campaign_id=? AND tenant_id=?", id, tenant).stream()
                .map(m -> new Condition((String) m.get("condition_type"), (BigDecimal) m.get("numeric_value"), (String) m.get("text_value"))).toList();
        List<Reward> rewards = jdbc.queryForList("SELECT * FROM promotion_rewards WHERE campaign_id=? AND tenant_id=?", id, tenant).stream()
                .map(m -> new Reward((String) m.get("reward_type"), (BigDecimal) m.get("percent_value"), (BigDecimal) m.get("amount_value"),
                        (Integer) m.get("buy_quantity"), (Integer) m.get("get_quantity"), (Integer) m.get("min_quantity"))).toList();
        return new Campaign(id, (String) r.get("name"), (String) r.get("promotion_type"), (String) r.get("scope"), ((Number) r.get("priority")).intValue(),
                Boolean.TRUE.equals(r.get("stackable")), (BigDecimal) r.get("min_order_value"), (BigDecimal) r.get("max_discount_amount"),
                (String) r.get("customer_segment"), Boolean.TRUE.equals(r.get("first_order_only")), couponCode, rules, conds, rewards);
    }

    private static OffsetDateTime toOffset(Object v) {
        if (v instanceof OffsetDateTime o) return o;
        if (v instanceof java.sql.Timestamp t) return t.toInstant().atOffset(java.time.ZoneOffset.UTC);
        throw new IllegalStateException("Unexpected timestamp type " + v);
    }
}
