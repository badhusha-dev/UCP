package com.ecommerce.procurement.ucp;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/** Tenant-scoped suppliers, purchase orders, goods receiving and supplier returns. Every query carries the verified tenant id. */
@Service
@RequiredArgsConstructor
class UcpService {
    static final int OVER_RECEIPT_TOLERANCE_PERCENT = 5;
    private final JdbcTemplate jdbc;
    private final InventoryClient inventory;
    private final AccountingClient accounting;

    // ---------- suppliers ----------
    @Transactional
    Map<String, Object> createSupplier(UUID t, Map<String, Object> r, String actor) {
        String code = text(r, "code", true).toUpperCase(), name = text(r, "name", true);
        String currency = Optional.ofNullable(text(r, "currency", false)).orElse("USD").toUpperCase();
        if (!currency.matches("[A-Z]{3}")) throw bad("Currency must be a three-letter code.");
        int terms = r.get("paymentTermsDays") == null ? 30 : integer(r.get("paymentTermsDays"));
        if (terms < 0) throw bad("Payment terms cannot be negative.");
        long id;
        try {
            id = insert("INSERT INTO proc_supplier(tenant_id,code,name,contact_name,email,phone,payment_terms_days,currency,address,status,created_by) VALUES(?,?,?,?,?,?,?,?,?,'PENDING',?)",
                    t, code, name, text(r, "contactName", false), text(r, "email", false), text(r, "phone", false), terms, currency, text(r, "address", false), actor);
        } catch (DuplicateKeyException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier code already exists."); }
        audit(t, "SUPPLIER", id, "CREATE", actor, code);
        return supplier(t, id);
    }

    Map<String, Object> supplier(UUID t, long id) {
        return one("SELECT * FROM proc_supplier WHERE tenant_id=? AND id=?", "Supplier not found.", t, id);
    }

    List<Map<String, Object>> suppliers(UUID t, String status, String q) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM proc_supplier WHERE tenant_id=? ORDER BY name", t);
        return rows.stream().filter(m -> status == null || status.isBlank() || status.equalsIgnoreCase(String.valueOf(m.get("status"))))
                .filter(m -> q == null || q.isBlank() || String.valueOf(m.get("name")).toLowerCase().contains(q.toLowerCase()) || String.valueOf(m.get("code")).equalsIgnoreCase(q)).toList();
    }

    @Transactional
    Map<String, Object> setSupplierStatus(UUID t, long id, String status, String actor) {
        supplier(t, id);
        jdbc.update("UPDATE proc_supplier SET status=?, approved_by=CASE WHEN ?='APPROVED' THEN ? ELSE approved_by END WHERE tenant_id=? AND id=?", status, status, actor, t, id);
        audit(t, "SUPPLIER", id, status, actor, null);
        return supplier(t, id);
    }

    @Transactional
    Map<String, Object> upsertItem(UUID t, long supplierId, Map<String, Object> r, String actor) {
        supplier(t, supplierId);
        long product = longValue(r.get("productId"));
        BigDecimal cost = money(r.get("unitCost"));
        if (cost.signum() < 0) throw bad("Unit cost cannot be negative.");
        int lead = r.get("leadTimeDays") == null ? 7 : integer(r.get("leadTimeDays")), moq = r.get("minimumOrderQuantity") == null ? 1 : integer(r.get("minimumOrderQuantity"));
        if (lead < 0 || moq < 1) throw bad("Lead time and minimum order quantity are invalid.");
        int updated = jdbc.update("UPDATE proc_supplier_item SET unit_cost=?,lead_time_days=?,minimum_order_quantity=?,supplier_sku=?,active=TRUE WHERE tenant_id=? AND supplier_id=? AND product_id=?",
                cost, lead, moq, text(r, "supplierSku", false), t, supplierId, product);
        if (updated == 0) jdbc.update("INSERT INTO proc_supplier_item(tenant_id,supplier_id,product_id,supplier_sku,unit_cost,lead_time_days,minimum_order_quantity) VALUES(?,?,?,?,?,?,?)",
                t, supplierId, product, text(r, "supplierSku", false), cost, lead, moq);
        audit(t, "SUPPLIER_ITEM", supplierId + "/" + product, "UPSERT", actor, cost.toPlainString());
        return one("SELECT * FROM proc_supplier_item WHERE tenant_id=? AND supplier_id=? AND product_id=?", "Item not found.", t, supplierId, product);
    }

