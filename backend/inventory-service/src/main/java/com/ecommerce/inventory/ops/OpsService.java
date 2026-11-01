package com.ecommerce.inventory.ops;

import com.ecommerce.inventory.model.InventoryItem;
import com.ecommerce.inventory.model.StockMovement;
import com.ecommerce.inventory.ops.domain.*;
import com.ecommerce.inventory.repository.InventoryItemRepository;
import com.ecommerce.inventory.repository.StockMovementRepository;
import com.ecommerce.inventory.service.InventoryProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Warehouse stock, transfers, adjustments, receipts, valuation and forecasting. Every method is tenant-scoped by the caller's verified tenant id. */
@Service
@RequiredArgsConstructor
public class OpsService {
    private final WarehouseRepository warehouses;
    private final LocationRepository locations;
    private final WarehouseStockRepository whStock;
    private final TransferRepository transfers;
    private final TransferLineRepository transferLines;
    private final AdjustmentRepository adjustments;
    private final CostLayerRepository layers;
    private final ConfigRepository configs;
    private final AuditRepository audits;
    private final InventoryItemRepository items;
    private final StockMovementRepository movements;
    private final InventoryProducerService events;
    private final JdbcTemplate jdbc;

    public record WarehouseRequest(String code, String name, String storeId, String address, Integer capacityUnits, String managerEmail) {}
    public record LocationRequest(String code, Integer capacityUnits) {}
    public record TransferLineRequest(Long productId, Integer quantity) {}
    public record TransferRequest(Long sourceWarehouseId, Long destinationWarehouseId, String note, List<TransferLineRequest> lines) {}
    public record ReceiveLine(Long productId, Integer received, Integer damaged) {}
    public record AdjustmentRequest(Long warehouseId, Long productId, Integer newQuantity, String reason, String evidence, String idempotencyKey) {}
    public record ReceiptLine(Long productId, Integer acceptedQuantity, BigDecimal unitCost, String batchNo, LocalDate expiryDate) {}
    public record ReceiptRequest(String sourceRef, Long warehouseId, List<ReceiptLine> lines) {}

