package com.ecommerce.order.rma;

import com.ecommerce.order.admin.AdminSupport;
import com.ecommerce.order.admin.security.AdminPrincipal;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.*;

/**
 * Exchanges: a different product, variant or size in place of a returned item. Nothing is fulfilled until the exchange is approved,
 * stock for the replacement is confirmed available, and the goods are accepted (inspection) when the policy requires it. The
 * replacement is an ordinary order, so it follows the normal order lifecycle; if the new item costs more, that order cannot move
 * into processing until the Payment service confirms the difference. If it costs less, the difference is refunded through a normal
 * refund request.
 */
@Service
public class ExchangeService {
    private final JdbcTemplate jdbc;
    private final AdminSupport support;
    private final OrderRepository orders;
    private final ReturnService returns;
    private final PolicyService policies;
    private final CatalogClient catalog;

    public ExchangeService(JdbcTemplate jdbc, AdminSupport support, OrderRepository orders, ReturnService returns, PolicyService policies, CatalogClient catalog) {
        this.jdbc = jdbc; this.support = support; this.orders = orders; this.returns = returns; this.policies = policies; this.catalog = catalog;
    }

    public record Proposal(long returnItemId, long newProductId, String variant, int quantity, String note) {}

    @Transactional
    public Map<String, Object> propose(AdminPrincipal admin, long returnId, Proposal p) {
        Map<String, Object> r = returns.lock(admin, returnId);
        String status = (String) r.get("status");
        if (!"EXCHANGE".equals(r.get("resolution"))) throw conflict("This return was not requested as an exchange.");
        if (!Set.of("APPROVED", "RECEIVED", "INSPECTION_PENDING", "INSPECTION_COMPLETED", "EXCHANGE_PENDING", "PICKUP_SCHEDULED", "PICKED_UP", "IN_TRANSIT").contains(status))
            throw conflict("An exchange cannot be created while the return is " + status + ".");
        Policy policy = policies.version(admin.tenantId(), r.get("policyVersionId") == null ? null : ((Number) r.get("policyVersionId")).longValue(), (String) r.get("returnKind"));
        if (!policy.exchangeAllowed()) throw conflict("The return policy does not allow exchanges.");
        Map<String, Object> item = support.rows("SELECT * FROM order_return_items WHERE id=? AND return_id=?", p.returnItemId(), returnId).stream().findFirst().orElseThrow(() -> bad("That item is not part of this return."));
        int limit = item.get("acceptedQuantity") != null ? ((Number) item.get("acceptedQuantity")).intValue() : item.get("approvedQuantity") != null ? ((Number) item.get("approvedQuantity")).intValue() : ((Number) item.get("quantity")).intValue();
        Integer already = jdbc.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM exchange_requests WHERE tenant_id=? AND return_item_id=? AND status NOT IN ('REJECTED','CANCELLED')", Integer.class, admin.tenantId(), p.returnItemId());
        if (p.quantity() <= 0 || p.quantity() + (already == null ? 0 : already) > limit) throw bad("The exchange quantity must be between 1 and the " + limit + " unit(s) returned, counting existing exchanges.");
        if (p.variant() != null && (p.variant().length() > 80 || p.variant().matches(".*[<>].*"))) throw bad("variant must be plain text of at most 80 characters.");
        CatalogClient.ProductInfo product = catalog.product(p.newProductId()).orElseThrow(() -> conflict("The replacement product could not be found; try again shortly."));
        if (!product.active() || product.price() == null) throw conflict("The replacement product is not available for sale.");
        int stock = catalog.availableStock(p.newProductId()).orElseThrow(() -> conflict("Stock for the replacement could not be confirmed; try again shortly."));
        if (stock < p.quantity()) throw conflict("Only " + stock + " unit(s) of the replacement are in stock.");
        BigDecimal oldPrice = (BigDecimal) item.get("unitPrice");
        BigDecimal diff = product.price().subtract(oldPrice).multiply(BigDecimal.valueOf(p.quantity())).setScale(2, RoundingMode.HALF_UP);
        if (diff.signum() != 0 && !policy.exchangePriceDifferenceAllowed()) throw conflict("The policy only allows like-for-like exchanges (the price difference is " + diff + ").");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO exchange_requests(tenant_id, return_id, return_item_id, new_product_id, new_variant, new_product_name, quantity, old_unit_price, new_unit_price, price_difference, stock_checked_at, stock_available, note, created_by) VALUES (?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?,?,?)", new String[]{"id"});
            int n = 1;
            ps.setObject(n++, admin.tenantId()); ps.setLong(n++, returnId); ps.setLong(n++, p.returnItemId()); ps.setLong(n++, p.newProductId()); ps.setString(n++, p.variant()); ps.setString(n++, product.name());
            ps.setInt(n++, p.quantity()); ps.setBigDecimal(n++, oldPrice); ps.setBigDecimal(n++, product.price()); ps.setBigDecimal(n++, diff); ps.setInt(n++, stock); ps.setString(n++, p.note()); ps.setLong(n, admin.userId());
            return ps;
        }, kh);
        long id = ((Number) kh.getKeys().get("id")).longValue();
        returns.audit(admin.tenantId(), returnId, "EXCHANGE", String.valueOf(id), "PROPOSE", ReturnService.Actor.of(admin), product.name() + " x" + p.quantity() + " diff " + diff);
        return view(admin.tenantId(), id);
    }

    @Transactional
    public Map<String, Object> decide(AdminPrincipal admin, long exchangeId, boolean approve, String note) {
        Map<String, Object> e = lockExchange(admin, exchangeId);
        if (!"PENDING_APPROVAL".equals(e.get("status"))) throw conflict("This exchange is already " + e.get("status") + ".");
        if (!approve && (note == null || note.isBlank())) throw bad("A reason is required to reject an exchange.");
        if (approve) {
            int stock = catalog.availableStock(((Number) e.get("newProductId")).longValue()).orElseThrow(() -> conflict("Stock could not be confirmed; try again shortly."));
            if (stock < ((Number) e.get("quantity")).intValue()) throw conflict("Only " + stock + " unit(s) of the replacement are in stock now.");
            jdbc.update("UPDATE exchange_requests SET status='APPROVED', approved_by=?, approved_at=CURRENT_TIMESTAMP, stock_checked_at=CURRENT_TIMESTAMP, stock_available=?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=?", admin.userId(), stock, exchangeId);
        } else jdbc.update("UPDATE exchange_requests SET status='REJECTED', note=?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=?", ReturnService.clip(note, 500), exchangeId);
        returns.audit(admin.tenantId(), ((Number) e.get("returnId")).longValue(), "EXCHANGE", String.valueOf(exchangeId), approve ? "APPROVE" : "REJECT", ReturnService.Actor.of(admin), note);
        return view(admin.tenantId(), exchangeId);
    }

    /** Creates the replacement order. Requires an approved exchange, accepted goods (when inspection applies), and a fresh stock check. */
    @Transactional
    public Map<String, Object> fulfill(AdminPrincipal admin, long exchangeId) {
        Map<String, Object> e = lockExchange(admin, exchangeId);
        if (!"APPROVED".equals(e.get("status"))) throw conflict("Only an approved exchange can be fulfilled (it is " + e.get("status") + ").");
        long returnId = ((Number) e.get("returnId")).longValue();
        Map<String, Object> r = returns.lock(admin, returnId);
        String status = (String) r.get("status");
        Policy policy = policies.version(admin.tenantId(), r.get("policyVersionId") == null ? null : ((Number) r.get("policyVersionId")).longValue(), (String) r.get("returnKind"));
        if (status.equals("INSPECTION_COMPLETED")) {
            List<String> outcome = jdbc.queryForList("SELECT outcome FROM return_inspections WHERE tenant_id=? AND return_id=? ORDER BY id DESC", String.class, admin.tenantId(), returnId);
            if (outcome.isEmpty() || !Set.of("ACCEPTED_FOR_EXCHANGE", "PARTIALLY_ACCEPTED").contains(outcome.get(0))) throw conflict("The inspection did not accept the goods for exchange.");
            returns.move(admin.tenantId(), returnId, "EXCHANGE_PENDING", ReturnService.Actor.of(admin), "Exchange being fulfilled.", "EXCHANGE_START");
        } else if (status.equals("RECEIVED") && !policy.inspectionRequired()) {
            returns.move(admin.tenantId(), returnId, "EXCHANGE_PENDING", ReturnService.Actor.of(admin), "Exchange being fulfilled (inspection waived).", "EXCHANGE_START");
        } else if (!status.equals("EXCHANGE_PENDING")) throw conflict("The returned goods must be received" + (policy.inspectionRequired() ? " and accepted by inspection" : "") + " before the replacement is sent (return is " + status + ").");
        int qty = ((Number) e.get("quantity")).intValue();
        long productId = ((Number) e.get("newProductId")).longValue();
        int stock = catalog.availableStock(productId).orElseThrow(() -> conflict("Stock could not be confirmed; try again shortly."));
        if (stock < qty) throw conflict("Only " + stock + " unit(s) of the replacement are in stock now.");
        Order original = orders.findById(((Number) r.get("orderId")).longValue()).orElseThrow(() -> notFound("Order"));
        BigDecimal diff = (BigDecimal) e.get("priceDifference");
        BigDecimal unit = (BigDecimal) e.get("newUnitPrice");

        Order n = new Order();
        n.setUserId(original.getUserId()); n.setTenantId(original.getTenantId()); n.setStoreId(original.getStoreId()); n.setVendorId(original.getVendorId()); n.setOrderType(original.getOrderType());
        n.setCustomerName(original.getCustomerName()); n.setCustomerEmail(original.getCustomerEmail()); n.setCustomerPhone(original.getCustomerPhone());
        n.setDeliveryAddress(original.getDeliveryAddress()); n.setSpecialInstructions("Exchange for " + r.get("rmaNumber"));
        n.setTotalAmount(unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        n.setExchangeOfReturnId(returnId);
        n.setPaymentMethod("EXCHANGE");
        boolean payRequired = diff.signum() > 0;
        n.setExchangePaymentRequired(payRequired);
        n.setPaymentStatus(payRequired ? "PENDING" : "PAID");
        n.setStatus(payRequired ? "CREATED" : "CONFIRMED");
        if (!payRequired) n.setConfirmedAt(OffsetDateTime.now(ZoneOffset.UTC));
        OrderItem item = new OrderItem();
        item.setProductId(productId); item.setProductName(((String) e.get("newProductName")) + (e.get("newVariant") == null ? "" : " (" + e.get("newVariant") + ")")); item.setPrice(unit); item.setQuantity(qty); item.setVendorId(original.getVendorId());
        n.addItem(item);
        n = orders.save(n);
        jdbc.update("UPDATE exchange_requests SET status='FULFILLMENT_CREATED', replacement_order_id=?, stock_checked_at=CURRENT_TIMESTAMP, stock_available=?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=?", n.getId(), stock, exchangeId);
        returns.audit(admin.tenantId(), returnId, "EXCHANGE", String.valueOf(exchangeId), "FULFILL", ReturnService.Actor.of(admin), "replacement order " + n.getId() + " payRequired=" + payRequired);
        if (diff.signum() < 0) createDifferenceRefund(admin, returnId, original, diff.abs());
        return view(admin.tenantId(), exchangeId);
    }

    private void createDifferenceRefund(AdminPrincipal admin, long returnId, Order original, BigDecimal amount) {
        BigDecimal reserved = jdbc.queryForObject("SELECT COALESCE(SUM(final_amount),0) FROM return_refunds WHERE tenant_id=? AND order_id=? AND status IN ('APPROVED','PROCESSING','COMPLETED')", BigDecimal.class, admin.tenantId(), original.getId());
        BigDecimal headroom = original.getTotalAmount().subtract(reserved);
        BigDecimal final_ = amount.min(headroom).max(BigDecimal.ZERO);
        if (final_.signum() == 0) return;
        jdbc.update("INSERT INTO return_refunds(tenant_id, return_id, order_id, kind, method, status, original_amount, previously_refunded, items_amount, final_amount, calculation, requested_by) VALUES (?,?,?,'PRICE_DIFFERENCE','ORIGINAL','CALCULATED',?,?,?,?,?,?)",
                admin.tenantId(), returnId, original.getId(), original.getTotalAmount(), reserved, final_, final_, "Replacement costs less than the returned item.", admin.userId());
    }

    /** Called when the replacement order ships: the exchange is dispatched and the return is completed. */
    @Transactional
    public void onReplacementDispatched(UUID tenant, long replacementOrderId) {
        List<Map<String, Object>> ex = support.rows("SELECT id, return_id FROM exchange_requests WHERE tenant_id=? AND replacement_order_id=? AND status='FULFILLMENT_CREATED'", tenant, replacementOrderId);
        for (Map<String, Object> e : ex) {
            long returnId = ((Number) e.get("returnId")).longValue();
            jdbc.update("UPDATE exchange_requests SET status='DISPATCHED', version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=?", e.get("id"));
            Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM exchange_requests WHERE tenant_id=? AND return_id=? AND status IN ('PENDING_APPROVAL','APPROVED','FULFILLMENT_CREATED')", Integer.class, tenant, returnId);
            String st = (String) support.one("SELECT status FROM order_return WHERE id=?", "Return", returnId).get("status");
            if ((open == null || open == 0) && st.equals("EXCHANGE_PENDING")) returns.move(tenant, returnId, "EXCHANGED", ReturnService.Actor.system("ORDER"), "Replacement order dispatched.", "EXCHANGE_DISPATCHED");
        }
    }

    Map<String, Object> lockExchange(AdminPrincipal admin, long id) {
        Map<String, Object> e = support.one("SELECT * FROM exchange_requests WHERE id=? AND tenant_id=?", "Exchange", id, admin.tenantId());
        returns.load(admin, ((Number) e.get("returnId")).longValue());
        jdbc.query("SELECT id FROM exchange_requests WHERE id=? AND tenant_id=? FOR UPDATE", rs -> {}, id, admin.tenantId());
        return support.one("SELECT * FROM exchange_requests WHERE id=? AND tenant_id=?", "Exchange", id, admin.tenantId());
    }

    public Map<String, Object> view(UUID tenant, long id) { return support.one("SELECT * FROM exchange_requests WHERE id=? AND tenant_id=?", "Exchange", id, tenant); }

    public Map<String, Object> list(AdminPrincipal admin, Map<String, String> q) {
        List<Object> args = new ArrayList<>(); args.add(admin.tenantId());
        StringBuilder w = new StringBuilder(" WHERE e.tenant_id=?");
        if (!admin.hasPermission("order:view-all-stores")) {
            if (admin.storeIds().isEmpty()) w.append(" AND 1=0");
            else { w.append(" AND r.store_id IN (").append(admin.storeIds().stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","))).append(")"); args.addAll(admin.storeIds()); }
        }
        if (q.get("status") != null && !q.get("status").isBlank()) { w.append(" AND e.status=?"); args.add(q.get("status")); }
        int page = Math.max(0, Integer.parseInt(q.getOrDefault("page", "0"))), size = Math.min(100, Math.max(1, Integer.parseInt(q.getOrDefault("size", "25"))));
        String from = " FROM exchange_requests e JOIN order_return r ON r.id=e.return_id";
        Integer total = jdbc.queryForObject("SELECT COUNT(*)" + from + w, Integer.class, args.toArray());
        args.add(size); args.add(page * size);
        return Map.of("items", support.rows("SELECT e.id, e.return_id, r.rma_number, r.status AS return_status, e.new_product_id, e.new_product_name, e.new_variant, e.quantity, e.price_difference, e.status, e.replacement_order_id, e.created_at"
                + from + w + " ORDER BY e.created_at DESC, e.id DESC LIMIT ? OFFSET ?", args.toArray()), "total", total, "page", page, "size", size);
    }
}