    List<Map<String, Object>> items(UUID t, long supplierId) {
        supplier(t, supplierId);
        return jdbc.queryForList("SELECT * FROM proc_supplier_item WHERE tenant_id=? AND supplier_id=? ORDER BY product_id", t, supplierId);
    }

    Map<String, Object> performance(UUID t, long supplierId) {
        supplier(t, supplierId);
        List<Map<String, Object>> pos = jdbc.queryForList("SELECT status, expected_date, updated_at FROM proc_po WHERE tenant_id=? AND supplier_id=?", t, supplierId);
        long completed = pos.stream().filter(p -> "RECEIVED".equals(p.get("status"))).count();
        long onTime = pos.stream().filter(p -> "RECEIVED".equals(p.get("status")) && p.get("expected_date") != null
                && !((java.sql.Timestamp) toTs(p.get("updated_at"))).toLocalDateTime().toLocalDate().isAfter(((Date) p.get("expected_date")).toLocalDate())).count();
        Map<String, Object> q = jdbc.queryForMap("SELECT COALESCE(SUM(l.accepted_quantity),0) AS accepted, COALESCE(SUM(l.rejected_quantity),0) AS rejected FROM proc_po_line l JOIN proc_po p ON p.id=l.po_id WHERE l.tenant_id=? AND p.supplier_id=?", t, supplierId);
        long acc = ((Number) q.get("accepted")).longValue(), rej = ((Number) q.get("rejected")).longValue();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("purchaseOrders", pos.size()); m.put("completed", completed); m.put("onTimeDeliveries", onTime);
        m.put("onTimeRate", completed == 0 ? null : BigDecimal.valueOf(onTime * 100.0 / completed).setScale(1, java.math.RoundingMode.HALF_UP));
        m.put("acceptedUnits", acc); m.put("rejectedUnits", rej);
        m.put("qualityRate", acc + rej == 0 ? null : BigDecimal.valueOf(acc * 100.0 / (acc + rej)).setScale(1, java.math.RoundingMode.HALF_UP));
        return m;
    }

    private Object toTs(Object v) { return v instanceof java.sql.Timestamp ? v : java.sql.Timestamp.from(((java.time.OffsetDateTime) v).toInstant()); }

