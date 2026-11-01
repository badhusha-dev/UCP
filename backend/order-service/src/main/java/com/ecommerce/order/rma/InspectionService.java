package com.ecommerce.order.rma;

import com.ecommerce.order.admin.AdminSupport;
import com.ecommerce.order.admin.security.AdminPrincipal;
import com.ecommerce.order.service.ReturnStockClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.*;

/**
 * The warehouse inspection of received goods. Only the quantities the inspector accepts can be refunded; restocking is only possible
 * for goods in a resellable condition, and happens once the inspection is saved.
 */
@Service
public class InspectionService {
    public record ItemResult(long returnItemId, int receivedQuantity, int acceptedQuantity, String condition, boolean restockable, String note) {}

    public record Command(String packagingCondition, boolean accessoriesReceived, String missingItems, String damageDetails, String notes, String outcome, List<ItemResult> items) {}

    private static final Set<String> OUTCOMES = Set.of("ACCEPTED_FOR_REFUND", "ACCEPTED_FOR_EXCHANGE", "PARTIALLY_ACCEPTED", "REJECTED", "REQUIRES_MANUAL_REVIEW");
    private static final Set<String> PACKAGING = Set.of("INTACT", "DAMAGED", "MISSING", "OPENED");
    private static final Set<String> CONDITIONS = Set.of("NEW", "LIKE_NEW", "USED", "DAMAGED", "DEFECTIVE", "WRONG_ITEM");
    private static final Set<String> RESELLABLE = Set.of("NEW", "LIKE_NEW");

    private final JdbcTemplate jdbc;
    private final AdminSupport support;
    private final ReturnService returns;
    private final ReturnExceptionService exceptions;
    private final ReturnStockClient stock;

    public InspectionService(JdbcTemplate jdbc, AdminSupport support, ReturnService returns, ReturnExceptionService exceptions, ReturnStockClient stock) {
        this.jdbc = jdbc; this.support = support; this.returns = returns; this.exceptions = exceptions; this.stock = stock;
    }