    // ---------- warehouses ----------
    @Transactional
    public Warehouse createWarehouse(UUID tenant, WarehouseRequest r, String actor) {
        if (blank(r.code()) || blank(r.name())) throw bad("Warehouse code and name are required.");
        if (r.capacityUnits() != null && r.capacityUnits() < 0) throw bad("Capacity cannot be negative.");
        String code = r.code().trim().toUpperCase();
        if (warehouses.existsByTenantIdAndCode(tenant, code)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Warehouse code already exists.");
        Warehouse w = new Warehouse();
        w.tenantId = tenant; w.code = code; w.name = r.name().trim(); w.storeId = r.storeId(); w.address = r.address();
        w.capacityUnits = r.capacityUnits() == null ? 0 : r.capacityUnits(); w.managerEmail = r.managerEmail();
        w = warehouses.save(w);
        audit(tenant, "WAREHOUSE", w.id, "CREATE", actor, code);
        return w;
    }

    @Transactional(readOnly = true)
    public List<Warehouse> listWarehouses(UUID tenant) { return warehouses.findByTenantIdOrderByCode(tenant); }

    @Transactional
    public WarehouseLocation addLocation(UUID tenant, Long warehouseId, LocationRequest r, String actor) {
        Warehouse w = warehouse(tenant, warehouseId);
        if (blank(r.code())) throw bad("Location code is required.");
        String code = r.code().trim().toUpperCase();
        if (locations.existsByTenantIdAndWarehouseIdAndCode(tenant, w.id, code)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Location already exists.");
        WarehouseLocation l = new WarehouseLocation();
        l.tenantId = tenant; l.warehouseId = w.id; l.code = code; l.capacityUnits = r.capacityUnits() == null ? 0 : r.capacityUnits();
        l = locations.save(l);
        audit(tenant, "LOCATION", l.id, "CREATE", actor, w.code + "/" + code);
        return l;
    }

    @Transactional(readOnly = true)
    public List<WarehouseLocation> listLocations(UUID tenant, Long warehouseId) { return locations.findByTenantIdAndWarehouseId(tenant, warehouse(tenant, warehouseId).id); }

    @Transactional(readOnly = true)
    public List<WarehouseStock> warehouseStock(UUID tenant, Long warehouseId) {
        return warehouseId == null ? whStock.findByTenantId(tenant) : whStock.findByTenantIdAndWarehouseId(tenant, warehouse(tenant, warehouseId).id);
    }

    // ---------- ledger (atomic) ----------
    /** Atomic on-hand change at warehouse level and aggregate level, with a movement row. Fails (409) rather than going negative or below reserved. */
    void applyOnHand(UUID tenant, Long warehouseId, Long productId, int delta, String type, String reason, String actor) {
        ensureStockRow(tenant, warehouseId, productId);
        int rows = jdbc.update("UPDATE wms_stock SET on_hand = on_hand + ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id=? AND warehouse_id=? AND product_id=? AND on_hand + ? - reserved - unavailable >= 0",
                delta, tenant, warehouseId, productId, delta);
        if (rows == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient unreserved stock in the warehouse for product " + productId + ".");
        if (items.findByTenantIdAndProductId(tenant, productId).isEmpty()) {
            if (delta < 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory record not found for product " + productId + ".");
            items.save(InventoryItem.builder().tenantId(tenant).productId(productId).stockQuantity(0).build());
        }
        if (items.tryAdjustOnHand(tenant, productId, delta) == 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aggregate stock would drop below reserved quantity for product " + productId + ".");
        movements.save(StockMovement.builder().tenantId(tenant).productId(productId).delta(delta).movementType(type).reason(reason).performedBy(actor).build());
    }

    private void reserveAtWarehouse(UUID tenant, Long warehouseId, Long productId, int qty) {
        ensureStockRow(tenant, warehouseId, productId);
        int rows = jdbc.update("UPDATE wms_stock SET reserved = reserved + ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id=? AND warehouse_id=? AND product_id=? AND on_hand - reserved - unavailable >= ?",
                qty, tenant, warehouseId, productId, qty);
        if (rows == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available stock at source warehouse for product " + productId + ".");
        if (items.tryReserve(tenant, productId, qty) == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available stock for product " + productId + ".");
    }

    private void releaseAtWarehouse(UUID tenant, Long warehouseId, Long productId, int qty) {
        jdbc.update("UPDATE wms_stock SET reserved = reserved - ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id=? AND warehouse_id=? AND product_id=? AND reserved >= ?", qty, tenant, warehouseId, productId, qty);
        items.releaseReserved(tenant, productId, qty);
    }

    private void ensureStockRow(UUID tenant, Long warehouseId, Long productId) {
        if (whStock.findByTenantIdAndWarehouseIdAndProductId(tenant, warehouseId, productId).isEmpty()) {
            WarehouseStock s = new WarehouseStock();
            s.tenantId = tenant; s.warehouseId = warehouseId; s.productId = productId;
            whStock.saveAndFlush(s);
        }
    }

    // ---------- transfers ----------
    @Transactional
    public Map<String, Object> createTransfer(UUID tenant, TransferRequest r, String actor) {
        if (r.lines() == null || r.lines().isEmpty()) throw bad("A transfer needs at least one line.");
        if (Objects.equals(r.sourceWarehouseId(), r.destinationWarehouseId())) throw bad("Source and destination must differ.");
        Warehouse src = warehouse(tenant, r.sourceWarehouseId());
        warehouse(tenant, r.destinationWarehouseId());
        for (var l : r.lines()) if (l.productId() == null || l.quantity() == null || l.quantity() <= 0) throw bad("Each line needs a product and a positive quantity.");
        StockTransfer t = new StockTransfer();
        t.tenantId = tenant; t.transferNo = "TR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        t.sourceWarehouseId = src.id; t.destinationWarehouseId = r.destinationWarehouseId();
        t.status = TransferState.DRAFT.name(); t.requestedBy = actor; t.note = r.note();
        t = transfers.save(t);
        for (var l : r.lines()) {
            StockTransferLine line = new StockTransferLine();
            line.tenantId = tenant; line.transferId = t.id; line.productId = l.productId(); line.quantity = l.quantity();
            transferLines.save(line);
        }
        audit(tenant, "TRANSFER", t.id, "CREATE", actor, t.transferNo);
        return transferView(tenant, t);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTransfers(UUID tenant) {
        return transfers.findByTenantIdOrderByIdDesc(tenant).stream().map(t -> transferView(tenant, t)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTransfer(UUID tenant, Long id) { return transferView(tenant, transfer(tenant, id)); }

    /** Moves a transfer to the target state and performs the stock effect of that step. {@code receive} is used for IN_TRANSIT receipts. */
    @Transactional
    public Map<String, Object> transition(UUID tenant, Long id, TransferState target, String actor, boolean canApprove, List<ReceiveLine> receive) {
        StockTransfer t = transfer(tenant, id);
        TransferState current = TransferState.valueOf(t.status);
        List<StockTransferLine> lines = transferLines.findByTenantIdAndTransferId(tenant, id);
        if (target == TransferState.RECEIVED || target == TransferState.PARTIALLY_RECEIVED) {
            if (current == TransferState.DISPATCHED) { t.status = TransferState.IN_TRANSIT.name(); current = TransferState.IN_TRANSIT; }
            if (current != TransferState.IN_TRANSIT && current != TransferState.PARTIALLY_RECEIVED) throw conflict("Transfer is not in transit.");
            receiveLines(tenant, t, lines, receive, actor);
            boolean complete = lines.stream().allMatch(l -> l.receivedQuantity + l.damagedQuantity >= l.quantity);
            t.status = complete ? TransferState.COMPLETED.name() : TransferState.PARTIALLY_RECEIVED.name();
            return finish(tenant, t, actor, "RECEIVE", t.status);
        }
        try { current.require(target); } catch (IllegalStateException e) { throw conflict(e.getMessage()); }
        switch (target) {
            case APPROVED -> {
                if (!canApprove) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transfer approval permission is required.");
                if (actor != null && actor.equalsIgnoreCase(t.requestedBy)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A transfer cannot be approved by its requester.");
                for (var l : lines) reserveAtWarehouse(tenant, t.sourceWarehouseId, l.productId, l.quantity);
                t.approvedBy = actor;
            }
            case REJECTED -> {
                if (!canApprove) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transfer approval permission is required.");
            }
            case CANCELLED -> { if (current.holdsSourceReservation()) for (var l : lines) releaseAtWarehouse(tenant, t.sourceWarehouseId, l.productId, l.quantity); }
            case DISPATCHED -> {
                for (var l : lines) {
                    releaseAtWarehouse(tenant, t.sourceWarehouseId, l.productId, l.quantity);
                    applyOnHand(tenant, t.sourceWarehouseId, l.productId, -l.quantity, "TRANSFER_OUT", t.transferNo, actor);
                    jdbc.update("UPDATE wms_stock SET in_transit_out = in_transit_out + ? WHERE tenant_id=? AND warehouse_id=? AND product_id=?", l.quantity, tenant, t.sourceWarehouseId, l.productId);
                }
            }
            case LOST, DAMAGED -> {
                for (var l : lines) jdbc.update("UPDATE wms_stock SET in_transit_out = in_transit_out - ? WHERE tenant_id=? AND warehouse_id=? AND product_id=? AND in_transit_out >= ?", l.quantity - l.receivedQuantity - l.damagedQuantity, tenant, t.sourceWarehouseId, l.productId, l.quantity - l.receivedQuantity - l.damagedQuantity);
            }
            default -> { }
        }
        t.status = target.name();
        return finish(tenant, t, actor, target.name(), t.transferNo);
    }

    private void receiveLines(UUID tenant, StockTransfer t, List<StockTransferLine> lines, List<ReceiveLine> receive, String actor) {
        Map<Long, ReceiveLine> byProduct = new HashMap<>();
        if (receive != null) for (var r : receive) byProduct.put(r.productId(), r);
        for (var l : lines) {
            ReceiveLine r = byProduct.get(l.productId);
            int got = r == null || r.received() == null ? l.quantity - l.receivedQuantity - l.damagedQuantity : r.received();
            int bad = r == null || r.damaged() == null ? 0 : r.damaged();
            if (got < 0 || bad < 0 || bad > got) throw bad("Invalid received/damaged quantities for product " + l.productId + ".");
            if (l.receivedQuantity + l.damagedQuantity + got > l.quantity) throw bad("Cannot receive more than was dispatched for product " + l.productId + ".");
            int accepted = got - bad;
            if (accepted > 0) applyOnHand(tenant, t.destinationWarehouseId, l.productId, accepted, "TRANSFER_IN", t.transferNo, actor);
            l.receivedQuantity += accepted; l.damagedQuantity += bad;
            transferLines.save(l);
            jdbc.update("UPDATE wms_stock SET in_transit_out = in_transit_out - ? WHERE tenant_id=? AND warehouse_id=? AND product_id=? AND in_transit_out >= ?", got, tenant, t.sourceWarehouseId, l.productId, got);
        }
    }

    private Map<String, Object> finish(UUID tenant, StockTransfer t, String actor, String action, String details) {
        t.updatedAt = java.time.OffsetDateTime.now();
        transfers.save(t);
        audit(tenant, "TRANSFER", t.id, action, actor, details);
        return transferView(tenant, t);
    }

    private Map<String, Object> transferView(UUID tenant, StockTransfer t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id); m.put("transferNo", t.transferNo); m.put("status", t.status);
        m.put("sourceWarehouseId", t.sourceWarehouseId); m.put("destinationWarehouseId", t.destinationWarehouseId);
        m.put("requestedBy", t.requestedBy); m.put("approvedBy", t.approvedBy); m.put("note", t.note); m.put("createdAt", t.createdAt);
        m.put("lines", transferLines.findByTenantIdAndTransferId(tenant, t.id).stream().map(l -> Map.of("productId", l.productId, "quantity", l.quantity,
                "receivedQuantity", l.receivedQuantity, "damagedQuantity", l.damagedQuantity)).toList());
        return m;
    }

    // ---------- adjustments ----------
    @Transactional
    public StockAdjustment requestAdjustment(UUID tenant, AdjustmentRequest r, String actor, boolean canApprove) {
        if (blank(r.reason())) throw bad("A reason is required for every adjustment.");
        if (r.newQuantity() == null || r.newQuantity() < 0 || r.productId() == null) throw bad("Product and a non-negative new quantity are required.");
        warehouse(tenant, r.warehouseId());
        if (r.idempotencyKey() != null) {
            var existing = adjustments.findByTenantIdAndIdempotencyKey(tenant, r.idempotencyKey());
            if (existing.isPresent()) return existing.get();
        }
        int before = whStock.findByTenantIdAndWarehouseIdAndProductId(tenant, r.warehouseId(), r.productId()).map(s -> s.onHand).orElse(0);
        StockAdjustment a = new StockAdjustment();
        a.tenantId = tenant; a.warehouseId = r.warehouseId(); a.productId = r.productId(); a.beforeQuantity = before; a.afterQuantity = r.newQuantity();
        a.delta = r.newQuantity() - before; a.reason = r.reason(); a.evidence = r.evidence(); a.requestedBy = actor; a.idempotencyKey = r.idempotencyKey();
        if (a.delta == 0) throw bad("The new quantity equals the current quantity.");
        InventoryConfig cfg = config(tenant);
        boolean needsApproval = Math.abs(a.delta) > cfg.adjustmentApprovalThreshold;
        a.status = needsApproval ? "PENDING" : "APPLIED";
        a = adjustments.save(a);
        if (needsApproval) {
            audit(tenant, "ADJUSTMENT", a.id, "REQUEST", actor, "delta " + a.delta);
            events.publishInventoryEvent("ADJUSTMENT_PENDING", a.productId, Map.of("tenantId", tenant.toString(), "adjustmentId", a.id,
                    "alertKey", "ADJ-PENDING:" + tenant + ":" + a.id, "delta", a.delta));
        } else {
            applyAdjustment(tenant, a, actor);
        }
        return a;
    }

    @Transactional
    public StockAdjustment decideAdjustment(UUID tenant, Long id, boolean approve, String actor) {
        StockAdjustment a = adjustments.findByTenantIdAndId(tenant, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adjustment not found."));
        if (!"PENDING".equals(a.status)) throw conflict("Adjustment is already " + a.status + ".");
        if (actor != null && actor.equalsIgnoreCase(a.requestedBy)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An adjustment cannot be approved by its requester.");
        a.approvedBy = actor; a.decidedAt = java.time.OffsetDateTime.now();
        if (approve) { a.status = "APPLIED"; applyAdjustment(tenant, a, actor); } else { a.status = "REJECTED"; adjustments.save(a); audit(tenant, "ADJUSTMENT", a.id, "REJECT", actor, a.reason); }
        return a;
    }

    private void applyAdjustment(UUID tenant, StockAdjustment a, String actor) {
        applyOnHand(tenant, a.warehouseId, a.productId, a.delta, "ADJUSTMENT", a.reason, actor);
        adjustments.save(a);
        audit(tenant, "ADJUSTMENT", a.id, "APPLY", actor, a.beforeQuantity + " -> " + a.afterQuantity);
    }

    @Transactional(readOnly = true)
    public List<StockAdjustment> listAdjustments(UUID tenant) { return adjustments.findByTenantIdOrderByIdDesc(tenant); }

    // ---------- receipts from procurement (service-token callback) ----------
    /** Accepted quantity only. Idempotent per sourceRef: a duplicate or concurrent replay never adds stock twice. */
    @Transactional
    public Map<String, Object> receive(UUID tenant, ReceiptRequest r, String actor) {
        if (blank(r.sourceRef()) || r.lines() == null || r.lines().isEmpty()) throw bad("sourceRef and lines are required.");
        warehouse(tenant, r.warehouseId());
        if (layers.existsByTenantIdAndSourceRef(tenant, r.sourceRef())) return Map.of("status", "DUPLICATE_IGNORED", "sourceRef", r.sourceRef());
        for (var l : r.lines()) {
            if (l.acceptedQuantity() == null || l.acceptedQuantity() <= 0 || l.unitCost() == null || l.unitCost().signum() < 0) throw bad("Accepted quantity must be positive and cost non-negative.");
            InventoryItem item = items.findByTenantIdAndProductId(tenant, l.productId()).orElse(null);
            int onHandBefore = item == null ? 0 : item.getStockQuantity();
            BigDecimal avgBefore = item == null ? BigDecimal.ZERO : item.getCostPrice();
            applyOnHand(tenant, r.warehouseId(), l.productId(), l.acceptedQuantity(), "RECEIVE", "Goods receipt " + r.sourceRef(), actor);
            CostLayer c = new CostLayer();
            c.tenantId = tenant; c.productId = l.productId(); c.warehouseId = r.warehouseId(); c.remaining = l.acceptedQuantity(); c.unitCost = l.unitCost();
            c.batchNo = l.batchNo(); c.expiryDate = l.expiryDate(); c.sourceRef = r.sourceRef();
            layers.save(c);
            BigDecimal avg = Valuation.weightedAverage(onHandBefore, avgBefore, l.acceptedQuantity(), l.unitCost());
            jdbc.update("UPDATE inventory_item SET cost_price=?, incoming_quantity = CASE WHEN incoming_quantity >= ? THEN incoming_quantity - ? ELSE 0 END WHERE tenant_id=? AND product_id=?",
                    avg, l.acceptedQuantity(), l.acceptedQuantity(), tenant, l.productId());
        }
        audit(tenant, "RECEIPT", r.sourceRef(), "RECEIVE", actor, r.lines().size() + " lines");
        return Map.of("status", "RECEIVED", "sourceRef", r.sourceRef());
    }

    /** Incoming is tracked separately and never counted as available. */
    @Transactional
    public void adjustIncoming(UUID tenant, Long productId, int delta) {
        jdbc.update("UPDATE inventory_item SET incoming_quantity = CASE WHEN incoming_quantity + ? < 0 THEN 0 ELSE incoming_quantity + ? END WHERE tenant_id=? AND product_id=?", delta, delta, tenant, productId);
    }

    /** Supplier return: remove accepted stock using FIFO layers; returns the cost value for the credit note. */
    @Transactional
    public Map<String, Object> supplierReturnOut(UUID tenant, String sourceRef, Long warehouseId, Long productId, int quantity, String actor) {
        var prior = jdbc.queryForList("SELECT details FROM wms_audit_log WHERE tenant_id=? AND entity='SUPPLIER_RETURN' AND entity_id=?", String.class, tenant, sourceRef);
        if (!prior.isEmpty()) return Map.of("sourceRef", sourceRef, "quantity", quantity, "costValue", new BigDecimal(prior.get(0).substring(prior.get(0).indexOf("cost ") + 5)), "replayed", true);
        warehouse(tenant, warehouseId);
        var open = layers.findByTenantIdAndProductIdAndRemainingGreaterThanOrderByReceivedAtAscIdAsc(tenant, productId, 0);
        List<Valuation.Layer> view = open.stream().map(c -> new Valuation.Layer(c.id, c.remaining, c.unitCost)).toList();
        List<Valuation.Consumption> used;
        try { used = Valuation.fifoConsume(view, quantity); } catch (IllegalStateException e) { throw conflict("Not enough costed stock to return: " + e.getMessage()); }
        applyOnHand(tenant, warehouseId, productId, -quantity, "SUPPLIER_RETURN", sourceRef, actor);
        Map<Long, CostLayer> byId = open.stream().collect(Collectors.toMap(c -> c.id, c -> c));
        for (var u : used) { CostLayer c = byId.get(u.layerId()); c.remaining -= u.quantity(); layers.save(c); }
        audit(tenant, "SUPPLIER_RETURN", sourceRef, "ISSUE", actor, "qty " + quantity + " cost " + Valuation.fifoCost(used).toPlainString());
        return Map.of("sourceRef", sourceRef, "quantity", quantity, "costValue", Valuation.fifoCost(used));
    }

    // ---------- reads ----------
    @Transactional(readOnly = true)
    public Map<String, Object> inventoryPage(UUID tenant, String q, String status, String category, String sort, int page, int size) {
        List<InventoryItem> all = items.findByTenantId(tenant);
        var stream = all.stream().filter(i -> q == null || q.isBlank() || (i.getSku() != null && i.getSku().toLowerCase().contains(q.toLowerCase())) || String.valueOf(i.getProductId()).equals(q.trim()));
        if (category != null && !category.isBlank()) stream = stream.filter(i -> category.equalsIgnoreCase(i.getCategory()));
        if (status != null && !status.isBlank()) stream = stream.filter(i -> statusOf(i).name().equalsIgnoreCase(status));
        Comparator<InventoryItem> cmp = switch (sort == null ? "" : sort) {
            case "available" -> Comparator.comparingInt(InventoryItem::getAvailableQuantity);
            case "onHand" -> Comparator.comparingInt(InventoryItem::getStockQuantity);
            case "sku" -> Comparator.comparing(i -> i.getSku() == null ? "" : i.getSku());
            default -> Comparator.comparingLong(InventoryItem::getProductId);
        };
        List<InventoryItem> sorted = stream.sorted(cmp).toList();
        int from = Math.min(Math.max(page, 0) * Math.max(size, 1), sorted.size());
        int to = Math.min(from + Math.max(1, Math.min(size, 200)), sorted.size());
        List<Map<String, Object>> rows = sorted.subList(from, to).stream().map(this::itemView).toList();
        return Map.of("content", rows, "total", sorted.size(), "page", page, "size", size);
    }

    private Availability.Status statusOf(InventoryItem i) { return Availability.status(i.getAvailableQuantity(), i.getReorderThreshold()); }

    private Map<String, Object> itemView(InventoryItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productId", i.getProductId()); m.put("sku", i.getSku()); m.put("category", i.getCategory());
        m.put("onHand", i.getStockQuantity()); m.put("reserved", i.getReservedQuantity()); m.put("unavailable", i.getUnavailableQuantity());
        m.put("incoming", i.getIncomingQuantity()); m.put("available", i.getAvailableQuantity());
        m.put("reorderPoint", i.getReorderThreshold()); m.put("status", statusOf(i).name()); m.put("unitCost", i.getCostPrice());
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(UUID tenant, Long warehouseId) {
        List<InventoryItem> all = items.findByTenantId(tenant);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("skus", all.size());
        m.put("onHandUnits", all.stream().mapToInt(InventoryItem::getStockQuantity).sum());
        m.put("reservedUnits", all.stream().mapToInt(InventoryItem::getReservedQuantity).sum());
        m.put("availableUnits", all.stream().mapToInt(InventoryItem::getAvailableQuantity).sum());
        m.put("incomingUnits", all.stream().mapToInt(InventoryItem::getIncomingQuantity).sum());
        m.put("lowStock", all.stream().filter(i -> statusOf(i) == Availability.Status.LOW_STOCK).count());
        m.put("outOfStock", all.stream().filter(i -> statusOf(i) == Availability.Status.OUT_OF_STOCK).count());
        m.put("valuation", all.stream().map(i -> i.getCostPrice().multiply(BigDecimal.valueOf(i.getStockQuantity()))).reduce(BigDecimal.ZERO, BigDecimal::add));
        m.put("warehouses", warehouses.findByTenantIdOrderByCode(tenant).size());
        m.put("pendingAdjustments", adjustments.countByTenantIdAndStatus(tenant, "PENDING"));
        m.put("openTransfers", transfers.findByTenantIdOrderByIdDesc(tenant).stream().filter(t -> !TransferState.valueOf(t.status).terminal()).count());
        if (warehouseId != null) {
            var rows = whStock.findByTenantIdAndWarehouseId(tenant, warehouse(tenant, warehouseId).id);
            m.put("warehouseOnHand", rows.stream().mapToInt(s -> s.onHand).sum());
            m.put("warehouseReserved", rows.stream().mapToInt(s -> s.reserved).sum());
        }
        return m;
    }

    /** Valuation by FIFO layers vs weighted average on the item, with a reconciliation against on-hand. */
    @Transactional(readOnly = true)
    public Map<String, Object> valuation(UUID tenant) {
        List<CostLayer> open = layers.findByTenantIdAndRemainingGreaterThan(tenant, 0);
        Map<Long, List<CostLayer>> byProduct = open.stream().collect(Collectors.groupingBy(c -> c.productId));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal fifoTotal = BigDecimal.ZERO, avgTotal = BigDecimal.ZERO;
        for (InventoryItem i : items.findByTenantId(tenant)) {
            List<Valuation.Layer> ls = byProduct.getOrDefault(i.getProductId(), List.of()).stream().map(c -> new Valuation.Layer(c.id, c.remaining, c.unitCost)).toList();
            int layered = ls.stream().mapToInt(Valuation.Layer::remaining).sum();
            BigDecimal fifo = Valuation.layersValue(ls);
            BigDecimal avg = i.getCostPrice().multiply(BigDecimal.valueOf(i.getStockQuantity())).setScale(4, java.math.RoundingMode.HALF_UP);
            fifoTotal = fifoTotal.add(fifo); avgTotal = avgTotal.add(avg);
            rows.add(Map.of("productId", i.getProductId(), "onHand", i.getStockQuantity(), "costedUnits", layered, "uncostedUnits", i.getStockQuantity() - layered,
                    "fifoValue", fifo, "weightedAverageValue", avg));
        }
        return Map.of("rows", rows, "fifoTotal", fifoTotal, "weightedAverageTotal", avgTotal,
                "note", "uncostedUnits are on-hand units without a cost layer (e.g. opening balances); FIFO value excludes them.");
    }

    /** Rule-based forecast from real movement history only. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forecast(UUID tenant, int windowDays) {
        int days = Math.max(1, windowDays);
        Timestamp since = Timestamp.from(Instant.now().minus(days, ChronoUnit.DAYS));
        Map<Long, Long> sold = new HashMap<>();
        jdbc.query("SELECT product_id, -SUM(delta) AS qty FROM stock_movement WHERE tenant_id=? AND delta<0 AND movement_type IN ('RESERVATION_CONFIRM','ORDER_DEDUCTION','SALE') AND created_at >= ? GROUP BY product_id",
                rs -> { sold.put(rs.getLong(1), rs.getLong(2)); }, tenant, since);
        InventoryConfig cfg = config(tenant);
        List<Map<String, Object>> out = new ArrayList<>();
        for (InventoryItem i : items.findByTenantId(tenant)) {
            BigDecimal velocity = BigDecimal.valueOf(sold.getOrDefault(i.getProductId(), 0L)).divide(BigDecimal.valueOf(days), 4, java.math.RoundingMode.HALF_UP);
            BigDecimal cover = ReorderMath.coverageDays(i.getAvailableQuantity(), velocity);
            int rop = ReorderMath.reorderPoint(velocity, cfg.defaultLeadTimeDays, cfg.defaultSafetyStock);
            String classification = velocity.signum() == 0 ? (i.getStockQuantity() > 0 ? "DEAD_STOCK" : "NO_DEMAND")
                    : cover.compareTo(BigDecimal.valueOf(90)) > 0 ? "SLOW_MOVING" : "ACTIVE";
            boolean risk = cover != null && cover.compareTo(BigDecimal.valueOf(cfg.defaultLeadTimeDays)) < 0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", i.getProductId()); m.put("sku", i.getSku()); m.put("available", i.getAvailableQuantity());
            m.put("avgDailyDemand", velocity); m.put("coverageDays", cover); m.put("reorderPoint", rop);
            m.put("classification", classification); m.put("stockoutRisk", risk);
            m.put("suggestedOrderQuantity", ReorderMath.suggestedQuantity(i.getAvailableQuantity(), i.getIncomingQuantity(), rop, rop * 2, 1));
            m.put("basis", "movement history of last " + days + " days");
            out.add(m);
        }
        return out;
    }

    /** Reconciles item-level reserved quantity against active holds and transfer reservations. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> reservationReconciliation(UUID tenant) {
        Map<Long, Long> holds = new HashMap<>();
        jdbc.query("SELECT product_id, SUM(quantity) FROM stock_reservation WHERE tenant_id=? AND status='HOLD' GROUP BY product_id", rs -> { holds.put(rs.getLong(1), rs.getLong(2)); }, tenant);
        Map<Long, Long> whReserved = new HashMap<>();
        jdbc.query("SELECT product_id, SUM(reserved) FROM wms_stock WHERE tenant_id=? GROUP BY product_id", rs -> { whReserved.put(rs.getLong(1), rs.getLong(2)); }, tenant);
        List<Map<String, Object>> out = new ArrayList<>();
        for (InventoryItem i : items.findByTenantId(tenant)) {
            long expected = holds.getOrDefault(i.getProductId(), 0L) + whReserved.getOrDefault(i.getProductId(), 0L);
            if (expected != i.getReservedQuantity())
                out.add(Map.of("productId", i.getProductId(), "itemReserved", i.getReservedQuantity(), "activeHolds", holds.getOrDefault(i.getProductId(), 0L), "transferReserved", whReserved.getOrDefault(i.getProductId(), 0L), "drift", i.getReservedQuantity() - expected));
        }
        return out;
    }

    @Transactional
    public InventoryConfig getOrCreateConfig(UUID tenant) { return config(tenant); }

    @Transactional
    public InventoryConfig updateConfig(UUID tenant, Map<String, Object> body, String actor) {
        InventoryConfig c = config(tenant);
        if (body.get("adjustmentApprovalThreshold") != null) c.adjustmentApprovalThreshold = positive(body.get("adjustmentApprovalThreshold"));
        if (body.get("reservationHoldMinutes") != null) c.reservationHoldMinutes = positive(body.get("reservationHoldMinutes"));
        if (body.get("defaultLeadTimeDays") != null) c.defaultLeadTimeDays = positive(body.get("defaultLeadTimeDays"));
        if (body.get("defaultSafetyStock") != null) c.defaultSafetyStock = positive(body.get("defaultSafetyStock"));
        if (body.get("overReceiptTolerancePercent") != null) c.overReceiptTolerancePercent = positive(body.get("overReceiptTolerancePercent"));
        if (body.get("valuationMethod") != null) {
            String v = String.valueOf(body.get("valuationMethod")).toUpperCase();
            if (!v.equals("FIFO") && !v.equals("WEIGHTED_AVERAGE")) throw bad("valuationMethod must be FIFO or WEIGHTED_AVERAGE.");
            c.valuationMethod = v;
        }
        configs.save(c);
        audit(tenant, "CONFIG", tenant, "UPDATE", actor, body.keySet().toString());
        return c;
    }

    @Transactional(readOnly = true)
    public List<OpsAudit> auditTrail(UUID tenant) { return audits.findTop100ByTenantIdOrderByIdDesc(tenant); }

    // ---------- helpers ----------
    private InventoryConfig config(UUID tenant) {
        return configs.findById(tenant).orElseGet(() -> { InventoryConfig c = new InventoryConfig(); c.tenantId = tenant; return configs.save(c); });
    }
    private int positive(Object v) {
        try { int n = Integer.parseInt(String.valueOf(v)); if (n < 0) throw new NumberFormatException(); return n; }
        catch (NumberFormatException e) { throw bad("Configuration values must be non-negative integers."); }
    }
    private Warehouse warehouse(UUID tenant, Long id) {
        if (id == null) throw bad("Warehouse is required.");
        return warehouses.findByTenantIdAndId(tenant, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Warehouse not found."));
    }
    private StockTransfer transfer(UUID tenant, Long id) {
        return transfers.findByTenantIdAndId(tenant, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found."));
    }
    private void audit(UUID tenant, String entity, Object id, String action, String actor, String details) {
        OpsAudit a = new OpsAudit(); a.tenantId = tenant; a.entity = entity; a.entityId = String.valueOf(id); a.action = action; a.actor = actor; a.details = details;
        audits.save(a);
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException conflict(String m) { return new ResponseStatusException(HttpStatus.CONFLICT, m); }
}