    // ---------- purchase orders ----------
    @Transactional
    Map<String, Object> createPo(UUID t, Map<String, Object> r, String actor) {
        Map<String, Object> sup = supplier(t, longValue(r.get("supplierId")));
        if (!"APPROVED".equals(sup.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Purchase orders require an APPROVED supplier.");
        long warehouse = longValue(r.get("warehouseId"));
        List<?> lines = (List<?>) r.get("lines");
        if (lines == null || lines.isEmpty()) throw bad("A purchase order needs at least one line.");
        BigDecimal discount = r.get("discount") == null ? BigDecimal.ZERO : money(r.get("discount")), shipping = r.get("shipping") == null ? BigDecimal.ZERO : money(r.get("shipping")),
                rate = r.get("taxRatePercent") == null ? BigDecimal.ZERO : money(r.get("taxRatePercent"));
        if (discount.signum() < 0 || shipping.signum() < 0 || rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) throw bad("Discount, shipping and tax rate must be valid non-negative values.");
        record L(long product, int qty, BigDecimal cost) {}
        List<L> parsed = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Object o : lines) {
            Map<?, ?> m = (Map<?, ?>) o;
            long product = longValue(m.get("productId")); int qty = integer(m.get("quantity"));
            if (qty <= 0) throw bad("Line quantity must be positive.");
            if (!seen.add(product)) throw bad("Duplicate product on the purchase order.");
            BigDecimal cost = m.get("unitCost") != null ? money(m.get("unitCost")) : jdbc.query("SELECT unit_cost FROM proc_supplier_item WHERE tenant_id=? AND supplier_id=? AND product_id=? AND active=TRUE",
                    (rs, i) -> rs.getBigDecimal(1), t, sup.get("id"), product).stream().findFirst().orElseThrow(() -> bad("No catalog price for product " + product + "; supply unitCost."));
            if (cost.signum() < 0) throw bad("Unit cost cannot be negative.");
            parsed.add(new L(product, qty, cost));
        }
        BigDecimal subtotal = parsed.stream().map(l -> l.cost().multiply(BigDecimal.valueOf(l.qty()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total;
        try { total = PoRules.total(subtotal, discount, rate, shipping); } catch (IllegalArgumentException e) { throw bad(e.getMessage()); }
        String number = "PO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate expected = r.get("expectedDate") == null ? null : LocalDate.parse(String.valueOf(r.get("expectedDate")));
        long id = insert("INSERT INTO proc_po(tenant_id,po_number,supplier_id,warehouse_id,status,currency,subtotal,discount,tax_rate,tax_amount,shipping,total,expected_date,note,created_by) VALUES(?,?,?,?,'DRAFT',?,?,?,?,?,?,?,?,?,?)",
                t, number, sup.get("id"), warehouse, sup.get("currency"), subtotal, discount, rate, PoRules.tax(subtotal, discount, rate), shipping, total, expected == null ? null : Date.valueOf(expected), text(r, "note", false), actor);
        for (L l : parsed) jdbc.update("INSERT INTO proc_po_line(tenant_id,po_id,product_id,quantity,unit_cost,line_total) VALUES(?,?,?,?,?,?)", t, id, l.product(), l.qty(), l.cost(), l.cost().multiply(BigDecimal.valueOf(l.qty())));
        audit(t, "PO", id, "CREATE", actor, number);
        return po(t, id);
    }

    Map<String, Object> po(UUID t, long id) {
        Map<String, Object> m = new LinkedHashMap<>(one("SELECT * FROM proc_po WHERE tenant_id=? AND id=?", "Purchase order not found.", t, id));
        m.put("lines", jdbc.queryForList("SELECT * FROM proc_po_line WHERE tenant_id=? AND po_id=? ORDER BY id", t, id));
        m.put("overdue", isOverdue(m));
        return m;
    }

    List<Map<String, Object>> pos(UUID t, String status, Long supplierId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM proc_po WHERE tenant_id=? ORDER BY id DESC", t)) {
            Map<String, Object> m = new LinkedHashMap<>(row);
            boolean overdue = isOverdue(m);
            m.put("overdue", overdue);
            boolean statusOk = status == null || status.isBlank() || ("OVERDUE".equalsIgnoreCase(status) ? overdue : status.equalsIgnoreCase(String.valueOf(m.get("status"))));
            if (statusOk && (supplierId == null || supplierId.equals(((Number) m.get("supplier_id")).longValue()))) out.add(m);
        }
        return out;
    }

    private boolean isOverdue(Map<String, Object> m) {
        Object d = m.get("expected_date");
        return d != null && Set.of("ISSUED", "PARTIALLY_RECEIVED").contains(String.valueOf(m.get("status"))) && ((Date) d).toLocalDate().isBefore(LocalDate.now());
    }

    @Transactional
    Map<String, Object> transitionPo(UUID t, long id, PoRules.State target, String actor, String reason) {
        Map<String, Object> po = lockPo(t, id);
        PoRules.State current = PoRules.State.valueOf(String.valueOf(po.get("status")));
        if (!current.canMoveTo(target)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Purchase order cannot move from " + current + " to " + target + ".");
        if (target == PoRules.State.APPROVED && actor != null && actor.equalsIgnoreCase(String.valueOf(po.get("created_by"))))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A purchase order cannot be approved by its creator.");
        if (target == PoRules.State.REJECTED && (reason == null || reason.isBlank())) throw bad("A rejection reason is required.");
        List<Map<String, Object>> lines = jdbc.queryForList("SELECT * FROM proc_po_line WHERE tenant_id=? AND po_id=?", t, id);
        if (target == PoRules.State.CANCELLED && lines.stream().anyMatch(l -> ((Number) l.get("accepted_quantity")).intValue() > 0)) throw new ResponseStatusException(HttpStatus.CONFLICT, "A purchase order with received goods cannot be cancelled.");
        if (target == PoRules.State.ISSUED) for (var l : lines) inventory.incoming(t, ((Number) l.get("product_id")).longValue(), ((Number) l.get("quantity")).intValue());
        if (target == PoRules.State.CANCELLED && current == PoRules.State.ISSUED) for (var l : lines) inventory.incoming(t, ((Number) l.get("product_id")).longValue(), -((Number) l.get("quantity")).intValue());
        jdbc.update("UPDATE proc_po SET status=?, approved_by=CASE WHEN ?='APPROVED' THEN ? ELSE approved_by END, reject_reason=?, updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?",
                target.name(), target.name(), actor, target == PoRules.State.REJECTED ? reason : null, t, id);
        audit(t, "PO", id, target.name(), actor, reason);
        return po(t, id);
    }

    private Map<String, Object> lockPo(UUID t, long id) {
        return one("SELECT * FROM proc_po WHERE tenant_id=? AND id=? FOR UPDATE", "Purchase order not found.", t, id);
    }

    // ---------- goods receiving ----------
    @Transactional
    Map<String, Object> receive(UUID t, long poId, String idempotencyKey, List<Map<String, Object>> lines, String actor) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw bad("An idempotency key is required for goods receipts.");
        List<Map<String, Object>> prior = jdbc.queryForList("SELECT * FROM proc_receipt WHERE tenant_id=? AND idempotency_key=?", t, idempotencyKey);
        if (!prior.isEmpty()) {
            Map<String, Object> m = new LinkedHashMap<>(prior.get(0)); m.put("replayed", true); return m;
        }
        Map<String, Object> po = lockPo(t, poId);
        String status = String.valueOf(po.get("status"));
        if (!status.equals("ISSUED") && !status.equals("PARTIALLY_RECEIVED")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Goods can only be received against an issued purchase order.");
        if (lines == null || lines.isEmpty()) throw bad("At least one receipt line is required.");
        String receiptNo = "GR-" + po.get("po_number") + "-" + Integer.toHexString(idempotencyKey.hashCode()).toUpperCase();
        long receiptId;
        try { receiptId = insert("INSERT INTO proc_receipt(tenant_id,receipt_no,po_id,idempotency_key,received_by) VALUES(?,?,?,?,?)", t, receiptNo, poId, idempotencyKey, actor); }
        catch (DuplicateKeyException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "This receipt is already being processed; retry to read the result."); }
        List<Map<String, Object>> toInventory = new ArrayList<>();
        int closedOut = 0;
        for (Map<String, Object> l : lines) {
            long lineId = longValue(l.get("poLineId"));
            Map<String, Object> pl = one("SELECT * FROM proc_po_line WHERE tenant_id=? AND po_id=? AND id=?", "Purchase order line not found.", t, poId, lineId);
            int received = integer(l.get("received")), rejected = l.get("rejected") == null ? 0 : integer(l.get("rejected")), damaged = l.get("damaged") == null ? 0 : integer(l.get("damaged"));
            int accepted;
            try { accepted = PoRules.accepted(received, rejected, damaged); } catch (IllegalArgumentException e) { throw bad(e.getMessage()); }
            int ordered = ((Number) pl.get("quantity")).intValue(), already = ((Number) pl.get("accepted_quantity")).intValue();
            if (PoRules.overReceipt(ordered, already, accepted, OVER_RECEIPT_TOLERANCE_PERCENT)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Receipt exceeds the ordered quantity plus tolerance for line " + lineId + ".");
            jdbc.update("INSERT INTO proc_receipt_line(tenant_id,receipt_id,po_line_id,product_id,received,accepted,rejected,damaged,batch_no,expiry_date,serial_no,inspection_note) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    t, receiptId, lineId, pl.get("product_id"), received, accepted, rejected, damaged, text(l, "batchNo", false), l.get("expiryDate") == null ? null : Date.valueOf(LocalDate.parse(String.valueOf(l.get("expiryDate")))), text(l, "serialNo", false), text(l, "inspectionNote", false));
            jdbc.update("UPDATE proc_po_line SET accepted_quantity=accepted_quantity+?, rejected_quantity=rejected_quantity+? WHERE tenant_id=? AND id=?", accepted, rejected + damaged, t, lineId);
            closedOut += rejected + damaged;
            if (accepted > 0) {
                Map<String, Object> inv = new LinkedHashMap<>();
                inv.put("productId", pl.get("product_id")); inv.put("acceptedQuantity", accepted); inv.put("unitCost", pl.get("unit_cost"));
                inv.put("batchNo", l.get("batchNo")); inv.put("expiryDate", l.get("expiryDate"));
                toInventory.add(inv);
            }
            if (rejected + damaged > 0) inventory.incoming(t, ((Number) pl.get("product_id")).longValue(), -(rejected + damaged));
        }
        // Only accepted quantity reaches stock. Inventory is idempotent per receiptNo, so a retried request cannot add stock twice.
        if (!toInventory.isEmpty()) inventory.receipt(t, Map.of("sourceRef", receiptNo, "warehouseId", po.get("warehouse_id"), "lines", toInventory));
        boolean complete = jdbc.queryForObject("SELECT COUNT(*) FROM proc_po_line WHERE tenant_id=? AND po_id=? AND accepted_quantity < quantity", Integer.class, t, poId) == 0;
        jdbc.update("UPDATE proc_po SET status=?, updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?", complete ? "RECEIVED" : "PARTIALLY_RECEIVED", t, poId);
        audit(t, "RECEIPT", receiptNo, "RECEIVE", actor, "lines=" + lines.size() + " rejectedOrDamaged=" + closedOut);
        Map<String, Object> out = new LinkedHashMap<>(one("SELECT * FROM proc_receipt WHERE tenant_id=? AND id=?", "Receipt not found.", t, receiptId));
        out.put("lines", jdbc.queryForList("SELECT * FROM proc_receipt_line WHERE tenant_id=? AND receipt_id=?", t, receiptId));
        out.put("poStatus", complete ? "RECEIVED" : "PARTIALLY_RECEIVED");
        return out;
    }

    List<Map<String, Object>> receipts(UUID t) { return jdbc.queryForList("SELECT * FROM proc_receipt WHERE tenant_id=? ORDER BY id DESC", t); }

    // ---------- supplier returns ----------
    @Transactional
    Map<String, Object> createReturn(UUID t, Map<String, Object> r, String actor) {
        Map<String, Object> sup = supplier(t, longValue(r.get("supplierId")));
        List<?> lines = (List<?>) r.get("lines");
        String reason = text(r, "reason", true);
        if (lines == null || lines.isEmpty()) throw bad("A supplier return needs at least one line.");
        String no = "SR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long id = insert("INSERT INTO proc_return(tenant_id,return_no,supplier_id,warehouse_id,status,reason,created_by) VALUES(?,?,?,?,'DRAFT',?,?)", t, no, sup.get("id"), longValue(r.get("warehouseId")), reason, actor);
        for (Object o : lines) {
            Map<?, ?> m = (Map<?, ?>) o; int qty = integer(m.get("quantity"));
            if (qty <= 0) throw bad("Return quantity must be positive.");
            jdbc.update("INSERT INTO proc_return_line(tenant_id,return_id,product_id,quantity) VALUES(?,?,?,?)", t, id, longValue(m.get("productId")), qty);
        }
        audit(t, "SUPPLIER_RETURN", id, "CREATE", actor, no);
        return supplierReturn(t, id);
    }

    Map<String, Object> supplierReturn(UUID t, long id) {
        Map<String, Object> m = new LinkedHashMap<>(one("SELECT * FROM proc_return WHERE tenant_id=? AND id=?", "Supplier return not found.", t, id));
        m.put("lines", jdbc.queryForList("SELECT * FROM proc_return_line WHERE tenant_id=? AND return_id=?", t, id));
        return m;
    }

    List<Map<String, Object>> returns(UUID t) { return jdbc.queryForList("SELECT * FROM proc_return WHERE tenant_id=? ORDER BY id DESC", t); }

    /** Removes the stock via inventory's FIFO costing API; commits SHIPPED before any accounting call so a retry never deducts twice. */
    @Transactional
    Map<String, Object> shipReturn(UUID t, long id, String actor) {
        Map<String, Object> ret = one("SELECT * FROM proc_return WHERE tenant_id=? AND id=? FOR UPDATE", "Supplier return not found.", t, id);
        if (!"DRAFT".equals(ret.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a DRAFT return can be shipped.");
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> l : jdbc.queryForList("SELECT * FROM proc_return_line WHERE tenant_id=? AND return_id=?", t, id)) {
            Map<String, Object> res = inventory.supplierReturn(t, Map.of("sourceRef", ret.get("return_no") + "-" + l.get("product_id"), "warehouseId", ret.get("warehouse_id"), "productId", l.get("product_id"), "quantity", l.get("quantity")));
            BigDecimal cost = new BigDecimal(String.valueOf(res.get("costValue")));
            jdbc.update("UPDATE proc_return_line SET cost_value=? WHERE id=?", cost, l.get("id"));
            total = total.add(cost);
        }
        jdbc.update("UPDATE proc_return SET status='SHIPPED', total_credit=? WHERE tenant_id=? AND id=?", total, t, id);
        audit(t, "SUPPLIER_RETURN", id, "SHIP", actor, total.toPlainString());
        return supplierReturn(t, id);
    }

    /** Posts the supplier credit note through the accounting API (idempotent). Accounting tables are never written from here. */
    @Transactional
    Map<String, Object> creditReturn(UUID t, long id, String actor) {
        Map<String, Object> ret = one("SELECT * FROM proc_return WHERE tenant_id=? AND id=? FOR UPDATE", "Supplier return not found.", t, id);
        if ("CREDITED".equals(ret.get("status"))) return supplierReturn(t, id);
        if (!"SHIPPED".equals(ret.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a SHIPPED return can be credited.");
        BigDecimal total = (BigDecimal) ret.get("total_credit");
        if (total.signum() <= 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "The return has no costed value to credit.");
        Map<String, Object> sup = supplier(t, ((Number) ret.get("supplier_id")).longValue());
        Map<String, Object> entry = accounting.creditNote(Map.of("idempotencyKey", "SRET-" + t + "-" + ret.get("return_no"), "creditNoteNumber", ret.get("return_no"),
                "supplierName", sup.get("name"), "currency", sup.get("currency"), "subtotal", total, "taxAmount", BigDecimal.ZERO, "totalAmount", total, "postedBy", actor == null ? "procurement-service" : actor));
        jdbc.update("UPDATE proc_return SET status='CREDITED', accounting_entry=? WHERE tenant_id=? AND id=?", String.valueOf(entry.get("entryNumber")), t, id);
        audit(t, "SUPPLIER_RETURN", id, "CREDIT", actor, String.valueOf(entry.get("entryNumber")));
        return supplierReturn(t, id);
    }

    // ---------- replenishment & analytics ----------
    List<Map<String, Object>> replenishment(UUID t) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : inventory.forecast(t)) {
            if (((Number) f.get("suggestedOrderQuantity")).intValue() <= 0) continue;
            Map<String, Object> m = new LinkedHashMap<>(f);
            List<Map<String, Object>> cat = jdbc.queryForList("SELECT i.supplier_id, i.unit_cost, i.minimum_order_quantity FROM proc_supplier_item i JOIN proc_supplier s ON s.id=i.supplier_id WHERE i.tenant_id=? AND i.product_id=? AND i.active=TRUE AND s.status='APPROVED' ORDER BY i.unit_cost", t, f.get("productId"));
            m.put("suggestedSupplier", cat.isEmpty() ? null : cat.get(0));
            out.add(m);
        }
        return out;
    }

    Map<String, Object> analytics(UUID t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("poByStatus", jdbc.queryForList("SELECT status, COUNT(*) AS count, COALESCE(SUM(total),0) AS value FROM proc_po WHERE tenant_id=? GROUP BY status", t));
        m.put("spendBySupplier", jdbc.queryForList("SELECT s.name AS supplier, COALESCE(SUM(p.total),0) AS value FROM proc_po p JOIN proc_supplier s ON s.id=p.supplier_id WHERE p.tenant_id=? AND p.status IN ('ISSUED','PARTIALLY_RECEIVED','RECEIVED') GROUP BY s.name ORDER BY value DESC", t));
        m.put("overduePurchaseOrders", pos(t, "OVERDUE", null).size());
        m.put("openPurchaseOrderValue", jdbc.queryForObject("SELECT COALESCE(SUM(total),0) FROM proc_po WHERE tenant_id=? AND status IN ('ISSUED','PARTIALLY_RECEIVED')", BigDecimal.class, t));
        m.put("suppliers", jdbc.queryForList("SELECT status, COUNT(*) AS count FROM proc_supplier WHERE tenant_id=? GROUP BY status", t));
        return m;
    }

    String poDocument(UUID t, long id) {
        Map<String, Object> po = po(t, id), sup = supplier(t, ((Number) po.get("supplier_id")).longValue());
        StringBuilder h = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><title>").append(esc(po.get("po_number"))).append("</title></head><body><h1>Purchase Order ")
                .append(esc(po.get("po_number"))).append("</h1><p>Supplier: ").append(esc(sup.get("name"))).append(" (").append(esc(sup.get("code"))).append(")</p><p>Status: ").append(esc(po.get("status")))
                .append(" | Expected: ").append(esc(po.get("expected_date"))).append("</p><table border=\"1\" cellpadding=\"4\"><tr><th>Product</th><th>Qty</th><th>Unit cost</th><th>Total</th></tr>");
        for (Object o : (List<?>) po.get("lines")) { Map<?, ?> l = (Map<?, ?>) o; h.append("<tr><td>").append(esc(l.get("product_id"))).append("</td><td>").append(esc(l.get("quantity"))).append("</td><td>").append(esc(l.get("unit_cost"))).append("</td><td>").append(esc(l.get("line_total"))).append("</td></tr>"); }
        return h.append("</table><p>Subtotal ").append(esc(po.get("subtotal"))).append(" | Discount ").append(esc(po.get("discount"))).append(" | Tax ").append(esc(po.get("tax_amount")))
                .append(" | Shipping ").append(esc(po.get("shipping"))).append(" | <strong>Total ").append(esc(po.get("total"))).append(' ').append(esc(po.get("currency"))).append("</strong></p></body></html>").toString();
    }

    List<Map<String, Object>> audit(UUID t) { return jdbc.queryForList("SELECT * FROM proc_audit WHERE tenant_id=? ORDER BY id DESC LIMIT 100", t); }

    // ---------- helpers ----------
    private static String esc(Object v) { return v == null ? "" : String.valueOf(v).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    private void audit(UUID t, String entity, Object id, String action, String actor, String details) {
        jdbc.update("INSERT INTO proc_audit(tenant_id,entity,entity_id,action,actor,details) VALUES(?,?,?,?,?,?)", t, entity, String.valueOf(id), action, actor, details);
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> { var ps = con.prepareStatement(sql, new String[]{"id"}); for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]); return ps; }, kh);
        return ((Number) Objects.requireNonNull(kh.getKeys()).get("id")).longValue();
    }

    private Map<String, Object> one(String sql, String notFound, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFound);
        return rows.get(0);
    }

    private static String text(Map<String, Object> m, String key, boolean required) {
        Object v = m.get(key);
        String s = v == null ? null : String.valueOf(v).trim();
        if (required && (s == null || s.isEmpty())) throw bad(key + " is required.");
        return s == null || s.isEmpty() ? null : s;
    }

    private static int integer(Object v) { try { return new BigDecimal(String.valueOf(v)).intValueExact(); } catch (Exception e) { throw bad("Expected a whole number but got: " + v); } }
    private static long longValue(Object v) { try { return new BigDecimal(String.valueOf(v)).longValueExact(); } catch (Exception e) { throw bad("Expected an identifier but got: " + v); } }
    private static BigDecimal money(Object v) { try { return new BigDecimal(String.valueOf(v)).setScale(4, java.math.RoundingMode.HALF_UP); } catch (Exception e) { throw bad("Expected a decimal number but got: " + v); } }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
