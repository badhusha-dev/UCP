package com.ecommerce.order.admin;

import com.ecommerce.order.admin.security.AdminPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.bad;

/** Per-tenant (optionally per-store) SLA thresholds and workflow switches. Validated numbers/booleans only. */
@Service
public class OrderSettingsService {
    public record Settings(SlaCalculator.Thresholds sla, boolean requirePaymentBeforeProcessing, int maxBulkSize) {}

    private static final List<String> KEYS = List.of("confirmSlaMinutes", "processingSlaMinutes", "fulfillmentSlaMinutes", "deliverySlaMinutes", "refundSlaMinutes", "restaurantAcceptSlaMinutes");

    private final JdbcTemplate jdbc;
    private final AdminSupport support;

    public OrderSettingsService(JdbcTemplate jdbc, AdminSupport support) { this.jdbc = jdbc; this.support = support; }

    public Settings effective(UUID tenant, String store) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM order_settings WHERE tenant_id=? AND store_id IN (?, '*') ORDER BY CASE WHEN store_id='*' THEN 1 ELSE 0 END", tenant, store == null ? "*" : store);
        if (rows.isEmpty()) return new Settings(SlaCalculator.Thresholds.defaults(), false, 200);
        Map<String, Object> r = rows.get(0);
        return new Settings(new SlaCalculator.Thresholds(i(r, "confirm_sla_minutes"), i(r, "processing_sla_minutes"), i(r, "fulfillment_sla_minutes"),
                i(r, "delivery_sla_minutes"), i(r, "refund_sla_minutes"), i(r, "restaurant_accept_sla_minutes")),
                Boolean.TRUE.equals(r.get("require_payment_before_processing")), i(r, "max_bulk_size"));
    }

    private static int i(Map<String, Object> r, String k) { return ((Number) r.get(k)).intValue(); }

    public Map<String, Object> view(UUID tenant, String store) {
        Settings s = effective(tenant, store);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("storeId", store == null ? "*" : store);
        out.put("confirmSlaMinutes", s.sla().confirmMinutes()); out.put("processingSlaMinutes", s.sla().processingMinutes());
        out.put("fulfillmentSlaMinutes", s.sla().fulfillmentMinutes()); out.put("deliverySlaMinutes", s.sla().deliveryMinutes());
        out.put("refundSlaMinutes", s.sla().refundMinutes()); out.put("restaurantAcceptSlaMinutes", s.sla().restaurantAcceptMinutes());
        out.put("requirePaymentBeforeProcessing", s.requirePaymentBeforeProcessing()); out.put("maxBulkSize", s.maxBulkSize());
        return out;
    }

    @Transactional
    public Map<String, Object> upsert(AdminPrincipal actor, String store, Map<String, Object> body) {
        String scope = store == null || store.isBlank() ? "*" : store.trim();
        Map<String, Object> cur = view(actor.tenantId(), scope);
        Map<String, Object> next = new LinkedHashMap<>(cur);
        for (String k : KEYS) if (body.containsKey(k)) next.put(k, minutes(body.get(k), k));
        if (body.containsKey("requirePaymentBeforeProcessing")) next.put("requirePaymentBeforeProcessing", Boolean.TRUE.equals(body.get("requirePaymentBeforeProcessing")));
        if (body.containsKey("maxBulkSize")) {
            int n = minutes(body.get("maxBulkSize"), "maxBulkSize");
            if (n > 1000) throw bad("maxBulkSize must be 1-1000.");
            next.put("maxBulkSize", n);
        }
        int updated = jdbc.update("UPDATE order_settings SET confirm_sla_minutes=?, processing_sla_minutes=?, fulfillment_sla_minutes=?, delivery_sla_minutes=?, refund_sla_minutes=?, restaurant_accept_sla_minutes=?, "
                        + "require_payment_before_processing=?, max_bulk_size=?, version=version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND store_id=?",
                next.get("confirmSlaMinutes"), next.get("processingSlaMinutes"), next.get("fulfillmentSlaMinutes"), next.get("deliverySlaMinutes"), next.get("refundSlaMinutes"),
                next.get("restaurantAcceptSlaMinutes"), next.get("requirePaymentBeforeProcessing"), next.get("maxBulkSize"), actor.userId(), actor.tenantId(), scope);
        if (updated == 0) jdbc.update("INSERT INTO order_settings(tenant_id, store_id, confirm_sla_minutes, processing_sla_minutes, fulfillment_sla_minutes, delivery_sla_minutes, refund_sla_minutes, restaurant_accept_sla_minutes, require_payment_before_processing, max_bulk_size, updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                actor.tenantId(), scope, next.get("confirmSlaMinutes"), next.get("processingSlaMinutes"), next.get("fulfillmentSlaMinutes"), next.get("deliverySlaMinutes"), next.get("refundSlaMinutes"),
                next.get("restaurantAcceptSlaMinutes"), next.get("requirePaymentBeforeProcessing"), next.get("maxBulkSize"), actor.userId());
        return view(actor.tenantId(), scope);
    }

    private static int minutes(Object v, String name) {
        try {
            int n = Integer.parseInt(String.valueOf(v));
            if (n < 1 || n > 100000) throw bad(name + " must be between 1 and 100000.");
            return n;
        } catch (NumberFormatException e) { throw bad(name + " must be a whole number."); }
    }
}