    @Transactional
    public Map<String, Object> record(AdminPrincipal admin, long returnId, Command c) {
        Map<String, Object> r = returns.lock(admin, returnId);
        String status = (String) r.get("status");
        if (!status.equals("INSPECTION_PENDING")) throw conflict("Inspection can only be recorded while the return is INSPECTION_PENDING (it is " + status + ").");
        if (c == null || c.items() == null || c.items().isEmpty()) throw bad("Inspect every returned item.");
        if (!OUTCOMES.contains(c.outcome())) throw bad("outcome must be one of " + OUTCOMES + ".");
        if (!PACKAGING.contains(c.packagingCondition())) throw bad("packagingCondition must be one of " + PACKAGING + ".");
        for (String t : new String[]{c.missingItems(), c.damageDetails(), c.notes()}) if (t != null && t.length() > 2000) throw bad("Text fields are limited to 2000 characters.");
        List<Map<String, Object>> items = support.rows("SELECT * FROM order_return_items WHERE return_id=? ORDER BY id", returnId);
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        items.forEach(i -> byId.put(((Number) i.get("id")).longValue(), i));
        if (c.items().size() != byId.size() || c.items().stream().map(ItemResult::returnItemId).distinct().count() != byId.size() || !byId.keySet().containsAll(c.items().stream().map(ItemResult::returnItemId).toList()))
            throw bad("Provide exactly one result for each returned item.");
        int approvedTotal = 0, acceptedTotal = 0, receivedTotal = 0;
        for (ItemResult it : c.items()) {
            Map<String, Object> row = byId.get(it.returnItemId());
            int approved = row.get("approvedQuantity") == null ? ((Number) row.get("quantity")).intValue() : ((Number) row.get("approvedQuantity")).intValue();
            if (!CONDITIONS.contains(it.condition())) throw bad("condition must be one of " + CONDITIONS + ".");
            if (it.receivedQuantity() < 0 || it.acceptedQuantity() < 0) throw bad("Quantities cannot be negative.");
            if (it.receivedQuantity() > approved) throw bad("Received quantity for item " + it.returnItemId() + " exceeds the approved " + approved + ". Record the discrepancy in notes.");
            if (it.acceptedQuantity() > it.receivedQuantity()) throw bad("Accepted quantity cannot exceed received quantity.");
            if (it.restockable() && (!RESELLABLE.contains(it.condition()) || it.acceptedQuantity() == 0)) throw bad("Only accepted goods in NEW or LIKE_NEW condition can be restocked.");
            if (it.note() != null && it.note().length() > 500) throw bad("Item notes are limited to 500 characters.");
            approvedTotal += approved; acceptedTotal += it.acceptedQuantity(); receivedTotal += it.receivedQuantity();
        }
        switch (c.outcome()) {
            case "ACCEPTED_FOR_REFUND", "ACCEPTED_FOR_EXCHANGE" -> { if (acceptedTotal == 0) throw bad("An accepting outcome needs at least one accepted unit."); }
            case "PARTIALLY_ACCEPTED" -> { if (acceptedTotal == 0 || acceptedTotal >= approvedTotal) throw bad("PARTIALLY_ACCEPTED means some, but not all, approved units were accepted."); }
            case "REJECTED" -> { if (acceptedTotal != 0) throw bad("A rejected inspection cannot accept units."); }
            default -> { }
        }
        if (c.outcome().equals("ACCEPTED_FOR_EXCHANGE") && !"EXCHANGE".equals(r.get("resolution"))) throw bad("The customer did not ask for an exchange.");
        if (c.outcome().equals("ACCEPTED_FOR_REFUND") && "EXCHANGE".equals(r.get("resolution"))) throw bad("This return is an exchange; use ACCEPTED_FOR_EXCHANGE.");

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO return_inspections(tenant_id, return_id, packaging_condition, accessories_received, missing_items, damage_details, notes, outcome, inspector_id, inspector_email) VALUES (?,?,?,?,?,?,?,?,?,?)", new String[]{"id"});
            ps.setObject(1, admin.tenantId()); ps.setLong(2, returnId); ps.setString(3, c.packagingCondition()); ps.setBoolean(4, c.accessoriesReceived()); ps.setString(5, c.missingItems()); ps.setString(6, c.damageDetails());
            ps.setString(7, c.notes()); ps.setString(8, c.outcome()); ps.setLong(9, admin.userId()); ps.setString(10, admin.email());
            return ps;
        }, kh);
        long inspectionId = ((Number) kh.getKeys().get("id")).longValue();
        List<Runnable> restocks = new ArrayList<>();
        for (ItemResult it : c.items()) {
            Map<String, Object> row = byId.get(it.returnItemId());
            jdbc.update("INSERT INTO return_inspection_items(tenant_id, inspection_id, return_item_id, received_quantity, accepted_quantity, product_condition, restockable, note) VALUES (?,?,?,?,?,?,?,?)",
                    admin.tenantId(), inspectionId, it.returnItemId(), it.receivedQuantity(), it.acceptedQuantity(), it.condition(), it.restockable(), it.note());
            jdbc.update("UPDATE order_return_items SET received_quantity=?, accepted_quantity=?, disposition=? WHERE id=?", it.receivedQuantity(), it.acceptedQuantity(), it.restockable() ? "RESTOCK" : it.acceptedQuantity() > 0 ? "NO_RESTOCK" : "REJECTED", it.returnItemId());
            if (it.restockable() && !c.outcome().equals("REQUIRES_MANUAL_REVIEW")) {
                long productId = ((Number) row.get("productId")).longValue();
                restocks.add(() -> stock.restock(productId, it.acceptedQuantity(), "RMA-" + returnId + "-" + it.returnItemId(), "Return inspection accepted"));
            }
        }
        returns.audit(admin.tenantId(), returnId, "INSPECTION", String.valueOf(inspectionId), "RECORD", ReturnService.Actor.of(admin), c.outcome() + " accepted=" + acceptedTotal + "/" + approvedTotal);
        if (receivedTotal < approvedTotal || acceptedTotal < receivedTotal || c.packagingCondition().equals("MISSING") || (c.missingItems() != null && !c.missingItems().isBlank()))
            exceptions.raise(admin.tenantId(), returnId, c.missingItems() != null && !c.missingItems().isBlank() ? "MISSING_ITEMS" : "INSPECTION_MISMATCH", "MEDIUM",
                    "Received " + receivedTotal + " / accepted " + acceptedTotal + " of " + approvedTotal + " approved units.", c.damageDetails(), "inspection-" + inspectionId);
        String target = switch (c.outcome()) {
            case "REJECTED" -> "INSPECTION_FAILED";
            case "REQUIRES_MANUAL_REVIEW" -> null;
            default -> "INSPECTION_COMPLETED";
        };
        if (target == null) {
            exceptions.raise(admin.tenantId(), returnId, "MANUAL_REVIEW", "MEDIUM", "Inspector requested manual review.", c.notes(), "inspection-" + inspectionId);
            return Map.of("inspectionId", inspectionId, "outcome", c.outcome(), "status", status);
        }
        Map<String, Object> moved = returns.move(admin.tenantId(), returnId, target, ReturnService.Actor.of(admin), "Inspection " + c.outcome() + ".", "INSPECTION");
        restocks.forEach(Runnable::run);
        Map<String, Object> out = new LinkedHashMap<>(moved);
        out.put("inspectionId", inspectionId);
        out.put("outcome", c.outcome());
        return out;
    }

    public List<Map<String, Object>> list(AdminPrincipal admin, long returnId) {
        returns.load(admin, returnId);
        List<Map<String, Object>> out = support.rows("SELECT * FROM return_inspections WHERE tenant_id=? AND return_id=? ORDER BY id DESC", admin.tenantId(), returnId);
        for (Map<String, Object> i : out) i.put("items", support.rows("SELECT * FROM return_inspection_items WHERE tenant_id=? AND inspection_id=? ORDER BY id", admin.tenantId(), i.get("id")));
        return out;
    }
}
