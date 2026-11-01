package com.ecommerce.order.admin;

import com.ecommerce.order.admin.security.AdminPrincipal;
import com.ecommerce.order.repository.OrderReturnRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.*;

/** Saved filter views and the read-only returns/refunds window (returns are still processed by the existing Returns module). */
@Service
public class OrderMiscService {
    private static final Set<String> VIEW_KEYS = Set.of("q", "view", "statuses", "orderType", "storeIds", "vendorId", "paymentStatus", "fulfillmentStatus", "deliveryStatus",
            "from", "to", "minAmount", "maxAmount", "operatorId", "slaBreached", "sort", "columns");

    private final JdbcTemplate jdbc;
    private final AdminSupport support;
    private final ObjectMapper mapper;
    private final OrderReturnRepository returns;
    private final OrderAdminService admin;

    public OrderMiscService(JdbcTemplate jdbc, AdminSupport support, ObjectMapper mapper, OrderReturnRepository returns, OrderAdminService admin) {
        this.jdbc = jdbc; this.support = support; this.mapper = mapper; this.returns = returns; this.admin = admin;
    }

    public List<Map<String, Object>> views(AdminPrincipal actor) {
        List<Map<String, Object>> rows = support.rows("SELECT id, name, filters, created_at FROM order_saved_views WHERE tenant_id=? AND user_id=? ORDER BY name", actor.tenantId(), actor.userId());
        rows.forEach(r -> r.put("filters", parse((String) r.get("filters"))));
        return rows;
    }

    @Transactional
    public Map<String, Object> saveView(AdminPrincipal actor, String name, Map<String, Object> filters) {
        String n = ContentChecks.plain(name, "name", 80);
        if (filters == null || filters.size() > 20 || !VIEW_KEYS.containsAll(filters.keySet())) throw bad("A view may only contain the standard filter and column settings.");
        try {
            jdbc.update("INSERT INTO order_saved_views(tenant_id, user_id, name, filters) VALUES (?,?,?,?)", actor.tenantId(), actor.userId(), n, mapper.writeValueAsString(filters));
        } catch (DuplicateKeyException e) { throw conflict("You already have a view named '" + n + "'."); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw bad("The filters could not be stored."); }
        return Map.of("views", views(actor));
    }

    @Transactional
    public void deleteView(AdminPrincipal actor, long id) {
        if (jdbc.update("DELETE FROM order_saved_views WHERE id=? AND tenant_id=? AND user_id=?", id, actor.tenantId(), actor.userId()) == 0) throw notFound("View");
    }

    /** Return requests for the tenant with item quantities and refund state. Refund completion is only ever set by the Payment/Accounting callback. */
    @Transactional(readOnly = true)
    public Map<String, Object> returns(AdminPrincipal actor, String status, int page, int size) {
        if (size < 1 || size > 100 || page < 0) throw bad("page must be >= 0 and size 1-100.");
        StringBuilder where = new StringBuilder(" FROM order_return r JOIN orders o ON o.id=r.order_id WHERE o.tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(actor.tenantId()));
        if (status != null && !status.isBlank()) { where.append(" AND r.status=?"); args.add(status); }
        if (!actor.hasPermission("order:view-all-stores")) {
            if (actor.storeIds().isEmpty()) where.append(" AND 1=0");
            else { where.append(" AND o.store_id IN (").append(String.join(",", Collections.nCopies(actor.storeIds().size(), "?"))).append(")"); args.addAll(actor.storeIds()); }
        }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size); pageArgs.add((long) page * size);
        List<Long> ids = jdbc.queryForList("SELECT r.id" + where + " ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?", Long.class, pageArgs.toArray());
        List<Map<String, Object>> content = new ArrayList<>();
        for (var r : returns.findAllById(ids)) {
            Map<String, Object> m = admin.returnView(r);
            m.put("orderId", r.getOrderId()); m.put("orderNumber", orderNumber(r.getOrderId()));
            m.put("link", "/api/orders/returns/" + r.getId() + "/process");
            content.add(m);
        }
        content.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("createdAt"))).reversed());
        return Map.of("content", content, "totalElements", total, "page", page, "size", size);
    }

    private Map<String, Object> parse(String s) { try { return mapper.readValue(s, new TypeReference<Map<String, Object>>() {}); } catch (Exception e) { return Map.of(); } }
}
