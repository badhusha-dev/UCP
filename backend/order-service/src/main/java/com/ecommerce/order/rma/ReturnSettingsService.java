package com.ecommerce.order.rma;

import com.ecommerce.order.admin.AdminSupport;
import com.ecommerce.order.admin.security.AdminPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.bad;

/** Refund and exception thresholds per tenant. Validated numbers only. */
@Service
public class ReturnSettingsService {
    public record Settings(BigDecimal highValueRefundThreshold, BigDecimal refundApprovalThreshold, int repeatReturnCount, int repeatReturnDays, int pickupFailureLimit,
                           int lostShipmentDays, int reviewSlaHours, int refundSlaHours) {}

    private final JdbcTemplate jdbc;
    private final AdminSupport support;

    public ReturnSettingsService(JdbcTemplate jdbc, AdminSupport support) { this.jdbc = jdbc; this.support = support; }

    public Settings get(UUID tenant) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM return_settings WHERE tenant_id=?", tenant);
        if (rows.isEmpty()) return new Settings(new BigDecimal("500.00"), new BigDecimal("100.00"), 3, 30, 2, 14, 48, 72);
        Map<String, Object> r = rows.get(0);
        return new Settings((BigDecimal) r.get("high_value_refund_threshold"), (BigDecimal) r.get("refund_approval_threshold"), i(r, "repeat_return_count"), i(r, "repeat_return_days"),
                i(r, "pickup_failure_limit"), i(r, "lost_shipment_days"), i(r, "review_sla_hours"), i(r, "refund_sla_hours"));
    }

    private static int i(Map<String, Object> r, String k) { return ((Number) r.get(k)).intValue(); }

    public Map<String, Object> view(UUID tenant) {
        Settings s = get(tenant);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("highValueRefundThreshold", s.highValueRefundThreshold()); m.put("refundApprovalThreshold", s.refundApprovalThreshold()); m.put("repeatReturnCount", s.repeatReturnCount());
        m.put("repeatReturnDays", s.repeatReturnDays()); m.put("pickupFailureLimit", s.pickupFailureLimit()); m.put("lostShipmentDays", s.lostShipmentDays());
        m.put("reviewSlaHours", s.reviewSlaHours()); m.put("refundSlaHours", s.refundSlaHours());
        return m;
    }

    @Transactional
    public Map<String, Object> upsert(AdminPrincipal actor, Map<String, Object> body) {
        Map<String, Object> cur = view(actor.tenantId());
        BigDecimal high = money(body.getOrDefault("highValueRefundThreshold", cur.get("highValueRefundThreshold")), "highValueRefundThreshold", true);
        BigDecimal approval = money(body.getOrDefault("refundApprovalThreshold", cur.get("refundApprovalThreshold")), "refundApprovalThreshold", false);
        int repeat = num(body.getOrDefault("repeatReturnCount", cur.get("repeatReturnCount")), "repeatReturnCount", 2, 100);
        int days = num(body.getOrDefault("repeatReturnDays", cur.get("repeatReturnDays")), "repeatReturnDays", 1, 365);
        int pf = num(body.getOrDefault("pickupFailureLimit", cur.get("pickupFailureLimit")), "pickupFailureLimit", 1, 20);
        int lost = num(body.getOrDefault("lostShipmentDays", cur.get("lostShipmentDays")), "lostShipmentDays", 1, 365);
        int review = num(body.getOrDefault("reviewSlaHours", cur.get("reviewSlaHours")), "reviewSlaHours", 1, 2000);
        int refund = num(body.getOrDefault("refundSlaHours", cur.get("refundSlaHours")), "refundSlaHours", 1, 2000);
        int u = jdbc.update("UPDATE return_settings SET high_value_refund_threshold=?, refund_approval_threshold=?, repeat_return_count=?, repeat_return_days=?, pickup_failure_limit=?, lost_shipment_days=?, review_sla_hours=?, refund_sla_hours=?, version=version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE tenant_id=?",
                high, approval, repeat, days, pf, lost, review, refund, actor.userId(), actor.tenantId());
        if (u == 0) jdbc.update("INSERT INTO return_settings(tenant_id, high_value_refund_threshold, refund_approval_threshold, repeat_return_count, repeat_return_days, pickup_failure_limit, lost_shipment_days, review_sla_hours, refund_sla_hours, updated_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                actor.tenantId(), high, approval, repeat, days, pf, lost, review, refund, actor.userId());
        jdbc.update("INSERT INTO return_audit_logs(tenant_id, entity_type, entity_id, action, actor_id, actor_email, detail) VALUES (?,?,?,?,?,?,?)", actor.tenantId(), "SETTINGS", "refund-config", "UPDATE", actor.userId(), actor.email(), cur + " -> " + view(actor.tenantId()));
        return view(actor.tenantId());
    }

    private static BigDecimal money(Object v, String f, boolean positive) {
        try {
            BigDecimal b = new BigDecimal(String.valueOf(v));
            if (b.signum() < 0 || (positive && b.signum() == 0) || b.compareTo(new BigDecimal("1000000000")) > 0) throw bad(f + " is out of range.");
            return b.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) { throw bad(f + " must be a number."); }
    }

    private static int num(Object v, String f, int min, int max) {
        try { int n = Integer.parseInt(String.valueOf(v)); if (n < min || n > max) throw bad(f + " must be between " + min + " and " + max + "."); return n; }
        catch (NumberFormatException e) { throw bad(f + " must be a whole number."); }
    }
}
